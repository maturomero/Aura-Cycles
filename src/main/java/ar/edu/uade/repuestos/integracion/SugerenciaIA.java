package ar.edu.uade.repuestos.integracion;

// Lo que la IA le sugiere al moderador. Nunca publica ni rechaza nada por si sola
public record SugerenciaIA(String sugerencia, String motivo, String explicacion, double confianza) implements java.io.Serializable {

    public boolean conIA() {
        return !"SIN_IA".equals(motivo);
    }

    public int porcentaje() {
        return (int) Math.round(confianza * 100);
    }

    public static SugerenciaIA sinIA() {
        return new SugerenciaIA("REVISAR", "SIN_IA", "Sin sugerencia: el asistente no respondió.", 0);
    }
}
