package ar.edu.uade.repuestos.api;

import ar.edu.uade.repuestos.util.ClaveRepetidaException;
import ar.edu.uade.repuestos.util.MensajeriaNoDisponibleException;
import ar.edu.uade.repuestos.util.NoExisteException;
import ar.edu.uade.repuestos.util.ReglaNegocioException;
import ar.edu.uade.repuestos.util.TransicionInvalidaException;
import jakarta.ejb.EJBAccessException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import java.util.logging.Level;
import java.util.logging.Logger;

// Toda excepcion que sale de la API termina en un Problem Details. Nunca un stack trace
@Provider
@ApplicationScoped
public class ErroresDeLaApi implements ExceptionMapper<Throwable> {

    private static final Logger LOG = Logger.getLogger(ErroresDeLaApi.class.getName());

    @Context
    private UriInfo uri;

    public ErroresDeLaApi() {
    }

    ErroresDeLaApi(UriInfo uri) {
        this.uri = uri;
    }

    @Override
    public Response toResponse(Throwable error) {
        String ruta = uri == null ? "" : uri.getPath();
        Throwable causa = causaConocida(error);

        if (causa instanceof TransicionInvalidaException) {
            return Problema.de(409, "transicion-invalida", "Transición inválida", causa.getMessage(), ruta).build();
        }
        if (causa instanceof ClaveRepetidaException) {
            return Problema.de(409, "clave-repetida", "Idempotency-Key repetida", causa.getMessage(), ruta).build();
        }
        if (causa instanceof NoExisteException) {
            return Problema.de(404, "no-existe", "No existe", causa.getMessage(), ruta).build();
        }
        if (causa instanceof MensajeriaNoDisponibleException) {
            return Problema.de(503, "mensajeria-caida", "Servicio no disponible", causa.getMessage(), ruta)
                    .header(HttpHeaders.RETRY_AFTER, 30).build();
        }
        if (causa instanceof ReglaNegocioException) {
            return Problema.de(422, "regla-de-negocio", "No se puede procesar", causa.getMessage(), ruta).build();
        }
        if (causa instanceof PedidoInvalidoException) {
            return Problema.de(400, "pedido-invalido", "Pedido inválido", causa.getMessage(), ruta).build();
        }
        if (causa instanceof EJBAccessException) {
            return Problema.de(403, "sin-permiso", "Sin permiso", "Tu rol no puede hacer esta operación.", ruta).build();
        }
        if (causa instanceof WebApplicationException web) {
            return deJaxRs(web, ruta);
        }

        // La ruta no va al log: la escribe el cliente y podria meter saltos de linea
        LOG.log(Level.SEVERE, "[API] error no previsto", error);
        return Problema.de(500, "error-interno", "Error interno", "Algo falló de nuestro lado. Probá de nuevo en un rato.", ruta).build();
    }

    // Lo que el contenedor envuelve (EJBException) se desenvuelve hasta encontrar un error nuestro
    private static Throwable causaConocida(Throwable error) {
        for (Throwable t = error; t != null; t = t.getCause()) {
            if (t instanceof ReglaNegocioException || t instanceof PedidoInvalidoException
                    || t instanceof WebApplicationException || t instanceof EJBAccessException) {
                return t;
            }
            if (t.getCause() == t) {
                break;
            }
        }
        return error;
    }

    // Ruta que no existe, metodo no permitido, JSON mal armado, precondicion que no se cumple
    private static Response deJaxRs(WebApplicationException web, String ruta) {
        int estado = web.getResponse().getStatus();
        String titulo = switch (estado) {
            case 400 -> "Pedido inválido";
            case 404 -> "No existe";
            case 405 -> "Método no permitido";
            case 406, 415 -> "Formato no soportado";
            case 412 -> "Precondición fallida";
            default -> estado >= 500 ? "Error interno" : "Pedido rechazado";
        };
        String detalle = switch (estado) {
            case 400 -> "El cuerpo del pedido no es un JSON válido.";
            case 404 -> "Esa ruta no existe en la API.";
            case 412 -> "El recurso cambió desde que lo leíste. Volvé a pedirlo y usá el ETag nuevo.";
            case 406, 415 -> "La API recibe y devuelve application/json.";
            default -> titulo + ".";
        };
        Response.ResponseBuilder respuesta = Problema.de(estado, "http-" + estado, titulo, detalle, ruta);
        Object etag = web.getResponse().getHeaders().getFirst(HttpHeaders.ETAG);
        if (etag != null) {
            respuesta.header(HttpHeaders.ETAG, etag);
        }
        return respuesta.build();
    }
}
