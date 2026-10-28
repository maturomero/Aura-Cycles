package ar.edu.uade.repuestos.negocio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ar.edu.uade.repuestos.datos.UsuarioRepository;
import ar.edu.uade.repuestos.modelo.Rol;
import ar.edu.uade.repuestos.modelo.Usuario;
import ar.edu.uade.repuestos.util.PasswordHash;
import ar.edu.uade.repuestos.util.ReglaNegocioException;
import java.util.Optional;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ServicioDeUsuariosTest {

    private static String hashDeClave1234;

    @Mock UsuarioRepository repo;
    @InjectMocks ServicioDeUsuarios servicio;

    @BeforeAll
    static void hash() {
        hashDeClave1234 = PasswordHash.hashear("Clave1234");
    }

    private Usuario ana() {
        Usuario u = new Usuario("ana@mail.com", hashDeClave1234, "Ana", Rol.CLIENTE);
        u.setId(1L);
        return u;
    }

    @Test
    void entraConLaClaveCorrectaAunqueEscribaElMailConMayusculas() {
        when(repo.porEmail("ana@mail.com")).thenReturn(Optional.of(ana()));
        assertTrue(servicio.autenticar("  Ana@Mail.com ", "Clave1234").isPresent());
    }

    @Test
    void conLaClaveMalNoEntra() {
        when(repo.porEmail("ana@mail.com")).thenReturn(Optional.of(ana()));
        assertTrue(servicio.autenticar("ana@mail.com", "clave1234").isEmpty());
    }

    @Test
    void unEmailQueNoExisteNoEntra() {
        when(repo.porEmail("nadie@mail.com")).thenReturn(Optional.empty());
        assertTrue(servicio.autenticar("nadie@mail.com", "Clave1234").isEmpty());
    }

    @ParameterizedTest
    @CsvSource(nullValues = "NULO", value = {"NULO, Clave1234", "'', Clave1234", "ana@mail.com, NULO", "ana@mail.com, '  '"})
    void datosVaciosNiSiquieraConsultanLaBase(String email, String clave) {
        assertTrue(servicio.autenticar(email, clave).isEmpty());
        verify(repo, never()).porEmail(any());
    }

    @Test
    void registrarGuardaClienteConBcryptYEmailNormalizado() {
        when(repo.existeEmail("nuevo@mail.com")).thenReturn(false);
        when(repo.guardar(any())).thenAnswer(i -> i.getArgument(0));

        Usuario u = servicio.registrar(" Nuevo@Mail.com", "Clave1234", "Nuevo");

        assertEquals("nuevo@mail.com", u.getEmail());
        assertEquals(Rol.CLIENTE, u.getRol());
        assertTrue(u.getPasswordHash().startsWith("$2a$12$"));
        assertTrue(PasswordHash.verificar("Clave1234", u.getPasswordHash()));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"sin-arroba", "   "})
    void emailInvalido(String email) {
        assertEquals("El email no es valido.",
                assertThrows(ReglaNegocioException.class, () -> servicio.registrar(email, "Clave1234", "X")).getMessage());
    }

    // Bordes de la clave: 7 no, 8 si, 64 si, 65 no
    @ParameterizedTest
    @CsvSource({"7, false", "8, true", "64, true", "65, false"})
    void largoDeLaClave(int largo, boolean vale) {
        String clave = "a".repeat(largo);
        if (vale) {
            when(repo.existeEmail(any())).thenReturn(false);
            when(repo.guardar(any())).thenAnswer(i -> i.getArgument(0));
            assertEquals("x@mail.com", servicio.registrar("x@mail.com", clave, "X").getEmail());
        } else {
            assertThrows(ReglaNegocioException.class, () -> servicio.registrar("x@mail.com", clave, "X"));
        }
    }

    @Test
    void noSeRepiteElEmail() {
        when(repo.existeEmail("ana@mail.com")).thenReturn(true);
        assertThrows(ReglaNegocioException.class, () -> servicio.registrar("ana@mail.com", "Clave1234", "Ana 2"));
        verify(repo, never()).guardar(any());
    }

    @Test
    void actualizarPerfil() {
        Usuario u = ana();
        when(repo.porId(1L)).thenReturn(Optional.of(u));
        when(repo.existeEmail("ana.t@mail.com")).thenReturn(false);
        when(repo.guardar(any())).thenAnswer(i -> i.getArgument(0));

        servicio.actualizarPerfil(1L, "Ana T", "ANA.T@mail.com");

        assertEquals("Ana T", u.getNombre());
        assertEquals("ana.t@mail.com", u.getEmail());
    }

    @Test
    void elMismoEmailPropioNoCuentaComoRepetido() {
        Usuario u = ana();
        when(repo.porId(1L)).thenReturn(Optional.of(u));
        when(repo.guardar(any())).thenAnswer(i -> i.getArgument(0));
        servicio.actualizarPerfil(1L, "Ana", "ana@mail.com");
        verify(repo, never()).existeEmail(any());
    }

    @Test
    void noPuedeUsarElEmailDeOtro() {
        when(repo.porId(1L)).thenReturn(Optional.of(ana()));
        when(repo.existeEmail("carlos@mail.com")).thenReturn(true);
        assertThrows(ReglaNegocioException.class, () -> servicio.actualizarPerfil(1L, "Ana", "carlos@mail.com"));
        verify(repo, never()).guardar(any());
    }

    @Test
    void usuarioInexistente() {
        when(repo.porId(9L)).thenReturn(Optional.empty());
        assertThrows(ReglaNegocioException.class, () -> servicio.actualizarPerfil(9L, "x", "x@mail.com"));
        assertThrows(ReglaNegocioException.class, () -> servicio.cambiarPassword(9L, "Clave1234", "Nueva12345"));
        assertTrue(servicio.porId(9L).isEmpty());
    }

    @Test
    void cambiarPassword() {
        Usuario u = ana();
        when(repo.porId(1L)).thenReturn(Optional.of(u));
        servicio.cambiarPassword(1L, "Clave1234", "OtraClave99");
        assertTrue(PasswordHash.verificar("OtraClave99", u.getPasswordHash()));
        assertFalse(PasswordHash.verificar("Clave1234", u.getPasswordHash()));
        verify(repo).guardar(u);
    }

    @ParameterizedTest
    @CsvSource({"7, false", "8, true", "64, true", "65, false"})
    void largoDeLaClaveNueva(int largo, boolean vale) {
        Usuario u = ana();
        when(repo.porId(1L)).thenReturn(Optional.of(u));
        if (vale) {
            servicio.cambiarPassword(1L, "Clave1234", "b".repeat(largo));
            assertTrue(PasswordHash.verificar("b".repeat(largo), u.getPasswordHash()));
        } else {
            assertThrows(ReglaNegocioException.class, () -> servicio.cambiarPassword(1L, "Clave1234", "b".repeat(largo)));
        }
    }

    @Test
    void porIdDevuelveElUsuario() {
        Usuario u = ana();
        when(repo.porId(1L)).thenReturn(Optional.of(u));
        assertEquals(u, servicio.porId(1L).orElseThrow());
    }

    @Test
    void cambiarPasswordConLaActualMalOLaNuevaCorta() {
        Usuario u = ana();
        when(repo.porId(1L)).thenReturn(Optional.of(u));
        assertEquals("La contrasena actual no coincide.",
                assertThrows(ReglaNegocioException.class, () -> servicio.cambiarPassword(1L, "mal", "OtraClave99")).getMessage());
        assertThrows(ReglaNegocioException.class, () -> servicio.cambiarPassword(1L, "Clave1234", "corta"));
        assertEquals(hashDeClave1234, u.getPasswordHash());
    }
}
