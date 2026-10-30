package ar.edu.uade.repuestos.modelo;

import ar.edu.uade.repuestos.util.TransicionInvalidaException;

// Mismo State que la devolucion: solo PENDIENTE_PAGO tiene salidas, el resto son finales
public enum EstadoVenta {

    PENDIENTE_PAGO("Pendiente de pago") {
        @Override
        public EstadoVenta pagar() { return PAGADA; }

        @Override
        public EstadoVenta rechazar() { return RECHAZADA; }

        @Override
        public EstadoVenta vencer() { return VENCIDA; }
    },

    PAGADA("Pagada"),

    RECHAZADA("Pago rechazado"),

    VENCIDA("Reserva vencida");

    private final String descripcion;

    EstadoVenta(String descripcion) {
        this.descripcion = descripcion;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public EstadoVenta pagar() {
        throw new TransicionInvalidaException("compra", name(), "pagar");
    }

    public EstadoVenta rechazar() {
        throw new TransicionInvalidaException("compra", name(), "rechazar");
    }

    public EstadoVenta vencer() {
        throw new TransicionInvalidaException("compra", name(), "vencer");
    }
}
