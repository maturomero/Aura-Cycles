package ar.edu.uade.repuestos.integracion;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

// El token nunca va en el repo: sale de los VM arguments del servidor en Eclipse (-DMP_ACCESS_TOKEN=...) o de una variable de entorno
public record ConfiguracionMercadoPago(String urlBase, String accessToken, String urlApp, Duration timeout) {

    public static final Duration TIMEOUT = Duration.ofSeconds(5);
    public static final String API_REAL = "https://api.mercadopago.com";
    public static final String SIMULADO = "http://localhost:8089";
    static final String TOKEN_SIMULADO = "TEST-simulado";

    // Sin token se usa el Mercado Pago simulado: alcanza con correr MockMercadoPago desde Eclipse
    public static ConfiguracionMercadoPago desdeEntorno() {
        String token = valor("MP_ACCESS_TOKEN", null);
        String urlApp = valor("AURA_URL", "https://localhost:8443/repuestos");
        if (token == null) {
            return new ConfiguracionMercadoPago(valor("MP_BASE_URL", SIMULADO), TOKEN_SIMULADO, urlApp, TIMEOUT);
        }
        return new ConfiguracionMercadoPago(valor("MP_BASE_URL", API_REAL), token, urlApp, TIMEOUT);
    }

    public boolean esSimulado() {
        return !urlBase.startsWith(API_REAL);
    }

    // Lo que conviene ver en la consola al arrancar; vacio si la configuracion esta bien
    public List<String> avisos() {
        List<String> avisos = new ArrayList<>();
        if (esSimulado() && !TOKEN_SIMULADO.equals(accessToken)) {
            avisos.add("hay MP_ACCESS_TOKEN pero MP_BASE_URL apunta a " + urlBase
                    + ": se sigue usando el simulado. Para Mercado Pago real, MP_BASE_URL=" + API_REAL);
        }
        if (!esSimulado() && urlAppEsLocal()) {
            avisos.add("AURA_URL es " + urlApp + " y Mercado Pago no acepta localhost en las URLs de vuelta: "
                    + "el comprador ve un error despues de pagar. Poner la IP de la maquina, por ejemplo "
                    + "https://192.168.0.15:8443/repuestos");
        }
        return avisos;
    }

    // Mercado Pago no acepta localhost en back_urls: con el simulado no importa, con el real hay que usar la IP
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
