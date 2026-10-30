package ar.edu.uade.repuestos.integracion;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Mercado Pago simulado: preferencias, checkout, pagos, busqueda y reembolsos.
 * Solo usa el JDK, asi se corre sin compilar nada:
 *   java src/test/java/ar/edu/uade/repuestos/integracion/MockMercadoPago.java 8089
 * Para la prueba de resiliencia: http://localhost:8089/__modo?valor=lento | error | roto | normal
 */
public class MockMercadoPago {

    private final HttpServer servidor;
    private volatile String modo = "normal";

    private final Map<String, Map<String, Object>> preferencias = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Object>> pagos = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Object>> reembolsosPorClave = new ConcurrentHashMap<>();
    private final AtomicLong idPago = new AtomicLong(1_000_000_000L);
    private final AtomicLong idPreferencia = new AtomicLong();
    private final AtomicLong idReembolso = new AtomicLong(5_000_000L);
    private final AtomicInteger llamadasApi = new AtomicInteger();

    public static void main(String[] args) throws IOException {
        int puerto = args.length > 0 ? Integer.parseInt(args[0]) : 8089;
        MockMercadoPago mock = iniciar(puerto);
        System.out.println("Mercado Pago simulado en http://localhost:" + mock.puerto());
        System.out.println("Modos: http://localhost:" + mock.puerto() + "/__modo");
    }

    public static MockMercadoPago iniciar(int puerto) throws IOException {
        return new MockMercadoPago(puerto);
    }

    private MockMercadoPago(int puerto) throws IOException {
        servidor = HttpServer.create(new InetSocketAddress(puerto), 0);
        servidor.setExecutor(Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "mock-mp");
            t.setDaemon(true);
            return t;
        }));
        servidor.createContext("/", this::atender);
        servidor.start();
    }

    // ---------- API para los tests ----------

    public int puerto() { return servidor.getAddress().getPort(); }

    public String url() { return "http://localhost:" + puerto(); }

    public void modo(String modo) { this.modo = modo; }

    public int llamadasApi() { return llamadasApi.get(); }

    public Map<String, Object> preferencia(String id) { return preferencias.get(id); }

    public int reembolsosCreados() { return reembolsosPorClave.size(); }

    // Lo mismo que hace el comprador al tocar un boton del checkout
    public String pagar(String preferenciaId, String estado) {
        return crearPago(preferencias.get(preferenciaId), estado).get("id").toString();
    }

    public void detener() { servidor.stop(0); }

    // ---------- ruteo ----------

    private void atender(HttpExchange ex) throws IOException {
        try {
            String metodo = ex.getRequestMethod();
            String ruta = ex.getRequestURI().getPath();

            if (ruta.equals("/__modo")) {
                modoHttp(ex);
            } else if (ruta.equals("/checkout") && metodo.equals("GET")) {
                paginaCheckout(ex);
            } else if (ruta.equals("/checkout/pagar") && metodo.equals("GET")) {
                pagarDesdeCheckout(ex);
            } else if (ruta.equals("/checkout/preferences") || ruta.startsWith("/v1/payments")) {
                api(ex, metodo, ruta);
            } else {
                json(ex, 404, Map.of("message", "not_found", "status", 404));
            }
        } catch (RuntimeException e) {
            json(ex, 400, Map.of("message", "bad_request: " + e.getMessage(), "status", 400));
        } finally {
            ex.close();
        }
    }

    private void api(HttpExchange ex, String metodo, String ruta) throws IOException {
        llamadasApi.incrementAndGet();
        switch (modo) {
            case "lento" -> dormir(8_000);  // mas que los 5 s del cliente
            case "error" -> { json(ex, 500, Map.of("message", "internal_error", "status", 500)); return; }
            case "roto" -> { responder(ex, 200, "text/html", "<html>503 Service Temporarily Unavailable</html>"); return; }
            default -> { }
        }

        String auth = ex.getRequestHeaders().getFirst("Authorization");
        if (auth == null || !(auth.startsWith("Bearer TEST-") || auth.startsWith("Bearer APP_USR-"))) {
            json(ex, 401, Map.of("message", "invalid access token", "status", 401));
            return;
        }

        if (ruta.equals("/checkout/preferences") && metodo.equals("POST")) {
            crearPreferencia(ex);
        } else if (ruta.equals("/v1/payments/search") && metodo.equals("GET")) {
            buscarPagos(ex);
        } else if (ruta.matches("/v1/payments/\\d+/refunds") && metodo.equals("POST")) {
            reembolsar(ex, ruta.split("/")[3]);
        } else if (ruta.matches("/v1/payments/\\d+") && metodo.equals("GET")) {
            Map<String, Object> pago = pagos.get(ruta.split("/")[3]);
            if (pago == null) {
                json(ex, 404, Map.of("message", "Payment not found", "status", 404));
            } else {
                json(ex, 200, pago);
            }
        } else {
            json(ex, 404, Map.of("message", "not_found", "status", 404));
        }
    }

    // ---------- endpoints ----------

    @SuppressWarnings("unchecked")
    private void crearPreferencia(HttpExchange ex) throws IOException {
        Map<String, Object> cuerpo = (Map<String, Object>) Json.leer(cuerpo(ex));
        List<Object> items = (List<Object>) cuerpo.get("items");
        if (items == null || items.isEmpty() || cuerpo.get("external_reference") == null) {
            json(ex, 400, Map.of("message", "items and external_reference are required", "status", 400));
            return;
        }
        String id = "mock-pref-" + idPreferencia.incrementAndGet();
        Map<String, Object> pref = new LinkedHashMap<>(cuerpo);
        pref.put("id", id);
        preferencias.put(id, pref);

        // En Docker el navegador no ve "mercadopago-mock": MOCK_URL_PUBLICA dice como llegar desde afuera
        String publica = System.getenv("MOCK_URL_PUBLICA");
        String base = publica != null && !publica.isBlank() ? publica : "http://" + ex.getRequestHeaders().getFirst("Host");
        String checkout = base + "/checkout?pref=" + id;
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("id", id);
        r.put("init_point", checkout);
        r.put("sandbox_init_point", checkout);
        r.put("external_reference", cuerpo.get("external_reference"));
        json(ex, 201, r);
    }

    private void buscarPagos(HttpExchange ex) throws IOException {
        String referencia = parametros(ex).get("external_reference");
        List<Map<String, Object>> encontrados = new ArrayList<>();
        for (Map<String, Object> p : pagos.values()) {
            if (referencia == null || referencia.equals(p.get("external_reference"))) {
                encontrados.add(p);
            }
        }
        encontrados.sort((a, b) -> Long.compare((Long) b.get("id"), (Long) a.get("id")));
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("paging", Map.of("total", encontrados.size(), "limit", 30, "offset", 0));
        r.put("results", encontrados);
        json(ex, 200, r);
    }

    @SuppressWarnings("unchecked")
    private void reembolsar(HttpExchange ex, String pagoId) throws IOException {
        String clave = ex.getRequestHeaders().getFirst("X-Idempotency-Key");
        if (clave == null || clave.isBlank()) {
            json(ex, 400, Map.of("message", "X-Idempotency-Key header is required", "status", 400));
            return;
        }
        synchronized (this) {
            Map<String, Object> repetido = reembolsosPorClave.get(clave);
            if (repetido != null) {
                json(ex, 201, repetido);
                return;
            }
            Map<String, Object> pago = pagos.get(pagoId);
            if (pago == null) {
                json(ex, 404, Map.of("message", "Payment not found", "status", 404));
                return;
            }
            if (!"approved".equals(pago.get("status"))) {
                json(ex, 400, Map.of("message", "Payment status is not approved", "status", 400));
                return;
            }
            BigDecimal total = (BigDecimal) pago.get("transaction_amount");
            BigDecimal yaDevuelto = (BigDecimal) pago.get("transaction_amount_refunded");
            String texto = cuerpo(ex);
            Map<String, Object> cuerpo = texto.isBlank() ? Map.of() : (Map<String, Object>) Json.leer(texto);
            BigDecimal monto = cuerpo.get("amount") == null ? total.subtract(yaDevuelto)
                    : new BigDecimal(cuerpo.get("amount").toString());
            if (monto.signum() <= 0 || yaDevuelto.add(monto).compareTo(total) > 0) {
                json(ex, 400, Map.of("message", "Invalid refund amount", "status", 400));
                return;
            }
            BigDecimal devuelto = yaDevuelto.add(monto);
            pago.put("transaction_amount_refunded", devuelto);
            if (devuelto.compareTo(total) == 0) {
                pago.put("status", "refunded");
            }
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("id", idReembolso.incrementAndGet());
            r.put("payment_id", Long.parseLong(pagoId));
            r.put("amount", monto);
            r.put("status", "approved");
            r.put("date_created", OffsetDateTime.now().toString());
            reembolsosPorClave.put(clave, r);
            json(ex, 201, r);
        }
    }

    @SuppressWarnings("unchecked")
    private void paginaCheckout(HttpExchange ex) throws IOException {
        Map<String, Object> pref = preferencias.get(parametros(ex).get("pref"));
        if (pref == null) {
            html(ex, 404, "<h2>No encontramos ese link de pago</h2>");
            return;
        }
        if (vencida(pref)) {
            html(ex, 200, "<h2>Este link de pago venci&oacute;</h2><p>Volv&eacute; a la tienda y hac&eacute; la compra de nuevo.</p>");
            return;
        }
        Map<String, Object> item = (Map<String, Object>) ((List<Object>) pref.get("items")).get(0);
        String id = (String) pref.get("id");
        String pagina = """
                <h2 style="margin:0 0 4px">Mercado Pago <small style="color:#888">(simulado)</small></h2>
                <p style="color:#555;margin:0 0 18px">En el Mercado Pago real se paga con la tarjeta de prueba: titular APRO aprueba, OTHE rechaza.</p>
                <div style="border:1px solid #ddd;border-radius:8px;padding:16px;margin-bottom:18px">
                  <div>%s x %s</div>
                  <div style="font-size:26px;font-weight:600;margin-top:6px">$ %s</div>
                </div>
                <a href="/checkout/pagar?pref=%s&amp;resultado=approved" style="%s;background:#009ee3;color:#fff">Pagar (aprobado)</a>
                <a href="/checkout/pagar?pref=%s&amp;resultado=rejected" style="%s;background:#fff;color:#c0392b;border:1px solid #c0392b">Pagar (rechazado)</a>
                <a href="/checkout/pagar?pref=%s&amp;resultado=volver" style="%s;color:#555">Volver a la tienda sin pagar</a>
                """.formatted(esc(item.get("title")), item.get("quantity"), total(pref).toPlainString(),
                id, BOTON, id, BOTON, id, BOTON);
        html(ex, 200, pagina);
    }

    @SuppressWarnings("unchecked")
    private void pagarDesdeCheckout(HttpExchange ex) throws IOException {
        Map<String, String> q = parametros(ex);
        Map<String, Object> pref = preferencias.get(q.get("pref"));
        if (pref == null || vencida(pref)) {
            html(ex, 400, "<h2>Este link de pago no es v&aacute;lido o venci&oacute;</h2>");
            return;
        }
        Map<String, Object> urls = (Map<String, Object>) pref.get("back_urls");
        String resultado = q.getOrDefault("resultado", "approved");

        Map<String, String> vuelta = new LinkedHashMap<>();
        String destino;
        if (resultado.equals("volver")) {
            destino = (String) urls.get("failure");
            for (String p : List.of("collection_id", "collection_status", "payment_id", "status")) {
                vuelta.put(p, "null");
            }
            vuelta.put("external_reference", (String) pref.get("external_reference"));
        } else {
            Map<String, Object> pago = crearPago(pref, resultado);
            destino = (String) urls.get(resultado.equals("approved") ? "success" : "failure");
            String pid = pago.get("id").toString();
            vuelta.put("collection_id", pid);
            vuelta.put("collection_status", resultado);
            vuelta.put("payment_id", pid);
            vuelta.put("status", resultado);
            vuelta.put("external_reference", (String) pref.get("external_reference"));
            vuelta.put("payment_type", "credit_card");
        }
        vuelta.put("merchant_order_id", "null");
        vuelta.put("preference_id", (String) pref.get("id"));
        vuelta.put("site_id", "MLA");

        StringBuilder url = new StringBuilder(destino).append(destino.contains("?") ? '&' : '?');
        vuelta.forEach((k, v) -> url.append(k).append('=').append(URLEncoder.encode(v, StandardCharsets.UTF_8)).append('&'));
        url.setLength(url.length() - 1);
        ex.getResponseHeaders().add("Location", url.toString());
        ex.sendResponseHeaders(302, -1);
    }

    // ---------- helpers ----------

    private Map<String, Object> crearPago(Map<String, Object> pref, String estado) {
        Map<String, Object> pago = new LinkedHashMap<>();
        long id = idPago.incrementAndGet();
        pago.put("id", id);
        pago.put("status", estado);
        pago.put("status_detail", estado.equals("approved") ? "accredited" : "cc_rejected_other_reason");
        pago.put("external_reference", pref.get("external_reference"));
        pago.put("transaction_amount", total(pref));
        pago.put("transaction_amount_refunded", BigDecimal.ZERO);
        pago.put("currency_id", "ARS");
        pago.put("payment_type_id", "credit_card");
        pago.put("date_created", OffsetDateTime.now().toString());
        pagos.put(Long.toString(id), pago);
        return pago;
    }

    @SuppressWarnings("unchecked")
    private static BigDecimal total(Map<String, Object> pref) {
        BigDecimal total = BigDecimal.ZERO;
        for (Object o : (List<Object>) pref.get("items")) {
            Map<String, Object> item = (Map<String, Object>) o;
            total = total.add(new BigDecimal(item.get("unit_price").toString())
                    .multiply(new BigDecimal(item.get("quantity").toString())));
        }
        return total;
    }

    private static boolean vencida(Map<String, Object> pref) {
        Object hasta = pref.get("expiration_date_to");
        return Boolean.TRUE.equals(pref.get("expires")) && hasta != null
                && OffsetDateTime.parse(hasta.toString()).isBefore(OffsetDateTime.now());
    }

    private void modoHttp(HttpExchange ex) throws IOException {
        String valor = parametros(ex).get("valor");
        if (valor != null) {
            if (!List.of("normal", "lento", "error", "roto").contains(valor)) {
                responder(ex, 400, "text/plain", "modos: normal, lento, error, roto");
                return;
            }
            modo = valor;
            System.out.println("[MOCK MP] modo " + modo);
        }
        html(ex, 200, "<h2>Modo actual: " + modo + "</h2>"
                + "<p><a href='/__modo?valor=normal'>normal</a> &middot; <a href='/__modo?valor=lento'>lento (8 s)</a>"
                + " &middot; <a href='/__modo?valor=error'>error 500</a> &middot; <a href='/__modo?valor=roto'>respuesta rota</a></p>");
    }

    private static final String BOTON = "display:block;text-align:center;padding:12px;border-radius:6px;"
            + "text-decoration:none;font-weight:600;margin-bottom:10px";

    private static Map<String, String> parametros(HttpExchange ex) {
        Map<String, String> q = new LinkedHashMap<>();
        String crudo = ex.getRequestURI().getRawQuery();
        if (crudo != null) {
            for (String par : crudo.split("&")) {
                int i = par.indexOf('=');
                if (i > 0) {
                    q.put(URLDecoder.decode(par.substring(0, i), StandardCharsets.UTF_8),
                          URLDecoder.decode(par.substring(i + 1), StandardCharsets.UTF_8));
                }
            }
        }
        return q;
    }

    private static String cuerpo(HttpExchange ex) throws IOException {
        return new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
    }

    private static void json(HttpExchange ex, int status, Object cuerpo) throws IOException {
        responder(ex, status, "application/json", Json.escribir(cuerpo));
    }

    private static void html(HttpExchange ex, int status, String contenido) throws IOException {
        responder(ex, status, "text/html; charset=utf-8",
                "<!doctype html><meta charset='utf-8'><meta name='viewport' content='width=device-width'>"
                + "<body style='font-family:sans-serif;max-width:420px;margin:40px auto;padding:0 16px'>"
                + contenido + "</body>");
    }

    private static void responder(HttpExchange ex, int status, String tipo, String texto) throws IOException {
        byte[] bytes = texto.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", tipo);
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static String esc(Object texto) {
        return String.valueOf(texto).replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static void dormir(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // JSON minimo para no depender de ninguna libreria
    static final class Json {

        private final String s;
        private int i;

        private Json(String s) { this.s = s; }

        static Object leer(String texto) {
            Json p = new Json(texto);
            Object v = p.valor();
            p.espacios();
            if (p.i != p.s.length()) {
                throw new IllegalArgumentException("JSON invalido");
            }
            return v;
        }

        private Object valor() {
            espacios();
            if (i >= s.length()) throw new IllegalArgumentException("JSON incompleto");
            char c = s.charAt(i);
            if (c == '{') return objeto();
            if (c == '[') return lista();
            if (c == '"') return texto();
            if (s.startsWith("true", i)) { i += 4; return Boolean.TRUE; }
            if (s.startsWith("false", i)) { i += 5; return Boolean.FALSE; }
            if (s.startsWith("null", i)) { i += 4; return null; }
            int inicio = i;
            while (i < s.length() && "+-0123456789.eE".indexOf(s.charAt(i)) >= 0) i++;
            if (inicio == i) throw new IllegalArgumentException("JSON invalido en " + i);
            return new BigDecimal(s.substring(inicio, i));
        }

        private Map<String, Object> objeto() {
            Map<String, Object> m = new LinkedHashMap<>();
            i++;
            espacios();
            if (s.charAt(i) == '}') { i++; return m; }
            while (true) {
                espacios();
                String k = texto();
                espacios();
                esperar(':');
                m.put(k, valor());
                espacios();
                if (s.charAt(i) == ',') { i++; continue; }
                esperar('}');
                return m;
            }
        }

        private List<Object> lista() {
            List<Object> l = new ArrayList<>();
            i++;
            espacios();
            if (s.charAt(i) == ']') { i++; return l; }
            while (true) {
                l.add(valor());
                espacios();
                if (s.charAt(i) == ',') { i++; continue; }
                esperar(']');
                return l;
            }
        }

        private String texto() {
            esperar('"');
            StringBuilder b = new StringBuilder();
            while (s.charAt(i) != '"') {
                char c = s.charAt(i++);
                if (c == '\\') {
                    char e = s.charAt(i++);
                    switch (e) {
                        case 'n' -> b.append('\n');
                        case 't' -> b.append('\t');
                        case 'r' -> b.append('\r');
                        case 'b' -> b.append('\b');
                        case 'f' -> b.append('\f');
                        case 'u' -> { b.append((char) Integer.parseInt(s.substring(i, i + 4), 16)); i += 4; }
                        default -> b.append(e);
                    }
                } else {
                    b.append(c);
                }
            }
            i++;
            return b.toString();
        }

        private void espacios() {
            while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++;
        }

        private void esperar(char c) {
            if (i >= s.length() || s.charAt(i) != c) throw new IllegalArgumentException("se esperaba " + c);
            i++;
        }

        @SuppressWarnings("unchecked")
        static String escribir(Object v) {
            if (v == null) return "null";
            if (v instanceof String t) {
                StringBuilder b = new StringBuilder("\"");
                for (char c : t.toCharArray()) {
                    switch (c) {
                        case '"' -> b.append("\\\"");
                        case '\\' -> b.append("\\\\");
                        case '\n' -> b.append("\\n");
                        default -> { if (c < 0x20) b.append(String.format("\\u%04x", (int) c)); else b.append(c); }
                    }
                }
                return b.append('"').toString();
            }
            if (v instanceof BigDecimal d) return d.toPlainString();
            if (v instanceof Number || v instanceof Boolean) return v.toString();
            if (v instanceof Map<?, ?> m) {
                StringBuilder b = new StringBuilder("{");
                for (Map.Entry<?, ?> e : m.entrySet()) {
                    if (b.length() > 1) b.append(',');
                    b.append(escribir(e.getKey().toString())).append(':').append(escribir(e.getValue()));
                }
                return b.append('}').toString();
            }
            if (v instanceof List<?> l) {
                StringBuilder b = new StringBuilder("[");
                for (Object o : l) {
                    if (b.length() > 1) b.append(',');
                    b.append(escribir(o));
                }
                return b.append(']').toString();
            }
            return escribir(v.toString());
        }
    }
}
