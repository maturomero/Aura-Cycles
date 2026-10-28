package ar.edu.uade.repuestos.negocio.reembolso;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import ar.edu.uade.repuestos.modelo.MotivoDevolucion;
import java.math.BigDecimal;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

class PoliticasDeReembolsoTest {

    private final ReembolsoTotal total = new ReembolsoTotal();
    private final ReembolsoConRetencion retencion = new ReembolsoConRetencion();

    // Para cada motivo aplica exactamente una de las dos
    @ParameterizedTest
    @EnumSource(MotivoDevolucion.class)
    void cadaMotivoTieneUnaSolaPolitica(MotivoDevolucion m) {
        assertNotEquals(total.aplicaA(m), retencion.aplicaA(m));
        assertEquals(m == MotivoDevolucion.ARREPENTIMIENTO, retencion.aplicaA(m));
    }

    @ParameterizedTest
    @CsvSource({"46800.00, 42120.00", "18400.00, 16560.00", "99.99, 89.99", "0.05, 0.05", "0.00, 0.00"})
    void retieneEl10PorCientoRedondeandoACentavos(String pagado, String devuelve) {
        assertEquals(new BigDecimal(devuelve), retencion.calcular(new BigDecimal(pagado)));
    }

    @ParameterizedTest
    @CsvSource({"46800.00", "0.01"})
    void elTotalDevuelveLoPagado(String pagado) {
        assertEquals(0, new BigDecimal(pagado).compareTo(total.calcular(new BigDecimal(pagado))));
    }
}
