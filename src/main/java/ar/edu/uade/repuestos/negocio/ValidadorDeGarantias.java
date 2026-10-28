package ar.edu.uade.repuestos.negocio;

import ar.edu.uade.repuestos.datos.DevolucionRepository;
import ar.edu.uade.repuestos.integracion.ClienteProveedor;
import ar.edu.uade.repuestos.integracion.GarantiaNoValidaException;
import ar.edu.uade.repuestos.integracion.ProveedorNoDisponibleException;
import ar.edu.uade.repuestos.modelo.Devolucion;
import ar.edu.uade.repuestos.modelo.EstadoDevolucion;
import ar.edu.uade.repuestos.modelo.MotivoDevolucion;
import ar.edu.uade.repuestos.modelo.Venta;
import jakarta.annotation.Resource;
import jakarta.ejb.Schedule;
import jakarta.ejb.SessionContext;
import jakarta.ejb.Stateless;
import jakarta.ejb.TransactionAttribute;
import jakarta.ejb.TransactionAttributeType;
import jakarta.inject.Inject;
import java.util.logging.Logger;

/**
 * CAPA DE NEGOCIO - STATELESS. Garantia con el proveedor mayorista (AUR-32) y su reintento (AUR-49).
 * Sin cola: un timer revisa las PENDIENTE_VALIDACION. Despues de MAX_INTENTOS pasan al moderador sin RMA.
 */
@Stateless
public class ValidadorDeGarantias {

    private static final Logger LOG = Logger.getLogger(ValidadorDeGarantias.class.getName());
    static final int MAX_INTENTOS = 5;

    @Inject
    private ClienteProveedor proveedor;

    @Inject
    private DevolucionRepository devolucionRepository;

    @Inject
    private ServicioDeNotificaciones servicioDeNotificaciones;

    @Resource
    private SessionContext contexto;

    // RMA del proveedor. Tira GarantiaNoValidaException (negocio) o ProveedorNoDisponibleException (tecnico).
    // Fuera de la transaccion del que llama: si el proveedor falla, la devolucion se guarda igual como pendiente
    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public String consultar(Venta venta, MotivoDevolucion motivo) {
        return proveedor.validarGarantia(venta.getProducto().getCodigo(), venta.getFecha().toLocalDate(),
                venta.getCantidad(), motivo.name(), venta.getNumeroOrden());
    }

    @Schedule(minute = "*/2", hour = "*", persistent = false)
    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public void reintentarPendientes() {
        for (Devolucion d : devolucionRepository.porEstado(EstadoDevolucion.PENDIENTE_VALIDACION)) {
            contexto.getBusinessObject(ValidadorDeGarantias.class).reintentar(d.getId());
        }
    }

    // Cada devolucion en su propia transaccion: si una falla, las demas siguen
    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
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
                LOG.info(() -> "[GARANTIA] " + d.getCodigo() + " intento " + d.getIntentosValidacion()
                        + " sin respuesta, se reintenta en 2 minutos");
            }
        }
        devolucionRepository.guardar(d);
    }
}
