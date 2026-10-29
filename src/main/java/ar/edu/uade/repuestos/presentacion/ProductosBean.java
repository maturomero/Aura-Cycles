package ar.edu.uade.repuestos.presentacion;

import ar.edu.uade.repuestos.modelo.Producto;
import ar.edu.uade.repuestos.modelo.Resena;
import ar.edu.uade.repuestos.modelo.ResumenResenas;
import ar.edu.uade.repuestos.modelo.Venta;
import ar.edu.uade.repuestos.negocio.ServicioDeCobros;
import ar.edu.uade.repuestos.negocio.ServicioDeProductos;
import ar.edu.uade.repuestos.negocio.ServicioDeResenas;
import ar.edu.uade.repuestos.negocio.ServicioDeVentas;
import ar.edu.uade.repuestos.util.ReglaNegocioException;
import jakarta.annotation.PostConstruct;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import java.io.Serializable;
import java.util.List;
import java.util.Map;

@Named("productosBean")
@ViewScoped
public class ProductosBean implements Serializable {
    private static final long serialVersionUID = 1L;

    @Inject
    private ServicioDeProductos servicioDeProductos;

    @Inject
    private ServicioDeResenas servicioDeResenas;

    @Inject
    private ServicioDeVentas servicioDeVentas;

    @Inject
    private ServicioDeCobros servicioDeCobros;

    @Inject
    private SesionBean sesion;

    private String busqueda;
    private List<Producto> productos;

    private Long productoId;
    private Producto seleccionado;
    private List<Resena> resenas;
    private int cantidad = 1;
    private Map<Long, ResumenResenas> resumen = Map.of();

    @PostConstruct
    public void cargar() {
        this.productos = servicioDeProductos.listar();
        this.resumen = servicioDeProductos.resumenResenas();
    }

    public void cargarDetalle() {
        if (productoId == null) {
            return;
        }
        try {
            this.seleccionado = servicioDeProductos.porId(productoId);
            this.resenas = servicioDeResenas.publicadasDe(productoId);
        } catch (ReglaNegocioException e) {
            Mensajes.error(e.getMessage());
        }
    }

    public void buscar() {
        this.productos = servicioDeProductos.buscar(busqueda);
    }

    // Reserva el stock y manda a Mercado Pago. Si MP no responde, la compra queda reservada para pagar despues
    public String comprar(Long id) {
        try {
            Venta venta = servicioDeVentas.comprar(sesion.getUsuarioId(), id, cantidad);
            IrAPagar.ejecutar(servicioDeCobros, venta.getId(), sesion.getUsuarioId(), venta.getNumeroOrden());
        } catch (ReglaNegocioException e) {
            Mensajes.error(e.getMessage());
        }
        cargar();
        if (seleccionado != null) {
            cargarDetalle();
        }
        return null;
    }

    public double promedio(Long id) {
        return resumen.getOrDefault(id, ResumenResenas.SIN_RESENAS).promedio();
    }

    public int cantidadResenas(Long id) {
        return (int) resumen.getOrDefault(id, ResumenResenas.SIN_RESENAS).cantidad();
    }

    public String estrellas(int puntaje) {
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i <= 5; i++) {
            sb.append(i <= puntaje ? '\u2605' : '\u2606');
        }
        return sb.toString();
    }

    public boolean isPuedeResenar() {
        return seleccionado != null
            && servicioDeResenas.puedeResenar(sesion.getUsuarioId(), seleccionado.getId());
    }

    public String getBusqueda() { return busqueda; }
    public void setBusqueda(String busqueda) { this.busqueda = busqueda; }

    public List<Producto> getProductos() { return productos; }

    public Long getProductoId() { return productoId; }
    public void setProductoId(Long productoId) { this.productoId = productoId; }

    public Producto getSeleccionado() { return seleccionado; }

    public List<Resena> getResenas() { return resenas; }

    public int getCantidad() { return cantidad; }
    public void setCantidad(int cantidad) { this.cantidad = cantidad; }
}
