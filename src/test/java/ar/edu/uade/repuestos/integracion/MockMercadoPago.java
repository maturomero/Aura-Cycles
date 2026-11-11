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
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.Duration;
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
            pagina(ex, 404, "No encontramos ese link de pago",
                    "<div class='aviso'><h1>No encontramos ese link de pago</h1><p>Volv&eacute; a la tienda y entr&aacute; desde Mis compras.</p></div>");
            return;
        }
        if (vencida(pref)) {
            pagina(ex, 200, "El link de pago venci&oacute;",
                    "<div class='aviso'><h1>Este link de pago venci&oacute;</h1><p>Volv&eacute; a la tienda y hac&eacute; la compra de nuevo.</p></div>");
            return;
        }
        String id = esc(pref.get("id"));
        String total = plata(total(pref));

        StringBuilder items = new StringBuilder();
        for (Object o : (List<Object>) pref.get("items")) {
            Map<String, Object> item = (Map<String, Object>) o;
            BigDecimal unitario = new BigDecimal(item.get("unit_price").toString());
            BigDecimal cantidad = new BigDecimal(item.get("quantity").toString());
            items.append("<li class='item'>").append(PLATO)
                    .append("<div><p class='nombre'>").append(esc(item.get("title"))).append("</p><p class='dato'>");
            if (item.get("id") != null) {
                items.append("C&oacute;digo ").append(esc(item.get("id"))).append("<br>");
            }
            items.append(esc(item.get("quantity"))).append(cantidad.compareTo(BigDecimal.ONE) == 0 ? " unidad" : " unidades")
                    .append(" a ").append(plata(unitario)).append("</p></div>")
                    .append("<p class='precio'>").append(plata(unitario.multiply(cantidad))).append("</p></li>");
        }

        // Como en Mercado Pago de prueba: paga cualquiera, y el nombre del titular decide si se aprueba
        String cuerpo = """
                <div class="columnas">
                  <form class="panel pago" action="/checkout/pagar" method="get" id="form-pago">
                    <h1>Pag&aacute; con tarjeta</h1>
                    <div class="tarjeta" aria-hidden="true">
                      <span class="chip"></span>
                      <span class="t-numero" id="v-numero">5031 7557 3453 0604</span>
                      <span class="t-pie"><span id="v-titular">APRO</span><span id="v-vence">11/30</span></span>
                    </div>
                    <input type="hidden" name="pref" value="%s">
                    <label for="numero">N&uacute;mero de tarjeta</label>
                    <input id="numero" inputmode="numeric" autocomplete="off" maxlength="19" required value="5031 7557 3453 0604">
                    <label for="titular">Nombre del titular</label>
                    <input id="titular" name="titular" autocomplete="off" maxlength="40" required value="APRO">
                    <div class="fila">
                      <div><label for="vence">Vencimiento</label>
                        <input id="vence" inputmode="numeric" autocomplete="off" maxlength="5" required value="11/30"></div>
                      <div><label for="cvv">C&oacute;digo de seguridad</label>
                        <input id="cvv" inputmode="numeric" autocomplete="off" maxlength="4" required value="123"></div>
                    </div>
                    <label for="dni">Documento del titular</label>
                    <input id="dni" inputmode="numeric" autocomplete="off" maxlength="10" required value="12345678">
                    <p class="ayuda">Tarjeta de prueba. Con el titular APRO el pago se aprueba y con OTHE se rechaza.</p>
                    <button type="submit" id="pagar">Pagar %s</button>
                    <a class="volver" href="/checkout/pagar?pref=%s&amp;resultado=volver">Volver a la tienda sin pagar</a>
                  </form>
                  <section class="panel resumen" aria-labelledby="t-compra">
                    <p class="vendedor">Le pag&aacute;s a <strong>Aura Cycles</strong></p>
                    <h2 id="t-compra">Detalle de tu compra</h2>
                    <ul class="items">%s</ul>
                    <p class="total"><span>Total</span><strong>%s</strong></p>
                    <dl class="pie"><dt>Orden</dt><dd>%s</dd>%s</dl>
                  </section>
                </div>
                """.formatted(id, total, id, items, total, esc(pref.get("external_reference")), plazo(pref)) + GUION;
        pagina(ex, 200, "Pagar " + total, cuerpo);
    }

    private static String plazo(Map<String, Object> pref) {
        Object hasta = pref.get("expiration_date_to");
        if (!Boolean.TRUE.equals(pref.get("expires")) || hasta == null) {
            return "";
        }
        long minutos = Duration.between(OffsetDateTime.now(), OffsetDateTime.parse(hasta.toString())).toMinutes();
        String texto = minutos < 1 ? "menos de un minuto" : minutos == 1 ? "1 minuto" : minutos + " minutos";
        return "<dt>Reserva</dt><dd>Ten&eacute;s " + texto + " para pagar</dd>";
    }

    @SuppressWarnings("unchecked")
    private void pagarDesdeCheckout(HttpExchange ex) throws IOException {
        Map<String, String> q = parametros(ex);
        Map<String, Object> pref = preferencias.get(q.get("pref"));
        if (pref == null || vencida(pref)) {
            pagina(ex, 400, "Link de pago inv&aacute;lido",
                    "<h1>Este link de pago no es v&aacute;lido o venci&oacute;</h1><p class='bajada'>Volv&eacute; a la tienda y hac&eacute; la compra de nuevo.</p>");
            return;
        }
        Map<String, Object> urls = (Map<String, Object>) pref.get("back_urls");
        // Sin "resultado" decide el titular de la tarjeta: OTHE se rechaza y cualquier otro se aprueba
        String resultado = q.get("resultado");
        if (resultado == null) {
            resultado = "OTHE".equalsIgnoreCase(q.getOrDefault("titular", "").trim()) ? "rejected" : "approved";
        }

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

    // $ 1.500,00
    private static String plata(BigDecimal monto) {
        DecimalFormatSymbols simbolos = new DecimalFormatSymbols();
        simbolos.setGroupingSeparator('.');
        simbolos.setDecimalSeparator(',');
        return "$&nbsp;" + new DecimalFormat("#,##0.00", simbolos).format(monto);
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
        pagina(ex, 200, "Modo del simulador", "<h1>Modo actual: " + modo + "</h1>"
                + "<p class='bajada'>Para probar qu&eacute; hace la tienda cuando Mercado Pago anda mal.</p>"
                + "<ul class='modos'><li><a href='/__modo?valor=normal'>normal</a></li>"
                + "<li><a href='/__modo?valor=lento'>lento (8 s)</a></li>"
                + "<li><a href='/__modo?valor=error'>error 500</a></li>"
                + "<li><a href='/__modo?valor=roto'>respuesta rota</a></li></ul>");
    }

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

    private static void pagina(HttpExchange ex, int status, String titulo, String cuerpo) throws IOException {
        responder(ex, status, "text/html; charset=utf-8",
                "<!doctype html><html lang='es'><head><meta charset='utf-8'>"
                + "<meta name='viewport' content='width=device-width, initial-scale=1'>"
                + "<title>" + titulo + " | Pago seguro</title>" + ESTILO + "</head><body>"
                + "<header class='barra'><span class='seguro'>" + CANDADO + "Pago seguro</span>"
                + "<span class='ambiente'>Ambiente de prueba</span></header>"
                + "<main>" + cuerpo + "</main></body></html>");
    }

    private static final String CANDADO = "<svg viewBox='0 0 24 24' width='18' height='18' fill='none' stroke='currentColor'"
            + " stroke-width='2' stroke-linecap='round' stroke-linejoin='round' aria-hidden='true'>"
            + "<rect x='5' y='11' width='14' height='9' rx='2'/><path d='M8 11V8a4 4 0 0 1 8 0v3'/></svg>";

    // El plato de la marca, para la miniatura del producto
    private static final String PLATO = "<svg class='mini' viewBox='0 0 48 48' fill='none' stroke='currentColor' stroke-width='2.5'"
            + " stroke-linecap='round' aria-hidden='true'><circle cx='24' cy='24' r='15'/><circle cx='24' cy='24' r='4'/>"
            + "<path d='M24 5v4M24 39v4M5 24h4M39 24h4M10.6 10.6l2.8 2.8M34.6 34.6l2.8 2.8M10.6 37.4l2.8-2.8M34.6 13.4l2.8-2.8'/></svg>";

    // La tarjeta dibujada copia lo que se escribe, y el boton avisa que se esta procesando
    private static final String GUION = """
            <script>
            for (const [campo, vista] of [['numero', 'v-numero'], ['titular', 'v-titular'], ['vence', 'v-vence']]) {
              document.getElementById(campo).addEventListener('input', e => {
                document.getElementById(vista).textContent = e.target.value.toUpperCase() || '\\u00a0';
              });
            }
            document.getElementById('form-pago').addEventListener('submit', () => {
              document.getElementById('pagar').textContent = 'Procesando el pago...';
            });
            </script>
            """;

    // Colores propios a proposito: no es la tienda ni es Mercado Pago
    private static final String ESTILO = """
            <style>
            :root { --fondo:#EEF1F4; --panel:#FFFFFF; --tinta:#17212B; --suave:#55636F; --linea:#D6DCE2; --campo:#FFFFFF;
                    --barra:#17212B; --barra-tinta:#FFFFFF; --ambiente:#FFE9A8; --ambiente-tinta:#3D2E00;
                    --pagar:#14613F; --pagar-sobre:#0E4A30; --pagar-tinta:#FFFFFF; --foco:#1F6FEB;
                    --tarjeta-a:#1B2A38; --tarjeta-b:#35526B; --mini:#E7EDF2; }
            @media (prefers-color-scheme: dark) {
              :root { --fondo:#12161A; --panel:#1B2127; --tinta:#E6EAEE; --suave:#A3AEB9; --linea:#333C45; --campo:#12161A;
                      --barra:#0B0E11; --pagar:#3DB37E; --pagar-sobre:#55C592; --pagar-tinta:#06140D; --foco:#6CA8FF;
                      --tarjeta-a:#0F1A24; --tarjeta-b:#2A4459; --mini:#252D35; }
            }
            * { box-sizing:border-box; }
            body { margin:0; background:var(--fondo); color:var(--tinta);
                   font:16px/1.5 system-ui, -apple-system, "Segoe UI", Roboto, sans-serif; }
            .barra { display:flex; justify-content:space-between; align-items:center; gap:12px; padding:12px 20px;
                     background:var(--barra); color:var(--barra-tinta); }
            .seguro { display:inline-flex; align-items:center; gap:8px; font-weight:600; }
            .ambiente { padding:2px 10px; border-radius:999px; font-size:13px; font-weight:600;
                        background:var(--ambiente); color:var(--ambiente-tinta); }
            main { max-width:960px; margin:0 auto; padding:32px 16px 56px; }
            .columnas { display:grid; gap:20px; grid-template-columns:1fr; align-items:start; }
            .resumen { order:-1; }
            @media (min-width:820px) { .columnas { grid-template-columns:1.15fr 1fr; } .resumen { order:0; } }
            .panel { background:var(--panel); border:1px solid var(--linea); border-radius:12px; padding:24px; }
            h1 { margin:0 0 18px; font-size:24px; line-height:1.2; }
            h2 { margin:0 0 14px; font-size:15px; font-weight:600; color:var(--suave); }
            .aviso { max-width:60ch; }
            .aviso p, .bajada { color:var(--suave); }

            .tarjeta { display:flex; flex-direction:column; justify-content:space-between; width:100%; max-width:340px;
                       aspect-ratio:1.586; margin:0 0 22px; padding:20px; border-radius:14px; color:#FFFFFF;
                       background:linear-gradient(135deg, var(--tarjeta-a), var(--tarjeta-b)); }
            .chip { width:42px; height:30px; border-radius:6px; background:linear-gradient(135deg, #E9D08A, #B8964A); }
            .t-numero { font-size:21px; letter-spacing:.06em; font-variant-numeric:tabular-nums; }
            .t-pie { display:flex; justify-content:space-between; gap:12px; font-size:14px; letter-spacing:.04em; }
            #v-titular { overflow:hidden; text-overflow:ellipsis; white-space:nowrap; }

            label { display:block; margin:14px 0 6px; font-size:14px; font-weight:600; }
            input:not([type=hidden]) { width:100%; padding:11px 12px; border:1px solid var(--linea); border-radius:8px;
                    background:var(--campo); color:var(--tinta); font:inherit; font-variant-numeric:tabular-nums; }
            .fila { display:grid; grid-template-columns:1fr 1fr; gap:14px; }
            .ayuda { margin:14px 0 0; font-size:14px; color:var(--suave); }
            button { width:100%; margin-top:18px; padding:15px; border:0; border-radius:8px; cursor:pointer;
                     background:var(--pagar); color:var(--pagar-tinta); font:inherit; font-size:17px; font-weight:600;
                     font-variant-numeric:tabular-nums; }
            button:hover { background:var(--pagar-sobre); }
            .volver { display:block; margin-top:14px; text-align:center; color:var(--suave); }
            a { color:inherit; }
            :focus-visible { outline:3px solid var(--foco); outline-offset:2px; }

            .vendedor { margin:0 0 18px; padding-bottom:16px; border-bottom:1px solid var(--linea); color:var(--suave); }
            .vendedor strong { color:var(--tinta); }
            .items { list-style:none; margin:0; padding:0; }
            .item { display:grid; grid-template-columns:56px 1fr auto; gap:14px; align-items:start; }
            .item + .item { margin-top:16px; padding-top:16px; border-top:1px solid var(--linea); }
            .mini { width:56px; height:56px; padding:8px; border-radius:10px; background:var(--mini); color:var(--suave); }
            .nombre { margin:0; font-weight:600; line-height:1.3; }
            .dato { margin:4px 0 0; font-size:14px; color:var(--suave); font-variant-numeric:tabular-nums; }
            .precio { margin:0; font-weight:600; white-space:nowrap; font-variant-numeric:tabular-nums; }
            .total { display:flex; justify-content:space-between; align-items:baseline; margin:20px 0 0;
                     padding-top:16px; border-top:1px solid var(--linea); }
            .total strong { font-size:28px; line-height:1.1; font-variant-numeric:tabular-nums; }
            .pie { display:grid; grid-template-columns:auto 1fr; gap:4px 14px; margin:18px 0 0; font-size:14px; color:var(--suave); }
            .pie dd { margin:0; color:var(--tinta); overflow-wrap:anywhere; }
            .modos { padding-left:20px; line-height:2; }
            </style>
            """;

    private static void responder(HttpExchange ex, int status, String tipo, String texto) throws IOException {
        byte[] bytes = texto.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", tipo);
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static String esc(Object texto) {
        return String.valueOf(texto).replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
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
