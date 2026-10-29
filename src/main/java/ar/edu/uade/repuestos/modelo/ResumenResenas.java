package ar.edu.uade.repuestos.modelo;

import java.io.Serializable;

// Promedio y cantidad de resenas aprobadas de un producto
public record ResumenResenas(double promedio, long cantidad) implements Serializable {

    public static final ResumenResenas SIN_RESENAS = new ResumenResenas(0, 0);
}
