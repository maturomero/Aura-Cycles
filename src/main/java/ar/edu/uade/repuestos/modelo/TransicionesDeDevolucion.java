package ar.edu.uade.repuestos.modelo;

import ar.edu.uade.repuestos.util.TransicionInvalidaException;

// Interfaz State. Por defecto toda transicion es invalida; cada estado habilita las suyas.
public interface TransicionesDeDevolucion {

    String name();

    default EstadoDevolucion aprobar() {
        throw new TransicionInvalidaException(name(), "aprobar");
    }

    default EstadoDevolucion rechazar() {
        throw new TransicionInvalidaException(name(), "rechazar");
    }

    default EstadoDevolucion reembolsar() {
        throw new TransicionInvalidaException(name(), "reembolsar");
    }
}
