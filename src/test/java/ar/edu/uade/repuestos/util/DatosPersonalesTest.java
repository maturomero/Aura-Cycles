package ar.edu.uade.repuestos.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

// SEC-09: en el log nunca el email entero
class DatosPersonalesTest {

    @ParameterizedTest
    @CsvSource(nullValues = "NULO", value = {
        "ana@mail.com, a***@mail.com",
        "x@uade.edu.ar, x***@uade.edu.ar",
        "sin-arroba, ***",
        "@mail.com, ***",
        "NULO, -"
    })
    void enmascara(String email, String esperado) {
        assertEquals(esperado, DatosPersonales.enmascararEmail(email));
    }
}
