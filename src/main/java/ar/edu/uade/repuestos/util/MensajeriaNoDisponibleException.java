package ar.edu.uade.repuestos.util;

// El broker no respondio al publicar: rollback completo (nada queda a medias) y el moderador lo reintenta
public class MensajeriaNoDisponibleException extends ReglaNegocioException {

    private static final long serialVersionUID = 1L;

    public MensajeriaNoDisponibleException(Throwable causa) {
        super("No pudimos procesarla ahora: la mensajería no responde. No se cambió nada, probá de nuevo en un rato.");
        initCause(causa);
    }
}
