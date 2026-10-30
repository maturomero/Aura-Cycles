package ar.edu.uade.repuestos.util;

import org.mindrot.jbcrypt.BCrypt;

// SEC-02: bcrypt con costo 12 (unas 250 ms por intento). WildFly valida el mismo hash con el modular-crypt-mapper
public final class PasswordHash {

    public static final int COSTO = 12;
    public static final int LARGO_MAXIMO = 64;  // bcrypt ignora lo que pase de 72 bytes

    // Hash de una clave que nadie tiene: se compara igual cuando el email no existe, asi tarda lo mismo
    private static final String HASH_FALSO = BCrypt.hashpw("no-existe-este-usuario", BCrypt.gensalt(COSTO));

    private PasswordHash() {
    }

    public static String hashear(String password) {
        return BCrypt.hashpw(password, BCrypt.gensalt(COSTO));
    }

    public static boolean verificar(String password, String guardado) {
        if (password == null || guardado == null || !guardado.startsWith("$2a$")) {
            return false;
        }
        try {
            return BCrypt.checkpw(password, guardado);
        } catch (RuntimeException e) {
            // jBCrypt tira StringIndexOutOfBounds con un hash cortado: es un "no", no un error 500
            return false;
        }
    }

    // Para que "el email no existe" y "la clave esta mal" tarden lo mismo (SEC-01)
    public static void gastarTiempo(String password) {
        BCrypt.checkpw(password == null ? "" : password, HASH_FALSO);
    }
}
