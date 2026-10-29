package ar.edu.uade.repuestos.datos;

import ar.edu.uade.repuestos.modelo.EstadoResena;
import ar.edu.uade.repuestos.modelo.Resena;
import ar.edu.uade.repuestos.modelo.ResumenResenas;
import jakarta.ejb.Stateless;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Stateless
public class ResenaRepository {
    @PersistenceContext(unitName = "repuestosPU")
    private EntityManager em;

    public Resena guardar(Resena resena) {
        if (resena.getId() == null) {
            em.persist(resena);
            return resena;
        }
        return em.merge(resena);
    }

    public Optional<Resena> porId(Long id) {
        return Optional.ofNullable(em.find(Resena.class, id));
    }

    public List<Resena> aprobadasPorProducto(Long productoId) {
        return em.createNamedQuery("Resena.aprobadasPorProducto", Resena.class)
                 .setParameter("productoId", productoId)
                 .getResultList();
    }

    public List<Resena> porEstado(EstadoResena estado) {
        return em.createNamedQuery("Resena.porEstado", Resena.class)
                 .setParameter("estado", estado)
                 .getResultList();
    }

    public double promedioPuntaje(Long productoId) {
        Double promedio = em.createNamedQuery("Resena.promedioProducto", Double.class)
                            .setParameter("productoId", productoId)
                            .getSingleResult();
        return promedio == null ? 0d : promedio;
    }

    public long cantidadAprobadas(Long productoId) {
        return em.createNamedQuery("Resena.cantidadProducto", Long.class)
                 .setParameter("productoId", productoId)
                 .getSingleResult();
    }

    // Todo el catalogo en una consulta, en vez de dos por producto
    public Map<Long, ResumenResenas> resumenAprobadas() {
        Map<Long, ResumenResenas> resumen = new HashMap<>();
        for (Object[] fila : em.createNamedQuery("Resena.resumenPorProducto", Object[].class).getResultList()) {
            resumen.put((Long) fila[0], new ResumenResenas(((Number) fila[1]).doubleValue(), ((Number) fila[2]).longValue()));
        }
        return resumen;
    }
}
