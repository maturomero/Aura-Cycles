package ar.edu.uade.repuestos.integracion;

import jakarta.ejb.ApplicationException;

// Timeout, Fault tecnico, respuesta rota o XML peligroso. La devolucion queda PENDIENTE_VALIDACION y se reintenta por cola.validaciones
@ApplicationException(rollback = true)
public class ProveedorNoDisponibleException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ProveedorNoDisponibleException(String mensaje) {
        super(mensaje);
    }

    public ProveedorNoDisponibleException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
