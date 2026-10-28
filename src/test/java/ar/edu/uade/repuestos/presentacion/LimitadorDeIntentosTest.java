package ar.edu.uade.repuestos.presentacion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

// Fuerza bruta sobre el login: con un reloj de mentira para no esperar 5 minutos de verdad
class LimitadorDeIntentosTest {

    private Instant ahora;
    private LimitadorDeIntentos limitador;

    @BeforeEach
    void preparar() {
        ahora = Instant.parse("2026-11-10T13:00:00Z");
        Clock reloj = new Clock() {
            public ZoneOffset getZone() { return ZoneOffset.UTC; }
            public Clock withZone(java.time.ZoneId z) { return this; }
            public Instant instant() { return ahora; }
        };
        limitador = new LimitadorDeIntentos(reloj);
    }

    @Test
    void cuatroFallosTodaviaDejanProbar() {
        fallar(4, "email:ana@mail.com");
        assertEquals(0, limitador.segundosDeBloqueo("email:ana@mail.com"));
    }

    @Test
    void alQuintoFalloBloqueaCincoMinutos() {
        fallar(5, "email:ana@mail.com");
        long espera = limitador.segundosDeBloqueo("email:ana@mail.com");
        assertTrue(espera > 290 && espera <= 301, "espera " + espera);
        avanzar(Duration.ofMinutes(5).plusSeconds(2));
        assertEquals(0, limitador.segundosDeBloqueo("email:ana@mail.com"));
    }

    @Test
    void losFallosViejosNoSuman() {
        fallar(4, "email:ana@mail.com");
        avanzar(Duration.ofMinutes(11));
        fallar(1, "email:ana@mail.com");
        assertEquals(0, limitador.segundosDeBloqueo("email:ana@mail.com"));
    }

    @Test
    void entrarBienBorraLosFallos() {
        fallar(4, "email:ana@mail.com");
        limitador.exito("email:ana@mail.com");
        fallar(4, "email:ana@mail.com");
        assertEquals(0, limitador.segundosDeBloqueo("email:ana@mail.com"));
    }

    // Probar un email distinto en cada intento desde la misma IP tambien se corta
    @Test
    void laIpSeBloqueaAunqueCambieElEmail() {
        for (int i = 0; i < 5; i++) {
            limitador.fallo("email:user" + i + "@mail.com", "ip:10.0.0.7");
        }
        assertTrue(limitador.segundosDeBloqueo("email:nuevo@mail.com", "ip:10.0.0.7") > 0);
        assertEquals(0, limitador.segundosDeBloqueo("email:nuevo@mail.com", "ip:10.0.0.8"));
    }

    private void fallar(int veces, String clave) {
        for (int i = 0; i < veces; i++) {
            limitador.fallo(clave);
        }
    }

    private void avanzar(Duration d) {
        ahora = ahora.plus(d);
    }
}
