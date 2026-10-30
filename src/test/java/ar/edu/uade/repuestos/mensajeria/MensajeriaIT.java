package ar.edu.uade.repuestos.mensajeria;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ar.edu.uade.repuestos.datos.DevolucionRepository;
import ar.edu.uade.repuestos.modelo.Devolucion;
import ar.edu.uade.repuestos.negocio.ServicioDeNotificaciones;
import ar.edu.uade.repuestos.negocio.ServicioDeReembolsos;
import ar.edu.uade.repuestos.negocio.ServicioDeVentas;
import ar.edu.uade.repuestos.negocio.ValidadorDeGarantias;
import jakarta.jms.Connection;
import jakarta.jms.JMSContext;
import jakarta.jms.MapMessage;
import jakarta.jms.Message;
import jakarta.jms.MessageConsumer;
import jakarta.jms.Queue;
import jakarta.jms.Session;
import jakarta.jms.Topic;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.Optional;
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.MountableFile;

// topico.devoluciones contra un Artemis real: lo mismo que hace ServicioDeModeracion al publicar y los MDB al consumir
@Testcontainers(disabledWithoutDocker = true) // en Eclipse sin Docker se saltean; en el CI corren siempre
class MensajeriaIT {

    @Container
    static final GenericContainer<?> ARTEMIS = new GenericContainer<>("apache/activemq-artemis:2.42.0")
            .withEnv("ANONYMOUS_LOGIN", "true")
            .withExposedPorts(61616)
            .withCopyFileToContainer(MountableFile.forClasspathResource("artemis/broker.properties"),
                    "/var/lib/artemis-instance/etc-override/broker.properties")
            .waitingFor(Wait.forLogMessage(".*AMQ221007.*", 1));

    private ActiveMQConnectionFactory fabrica;
    private Connection conexion;
    private Topic topico;

    @BeforeEach
    void conectar() throws Exception {
        fabrica = new ActiveMQConnectionFactory("tcp://" + ARTEMIS.getHost() + ":" + ARTEMIS.getMappedPort(61616));
        conexion = fabrica.createConnection();
        conexion.setClientID("prueba-" + System.nanoTime());
        conexion.start();
        topico = conexion.createSession(false, Session.AUTO_ACKNOWLEDGE).createTopic("topico.devoluciones");
    }

    @AfterEach
    void cerrar() throws Exception {
        conexion.close();
        fabrica.close();
    }

    // Igual que publicar(): cuerpo con el id, estado como propiedad
    private void publicar(long id, String estado) {
        try (JMSContext ctx = fabrica.createContext()) {
            ctx.createProducer().setProperty("estado", estado).send(ctx.createTopic("topico.devoluciones"),
                    Map.<String, Object>of("idDevolucion", id));
        }
    }

    @Test
    void stockYReembolsosSoloRecibenLasAprobadasYNotificacionesTodas() throws Exception {
        Session s = conexion.createSession(false, Session.AUTO_ACKNOWLEDGE);
        MessageConsumer stock = s.createDurableSubscriber(topico, "stock", "estado = 'APROBADA'", false);
        MessageConsumer avisos = s.createDurableSubscriber(topico, "notificaciones");

        publicar(7, "RECHAZADA");
        publicar(8, "APROBADA");

        MapMessage aprobada = (MapMessage) stock.receive(5000);
        assertEquals(8L, aprobada.getLong("idDevolucion"));
        assertNull(stock.receive(500), "stock no tiene que recibir la rechazada");
        assertEquals(7L, ((MapMessage) avisos.receive(5000)).getLong("idDevolucion"));
        assertEquals(8L, ((MapMessage) avisos.receive(5000)).getLong("idDevolucion"));
    }

    // Suscripcion durable: si el consumidor esta caido cuando se publica, el mensaje lo espera
    @Test
    void conElConsumidorCaidoElMensajeNoSePierde() throws Exception {
        Session s = conexion.createSession(false, Session.AUTO_ACKNOWLEDGE);
        s.createDurableSubscriber(topico, "reembolsos", "estado = 'APROBADA'", false).close();

        publicar(21, "APROBADA");

        MessageConsumer vuelve = s.createDurableSubscriber(topico, "reembolsos", "estado = 'APROBADA'", false);
        assertEquals(21L, ((MapMessage) vuelve.receive(5000)).getLong("idDevolucion"));
    }

    // El MDB de stock lee el mensaje real del broker y pide reponer la devolucion indicada
    @Test
    void stockMdbConUnMensajeRealDelBroker() throws Exception {
        Session s = conexion.createSession(false, Session.AUTO_ACKNOWLEDGE);
        MessageConsumer c = s.createDurableSubscriber(topico, "stock-mdb", "estado = 'APROBADA'", false);
        publicar(33, "APROBADA");
        Message m = c.receive(5000);

        DevolucionRepository repo = mock(DevolucionRepository.class);
        ServicioDeVentas ventas = mock(ServicioDeVentas.class);
        Devolucion d = new Devolucion();
        d.setId(33L);
        when(repo.porId(33L)).thenReturn(Optional.of(d));
        StockMDB mdb = new StockMDB();
        inyectar(mdb, "devolucionRepository", repo);
        inyectar(mdb, "servicioDeVentas", ventas);

        mdb.onMessage(m);

        verify(ventas).reponerStockDeDevolucion(33L);
    }

    // Un evento de una devolucion que ya no existe se descarta (no queda reintentando para siempre)
    @Test
    void reembolsosMdbDescartaUnaDevolucionInexistente() throws Exception {
        Session s = conexion.createSession(false, Session.AUTO_ACKNOWLEDGE);
        MessageConsumer c = s.createDurableSubscriber(topico, "reembolsos-mdb", "estado = 'APROBADA'", false);
        publicar(404, "APROBADA");
        Message m = c.receive(5000);

        DevolucionRepository repo = mock(DevolucionRepository.class);
        ServicioDeReembolsos reembolsos = mock(ServicioDeReembolsos.class);
        when(repo.porId(404L)).thenReturn(Optional.empty());
        ReembolsosMDB mdb = new ReembolsosMDB();
        inyectar(mdb, "devolucionRepository", repo);
        inyectar(mdb, "servicioDeReembolsos", reembolsos);

        mdb.onMessage(m);

        verify(reembolsos, never()).emitir(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void notificacionesMdbAvisaAlCliente() throws Exception {
        Session s = conexion.createSession(false, Session.AUTO_ACKNOWLEDGE);
        MessageConsumer c = s.createDurableSubscriber(topico, "notif-mdb");
        publicar(55, "RECHAZADA");
        Message m = c.receive(5000);

        DevolucionRepository repo = mock(DevolucionRepository.class);
        ServicioDeNotificaciones avisos = mock(ServicioDeNotificaciones.class);
        Devolucion d = new Devolucion();
        when(repo.porId(55L)).thenReturn(Optional.of(d));
        NotificacionesMDB mdb = new NotificacionesMDB();
        inyectar(mdb, "devolucionRepository", repo);
        inyectar(mdb, "servicioDeNotificaciones", avisos);

        mdb.onMessage(m);

        verify(avisos).devolucionResuelta(d);
    }

    // Un mensaje que falla siempre (por ejemplo, Mercado Pago caido) se reintenta N veces y termina en la DLQ,
    // de donde se puede reprocesar. En el test N = 3; en docker/artemis/broker.properties es 10
    @Test
    void unMensajeQueFallaSiempreTerminaEnLaDlqYSePuedeReprocesar() throws Exception {
        Session t = conexion.createSession(true, Session.SESSION_TRANSACTED);
        MessageConsumer c = t.createDurableSubscriber(topico, "reembolsos-dlq", "estado = 'APROBADA'", false);
        publicar(66, "APROBADA");

        for (int intento = 1; intento <= 3; intento++) {
            Message m = c.receive(5000);
            assertNotNull(m, "intento " + intento);
            assertEquals(intento, m.getIntProperty("JMSXDeliveryCount"));
            t.rollback();
        }
        assertNull(c.receive(1500), "despues del tercer intento no se entrega mas");

        Session s = conexion.createSession(false, Session.AUTO_ACKNOWLEDGE);
        Queue dlq = s.createQueue("DLQ");
        MapMessage muerto = (MapMessage) s.createConsumer(dlq).receive(5000);
        assertEquals(66L, muerto.getLong("idDevolucion"));
        assertEquals("APROBADA", muerto.getStringProperty("estado"));

        // Reproceso: se lo vuelve a publicar con el mismo contenido
        publicar(muerto.getLong("idDevolucion"), muerto.getStringProperty("estado"));
        Message denuevo = c.receive(5000);
        assertEquals(66L, ((MapMessage) denuevo).getLong("idDevolucion"));
        t.commit();
    }

    // Igual que ValidadorDeGarantias.encolar(): el id en el cuerpo y una espera antes de entregarlo
    private void encolar(long id, long esperaMs) {
        try (JMSContext ctx = fabrica.createContext()) {
            ctx.createProducer().setDeliveryDelay(esperaMs).send(ctx.createQueue("cola.validaciones"),
                    Map.<String, Object>of("idDevolucion", id));
        }
    }

    // La cola no entrega el reintento antes de tiempo: en la app la espera es de 2 minutos, aca 2 segundos
    @Test
    void laColaDeValidacionesEntregaRecienDespuesDeLaEspera() throws Exception {
        Session s = conexion.createSession(false, Session.AUTO_ACKNOWLEDGE);
        MessageConsumer c = s.createConsumer(s.createQueue("cola.validaciones"));
        encolar(77, 2000);

        assertNull(c.receive(1000), "antes de la espera no llega");
        assertEquals(77L, ((MapMessage) c.receive(5000)).getLong("idDevolucion"));
    }

    // Punto a punto: con dos consumidores escuchando, cada mensaje lo procesa uno solo
    @Test
    void cadaValidacionLaProcesaUnSoloConsumidor() throws Exception {
        Session s = conexion.createSession(false, Session.AUTO_ACKNOWLEDGE);
        Queue cola = s.createQueue("cola.validaciones");
        MessageConsumer uno = s.createConsumer(cola);
        MessageConsumer otro = s.createConsumer(cola);
        encolar(88, 0);

        Message primero = uno.receive(3000);
        Message segundo = otro.receive(primero == null ? 3000 : 1000);
        assertEquals(1, (primero == null ? 0 : 1) + (segundo == null ? 0 : 1));
    }

    // El MDB de la cola lee el mensaje real y le pide al validador que reintente esa devolucion
    @Test
    void validacionesMdbConUnMensajeRealDeLaCola() throws Exception {
        Session s = conexion.createSession(false, Session.AUTO_ACKNOWLEDGE);
        MessageConsumer c = s.createConsumer(s.createQueue("cola.validaciones"));
        encolar(99, 0);
        Message m = c.receive(5000);

        ValidadorDeGarantias validador = mock(ValidadorDeGarantias.class);
        ValidacionesMDB mdb = new ValidacionesMDB();
        inyectar(mdb, "validador", validador);

        mdb.onMessage(m);

        verify(validador).reintentar(99L);
    }

    private static void inyectar(Object destino, String campo, Object valor) throws Exception {
        Field f = destino.getClass().getDeclaredField(campo);
        f.setAccessible(true);
        f.set(destino, valor);
    }
}
