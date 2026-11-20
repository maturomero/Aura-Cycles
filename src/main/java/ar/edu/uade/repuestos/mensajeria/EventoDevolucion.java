package ar.edu.uade.repuestos.mensajeria;

import ar.edu.uade.repuestos.datos.DevolucionRepository;
import ar.edu.uade.repuestos.modelo.Devolucion;
import ar.edu.uade.repuestos.negocio.ServicioDeModeracion;
import jakarta.jms.JMSException;
import jakarta.jms.MapMessage;
import jakarta.jms.Message;
import java.util.Optional;
import java.util.logging.Logger;

final class EventoDevolucion {

    private static final Logger LOG = Logger.getLogger(EventoDevolucion.class.getName());

    private EventoDevolucion() {
    }

    // El topico y la cola mandan lo mismo: un MapMessage con el id de la devolucion
    static long idDe(Message mensaje) {
        try {
            return ((MapMessage) mensaje).getLong("idDevolucion");
        } catch (JMSException e) {
            throw new IllegalStateException("No se pudo leer el id de la devolucion del mensaje", e);
        }
    }

    // Version del evento y correlationId (M3). Un mensaje viejo, publicado antes de agregarlos, sigue sirviendo:
    // se lo procesa igual y en el log figura sin version
    static String versionDe(Message mensaje) {
        try {
            String version = mensaje.getStringProperty(ServicioDeModeracion.PROPIEDAD_VERSION);
            return version == null ? "sin version" : "v" + version;
        } catch (JMSException e) {
            return "sin version";
        }
    }

    static String correlationIdDe(Message mensaje) {
        try {
            String id = mensaje.getJMSCorrelationID();
            return id == null ? "sin correlationId" : id;
        } catch (JMSException e) {
            return "sin correlationId";
        }
    }

    // Si la devolucion no existe se descarta el mensaje, asi no queda reintentando para siempre.
    // Se bloquea la fila: los tres consumidores tocan la misma devolucion y asi ninguno pisa lo que guardo otro
    static Optional<Devolucion> devolucionDe(Message mensaje, DevolucionRepository repositorio, String consumidor) {
        long id = idDe(mensaje);
        String traza = versionDe(mensaje) + " correlationId=" + correlationIdDe(mensaje);
        LOG.info(() -> "[TOPICO] " + consumidor + " recibio la devolucion " + id + " " + traza);
        Optional<Devolucion> devolucion = repositorio.porIdBloqueado(id);
        if (devolucion.isEmpty()) {
            LOG.warning(() -> "[TOPICO] " + consumidor + ": la devolucion " + id + " no existe, se descarta (" + traza + ")");
        }
        return devolucion;
    }
}
