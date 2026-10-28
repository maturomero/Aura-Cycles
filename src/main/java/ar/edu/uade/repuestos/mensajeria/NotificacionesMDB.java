package ar.edu.uade.repuestos.mensajeria;

import ar.edu.uade.repuestos.datos.DevolucionRepository;
import ar.edu.uade.repuestos.negocio.ServicioDeNotificaciones;
import ar.edu.uade.repuestos.negocio.ServicioDeModeracion;
import jakarta.ejb.ActivationConfigProperty;
import jakarta.ejb.MessageDriven;
import jakarta.inject.Inject;
import jakarta.jms.Message;
import jakarta.jms.MessageListener;

// Le avisa al cliente cuando su devolucion se aprueba o se rechaza
@MessageDriven(activationConfig = {
    @ActivationConfigProperty(propertyName = "destinationLookup", propertyValue = ServicioDeModeracion.TOPICO_DEVOLUCIONES),
    @ActivationConfigProperty(propertyName = "destinationType", propertyValue = "jakarta.jms.Topic"),
    @ActivationConfigProperty(propertyName = "subscriptionDurability", propertyValue = "Durable"),
    @ActivationConfigProperty(propertyName = "clientId", propertyValue = "aura-notificaciones"),
    @ActivationConfigProperty(propertyName = "subscriptionName", propertyValue = "notificaciones")
})
public class NotificacionesMDB implements MessageListener {

    @Inject
    private DevolucionRepository devolucionRepository;

    @Inject
    private ServicioDeNotificaciones servicioDeNotificaciones;

    @Override
    public void onMessage(Message mensaje) {
        EventoDevolucion.devolucionDe(mensaje, devolucionRepository)
                .ifPresent(servicioDeNotificaciones::devolucionResuelta);
    }
}
