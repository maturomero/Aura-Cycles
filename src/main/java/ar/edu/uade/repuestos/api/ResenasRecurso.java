package ar.edu.uade.repuestos.api;

import ar.edu.uade.repuestos.modelo.Resena;
import ar.edu.uade.repuestos.negocio.ServicioDeProductos;
import ar.edu.uade.repuestos.negocio.ServicioDeResenas;
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.json.Json;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import java.util.List;

@Path("/productos/{productoId}/resenas")
@RequestScoped
@Produces(MediaType.APPLICATION_JSON)
public class ResenasRecurso {

    @Inject
    private ServicioDeResenas servicioDeResenas;

    @Inject
    private ServicioDeProductos servicioDeProductos;

    @Context
    private SecurityContext seguridad;

    public ResenasRecurso() {
    }

    ResenasRecurso(ServicioDeResenas servicioDeResenas, ServicioDeProductos servicioDeProductos, SecurityContext seguridad) {
        this.servicioDeResenas = servicioDeResenas;
        this.servicioDeProductos = servicioDeProductos;
        this.seguridad = seguridad;
    }

    // Solo las aprobadas por un moderador
    @GET
    @RolesAllowed({"CLIENTE", "MODERADOR"})
    public Response publicadas(@PathParam("productoId") long productoId) {
        servicioDeProductos.porId(productoId);
        List<Resena> lista = servicioDeResenas.publicadasDe(productoId);

        JsonArrayBuilder items = Json.createArrayBuilder();
        lista.forEach(r -> items.add(json(r, false)));
        return Response.ok(Json.createObjectBuilder().add("total", lista.size()).add("items", items).build().toString()).build();
    }

    // Queda PENDIENTE hasta que la revise un moderador. El autor sale del token, no del cuerpo
    @POST
    @RolesAllowed("CLIENTE")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response escribir(@PathParam("productoId") long productoId, String texto) {
        Cuerpo cuerpo = Cuerpo.leer(texto);
        long puntaje = cuerpo.entero("puntaje");
        if (puntaje < 1 || puntaje > 5) {
            throw new PedidoInvalidoException("El puntaje va de 1 a 5.");
        }
        servicioDeProductos.porId(productoId);

        Sesion sesion = (Sesion) seguridad.getUserPrincipal();
        Resena resena = servicioDeResenas.escribir(sesion.usuarioId(), productoId, (int) puntaje,
                cuerpo.texto("titulo"), cuerpo.textoObligatorio("texto"));
        return Response.status(Response.Status.CREATED).entity(json(resena, true).toString()).build();
    }

    private static JsonObject json(Resena r, boolean conEstado) {
        JsonObjectBuilder json = Json.createObjectBuilder()
                .add("id", r.getId())
                .add("codigo", r.getCodigo())
                .add("puntaje", r.getPuntaje())
                .add("titulo", r.getTitulo() == null ? "" : r.getTitulo())
                .add("texto", r.getTexto())
                .add("autor", r.getUsuario().getNombre())
                .add("fecha", DevolucionJson.fecha(r.getFecha()));
        if (conEstado) {
            json.add("estado", r.getEstado().name());
        }
        return json.build();
    }
}
