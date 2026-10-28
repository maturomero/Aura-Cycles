package ar.edu.uade.repuestos.mensajeria;

import ar.edu.uade.repuestos.datos.DevolucionRepository;
import ar.edu.uade.repuestos.negocio.ServicioDeModeracion;
import ar.edu.uade.repuestos.negocio.ServicioDeReembolsos;
import jakarta.ejb.ActivationConfigProperty;
import jakarta.ejb.MessageDriven;
import jakarta.inject.Inject;
import jakarta.jms.Message;
import jakarta.jms.MessageListener;

// Reembolsa por Mercado Pago las aprobadas. Si MP no responde hay rollback y el broker reentrega el mensaje
@MessageDriven(activationConfig = {
    @ActivationConfigProperty(propertyName = "destinationLookup", propertyValue = ServicioDeModeracion.TOPICO_DEVOLUCIONES),
    @ActivationConfigProperty(propertyName = "destinationType", propertyValue = "jakarta.jms.Topic"),
    @ActivationConfigProperty(propertyName = "subscriptionDurability", propertyValue = "Durable"),
    @ActivationConfigProperty(propertyName = "clientId", propertyValue = "aura-reembolsos"),
    @ActivationConfigProperty(propertyName = "subscriptionName", propertyValue = "reembolsos"),
    @ActivationConfigProperty(propertyName = "messageSelector", propertyValue = "estado = 'APROBADA'")
})
public class ReembolsosMDB implements MessageListener {

    @Inject
    private DevolucionRepository devolucionRepository;

    @Inject
    private ServicioDeReembolsos servicioDeReembolsos;

    @Override
    public void onMessage(Message mensaje) {
        EventoDevolucion.devolucionDe(mensaje, devolucionRepository)
                .ifPresent(servicioDeReembolsos::emitir);
    }
}
