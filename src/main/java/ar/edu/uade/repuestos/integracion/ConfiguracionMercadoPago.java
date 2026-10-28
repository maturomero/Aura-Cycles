package ar.edu.uade.repuestos.integracion;

import java.net.URI;
import java.time.Duration;

// El token nunca va en el repo: sale de los VM arguments del servidor en Eclipse (-DMP_ACCESS_TOKEN=...) o de una variable de entorno
public record ConfiguracionMercadoPago(String urlBase, String accessToken, String urlApp, Duration timeout) {

    public static final Duration TIMEOUT = Duration.ofSeconds(5);
    public static final String API_REAL = "https://api.mercadopago.com";
    public static final String SIMULADO = "http://localhost:8089";

    // Sin token se usa el Mercado Pago simulado: alcanza con correr MockMercadoPago desde Eclipse
    public static ConfiguracionMercadoPago desdeEntorno() {
        String token = valor("MP_ACCESS_TOKEN", null);
        String urlApp = valor("AURA_URL", "http://localhost:8080/repuestos");
        if (token == null) {
            return new ConfiguracionMercadoPago(valor("MP_BASE_URL", SIMULADO), "TEST-simulado", urlApp, TIMEOUT);
        }
        return new ConfiguracionMercadoPago(valor("MP_BASE_URL", API_REAL), token, urlApp, TIMEOUT);
    }

    public boolean esSimulado() {
        return !urlBase.startsWith(API_REAL);
    }

    // auto_return no funciona con back_urls en localhost: ahi el cliente vuelve con el boton "Volver al sitio"
    public boolean urlAppEsLocal() {
        String host = URI.create(urlApp).getHost();
        return host == null || host.equals("localhost") || host.startsWith("127.");
    }

    private static String valor(String nombre, String porDefecto) {
        String valor = System.getProperty(nombre);
        if (valor == null || valor.isBlank()) {
            valor = System.getenv(nombre);
        }
        return valor == null || valor.isBlank() ? porDefecto : valor.trim();
    }
}
