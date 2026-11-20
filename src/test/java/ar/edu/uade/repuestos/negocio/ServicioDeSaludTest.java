package ar.edu.uade.repuestos.negocio;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.jms.ConnectionFactory;
import jakarta.jms.JMSException;
import jakarta.jms.Session;
import jakarta.jms.TemporaryQueue;
import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

// el chequeo de salud prueba la base y el broker por separado, y cierra lo que abre
class ServicioDeSaludTest {

    private DataSource base;
    private ConnectionFactory broker;
    private Connection conexionBase;
    private jakarta.jms.Connection conexionBroker;
    private Session sesion;
    private TemporaryQueue colaTemporal;
    private ServicioDeSalud salud;

    @BeforeEach
    void preparar() throws Exception {
        base = mock(DataSource.class);
        broker = mock(ConnectionFactory.class);
        conexionBase = mock(Connection.class);
        conexionBroker = mock(jakarta.jms.Connection.class);
        when(base.getConnection()).thenReturn(conexionBase);
        when(conexionBase.isValid(2)).thenReturn(true);
        when(broker.createConnection()).thenReturn(conexionBroker);
        sesion = mock(Session.class);
        colaTemporal = mock(TemporaryQueue.class);
        when(conexionBroker.createSession(false, Session.AUTO_ACKNOWLEDGE)).thenReturn(sesion);
        when(sesion.createTemporaryQueue()).thenReturn(colaTemporal);
        salud = new ServicioDeSalud(base, broker);
    }

    @Test
    void conTodoAndandoEstaBienYCierraLasConexiones() throws Exception {
        ServicioDeSalud.Estado estado = salud.revisar();
        assertTrue(estado.base());
        assertTrue(estado.broker());
        assertTrue(estado.todoBien());
        verify(conexionBase).close();
        verify(colaTemporal).delete();
        verify(sesion).close();
        verify(conexionBroker).close();
    }

    // El pool puede dar una conexion aunque Artemis este apagado: lo que falla es hablar con el broker
    @Test
    void siElBrokerNoContestaAlCrearLaColaTemporalEstaCaido() throws Exception {
        when(sesion.createTemporaryQueue()).thenThrow(new JMSException("AMQ219014: Timed out"));
        assertFalse(salud.revisar().broker());
        verify(conexionBroker).close();
    }

    @Test
    void conLaBaseCaidaLoDiceYElBrokerSigueUp() throws Exception {
        when(base.getConnection()).thenThrow(new SQLException("Communications link failure"));
        ServicioDeSalud.Estado estado = salud.revisar();
        assertFalse(estado.base());
        assertTrue(estado.broker());
        assertFalse(estado.todoBien());
    }

    @Test
    void unaConexionInvalidaCuentaComoBaseCaida() throws Exception {
        when(conexionBase.isValid(2)).thenReturn(false);
        assertFalse(salud.revisar().base());
    }

    @Test
    void conElBrokerCaidoLoDice() throws Exception {
        when(broker.createConnection()).thenThrow(new JMSException("AMQ219007: Cannot connect"));
        ServicioDeSalud.Estado estado = salud.revisar();
        assertTrue(estado.base());
        assertFalse(estado.broker());
        assertFalse(estado.todoBien());
    }

    @Test
    void unErrorInesperadoTambienCuentaComoCaido() throws Exception {
        when(base.getConnection()).thenThrow(new IllegalStateException("pool cerrado"));
        when(broker.createConnection()).thenThrow(new IllegalStateException("adaptador caido"));
        ServicioDeSalud.Estado estado = salud.revisar();
        assertFalse(estado.base());
        assertFalse(estado.broker());
    }
}
