package ar.edu.uade.repuestos.negocio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ar.edu.uade.repuestos.integracion.ClienteMercadoPago;
import ar.edu.uade.repuestos.integracion.MercadoPagoNoDisponibleException;
import ar.edu.uade.repuestos.integracion.ReembolsoMP;
import ar.edu.uade.repuestos.modelo.Devolucion;
import ar.edu.uade.repuestos.modelo.EstadoDevolucion;
import ar.edu.uade.repuestos.modelo.EstadoVenta;
import ar.edu.uade.repuestos.modelo.MotivoDevolucion;
import ar.edu.uade.repuestos.modelo.Venta;
import ar.edu.uade.repuestos.negocio.reembolso.PoliticaDeReembolso;
import ar.edu.uade.repuestos.negocio.reembolso.ReembolsoConRetencion;
import ar.edu.uade.repuestos.negocio.reembolso.ReembolsoTotal;
import ar.edu.uade.repuestos.util.ReglaNegocioException;
import jakarta.enterprise.inject.Instance;
import java.math.BigDecimal;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ServicioDeReembolsosTest {

    @Mock Instance<PoliticaDeReembolso> politicas;
    @Mock ServicioDeNotificaciones notificaciones;
    @Mock ClienteMercadoPago mercadoPago;
    @InjectMocks ServicioDeReembolsos servicio;

    private Venta venta;

    @BeforeEach
    void preparar() {
        when(politicas.stream()).thenAnswer(i -> Stream.of(new ReembolsoTotal(), new ReembolsoConRetencion()));
        venta = Datos.venta(9L, Datos.ana(), 1, EstadoVenta.PAGADA);
        venta.setMpPagoId("1000000001");
    }

    // Strategy: el monto sale de la politica que corresponde al motivo
    @ParameterizedTest
    @CsvSource({
        "ARREPENTIMIENTO, 42120.00",
        "DEFECTUOSO, 46800.00",
        "MEDIDA_INCORRECTA, 46800.00",
        "NO_COMPATIBLE, 46800.00",
        "DISTINTO_AL_PUBLICADO, 46800.00"
    })
    void elMontoDependeDelMotivo(MotivoDevolucion motivo, String esperado) {
        assertEquals(0, new BigDecimal(esperado).compareTo(servicio.calcularMonto(Datos.devolucion(1L, venta, motivo))));
    }

    @Test
    void sinPoliticaOConDosParaElMismoMotivoCorta() {
        when(politicas.stream()).thenAnswer(i -> Stream.of(new ReembolsoTotal()));
        assertThrows(IllegalStateException.class, () -> servicio.calcularMonto(Datos.devolucion(1L, venta, MotivoDevolucion.ARREPENTIMIENTO)));
        when(politicas.stream()).thenAnswer(i -> Stream.of(new ReembolsoTotal(), new ReembolsoTotal()));
        assertThrows(IllegalStateException.class, () -> servicio.calcularMonto(Datos.devolucion(1L, venta, MotivoDevolucion.DEFECTUOSO)));
    }

    @Test
    void reembolsaPorMercadoPagoConClaveDeIdempotencia() {
        Devolucion d = aprobada(MotivoDevolucion.ARREPENTIMIENTO, "42120.00");
        String clave = "aura-" + venta.getReferenciaPago() + "-DEV-000007";
        when(mercadoPago.reembolsar("1000000001", new BigDecimal("42120.00"), clave))
                .thenReturn(new ReembolsoMP("5000001", "approved", new BigDecimal("42120.00")));

        servicio.emitir(d);

        assertEquals(EstadoDevolucion.REEMBOLSADA, d.getEstado());
        assertEquals("5000001", d.getMpReembolsoId());
        verify(notificaciones).reembolsoEmitido(d);
    }

    // Mensaje repetido: si ya esta reembolsada no se vuelve a llamar a Mercado Pago
    @Test
    void unaYaReembolsadaNoSeReembolsaDeNuevo() {
        Devolucion d = aprobada(MotivoDevolucion.DEFECTUOSO, "46800.00");
        d.reembolsar();
        servicio.emitir(d);
        verifyNoInteractions(mercadoPago);
        verify(notificaciones, never()).reembolsoEmitido(any());
    }

    @Test
    void unaCompraSinPagoEnMercadoPagoSeReembolsaAMano() {
        venta.setMpPagoId(null);
        Devolucion d = aprobada(MotivoDevolucion.DEFECTUOSO, "46800.00");
        servicio.emitir(d);
        assertEquals(EstadoDevolucion.REEMBOLSADA, d.getEstado());
        assertNull(d.getMpReembolsoId());
        verifyNoInteractions(mercadoPago);
    }

    // Mercado Pago caido: la excepcion sale (rollback, el broker reintenta) y la devolucion sigue APROBADA
    @Test
    void conMercadoPagoCaidoNoSeMarcaComoReembolsada() {
        Devolucion d = aprobada(MotivoDevolucion.DEFECTUOSO, "46800.00");
        when(mercadoPago.reembolsar(any(), any(), any())).thenThrow(new MercadoPagoNoDisponibleException("caido"));
        assertThrows(MercadoPagoNoDisponibleException.class, () -> servicio.emitir(d));
        assertEquals(EstadoDevolucion.APROBADA, d.getEstado());
        verify(notificaciones, never()).reembolsoEmitido(any());
    }

    @Test
    void sinMontoCalculadoNoSeEmite() {
        Devolucion d = Datos.devolucion(7L, venta, MotivoDevolucion.DEFECTUOSO);
        assertThrows(ReglaNegocioException.class, () -> servicio.emitir(d));
        verifyNoInteractions(mercadoPago);
    }

    private Devolucion aprobada(MotivoDevolucion motivo, String monto) {
        Devolucion d = Datos.devolucion(7L, venta, motivo);
        d.garantiaValidada("RMA-2026-00010");
        d.aprobar(new BigDecimal(monto));
        return d;
    }
}
