package ar.edu.uade.repuestos.negocio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ar.edu.uade.repuestos.datos.DevolucionRepository;
import ar.edu.uade.repuestos.integracion.ClienteProveedor;
import ar.edu.uade.repuestos.integracion.GarantiaNoValidaException;
import ar.edu.uade.repuestos.integracion.ProveedorNoDisponibleException;
import ar.edu.uade.repuestos.modelo.Devolucion;
import ar.edu.uade.repuestos.modelo.EstadoDevolucion;
import ar.edu.uade.repuestos.modelo.EstadoVenta;
import ar.edu.uade.repuestos.modelo.MotivoDevolucion;
import ar.edu.uade.repuestos.modelo.Venta;
import ar.edu.uade.repuestos.util.MensajeriaNoDisponibleException;
import jakarta.jms.JMSContext;
import jakarta.jms.JMSProducer;
import jakarta.jms.JMSRuntimeException;
import jakarta.jms.Queue;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ValidadorDeGarantiasTest {

    @Mock ClienteProveedor proveedor;
    @Mock DevolucionRepository devoluciones;
    @Mock ServicioDeNotificaciones notificaciones;
    @Mock JMSContext jms;
    @Mock JMSProducer productor;
    @Mock Queue cola;
    @InjectMocks ValidadorDeGarantias validador;

    private Devolucion pendiente;

    @BeforeEach
    void preparar() {
        Venta v = Datos.venta(9L, Datos.ana(), 2, EstadoVenta.PAGADA);
        pendiente = Datos.devolucion(4L, v, MotivoDevolucion.NO_COMPATIBLE);
        when(devoluciones.porId(4L)).thenReturn(Optional.of(pendiente));
        when(jms.createProducer()).thenReturn(productor);
        when(productor.setDeliveryDelay(anyLong())).thenReturn(productor);
    }

    private void proveedorCaido() {
        when(proveedor.validarGarantia(anyString(), any(), anyInt(), anyString(), anyString()))
                .thenThrow(new ProveedorNoDisponibleException("timeout"));
    }

    @Test
    void lePasaAlProveedorLosDatosDeLaCompra() {
        Venta v = pendiente.getVenta();
        when(proveedor.validarGarantia("CUB-29-210", v.getFecha().toLocalDate(), 2, "NO_COMPATIBLE", "ORD-000009"))
                .thenReturn("RMA-2026-00050");
        assertEquals("RMA-2026-00050", validador.consultar(v, MotivoDevolucion.NO_COMPATIBLE));
    }

    @Test
    void encolarMandaElIdALaColaConDosMinutosDeEspera() {
        validador.encolar(pendiente);
        verify(productor).setDeliveryDelay(120_000L);
        verify(productor).send(cola, Map.<String, Object>of("idDevolucion", 4L));
    }

    @Test
    void conElBrokerCaidoNoSeEncolaYAvisa() {
        when(jms.createProducer()).thenThrow(new JMSRuntimeException("AMQ219007: Cannot connect"));
        assertThrows(MensajeriaNoDisponibleException.class, () -> validador.encolar(pendiente));
    }

    @Test
    void cuandoElProveedorVuelvePasaAlModerador() {
        when(proveedor.validarGarantia(anyString(), any(), anyInt(), anyString(), anyString())).thenReturn("RMA-2026-00051");
        validador.reintentar(4L);
        assertEquals(EstadoDevolucion.PENDIENTE, pendiente.getEstado());
        assertEquals("RMA-2026-00051", pendiente.getRma());
        verify(devoluciones).guardar(pendiente);
        verifyNoInteractions(jms);
    }

    @Test
    void siElProveedorLaRechazaQuedaRechazadaYSeAvisa() {
        when(proveedor.validarGarantia(anyString(), any(), anyInt(), anyString(), anyString()))
                .thenThrow(new GarantiaNoValidaException("FUERA_DE_PLAZO"));
        validador.reintentar(4L);
        assertEquals(EstadoDevolucion.RECHAZADA, pendiente.getEstado());
        verify(notificaciones).devolucionResuelta(pendiente);
        verifyNoInteractions(jms);
    }

    @Test
    void sinRespuestaSumaUnIntentoYVuelveALaCola() {
        proveedorCaido();
        validador.reintentar(4L);
        assertEquals(EstadoDevolucion.PENDIENTE_VALIDACION, pendiente.getEstado());
        assertEquals(1, pendiente.getIntentosValidacion());
        verify(productor).send(cola, Map.<String, Object>of("idDevolucion", 4L));
        verify(devoluciones).guardar(pendiente);
    }

    @Test
    void alQuintoIntentoPasaAlModeradorSinRmaYNoVuelveALaCola() {
        pendiente.setIntentosValidacion(ValidadorDeGarantias.MAX_INTENTOS - 1);
        proveedorCaido();
        validador.reintentar(4L);
        assertEquals(EstadoDevolucion.PENDIENTE, pendiente.getEstado());
        assertNull(pendiente.getRma());
        assertTrue(pendiente.isSinValidar());
        verifyNoInteractions(jms);
        verify(devoluciones).guardar(pendiente);
    }

    @Test
    void unaQueYaNoEsperaValidacionNoSeConsulta() {
        pendiente.garantiaValidada("RMA-2026-00001");
        validador.reintentar(4L);
        when(devoluciones.porId(99L)).thenReturn(Optional.empty());
        validador.reintentar(99L);
        verifyNoInteractions(proveedor, jms);
        verify(devoluciones, never()).guardar(any());
    }
}
