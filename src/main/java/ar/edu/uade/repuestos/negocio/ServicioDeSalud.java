package ar.edu.uade.repuestos.negocio;

import jakarta.annotation.Resource;
import jakarta.ejb.Stateless;
import jakarta.ejb.TransactionAttribute;
import jakarta.ejb.TransactionAttributeType;
import jakarta.jms.ConnectionFactory;
import jakarta.jms.JMSException;
import jakarta.jms.Session;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.logging.Logger;
import javax.sql.DataSource;

/**
 * Chequeo de salud de la app: prueba de verdad la base y el broker, no solo que el proceso este vivo.
 * Sin transaccion: es una consulta de diagnostico y no tiene que quedar enganchada a nada.
 */
@Stateless
@TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
public class ServicioDeSalud {

    private static final Logger LOG = Logger.getLogger(ServicioDeSalud.class.getName());
    private static final int SEGUNDOS_PARA_LA_BASE = 2;

    @Resource(lookup = "java:/RepuestosDS")
    private DataSource base;

    // La misma fabrica con la que se publica en el topico (ver docker/wildfly/configurar.cli)
    @Resource(lookup = "java:/RemoteJmsXA")
    private ConnectionFactory broker;

    public ServicioDeSalud() {
    }

    ServicioDeSalud(DataSource base, ConnectionFactory broker) {
        this.base = base;
        this.broker = broker;
    }

    public record Estado(boolean base, boolean broker) {
        public boolean todoBien() {
            return base && broker;
        }
    }

    public Estado revisar() {
        return new Estado(baseResponde(), brokerResponde());
    }

    private boolean baseResponde() {
        try (Connection c = base.getConnection()) {
            return c.isValid(SEGUNDOS_PARA_LA_BASE);
        } catch (SQLException | RuntimeException e) {
            LOG.warning(() -> "[SALUD] la base no responde (" + e.getClass().getSimpleName() + ")");
            return false;
        }
    }

    // Abrir la conexion no alcanza: la fabrica es un pool y puede devolver una conexion vieja aunque el broker
    // este apagado. Crear una cola temporal obliga a hablar con Artemis; se borra al cerrar la conexion
    private boolean brokerResponde() {
        try (jakarta.jms.Connection c = broker.createConnection();
             Session sesion = c.createSession(false, Session.AUTO_ACKNOWLEDGE)) {
            sesion.createTemporaryQueue().delete();
            return true;
        } catch (JMSException | RuntimeException e) {
            LOG.warning(() -> "[SALUD] el broker no responde (" + e.getClass().getSimpleName() + ")");
            return false;
        }
    }
}
