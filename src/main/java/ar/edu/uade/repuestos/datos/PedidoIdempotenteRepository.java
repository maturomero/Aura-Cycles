package ar.edu.uade.repuestos.datos;

import ar.edu.uade.repuestos.modelo.PedidoIdempotente;
import jakarta.ejb.Stateless;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.Optional;

@Stateless
public class PedidoIdempotenteRepository {
    @PersistenceContext(unitName = "repuestosPU")
    private EntityManager em;

    public Optional<PedidoIdempotente> porClave(String email, String clave) {
        return em.createNamedQuery("PedidoIdempotente.porClave", PedidoIdempotente.class)
                 .setParameter("email", email)
                 .setParameter("clave", clave)
                 .getResultStream()
                 .findFirst();
    }

    // flush: si dos pedidos iguales llegan a la vez, el indice unico frena al segundo aca y no al confirmar
    public PedidoIdempotente guardar(PedidoIdempotente pedido) {
        em.persist(pedido);
        em.flush();
        return pedido;
    }
}
