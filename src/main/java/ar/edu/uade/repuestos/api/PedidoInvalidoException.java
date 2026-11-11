package ar.edu.uade.repuestos.api;

// Al pedido le falta algo o un dato tiene mal formato: 400
public class PedidoInvalidoException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public PedidoInvalidoException(String mensaje) {
        super(mensaje);
    }
}
