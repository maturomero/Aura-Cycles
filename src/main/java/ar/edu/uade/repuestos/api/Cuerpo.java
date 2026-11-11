package ar.edu.uade.repuestos.api;

import jakarta.json.Json;
import jakarta.json.JsonException;
import jakarta.json.JsonNumber;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;
import java.io.StringReader;

// Lee el JSON de un pedido y valida los campos. Lo que venga mal termina en un 400
final class Cuerpo {

    static final int LARGO_MAXIMO = 10_000;

    private final JsonObject json;

    private Cuerpo(JsonObject json) {
        this.json = json;
    }

    static Cuerpo leer(String texto) {
        if (texto == null || texto.isBlank()) {
            throw new PedidoInvalidoException("Falta el cuerpo del pedido.");
        }
        if (texto.length() > LARGO_MAXIMO) {
            throw new PedidoInvalidoException("El cuerpo del pedido es demasiado largo.");
        }
        try (JsonReader lector = Json.createReader(new StringReader(texto))) {
            return new Cuerpo(lector.readObject());
        } catch (JsonException | IllegalStateException e) {
            throw new PedidoInvalidoException("El cuerpo del pedido no es un JSON válido.");
        }
    }

    String texto(String campo) {
        JsonValue valor = json.get(campo);
        if (valor == null || valor == JsonValue.NULL) {
            return null;
        }
        if (!(valor instanceof JsonString s)) {
            throw new PedidoInvalidoException("El campo " + campo + " tiene que ser un texto.");
        }
        return s.getString();
    }

    String textoObligatorio(String campo) {
        String valor = texto(campo);
        if (valor == null || valor.isBlank()) {
            throw new PedidoInvalidoException("Falta el campo " + campo + ".");
        }
        return valor;
    }

    long entero(String campo) {
        JsonValue valor = json.get(campo);
        if (!(valor instanceof JsonNumber n) || !n.isIntegral()) {
            throw new PedidoInvalidoException("El campo " + campo + " tiene que ser un número entero.");
        }
        try {
            return n.longValueExact();
        } catch (ArithmeticException e) {
            throw new PedidoInvalidoException("El campo " + campo + " está fuera de rango.");
        }
    }

    <E extends Enum<E>> E opcion(String campo, Class<E> tipo) {
        String valor = textoObligatorio(campo);
        try {
            return Enum.valueOf(tipo, valor.trim());
        } catch (IllegalArgumentException e) {
            throw new PedidoInvalidoException("El valor de " + campo + " no es uno de los permitidos.");
        }
    }
}
