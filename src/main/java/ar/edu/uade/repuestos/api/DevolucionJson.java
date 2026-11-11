package ar.edu.uade.repuestos.api;

import ar.edu.uade.repuestos.modelo.Devolucion;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.ws.rs.core.EntityTag;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;

// Como se ve una devolucion en la API. No lleva datos del cliente ni del pago
final class DevolucionJson {

    static final String RUTA = "devoluciones/";

    private DevolucionJson() {
    }

    static JsonObject de(Devolucion d) {
        JsonObjectBuilder json = Json.createObjectBuilder()
                .add("id", d.getId())
                .add("codigo", d.getCodigo())
                .add("estado", d.getEstado().name())
                .add("motivo", d.getMotivo().name())
                .add("ventaId", d.getVenta().getId())
                .add("orden", d.getVenta().getNumeroOrden())
                .add("producto", d.getVenta().getProducto().getNombre())
                .add("importe", d.getVenta().getImporte())
                .add("validadaConProveedor", !d.isSinValidar() && d.getRma() != null)
                .add("fechaSolicitud", fecha(d.getFechaSolicitud()));
        agregar(json, "comentario", d.getComentario());
        agregar(json, "rma", d.getRma());
        agregar(json, "fechaResolucion", fecha(d.getFechaResolucion()));
        if (d.getMontoReembolso() == null) {
            json.addNull("montoReembolso");
        } else {
            json.add("montoReembolso", d.getMontoReembolso());
        }
        return json.add("_links", Json.createObjectBuilder()
                        .add("self", Json.createObjectBuilder().add("href", Problema.BASE + RUTA + d.getId())))
                .build();
    }

    // Cambia cada vez que cambia algo que el cliente ve. Con esto se arma el If-Match
    static EntityTag etag(Devolucion d) {
        String contenido = d.getId() + "|" + d.getEstado() + "|" + d.getRma() + "|" + d.getMontoReembolso()
                + "|" + d.getMpReembolsoId() + "|" + d.getFechaResolucion() + "|" + d.getIntentosValidacion();
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(contenido.getBytes(StandardCharsets.UTF_8));
            return new EntityTag(HexFormat.of().formatHex(hash, 0, 8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    static String fecha(LocalDateTime fecha) {
        return fecha == null ? null : fecha.truncatedTo(ChronoUnit.SECONDS).toString();
    }

    private static void agregar(JsonObjectBuilder json, String campo, String valor) {
        if (valor == null) {
            json.addNull(campo);
        } else {
            json.add(campo, valor);
        }
    }
}
