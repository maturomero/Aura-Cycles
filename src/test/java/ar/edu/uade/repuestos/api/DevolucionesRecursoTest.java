package ar.edu.uade.repuestos.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ar.edu.uade.repuestos.modelo.Devolucion;
import ar.edu.uade.repuestos.modelo.EstadoDevolucion;
import ar.edu.uade.repuestos.modelo.EstadoVenta;
import ar.edu.uade.repuestos.modelo.MotivoDevolucion;
import ar.edu.uade.repuestos.modelo.Producto;
import ar.edu.uade.repuestos.modelo.Rol;
import ar.edu.uade.repuestos.modelo.Usuario;
import ar.edu.uade.repuestos.modelo.Venta;
import ar.edu.uade.repuestos.negocio.ServicioDeSolicitudes;
import ar.edu.uade.repuestos.negocio.ServicioDeSolicitudes.Solicitud;
import ar.edu.uade.repuestos.util.NoExisteException;
import ar.edu.uade.repuestos.util.TransicionInvalidaException;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.ws.rs.core.EntityTag;
import jakarta.ws.rs.core.Request;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import java.io.StringReader;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

// El recurso REST de devoluciones con la capa de negocio mockeada: codigos, headers y a quien delega
class DevolucionesRecursoTest {

    private static final String CLAVE = "pedido-0001";
    private static final String CUERPO = "{\"ventaId\":9,\"motivo\":\"MEDIDA_INCORRECTA\",\"comentario\":\"Me quedo chica\"}";

    private ServicioDeSolicitudes solicitudes;
    private PuenteDeModeracion moderacion;
    private SecurityContext seguridad;
    private Request pedido;
    private DevolucionesRecurso recurso;
    private Devolucion devolucion;

    @BeforeEach
    void preparar() {
        solicitudes = mock(ServicioDeSolicitudes.class);
        moderacion = mock(PuenteDeModeracion.class);
        seguridad = mock(SecurityContext.class);
        pedido = mock(Request.class);
        recurso = new DevolucionesRecurso(solicitudes, moderacion, seguridad, pedido);
        devolucion = devolucion(7L);
        como(1L, "ana@mail.com", "CLIENTE");
    }

    private void como(Long id, String email, String rol) {
        when(seguridad.getUserPrincipal()).thenReturn(new Sesion(id, email, rol));
    }

    static Devolucion devolucion(Long id) {
        Usuario ana = new Usuario("ana@mail.com", "x", "Ana Torres", Rol.CLIENTE);
        ana.setId(1L);
        Producto p = new Producto();
        p.setId(1L);
        p.setNombre("Cubierta MTB 29 x 2.10");
        Venta v = new Venta(ana, p, 1, new BigDecimal("46800.00"));
        v.setId(9L);
        v.setEstado(EstadoVenta.PAGADA);
        Devolucion d = new Devolucion(v, MotivoDevolucion.MEDIDA_INCORRECTA, "Me quedo chica");
        d.setId(id);
        d.garantiaValidada("RMA-2026-00007");
        return d;
    }

    private static JsonObject json(Response r) {
        return Json.createReader(new StringReader(r.getEntity().toString())).readObject();
    }

    // ---------- POST ----------

    @Test
    void crearDevuelve201ConLocationYETag() {
        when(solicitudes.solicitar("ana@mail.com", CLAVE, 9L, MotivoDevolucion.MEDIDA_INCORRECTA, "Me quedo chica"))
                .thenReturn(new Solicitud(devolucion, 201, false));

        Response r = recurso.crear(CLAVE, CUERPO);

        assertEquals(201, r.getStatus());
        assertEquals("devoluciones/7", r.getLocation().toString());
        assertEquals(DevolucionJson.etag(devolucion), r.getEntityTag());
        assertNull(r.getHeaderString("Idempotent-Replayed"));
        assertNull(r.getHeaderString("Retry-After"));
        JsonObject json = json(r);
        assertEquals("DEV-000007", json.getString("codigo"));
        assertEquals("PENDIENTE", json.getString("estado"));
        assertEquals("RMA-2026-00007", json.getString("rma"));
        assertTrue(json.getBoolean("validadaConProveedor"));
        assertEquals("/repuestos/api/v1/devoluciones/7", json.getJsonObject("_links").getJsonObject("self").getString("href"));
    }

    // Si el SOAP del proveedor no contesto: 202 y cuando volver a mirar
    @Test
    void conElProveedorCaidoEs202ConRetryAfter() {
        Devolucion enCola = new Devolucion(devolucion.getVenta(), MotivoDevolucion.MEDIDA_INCORRECTA, null);
        enCola.setId(8L);
        when(solicitudes.solicitar(anyString(), anyString(), anyLong(), any(), any())).thenReturn(new Solicitud(enCola, 202, false));

        Response r = recurso.crear(CLAVE, "{\"ventaId\":9,\"motivo\":\"MEDIDA_INCORRECTA\"}");

        assertEquals(202, r.getStatus());
        assertEquals("120", r.getHeaderString("Retry-After"));
        JsonObject json = json(r);
        assertEquals("PENDIENTE_VALIDACION", json.getString("estado"));
        assertTrue(json.isNull("rma"));
        assertTrue(json.isNull("comentario"));
        assertTrue(json.isNull("montoReembolso"));
        assertTrue(json.isNull("fechaResolucion"));
        assertEquals(false, json.getBoolean("validadaConProveedor"));
    }

    @Test
    void elReintentoConLaMismaClaveAvisaQueEsRepetido() {
        when(solicitudes.solicitar(anyString(), anyString(), anyLong(), any(), any())).thenReturn(new Solicitud(devolucion, 201, true));
        Response r = recurso.crear(CLAVE, CUERPO);
        assertEquals(201, r.getStatus());
        assertEquals("true", r.getHeaderString("Idempotent-Replayed"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "corta", "con espacios adentro", "con'comilla--", "clave;drop table"})
    void sinUnaIdempotencyKeyValidaNoSeCreaNada(String clave) {
        assertThrows(PedidoInvalidoException.class, () -> recurso.crear(clave, CUERPO));
        verifyNoInteractions(solicitudes);
    }

    @Test
    void sinHeaderOConElCuerpoMalArmadoNoSeCreaNada() {
        assertThrows(PedidoInvalidoException.class, () -> recurso.crear(null, CUERPO));
        assertThrows(PedidoInvalidoException.class, () -> recurso.crear("x".repeat(81), CUERPO));
        assertThrows(PedidoInvalidoException.class, () -> recurso.crear(CLAVE, "{\"motivo\":\"MEDIDA_INCORRECTA\"}"));
        assertThrows(PedidoInvalidoException.class, () -> recurso.crear(CLAVE, "{\"ventaId\":9,\"motivo\":\"NO_EXISTE\"}"));
        assertThrows(PedidoInvalidoException.class, () -> recurso.crear(CLAVE, "{\"ventaId\":9}"));
        verifyNoInteractions(solicitudes);
    }

    // El email sale del token: aunque el cuerpo diga otra cosa, no se usa
    @Test
    void elDuenioSaleDelTokenYNoDelCuerpo() {
        when(solicitudes.solicitar(anyString(), anyString(), anyLong(), any(), any())).thenReturn(new Solicitud(devolucion, 201, false));
        recurso.crear(CLAVE, "{\"ventaId\":9,\"motivo\":\"ARREPENTIMIENTO\",\"email\":\"carlos@mail.com\",\"usuarioId\":2}");
        verify(solicitudes).solicitar(eq("ana@mail.com"), eq(CLAVE), eq(9L), eq(MotivoDevolucion.ARREPENTIMIENTO), eq(null));
    }

    // ---------- GET ----------

    @Test
    void verDevuelveLaDevolucionConSuETag() {
        when(solicitudes.ver(7L, "ana@mail.com", false)).thenReturn(devolucion);
        Response r = recurso.ver(7L);
        assertEquals(200, r.getStatus());
        assertEquals(DevolucionJson.etag(devolucion), r.getEntityTag());
        assertEquals(7, json(r).getInt("id"));
    }

    @Test
    void conIfNoneMatchVigenteEs304SinCuerpo() {
        when(solicitudes.ver(7L, "ana@mail.com", false)).thenReturn(devolucion);
        when(pedido.evaluatePreconditions(DevolucionJson.etag(devolucion))).thenReturn(Response.notModified());
        Response r = recurso.ver(7L);
        assertEquals(304, r.getStatus());
        assertNull(r.getEntity());
        assertEquals(DevolucionJson.etag(devolucion), r.getEntityTag());
    }

    // BOLA: el recurso pasa quien pregunta; que una ajena no se vea lo decide ServicioDeSolicitudes
    @Test
    void unaDevolucionAjenaEsLaMismaRespuestaQueUnaQueNoExiste() {
        como(2L, "carlos@mail.com", "CLIENTE");
        when(solicitudes.ver(7L, "carlos@mail.com", false)).thenThrow(new NoExisteException("La devolución no existe."));
        assertEquals("La devolución no existe.", assertThrows(NoExisteException.class, () -> recurso.ver(7L)).getMessage());
    }

    @Test
    void elClienteListaLasSuyasYElModeradorLaCola() {
        when(solicitudes.delCliente(1L, null)).thenReturn(List.of(devolucion));
        JsonObject delCliente = json(recurso.listar(null));
        assertEquals(1, delCliente.getInt("total"));
        assertEquals(7, delCliente.getJsonArray("items").getJsonObject(0).getInt("id"));
        verify(solicitudes, never()).paraModerar(any());

        como(5L, "mod@mail.com", "MODERADOR");
        when(solicitudes.paraModerar(EstadoDevolucion.APROBADA)).thenReturn(List.of());
        assertEquals(0, json(recurso.listar(" APROBADA ")).getInt("total"));
        recurso.listar("");
        verify(solicitudes).paraModerar(null);
    }

    @Test
    void unEstadoQueNoExisteEnElFiltroEs400() {
        assertThrows(PedidoInvalidoException.class, () -> recurso.listar("CUALQUIERA"));
    }

    // ---------- PATCH ----------

    @Test
    void sinIfMatchNoSeResuelveNada() {
        como(5L, "mod@mail.com", "MODERADOR");
        when(solicitudes.ver(7L, "mod@mail.com", true)).thenReturn(devolucion);
        for (String ifMatch : new String[] {null, " "}) {
            Response r = recurso.resolver(7L, ifMatch, "{\"estado\":\"APROBADA\"}");
            assertEquals(428, r.getStatus());
            assertEquals(Problema.TIPO, r.getMediaType().toString());
        }
        verifyNoInteractions(moderacion);
    }

    // Otro moderador la cambio despues de que la leimos: 412 y el ETag nuevo para reintentar
    @Test
    void conUnETagViejoEs412YNoSeToca() {
        como(5L, "mod@mail.com", "MODERADOR");
        when(solicitudes.ver(7L, "mod@mail.com", true)).thenReturn(devolucion);
        when(pedido.evaluatePreconditions(any(EntityTag.class))).thenReturn(Response.status(412));

        Response r = recurso.resolver(7L, "\"viejo\"", "{\"estado\":\"APROBADA\"}");

        assertEquals(412, r.getStatus());
        assertEquals(DevolucionJson.etag(devolucion), r.getEntityTag());
        verifyNoInteractions(moderacion);
    }

    @Test
    void aprobarDelegaEnLaFachadaYDevuelveElETagNuevo() {
        como(5L, "mod@mail.com", "MODERADOR");
        Devolucion aprobada = devolucion(7L);
        aprobada.aprobar(new BigDecimal("46800.00"));
        when(solicitudes.ver(7L, "mod@mail.com", true)).thenReturn(devolucion, aprobada);
        String antes = DevolucionJson.etag(devolucion).getValue();

        Response r = recurso.resolver(7L, "\"" + antes + "\"", "{\"estado\":\"APROBADA\"}");

        verify(moderacion).aprobar(7L);
        verify(moderacion, never()).rechazar(anyLong());
        assertEquals(200, r.getStatus());
        assertEquals("APROBADA", json(r).getString("estado"));
        assertEquals(46800.00, json(r).getJsonNumber("montoReembolso").doubleValue());
        assertNotEquals(antes, r.getEntityTag().getValue());
    }

    @Test
    void rechazarDelegaEnLaFachada() {
        como(5L, "mod@mail.com", "MODERADOR");
        when(solicitudes.ver(7L, "mod@mail.com", true)).thenReturn(devolucion);
        recurso.resolver(7L, "\"x\"", "{\"estado\":\"RECHAZADA\"}");
        verify(moderacion).rechazar(7L);
        verify(moderacion, never()).aprobar(anyLong());
    }

    // El 409 de la entrega: el State rechaza la transicion y la excepcion sale tal cual hacia ErroresDeLaApi
    @Test
    void siElStateNoPermiteLaTransicionLaExcepcionSale() {
        como(5L, "mod@mail.com", "MODERADOR");
        when(solicitudes.ver(7L, "mod@mail.com", true)).thenReturn(devolucion);
        doThrow(new TransicionInvalidaException("REEMBOLSADA", "aprobar")).when(moderacion).aprobar(7L);
        assertThrows(TransicionInvalidaException.class, () -> recurso.resolver(7L, "\"x\"", "{\"estado\":\"APROBADA\"}"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"estado\":\"REEMBOLSADA\"}", "{\"estado\":\"PENDIENTE\"}", "{\"estado\":\"cualquiera\"}", "{}", "no es json"})
    void porLaApiSoloSePuedeAprobarORechazar(String cuerpo) {
        como(5L, "mod@mail.com", "MODERADOR");
        assertThrows(PedidoInvalidoException.class, () -> recurso.resolver(7L, "\"x\"", cuerpo));
        verify(solicitudes, never()).ver(anyLong(), anyString(), anyBoolean());
        verifyNoInteractions(moderacion);
    }

    // ---------- ETag ----------

    @Test
    void elETagCambiaCuandoCambiaLaDevolucionYSoloEntonces() {
        EntityTag inicial = DevolucionJson.etag(devolucion);
        assertEquals(inicial, DevolucionJson.etag(devolucion(7L)));
        assertNotEquals(inicial, DevolucionJson.etag(devolucion(8L)));
        devolucion.aprobar(new BigDecimal("46800.00"));
        EntityTag aprobada = DevolucionJson.etag(devolucion);
        assertNotEquals(inicial, aprobada);
        devolucion.reembolsar();
        assertNotEquals(aprobada, DevolucionJson.etag(devolucion));
        assertEquals(16, inicial.getValue().length());
    }
}
