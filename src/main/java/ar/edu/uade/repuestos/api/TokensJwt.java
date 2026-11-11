package ar.edu.uade.repuestos.api;

import ar.edu.uade.repuestos.modelo.Usuario;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import jakarta.enterprise.context.ApplicationScoped;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.text.ParseException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

// Emite y valida los JWT de la API. Firma HS256 con una clave que nunca esta en el repo
@ApplicationScoped
public class TokensJwt {

    public static final Duration VIGENCIA = Duration.ofMinutes(15);
    static final String EMISOR = "aura-cycles";
    static final int LARGO_MINIMO = 32;

    private static final Logger LOG = Logger.getLogger(TokensJwt.class.getName());
    private static final Set<String> ROLES = Set.of("CLIENTE", "MODERADOR");

    private static final SecureRandom AZAR = new SecureRandom();

    private final byte[] secreto;
    private final Clock reloj;

    public TokensJwt() {
        this(secretoDesde(configurado()), Clock.systemUTC());
    }

    TokensJwt(byte[] secreto, Clock reloj) {
        this.secreto = secreto.clone();
        this.reloj = reloj;
    }

    public String emitir(Usuario usuario) {
        Instant ahora = reloj.instant();
        JWTClaimsSet datos = new JWTClaimsSet.Builder()
                .issuer(EMISOR)
                .subject(usuario.getEmail())
                .claim("uid", usuario.getId())
                .claim("rol", usuario.getRol().name())
                .jwtID(UUID.randomUUID().toString())
                .issueTime(Date.from(ahora))
                .expirationTime(Date.from(ahora.plus(VIGENCIA)))
                .build();
        try {
            SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), datos);
            jwt.sign(new MACSigner(secreto));
            return jwt.serialize();
        } catch (JOSEException e) {
            throw new IllegalStateException("No se pudo firmar el token", e);
        }
    }

    public Sesion validar(String token) {
        try {
            SignedJWT jwt = SignedJWT.parse(token);
            // Solo HS256: un token con alg "none" u otro algoritmo no llega a verificarse
            if (!JWSAlgorithm.HS256.equals(jwt.getHeader().getAlgorithm()) || !jwt.verify(new MACVerifier(secreto))) {
                throw new TokenInvalidoException("firma");
            }
            JWTClaimsSet datos = jwt.getJWTClaimsSet();
            Date vence = datos.getExpirationTime();
            if (vence == null || !vence.toInstant().isAfter(reloj.instant())) {
                throw new TokenInvalidoException("vencido");
            }
            String rol = datos.getStringClaim("rol");
            Long uid = datos.getLongClaim("uid");
            if (!EMISOR.equals(datos.getIssuer()) || datos.getSubject() == null || uid == null || rol == null || !ROLES.contains(rol)) {
                throw new TokenInvalidoException("datos");
            }
            return new Sesion(uid, datos.getSubject(), rol);
        } catch (ParseException | JOSEException e) {
            throw new TokenInvalidoException("formato");
        }
    }

    // Sin JWT_SECRET (o con una demasiado corta para HS256) se genera una al arrancar:
    // los tokens dejan de valer cuando se reinicia el servidor
    static byte[] secretoDesde(String valor) {
        if (valor != null && valor.trim().length() >= LARGO_MINIMO) {
            return valor.trim().getBytes(StandardCharsets.UTF_8);
        }
        LOG.info("[JWT] sin JWT_SECRET de al menos " + LARGO_MINIMO + " caracteres: se usa una clave generada para esta ejecucion");
        byte[] generada = new byte[LARGO_MINIMO];
        AZAR.nextBytes(generada);
        return generada;
    }

    private static String configurado() {
        String valor = System.getProperty("JWT_SECRET");
        return valor == null || valor.isBlank() ? System.getenv("JWT_SECRET") : valor;
    }
}
