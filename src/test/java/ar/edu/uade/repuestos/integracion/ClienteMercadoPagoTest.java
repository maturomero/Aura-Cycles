package ar.edu.uade.repuestos.integracion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

// Contra el Mercado Pago simulado levantado en un puerto libre
class ClienteMercadoPagoTest {

    private static final String REFERENCIA = "3f2b8c1e-0000-4000-8000-000000000001";
    private static final BigDecimal PRECIO = new BigDecimal("46800.00");

    private MockMercadoPago mock;
    private ClienteMercadoPago cliente;

    @BeforeEach
    void levantarMock() throws Exception {
        mock = MockMercadoPago.iniciar(0);
        cliente = clienteCon("TEST-123", "http://localhost:8080/repuestos");
    }

    @AfterEach
    void bajarMock() {
        mock.detener();
    }

    @Test
    void creaLaPreferenciaConReferenciaVueltaYVencimiento() {
        PreferenciaMP p = crearPreferencia(cliente, 2);

        assertTrue(p.urlDePago().startsWith(mock.url() + "/checkout?pref="));
        Map<String, Object> enviada = mock.preferencia(p.id());
        assertEquals(REFERENCIA, enviada.get("external_reference"));
        assertEquals(Boolean.TRUE, enviada.get("binary_mode"));
        assertEquals(Boolean.TRUE, enviada.get("expires"));
        assertEquals("http://localhost:8080/repuestos/pago.xhtml?venta=7",
                ((Map<?, ?>) enviada.get("back_urls")).get("success"));
        assertNull(enviada.get("auto_return"), "con localhost MP no acepta auto_return");
    }

    @Test
    void conUrlPublicaPideVolverSolo() {
        ClienteMercadoPago publico = clienteCon("TEST-123", "http://192.168.0.15:8080/repuestos");
        PreferenciaMP p = crearPreferencia(publico, 1);
        assertEquals("approved", mock.preferencia(p.id()).get("auto_return"));
    }

    @Test
    void leeElPagoAprobadoConMontoYReferencia() {
        PreferenciaMP p = crearPreferencia(cliente, 2);
        String pagoId = mock.pagar(p.id(), "approved");

        PagoMP pago = cliente.obtenerPago(pagoId).orElseThrow();

        assertTrue(pago.aprobado());
        assertEquals(REFERENCIA, pago.referencia());
        assertEquals(0, new BigDecimal("93600.00").compareTo(pago.monto()));
        assertEquals("ARS", pago.moneda());
    }

    @Test
    void laBusquedaTraePrimeroElPagoMasNuevo() {
        PreferenciaMP p = crearPreferencia(cliente, 1);
        mock.pagar(p.id(), "rejected");
        String aprobado = mock.pagar(p.id(), "approved");

        List<PagoMP> pagos = cliente.buscarPagos(REFERENCIA);

        assertEquals(2, pagos.size());
        assertEquals(aprobado, pagos.get(0).id());
        assertTrue(cliente.buscarPagos("otra-referencia").isEmpty());
    }

    @Test
    void unPagoQueNoExisteVieneVacio() {
        assertTrue(cliente.obtenerPago("999999").isEmpty());
    }

    // El payment_id viene de la URL: si no es un numero ni siquiera se llama a MP
    @ParameterizedTest
    @ValueSource(strings = {"null", "../../users/me", "123?x=1", "", "12345678901234567890123"})
    void unIdQueNoEsNumeroNoSeConsulta(String id) {
        int antes = mock.llamadasApi();
        assertTrue(cliente.obtenerPago(id).isEmpty());
        assertEquals(antes, mock.llamadasApi());
    }

    @Test
    void sinTokenNoSeLlamaAMercadoPago() {
        ClienteMercadoPago sinToken = clienteCon(null, "http://localhost:8080/repuestos");
        var e = assertThrows(MercadoPagoNoDisponibleException.class, () -> crearPreferencia(sinToken, 1));
        assertTrue(e.getMessage().contains("MP_ACCESS_TOKEN"));
        assertEquals(0, mock.llamadasApi());
    }

    @Test
    void conTokenInvalidoAvisaCredenciales() {
        ClienteMercadoPago malo = clienteCon("cualquiera", "http://localhost:8080/repuestos");
        var e = assertThrows(MercadoPagoNoDisponibleException.class, () -> crearPreferencia(malo, 1));
        assertTrue(e.getMessage().contains("credenciales"));
    }

    @Test
    void siMercadoPagoNoContestaCortaALos5Segundos() {
        ClienteMercadoPago real = new ClienteMercadoPago(new ConfiguracionMercadoPago(
                mock.url(), "TEST-123", "http://localhost:8080/repuestos", ConfiguracionMercadoPago.TIMEOUT));
        mock.modo("lento");

        long inicio = System.nanoTime();
        assertThrows(MercadoPagoNoDisponibleException.class, () -> real.buscarPagos(REFERENCIA));
        Duration tardo = Duration.ofNanos(System.nanoTime() - inicio);

        assertTrue(tardo.compareTo(Duration.ofMillis(4500)) >= 0, "corto antes: " + tardo);
        assertTrue(tardo.compareTo(Duration.ofSeconds(7)) < 0, "no respeto el timeout: " + tardo);
    }

    @Test
    void unError500NoSeTomaComoRespuesta() {
        mock.modo("error");
        assertThrows(MercadoPagoNoDisponibleException.class, () -> cliente.obtenerPago("1000000001"));
        assertThrows(MercadoPagoNoDisponibleException.class, () -> crearPreferencia(cliente, 1));
    }

    @Test
    void unaRespuestaQueNoEsJsonNoRompeNada() {
        mock.modo("roto");
        assertThrows(MercadoPagoNoDisponibleException.class, () -> cliente.buscarPagos(REFERENCIA));
        assertThrows(MercadoPagoNoDisponibleException.class, () -> cliente.obtenerPago("1000000001"));
    }

    @Test
    void siMercadoPagoEstaApagadoAvisaQueNoHayConexion() {
        String url = mock.url();
        mock.detener();
        ClienteMercadoPago apagado = clienteConUrl(url);
        assertThrows(MercadoPagoNoDisponibleException.class, () -> apagado.buscarPagos(REFERENCIA));
    }

    @Test
    void reintentarElReembolsoConLaMismaClaveNoDevuelveDosVeces() {
        String pagoId = mock.pagar(crearPreferencia(cliente, 1).id(), "approved");

        ReembolsoMP primero = cliente.reembolsar(pagoId, new BigDecimal("42120.00"), "aura-x-DEV-000002");
        ReembolsoMP reintento = cliente.reembolsar(pagoId, new BigDecimal("42120.00"), "aura-x-DEV-000002");

        assertEquals(primero.id(), reintento.id());
        assertEquals(1, mock.reembolsosCreados());
        assertEquals(0, new BigDecimal("42120.00").compareTo(primero.monto()));
    }

    @Test
    void noSePuedeReembolsarMasDeLoPagado() {
        String pagoId = mock.pagar(crearPreferencia(cliente, 1).id(), "approved");
        assertThrows(MercadoPagoNoDisponibleException.class,
                () -> cliente.reembolsar(pagoId, new BigDecimal("50000.00"), "aura-y-DEV-000003"));
        assertFalse(mock.reembolsosCreados() > 0);
    }

    // ---------- helpers ----------

    private ClienteMercadoPago clienteCon(String token, String urlApp) {
        return new ClienteMercadoPago(new ConfiguracionMercadoPago(mock.url(), token, urlApp, Duration.ofSeconds(1)));
    }

    private static ClienteMercadoPago clienteConUrl(String url) {
        return new ClienteMercadoPago(new ConfiguracionMercadoPago(url, "TEST-123",
                "http://localhost:8080/repuestos", Duration.ofSeconds(1)));
    }

    private static PreferenciaMP crearPreferencia(ClienteMercadoPago c, int cantidad) {
        OffsetDateTime ahora = OffsetDateTime.now();
        return c.crearPreferencia(REFERENCIA, "CUB-29-210", "Cubierta MTB 29 x 2.10", cantidad, PRECIO,
                "http://localhost:8080/repuestos/pago.xhtml?venta=7", ahora, ahora.plusMinutes(30));
    }
}
