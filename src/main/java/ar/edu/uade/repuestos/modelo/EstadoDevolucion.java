package ar.edu.uade.repuestos.modelo;

// Patron State: cada estado decide que transiciones permite. Las que no sobrescribe, las rechaza.
public enum EstadoDevolucion implements TransicionesDeDevolucion {

    // Esperando que el proveedor valide la garantia por SOAP (AUR-32)
    PENDIENTE_VALIDACION {
        @Override
        public EstadoDevolucion validar() { return PENDIENTE; }

        @Override
        public EstadoDevolucion rechazar() { return RECHAZADA; }
    },

    PENDIENTE {
        @Override
        public EstadoDevolucion aprobar() { return APROBADA; }

        @Override
        public EstadoDevolucion rechazar() { return RECHAZADA; }
    },

    APROBADA {
        @Override
        public EstadoDevolucion reembolsar() { return REEMBOLSADA; }
    },

    RECHAZADA,

    REEMBOLSADA
}
