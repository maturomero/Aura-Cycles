package ar.edu.uade.repuestos.negocio;

import ar.edu.uade.repuestos.datos.DevolucionRepository;
import ar.edu.uade.repuestos.integracion.ProveedorNoDisponibleException;
import ar.edu.uade.repuestos.modelo.Devolucion;
import ar.edu.uade.repuestos.modelo.EstadoDevolucion;
import ar.edu.uade.repuestos.modelo.EstadoVenta;
import ar.edu.uade.repuestos.modelo.MotivoDevolucion;
import ar.edu.uade.repuestos.modelo.Venta;
import ar.edu.uade.repuestos.util.ReglaNegocioException;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import jakarta.ejb.Remove;
import jakarta.ejb.SessionContext;
import jakarta.ejb.Stateful;
import jakarta.ejb.TransactionAttribute;
import jakarta.ejb.TransactionAttributeType;
import jakarta.inject.Inject;
import java.io.Serializable;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.logging.Logger;

// Stateful: el borrador (compra, motivo, comentario) sobrevive entre los tres pasos.
@Stateful
public class ServicioDeDevoluciones implements Serializable {
    private static final long serialVersionUID = 1L;

    private static final Logger LOG = Logger.getLogger(ServicioDeDevoluciones.class.getName());

    private static final int DIAS_PARA_DEVOLVER = 30;
    static final int LARGO_COMENTARIO = 1000;

    @Inject
    private DevolucionRepository devolucionRepository;

    @Inject
    private ServicioDeVentas servicioDeVentas;

    @Inject
    private ServicioDeNotificaciones servicioDeNotificaciones;

    @Inject
    private ValidadorDeGarantias validadorDeGarantias;

    @Resource
    private SessionContext contexto;

    private Venta ventaSeleccionada;
    private MotivoDevolucion motivo;
    private String comentario;
    private LocalDateTime iniciadoEn;

    @PostConstruct
    public void iniciar() {
        this.iniciadoEn = LocalDateTime.now();
        LOG.info(() -> "[CICLO DE VIDA] @PostConstruct ServicioDeDevoluciones (stateful) instancia #"
                + Integer.toHexString(System.identityHashCode(this)) + ": borrador vacio.");
    }

    @PreDestroy
    public void limpiar() {
        LOG.info(() -> "[CICLO DE VIDA] @PreDestroy ServicioDeDevoluciones (stateful) instancia #"
                + Integer.toHexString(System.identityHashCode(this)) + ": el contenedor la descarta.");
    }

    public void seleccionarCompra(Long ventaId) {
        Venta venta = servicioDeVentas.porId(ventaId);

        // SEC-05 (BOLA): el usuario sale del contenedor, no de la pantalla. Si no es suya, mismo error que si no existiera
        if (!venta.esDe(contexto.getCallerPrincipal().getName())) {
            throw new ReglaNegocioException("La compra no existe.");
        }

        if (venta.getEstado() != EstadoVenta.PAGADA) {
            throw new ReglaNegocioException("La orden " + venta.getNumeroOrden() + " no esta pagada.");
        }

        if (devolucionRepository.existeParaVenta(ventaId)) {
            throw new ReglaNegocioException(
                "Ya existe una solicitud de devolucion para la orden " + venta.getNumeroOrden() + ".");
        }

        long dias = Duration.between(venta.getFecha(), LocalDateTime.now()).toDays();
        if (dias > DIAS_PARA_DEVOLVER) {
            throw new ReglaNegocioException(
                "El plazo de " + DIAS_PARA_DEVOLVER + " dias para devolver esta compra ya vencio.");
        }

        this.ventaSeleccionada = venta;
    }

    public void cargarMotivo(MotivoDevolucion motivo, String comentario) {
        exigirCompraSeleccionada();

        if (motivo == null) {
            throw new ReglaNegocioException("Elegi un motivo para la devolucion.");
        }
        if (motivo == MotivoDevolucion.DEFECTUOSO
                && (comentario == null || comentario.isBlank())) {
            throw new ReglaNegocioException(
                "Si el repuesto llego defectuoso, conta brevemente que le pasa.");
        }

        if (comentario != null && comentario.length() > LARGO_COMENTARIO) {
            throw new ReglaNegocioException("El comentario puede tener hasta " + LARGO_COMENTARIO + " caracteres.");
        }

        this.motivo = motivo;
        this.comentario = comentario;
    }

    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    public Devolucion confirmar() {
        exigirCompraSeleccionada();

        if (motivo == null) {
            throw new ReglaNegocioException("Falta elegir el motivo.");
        }

        // La garantia se valida con el proveedor antes de guardar: si no la acepta, no se crea nada
        Devolucion nueva = new Devolucion(ventaSeleccionada, motivo, comentario);
        try {
            nueva.garantiaValidada(validadorDeGarantias.consultar(ventaSeleccionada, motivo));
        } catch (ProveedorNoDisponibleException e) {
            LOG.warning(() -> "[SOAP] el proveedor no respondio, la devolucion queda PENDIENTE_VALIDACION");
        }

        Devolucion devolucion = devolucionRepository.guardar(nueva);
        servicioDeNotificaciones.devolucionRecibida(devolucion);

        LOG.info(() -> "Devolucion " + devolucion.getCodigo() + " registrada (" + devolucion.getEstado() + ").");
        return devolucion;
    }

    // Fin del flujo: el contenedor descarta la instancia.
    @Remove
    public void finalizar() {
        LOG.info(() -> "[CICLO DE VIDA] @Remove ServicioDeDevoluciones instancia #"
                + Integer.toHexString(System.identityHashCode(this)) + ": fin de la conversacion.");
        this.ventaSeleccionada = null;
        this.motivo = null;
        this.comentario = null;
    }

    public List<Devolucion> misDevoluciones(Long usuarioId) {
        return devolucionRepository.porUsuario(usuarioId);
    }

    public boolean sePuedeDevolver(Venta venta) {
        if (venta.getEstado() != EstadoVenta.PAGADA || devolucionRepository.existeParaVenta(venta.getId())) {
            return false;
        }
        long dias = Duration.between(venta.getFecha(), LocalDateTime.now()).toDays();
        return dias <= DIAS_PARA_DEVOLVER;
    }

    public List<Devolucion> pendientes() {
        return devolucionRepository.porEstado(EstadoDevolucion.PENDIENTE);
    }

    private void exigirCompraSeleccionada() {
        if (ventaSeleccionada == null) {
            throw new ReglaNegocioException("Primero elegí la compra que querés devolver.");
        }
    }

    public Venta getVentaSeleccionada() { return ventaSeleccionada; }

    public MotivoDevolucion getMotivo() { return motivo; }

    public String getComentario() { return comentario; }

    public LocalDateTime getIniciadoEn() { return iniciadoEn; }

    public boolean hayBorrador() { return ventaSeleccionada != null; }
}
