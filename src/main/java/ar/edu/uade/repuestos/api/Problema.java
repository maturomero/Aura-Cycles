package ar.edu.uade.repuestos.api;

import jakarta.json.Json;
import jakarta.ws.rs.core.Response;

// Errores de la API en formato Problem Details (RFC 9457)
public final class Problema {

    public static final String TIPO = "application/problem+json";
    public static final String BASE = "/repuestos/api/v1/";

    private Problema() {
    }

    public static Response.ResponseBuilder de(int estado, String clave, String titulo, String detalle, String ruta) {
        return armar(estado, clave, titulo, detalle, BASE + (ruta == null ? "" : ruta.replaceFirst("^/+", "")));
    }

    private static Response.ResponseBuilder armar(int estado, String clave, String titulo, String detalle, String instancia) {
        String cuerpo = Json.createObjectBuilder()
                .add("type", "urn:auracycles:problema:" + clave)
                .add("title", titulo)
                .add("status", estado)
                .add("detail", detalle)
                .add("instance", instancia)
                .build().toString();
        return Response.status(estado).type(TIPO).entity(cuerpo);
    }
}
