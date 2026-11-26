package ar.edu.uade.repuestos.presentacion;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CabecerasDeSeguridadTest {

    HttpServletRequest pedido = mock(HttpServletRequest.class);
    HttpServletResponse respuesta = mock(HttpServletResponse.class);
    FilterChain cadena = mock(FilterChain.class);
    CabecerasDeSeguridad filtro = new CabecerasDeSeguridad();

    @BeforeEach
    void contexto() {
        when(pedido.getContextPath()).thenReturn("/repuestos");
    }

    @Test
    void poneLosHeadersDeSeguridad() throws Exception {
        filtro.doFilter(pedido, respuesta, cadena);
        verify(respuesta).setHeader("Content-Security-Policy", CabecerasDeSeguridad.CSP);
        verify(respuesta).setHeader("X-Frame-Options", "DENY");
        verify(cadena).doFilter(pedido, respuesta);
    }

    // DEF-27: con "Cerrar sesion" en otra pestania, Undertow corta los pedidos en curso con UT000010 y salia un 500
    @Test
    void siOtroPedidoCerroLaSesionVuelveAlLoginSin500() throws Exception {
        doThrow(new ServletException("UT000010: Session is invalid abc", new IllegalStateException("UT000010: Session is invalid abc")))
                .when(cadena).doFilter(pedido, respuesta);
        filtro.doFilter(pedido, respuesta, cadena);
        verify(respuesta).sendRedirect("/repuestos/login.xhtml");
    }

    @Test
    void cualquierOtroErrorSigueSuCamino() throws Exception {
        ServletException otro = new ServletException("otra cosa", new IllegalStateException("nada que ver"));
        doThrow(otro).when(cadena).doFilter(pedido, respuesta);
        assertSame(otro, assertThrows(ServletException.class, () -> filtro.doFilter(pedido, respuesta, cadena)));
        verify(respuesta, never()).sendRedirect(anyString());
    }

    @Test
    void siLaRespuestaYaSalioNoRedirige() throws Exception {
        when(respuesta.isCommitted()).thenReturn(true);
        doThrow(new IllegalStateException("UT000010: Session is invalid abc")).when(cadena).doFilter(pedido, respuesta);
        assertThrows(IllegalStateException.class, () -> filtro.doFilter(pedido, respuesta, cadena));
        verify(respuesta, never()).sendRedirect(anyString());
    }
}
