package ar.edu.uade.repuestos.negocio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ar.edu.uade.repuestos.datos.DevolucionRepository;
import ar.edu.uade.repuestos.datos.ResenaRepository;
import ar.edu.uade.repuestos.modelo.Devolucion;
import ar.edu.uade.repuestos.modelo.EstadoDevolucion;
import ar.edu.uade.repuestos.modelo.EstadoResena;
import ar.edu.uade.repuestos.modelo.EstadoVenta;
import ar.edu.uade.repuestos.modelo.MotivoDevolucion;
import ar.edu.uade.repuestos.modelo.Resena;
import ar.edu.uade.repuestos.util.MensajeriaNoDisponibleException;
import ar.edu.uade.repuestos.util.ReglaNegocioException;
import ar.edu.uade.repuestos.util.TransicionInvalidaException;
import jakarta.annotation.security.RolesAllowed;
import jakarta.jms.JMSContext;
import jakarta.jms.JMSProducer;
import jakarta.jms.JMSRuntimeException;
import jakarta.jms.Topic;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
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
class ServicioDeModeracionTest {

    @Mock ResenaRepository resenas;
    @Mock DevolucionRepository devoluciones;
    @Mock ServicioDeReembolsos reembolsos;
    @Mock ServicioDeNotificaciones notificaciones;
    @Mock JMSContext jms;
    @Mock JMSProducer productor;
    @Mock Topic topico;
    @InjectMocks ServicioDeModeracion servicio;

    private Devolucion devolucion;

    @BeforeEach
    void preparar() {
        devolucion = Datos.devolucion(7L, Datos.venta(9L, Datos.ana(), 1, EstadoVenta.PAGADA), MotivoDevolucion.ARREPENTIMIENTO);
        devolucion.garantiaValidada("RMA-2026-00010");
        when(devoluciones.porId(7L)).thenReturn(Optional.of(devolucion));
        when(jms.createProducer()).thenReturn(productor);
        when(productor.setProperty(anyString(), anyString())).thenReturn(productor);
    }

    // SEC-04: el rol lo exige el EJB, no solo la pantalla. Un metodo publico nuevo sin @RolesAllowed rompe este test
    @Test
    void todosLosMetodosPublicosExigenElRolModerador() {
        Method[] publicos = Arrays.stream(ServicioDeModeracion.class.getDeclaredMethods())
                .filter(m -> Modifier.isPublic(m.getModifiers()) && !m.isSynthetic())
                .toArray(Method[]::new);
        assertTrue(publicos.length >= 6);
        for (Method m : publicos) {
            RolesAllowed roles = m.getAnnotation(RolesAllowed.class);
            assertTrue(roles != null && Arrays.asList(roles.value()).equals(List.of("MODERADOR")), m.getName());
        }
    }

    @Test
    void aprobarCalculaElMontoYPublicaSoloElIdConElEstado() {
        when(reembolsos.calcularMonto(devolucion)).thenReturn(new BigDecimal("42120.00"));

        servicio.aprobarDevolucion(7L);

        assertEquals(EstadoDevolucion.APROBADA, devolucion.getEstado());
        assertEquals(0, new BigDecimal("42120.00").compareTo(devolucion.getMontoReembolso()));
        verify(devoluciones).guardar(devolucion);
        verify(productor).setProperty("estado", "APROBADA");
        verify(productor).send(topico, Map.<String, Object>of("idDevolucion", 7L));
    }

    @Test
    void rechazarPublicaConEstadoRechazada() {
        servicio.rechazarDevolucion(7L);
        assertEquals(EstadoDevolucion.RECHAZADA, devolucion.getEstado());
        assertNull(devolucion.getMontoReembolso());
        verify(productor).setProperty("estado", "RECHAZADA");
    }

    // Broker caido: el error sale como regla de negocio (rollback) y el moderador ve el aviso
    @Test
    void conElBrokerCaidoLaOperacionVuelveAtras() {
        when(reembolsos.calcularMonto(devolucion)).thenReturn(BigDecimal.TEN);
        when(jms.createProducer()).thenThrow(new JMSRuntimeException("AMQ219007: Cannot connect"));
        var e = assertThrows(MensajeriaNoDisponibleException.class, () -> servicio.aprobarDevolucion(7L));
        assertEquals(true, e.getMessage().contains("No se cambió nada"));
    }

    @Test
    void unaDevolucionYaResueltaNoSeApruebaNiSePublica() {
        devolucion.rechazar();
        assertThrows(TransicionInvalidaException.class, () -> servicio.aprobarDevolucion(7L));
        verify(jms, never()).createProducer();
    }

    @Test
    void devolucionOResenaInexistente() {
        when(devoluciones.porId(99L)).thenReturn(Optional.empty());
        when(resenas.porId(99L)).thenReturn(Optional.empty());
        assertEquals("La solicitud no existe.", assertThrows(ReglaNegocioException.class, () -> servicio.aprobarDevolucion(99L)).getMessage());
        assertEquals("La resena no existe.", assertThrows(ReglaNegocioException.class, () -> servicio.aprobarResena(99L)).getMessage());
    }

    @Test
    void aprobarYRechazarResenas() {
        Resena r = new Resena(Datos.ana(), Datos.cubierta(), 4, "t", "x");
        Resena otra = new Resena(Datos.ana(), Datos.cubierta(), 2, "t", "x");
        when(resenas.porId(1L)).thenReturn(Optional.of(r));
        when(resenas.porId(2L)).thenReturn(Optional.of(otra));

        servicio.aprobarResena(1L);
        servicio.rechazarResena(2L, "Lenguaje ofensivo");

        assertEquals(EstadoResena.APROBADA, r.getEstado());
        assertEquals(EstadoResena.RECHAZADA, otra.getEstado());
        assertEquals("Lenguaje ofensivo", otra.getMotivoRechazo());
        verify(notificaciones).resenaResuelta(r);
        verify(notificaciones).resenaResuelta(otra);
        verify(resenas).guardar(r);
        verify(resenas).guardar(otra);
    }

    @Test
    void unaResenaYaResueltaNoSeVuelveAModerar() {
        Resena r = new Resena(Datos.ana(), Datos.cubierta(), 4, "t", "x");
        r.aprobar();
        when(resenas.porId(1L)).thenReturn(Optional.of(r));
        assertThrows(ReglaNegocioException.class, () -> servicio.rechazarResena(1L, "x"));
        assertEquals(EstadoResena.APROBADA, r.getEstado());
        verify(resenas, never()).guardar(any());
    }

    @Test
    void colasPendientes() {
        Resena r = new Resena(Datos.ana(), Datos.cubierta(), 4, "t", "x");
        when(resenas.porEstado(EstadoResena.PENDIENTE)).thenReturn(List.of(r));
        when(devoluciones.porEstado(EstadoDevolucion.PENDIENTE)).thenReturn(List.of(devolucion));
        assertEquals(List.of(r), servicio.resenasPendientes());
        assertEquals(List.of(devolucion), servicio.devolucionesPendientes());
    }
}
