package ar.edu.uade.repuestos.negocio;

import ar.edu.uade.repuestos.datos.DevolucionRepository;
import ar.edu.uade.repuestos.datos.ProductoRepository;
import ar.edu.uade.repuestos.datos.UsuarioRepository;
import ar.edu.uade.repuestos.datos.VentaRepository;
import ar.edu.uade.repuestos.modelo.Devolucion;
import ar.edu.uade.repuestos.modelo.Producto;
import ar.edu.uade.repuestos.modelo.Usuario;
import ar.edu.uade.repuestos.modelo.Venta;
import ar.edu.uade.repuestos.util.ReglaNegocioException;
import jakarta.ejb.Stateless;
import jakarta.ejb.TransactionAttribute;
import jakarta.ejb.TransactionAttributeType;
import jakarta.inject.Inject;
import java.math.BigDecimal;
import java.util.List;
import java.util.logging.Logger;

@Stateless
public class ServicioDeVentas {

    private static final Logger LOG = Logger.getLogger(ServicioDeVentas.class.getName());

    @Inject
    private DevolucionRepository devolucionRepository;
    @Inject
    private VentaRepository ventaRepository;

    @Inject
    private ProductoRepository productoRepository;

    @Inject
    private UsuarioRepository usuarioRepository;

    // Descontar stock y registrar la venta van juntos: si falla el guardado, el stock vuelve.
    // La venta queda PENDIENTE_PAGO con el stock reservado hasta que ServicioDeCobros la resuelva.
    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    public Venta comprar(Long usuarioId, Long productoId, int cantidad) {
        if (cantidad <= 0) {
            throw new ReglaNegocioException("La cantidad tiene que ser mayor a cero.");
        }

        Usuario usuario = usuarioRepository.porId(usuarioId)
                .orElseThrow(() -> new ReglaNegocioException("El usuario no existe."));

        Producto producto = productoRepository.porIdBloqueado(productoId)
                .orElseThrow(() -> new ReglaNegocioException("El repuesto no existe."));

        if (!producto.hayStock(cantidad)) {
            throw new ReglaNegocioException(
                "Quedan " + producto.getStock() + " unidades de " + producto.getNombre() + ".");
        }

        producto.descontarStock(cantidad);
        productoRepository.guardar(producto);

        BigDecimal importe = producto.getPrecio().multiply(BigDecimal.valueOf(cantidad));
        return ventaRepository.guardar(new Venta(usuario, producto, cantidad, importe));
    }

    public List<Venta> comprasDe(Long usuarioId) {
        return ventaRepository.porUsuario(usuarioId);
    }

    public Venta porId(Long ventaId) {
        return ventaRepository.porId(ventaId)
                .orElseThrow(() -> new ReglaNegocioException("La compra no existe."));
    }

    // Lo llama StockMDB. Si el mismo evento llega dos veces, la segunda no hace nada
    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    public boolean reponerStockDeDevolucion(Long devolucionId) {
        Devolucion devolucion = devolucionRepository.porIdBloqueado(devolucionId).orElse(null);
        if (devolucion == null || !devolucion.marcarStockRepuesto()) {
            LOG.info(() -> "[STOCK] devolucion " + devolucionId + " ya repuesta o inexistente, mensaje repetido");
            return false;
        }
        reponerStockDe(devolucion.getVenta());
        devolucionRepository.guardar(devolucion);
        return true;
    }

    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    public void reponerStockDe(Venta venta) {
        Producto producto = productoRepository.porIdBloqueado(venta.getProducto().getId())
                .orElseThrow(() -> new ReglaNegocioException("El repuesto no existe."));
        producto.reponerStock(venta.getCantidad());
        productoRepository.guardar(producto);
    }
}
