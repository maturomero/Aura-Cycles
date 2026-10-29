package ar.edu.uade.repuestos.presentacion;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

// Headers que pide el eje D. Sin 'unsafe-inline': ni scripts ni estilos en linea (todo sale de app.css y faces.js)
// FORWARD y ERROR: si no, el login que muestra el contenedor y las paginas de error salen sin headers (ZAP)
@WebFilter(urlPatterns = "/*", dispatcherTypes = {DispatcherType.REQUEST, DispatcherType.FORWARD, DispatcherType.ERROR})
public class CabecerasDeSeguridad implements Filter {

    static final String CSP = "default-src 'self'; script-src 'self'; style-src 'self'; "
            + "img-src 'self' data:; font-src 'self'; connect-src 'self'; object-src 'none'; base-uri 'self'; "
            // form-action tambien rige la redireccion a Mercado Pago despues de "Comprar"
            + "form-action 'self' https://*.mercadopago.com.ar https://*.mercadopago.com http://localhost:8089; "
            + "frame-ancestors 'none'";

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
            throws IOException, ServletException {
        HttpServletResponse r = (HttpServletResponse) res;
        r.setHeader("Content-Security-Policy", CSP);
        r.setHeader("Strict-Transport-Security", "max-age=31536000; includeSubDomains");
        r.setHeader("X-Content-Type-Options", "nosniff");
        r.setHeader("X-Frame-Options", "DENY");
        r.setHeader("Referrer-Policy", "same-origin");
        r.setHeader("Permissions-Policy", "camera=(), microphone=(), geolocation=()");
        r.setHeader("Cross-Origin-Opener-Policy", "same-origin");
        r.setHeader("Cache-Control", "no-store");
        chain.doFilter(req, res);
    }
}
