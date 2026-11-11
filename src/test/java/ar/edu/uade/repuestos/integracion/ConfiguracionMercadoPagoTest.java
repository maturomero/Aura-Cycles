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

    @Test
    void sinTokenOConTodoBienNoHayAvisos() {
        assertTrue(ConfiguracionMercadoPago.desdeEntorno().avisos().isEmpty());
        System.setProperty("MP_ACCESS_TOKEN", "APP_USR-123");
        System.setProperty("AURA_URL", "https://192.168.0.15:8443/repuestos");
        assertTrue(ConfiguracionMercadoPago.desdeEntorno().avisos().isEmpty());
    }

    // En docker MP_BASE_URL viene apuntando al simulado: con el token solo, se seguiria usando el simulado sin que se note
    @Test
    void conTokenPeroApuntandoAlSimuladoAvisa() {
        System.setProperty("MP_ACCESS_TOKEN", "APP_USR-123");
        System.setProperty("MP_BASE_URL", "http://mercadopago-mock:8089");
        ConfiguracionMercadoPago c = ConfiguracionMercadoPago.desdeEntorno();
        assertTrue(c.esSimulado());
        assertEquals(1, c.avisos().size());
        assertTrue(c.avisos().get(0).contains("MP_BASE_URL=https://api.mercadopago.com"));
        assertFalse(c.avisos().get(0).contains("APP_USR-123"), "el token no va al log");
    }

    // Mercado Pago no acepta localhost en las URLs de vuelta: el comprador veria un error despues de pagar
    @Test
    void conMercadoPagoRealYLaTiendaEnLocalhostAvisa() {
        System.setProperty("MP_ACCESS_TOKEN", "APP_USR-123");
        ConfiguracionMercadoPago c = ConfiguracionMercadoPago.desdeEntorno();
        assertFalse(c.esSimulado());
        assertEquals(1, c.avisos().size());
        assertTrue(c.avisos().get(0).contains("localhost"));
    }
}
