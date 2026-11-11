package ar.edu.uade.repuestos.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ResourceInfo;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import jakarta.ws.rs.core.UriInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

// El filtro de la API: 401 sin token valido, 403 con un rol que no alcanza (BFLA)
class SeguridadDeLaApiTest {

    private TokensJwt tokens;
    private ResourceInfo recurso;
    private ContainerRequestContext pedido;
    private SeguridadDeLaApi filtro;

    @BeforeEach
    void preparar() throws Exception {
        tokens = mock(TokensJwt.class);
        recurso = mock(ResourceInfo.class);
        pedido = mock(ContainerRequestContext.class);
        UriInfo uri = mock(UriInfo.class);
        when(uri.getPath()).thenReturn("/devoluciones/7");
        when(pedido.getUriInfo()).thenReturn(uri);
        filtro = new SeguridadDeLaApi(tokens, recurso);
        metodo("resolver", long.class, String.class, String.class);
    }

    private void metodo(String nombre, Class<?>... parametros) throws Exception {
        when(recurso.getResourceMethod()).thenReturn(DevolucionesRecurso.class.getMethod(nombre, parametros));
    }

    private Response rechazo() {
        ArgumentCaptor<Response> respuesta = ArgumentCaptor.forClass(Response.class);
        verify(pedido).abortWith(respuesta.capture());
        return respuesta.getValue();
    }

    @Test
    void elLoginNoPideToken() throws Exception {
        when(recurso.getResourceMethod()).thenReturn(AuthRecurso.class.getMethod("ingresar", String.class));
        filtro.filter(pedido);
        verify(pedido, never()).abortWith(any());
        verifyNoInteractions(tokens);
    }

    @Test
    void sinTokenEs401ConProblemDetails() {
        filtro.filter(pedido);
        Response r = rechazo();
        assertEquals(401, r.getStatus());
        assertEquals("Bearer", r.getHeaderString("WWW-Authenticate"));
        assertEquals(Problema.TIPO, r.getMediaType().toString());
        assertTrue(r.getEntity().toString().contains("\"instance\":\"/repuestos/api/v1/devoluciones/7\""));
    }

    @Test
    void conOtroEsquemaEs401() {
        when(pedido.getHeaderString("Authorization")).thenReturn("Basic YW5hOmNsYXZl");
        filtro.filter(pedido);
        assertEquals(401, rechazo().getStatus());
        verifyNoInteractions(tokens);
    }

    // Vencido, mal firmado o roto: mismo 401 y mismo texto, sin decir cual fue
    @Test
    void conTokenInvalidoEs401SinDarPistas() {
        when(pedido.getHeaderString("Authorization")).thenReturn("Bearer xxx");
        when(tokens.validar("xxx")).thenThrow(new TokenInvalidoException("vencido"));
        filtro.filter(pedido);
        Response r = rechazo();
        assertEquals(401, r.getStatus());
        assertFalse(r.getEntity().toString().contains("vencido\""));
        assertTrue(r.getEntity().toString().contains("no es válido o venció"));
    }

    // BFLA: un cliente con token valido no puede resolver devoluciones
    @Test
    void unClienteNoPuedeUsarLoDelModerador() {
        when(pedido.getHeaderString("Authorization")).thenReturn("Bearer ok");
        when(tokens.validar("ok")).thenReturn(new Sesion(1L, "ana@mail.com", "CLIENTE"));
        filtro.filter(pedido);
        assertEquals(403, rechazo().getStatus());
        verify(pedido, never()).setSecurityContext(any());
    }

    @Test
    void unModeradorNoPuedeCrearDevoluciones() throws Exception {
        metodo("crear", String.class, String.class);
        when(pedido.getHeaderString("Authorization")).thenReturn("Bearer ok");
        when(tokens.validar("ok")).thenReturn(new Sesion(5L, "mod@mail.com", "MODERADOR"));
        filtro.filter(pedido);
        assertEquals(403, rechazo().getStatus());
    }

    @Test
    void conElRolCorrectoPasaYQuedaLaSesion() {
        when(pedido.getHeaderString("Authorization")).thenReturn("Bearer ok");
        when(tokens.validar("ok")).thenReturn(new Sesion(5L, "mod@mail.com", "MODERADOR"));
        SecurityContext original = mock(SecurityContext.class);
        when(original.isSecure()).thenReturn(true);
        when(pedido.getSecurityContext()).thenReturn(original);

        filtro.filter(pedido);

        verify(pedido, never()).abortWith(any());
        ArgumentCaptor<SecurityContext> contexto = ArgumentCaptor.forClass(SecurityContext.class);
        verify(pedido).setSecurityContext(contexto.capture());
        assertEquals("mod@mail.com", contexto.getValue().getUserPrincipal().getName());
        assertTrue(contexto.getValue().isUserInRole("MODERADOR"));
        assertFalse(contexto.getValue().isUserInRole("CLIENTE"));
        assertTrue(contexto.getValue().isSecure());
        assertEquals("Bearer", contexto.getValue().getAuthenticationScheme());
    }

    @Test
    void siNoHayMetodoQueProtegerNoHaceNada() {
        when(recurso.getResourceMethod()).thenReturn(null);
        filtro.filter(pedido);
        verify(pedido, never()).abortWith(any());
    }
}
