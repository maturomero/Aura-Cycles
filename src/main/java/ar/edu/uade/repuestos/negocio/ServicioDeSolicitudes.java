package ar.edu.uade.repuestos.negocio;

import ar.edu.uade.repuestos.datos.DevolucionRepository;
import ar.edu.uade.repuestos.datos.PedidoIdempotenteRepository;
import ar.edu.uade.repuestos.modelo.Devolucion;
import ar.edu.uade.repuestos.modelo.EstadoDevolucion;
import ar.edu.uade.repuestos.modelo.MotivoDevolucion;
import ar.edu.uade.repuestos.modelo.PedidoIdempotente;
import ar.edu.uade.repuestos.util.ClaveRepetidaException;
import ar.edu.uade.repuestos.util.NoExisteException;
import jakarta.ejb.Stateless;
import jakarta.ejb.TransactionAttribute;
import jakarta.ejb.TransactionAttributeType;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

// Lo que la API REST necesita de las devoluciones: crear en una sola llamada (idempotente), ver y listar
@Stateless
public class ServicioDeSolicitudes {

    public static final int CREADA = 201;
    public static final int ACEPTADA = 202;

    @Inject
    private DevolucionRepository devolucionRepository;

    @Inject
    private PedidoIdempotenteRepository pedidoRepository;

    // Cada pedido usa su propio borrador stateful y lo descarta al terminar
    @Inject
    private Instance<ServicioDeDevoluciones> borradores;

    public record Solicitud(Devolucion devolucion, int codigoHttp, boolean repetida) {
    }

    // La devolucion y la clave se guardan en la misma transaccion: o quedan las dos o ninguna
    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    public Solicitud solicitar(String email, String clave, Long ventaId, MotivoDevolucion motivo, String comentario) {
        String huella = huella(ventaId, motivo, comentario);

        Optional<PedidoIdempotente> previo = pedidoRepository.porClave(email, clave);
        if (previo.isPresent()) {
            if (!previo.get().esElMismoPedido(huella)) {
                throw new ClaveRepetidaException();
            }
            return new Solicitud(buscar(previo.get().getDevolucionId()), previo.get().getCodigoHttp(), true);
        }

        ServicioDeDevoluciones borrador = borradores.get();
        try {
            borrador.seleccionarCompraDe(email, ventaId);
            borrador.cargarMotivo(motivo, comentario);
            Devolucion devolucion = borrador.confirmar();

            // 202 si el proveedor no contesto y la garantia queda en la cola de validaciones
            int codigo = devolucion.getEstado() == EstadoDevolucion.PENDIENTE_VALIDACION ? ACEPTADA : CREADA;
            pedidoRepository.guardar(new PedidoIdempotente(email, clave, huella, devolucion.getId(), codigo));
            return new Solicitud(devolucion, codigo, false);
        } finally {
            borradores.destroy(borrador);
        }
    }

    // BOLA: el cliente solo ve las suyas; una ajena da el mismo error que una que no existe
    public Devolucion ver(Long id, String email, boolean moderador) {
        Devolucion devolucion = buscar(id);
        if (!moderador && !devolucion.getVenta().esDe(email)) {
            throw new NoExisteException("La devolución no existe.");
        }
        return devolucion;
    }

    public List<Devolucion> delCliente(Long usuarioId, EstadoDevolucion estado) {
        List<Devolucion> propias = devolucionRepository.porUsuario(usuarioId);
        if (estado == null) {
            return propias;
        }
        return propias.stream().filter(d -> d.getEstado() == estado).toList();
    }

    // La cola del moderador: sin filtro, las que esperan revision
    public List<Devolucion> paraModerar(EstadoDevolucion estado) {
        return devolucionRepository.porEstado(estado == null ? EstadoDevolucion.PENDIENTE : estado);
    }

    private Devolucion buscar(Long id) {
        return devolucionRepository.porId(id)
                .orElseThrow(() -> new NoExisteException("La devolución no existe."));
    }

    static String huella(Long ventaId, MotivoDevolucion motivo, String comentario) {
        String contenido = ventaId + "|" + motivo + "|" + (comentario == null ? "" : comentario.strip());
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(sha.digest(contenido.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
