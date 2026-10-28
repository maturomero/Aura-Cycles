package ar.edu.uade.repuestos.integracion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

// Lo mismo que poner -DMP_ACCESS_TOKEN=... en los VM arguments del servidor en Eclipse
class ConfiguracionMercadoPagoTest {

    @AfterEach
    void limpiar() {
        System.clearProperty("MP_ACCESS_TOKEN");
        System.clearProperty("MP_BASE_URL");
        System.clearProperty("AURA_URL");
    }

    @Test
    void sinTokenUsaElSimulado() {
        ConfiguracionMercadoPago c = ConfiguracionMercadoPago.desdeEntorno();
        assertEquals("http://localhost:8089", c.urlBase());
        assertTrue(c.esSimulado());
        assertTrue(c.urlAppEsLocal());
        assertEquals("https://localhost:8443/repuestos", c.urlApp());
    }

    @Test
    void conTokenVaAMercadoPagoReal() {
        System.setProperty("MP_ACCESS_TOKEN", "TEST-123");
        System.setProperty("AURA_URL", "http://192.168.0.15:8080/repuestos");
        ConfiguracionMercadoPago c = ConfiguracionMercadoPago.desdeEntorno();
        assertEquals("https://api.mercadopago.com", c.urlBase());
        assertEquals("TEST-123", c.accessToken());
        assertFalse(c.esSimulado());
        assertFalse(c.urlAppEsLocal());
    }

    @Test
    void elSimuladoPuedeEstarEnOtroPuerto() {
        System.setProperty("MP_BASE_URL", "http://localhost:9090");
        assertEquals("http://localhost:9090", ConfiguracionMercadoPago.desdeEntorno().urlBase());
    }
}
