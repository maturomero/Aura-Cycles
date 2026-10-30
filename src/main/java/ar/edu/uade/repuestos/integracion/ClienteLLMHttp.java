package ar.edu.uade.repuestos.integracion;

import jakarta.enterprise.context.Dependent;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import java.io.StringReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

// Messages API del proveedor. La clave sale de IA_API_KEY (nunca en el repo); sin clave, falla y el gateway usa el fallback
@Dependent
public class ClienteLLMHttp implements ClienteLLM {

    static final Duration TIMEOUT = Duration.ofSeconds(3);

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();

    @Override
    public String completar(String instrucciones, String datos) {
        String clave = Config.valor("IA_API_KEY", null);
        if (clave == null) {
            throw new IllegalStateException("Falta IA_API_KEY");
        }
        String cuerpo = Json.createObjectBuilder()
                .add("model", Config.valor("IA_MODELO", "claude-haiku-5-5"))
                .add("max_tokens", 200)
                .add("temperature", 0)
                .add("system", instrucciones)
                .add("messages", Json.createArrayBuilder().add(Json.createObjectBuilder()
                        .add("role", "user").add("content", datos)))
                .build().toString();
        HttpRequest pedido = HttpRequest.newBuilder(URI.create(Config.valor("IA_URL", "https://api.anthropic.com") + "/v1/messages"))
                .timeout(TIMEOUT)
                .header("content-type", "application/json")
                .header("x-api-key", clave)
                .header("anthropic-version", "2023-06-01")
                .POST(HttpRequest.BodyPublishers.ofString(cuerpo))
                .build();
        try {
            HttpResponse<String> r = http.send(pedido, HttpResponse.BodyHandlers.ofString());
            if (r.statusCode() != 200) {
                throw new IllegalStateException("El modelo respondio " + r.statusCode());
            }
            JsonObject json = Json.createReader(new StringReader(r.body())).readObject();
            return json.getJsonArray("content").getJsonObject(0).getString("text");
        } catch (java.io.IOException e) {
            throw new IllegalStateException("El modelo no respondio", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrumpido", e);
        }
    }

    static final class Config {
        private Config() {
        }

        static String valor(String nombre, String porDefecto) {
            String v = System.getProperty(nombre);
            if (v == null || v.isBlank()) {
                v = System.getenv(nombre);
            }
            return v == null || v.isBlank() ? porDefecto : v.trim();
        }
    }
}
