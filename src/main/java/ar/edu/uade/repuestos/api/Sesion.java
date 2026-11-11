package ar.edu.uade.repuestos.api;

import java.security.Principal;

// Quien hace el pedido, segun el JWT
public record Sesion(Long usuarioId, String email, String rol) implements Principal {

    @Override
    public String getName() {
        return email;
    }

    public boolean esModerador() {
        return "MODERADOR".equals(rol);
    }
}
