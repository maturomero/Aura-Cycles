package ar.edu.uade.repuestos.api;

public class TokenInvalidoException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public TokenInvalidoException(String motivo) {
        super(motivo);
    }
}
