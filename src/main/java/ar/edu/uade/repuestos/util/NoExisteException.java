package ar.edu.uade.repuestos.util;

// Lo que no existe y lo que es de otro usuario dan el mismo error: no se revela cual de los dos es
public class NoExisteException extends ReglaNegocioException {

    private static final long serialVersionUID = 1L;

    public NoExisteException(String mensaje) {
        super(mensaje);
    }
}
