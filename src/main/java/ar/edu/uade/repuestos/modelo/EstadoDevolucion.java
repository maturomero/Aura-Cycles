package ar.edu.uade.repuestos.modelo;

// Patron State: cada estado decide que transiciones permite. Las que no sobrescribe, las rechaza.
public enum EstadoDevolucion implements TransicionesDeDevolucion {

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
