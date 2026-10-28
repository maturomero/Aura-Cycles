package ar.edu.uade.repuestos.integracion;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StringReader;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/**
 * Proveedor mayorista simulado (SOAP 1.1). Solo usa el JDK.
 * En Eclipse: Run As > Java Application. WSDL en http://localhost:8090/proveedor?wsdl
 * Modos para la demo: http://localhost:8090/__modo?valor=normal | lento | error | roto | xxe
 */
public class MockProveedorSoap {

    static final String NS = "http://proveedor.mayorista.ar/ws";
    static final String NS_SOAP = "http://schemas.xmlsoap.org/soap/envelope/";

    private final HttpServer servidor;
    private final String wsdl;
    private final Schema esquema;
    private volatile String modo = "normal";
    private final AtomicInteger rma = new AtomicInteger(1);
    private final AtomicInteger pedidos = new AtomicInteger();

    public static void main(String[] args) throws Exception {
        MockProveedorSoap mock = iniciar(args.length > 0 ? Integer.parseInt(args[0]) : 8090);
        System.out.println("Proveedor mayorista simulado en http://localhost:" + mock.puerto() + "/proveedor?wsdl");
        System.out.println("Modos: http://localhost:" + mock.puerto() + "/__modo");
    }

    public static MockProveedorSoap iniciar(int puerto) throws Exception {
        return new MockProveedorSoap(puerto);
    }

    private MockProveedorSoap(int puerto) throws Exception {
        wsdl = leerWsdl();
        esquema = esquemaDe(wsdl);
        servidor = HttpServer.create(new InetSocketAddress(puerto), 0);
        servidor.setExecutor(Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "mock-proveedor");
            t.setDaemon(true);
            return t;
        }));
        servidor.createContext("/", this::atender);
        servidor.start();
    }

    public int puerto() { return servidor.getAddress().getPort(); }

    public String url() { return "http://localhost:" + puerto() + "/proveedor"; }

    public void modo(String modo) { this.modo = modo; }

    public int pedidos() { return pedidos.get(); }

    public void detener() { servidor.stop(0); }

    // ---------- HTTP ----------

    private void atender(HttpExchange ex) throws IOException {
        try {
            String ruta = ex.getRequestURI().getPath();
            String query = ex.getRequestURI().getRawQuery();
            if (ruta.equals("/__modo")) {
                cambiarModo(ex, query);
            } else if (ruta.equals("/proveedor") && "GET".equals(ex.getRequestMethod()) && "wsdl".equalsIgnoreCase(query)) {
                String host = ex.getRequestHeaders().getFirst("Host");
                responder(ex, 200, "text/xml; charset=utf-8",
                        wsdl.replace("http://localhost:8090/proveedor", "http://" + host + "/proveedor"));
            } else if (ruta.equals("/proveedor") && "POST".equals(ex.getRequestMethod())) {
                pedidos.incrementAndGet();
                soap(ex, new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            } else {
                responder(ex, 404, "text/plain", "no existe");
            }
        } finally {
            ex.close();
        }
    }

    private void soap(HttpExchange ex, String pedido) throws IOException {
        switch (modo) {
            case "lento" -> dormir(8_000);
            case "error" -> {
                // Un Fault tecnico como el de un sistema legado: con detalles internos que NO tienen que llegar al cliente
                responder(ex, 500, "text/xml; charset=utf-8", fault("soap:Server",
                        "java.sql.SQLException: ORA-12541 TNS:no listener at legacy.GarantiasDAO.buscar(GarantiasDAO.java:88)", ""));
                return;
            }
            case "roto" -> {
                responder(ex, 200, "text/html", "<html><body>502 Bad Gateway</body></html>");
                return;
            }
            case "xxe" -> {
                responder(ex, 200, "text/xml; charset=utf-8", "<?xml version=\"1.0\"?>"
                        + "<!DOCTYPE e [<!ENTITY xxe SYSTEM \"file:///etc/passwd\">]>"
                        + "<soap:Envelope xmlns:soap=\"" + NS_SOAP + "\"><soap:Body>"
                        + "<tns:validarGarantiaResponse xmlns:tns=\"" + NS + "\"><tns:rma>&xxe;</tns:rma>"
                        + "</tns:validarGarantiaResponse></soap:Body></soap:Envelope>");
                return;
            }
            default -> { }
        }

        Element operacion;
        try {
            Document d = leer(pedido);
            Element body = hijo(d.getDocumentElement(), NS_SOAP, "Body");
            operacion = body == null ? null : primerElemento(body);
            if (operacion == null) {
                throw new IllegalArgumentException("sin operacion");
            }
            esquema.newValidator().validate(new DOMSource(operacion));
        } catch (Exception e) {
            responder(ex, 500, "text/xml; charset=utf-8", fault("soap:Client", "Pedido invalido: " + e.getMessage(), ""));
            return;
        }

        switch (operacion.getLocalName()) {
            case "validarGarantia" -> validarGarantia(ex, operacion);
            case "consultarStock" -> responder(ex, 200, "text/xml; charset=utf-8", sobre(
                    "<tns:consultarStockResponse xmlns:tns=\"" + NS + "\"><tns:disponible>25</tns:disponible></tns:consultarStockResponse>"));
            default -> responder(ex, 500, "text/xml; charset=utf-8", fault("soap:Client", "Operacion desconocida", ""));
        }
    }

    // Reglas del proveedor: pastillas y camaras son de desgaste; la garantia del fabricante dura un año
    private void validarGarantia(HttpExchange ex, Element op) throws IOException {
        String codigo = texto(op, "codigoProducto");
        LocalDate fecha = LocalDate.parse(texto(op, "fechaCompra"));
        String rechazo = null;
        if (codigo.startsWith("PAS-") || codigo.startsWith("CAM-")) {
            rechazo = "CONSUMIBLE";
        } else if (fecha.isBefore(LocalDate.now().minusYears(1))) {
            rechazo = "FUERA_DE_PLAZO";
        }
        if (rechazo != null) {
            responder(ex, 500, "text/xml; charset=utf-8", fault("soap:Client", "Garantia no valida",
                    "<tns:garantiaNoValida xmlns:tns=\"" + NS + "\"><tns:codigo>" + rechazo + "</tns:codigo>"
                    + "<tns:mensaje>Producto " + codigo + " sin garantia (" + rechazo + ")</tns:mensaje></tns:garantiaNoValida>"));
            return;
        }
        String numero = String.format("RMA-%d-%05d", LocalDate.now().getYear(), rma.incrementAndGet());
        responder(ex, 200, "text/xml; charset=utf-8", sobre(
                "<tns:validarGarantiaResponse xmlns:tns=\"" + NS + "\"><tns:rma>" + numero + "</tns:rma></tns:validarGarantiaResponse>"));
    }

    private void cambiarModo(HttpExchange ex, String query) throws IOException {
        if (query != null && query.startsWith("valor=")) {
            String valor = query.substring(6);
            if (!List.of("normal", "lento", "error", "roto", "xxe").contains(valor)) {
                responder(ex, 400, "text/plain", "modos: normal, lento, error, roto, xxe");
                return;
            }
            modo = valor;
            System.out.println("[MOCK PROVEEDOR] modo " + modo);
        }
        responder(ex, 200, "text/html; charset=utf-8", "<!doctype html><body style='font-family:sans-serif;margin:40px'>"
                + "<h2>Proveedor simulado - modo: " + modo + "</h2><p>"
                + "<a href='/__modo?valor=normal'>normal</a> &middot; <a href='/__modo?valor=lento'>lento (8 s)</a> &middot; "
                + "<a href='/__modo?valor=error'>Fault tecnico</a> &middot; <a href='/__modo?valor=roto'>respuesta rota</a> &middot; "
                + "<a href='/__modo?valor=xxe'>XXE</a></p></body>");
    }

    // ---------- XML ----------

    static String sobre(String cuerpo) {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?><soap:Envelope xmlns:soap=\"" + NS_SOAP + "\"><soap:Body>"
                + cuerpo + "</soap:Body></soap:Envelope>";
    }

    static String fault(String codigo, String texto, String detalle) {
        return sobre("<soap:Fault><faultcode>" + codigo + "</faultcode><faultstring>" + texto.replace("<", "&lt;")
                + "</faultstring>" + (detalle.isEmpty() ? "" : "<detail>" + detalle + "</detail>") + "</soap:Fault>");
    }

    static Document leer(String xml) throws Exception {
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        f.setNamespaceAware(true);
        f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        var b = f.newDocumentBuilder();
        b.setErrorHandler(new org.xml.sax.helpers.DefaultHandler());
        return b.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
    }

    // El xs:schema que esta adentro del WSDL: con esto se valida el contrato
    static Schema esquemaDe(String wsdl) throws Exception {
        Document d = leer(wsdl);
        Node schema = d.getElementsByTagNameNS(XMLConstants.W3C_XML_SCHEMA_NS_URI, "schema").item(0);
        SchemaFactory sf = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
        sf.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        sf.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        return sf.newSchema(new DOMSource(schema, "wsdl"));
    }

    static String leerWsdl() throws IOException {
        try (InputStream in = MockProveedorSoap.class.getResourceAsStream("/wsdl/ProveedorMayorista.wsdl")) {
            if (in != null) {
                return new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
        }
        String ruta = System.getenv().getOrDefault("MOCK_WSDL", "src/main/resources/wsdl/ProveedorMayorista.wsdl");
        return Files.readString(Path.of(ruta));
    }

    private static Element hijo(Element padre, String ns, String nombre) {
        for (Node n = padre.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n instanceof Element e && ns.equals(e.getNamespaceURI()) && nombre.equals(e.getLocalName())) {
                return e;
            }
        }
        return null;
    }

    private static Element primerElemento(Element padre) {
        for (Node n = padre.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n instanceof Element e) {
                return e;
            }
        }
        return null;
    }

    private static String texto(Element op, String nombre) {
        return op.getElementsByTagNameNS(NS, nombre).item(0).getTextContent().trim();
    }

    private static void responder(HttpExchange ex, int status, String tipo, String texto) throws IOException {
        byte[] b = texto.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", tipo);
        ex.sendResponseHeaders(status, b.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(b);
        }
    }

    private static void dormir(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // Para el test de contrato: valida un XML suelto contra el esquema del WSDL
    static void validar(String xml) throws Exception {
        esquemaDe(leerWsdl()).newValidator().validate(new StreamSource(new StringReader(xml)));
    }
}
