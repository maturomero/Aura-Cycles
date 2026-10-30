package ar.edu.uade.repuestos.integracion;

// Lo que la IA le sugiere al moderador. Nunca publica ni rechaza nada por si sola
public record SugerenciaIA(String sugerencia, String motivo, String explicacion, double confianza) {

    public static SugerenciaIA sinIA() {
        return new SugerenciaIA("REVISAR", "SIN_IA", "Sin sugerencia: el asistente no respondió.", 0);
    }
}
