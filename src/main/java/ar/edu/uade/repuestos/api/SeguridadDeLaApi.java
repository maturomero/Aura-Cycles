package ar.edu.uade.repuestos.api;

import jakarta.annotation.Priority;
import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ResourceInfo;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.SecurityContext;
import jakarta.ws.rs.ext.Provider;
import java.lang.reflect.Method;
import java.security.Principal;
import java.util.Arrays;

// Corre antes de cada recurso: sin token valido 401, con un rol que no alcanza 403
@Provider
@ApplicationScoped
@Priority(Priorities.AUTHENTICATION)
public class SeguridadDeLaApi implements ContainerRequestFilter {

    private static final String BEARER = "Bearer ";

    @Inject
    private TokensJwt tokens;

    @Context
    private ResourceInfo recurso;

    public SeguridadDeLaApi() {
    }

    SeguridadDeLaApi(TokensJwt tokens, ResourceInfo recurso) {
        this.tokens = tokens;
        this.recurso = recurso;
    }

    @Override
    public void filter(ContainerRequestContext pedido) {
        Method metodo = recurso.getResourceMethod();
        if (metodo == null || metodo.isAnnotationPresent(PermitAll.class)) {
            return;
        }
        String ruta = pedido.getUriInfo().getPath();

        String cabecera = pedido.getHeaderString(HttpHeaders.AUTHORIZATION);
        if (cabecera == null || !cabecera.startsWith(BEARER)) {
            pedido.abortWith(sinToken(ruta, "Falta el token. Pedilo en /auth/login y mandalo en Authorization: Bearer."));
            return;
        }

        Sesion sesion;
        try {
            sesion = tokens.validar(cabecera.substring(BEARER.length()).trim());
        } catch (TokenInvalidoException e) {
            // Mismo mensaje para firma mala, vencido o roto: no se dan pistas
            pedido.abortWith(sinToken(ruta, "El token no es válido o venció."));
            return;
        }

        RolesAllowed roles = metodo.getAnnotation(RolesAllowed.class);
        if (roles != null && !Arrays.asList(roles.value()).contains(sesion.rol())) {
            pedido.abortWith(Problema.de(403, "sin-permiso", "Sin permiso",
                    "Tu rol no puede hacer esta operación.", ruta).build());
            return;
        }

        boolean https = pedido.getSecurityContext() != null && pedido.getSecurityContext().isSecure();
        pedido.setSecurityContext(new Contexto(sesion, https));
    }

    private static jakarta.ws.rs.core.Response sinToken(String ruta, String detalle) {
        return Problema.de(401, "no-autenticado", "No autenticado", detalle, ruta)
                .header(HttpHeaders.WWW_AUTHENTICATE, "Bearer")
                .build();
    }

    private record Contexto(Sesion sesion, boolean https) implements SecurityContext {

        @Override
        public Principal getUserPrincipal() {
            return sesion;
        }

        @Override
        public boolean isUserInRole(String rol) {
            return sesion.rol().equals(rol);
        }

        @Override
        public boolean isSecure() {
            return https;
        }

        @Override
        public String getAuthenticationScheme() {
            return "Bearer";
        }
    }
}
