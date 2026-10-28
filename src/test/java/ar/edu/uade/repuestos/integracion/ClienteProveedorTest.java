package ar.edu.uade.repuestos.integracion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.LocalDate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

// Contra el proveedor simulado, que ademas valida cada pedido contra el WSDL
class ClienteProveedorTest {

    private MockProveedorSoap mock;
    private ClienteProveedor cliente;

    @BeforeEach
    void levantar() throws Exception {
        mock = MockProveedorSoap.iniciar(0);
        cliente = new ClienteProveedor(mock.url(), Duration.ofSeconds(1));
    }

    @AfterEach
    void bajar() {
        mock.detener();
    }

    @Test
    void garantiaValidaDevuelveElRma() {
        String rma = validar("CUB-29-210", LocalDate.now().minusDays(4));
        assertTrue(rma.matches("RMA-\\d{4}-\\d{5}"), rma);
    }

    @Test
    void unConsumibleEsFaultDeNegocioConMensajeNuestro() {
        var e = assertThrows(GarantiaNoValidaException.class, () -> validar("PAS-DISC-01", LocalDate.now()));
        assertEquals("CONSUMIBLE", e.getCodigo());
        assertEquals("Es un repuesto de desgaste: el fabricante no da garantía.", e.getMessage());
    }

    @Test
    void fueraDelAnioDeGarantia() {
        var e = assertThrows(GarantiaNoValidaException.class,
                () -> validar("CUB-29-210", LocalDate.now().minusYears(2)));
        assertEquals("FUERA_DE_PLAZO", e.getCodigo());
    }

    // El Fault tecnico trae un stack de Oracle: no puede llegar a ningun mensaje nuestro
    @Test
    void unFaultTecnicoNoSeFiltraAlCliente() {
        mock.modo("error");
        var e = assertThrows(ProveedorNoDisponibleException.class, () -> validar("CUB-29-210", LocalDate.now()));
        assertFalse(e.getMessage().contains("ORA-"));
        assertFalse(e.getMessage().contains("SQLException"));
    }

    @Test
    void siElProveedorNoContestaCortaALos5Segundos() {
        ClienteProveedor real = new ClienteProveedor(mock.url(), ClienteProveedor.TIMEOUT);
        mock.modo("lento");
        long inicio = System.nanoTime();
        assertThrows(ProveedorNoDisponibleException.class, () -> real.validarGarantia(
                "CUB-29-210", LocalDate.now(), 1, "DEFECTUOSO", "ORD-000001"));
        Duration tardo = Duration.ofNanos(System.nanoTime() - inicio);
        assertTrue(tardo.compareTo(Duration.ofMillis(4500)) >= 0, "corto antes: " + tardo);
        assertTrue(tardo.compareTo(Duration.ofSeconds(7)) < 0, "no respeto el timeout: " + tardo);
    }

    @Test
    void unaRespuestaQueNoEsXmlSeTomaComoCaida() {
        mock.modo("roto");
        assertThrows(ProveedorNoDisponibleException.class, () -> validar("CUB-29-210", LocalDate.now()));
    }

    // La respuesta trae una entidad externa a /etc/passwd: el parser la rechaza antes de leerla
    @Test
    void unXxeEnLaRespuestaSeRechaza() {
        mock.modo("xxe");
        var e = assertThrows(ProveedorNoDisponibleException.class, () -> validar("CUB-29-210", LocalDate.now()));
        assertFalse(e.getMessage().contains("root:"));
        assertTrue(e.getMessage().contains("respuesta invalida"));
    }

    @Test
    void conElProveedorApagadoAvisaQueNoHayConexion() {
        mock.detener();
        assertThrows(ProveedorNoDisponibleException.class, () -> validar("CUB-29-210", LocalDate.now()));
    }

    // Si no escaparamos, el proveedor recibe XML roto y contesta Fault de pedido invalido
    @Test
    void losDatosConCaracteresDeXmlSeEscapan() {
        assertTrue(cliente.validarGarantia("CUB-<29>&\"x\"", LocalDate.now(), 1, "NO_COMPATIBLE", "ORD-<1>")
                .startsWith("RMA-"));
        assertEquals(1, mock.pedidos());
    }

    private String validar(String codigo, LocalDate fecha) {
        return cliente.validarGarantia(codigo, fecha, 1, "DEFECTUOSO", "ORD-000001");
    }
}
