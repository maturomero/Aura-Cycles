package ar.edu.uade.repuestos.negocio;

import ar.edu.uade.repuestos.datos.DevolucionRepository;
import ar.edu.uade.repuestos.datos.ResenaRepository;
import ar.edu.uade.repuestos.modelo.Devolucion;
import ar.edu.uade.repuestos.modelo.EstadoDevolucion;
import ar.edu.uade.repuestos.modelo.EstadoResena;
import ar.edu.uade.repuestos.modelo.Resena;
import ar.edu.uade.repuestos.util.MensajeriaNoDisponibleException;
import ar.edu.uade.repuestos.util.ReglaNegocioException;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ejb.Stateless;
import jakarta.ejb.TransactionAttribute;
import jakarta.ejb.TransactionAttributeType;
import jakarta.inject.Inject;
import jakarta.jms.JMSContext;
import jakarta.jms.JMSDestinationDefinition;
import jakarta.jms.JMSRuntimeException;
import jakarta.jms.Topic;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

// Fachada del panel de moderacion. La autorizacion la aplica el contenedor, no un if.
// El topico se crea solo al desplegar, no hace falta configurarlo a mano en WildFly
@JMSDestinationDefinition(
        name = ServicioDeModeracion.TOPICO_DEVOLUCIONES,
        interfaceName = "jakarta.jms.Topic",
        destinationName = "topico.devoluciones")
@Stateless
public class ServicioDeModeracion {

    public static final String TOPICO_DEVOLUCIONES = "java:/jms/topic/devoluciones";

    private static final Logger LOG = Logger.getLogger(ServicioDeModeracion.class.getName());

    @Inject
    private ResenaRepository resenaRepository;

    @Inject
    private DevolucionRepository devolucionRepository;

    @Inject
    private ServicioDeReembolsos servicioDeReembolsos;

    @Inject
    private ServicioDeNotificaciones servicioDeNotificaciones;

    @Inject
    private JMSContext jms;

    @Resource(lookup = TOPICO_DEVOLUCIONES)
    private Topic topicoDevoluciones;

    // Stateless: el pool reutiliza instancias, por eso esto aparece pocas veces en la consola
    @PostConstruct
    void alCrear() {
        LOG.info(() -> "[CICLO DE VIDA] @PostConstruct ServicioDeModeracion (stateless) instancia #"
                + Integer.toHexString(System.identityHashCode(this)) + " entra al pool.");
    }

    @PreDestroy
    void alDestruir() {
        LOG.info(() -> "[CICLO DE VIDA] @PreDestroy ServicioDeModeracion (stateless) instancia #"
                + Integer.toHexString(System.identityHashCode(this)) + " sale del pool.");
    }

    @RolesAllowed("MODERADOR")
    public List<Resena> resenasPendientes() {
        return resenaRepository.porEstado(EstadoResena.PENDIENTE);
    }

    @RolesAllowed("MODERADOR")
    public List<Devolucion> devolucionesPendientes() {
        return devolucionRepository.porEstado(EstadoDevolucion.PENDIENTE);
    }

    @RolesAllowed("MODERADOR")
    public void aprobarResena(Long resenaId) {
        Resena resena = buscarResena(resenaId);
        exigirPendiente(resena);
        resena.aprobar();
        resenaRepository.guardar(resena);
        servicioDeNotificaciones.resenaResuelta(resena);
    }

    @RolesAllowed("MODERADOR")
    public void rechazarResena(Long resenaId, String motivo) {
        Resena resena = buscarResena(resenaId);
        exigirPendiente(resena);
        resena.rechazar(motivo);
        resenaRepository.guardar(resena);
        servicioDeNotificaciones.resenaResuelta(resena);
    }

    // Aprueba y publica el evento. Stock, reembolso y aviso los hacen los MDB.
    @RolesAllowed("MODERADOR")
    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    public void aprobarDevolucion(Long devolucionId) {
        Devolucion devolucion = buscarDevolucion(devolucionId);

        BigDecimal monto = servicioDeReembolsos.calcularMonto(devolucion);
        devolucion.aprobar(monto);
        devolucionRepository.guardar(devolucion);
        publicar(devolucion);
    }

    @RolesAllowed("MODERADOR")
    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    public void rechazarDevolucion(Long devolucionId) {
        Devolucion devolucion = buscarDevolucion(devolucionId);

        devolucion.rechazar();
        devolucionRepository.guardar(devolucion);
        publicar(devolucion);
    }

    // Sale recien cuando la transaccion confirma: si algo falla, no se publica nada
    private void publicar(Devolucion devolucion) {
        try {
            jms.createProducer()
               .setProperty("estado", devolucion.getEstado().name())
               .send(topicoDevoluciones, Map.<String, Object>of("idDevolucion", devolucion.getId()));
        } catch (JMSRuntimeException e) {
            LOG.warning(() -> "[TOPICO] broker no disponible, " + devolucion.getCodigo() + " vuelve atras");
            throw new MensajeriaNoDisponibleException(e);
        }
        LOG.info(() -> "[TOPICO] publicada " + devolucion.getCodigo() + " " + devolucion.getEstado());
    }

    private Resena buscarResena(Long id) {
        return resenaRepository.porId(id)
                .orElseThrow(() -> new ReglaNegocioException("La resena no existe."));
    }

    private Devolucion buscarDevolucion(Long id) {
        return devolucionRepository.porId(id)
                .orElseThrow(() -> new ReglaNegocioException("La solicitud no existe."));
    }

    private void exigirPendiente(Resena resena) {
        if (resena.getEstado() != EstadoResena.PENDIENTE) {
            throw new ReglaNegocioException("Esa resena ya fue resuelta.");
        }
    }
}
