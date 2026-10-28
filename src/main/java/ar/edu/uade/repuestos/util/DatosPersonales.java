package ar.edu.uade.repuestos.util;

// SEC-09: en los logs el email sale enmascarado (a***@mail.com). Ley 25.326: datos no excesivos
public final class DatosPersonales {

    private DatosPersonales() {
    }

    public static String enmascararEmail(String email) {
        if (email == null || email.isBlank()) {
            return "-";
        }
        int arroba = email.indexOf('@');
        if (arroba <= 0) {
            return "***";
        }
        return email.charAt(0) + "***" + email.substring(arroba);
    }
}
