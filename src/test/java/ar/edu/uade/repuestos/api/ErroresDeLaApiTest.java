package ar.edu.uade.repuestos.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ar.edu.uade.repuestos.util.ClaveRepetidaException;
import ar.edu.uade.repuestos.util.MensajeriaNoDisponibleException;
import ar.edu.uade.repuestos.util.NoExisteException;
import ar.edu.uade.repuestos.util.ReglaNegocioException;
import ar.edu.uade.repuestos.util.TransicionInvalidaException;
import jakarta.ejb.EJBAccessException;
import jakarta.ejb.EJBException;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.ws.rs.NotAllowedException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.NotSupportedException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import java.io.StringReader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

// Cada error de la API sale como Problem Details con el codigo que corresponde
class ErroresDeLaApiTest {

    private ErroresDeLaApi errores;

    @BeforeEach
    void preparar() {
        UriInfo uri = mock(UriInfo.class);
        when(uri.getPath()).thenReturn("/devoluciones/7");
        errores = new ErroresDeLaApi(uri);
    }

    private static JsonObject cuerpo(Response r) {
        assertEquals(Problema.TIPO, r.getMediaType().toString());
        return Json.createReader(new StringReader(r.getEntity().toString())).readObject();
    }

    // Lo que pide la 2da entrega: una transicion que el State no permite es un 409
    @Test
    void unaTransicionInvalidaEs409() {
        Response r = errores.toResponse(new TransicionInvalidaException("REEMBOLSADA", "rechazar"));
        assertEquals(409, r.getStatus());
        JsonObject json = cuerpo(r);
        assertEquals("urn:auracycles:problema:transicion-invalida", json.getString("type"));
        assertEquals("Transición inválida", json.getString("title"));
        assertEquals(409, json.getInt("status"));
        assertEquals("No se puede rechazar una devolución que está REEMBOLSADA.", json.getString("detail"));
        assertEquals("/repuestos/api/v1/devoluciones/7", json.getString("instance"));
    }

    @Test
    void cadaErrorDeNegocioTieneSuCodigo() {
        assertEquals(409, errores.toResponse(new ClaveRepetidaException()).getStatus());
        assertEquals(404, errores.toResponse(new NoExisteException("La devolución no existe.")).getStatus());
        assertEquals(422, errores.toResponse(new ReglaNegocioException("El plazo ya venció.")).getStatus());
        assertEquals(400, errores.toResponse(new PedidoInvalidoException("Falta el campo motivo.")).getStatus());
        assertEquals(403, errores.toResponse(new EJBAccessException("no")).getStatus());
    }

    @Test
    void conElBrokerCaidoEs503YDiceCuandoReintentar() {
        Response r = errores.toResponse(new MensajeriaNoDisponibleException(new RuntimeException("broker")));
        assertEquals(503, r.getStatus());
        assertEquals("30", r.getHeaderString("Retry-After"));
    }

    // El contenedor envuelve las excepciones en EJBException: igual se llega a la nuestra
    @Test
    void desenvuelveLoQueEnvuelveElContenedor() {
        Response r = errores.toResponse(new EJBException(new RuntimeException(new TransicionInvalidaException("APROBADA", "aprobar"))));
        assertEquals(409, r.getStatus());
    }

    @Test
    void losErroresDeJaxRsTambienSalenComoProblema() {
        assertEquals(404, errores.toResponse(new NotFoundException()).getStatus());
        assertEquals("Esa ruta no existe en la API.", cuerpo(errores.toResponse(new NotFoundException())).getString("detail"));
        assertEquals(405, errores.toResponse(new NotAllowedException("GET")).getStatus());
        assertEquals("Formato no soportado", cuerpo(errores.toResponse(new NotSupportedException())).getString("title"));
        assertEquals("Pedido inválido", cuerpo(errores.toResponse(new WebApplicationException(400))).getString("title"));
        assertEquals("Pedido rechazado", cuerpo(errores.toResponse(new WebApplicationException(418))).getString("title"));
        assertEquals("Error interno", cuerpo(errores.toResponse(new WebApplicationException(502))).getString("title"));
    }

    @Test
    void unaPrecondicionFallidaConservaElETag() {
        Response r = errores.toResponse(new WebApplicationException(Response.status(412).header("ETag", "\"abc\"").build()));
        assertEquals(412, r.getStatus());
        assertEquals("\"abc\"", r.getHeaderString("ETag"));
    }

    // SEC-06 tambien en la API: lo inesperado es un 500 generico, sin el mensaje ni la clase de la excepcion
    @Test
    void unErrorInesperadoNoMuestraDetalles() {
        Response r = errores.toResponse(new IllegalStateException("SELECT * FROM usuario fallo en com.mysql.Driver"));
        assertEquals(500, r.getStatus());
        String texto = r.getEntity().toString();
        assertFalse(texto.contains("mysql"));
        assertFalse(texto.contains("IllegalState"));
        assertNotNull(cuerpo(r).getString("detail"));
    }

    @Test
    void sinUriIgualResponde() {
        Response r = new ErroresDeLaApi(null).toResponse(new NoExisteException("x"));
        assertTrue(cuerpo(r).getString("instance").startsWith("/repuestos/api/v1/"));
    }
}
