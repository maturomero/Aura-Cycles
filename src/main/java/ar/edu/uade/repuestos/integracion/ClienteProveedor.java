package ar.edu.uade.repuestos.integracion;

import jakarta.enterprise.context.Dependent;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.time.LocalDate;
import java.util.logging.Logger;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * CAPA DE INTEGRACION. Cliente SOAP 1.1 del proveedor mayorista (wsdl/ProveedorMayorista.wsdl).
 * Solo usamos validarGarantia: por eso un cliente chico y no todo lo que generaria wsimport.
 */
@Dependent
public class ClienteProveedor {

    private static final Logger LOG = Logger.getLogger(ClienteProveedor.class.getName());
    static final String NS = "http://proveedor.mayorista.ar/ws";
    static final String NS_SOAP = "http://schemas.xmlsoap.org/soap/envelope/";
    public static final Duration TIMEOUT = Duration.ofSeconds(5);

    private final String url;
    private final Duration timeout;
    private final HttpClient http;

    public ClienteProveedor() {
        this(urlDelEntorno(), TIMEOUT);
    }

    public ClienteProveedor(String url, Duration timeout) {
        this.url = url;
        this.timeout = timeout;
        this.http = HttpClient.newBuilder().connectTimeout(timeout).build();
    }

    // Devuelve el RMA. Fault de negocio: GarantiaNoValidaException. Cualquier otra falla: ProveedorNoDisponibleException
    public String validarGarantia(String codigoProducto, LocalDate fechaCompra, int cantidad,
                                  String motivo, String referencia) {
        Document respuesta = enviar("validarGarantia",
                cuerpoValidarGarantia(codigoProducto, fechaCompra, cantidad, motivo, referencia));

        Element fault = primero(respuesta, NS_SOAP, "Fault");
        if (fault != null) {
            Element detalle = primero(respuesta, NS, "garantiaNoValida");
            if (detalle != null) {
                String codigo = texto(detalle, "codigo");
                LOG.info(() -> "[SOAP] validarGarantia " + referencia + ": garantia no valida (" + codigo + ")");
                throw new GarantiaNoValidaException(codigo);
            }
            LOG.warning(() -> "[SOAP] validarGarantia " + referencia + ": Fault tecnico del proveedor");
            throw new ProveedorNoDisponibleException("El proveedor devolvio un error tecnico");
        }

        Element ok = primero(respuesta, NS, "validarGarantiaResponse");
        String rma = ok == null ? null : texto(ok, "rma");
        if (rma == null || !rma.matches("RMA-\\d{4}-\\d{4,8}")) {
            throw new ProveedorNoDisponibleException("El proveedor devolvio una respuesta sin RMA valido");
        }
        return rma;
    }

    // Separado para el test de contrato contra el WSDL
    static String cuerpoValidarGarantia(String codigoProducto, LocalDate fechaCompra, int cantidad,
                                        String motivo, String referencia) {
        return "<tns:validarGarantia xmlns:tns=\"" + NS + "\">"
                + "<tns:codigoProducto>" + XmlSeguro.escapar(codigoProducto) + "</tns:codigoProducto>"
                + "<tns:fechaCompra>" + fechaCompra + "</tns:fechaCompra>"
                + "<tns:cantidad>" + cantidad + "</tns:cantidad>"
                + "<tns:motivo>" + XmlSeguro.escapar(motivo) + "</tns:motivo>"
                + "<tns:referencia>" + XmlSeguro.escapar(referencia) + "</tns:referencia>"
                + "</tns:validarGarantia>";
    }

    static String sobre(String cuerpo) {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<soap:Envelope xmlns:soap=\"" + NS_SOAP + "\"><soap:Body>" + cuerpo + "</soap:Body></soap:Envelope>";
    }

    private Document enviar(String operacion, String cuerpo) {
        HttpRequest pedido = HttpRequest.newBuilder(URI.create(url))
                .timeout(timeout)
                .header("Content-Type", "text/xml; charset=utf-8")
                .header("SOAPAction", "\"" + operacion + "\"")
                .POST(HttpRequest.BodyPublishers.ofString(sobre(cuerpo)))
                .build();
        HttpResponse<String> r;
        try {
            r = http.send(pedido, HttpResponse.BodyHandlers.ofString());
        } catch (HttpTimeoutException e) {
            LOG.warning(() -> "[SOAP] " + operacion + ": sin respuesta en " + timeout.toSeconds() + " s");
            throw new ProveedorNoDisponibleException("El proveedor no respondio a tiempo", e);
        } catch (IOException e) {
            LOG.warning(() -> "[SOAP] " + operacion + ": no se pudo conectar (" + e.getClass().getSimpleName() + ")");
            throw new ProveedorNoDisponibleException("No se pudo conectar con el proveedor", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ProveedorNoDisponibleException("Se interrumpio la llamada al proveedor", e);
        }
        // SOAP 1.1 manda los Fault con HTTP 500: el cuerpo se lee igual
        if (r.statusCode() != 200 && r.statusCode() != 500) {
            LOG.warning(() -> "[SOAP] " + operacion + ": HTTP " + r.statusCode());
            throw new ProveedorNoDisponibleException("El proveedor respondio HTTP " + r.statusCode());
        }
        try {
            return XmlSeguro.leer(r.body());
        } catch (Exception e) {
            LOG.warning(() -> "[SOAP] " + operacion + ": respuesta rechazada (" + e.getClass().getSimpleName() + ")");
            throw new ProveedorNoDisponibleException("El proveedor devolvio una respuesta invalida", e);
        }
    }

    private static Element primero(Document d, String ns, String nombre) {
        NodeList l = d.getElementsByTagNameNS(ns, nombre);
        return l.getLength() == 0 ? null : (Element) l.item(0);
    }

    private static String texto(Element padre, String hijo) {
        NodeList l = padre.getElementsByTagNameNS(NS, hijo);
        return l.getLength() == 0 ? null : l.item(0).getTextContent().trim();
    }

    // Sin configurar, apunta al proveedor simulado (MockProveedorSoap en src/test)
    private static String urlDelEntorno() {
        String v = System.getProperty("PROVEEDOR_URL");
        if (v == null || v.isBlank()) {
            v = System.getenv("PROVEEDOR_URL");
        }
        return v == null || v.isBlank() ? "http://localhost:8090/proveedor" : v.trim();
    }
}
