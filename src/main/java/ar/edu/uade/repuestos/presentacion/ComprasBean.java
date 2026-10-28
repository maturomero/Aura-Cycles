package ar.edu.uade.repuestos.presentacion;

import ar.edu.uade.repuestos.modelo.Venta;
import ar.edu.uade.repuestos.negocio.ServicioDeCobros;
import ar.edu.uade.repuestos.negocio.ServicioDeDevoluciones;
import ar.edu.uade.repuestos.negocio.ServicioDeVentas;
import jakarta.annotation.PostConstruct;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import java.io.Serializable;
import java.util.List;

@Named("comprasBean")
@ViewScoped
public class ComprasBean implements Serializable {
    private static final long serialVersionUID = 1L;

    @Inject
    private ServicioDeVentas servicioDeVentas;

    @Inject
    private ServicioDeDevoluciones servicioDeDevoluciones;

    @Inject
    private ServicioDeCobros servicioDeCobros;

    @Inject
    private SesionBean sesion;

    private List<Venta> compras;

    @PostConstruct
    public void cargar() {
        if (sesion.isLogueado()) {
            this.compras = servicioDeVentas.comprasDe(sesion.getUsuarioId());
        }
    }

    public String pagar(Long ventaId) {
        String orden = String.format("ORD-%06d", ventaId);
        IrAPagar.ejecutar(servicioDeCobros, ventaId, sesion.getUsuarioId(), orden);
        cargar();
        return null;
    }

    public String claseEstado(Venta venta) {
        return switch (venta.getEstado()) {
            case PAGADA -> "badge-aprobada";
            case PENDIENTE_PAGO -> "badge-pendiente";
            default -> "badge-rechazada";
        };
    }

    public boolean sePuedeDevolver(Venta venta) {
        return servicioDeDevoluciones.sePuedeDevolver(venta);
    }

    public List<Venta> getCompras() { return compras; }
}
