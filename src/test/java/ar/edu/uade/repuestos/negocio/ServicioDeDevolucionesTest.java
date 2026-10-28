package ar.edu.uade.repuestos.negocio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ar.edu.uade.repuestos.datos.DevolucionRepository;
import ar.edu.uade.repuestos.integracion.GarantiaNoValidaException;
import ar.edu.uade.repuestos.integracion.ProveedorNoDisponibleException;
import ar.edu.uade.repuestos.modelo.Devolucion;
import ar.edu.uade.repuestos.modelo.EstadoDevolucion;
import ar.edu.uade.repuestos.modelo.EstadoVenta;
import ar.edu.uade.repuestos.modelo.MotivoDevolucion;
import ar.edu.uade.repuestos.modelo.Venta;
import ar.edu.uade.repuestos.util.ReglaNegocioException;
import jakarta.ejb.SessionContext;
import java.security.Principal;
import java.util.List;
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
class ServicioDeDevolucionesTest {

    @Mock DevolucionRepository devoluciones;
    @Mock ServicioDeVentas ventas;
    @Mock ServicioDeNotificaciones notificaciones;
    @Mock ValidadorDeGarantias garantias;
    @Mock SessionContext contexto;
    @InjectMocks ServicioDeDevoluciones servicio;

    private Venta venta;

    @BeforeEach
    void preparar() {
        venta = Datos.pagadaHace(4);
        when(ventas.porId(9L)).thenReturn(venta);
        logueado("ana@mail.com");
        when(devoluciones.guardar(any())).thenAnswer(i -> {
            Devolucion d = i.getArgument(0);
            d.setId(12L);
            return d;
        });
    }

    // SEC-05: la compra de otro da el mismo error que una que no existe
    @Test
    void unaCompraAjenaNoSeAbre() {
        logueado("carlos@mail.com");
        assertEquals("La compra no existe.", assertThrows(ReglaNegocioException.class, () -> servicio.seleccionarCompra(9L)).getMessage());
        assertFalse(servicio.hayBorrador());
        assertNull(servicio.getVentaSeleccionada());
    }

    @Test
    void laCompraPropiaSeAbre() {
        servicio.seleccionarCompra(9L);
        assertTrue(servicio.hayBorrador());
        assertSame(venta, servicio.getVentaSeleccionada());
    }

    @Test
    void unaCompraSinPagarNoSeDevuelve() {
        venta.setEstado(EstadoVenta.PENDIENTE_PAGO);
        assertThrows(ReglaNegocioException.class, () -> servicio.seleccionarCompra(9L));
    }

    @Test
    void unaSolaDevolucionPorCompra() {
        when(devoluciones.existeParaVenta(9L)).thenReturn(true);
        assertThrows(ReglaNegocioException.class, () -> servicio.seleccionarCompra(9L));
    }

    // Borde del plazo: 30 dias todavia se puede, 31 ya no
    @ParameterizedTest
    @CsvSource({"30, true", "31, false"})
    void plazoDeTreintaDias(int dias, boolean puede) {
        venta.setFecha(venta.getFecha().plusDays(4).minusDays(dias));
        if (puede) {
            servicio.seleccionarCompra(9L);
            assertTrue(servicio.hayBorrador());
        } else {
            assertThrows(ReglaNegocioException.class, () -> servicio.seleccionarCompra(9L));
        }
        assertEquals(puede, servicio.sePuedeDevolver(venta));
    }

    @Test
    void sePuedeDevolverSoloSiEstaPagadaYNoTieneOtra() {
        assertTrue(servicio.sePuedeDevolver(venta));
        when(devoluciones.existeParaVenta(9L)).thenReturn(true);
        assertFalse(servicio.sePuedeDevolver(venta));
        when(devoluciones.existeParaVenta(9L)).thenReturn(false);
        venta.setEstado(EstadoVenta.RECHAZADA);
        assertFalse(servicio.sePuedeDevolver(venta));
    }

    @Test
    void cargarMotivo() {
        assertThrows(ReglaNegocioException.class, () -> servicio.cargarMotivo(MotivoDevolucion.NO_COMPATIBLE, ""));
        servicio.seleccionarCompra(9L);
        assertThrows(ReglaNegocioException.class, () -> servicio.cargarMotivo(null, "x"));
        assertThrows(ReglaNegocioException.class, () -> servicio.cargarMotivo(MotivoDevolucion.DEFECTUOSO, "  "));
        servicio.cargarMotivo(MotivoDevolucion.NO_COMPATIBLE, "");
        assertEquals(MotivoDevolucion.NO_COMPATIBLE, servicio.getMotivo());
        assertEquals("", servicio.getComentario());
    }

    @Test
    void conGarantiaValidaQuedaParaElModeradorConRma() {
        servicio.seleccionarCompra(9L);
        servicio.cargarMotivo(MotivoDevolucion.DEFECTUOSO, "Se pincho");
        when(garantias.consultar(venta, MotivoDevolucion.DEFECTUOSO)).thenReturn("RMA-2026-00042");

        Devolucion d = servicio.confirmar();

        assertEquals(EstadoDevolucion.PENDIENTE, d.getEstado());
        assertEquals("RMA-2026-00042", d.getRma());
        verify(notificaciones).devolucionRecibida(d);
    }

    // Proveedor caido: no se pierde la solicitud, queda esperando la validacion
    @Test
    void conElProveedorCaidoQuedaPendienteDeValidacion() {
        servicio.seleccionarCompra(9L);
        servicio.cargarMotivo(MotivoDevolucion.NO_COMPATIBLE, "");
        when(garantias.consultar(any(), any())).thenThrow(new ProveedorNoDisponibleException("timeout"));

        Devolucion d = servicio.confirmar();

        assertEquals(EstadoDevolucion.PENDIENTE_VALIDACION, d.getEstado());
        assertNull(d.getRma());
        verify(devoluciones).guardar(d);
        verify(notificaciones).devolucionRecibida(d);
    }

    @Test
    void siElProveedorRechazaLaGarantiaNoSeCreaNada() {
        servicio.seleccionarCompra(9L);
        servicio.cargarMotivo(MotivoDevolucion.DEFECTUOSO, "x");
        when(garantias.consultar(any(), any())).thenThrow(new GarantiaNoValidaException("CONSUMIBLE"));
        assertThrows(GarantiaNoValidaException.class, () -> servicio.confirmar());
        verify(devoluciones, never()).guardar(any());
    }

    @Test
    void confirmarSinCompraOSinMotivo() {
        assertThrows(ReglaNegocioException.class, () -> servicio.confirmar());
        servicio.seleccionarCompra(9L);
        assertEquals("Falta elegir el motivo.", assertThrows(ReglaNegocioException.class, () -> servicio.confirmar()).getMessage());
    }

    @Test
    void finalizarLimpiaElBorradorYConsultas() {
        servicio.seleccionarCompra(9L);
        servicio.cargarMotivo(MotivoDevolucion.NO_COMPATIBLE, "x");
        servicio.finalizar();
        assertFalse(servicio.hayBorrador());
        assertNull(servicio.getMotivo());
        Devolucion d = Datos.devolucion(3L, venta, MotivoDevolucion.NO_COMPATIBLE);
        when(devoluciones.porUsuario(1L)).thenReturn(List.of(d));
        when(devoluciones.porEstado(EstadoDevolucion.PENDIENTE)).thenReturn(List.of(d));
        assertEquals(List.of(d), servicio.misDevoluciones(1L));
        assertEquals(List.of(d), servicio.pendientes());
        servicio.iniciar();
        assertTrue(servicio.getIniciadoEn() != null);
    }

    private void logueado(String email) {
        Principal p = () -> email;
        when(contexto.getCallerPrincipal()).thenReturn(p);
    }
}
