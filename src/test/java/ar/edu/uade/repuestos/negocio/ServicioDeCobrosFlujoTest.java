package ar.edu.uade.repuestos.negocio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ar.edu.uade.repuestos.datos.VentaRepository;
import ar.edu.uade.repuestos.integracion.ClienteMercadoPago;
import ar.edu.uade.repuestos.integracion.ConfiguracionMercadoPago;
import ar.edu.uade.repuestos.integracion.MercadoPagoNoDisponibleException;
import ar.edu.uade.repuestos.integracion.PagoMP;
import ar.edu.uade.repuestos.integracion.PreferenciaMP;
import ar.edu.uade.repuestos.modelo.EstadoVenta;
import ar.edu.uade.repuestos.modelo.Venta;
import ar.edu.uade.repuestos.util.ReglaNegocioException;
import jakarta.ejb.SessionContext;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

// El recorrido del cobro contra un Mercado Pago de mentira (mock)
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ServicioDeCobrosFlujoTest {

    @Mock VentaRepository ventas;
    @Mock ServicioDeVentas servicioDeVentas;
    @Mock ClienteMercadoPago mp;
    @Mock SessionContext contexto;
    @InjectMocks ServicioDeCobros cobros;

    private Venta venta;

    @BeforeEach
    void preparar() {
        venta = Datos.venta(9L, Datos.ana(), 2, EstadoVenta.PENDIENTE_PAGO);
        when(ventas.porId(9L)).thenReturn(Optional.of(venta));
        when(ventas.porIdBloqueado(9L)).thenReturn(Optional.of(venta));
        when(ventas.guardar(any())).thenAnswer(i -> i.getArgument(0));
        when(mp.configuracion()).thenReturn(new ConfiguracionMercadoPago("http://mp", "TEST-x",
                "https://localhost:8443/repuestos", ConfiguracionMercadoPago.TIMEOUT));
    }

    @Test
    void iniciarPagoArmaLaPreferenciaConLaReferenciaYElPrecioUnitario() {
        when(mp.crearPreferencia(eq(venta.getReferenciaPago()), eq("CUB-29-210"), anyString(), eq(2),
                eq(new BigDecimal("46800.00")), eq("https://localhost:8443/repuestos/pago.xhtml?venta=9"), any(), any()))
                .thenReturn(new PreferenciaMP("pref-1", "http://mp/checkout?pref=pref-1"));

        assertEquals("http://mp/checkout?pref=pref-1", cobros.iniciarPago(9L, 1L));
        assertEquals("pref-1", venta.getMpPreferenciaId());
        verify(mp, never()).buscarPagos(any());
    }

    // SEC: la compra de otro cliente da lo mismo que una que no existe
    @Test
    void laCompraDeOtroNoExiste() {
        assertEquals("La compra no existe.", assertThrows(ReglaNegocioException.class, () -> cobros.iniciarPago(9L, 2L)).getMessage());
        assertThrows(ReglaNegocioException.class, () -> cobros.confirmar(9L, 2L, "1"));
        verifyNoInteractions(mp);
    }

    @Test
    void unaVentaQueYaNoEsperaPagoNoVuelveAMercadoPago() {
        venta.marcarPagada("1000000001");
        assertThrows(ReglaNegocioException.class, () -> cobros.iniciarPago(9L, 1L));
        assertSame(venta, cobros.confirmar(9L, 1L, "1000000001"));
        verify(mp, never()).obtenerPago(any());
    }

    @Test
    void laReservaVencidaNoSePaga() {
        venta.setFecha(LocalDateTime.now().minusMinutes(31));
        assertTrue(assertThrows(ReglaNegocioException.class, () -> cobros.iniciarPago(9L, 1L)).getMessage().contains("vencio"));
    }

    // Ya la mando a pagar antes y pago, pero no volvio: no se le cobra de nuevo
    @Test
    void siYaPagoNoSeCreaOtraPreferencia() {
        venta.setMpPreferenciaId("pref-1");
        when(mp.buscarPagos(venta.getReferenciaPago())).thenReturn(List.of(aprobado()));
        assertNull(cobros.iniciarPago(9L, 1L));
        assertEquals(EstadoVenta.PAGADA, venta.getEstado());
        verify(mp, never()).crearPreferencia(any(), any(), any(), anyInt(), any(), any(), any(), any());
    }

    @Test
    void siLaMandoAPagarYNoPagoSeCreaOtraPreferencia() {
        venta.setMpPreferenciaId("pref-1");
        when(mp.buscarPagos(any())).thenReturn(List.of());
        when(mp.crearPreferencia(any(), any(), any(), anyInt(), any(), any(), any(), any()))
                .thenReturn(new PreferenciaMP("pref-2", "http://mp/checkout?pref=pref-2"));
        assertEquals("http://mp/checkout?pref=pref-2", cobros.iniciarPago(9L, 1L));
        assertEquals("pref-2", venta.getMpPreferenciaId());
    }

    @Test
    void confirmarConElPagoAprobadoLaMarcaPagada() {
        when(mp.obtenerPago("1000000001")).thenReturn(Optional.of(aprobado()));
        assertEquals(EstadoVenta.PAGADA, cobros.confirmar(9L, 1L, "1000000001").getEstado());
        assertEquals("1000000001", venta.getMpPagoId());
        verify(servicioDeVentas, never()).reponerStockDe(any());
    }

    @Test
    void confirmarUnRechazoDevuelveElStock() {
        when(mp.obtenerPago("1000000002")).thenReturn(Optional.of(
                new PagoMP("1000000002", "rejected", venta.getReferenciaPago(), venta.getImporte(), "ARS")));
        assertEquals(EstadoVenta.RECHAZADA, cobros.confirmar(9L, 1L, "1000000002").getEstado());
        verify(servicioDeVentas).reponerStockDe(venta);
    }

    // Sin payment_id en la URL (o "null"), se busca por la referencia
    @Test
    void sinPaymentIdSeBuscaPorReferencia() {
        when(mp.obtenerPago("null")).thenReturn(Optional.empty());
        when(mp.buscarPagos(venta.getReferenciaPago())).thenReturn(List.of());
        assertEquals(EstadoVenta.PENDIENTE_PAGO, cobros.confirmar(9L, 1L, "null").getEstado());
    }

    @Test
    void confirmarConMercadoPagoCaidoNoTocaNada() {
        when(mp.obtenerPago(any())).thenThrow(new MercadoPagoNoDisponibleException("timeout"));
        assertThrows(MercadoPagoNoDisponibleException.class, () -> cobros.confirmar(9L, 1L, "1"));
        assertEquals(EstadoVenta.PENDIENTE_PAGO, venta.getEstado());
        verify(ventas, never()).guardar(any());
    }

    @Test
    void cerrarUnaReservaVencidaSinPreferenciaDevuelveElStock() {
        venta.setFecha(LocalDateTime.now().minusMinutes(40));
        cobros.cerrarReserva(9L);
        assertEquals(EstadoVenta.VENCIDA, venta.getEstado());
        verify(servicioDeVentas).reponerStockDe(venta);
        verifyNoInteractions(mp);
    }

    @Test
    void siPagoAntesDeVencerQuedaPagadaYNoVencida() {
        venta.setFecha(LocalDateTime.now().minusMinutes(40));
        venta.setMpPreferenciaId("pref-1");
        when(mp.buscarPagos(any())).thenReturn(List.of(aprobado()));
        cobros.cerrarReserva(9L);
        assertEquals(EstadoVenta.PAGADA, venta.getEstado());
        verify(servicioDeVentas, never()).reponerStockDe(any());
    }

    @Test
    void conUnPagoEnCursoNoSeVence() {
        venta.setFecha(LocalDateTime.now().minusMinutes(40));
        venta.setMpPreferenciaId("pref-1");
        when(mp.buscarPagos(any())).thenReturn(List.of(new PagoMP("1", "in_process", venta.getReferenciaPago(), venta.getImporte(), "ARS")));
        cobros.cerrarReserva(9L);
        assertEquals(EstadoVenta.PENDIENTE_PAGO, venta.getEstado());
    }

    // Aunque alguien llame cerrarReserva, una reserva reciente no se toca
    @Test
    void unaReservaQueTodaviaNoVencioNoSeCierra() {
        venta.setFecha(LocalDateTime.now().minusMinutes(10));
        cobros.cerrarReserva(9L);
        assertEquals(EstadoVenta.PENDIENTE_PAGO, venta.getEstado());
        verify(ventas, never()).guardar(any());
    }

    @Test
    void elTimerCortaSiMercadoPagoNoResponde() {
        Venta otra = Datos.venta(10L, Datos.ana(), 1, EstadoVenta.PENDIENTE_PAGO);
        when(ventas.pendientesDePagoAntesDe(any())).thenReturn(List.of(venta, otra));
        ServicioDeCobros proxy = org.mockito.Mockito.mock(ServicioDeCobros.class);
        when(contexto.getBusinessObject(ServicioDeCobros.class)).thenReturn(proxy);
        org.mockito.Mockito.doThrow(new MercadoPagoNoDisponibleException("x")).when(proxy).cerrarReserva(9L);

        cobros.vencerReservas();

        verify(proxy, times(1)).cerrarReserva(9L);
        verify(proxy, never()).cerrarReserva(10L);
    }

    @Test
    void elTimerSigueSiUnaVentaDaErrorDeNegocio() {
        Venta otra = Datos.venta(10L, Datos.ana(), 1, EstadoVenta.PENDIENTE_PAGO);
        when(ventas.pendientesDePagoAntesDe(any())).thenReturn(List.of(venta, otra));
        ServicioDeCobros proxy = org.mockito.Mockito.mock(ServicioDeCobros.class);
        when(contexto.getBusinessObject(ServicioDeCobros.class)).thenReturn(proxy);
        org.mockito.Mockito.doThrow(new ReglaNegocioException("monto distinto")).when(proxy).cerrarReserva(9L);

        cobros.vencerReservas();

        verify(proxy).cerrarReserva(10L);
    }

    private PagoMP aprobado() {
        return new PagoMP("1000000001", "approved", venta.getReferenciaPago(), venta.getImporte(), "ARS");
    }
}
