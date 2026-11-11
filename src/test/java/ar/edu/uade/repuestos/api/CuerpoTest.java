package ar.edu.uade.repuestos.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import ar.edu.uade.repuestos.modelo.MotivoDevolucion;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

// Validacion del JSON que llega a la API: lo que venga mal es un 400, nunca un 500
class CuerpoTest {

    @Test
    void leeLosCamposDeUnPedidoBienArmado() {
        Cuerpo c = Cuerpo.leer("{\"ventaId\": 9, \"motivo\": \"DEFECTUOSO\", \"comentario\": \"Vino rota\", \"nada\": null}");
        assertEquals(9L, c.entero("ventaId"));
        assertEquals(MotivoDevolucion.DEFECTUOSO, c.opcion("motivo", MotivoDevolucion.class));
        assertEquals("Vino rota", c.texto("comentario"));
        assertEquals("Vino rota", c.textoObligatorio("comentario"));
        assertNull(c.texto("nada"));
        assertNull(c.texto("no-esta"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "{", "[1,2]", "hola", "{\"a\":}", "null"})
    void loQueNoEsUnObjetoJsonSeRechaza(String texto) {
        assertThrows(PedidoInvalidoException.class, () -> Cuerpo.leer(texto));
    }

    @Test
    void sinCuerpoSeRechaza() {
        assertThrows(PedidoInvalidoException.class, () -> Cuerpo.leer(null));
    }

    @Test
    void unCuerpoEnormeSeRechazaSinParsearlo() {
        String enorme = "{\"comentario\":\"" + "a".repeat(Cuerpo.LARGO_MAXIMO) + "\"}";
        assertEquals("El cuerpo del pedido es demasiado largo.",
                assertThrows(PedidoInvalidoException.class, () -> Cuerpo.leer(enorme)).getMessage());
    }

    @Test
    void losTiposTienenQueSerLosEsperados() {
        Cuerpo c = Cuerpo.leer("{\"ventaId\": \"9\", \"decimal\": 1.5, \"enorme\": 99999999999999999999, \"motivo\": 3, \"vacio\": \" \", \"raro\": \"OTRO\"}");
        assertThrows(PedidoInvalidoException.class, () -> c.entero("ventaId"));
        assertThrows(PedidoInvalidoException.class, () -> c.entero("decimal"));
        assertThrows(PedidoInvalidoException.class, () -> c.entero("enorme"));
        assertThrows(PedidoInvalidoException.class, () -> c.entero("no-esta"));
        assertThrows(PedidoInvalidoException.class, () -> c.texto("motivo"));
        assertThrows(PedidoInvalidoException.class, () -> c.textoObligatorio("vacio"));
        assertThrows(PedidoInvalidoException.class, () -> c.textoObligatorio("no-esta"));
        assertThrows(PedidoInvalidoException.class, () -> c.opcion("raro", MotivoDevolucion.class));
    }

    // Una inyeccion en un campo de opciones no llega a ningun lado: no es uno de los valores permitidos
    @Test
    void unValorConInyeccionNoEsUnaOpcionValida() {
        Cuerpo c = Cuerpo.leer("{\"motivo\": \"DEFECTUOSO' OR '1'='1\"}");
        assertThrows(PedidoInvalidoException.class, () -> c.opcion("motivo", MotivoDevolucion.class));
    }
}
