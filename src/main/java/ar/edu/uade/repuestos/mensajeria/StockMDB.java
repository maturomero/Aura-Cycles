package ar.edu.uade.repuestos.mensajeria;

import ar.edu.uade.repuestos.datos.DevolucionRepository;
import ar.edu.uade.repuestos.negocio.ServicioDeModeracion;
import ar.edu.uade.repuestos.negocio.ServicioDeVentas;
import jakarta.ejb.ActivationConfigProperty;
import jakarta.ejb.MessageDriven;
import jakarta.inject.Inject;
import jakarta.jms.Message;
import jakarta.jms.MessageListener;

// Repone el stock. El selector hace que solo le lleguen las aprobadas
@MessageDriven(activationConfig = {
    @ActivationConfigProperty(propertyName = "destinationLookup", propertyValue = ServicioDeModeracion.TOPICO_DEVOLUCIONES),
    @ActivationConfigProperty(propertyName = "destinationType", propertyValue = "jakarta.jms.Topic"),
    @ActivationConfigProperty(propertyName = "subscriptionDurability", propertyValue = "Durable"),
    @ActivationConfigProperty(propertyName = "clientId", propertyValue = "aura-stock"),
    @ActivationConfigProperty(propertyName = "subscriptionName", propertyValue = "stock"),
    @ActivationConfigProperty(propertyName = "messageSelector", propertyValue = "estado = 'APROBADA'")
})
public class StockMDB implements MessageListener {

    @Inject
    private DevolucionRepository devolucionRepository;

    @Inject
    private ServicioDeVentas servicioDeVentas;

    @Override
    public void onMessage(Message mensaje) {
        EventoDevolucion.devolucionDe(mensaje, devolucionRepository)
                .ifPresent(d -> servicioDeVentas.reponerStockDeDevolucion(d.getId()));
    }
}
