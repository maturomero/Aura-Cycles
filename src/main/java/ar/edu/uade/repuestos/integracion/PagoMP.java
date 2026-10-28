package ar.edu.uade.repuestos.integracion;

import java.math.BigDecimal;

// Lo que nos importa de un pago de Mercado Pago. estado: approved, rejected, pending, in_process, cancelled, refunded...
public record PagoMP(String id, String estado, String referencia, BigDecimal monto, String moneda) {

    public boolean aprobado() {
        return "approved".equals(estado);
    }
}
