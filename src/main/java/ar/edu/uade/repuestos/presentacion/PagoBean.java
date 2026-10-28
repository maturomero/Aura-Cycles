package ar.edu.uade.repuestos.presentacion;

import ar.edu.uade.repuestos.integracion.MercadoPagoNoDisponibleException;
import ar.edu.uade.repuestos.modelo.EstadoVenta;
import ar.edu.uade.repuestos.modelo.Venta;
import ar.edu.uade.repuestos.negocio.ServicioDeCobros;
import ar.edu.uade.repuestos.util.ReglaNegocioException;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import java.io.Serializable;

/** CAPA DE PRESENTACION - vuelta desde Mercado Pago. Solo usa el payment_id; el estado lo confirma el servicio. */
@Named("pagoBean")
@ViewScoped
public class PagoBean implements Serializable {

    private static final long serialVersionUID = 1L;

    @Inject
    private ServicioDeCobros servicioDeCobros;

    @Inject
    private SesionBean sesion;

    private Long ventaId;
    private String paymentId;

    private Venta venta;
    private boolean sinRespuesta;

    public void confirmar() {
        if (ventaId == null) {
            Mensajes.error("No sabemos de que compra volviste.");
            return;
        }
        try {
            venta = servicioDeCobros.confirmar(ventaId, sesion.getUsuarioId(), paymentId);
        } catch (MercadoPagoNoDisponibleException e) {
            sinRespuesta = true;
        } catch (ReglaNegocioException e) {
            Mensajes.error(e.getMessage());
        }
    }

    public boolean es(String estado) {
        return venta != null && venta.getEstado() == EstadoVenta.valueOf(estado);
    }

    public Venta getVenta() { return venta; }

    public boolean isSinRespuesta() { return sinRespuesta; }

    public Long getVentaId() { return ventaId; }
    public void setVentaId(Long ventaId) { this.ventaId = ventaId; }

    public String getPaymentId() { return paymentId; }
    public void setPaymentId(String paymentId) { this.paymentId = paymentId; }
}
