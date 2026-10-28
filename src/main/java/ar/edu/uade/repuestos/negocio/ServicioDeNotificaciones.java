package ar.edu.uade.repuestos.negocio;

import ar.edu.uade.repuestos.modelo.Devolucion;
import ar.edu.uade.repuestos.modelo.Resena;
import ar.edu.uade.repuestos.modelo.Usuario;
import jakarta.ejb.Stateless;
import static ar.edu.uade.repuestos.util.DatosPersonales.enmascararEmail;
import java.util.logging.Logger;

// Hoy los avisos van al log. El email sale enmascarado (SEC-09)
@Stateless
public class ServicioDeNotificaciones {
    private static final Logger LOG = Logger.getLogger(ServicioDeNotificaciones.class.getName());

    public void devolucionRecibida(Devolucion devolucion) {
        Usuario cliente = devolucion.getVenta().getUsuario();
        LOG.info(() -> String.format(
            "[NOTIFICACION] Para %s: recibimos tu solicitud %s por %s. Queda pendiente de revision.",
            enmascararEmail(cliente.getEmail()), devolucion.getCodigo(),
            devolucion.getVenta().getProducto().getNombre()));
    }

    public void devolucionResuelta(Devolucion devolucion) {
        Usuario cliente = devolucion.getVenta().getUsuario();
        LOG.info(() -> String.format(
            "[NOTIFICACION] Para %s: tu solicitud %s quedo %s.",
            enmascararEmail(cliente.getEmail()), devolucion.getCodigo(), devolucion.getEstado()));
    }

    public void reembolsoEmitido(Devolucion devolucion) {
        Usuario cliente = devolucion.getVenta().getUsuario();
        LOG.info(() -> String.format(
            "[NOTIFICACION] Para %s: emitimos un reembolso de $%s por %s.",
            enmascararEmail(cliente.getEmail()), devolucion.getMontoReembolso(), devolucion.getCodigo()));
    }

    public void resenaResuelta(Resena resena) {
        LOG.info(() -> String.format(
            "[NOTIFICACION] Para %s: tu resena %s quedo %s.",
            enmascararEmail(resena.getUsuario().getEmail()), resena.getCodigo(), resena.getEstado()));
    }
}
