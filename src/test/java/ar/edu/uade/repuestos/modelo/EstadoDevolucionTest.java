package ar.edu.uade.repuestos.modelo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import ar.edu.uade.repuestos.util.TransicionInvalidaException;
import org.junit.jupiter.api.Test;

class EstadoDevolucionTest {

    @Test
    void pendienteSePuedeAprobarORechazar() {
        assertEquals(EstadoDevolucion.APROBADA, EstadoDevolucion.PENDIENTE.aprobar());
        assertEquals(EstadoDevolucion.RECHAZADA, EstadoDevolucion.PENDIENTE.rechazar());
    }

    @Test
    void aprobadaPasaAReembolsada() {
        assertEquals(EstadoDevolucion.REEMBOLSADA, EstadoDevolucion.APROBADA.reembolsar());
    }

    @Test
    void noSePuedeAprobarDosVeces() {
        assertThrows(TransicionInvalidaException.class, EstadoDevolucion.APROBADA::aprobar);
    }

    @Test
    void unaRechazadaNoSeReembolsa() {
        assertThrows(TransicionInvalidaException.class, EstadoDevolucion.RECHAZADA::reembolsar);
    }

    @Test
    void noSeReembolsaSinAprobar() {
        assertThrows(TransicionInvalidaException.class, EstadoDevolucion.PENDIENTE::reembolsar);
    }
}
