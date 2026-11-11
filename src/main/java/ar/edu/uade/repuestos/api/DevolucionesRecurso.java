package ar.edu.uade.repuestos.api;

import ar.edu.uade.repuestos.modelo.Devolucion;
import ar.edu.uade.repuestos.modelo.EstadoDevolucion;
import ar.edu.uade.repuestos.modelo.MotivoDevolucion;
import ar.edu.uade.repuestos.negocio.ServicioDeSolicitudes;
import ar.edu.uade.repuestos.negocio.ServicioDeSolicitudes.Solicitud;
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.json.Json;
import jakarta.json.JsonArrayBuilder;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.EntityTag;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Request;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import java.net.URI;
import java.util.List;
import java.util.regex.Pattern;

// Devoluciones por REST. El recurso no tiene reglas: valida el formato del pedido y delega en la capa de negocio
@Path("/devoluciones")
@RequestScoped
@Produces(MediaType.APPLICATION_JSON)
public class DevolucionesRecurso {

    private static final Pattern CLAVE = Pattern.compile("[A-Za-z0-9._-]{8,80}");

    @Inject
    private ServicioDeSolicitudes solicitudes;

    @Inject
    private PuenteDeModeracion moderacion;

    @Context
    private SecurityContext seguridad;

    @Context
    private Request pedido;

    public DevolucionesRecurso() {
    }

    DevolucionesRecurso(ServicioDeSolicitudes solicitudes, PuenteDeModeracion moderacion, SecurityContext seguridad, Request pedido) {
        this.solicitudes = solicitudes;
        this.moderacion = moderacion;
        this.seguridad = seguridad;
        this.pedido = pedido;
    }

    // Reintentar con la misma Idempotency-Key devuelve la misma devolucion, no crea otra
    @POST
    @RolesAllowed("CLIENTE")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response crear(@HeaderParam("Idempotency-Key") String clave, String texto) {
        if (clave == null || !CLAVE.matcher(clave).matches()) {
            throw new PedidoInvalidoException("Falta el header Idempotency-Key (8 a 80 letras, números, punto, guion o guion bajo).");
        }
        Cuerpo cuerpo = Cuerpo.leer(texto);
        long ventaId = cuerpo.entero("ventaId");
        MotivoDevolucion motivo = cuerpo.opcion("motivo", MotivoDevolucion.class);
        String comentario = cuerpo.texto("comentario");

        Solicitud solicitud = solicitudes.solicitar(sesion().email(), clave, ventaId, motivo, comentario);
        Devolucion devolucion = solicitud.devolucion();

        Response.ResponseBuilder respuesta = Response.status(solicitud.codigoHttp())
                .entity(DevolucionJson.de(devolucion).toString())
                .location(URI.create(DevolucionJson.RUTA + devolucion.getId()))
                .tag(DevolucionJson.etag(devolucion));
        if (solicitud.codigoHttp() == ServicioDeSolicitudes.ACEPTADA) {
            // El proveedor no contesto: la garantia se reintenta sola a los 2 minutos
            respuesta.header(HttpHeaders.RETRY_AFTER, 120);
        }
        if (solicitud.repetida()) {
            respuesta.header("Idempotent-Replayed", "true");
        }
        return respuesta.build();
    }

    @GET
    @Path("/{id}")
    @RolesAllowed({"CLIENTE", "MODERADOR"})
    public Response ver(@PathParam("id") long id) {
        Devolucion devolucion = solicitudes.ver(id, sesion().email(), sesion().esModerador());
        EntityTag etag = DevolucionJson.etag(devolucion);

        // If-None-Match con el ETag vigente: 304 sin cuerpo
        Response.ResponseBuilder sinCambios = pedido.evaluatePreconditions(etag);
        if (sinCambios != null) {
            return sinCambios.tag(etag).build();
        }
        return Response.ok(DevolucionJson.de(devolucion).toString()).tag(etag).build();
    }

    // El cliente ve las suyas; el moderador, la cola (por defecto las pendientes)
    @GET
    @RolesAllowed({"CLIENTE", "MODERADOR"})
    public Response listar(@QueryParam("estado") String estado) {
        EstadoDevolucion filtro = estado(estado);
        List<Devolucion> lista = sesion().esModerador()
                ? solicitudes.paraModerar(filtro)
                : solicitudes.delCliente(sesion().usuarioId(), filtro);

        JsonArrayBuilder items = Json.createArrayBuilder();
        lista.forEach(d -> items.add(DevolucionJson.de(d)));
        return Response.ok(Json.createObjectBuilder().add("total", lista.size()).add("items", items).build().toString()).build();
    }

    // Aprobar o rechazar. Exige If-Match; si el estado no admite el cambio, el State lo rechaza y sale un 409
    @PATCH
    @Path("/{id}")
    @RolesAllowed("MODERADOR")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response resolver(@PathParam("id") long id, @HeaderParam(HttpHeaders.IF_MATCH) String ifMatch, String texto) {
        String ruta = DevolucionJson.RUTA + id;
        EstadoDevolucion nuevo = Cuerpo.leer(texto).opcion("estado", EstadoDevolucion.class);
        if (nuevo != EstadoDevolucion.APROBADA && nuevo != EstadoDevolucion.RECHAZADA) {
            throw new PedidoInvalidoException("Por la API una devolución solo se puede pasar a APROBADA o RECHAZADA.");
        }

        Devolucion actual = solicitudes.ver(id, sesion().email(), true);
        if (ifMatch == null || ifMatch.isBlank()) {
            return Problema.de(428, "falta-if-match", "Falta la precondición",
                    "Mandá en If-Match el ETag que te dio el GET, así no pisás el cambio de otro moderador.", ruta).build();
        }
        if (pedido.evaluatePreconditions(DevolucionJson.etag(actual)) != null) {
            return Problema.de(412, "etag-viejo", "Precondición fallida",
                    "La devolución cambió desde que la leíste. Volvé a pedirla y usá el ETag nuevo.", ruta)
                    .tag(DevolucionJson.etag(actual)).build();
        }

        if (nuevo == EstadoDevolucion.APROBADA) {
            moderacion.aprobar(id);
        } else {
            moderacion.rechazar(id);
        }

        Devolucion resuelta = solicitudes.ver(id, sesion().email(), true);
        return Response.ok(DevolucionJson.de(resuelta).toString()).tag(DevolucionJson.etag(resuelta)).build();
    }

    private Sesion sesion() {
        return (Sesion) seguridad.getUserPrincipal();
    }

    private static EstadoDevolucion estado(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        try {
            return EstadoDevolucion.valueOf(valor.trim());
        } catch (IllegalArgumentException e) {
            throw new PedidoInvalidoException("Ese estado no existe.");
        }
    }
}
