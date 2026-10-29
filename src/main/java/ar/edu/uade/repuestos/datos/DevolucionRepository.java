package ar.edu.uade.repuestos.datos;

import ar.edu.uade.repuestos.modelo.Devolucion;
import ar.edu.uade.repuestos.modelo.EstadoDevolucion;
import jakarta.ejb.Stateless;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import java.util.Optional;

@Stateless
public class DevolucionRepository {
    @PersistenceContext(unitName = "repuestosPU")
    private EntityManager em;

    public Devolucion guardar(Devolucion devolucion) {
        if (devolucion.getId() == null) {
            em.persist(devolucion);
            em.flush();  // hace falta el id para armar el codigo DEV-000000
            return devolucion;
        }
        return em.merge(devolucion);
    }

    public Optional<Devolucion> porId(Long id) {
        return Optional.ofNullable(em.find(Devolucion.class, id));
    }

    // Dos entregas del mismo evento o dos moderadores a la vez: el segundo espera y ve el cambio del primero
    public Optional<Devolucion> porIdBloqueado(Long id) {
        Devolucion d = em.find(Devolucion.class, id);
        if (d != null) {
            em.refresh(d, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        }
        return Optional.ofNullable(d);
    }

    public List<Devolucion> porEstado(EstadoDevolucion estado) {
        return em.createNamedQuery("Devolucion.porEstado", Devolucion.class)
                 .setParameter("estado", estado)
                 .getResultList();
    }

    public List<Devolucion> porUsuario(Long usuarioId) {
        return em.createNamedQuery("Devolucion.porUsuario", Devolucion.class)
                 .setParameter("usuarioId", usuarioId)
                 .getResultList();
    }

    public boolean existeParaVenta(Long ventaId) {
        Long cantidad = em.createNamedQuery("Devolucion.porVenta", Long.class)
                          .setParameter("ventaId", ventaId)
                          .getSingleResult();
        return cantidad != null && cantidad > 0;
    }
}
