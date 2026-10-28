package ar.edu.uade.repuestos.presentacion;

import ar.edu.uade.repuestos.integracion.MercadoPagoNoDisponibleException;
import ar.edu.uade.repuestos.negocio.ServicioDeCobros;
import ar.edu.uade.repuestos.util.ReglaNegocioException;
import jakarta.faces.context.FacesContext;
import java.io.IOException;
import java.io.UncheckedIOException;

// Lo usan Productos (al comprar) y Mis compras (boton Pagar)
final class IrAPagar {

    private IrAPagar() {
    }

    static void ejecutar(ServicioDeCobros cobros, Long ventaId, Long usuarioId, String orden) {
        try {
            String url = cobros.iniciarPago(ventaId, usuarioId);
            if (url == null) {
                Mensajes.ok("El pago de " + orden + " ya estaba acreditado en Mercado Pago.");
                return;
            }
            FacesContext.getCurrentInstance().getExternalContext().redirect(url);
        } catch (MercadoPagoNoDisponibleException e) {
            Mensajes.error("Mercado Pago no responde. Te reservamos " + orden
                    + " por 30 minutos: pagala desde Mis compras en un rato.");
        } catch (ReglaNegocioException e) {
            Mensajes.error(e.getMessage());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
