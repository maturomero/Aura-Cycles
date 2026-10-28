package ar.edu.uade.repuestos.integracion;

import ar.edu.uade.repuestos.util.ReglaNegocioException;

// Fault de negocio del proveedor. Al cliente le llega un mensaje nuestro, nunca el texto del Fault
public class GarantiaNoValidaException extends ReglaNegocioException {

    private static final long serialVersionUID = 1L;

    private final String codigo;

    public GarantiaNoValidaException(String codigo) {
        super(mensajePara(codigo));
        this.codigo = codigo;
    }

    public String getCodigo() {
        return codigo;
    }

    static String mensajePara(String codigo) {
        return switch (codigo == null ? "" : codigo) {
            case "CONSUMIBLE" -> "Es un repuesto de desgaste: el fabricante no da garantía.";
            case "FUERA_DE_PLAZO" -> "La garantía del fabricante para este repuesto ya venció.";
            default -> "El proveedor no aceptó la garantía de este repuesto.";
        };
    }
}
