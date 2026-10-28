package ar.edu.uade.repuestos.negocio;

import ar.edu.uade.repuestos.datos.VentaRepository;
import ar.edu.uade.repuestos.integracion.ClienteMercadoPago;
import ar.edu.uade.repuestos.integracion.MercadoPagoNoDisponibleException;
import ar.edu.uade.repuestos.integracion.PagoMP;
import ar.edu.uade.repuestos.integracion.PreferenciaMP;
import ar.edu.uade.repuestos.modelo.Venta;
import ar.edu.uade.repuestos.util.ReglaNegocioException;
import jakarta.annotation.Resource;
import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ejb.Schedule;
import jakarta.ejb.SessionContext;
import jakarta.ejb.Stateless;
import jakarta.ejb.TransactionAttribute;
import jakarta.ejb.TransactionAttributeType;
import jakarta.inject.Inject;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * CAPA DE NEGOCIO - STATELESS. Cobro con Mercado Pago (Checkout Pro).
 *
 * La venta nace PENDIENTE_PAGO con el stock reservado. El estado del pago siempre
 * se le pregunta a Mercado Pago: lo que viene en la URL de vuelta no se cree.
 */
@Stateless
public class ServicioDeCobros {

    private static final Logger LOG = Logger.getLogger(ServicioDeCobros.class.getName());

    static final Duration PLAZO_PARA_PAGAR = Duration.ofMinutes(30);
    // La preferencia vence a los 30 min; esperamos 5 mas por si un pago se acredito justo al final
    static final Duration MARGEN = Duration.ofMinutes(5);

    enum Decision { PAGAR, RECHAZAR, ESPERAR }

    @Inject
    private VentaRepository ventaRepository;

    @Inject
    private ServicioDeVentas servicioDeVentas;

    @Inject
    private ClienteMercadoPago mercadoPago;

    @Resource
    private SessionContext contexto;

    // Devuelve la URL de Mercado Pago, o null si la compra ya estaba pagada y no hay que ir
    @RolesAllowed({"CLIENTE", "MODERADOR"})
    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    public String iniciarPago(Long ventaId, Long usuarioId) {
        Venta venta = ventaDe(ventaId, usuarioId);
        if (!venta.isPendienteDePago()) {
            throw new ReglaNegocioException("La orden " + venta.getNumeroOrden() + " ya no espera pago ("
                    + venta.getEstado().getDescripcion() + ").");
        }
        LocalDateTime vence = venta.getFecha().plus(PLAZO_PARA_PAGAR);
        if (LocalDateTime.now().isAfter(vence)) {
            throw new ReglaNegocioException("La reserva de " + venta.getNumeroOrden()
                    + " vencio. Hace la compra de nuevo.");
        }

        // Si ya se mando a pagar antes, puede haber pagado y no haber vuelto: no le cobramos dos veces
        if (venta.getMpPreferenciaId() != null) {
            Optional<PagoMP> pago = pagoVigente(venta);
            if (pago.isPresent()) {
                Venta resuelta = aplicar(venta.getId(), pago.get());
                if (!resuelta.isPendienteDePago()) {
                    return null;
                }
            }
        }

        String urlRetorno = mercadoPago.configuracion().urlApp() + "/pago.xhtml?venta=" + venta.getId();
        PreferenciaMP preferencia = mercadoPago.crearPreferencia(
                venta.getReferenciaPago(),
                venta.getProducto().getCodigo(),
                venta.getProducto().getNombre(),
                venta.getCantidad(),
                venta.getImporte().divide(BigDecimal.valueOf(venta.getCantidad()), 2, RoundingMode.HALF_UP),
                urlRetorno,
                conZona(venta.getFecha()),
                conZona(vence));

        venta.setMpPreferenciaId(preferencia.id());
        ventaRepository.guardar(venta);
        LOG.info(() -> "[COBRO] " + venta.getNumeroOrden() + " enviada a Mercado Pago (preferencia "
                + preferencia.id() + ")");
        return preferencia.urlDePago();
    }

    // Se llama al volver de Mercado Pago. Recargar la pagina no cambia nada
    @RolesAllowed({"CLIENTE", "MODERADOR"})
    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    public Venta confirmar(Long ventaId, Long usuarioId, String pagoIdDeLaUrl) {
        Venta venta = ventaDe(ventaId, usuarioId);
        if (!venta.isPendienteDePago()) {
            return venta;
        }
        Optional<PagoMP> pago = mercadoPago.obtenerPago(pagoIdDeLaUrl);
        if (pago.isEmpty()) {
            pago = pagoVigente(venta);
        }
        return pago.isPresent() ? aplicar(venta.getId(), pago.get()) : venta;
    }

    // Libera el stock de las reservas que nadie pago. Antes le pregunta a MP por si pagaron y no volvieron
    @Schedule(minute = "*/5", hour = "*", persistent = false)
    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public void vencerReservas() {
        LocalDateTime limite = LocalDateTime.now().minus(PLAZO_PARA_PAGAR).minus(MARGEN);
        List<Venta> pendientes = ventaRepository.pendientesDePagoAntesDe(limite);
        for (Venta venta : pendientes) {
            try {
                contexto.getBusinessObject(ServicioDeCobros.class).cerrarReserva(venta.getId());
            } catch (MercadoPagoNoDisponibleException e) {
                LOG.warning(() -> "[COBRO] Mercado Pago no responde, las reservas vencidas se revisan en 5 minutos");
                return;
            } catch (ReglaNegocioException e) {
                LOG.warning(() -> "[COBRO] " + venta.getNumeroOrden() + ": " + e.getMessage());
            }
        }
    }

    // PermitAll porque lo llama el timer, que no tiene usuario. Igual solo toca reservas ya vencidas
    @PermitAll
    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public void cerrarReserva(Long ventaId) {
        Venta venta = ventaRepository.porId(ventaId).orElse(null);
        if (venta == null || !venta.isPendienteDePago()
                || venta.getFecha().isAfter(LocalDateTime.now().minus(PLAZO_PARA_PAGAR).minus(MARGEN))) {
            return;
        }
        Optional<PagoMP> pago = venta.getMpPreferenciaId() == null ? Optional.empty() : pagoVigente(venta);
        if (pago.isPresent()) {
            aplicar(ventaId, pago.get());
            return;  // pagada, rechazada o todavia en curso en MP: en ningun caso se vence
        }
        Venta bloqueada = ventaRepository.porIdBloqueado(ventaId).orElseThrow();
        if (bloqueada.isPendienteDePago()) {
            bloqueada.vencer();
            servicioDeVentas.reponerStockDe(bloqueada);
            ventaRepository.guardar(bloqueada);
            LOG.info(() -> "[COBRO] " + bloqueada.getNumeroOrden() + " vencio sin pago, se libero el stock");
        }
    }

    // La regla en si, sin servidor ni base: la usan los tests
    static Decision decidir(Venta venta, PagoMP pago) {
        if (!venta.getReferenciaPago().equals(pago.referencia())) {
            throw new ReglaNegocioException("El pago " + pago.id() + " no corresponde a la orden "
                    + venta.getNumeroOrden() + ".");
        }
        switch (pago.estado()) {
            case "approved":
                if (!"ARS".equals(pago.moneda()) || pago.monto() == null
                        || pago.monto().compareTo(venta.getImporte()) != 0) {
                    throw new ReglaNegocioException("El monto pagado no coincide con la orden "
                            + venta.getNumeroOrden() + ".");
                }
                return Decision.PAGAR;
            case "rejected":
            case "cancelled":
            case "refunded":
            case "charged_back":
                return Decision.RECHAZAR;
            default:
                return Decision.ESPERAR;
        }
    }

    // Si hay alguno aprobado gana ese; si no, el mas nuevo
    static Optional<PagoMP> elegir(List<PagoMP> pagos) {
        return pagos.stream().filter(PagoMP::aprobado).findFirst()
                .or(() -> pagos.stream().findFirst());
    }

    // ---------- helpers ----------

    private Venta aplicar(Long ventaId, PagoMP pago) {
        Venta venta = ventaRepository.porIdBloqueado(ventaId).orElseThrow();
        if (!venta.isPendienteDePago()) {
            return venta;  // otra request la resolvio mientras consultabamos a MP
        }
        switch (decidir(venta, pago)) {
            case PAGAR -> {
                venta.marcarPagada(pago.id());
                LOG.info(() -> "[COBRO] " + venta.getNumeroOrden() + " PAGADA (pago " + pago.id() + ")");
            }
            case RECHAZAR -> {
                venta.marcarRechazada();
                servicioDeVentas.reponerStockDe(venta);
                LOG.info(() -> "[COBRO] " + venta.getNumeroOrden() + " pago " + pago.estado()
                        + ", se libero el stock");
            }
            case ESPERAR -> LOG.info(() -> "[COBRO] " + venta.getNumeroOrden() + " pago en curso ("
                    + pago.estado() + ")");
        }
        return ventaRepository.guardar(venta);
    }

    private Optional<PagoMP> pagoVigente(Venta venta) {
        return elegir(mercadoPago.buscarPagos(venta.getReferenciaPago()));
    }

    // Mismo mensaje si no existe o si es de otro cliente: no se puede adivinar que ordenes existen
    private Venta ventaDe(Long ventaId, Long usuarioId) {
        return ventaRepository.porId(ventaId)
                .filter(v -> v.getUsuario().getId().equals(usuarioId))
                .orElseThrow(() -> new ReglaNegocioException("La compra no existe."));
    }

    private static OffsetDateTime conZona(LocalDateTime fecha) {
        return fecha.atZone(ZoneId.systemDefault()).toOffsetDateTime();
    }
}
