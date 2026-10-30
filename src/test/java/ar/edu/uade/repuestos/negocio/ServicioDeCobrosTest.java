package ar.edu.uade.repuestos.negocio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ar.edu.uade.repuestos.integracion.PagoMP;
import ar.edu.uade.repuestos.modelo.Producto;
import ar.edu.uade.repuestos.modelo.Usuario;
import ar.edu.uade.repuestos.modelo.Venta;
import ar.edu.uade.repuestos.negocio.ServicioDeCobros.Decision;
import ar.edu.uade.repuestos.util.ReglaNegocioException;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

// La regla de que hacer con un pago, sin servidor: lo que diga la URL de vuelta no importa
class ServicioDeCobrosTest {

    private Venta venta;

    @BeforeEach
    void ventaPendiente() {
        venta = new Venta(new Usuario(), new Producto(), 2, new BigDecimal("93600.00"));
        venta.setId(9L);
    }

    @Test
    void unPagoAprobadoPorElMontoExactoSeAcepta() {
        assertEquals(Decision.PAGAR, ServicioDeCobros.decidir(venta, pago("approved", "93600")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"rejected", "cancelled", "refunded", "charged_back"})
    void unPagoQueNoSeConcretoLiberaElStock(String estado) {
        assertEquals(Decision.RECHAZAR, ServicioDeCobros.decidir(venta, pago(estado, "93600.00")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"pending", "in_process", "authorized", "in_mediation"})
    void unPagoEnCursoNoCambiaNada(String estado) {
        assertEquals(Decision.ESPERAR, ServicioDeCobros.decidir(venta, pago(estado, "93600.00")));
    }

    // Alguien vuelve con el payment_id de otra compra suya que si pago
    @Test
    void unPagoDeOtraCompraNoSeAcepta() {
        PagoMP otro = new PagoMP("1000000001", "approved", "otra-referencia", new BigDecimal("93600.00"), "ARS");
        var e = assertThrows(ReglaNegocioException.class, () -> ServicioDeCobros.decidir(venta, otro));
        assertTrue(e.getMessage().contains("no corresponde"));
    }

    @Test
    void unPagoSinReferenciaNoSeAcepta() {
        PagoMP sinRef = new PagoMP("1000000001", "approved", null, new BigDecimal("93600.00"), "ARS");
        assertThrows(ReglaNegocioException.class, () -> ServicioDeCobros.decidir(venta, sinRef));
    }

    @ParameterizedTest
    @ValueSource(strings = {"1.00", "46800.00", "93600.01"})
    void unPagoPorOtroMontoNoSeAcepta(String monto) {
        assertThrows(ReglaNegocioException.class, () -> ServicioDeCobros.decidir(venta, pago("approved", monto)));
    }

    @Test
    void unPagoEnOtraMonedaNoSeAcepta() {
        PagoMP usd = new PagoMP("1", "approved", venta.getReferenciaPago(), new BigDecimal("93600.00"), "USD");
        assertThrows(ReglaNegocioException.class, () -> ServicioDeCobros.decidir(venta, usd));
    }

    @Test
    void siHayUnAprobadoGanaAunqueNoSeaElUltimo() {
        PagoMP rechazadoNuevo = pago("rejected", "93600");
        PagoMP aprobadoViejo = pago("approved", "93600");
        assertEquals(aprobadoViejo, ServicioDeCobros.elegir(List.of(rechazadoNuevo, aprobadoViejo)).orElseThrow());
    }

    @Test
    void sinAprobadosSeMiraElMasNuevo() {
        PagoMP nuevo = pago("rejected", "93600");
        PagoMP viejo = pago("pending", "93600");
        assertEquals(nuevo, ServicioDeCobros.elegir(List.of(nuevo, viejo)).orElseThrow());
        assertTrue(ServicioDeCobros.elegir(List.of()).isEmpty());
    }

    private PagoMP pago(String estado, String monto) {
        return new PagoMP("1000000001", estado, venta.getReferenciaPago(), new BigDecimal(monto), "ARS");
    }
}
