package ar.edu.uade.repuestos.mensajeria;

import ar.edu.uade.repuestos.datos.DevolucionRepository;
import ar.edu.uade.repuestos.modelo.Devolucion;
import jakarta.jms.JMSException;
import jakarta.jms.MapMessage;
import jakarta.jms.Message;
import java.util.Optional;
import java.util.logging.Logger;

final class EventoDevolucion {

    private static final Logger LOG = Logger.getLogger(EventoDevolucion.class.getName());

    private EventoDevolucion() {
    }

    // Si la devolucion no existe se descarta el mensaje, asi no queda reintentando para siempre
    static Optional<Devolucion> devolucionDe(Message mensaje, DevolucionRepository repositorio) {
        try {
            long id = ((MapMessage) mensaje).getLong("idDevolucion");
            Optional<Devolucion> devolucion = repositorio.porId(id);
            if (devolucion.isEmpty()) {
                LOG.warning(() -> "[TOPICO] llego la devolucion " + id + " pero no existe, se descarta");
            }
            return devolucion;
        } catch (JMSException e) {
            throw new IllegalStateException("No se pudo leer el mensaje del topico", e);
        }
    }
}
