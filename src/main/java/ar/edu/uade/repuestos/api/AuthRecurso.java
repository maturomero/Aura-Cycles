package ar.edu.uade.repuestos.api;

import ar.edu.uade.repuestos.modelo.Usuario;
import ar.edu.uade.repuestos.negocio.ServicioDeUsuarios;
import ar.edu.uade.repuestos.presentacion.LimitadorDeIntentos;
import jakarta.annotation.security.PermitAll;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.json.Json;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.Locale;
import java.util.Optional;

@Path("/auth")
@RequestScoped
@Produces(MediaType.APPLICATION_JSON)
public class AuthRecurso {

    private static final String RUTA = "auth/login";

    @Inject
    private ServicioDeUsuarios servicioDeUsuarios;

    @Inject
    private LimitadorDeIntentos limitador;

    @Inject
    private TokensJwt tokens;

    @Context
    private HttpServletRequest request;

    public AuthRecurso() {
    }

    AuthRecurso(ServicioDeUsuarios servicioDeUsuarios, LimitadorDeIntentos limitador, TokensJwt tokens, HttpServletRequest request) {
        this.servicioDeUsuarios = servicioDeUsuarios;
        this.limitador = limitador;
        this.tokens = tokens;
        this.request = request;
    }

    @POST
    @Path("/login")
    @PermitAll
    @Consumes(MediaType.APPLICATION_JSON)
    public Response ingresar(String texto) {
        Cuerpo cuerpo = Cuerpo.leer(texto);
        String email = cuerpo.textoObligatorio("email").trim().toLowerCase(Locale.ROOT);
        String password = cuerpo.textoObligatorio("password");

        // El mismo limitador que el login de las pantallas: 5 fallos bloquean 5 minutos por email y por IP
        String[] claves = {"email:" + email, "ip:" + request.getRemoteAddr()};
        long espera = limitador.segundosDeBloqueo(claves);
        if (espera > 0) {
            return Problema.de(429, "demasiados-intentos", "Demasiados intentos",
                            "Demasiados intentos fallidos. Probá de nuevo más tarde.", RUTA)
                    .header(HttpHeaders.RETRY_AFTER, espera).build();
        }

        Optional<Usuario> usuario = servicioDeUsuarios.autenticar(email, password);
        if (usuario.isEmpty()) {
            limitador.fallo(claves);
            return Problema.de(401, "credenciales", "No autenticado", "Email o contraseña incorrectos.", RUTA).build();
        }
        limitador.exito(claves);

        String cuerpoJson = Json.createObjectBuilder()
                .add("token", tokens.emitir(usuario.get()))
                .add("tipo", "Bearer")
                .add("expiraEn", TokensJwt.VIGENCIA.toSeconds())
                .add("rol", usuario.get().getRol().name())
                .build().toString();
        return Response.ok(cuerpoJson).header(HttpHeaders.CACHE_CONTROL, "no-store").build();
    }
}
