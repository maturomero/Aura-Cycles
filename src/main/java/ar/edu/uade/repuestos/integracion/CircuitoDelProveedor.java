package ar.edu.uade.repuestos.integracion;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.logging.Logger;

/**
 * Circuit breaker de la llamada SOAP al proveedor.
 * Sin esto, con el proveedor caido cada devolucion espera los 5 s del timeout antes de quedar PENDIENTE_VALIDACION.
 * Despues de 3 fallas seguidas el circuito se abre: durante 30 s no se llama al proveedor y la devolucion
 * va directo al mismo fallback (PENDIENTE_VALIDACION y cola de validaciones). Pasados los 30 s deja pasar
 * una llamada de prueba: si anda se cierra, si falla se vuelve a abrir otros 30 s.
 * Un Fault de negocio (garantia no valida) no cuenta como falla: el proveedor contesto bien.
 */
public class CircuitoDelProveedor {

    public static final int FALLAS_PARA_ABRIR = 3;
    public static final Duration ESPERA_ABIERTO = Duration.ofSeconds(30);

    private static final Logger LOG = Logger.getLogger(CircuitoDelProveedor.class.getName());

    public enum Estado { CERRADO, ABIERTO, SEMIABIERTO }

    private final int fallasParaAbrir;
    private final Duration esperaAbierto;
    private final Clock reloj;

    private Estado estado = Estado.CERRADO;
    private int fallasSeguidas;
    private Instant abiertoDesde;

    public CircuitoDelProveedor() {
        this(FALLAS_PARA_ABRIR, ESPERA_ABIERTO, Clock.systemUTC());
    }

    public CircuitoDelProveedor(int fallasParaAbrir, Duration esperaAbierto, Clock reloj) {
        this.fallasParaAbrir = fallasParaAbrir;
        this.esperaAbierto = esperaAbierto;
        this.reloj = reloj;
    }

    // true si se puede llamar al proveedor. Abierto y ya paso la espera: pasa a semiabierto y deja probar
    public synchronized boolean permiteLlamar() {
        if (estado == Estado.ABIERTO && !reloj.instant().isBefore(abiertoDesde.plus(esperaAbierto))) {
            estado = Estado.SEMIABIERTO;
            LOG.info("[SOAP] circuito semiabierto: se prueba una llamada al proveedor");
        }
        return estado != Estado.ABIERTO;
    }

    public synchronized void registrarExito() {
        if (estado != Estado.CERRADO) {
            LOG.info("[SOAP] el proveedor volvio a responder, circuito cerrado");
        }
        estado = Estado.CERRADO;
        fallasSeguidas = 0;
    }

    public synchronized void registrarFalla() {
        fallasSeguidas++;
        if (estado == Estado.SEMIABIERTO || fallasSeguidas >= fallasParaAbrir) {
            estado = Estado.ABIERTO;
            abiertoDesde = reloj.instant();
            int fallas = fallasSeguidas;
            LOG.warning(() -> "[SOAP] circuito abierto despues de " + fallas + " fallas seguidas: "
                    + esperaAbierto.toSeconds() + " s sin llamar al proveedor");
        }
    }

    public synchronized Estado estado() {
        return estado;
    }
}
