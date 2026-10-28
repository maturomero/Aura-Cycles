package ar.edu.uade.repuestos.integracion;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.xml.sax.SAXException;

// Lo que mandamos y lo que esperamos recibir, contra el esquema publicado en el WSDL del proveedor
class ContratoWsdlTest {

    private static final String NS = "xmlns:tns=\"http://proveedor.mayorista.ar/ws\"";

    @Test
    void elPedidoQueArmamosCumpleElWsdl() {
        String cuerpo = ClienteProveedor.cuerpoValidarGarantia("CUB-29-210", LocalDate.of(2026, 10, 24), 2,
                "DEFECTUOSO", "ORD-000009");
        assertDoesNotThrow(() -> MockProveedorSoap.validar(cuerpo));
    }

    @Test
    void laRespuestaConRmaCumpleElWsdl() {
        assertDoesNotThrow(() -> MockProveedorSoap.validar(
                "<tns:validarGarantiaResponse " + NS + "><tns:rma>RMA-2026-00042</tns:rma></tns:validarGarantiaResponse>"));
    }

    @Test
    void elDetalleDelFaultCumpleElWsdl() {
        assertDoesNotThrow(() -> MockProveedorSoap.validar("<tns:garantiaNoValida " + NS
                + "><tns:codigo>CONSUMIBLE</tns:codigo><tns:mensaje>sin garantia</tns:mensaje></tns:garantiaNoValida>"));
    }

    // Prueba que la validacion es real: una cantidad 0 o un codigo de rechazo inventado no pasan
    @Test
    void unPedidoFueraDeContratoNoPasa() {
        String cantidadCero = ClienteProveedor.cuerpoValidarGarantia("CUB-29-210", LocalDate.now(), 0, "X", "ORD-1");
        assertThrows(SAXException.class, () -> MockProveedorSoap.validar(cantidadCero));
        assertThrows(SAXException.class, () -> MockProveedorSoap.validar("<tns:garantiaNoValida " + NS
                + "><tns:codigo>OTRO</tns:codigo><tns:mensaje>x</tns:mensaje></tns:garantiaNoValida>"));
    }
}
