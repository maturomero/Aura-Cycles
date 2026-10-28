package ar.edu.uade.repuestos.util;

// Hereda el rollback de ReglaNegocioException: si la transicion no vale, no se guarda nada
public class TransicionInvalidaException extends ReglaNegocioException {

    private static final long serialVersionUID = 1L;

    public TransicionInvalidaException(String estadoActual, String accion) {
        this("devolución", estadoActual, accion);
    }

    public TransicionInvalidaException(String entidad, String estadoActual, String accion) {
        super("No se puede " + accion + " una " + entidad + " que está " + estadoActual + ".");
    }
}
