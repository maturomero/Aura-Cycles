package ar.edu.uade.repuestos.integracion;

import jakarta.ejb.ApplicationException;

// Timeout, 5xx, respuesta rota o credenciales malas. La pantalla muestra el fallback; el MDB de reembolsos reintenta
@ApplicationException(rollback = true)
public class MercadoPagoNoDisponibleException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public MercadoPagoNoDisponibleException(String mensaje) {
        super(mensaje);
    }

    public MercadoPagoNoDisponibleException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
