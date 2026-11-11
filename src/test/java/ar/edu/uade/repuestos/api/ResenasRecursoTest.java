package ar.edu.uade.repuestos.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ar.edu.uade.repuestos.modelo.Producto;
import ar.edu.uade.repuestos.modelo.Resena;
import ar.edu.uade.repuestos.modelo.Rol;
import ar.edu.uade.repuestos.modelo.Usuario;
import ar.edu.uade.repuestos.negocio.ServicioDeProductos;
import ar.edu.uade.repuestos.negocio.ServicioDeResenas;
import ar.edu.uade.repuestos.util.NoExisteException;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import java.io.StringReader;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ResenasRecursoTest {

    private ServicioDeResenas resenas;
    private ServicioDeProductos productos;
    private ResenasRecurso recurso;
    private Resena resena;

    @BeforeEach
    void preparar() {
        resenas = mock(ServicioDeResenas.class);
        productos = mock(ServicioDeProductos.class);
        SecurityContext seguridad = mock(SecurityContext.class);
        when(seguridad.getUserPrincipal()).thenReturn(new Sesion(1L, "ana@mail.com", "CLIENTE"));
        recurso = new ResenasRecurso(resenas, productos, seguridad);

        Usuario ana = new Usuario("ana@mail.com", "x", "Ana Torres", Rol.CLIENTE);
        Producto p = new Producto();
        p.setId(3L);
        resena = new Resena(ana, p, 4, null, "Anda bien, cambia suave.");
        resena.setId(11L);
    }

    private static JsonObject json(Response r) {
        return Json.createReader(new StringReader(r.getEntity().toString())).readObject();
    }

    // Lo publico no lleva el email del autor ni el estado de moderacion
    @Test
    void listaLasPublicadasSinDatosDeMas() {
        when(resenas.publicadasDe(3L)).thenReturn(List.of(resena));
        Response r = recurso.publicadas(3L);
        assertEquals(200, r.getStatus());
        JsonObject item = json(r).getJsonArray("items").getJsonObject(0);
        assertEquals(1, json(r).getInt("total"));
        assertEquals("RES-000011", item.getString("codigo"));
        assertEquals("Ana Torres", item.getString("autor"));
        assertEquals("", item.getString("titulo"));
        assertFalse(item.containsKey("estado"));
        assertFalse(r.getEntity().toString().contains("ana@mail.com"));
    }

    @Test
    void siElRepuestoNoExisteEs404AntesDeBuscarNada() {
        when(productos.porId(99L)).thenThrow(new NoExisteException("El repuesto no existe."));
        assertThrows(NoExisteException.class, () -> recurso.publicadas(99L));
        assertThrows(NoExisteException.class, () -> recurso.escribir(99L, "{\"puntaje\":5,\"texto\":\"Muy buena\"}"));
        verify(resenas, never()).publicadasDe(anyLong());
        verify(resenas, never()).escribir(anyLong(), anyLong(), anyInt(), any(), any());
    }

    // El autor es el del token: no se puede resenar en nombre de otro
    @Test
    void laResenaNuevaQuedaPendienteYElAutorSaleDelToken() {
        when(resenas.escribir(1L, 3L, 4, "Buen cambio", "Anda bien, cambia suave.")).thenReturn(resena);
        Response r = recurso.escribir(3L, "{\"puntaje\":4,\"titulo\":\"Buen cambio\",\"texto\":\"Anda bien, cambia suave.\",\"usuarioId\":2}");
        assertEquals(201, r.getStatus());
        assertEquals("PENDIENTE", json(r).getString("estado"));
        verify(resenas).escribir(1L, 3L, 4, "Buen cambio", "Anda bien, cambia suave.");
    }

    @Test
    void puntajeFueraDeRangoOSinTextoEs400() {
        assertThrows(PedidoInvalidoException.class, () -> recurso.escribir(3L, "{\"puntaje\":0,\"texto\":\"x\"}"));
        assertThrows(PedidoInvalidoException.class, () -> recurso.escribir(3L, "{\"puntaje\":6,\"texto\":\"x\"}"));
        assertThrows(PedidoInvalidoException.class, () -> recurso.escribir(3L, "{\"puntaje\":\"5\",\"texto\":\"x\"}"));
        assertThrows(PedidoInvalidoException.class, () -> recurso.escribir(3L, "{\"puntaje\":5}"));
        verify(resenas, never()).escribir(anyLong(), anyLong(), anyInt(), any(), any());
    }
}
