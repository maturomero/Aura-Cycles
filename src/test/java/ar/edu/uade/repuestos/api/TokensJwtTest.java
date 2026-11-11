package ar.edu.uade.repuestos.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ar.edu.uade.repuestos.modelo.Rol;
import ar.edu.uade.repuestos.modelo.Usuario;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Date;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

// JWT de la API: lo que se acepta y, sobre todo, lo que no
class TokensJwtTest {

    private static final byte[] SECRETO = "clave-de-prueba-de-32-caracteres!!".getBytes(StandardCharsets.UTF_8);

    private Instant ahora;
    private TokensJwt tokens;

    @BeforeEach
    void preparar() {
        ahora = Instant.parse("2026-11-13T12:00:00Z");
        tokens = new TokensJwt(SECRETO, reloj());
    }

    private Clock reloj() {
        return new Clock() {
            public ZoneId getZone() { return ZoneOffset.UTC; }
            public Clock withZone(ZoneId z) { return this; }
            public Instant instant() { return ahora; }
        };
    }

    private static Usuario usuario(Long id, String email, Rol rol) {
        Usuario u = new Usuario(email, "x", "Nombre", rol);
        u.setId(id);
        return u;
    }

    @Test
    void elTokenEmitidoTraeAlUsuarioYSuRol() {
        Sesion sesion = tokens.validar(tokens.emitir(usuario(5L, "mod@mail.com", Rol.MODERADOR)));
        assertEquals(5L, sesion.usuarioId());
        assertEquals("mod@mail.com", sesion.email());
        assertEquals("mod@mail.com", sesion.getName());
        assertEquals("MODERADOR", sesion.rol());
        assertTrue(sesion.esModerador());
        assertFalse(tokens.validar(tokens.emitir(usuario(1L, "ana@mail.com", Rol.CLIENTE))).esModerador());
    }

    @Test
    void valeQuinceMinutosYNiUnSegundoMas() {
        String token = tokens.emitir(usuario(1L, "ana@mail.com", Rol.CLIENTE));
        ahora = ahora.plus(TokensJwt.VIGENCIA).minusSeconds(1);
        assertEquals("ana@mail.com", tokens.validar(token).email());
        ahora = ahora.plusSeconds(1);
        assertEquals("vencido", assertThrows(TokenInvalidoException.class, () -> tokens.validar(token)).getMessage());
    }

    @Test
    void firmadoConOtraClaveNoPasa() {
        TokensJwt otro = new TokensJwt("otra-clave-distinta-de-32-caracteres".getBytes(StandardCharsets.UTF_8), reloj());
        String ajeno = otro.emitir(usuario(1L, "ana@mail.com", Rol.CLIENTE));
        assertEquals("firma", assertThrows(TokenInvalidoException.class, () -> tokens.validar(ajeno)).getMessage());
    }

    // El ataque clasico: cambiar el rol en el payload y dejar la firma vieja
    @Test
    void siSeTocaElPayloadLaFirmaYaNoCoincide() {
        String[] partes = tokens.emitir(usuario(1L, "ana@mail.com", Rol.CLIENTE)).split("\\.");
        String payload = new String(Base64.getUrlDecoder().decode(partes[1]), StandardCharsets.UTF_8).replace("CLIENTE", "MODERADOR");
        String adulterado = partes[0] + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(payload.getBytes(StandardCharsets.UTF_8)) + "." + partes[2];
        assertThrows(TokenInvalidoException.class, () -> tokens.validar(adulterado));
    }

    @Test
    void conAlgNoneNoPasa() {
        String cabecera = Base64.getUrlEncoder().withoutPadding().encodeToString("{\"alg\":\"none\"}".getBytes(StandardCharsets.UTF_8));
        String payload = Base64.getUrlEncoder().withoutPadding().encodeToString(
                "{\"sub\":\"ana@mail.com\",\"uid\":1,\"rol\":\"MODERADOR\",\"iss\":\"aura-cycles\",\"exp\":4102444800}".getBytes(StandardCharsets.UTF_8));
        assertThrows(TokenInvalidoException.class, () -> tokens.validar(cabecera + "." + payload + "."));
    }

    @Test
    void cualquierCosaQueNoSeaUnJwtNoPasa() {
        for (String basura : new String[] {"", "abc", "a.b.c", "Bearer x", "eyJhbGciOiJIUzI1NiJ9..", "' OR 1=1 --"}) {
            assertThrows(TokenInvalidoException.class, () -> tokens.validar(basura), basura);
        }
    }

    // Bien firmado pero con datos que no emitimos nosotros: rol inventado, sin uid, otro emisor o sin vencimiento
    @Test
    void bienFirmadoPeroConDatosRarosNoPasa() throws Exception {
        Date vence = Date.from(ahora.plusSeconds(60));
        assertThrows(TokenInvalidoException.class, () -> tokens.validar(firmar(base().claim("rol", "ADMIN").expirationTime(vence).build())));
        assertThrows(TokenInvalidoException.class, () -> tokens.validar(firmar(
                new JWTClaimsSet.Builder().issuer(TokensJwt.EMISOR).subject("ana@mail.com").claim("rol", "CLIENTE").expirationTime(vence).build())));
        assertThrows(TokenInvalidoException.class, () -> tokens.validar(firmar(base().issuer("otro").expirationTime(vence).build())));
        assertThrows(TokenInvalidoException.class, () -> tokens.validar(firmar(base().build())));
        assertThrows(TokenInvalidoException.class, () -> tokens.validar(firmar(
                new JWTClaimsSet.Builder().issuer(TokensJwt.EMISOR).claim("uid", 1).claim("rol", "CLIENTE").expirationTime(vence).build())));
        assertEquals("CLIENTE", tokens.validar(firmar(base().expirationTime(vence).build())).rol());
    }

    // HS256 necesita 32 caracteres: una clave mas corta no se usa, se genera una
    @Test
    void unaClaveCortaOVaciaSeReemplazaPorUnaGenerada() {
        String buena = "una-clave-configurada-de-32-chars!";
        assertEquals(buena, new String(TokensJwt.secretoDesde("  " + buena + " "), StandardCharsets.UTF_8));
        for (String mala : new String[] {null, "", "   ", "corta", "x".repeat(31)}) {
            byte[] generada = TokensJwt.secretoDesde(mala);
            assertEquals(TokensJwt.LARGO_MINIMO, generada.length);
            assertFalse(java.util.Arrays.equals(generada, TokensJwt.secretoDesde(mala)));
        }
    }

    // Sin JWT_SECRET arranca igual, con una clave propia de esa ejecucion
    @Test
    void sinClaveConfiguradaGeneraUna() {
        TokensJwt porDefecto = new TokensJwt();
        String token = porDefecto.emitir(usuario(1L, "ana@mail.com", Rol.CLIENTE));
        assertEquals("ana@mail.com", porDefecto.validar(token).email());
        assertThrows(TokenInvalidoException.class, () -> new TokensJwt().validar(token));
    }

    private static JWTClaimsSet.Builder base() {
        return new JWTClaimsSet.Builder().issuer(TokensJwt.EMISOR).subject("ana@mail.com").claim("uid", 1).claim("rol", "CLIENTE");
    }

    private static String firmar(JWTClaimsSet datos) throws Exception {
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), datos);
        jwt.sign(new MACSigner(SECRETO));
        return jwt.serialize();
    }
}
