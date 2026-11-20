package ar.edu.uade.repuestos.api;

import ar.edu.uade.repuestos.negocio.ServicioDeSalud;
import jakarta.annotation.security.PermitAll;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.json.Json;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

// GET /api/v1/salud: 200 si la base y el broker responden, 503 si alguno no. Sin token, como cualquier
// health check. Solo dice UP o DOWN por componente: ni versiones, ni hosts, ni mensajes de error
@Path("/salud")
@RequestScoped
@Produces(MediaType.APPLICATION_JSON)
public class SaludRecurso {

    @Inject
    private ServicioDeSalud servicioDeSalud;

    public SaludRecurso() {
    }

    SaludRecurso(ServicioDeSalud servicioDeSalud) {
        this.servicioDeSalud = servicioDeSalud;
    }

    @GET
    @PermitAll
    public Response salud() {
        ServicioDeSalud.Estado estado = servicioDeSalud.revisar();
        return Response.status(estado.todoBien() ? 200 : 503)
                .header("Cache-Control", "no-store")
                .entity(Json.createObjectBuilder()
                        .add("estado", texto(estado.todoBien()))
                        .add("base", texto(estado.base()))
                        .add("broker", texto(estado.broker()))
                        .build())
                .build();
    }

    private static String texto(boolean anda) {
        return anda ? "UP" : "DOWN";
    }
}
