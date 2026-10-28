package ar.edu.uade.repuestos.modelo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ar.edu.uade.repuestos.util.TransicionInvalidaException;
import java.math.BigDecimal;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

class VentaTest {

    @Test
    void naceReservandoYConReferenciaPropia() {
        Venta a = nueva();
        Venta b = nueva();
        assertTrue(a.isPendienteDePago());
        assertNull(a.getMpPagoId());
        assertEquals(36, a.getReferenciaPago().length());
        assertNotEquals(a.getReferenciaPago(), b.getReferenciaPago());
    }

    @Test
    void alPagarseGuardaElIdDelPago() {
        Venta v = nueva();
        v.marcarPagada("1000000001");
        assertEquals(EstadoVenta.PAGADA, v.getEstado());
        assertEquals("1000000001", v.getMpPagoId());
    }

    @Test
    void pendientePuedeTerminarDeTresManeras() {
        assertEquals(EstadoVenta.PAGADA, EstadoVenta.PENDIENTE_PAGO.pagar());
        assertEquals(EstadoVenta.RECHAZADA, EstadoVenta.PENDIENTE_PAGO.rechazar());
        assertEquals(EstadoVenta.VENCIDA, EstadoVenta.PENDIENTE_PAGO.vencer());
    }

    // Cada estado final contra cada accion: ninguna combinacion vale
    @ParameterizedTest(name = "{0} no se puede {1}")
    @MethodSource("transicionesInvalidas")
    void unEstadoFinalNoTieneSalida(EstadoVenta estado, String accion, Consumer<Venta> hacer) {
        Venta v = nueva();
        v.setEstado(estado);
        var e = assertThrows(TransicionInvalidaException.class, () -> hacer.accept(v));
        assertTrue(e.getMessage().contains(accion));
        assertEquals(estado, v.getEstado(), "el estado no tiene que cambiar");
    }

    static Stream<Arguments> transicionesInvalidas() {
        return Stream.of(EstadoVenta.PAGADA, EstadoVenta.RECHAZADA, EstadoVenta.VENCIDA).flatMap(e -> Stream.of(
                Arguments.of(e, "pagar", (Consumer<Venta>) v -> v.marcarPagada("1")),
                Arguments.of(e, "rechazar", (Consumer<Venta>) Venta::marcarRechazada),
                Arguments.of(e, "vencer", (Consumer<Venta>) Venta::vencer)));
    }

    @ParameterizedTest
    @EnumSource(value = EstadoVenta.class, names = "PENDIENTE_PAGO", mode = EnumSource.Mode.EXCLUDE)
    void soloLaPendienteEsperaPago(EstadoVenta estado) {
        Venta v = nueva();
        v.setEstado(estado);
        assertTrue(!v.isPendienteDePago());
    }

    // SEC-05: la compra solo es de quien la hizo
    @Test
    void laCompraEsSoloDeQuienLaHizo() {
        Usuario ana = new Usuario("ana@mail.com", "x", "Ana", Rol.CLIENTE);
        Venta v = new Venta(ana, new Producto(), 1, new BigDecimal("46800.00"));
        assertTrue(v.esDe("ana@mail.com"));
        assertTrue(v.esDe("Ana@Mail.com"));
        assertTrue(!v.esDe("carlos@mail.com"));
        assertTrue(!v.esDe("anonymous"));
        assertTrue(!v.esDe(null));
    }

    private static Venta nueva() {
        return new Venta(new Usuario(), new Producto(), 1, new BigDecimal("46800.00"));
    }
}
