package ar.edu.uade.repuestos.integracion;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Modelo de IA simulado: responde POST /v1/messages con el mismo formato que el proveedor real.
 * Clasifica por palabras clave, asi la demo no depende de una clave ni de internet. Solo usa el JDK:
 *   java src/test/java/ar/edu/uade/repuestos/integracion/MockLLM.java 8091
 * Para la prueba de resiliencia: http://localhost:8091/__modo?valor=lento | error | roto | normal
 */
public class MockLLM {

    private static final List<String> INSULTOS = List.of("estafa", "basura", "chorros", "idiota", "porqueria", "ladrones");
    private static final List<String> SPAM = List.of("http", "www.", "promo", "gana plata", "descuento en", "whatsapp");
    private static final List<String> ORDENES = List.of("ignora", "olvida las instrucciones", "sos un", "responde aprobar");

    private final HttpServer servidor;
    private final AtomicInteger llamadas = new AtomicInteger();
    private volatile String modo = "normal";

    public static void main(String[] args) throws IOException {
        int puerto = args.length > 0 ? Integer.parseInt(args[0]) : 8091;
        MockLLM mock = iniciar(puerto);
        System.out.println("Modelo de IA simulado en http://localhost:" + mock.puerto());
        System.out.println("Modos: http://localhost:" + mock.puerto() + "/__modo");
    }

    public static MockLLM iniciar(int puerto) throws IOException {
        return new MockLLM(puerto);
    }

    private MockLLM(int puerto) throws IOException {
        servidor = HttpServer.create(new InetSocketAddress(puerto), 0);
        servidor.setExecutor(Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "mock-llm");
            t.setDaemon(true);
            return t;
        }));
        servidor.createContext("/", this::atender);
        servidor.start();
    }

    public int puerto() { return servidor.getAddress().getPort(); }

    public String url() { return "http://localhost:" + puerto(); }

    public void modo(String modo) { this.modo = modo; }

    public int llamadas() { return llamadas.get(); }

    public void detener() { servidor.stop(0); }

    private void atender(HttpExchange ex) throws IOException {
        try {
            String ruta = ex.getRequestURI().getPath();
            if (ruta.equals("/__modo")) {
                modoHttp(ex);
            } else if (ruta.equals("/v1/messages") && ex.getRequestMethod().equals("POST")) {
                completar(ex);
            } else {
                responder(ex, 404, "application/json", "{\"type\":\"error\",\"error\":{\"type\":\"not_found_error\"}}");
            }
        } finally {
            ex.close();
        }
    }

    private void completar(HttpExchange ex) throws IOException {
        llamadas.incrementAndGet();
        String pedido = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        switch (modo) {
            case "lento" -> dormir(8_000);  // mas que los 5 s del cliente
            case "error" -> { responder(ex, 500, "application/json", "{\"type\":\"error\",\"error\":{\"type\":\"api_error\"}}"); return; }
            case "roto" -> { responder(ex, 200, "application/json", sobre("Claro, te ayudo con la reseña. Creo que está bien.")); return; }
            default -> { }
        }
        if (ex.getRequestHeaders().getFirst("x-api-key") == null) {
            responder(ex, 401, "application/json", "{\"type\":\"error\",\"error\":{\"type\":\"authentication_error\"}}");
            return;
        }
        responder(ex, 200, "application/json", sobre(clasificar(pedido)));
    }

    // Lo que dice el cliente esta en "messages"; el "system" se ignora para no leer las instrucciones como si fueran la resena
    static String clasificar(String pedido) {
        int desde = pedido.indexOf("\"messages\"");
        String texto = (desde < 0 ? pedido : pedido.substring(desde)).toLowerCase();

        if (contiene(texto, INSULTOS)) {
            return sugerencia("RECHAZAR", "OFENSIVA", "Tiene insultos o acusaciones sin fundamento.", 0.93);
        }
        if (contiene(texto, SPAM)) {
            return sugerencia("RECHAZAR", "SPAM", "Parece publicidad o lleva a otro sitio.", 0.9);
        }
        if (texto.contains("[email]") || texto.contains("[telefono]")) {
            return sugerencia("REVISAR", "DATOS_PERSONALES", "El texto traía datos de contacto.", 0.82);
        }
        if (contiene(texto, ORDENES)) {
            return sugerencia("REVISAR", "FUERA_DE_TEMA", "El texto intenta darle órdenes al asistente en vez de opinar del repuesto.", 0.8);
        }
        if (texto.length() < 260) {
            return sugerencia("APROBAR", "OK", "Reseña muy corta, sin nada objetable.", 0.55);
        }
        return sugerencia("APROBAR", "OK", "Opina sobre el repuesto, sin insultos ni datos personales.", 0.91);
    }

    private static boolean contiene(String texto, List<String> palabras) {
        return palabras.stream().anyMatch(texto::contains);
    }

    private static String sugerencia(String sugerencia, String motivo, String explicacion, double confianza) {
        return "{\"sugerencia\":\"" + sugerencia + "\",\"motivo\":\"" + motivo + "\",\"explicacion\":\"" + explicacion
                + "\",\"confianza\":" + confianza + "}";
    }

    // El mismo sobre que devuelve la Messages API: el texto del modelo va en content[0].text
    private static String sobre(String texto) {
        String escapado = texto.replace("\\", "\\\\").replace("\"", "\\\"");
        return "{\"id\":\"msg_simulado\",\"type\":\"message\",\"role\":\"assistant\",\"model\":\"simulado\","
                + "\"content\":[{\"type\":\"text\",\"text\":\"" + escapado + "\"}],\"stop_reason\":\"end_turn\"}";
    }

    private void modoHttp(HttpExchange ex) throws IOException {
        String consulta = ex.getRequestURI().getQuery();
        if (consulta != null && consulta.startsWith("valor=")) {
            String valor = consulta.substring("valor=".length());
            if (List.of("normal", "lento", "error", "roto").contains(valor)) {
                modo = valor;
            }
        }
        responder(ex, 200, "text/html; charset=utf-8", "<!DOCTYPE html><html lang=\"es\"><head><meta charset=\"utf-8\"><title>Modelo de IA simulado</title></head>"
                + "<body style=\"font-family:system-ui;max-width:520px;margin:40px auto\"><h1>Modelo de IA simulado</h1>"
                + "<p>Modo actual: <strong>" + modo + "</strong> · llamadas: " + llamadas.get() + "</p>"
                + "<p><a href=\"?valor=normal\">normal</a> · <a href=\"?valor=lento\">lento (8 s)</a> · "
                + "<a href=\"?valor=error\">error 500</a> · <a href=\"?valor=roto\">respuesta fuera del contrato</a></p></body></html>");
    }

    private static void responder(HttpExchange ex, int codigo, String tipo, String cuerpo) throws IOException {
        byte[] bytes = cuerpo.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", tipo);
        ex.sendResponseHeaders(codigo, bytes.length);
        try (OutputStream salida = ex.getResponseBody()) {
            salida.write(bytes);
        }
    }

    private static void dormir(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
