package ar.edu.uade.repuestos.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ar.edu.uade.repuestos.modelo.Rol;
import ar.edu.uade.repuestos.modelo.Usuario;
import ar.edu.uade.repuestos.negocio.ServicioDeUsuarios;
import ar.edu.uade.repuestos.presentacion.LimitadorDeIntentos;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.core.Response;
import java.io.StringReader;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

// Login de la API: token para el que entra bien, 401 generico para el que no y 429 ante fuerza bruta
class AuthRecursoTest {

    private ServicioDeUsuarios usuarios;
    private LimitadorDeIntentos limitador;
    private TokensJwt tokens;
    private AuthRecurso recurso;

    @BeforeEach
    void preparar() {
        usuarios = mock(ServicioDeUsuarios.class);
        limitador = new LimitadorDeIntentos();
        tokens = new TokensJwt();
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRemoteAddr()).thenReturn("10.0.0.7");
        recurso = new AuthRecurso(usuarios, limitador, tokens, request);

        Usuario ana = new Usuario("ana@mail.com", "x", "Ana Torres", Rol.CLIENTE);
        ana.setId(1L);
        when(usuarios.autenticar("ana@mail.com", "Clave1234")).thenReturn(Optional.of(ana));
    }

    private static JsonObject json(Response r) {
        return Json.createReader(new StringReader(r.getEntity().toString())).readObject();
    }

    private static String login(String email, String clave) {
        return "{\"email\":\"" + email + "\",\"password\":\"" + clave + "\"}";
    }

    @Test
    void conLaClaveBienDevuelveUnTokenQueSirve() {
        Response r = recurso.ingresar(login(" Ana@Mail.com ", "Clave1234"));
        assertEquals(200, r.getStatus());
        assertEquals("no-store", r.getHeaderString("Cache-Control"));
        JsonObject json = json(r);
        assertEquals("Bearer", json.getString("tipo"));
        assertEquals("CLIENTE", json.getString("rol"));
        assertEquals(900, json.getInt("expiraEn"));
        Sesion sesion = tokens.validar(json.getString("token"));
        assertEquals("ana@mail.com", sesion.email());
        assertEquals(1L, sesion.usuarioId());
    }

    // SEC-01: clave mala y usuario que no existe dan exactamente lo mismo
    @Test
    void claveMalaYUsuarioInexistenteDanElMismo401() {
        when(usuarios.autenticar(anyString(), anyString())).thenReturn(Optional.empty());
        Response mala = recurso.ingresar(login("ana@mail.com", "otra"));
        Response nadie = recurso.ingresar(login("nadie@mail.com", "otra"));
        assertEquals(401, mala.getStatus());
        assertEquals(Problema.TIPO, mala.getMediaType().toString());
        assertEquals(mala.getEntity().toString(), nadie.getEntity().toString());
        assertFalse(mala.getEntity().toString().contains("token"));
    }

    // Fuerza bruta: al quinto fallo, 429 con Retry-After aunque despues manden la clave buena
    @Test
    void alQuintoFalloBloqueaConUn429() {
        when(usuarios.autenticar("ana@mail.com", "mala")).thenReturn(Optional.empty());
        for (int i = 0; i < 5; i++) {
            assertEquals(401, recurso.ingresar(login("ana@mail.com", "mala")).getStatus());
        }
        Response r = recurso.ingresar(login("ana@mail.com", "Clave1234"));
        assertEquals(429, r.getStatus());
        assertTrue(Long.parseLong(r.getHeaderString("Retry-After")) > 0);
        assertEquals(Problema.TIPO, r.getMediaType().toString());
        verify(usuarios, times(5)).autenticar("ana@mail.com", "mala");
        verify(usuarios, times(0)).autenticar("ana@mail.com", "Clave1234");
    }

    // El bloqueo tambien es por IP: probar otro email desde la misma maquina no sirve
    @Test
    void elBloqueoTambienEsPorIp() {
        when(usuarios.autenticar(anyString(), anyString())).thenReturn(Optional.empty());
        for (int i = 0; i < 5; i++) {
            recurso.ingresar(login("usuario" + i + "@mail.com", "mala"));
        }
        assertEquals(429, recurso.ingresar(login("otro@mail.com", "mala")).getStatus());
    }

    @Test
    void entrarBienBorraLosFallosAnteriores() {
        when(usuarios.autenticar("ana@mail.com", "mala")).thenReturn(Optional.empty());
        for (int i = 0; i < 4; i++) {
            recurso.ingresar(login("ana@mail.com", "mala"));
        }
        assertEquals(200, recurso.ingresar(login("ana@mail.com", "Clave1234")).getStatus());
        assertEquals(401, recurso.ingresar(login("ana@mail.com", "mala")).getStatus());
    }

    @Test
    void sinEmailOSinClaveEs400YNiSeConsulta() {
        assertThrows(PedidoInvalidoException.class, () -> recurso.ingresar("{\"email\":\"ana@mail.com\"}"));
        assertThrows(PedidoInvalidoException.class, () -> recurso.ingresar("{\"password\":\"Clave1234\"}"));
        assertThrows(PedidoInvalidoException.class, () -> recurso.ingresar("{\"email\":{\"$ne\":null},\"password\":\"x\"}"));
        assertThrows(PedidoInvalidoException.class, () -> recurso.ingresar("usuario=ana"));
        verifyNoInteractions(usuarios);
    }
}
