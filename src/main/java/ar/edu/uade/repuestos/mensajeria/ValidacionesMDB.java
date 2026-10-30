package ar.edu.uade.repuestos.mensajeria;

import ar.edu.uade.repuestos.negocio.ValidadorDeGarantias;
import jakarta.ejb.ActivationConfigProperty;
import jakarta.ejb.MessageDriven;
import jakarta.inject.Inject;
import jakarta.jms.Message;
import jakarta.jms.MessageListener;

// Unico consumidor de cola.validaciones: cada devolucion que el proveedor no contesto se reintenta una sola vez por mensaje
@MessageDriven(activationConfig = {
    @ActivationConfigProperty(propertyName = "destinationLookup", propertyValue = ValidadorDeGarantias.COLA_VALIDACIONES),
    @ActivationConfigProperty(propertyName = "destinationType", propertyValue = "jakarta.jms.Queue")
})
public class ValidacionesMDB implements MessageListener {

    @Inject
    private ValidadorDeGarantias validador;

    @Override
    public void onMessage(Message mensaje) {
        validador.reintentar(EventoDevolucion.idDe(mensaje));
    }
}
