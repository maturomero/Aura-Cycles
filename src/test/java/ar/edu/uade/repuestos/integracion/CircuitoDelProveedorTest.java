package ar.edu.uade.repuestos.integracion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ar.edu.uade.repuestos.integracion.CircuitoDelProveedor.Estado;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

// los tres estados del circuit breaker con un reloj que avanzamos a mano (sin esperar 30 s de verdad)
class CircuitoDelProveedorTest {

    private Instant ahora;
    private CircuitoDelProveedor circuito;

    @BeforeEach
    void preparar() {
        ahora = Instant.parse("2026-11-20T10:00:00Z");
        Clock reloj = new Clock() {
            @Override
            public ZoneOffset getZone() {
                return ZoneOffset.UTC;
            }

            @Override
            public Clock withZone(java.time.ZoneId zona) {
                return this;
            }

            @Override
            public Instant instant() {
                return ahora;
            }
        };
        circuito = new CircuitoDelProveedor(3, Duration.ofSeconds(30), reloj);
    }

    private void fallar(int veces) {
        for (int i = 0; i < veces; i++) {
            assertTrue(circuito.permiteLlamar());
            circuito.registrarFalla();
        }
    }

    @Test
    void arrancaCerradoYDejaLlamar() {
        assertEquals(Estado.CERRADO, circuito.estado());
        assertTrue(circuito.permiteLlamar());
    }

    @Test
    void conDosFallasSigueCerrado() {
        fallar(2);
        assertEquals(Estado.CERRADO, circuito.estado());
        assertTrue(circuito.permiteLlamar());
    }

    @Test
    void unExitoEnElMedioReiniciaLaCuenta() {
        fallar(2);
        circuito.registrarExito();
        fallar(2);
        assertEquals(Estado.CERRADO, circuito.estado());
    }

    @Test
    void aLaTerceraFallaSeAbreYNoDejaLlamar() {
        fallar(3);
        assertEquals(Estado.ABIERTO, circuito.estado());
        assertFalse(circuito.permiteLlamar());
        ahora = ahora.plusSeconds(29);
        assertFalse(circuito.permiteLlamar());
    }

    @Test
    void pasadaLaEsperaDejaProbarYSiAndaSeCierra() {
        fallar(3);
        ahora = ahora.plusSeconds(30);
        assertTrue(circuito.permiteLlamar());
        assertEquals(Estado.SEMIABIERTO, circuito.estado());
        circuito.registrarExito();
        assertEquals(Estado.CERRADO, circuito.estado());
        assertTrue(circuito.permiteLlamar());
    }

    @Test
    void siLaPruebaFallaSeVuelveAAbrirOtros30Segundos() {
        fallar(3);
        ahora = ahora.plusSeconds(31);
        assertTrue(circuito.permiteLlamar());
        circuito.registrarFalla();
        assertEquals(Estado.ABIERTO, circuito.estado());
        ahora = ahora.plusSeconds(29);
        assertFalse(circuito.permiteLlamar());
        ahora = ahora.plusSeconds(1);
        assertTrue(circuito.permiteLlamar());
    }

    @Test
    void elConstructorPorDefectoUsaTresFallasY30Segundos() {
        CircuitoDelProveedor porDefecto = new CircuitoDelProveedor();
        for (int i = 0; i < 3; i++) {
            porDefecto.registrarFalla();
        }
        assertEquals(Estado.ABIERTO, porDefecto.estado());
        assertEquals(Duration.ofSeconds(30), CircuitoDelProveedor.ESPERA_ABIERTO);
    }
}
