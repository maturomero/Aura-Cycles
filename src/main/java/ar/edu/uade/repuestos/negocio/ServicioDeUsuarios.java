package ar.edu.uade.repuestos.negocio;

import ar.edu.uade.repuestos.datos.UsuarioRepository;
import ar.edu.uade.repuestos.modelo.Rol;
import ar.edu.uade.repuestos.modelo.Usuario;
import ar.edu.uade.repuestos.util.PasswordHash;
import ar.edu.uade.repuestos.util.ReglaNegocioException;
import jakarta.ejb.Stateless;
import jakarta.inject.Inject;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

@Stateless
public class ServicioDeUsuarios {
    // Solo ASCII (evita emails que se confunden al pasar a minusculas). Sin grupos repetidos: no hay ReDoS
    static final int LARGO_EMAIL = 120;
    static final int LARGO_NOMBRE = 120;
    private static final Pattern LOCAL = Pattern.compile("[A-Za-z0-9._%+-]{1,64}");
    private static final Pattern DOMINIO = Pattern.compile("[A-Za-z0-9.-]{1,189}\\.[A-Za-z]{2,63}");

    @Inject
    private UsuarioRepository usuarioRepository;

    public Optional<Usuario> autenticar(String email, String password) {
        if (email == null || email.isBlank() || password == null || password.isBlank()) {
            return Optional.empty();
        }
        Optional<Usuario> usuario = usuarioRepository.porEmail(email.trim().toLowerCase(Locale.ROOT));
        if (usuario.isEmpty()) {
            PasswordHash.gastarTiempo(password);
            return Optional.empty();
        }
        return usuario.filter(u -> PasswordHash.verificar(password, u.getPasswordHash()));
    }

    public Usuario registrar(String email, String password, String nombre) {
        String limpio = emailValido(email);
        String nombreLimpio = nombreValido(nombre);
        if (password == null || password.length() < 8 || password.length() > PasswordHash.LARGO_MAXIMO) {
            throw new ReglaNegocioException("La contrasena necesita entre 8 y 64 caracteres.");
        }
        if (usuarioRepository.existeEmail(limpio)) {
            throw new ReglaNegocioException("Ya hay una cuenta registrada con ese email.");
        }

        Usuario usuario = new Usuario(limpio, PasswordHash.hashear(password), nombreLimpio, Rol.CLIENTE);
        return usuarioRepository.guardar(usuario);
    }

    public Usuario actualizarPerfil(Long usuarioId, String nombre, String email) {
        Usuario usuario = usuarioRepository.porId(usuarioId)
                .orElseThrow(() -> new ReglaNegocioException("El usuario no existe."));

        String limpio = emailValido(email);
        String nombreLimpio = nombreValido(nombre);
        if (!limpio.equals(usuario.getEmail()) && usuarioRepository.existeEmail(limpio)) {
            throw new ReglaNegocioException("Ese email ya esta en uso.");
        }

        usuario.setNombre(nombreLimpio);
        usuario.setEmail(limpio);
        return usuarioRepository.guardar(usuario);
    }

    public void cambiarPassword(Long usuarioId, String actual, String nueva) {
        Usuario usuario = usuarioRepository.porId(usuarioId)
                .orElseThrow(() -> new ReglaNegocioException("El usuario no existe."));

        if (!PasswordHash.verificar(actual, usuario.getPasswordHash())) {
            throw new ReglaNegocioException("La contrasena actual no coincide.");
        }
        if (nueva == null || nueva.length() < 8 || nueva.length() > PasswordHash.LARGO_MAXIMO) {
            throw new ReglaNegocioException("La contrasena nueva necesita entre 8 y 64 caracteres.");
        }

        usuario.setPasswordHash(PasswordHash.hashear(nueva));
        usuarioRepository.guardar(usuario);
    }

    static String emailValido(String email) {
        String limpio = email == null ? "" : email.trim();
        int arroba = limpio.indexOf('@');
        if (limpio.length() > LARGO_EMAIL || arroba < 0 || arroba != limpio.lastIndexOf('@') || limpio.contains("..")
                || !LOCAL.matcher(limpio.substring(0, arroba)).matches()
                || !DOMINIO.matcher(limpio.substring(arroba + 1)).matches()) {
            throw new ReglaNegocioException("El email no es valido.");
        }
        return limpio.toLowerCase(Locale.ROOT);
    }

    // Mismo largo que la columna: si no, MySQL corta con un 500
    static String nombreValido(String nombre) {
        String limpio = nombre == null ? "" : nombre.trim();
        if (limpio.isEmpty()) {
            throw new ReglaNegocioException("Escribi tu nombre.");
        }
        if (limpio.length() > LARGO_NOMBRE) {
            throw new ReglaNegocioException("El nombre puede tener hasta " + LARGO_NOMBRE + " caracteres.");
        }
        return limpio;
    }

    public Optional<Usuario> porId(Long id) {
        return usuarioRepository.porId(id);
    }
}
