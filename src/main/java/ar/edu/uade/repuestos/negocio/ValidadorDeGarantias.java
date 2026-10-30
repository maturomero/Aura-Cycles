package ar.edu.uade.repuestos.negocio;

import ar.edu.uade.repuestos.datos.DevolucionRepository;
import ar.edu.uade.repuestos.integracion.ClienteProveedor;
import ar.edu.uade.repuestos.integracion.GarantiaNoValidaException;
import ar.edu.uade.repuestos.integracion.ProveedorNoDisponibleException;
import ar.edu.uade.repuestos.modelo.Devolucion;
import ar.edu.uade.repuestos.modelo.EstadoDevolucion;
import ar.edu.uade.repuestos.modelo.MotivoDevolucion;
import ar.edu.uade.repuestos.modelo.Venta;
import ar.edu.uade.repuestos.util.MensajeriaNoDisponibleException;
import jakarta.annotation.Resource;
import jakarta.ejb.Stateless;
import jakarta.ejb.TransactionAttribute;
import jakarta.ejb.TransactionAttributeType;
import jakarta.inject.Inject;
import jakarta.jms.JMSContext;
import jakarta.jms.JMSDestinationDefinition;
import jakarta.jms.JMSRuntimeException;
import jakarta.jms.Queue;
import java.time.Duration;
import java.util.Map;
import java.util.logging.Logger;

// Garantia con el proveedor mayorista (AUR-32). Si no contesta, la devolucion va a una cola punto a punto:
// un solo consumidor la reintenta cada 2 minutos y al quinto intento pasa al moderador sin RMA
@JMSDestinationDefinition(
        name = ValidadorDeGarantias.COLA_VALIDACIONES,
        interfaceName = "jakarta.jms.Queue",
        destinationName = "cola.validaciones")
@Stateless
public class ValidadorDeGarantias {

    public static final String COLA_VALIDACIONES = "java:/jms/queue/validaciones";
    static final int MAX_INTENTOS = 5;
    static final Duration ESPERA = Duration.ofMinutes(2);

    private static final Logger LOG = Logger.getLogger(ValidadorDeGarantias.class.getName());

    @Inject
    private ClienteProveedor proveedor;

    @Inject
    private DevolucionRepository devolucionRepository;

    @Inject
    private ServicioDeNotificaciones servicioDeNotificaciones;

    @Inject
    private JMSContext jms;

    @Resource(lookup = COLA_VALIDACIONES)
    private Queue colaValidaciones;

    // RMA del proveedor. Tira GarantiaNoValidaException (negocio) o ProveedorNoDisponibleException (tecnico).
    // Fuera de la transaccion del que llama: si el proveedor falla, la devolucion se guarda igual como pendiente
    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public String consultar(Venta venta, MotivoDevolucion motivo) {
        return proveedor.validarGarantia(venta.getProducto().getCodigo(), venta.getFecha().toLocalDate(),
                venta.getCantidad(), motivo.name(), venta.getNumeroOrden());
    }

    // Va con la transaccion del que llama: si la devolucion no se guarda, el mensaje tampoco sale
    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    public void encolar(Devolucion devolucion) {
        try {
            jms.createProducer()
               .setDeliveryDelay(ESPERA.toMillis())
               .send(colaValidaciones, Map.<String, Object>of("idDevolucion", devolucion.getId()));
        } catch (JMSRuntimeException e) {
            LOG.warning(() -> "[COLA] broker no disponible, " + devolucion.getCodigo() + " vuelve atras");
            throw new MensajeriaNoDisponibleException(e);
        }
        LOG.info(() -> "[COLA] " + devolucion.getCodigo() + " en cola.validaciones, se reintenta en "
                + ESPERA.toMinutes() + " minutos");
    }

    // Lo llama ValidacionesMDB dentro de su transaccion: el estado y el proximo mensaje se confirman juntos
    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    public void reintentar(Long devolucionId) {
        Devolucion d = devolucionRepository.porId(devolucionId).orElse(null);
        if (d == null || d.getEstado() != EstadoDevolucion.PENDIENTE_VALIDACION) {
            return;
        }
        try {
            d.garantiaValidada(consultar(d.getVenta(), d.getMotivo()));
            LOG.info(() -> "[GARANTIA] " + d.getCodigo() + " validada (" + d.getRma() + "), pasa al moderador");
        } catch (GarantiaNoValidaException e) {
            d.garantiaRechazada();
            servicioDeNotificaciones.devolucionResuelta(d);
            LOG.info(() -> "[GARANTIA] " + d.getCodigo() + " rechazada por el proveedor (" + e.getCodigo() + ")");
        } catch (ProveedorNoDisponibleException e) {
            if (d.validacionFallida(MAX_INTENTOS)) {
                LOG.warning(() -> "[GARANTIA] " + d.getCodigo() + " sin respuesta del proveedor despues de "
                        + MAX_INTENTOS + " intentos: pasa al moderador sin validar");
            } else {
                LOG.info(() -> "[GARANTIA] " + d.getCodigo() + " intento " + d.getIntentosValidacion() + " sin respuesta");
                encolar(d);
            }
        }
        devolucionRepository.guardar(d);
    }
}
