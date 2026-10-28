package ar.edu.uade.repuestos.integracion;

import jakarta.enterprise.context.Dependent;
import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonException;
import jakarta.json.JsonNumber;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonReader;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;
import java.io.IOException;
import java.io.StringReader;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

/**
 * CAPA DE INTEGRACION. Cliente REST de Mercado Pago (Checkout Pro).
 * Toda llamada corta a los 5 s y cualquier falla sale como MercadoPagoNoDisponibleException.
 */
@Dependent
public class ClienteMercadoPago {

    private static final Logger LOG = Logger.getLogger(ClienteMercadoPago.class.getName());
    private static final DateTimeFormatter FECHA_MP = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");

    private static final AtomicBoolean AVISADO = new AtomicBoolean();

    private final ConfiguracionMercadoPago config;
    private final HttpClient http;

    public ClienteMercadoPago() {
        this(ConfiguracionMercadoPago.desdeEntorno());
        // Una sola vez por arranque: en la consola de Eclipse se ve contra cual Mercado Pago estamos
        if (AVISADO.compareAndSet(false, true)) {
            LOG.info(() -> config.esSimulado()
                    ? "[MP] usando el Mercado Pago SIMULADO en " + config.urlBase() + " (no hay MP_ACCESS_TOKEN)"
                    : "[MP] usando Mercado Pago real: " + config.urlBase());
        }
    }

    public ClienteMercadoPago(ConfiguracionMercadoPago config) {
        this.config = config;
        this.http = HttpClient.newBuilder()
                .connectTimeout(config.timeout())
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    public ConfiguracionMercadoPago configuracion() {
        return config;
    }

    public PreferenciaMP crearPreferencia(String referencia, String codigo, String titulo, int cantidad,
                                          BigDecimal precioUnitario, String urlRetorno,
                                          OffsetDateTime desde, OffsetDateTime vence) {
        JsonObjectBuilder cuerpo = Json.createObjectBuilder()
                .add("items", Json.createArrayBuilder().add(Json.createObjectBuilder()
                        .add("id", codigo)
                        .add("title", titulo)
                        .add("quantity", cantidad)
                        .add("unit_price", precioUnitario)
                        .add("currency_id", "ARS")))
                .add("external_reference", referencia)
                .add("back_urls", Json.createObjectBuilder()
                        .add("success", urlRetorno)
                        .add("failure", urlRetorno)
                        .add("pending", urlRetorno))
                .add("binary_mode", true)
                .add("expires", true)
                .add("expiration_date_from", FECHA_MP.format(desde))
                .add("expiration_date_to", FECHA_MP.format(vence));
        if (!config.urlAppEsLocal()) {
            cuerpo.add("auto_return", "approved");
        }

        JsonObject r = objeto(enviar("crear preferencia", post("/checkout/preferences", cuerpo.build(), null), false));
        String url = texto(r, "init_point");
        if (url == null) {
            url = texto(r, "sandbox_init_point");
        }
        if (texto(r, "id") == null || url == null) {
            throw new MercadoPagoNoDisponibleException("Mercado Pago no devolvio la URL de pago");
        }
        return new PreferenciaMP(texto(r, "id"), url);
    }

    // El id puede venir de la URL de vuelta: si no es un numero no se consulta nada
    public Optional<PagoMP> obtenerPago(String pagoId) {
        if (pagoId == null || !pagoId.matches("\\d{1,20}")) {
            return Optional.empty();
        }
        String cuerpo = enviar("consultar pago", get("/v1/payments/" + pagoId), true);
        return cuerpo == null ? Optional.empty() : Optional.of(pago(objeto(cuerpo)));
    }

    // Mas nuevos primero
    public List<PagoMP> buscarPagos(String referencia) {
        String ruta = "/v1/payments/search?sort=date_created&criteria=desc&external_reference="
                + URLEncoder.encode(referencia, StandardCharsets.UTF_8);
        JsonValue valor = leer(enviar("buscar pagos", get(ruta), false));
        if (valor.getValueType() == JsonValue.ValueType.ARRAY) {
            valor = ((JsonArray) valor).get(0);
        }
        List<PagoMP> pagos = new ArrayList<>();
        try {
            for (JsonValue v : valor.asJsonObject().getJsonArray("results")) {
                pagos.add(pago(v.asJsonObject()));
            }
        } catch (RuntimeException e) {
            throw new MercadoPagoNoDisponibleException("Mercado Pago devolvio una busqueda invalida", e);
        }
        return pagos;
    }

    // La misma clave devuelve el mismo reembolso: si se reintenta, no se devuelve dos veces
    public ReembolsoMP reembolsar(String pagoId, BigDecimal monto, String claveIdempotencia) {
        JsonObject cuerpo = Json.createObjectBuilder().add("amount", monto).build();
        JsonObject r = objeto(enviar("reembolsar",
                post("/v1/payments/" + pagoId + "/refunds", cuerpo, claveIdempotencia), false));
        if (texto(r, "id") == null) {
            throw new MercadoPagoNoDisponibleException("Mercado Pago no devolvio el id del reembolso");
        }
        return new ReembolsoMP(texto(r, "id"), texto(r, "status"), numero(r, "amount"));
    }

    // ---------- HTTP ----------

    private HttpRequest.Builder base(String ruta) {
        if (config.accessToken() == null) {
            throw new MercadoPagoNoDisponibleException("Falta configurar MP_ACCESS_TOKEN");
        }
        return HttpRequest.newBuilder(URI.create(config.urlBase() + ruta))
                .timeout(config.timeout())
                .header("Authorization", "Bearer " + config.accessToken())
                .header("Accept", "application/json");
    }

    private HttpRequest get(String ruta) {
        return base(ruta).GET().build();
    }

    private HttpRequest post(String ruta, JsonObject cuerpo, String claveIdempotencia) {
        HttpRequest.Builder b = base(ruta)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(cuerpo.toString()));
        if (claveIdempotencia != null) {
            b.header("X-Idempotency-Key", claveIdempotencia);
        }
        return b.build();
    }

    // Devuelve el cuerpo; null solo si es un 404 y se pidio tolerarlo
    private String enviar(String operacion, HttpRequest pedido, boolean null404) {
        HttpResponse<String> r;
        try {
            r = http.send(pedido, HttpResponse.BodyHandlers.ofString());
        } catch (HttpTimeoutException e) {
            LOG.warning(() -> "[MP] " + operacion + ": sin respuesta en " + config.timeout().toSeconds() + " s");
            throw new MercadoPagoNoDisponibleException("Mercado Pago no respondio a tiempo", e);
        } catch (IOException e) {
            LOG.warning(() -> "[MP] " + operacion + ": no se pudo conectar (" + e.getClass().getSimpleName() + ")");
            throw new MercadoPagoNoDisponibleException("No se pudo conectar con Mercado Pago", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new MercadoPagoNoDisponibleException("Se interrumpio la llamada a Mercado Pago", e);
        }

        int status = r.statusCode();
        if (status == 404 && null404) {
            return null;
        }
        if (status < 200 || status >= 300) {
            LOG.warning(() -> "[MP] " + operacion + ": HTTP " + status);
            String motivo = status == 401 || status == 403 ? "credenciales rechazadas" : "HTTP " + status;
            throw new MercadoPagoNoDisponibleException("Mercado Pago respondio con error (" + motivo + ")");
        }
        return r.body();
    }

    // ---------- JSON ----------

    private static JsonValue leer(String cuerpo) {
        try (JsonReader lector = Json.createReader(new StringReader(cuerpo))) {
            return lector.readValue();
        } catch (JsonException | IllegalStateException e) {
            LOG.warning("[MP] respuesta que no es JSON");
            throw new MercadoPagoNoDisponibleException("Mercado Pago devolvio una respuesta invalida", e);
        }
    }

    private static JsonObject objeto(String cuerpo) {
        JsonValue v = leer(cuerpo);
        if (v.getValueType() != JsonValue.ValueType.OBJECT) {
            throw new MercadoPagoNoDisponibleException("Mercado Pago devolvio una respuesta invalida");
        }
        return v.asJsonObject();
    }

    private static PagoMP pago(JsonObject o) {
        String id = texto(o, "id");
        if (id == null || texto(o, "status") == null) {
            throw new MercadoPagoNoDisponibleException("Mercado Pago devolvio un pago sin id o sin estado");
        }
        return new PagoMP(id, texto(o, "status"), texto(o, "external_reference"),
                numero(o, "transaction_amount"), texto(o, "currency_id"));
    }

    // MP manda algunos ids como numero y otros como texto
    private static String texto(JsonObject o, String campo) {
        JsonValue v = o.get(campo);
        if (v instanceof JsonString s) {
            return s.getString();
        }
        if (v instanceof JsonNumber n) {
            return n.bigDecimalValue().toPlainString();
        }
        return null;
    }

    private static BigDecimal numero(JsonObject o, String campo) {
        JsonValue v = o.get(campo);
        try {
            if (v instanceof JsonNumber n) {
                return n.bigDecimalValue();
            }
            if (v instanceof JsonString s) {
                return new BigDecimal(s.getString());
            }
        } catch (NumberFormatException e) {
            throw new MercadoPagoNoDisponibleException("Mercado Pago devolvio un monto invalido", e);
        }
        return null;
    }
}
