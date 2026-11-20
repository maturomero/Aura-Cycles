package ar.edu.uade.repuestos.mensajeria;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ar.edu.uade.repuestos.datos.DevolucionRepository;
import jakarta.jms.JMSException;
import jakarta.jms.MapMessage;
import java.util.Optional;
import org.junit.jupiter.api.Test;

// lo que cada consumidor lee del evento. Un mensaje viejo, sin version ni correlationId, se procesa igual
class EventoDevolucionTest {

    private static MapMessage evento(long id, String version, String correlationId) throws JMSException {
        MapMessage m = mock(MapMessage.class);
        when(m.getLong("idDevolucion")).thenReturn(id);
        when(m.getStringProperty("version")).thenReturn(version);
        when(m.getJMSCorrelationID()).thenReturn(correlationId);
        return m;
    }

    @Test
    void leeElIdLaVersionYElCorrelationId() throws JMSException {
        MapMessage m = evento(7L, "1", "c0ffee00-0000-4000-8000-000000000007");
        assertEquals(7L, EventoDevolucion.idDe(m));
        assertEquals("v1", EventoDevolucion.versionDe(m));
        assertEquals("c0ffee00-0000-4000-8000-000000000007", EventoDevolucion.correlationIdDe(m));
    }

    @Test
    void unMensajeViejoSinVersionNiCorrelationIdSeProcesaIgual() throws JMSException {
        MapMessage m = evento(8L, null, null);
        assertEquals("sin version", EventoDevolucion.versionDe(m));
        assertEquals("sin correlationId", EventoDevolucion.correlationIdDe(m));
    }

    @Test
    void siNoSePuedenLeerLasPropiedadesNoCortaElConsumo() throws JMSException {
        MapMessage m = mock(MapMessage.class);
        when(m.getStringProperty("version")).thenThrow(new JMSException("roto"));
        when(m.getJMSCorrelationID()).thenThrow(new JMSException("roto"));
        assertEquals("sin version", EventoDevolucion.versionDe(m));
        assertEquals("sin correlationId", EventoDevolucion.correlationIdDe(m));
    }

    @Test
    void unaDevolucionQueNoExisteSeDescarta() throws JMSException {
        DevolucionRepository repositorio = mock(DevolucionRepository.class);
        when(repositorio.porIdBloqueado(404L)).thenReturn(Optional.empty());
        assertTrue(EventoDevolucion.devolucionDe(evento(404L, "1", "x"), repositorio, "stock").isEmpty());
    }
}
