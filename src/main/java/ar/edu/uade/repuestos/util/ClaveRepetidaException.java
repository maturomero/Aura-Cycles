package ar.edu.uade.repuestos.util;

// La misma Idempotency-Key llego con otro contenido: no se puede saber cual de los dos pedidos vale
public class ClaveRepetidaException extends ReglaNegocioException {

    private static final long serialVersionUID = 1L;

    public ClaveRepetidaException() {
        super("Esa Idempotency-Key ya se usó con otros datos.");
    }
}
