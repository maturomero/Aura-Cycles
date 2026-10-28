package ar.edu.uade.repuestos.modelo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ar.edu.uade.repuestos.util.TransicionInvalidaException;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class DevolucionTest {

    @Test
    void naceEsperandoLaValidacionDelProveedor() {
        Devolucion d = nueva();
        assertEquals(EstadoDevolucion.PENDIENTE_VALIDACION, d.getEstado());
        assertNull(d.getRma());
        assertFalse(d.isSinValidar());
    }

    @Test
    void conRmaPasaAlModerador() {
        Devolucion d = nueva();
        d.garantiaValidada("RMA-2026-00002");
        assertEquals(EstadoDevolucion.PENDIENTE, d.getEstado());
        assertEquals("RMA-2026-00002", d.getRma());
        assertFalse(d.isSinValidar());
    }

    @Test
    void siElProveedorRechazaLaGarantiaQuedaRechazada() {
        Devolucion d = nueva();
        d.garantiaRechazada();
        assertEquals(EstadoDevolucion.RECHAZADA, d.getEstado());
        assertNotNull(d.getFechaResolucion());
    }

    // Con 5 intentos maximo: los primeros 4 la dejan esperando, el 5to la pasa al moderador marcada
    @Test
    void despuesDelUltimoIntentoPasaAlModeradorSinValidar() {
        Devolucion d = nueva();
        for (int i = 1; i < 5; i++) {
            assertFalse(d.validacionFallida(5));
            assertEquals(EstadoDevolucion.PENDIENTE_VALIDACION, d.getEstado());
        }
        assertTrue(d.validacionFallida(5));
        assertEquals(EstadoDevolucion.PENDIENTE, d.getEstado());
        assertEquals(5, d.getIntentosValidacion());
        assertTrue(d.isSinValidar());
    }

    @Test
    void elRecorridoCompletoHastaElReembolso() {
        Devolucion d = nueva();
        d.garantiaValidada("RMA-2026-00003");
        d.aprobar(new BigDecimal("42120.00"));
        assertEquals(EstadoDevolucion.APROBADA, d.getEstado());
        d.reembolsar();
        assertEquals(EstadoDevolucion.REEMBOLSADA, d.getEstado());
    }

    // Toda combinacion estado x accion que no esta habilitada tiene que fallar y no cambiar el estado
    static final Set<String> VALIDAS = Set.of(
            "PENDIENTE_VALIDACION.validar", "PENDIENTE_VALIDACION.rechazar",
            "PENDIENTE.aprobar", "PENDIENTE.rechazar",
            "APROBADA.reembolsar");

    static Stream<Arguments> transicionesInvalidas() {
        return Arrays.stream(EstadoDevolucion.values()).flatMap(e -> Stream.of(
                Arguments.of(e, "validar", (Function<EstadoDevolucion, EstadoDevolucion>) EstadoDevolucion::validar),
                Arguments.of(e, "aprobar", (Function<EstadoDevolucion, EstadoDevolucion>) EstadoDevolucion::aprobar),
                Arguments.of(e, "rechazar", (Function<EstadoDevolucion, EstadoDevolucion>) EstadoDevolucion::rechazar),
                Arguments.of(e, "reembolsar", (Function<EstadoDevolucion, EstadoDevolucion>) EstadoDevolucion::reembolsar)))
            .filter(a -> !VALIDAS.contains(a.get()[0] + "." + a.get()[1]));
    }

    @ParameterizedTest(name = "{0} no se puede {1}")
    @MethodSource("transicionesInvalidas")
    void transicionInvalida(EstadoDevolucion estado, String accion, Function<EstadoDevolucion, EstadoDevolucion> hacer) {
        var e = assertThrows(TransicionInvalidaException.class, () -> hacer.apply(estado));
        assertTrue(e.getMessage().contains(accion) && e.getMessage().contains(estado.name()), e.getMessage());
    }

    @Test
    void sonQuinceLasTransicionesInvalidas() {
        assertEquals(EstadoDevolucion.values().length * 4 - VALIDAS.size(), transicionesInvalidas().count());
        assertEquals(15, transicionesInvalidas().count());
    }

    private static Devolucion nueva() {
        Venta v = new Venta(new Usuario(), new Producto(), 1, new BigDecimal("46800.00"));
        return new Devolucion(v, MotivoDevolucion.DEFECTUOSO, "vino roto");
    }
}
