package ar.edu.uade.repuestos.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

// SEC-02: bcrypt con costo 12, en el formato que entiende el realm de Elytron
class PasswordHashTest {

    @Test
    void guardaBcryptConCosto12() {
        String h = PasswordHash.hashear("Clave1234");
        assertTrue(h.startsWith("$2a$12$"), h);
        assertEquals(60, h.length());
    }

    @Test
    void laMismaClaveDaHashesDistintos() {
        assertNotEquals(PasswordHash.hashear("Clave1234"), PasswordHash.hashear("Clave1234"));
    }

    @Test
    void verificaLaClaveCorrectaYRechazaOtra() {
        String h = PasswordHash.hashear("Clave1234");
        assertTrue(PasswordHash.verificar("Clave1234", h));
        assertFalse(PasswordHash.verificar("clave1234", h));
        assertFalse(PasswordHash.verificar("Clave12345", h));
    }

    // Los hashes viejos (sal:sha256) o cualquier cosa rara no dan acceso
    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"LbXeJJyPwq0KuE0kHzDnfw==:lwE9/IYKe2vZglC2y0YVdLwRBJTLhohBWoK7iu4b320=", "$2a$12$roto", "Clave1234"})
    void unHashQueNoEsBcryptValidoNoDaAcceso(String guardado) {
        assertFalse(PasswordHash.verificar("Clave1234", guardado));
    }

    // Que bcrypt sea lento es la idea: si esto tarda poco, alguien bajo el costo
    @Test
    void cadaVerificacionTardaAlMenos100ms() {
        String h = PasswordHash.hashear("Clave1234");
        long t = System.nanoTime();
        PasswordHash.verificar("otra", h);
        assertTrue((System.nanoTime() - t) / 1_000_000 >= 100);
    }

    @Test
    void elUsuarioQueNoExisteTardaLoMismo() {
        long t = System.nanoTime();
        PasswordHash.gastarTiempo("lo-que-sea");
        assertTrue((System.nanoTime() - t) / 1_000_000 >= 100);
    }
}
