package ar.edu.uade.repuestos.api;

import ar.edu.uade.repuestos.negocio.ServicioDeModeracion;
import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RunAs;
import jakarta.ejb.Stateless;
import jakarta.inject.Inject;

// La fachada le exige el rol MODERADOR al contenedor y en la API no hay sesion: el rol viene en el JWT.
// Este EJB corre como MODERADOR. Solo lo usa DevolucionesRecurso, despues de que el filtro valido el rol del token
@Stateless
@PermitAll
@RunAs("MODERADOR")
public class PuenteDeModeracion {

    @Inject
    private ServicioDeModeracion servicioDeModeracion;

    public void aprobar(Long devolucionId) {
        servicioDeModeracion.aprobarDevolucion(devolucionId);
    }

    public void rechazar(Long devolucionId) {
        servicioDeModeracion.rechazarDevolucion(devolucionId);
    }
}
