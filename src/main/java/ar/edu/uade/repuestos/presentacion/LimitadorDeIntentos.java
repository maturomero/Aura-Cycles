package ar.edu.uade.repuestos.presentacion;

import jakarta.enterprise.context.ApplicationScoped;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// Fuerza bruta sobre el login: 5 fallos en 10 minutos bloquean 5 minutos, por email y por IP
@ApplicationScoped
public class LimitadorDeIntentos {

    static final int MAX_FALLOS = 5;
    static final Duration VENTANA = Duration.ofMinutes(10);
    static final Duration BLOQUEO = Duration.ofMinutes(5);

    private final Map<String, Registro> registros = new ConcurrentHashMap<>();
    private final Clock reloj;

    public LimitadorDeIntentos() {
        this(Clock.systemUTC());
    }

    LimitadorDeIntentos(Clock reloj) {
        this.reloj = reloj;
    }

    // Segundos que faltan para poder intentar de nuevo; 0 si no esta bloqueado
    public long segundosDeBloqueo(String... claves) {
        Instant ahora = reloj.instant();
        long max = 0;
        for (String clave : claves) {
            Registro r = registros.get(clave);
            if (r != null && r.bloqueadoHasta != null && r.bloqueadoHasta.isAfter(ahora)) {
                max = Math.max(max, Duration.between(ahora, r.bloqueadoHasta).toSeconds() + 1);
            }
        }
        return max;
    }

    public void fallo(String... claves) {
        Instant ahora = reloj.instant();
        for (String clave : claves) {
            registros.compute(clave, (k, r) -> {
                if (r == null || r.desde.plus(VENTANA).isBefore(ahora)) {
                    r = new Registro(ahora);
                }
                r.fallos++;
                if (r.fallos >= MAX_FALLOS) {
                    r.bloqueadoHasta = ahora.plus(BLOQUEO);
                    r.fallos = 0;
                    r.desde = ahora;
                }
                return r;
            });
        }
    }

    public void exito(String... claves) {
        for (String clave : claves) {
            registros.remove(clave);
        }
    }

    private static final class Registro {
        private Instant desde;
        private int fallos;
        private Instant bloqueadoHasta;

        private Registro(Instant desde) {
            this.desde = desde;
        }
    }
}
