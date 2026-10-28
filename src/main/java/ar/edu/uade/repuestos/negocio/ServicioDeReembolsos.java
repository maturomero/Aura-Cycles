package ar.edu.uade.repuestos.negocio;

import ar.edu.uade.repuestos.integracion.ClienteMercadoPago;
import ar.edu.uade.repuestos.integracion.ReembolsoMP;
import ar.edu.uade.repuestos.modelo.Devolucion;
import ar.edu.uade.repuestos.modelo.EstadoDevolucion;
import ar.edu.uade.repuestos.modelo.MotivoDevolucion;
import ar.edu.uade.repuestos.negocio.reembolso.PoliticaDeReembolso;
import ar.edu.uade.repuestos.util.ReglaNegocioException;
import jakarta.ejb.Stateless;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import java.math.BigDecimal;
import java.util.List;
import java.util.logging.Logger;

// Strategy: la regla de cuanto se devuelve vive en las politicas, no aca.
// emitir() devuelve la plata por Mercado Pago sobre el pago original de la compra.
@Stateless
public class ServicioDeReembolsos {

    private static final Logger LOG = Logger.getLogger(ServicioDeReembolsos.class.getName());

    @Inject
    private Instance<PoliticaDeReembolso> politicas;

    @Inject
    private ServicioDeNotificaciones servicioDeNotificaciones;

    @Inject
    private ClienteMercadoPago mercadoPago;

    public BigDecimal calcularMonto(Devolucion devolucion) {
        return politicaPara(devolucion.getMotivo())
                .calcular(devolucion.getVenta().getImporte());
    }

    // Si MP no responde sale MercadoPagoNoDisponibleException: rollback y el broker reentrega el mensaje
    public void emitir(Devolucion devolucion) {
        if (devolucion.getEstado() == EstadoDevolucion.REEMBOLSADA) {
            LOG.info(() -> "[REEMBOLSO] " + devolucion.getCodigo() + " ya estaba reembolsada, mensaje repetido");
            return;
        }
        if (devolucion.getMontoReembolso() == null) {
            throw new ReglaNegocioException("La devolucion no tiene monto calculado.");
        }

        String pagoId = devolucion.getVenta().getMpPagoId();
        if (pagoId == null) {
            // Compras de los datos de prueba, anteriores a Mercado Pago
            LOG.info(() -> "[REEMBOLSO] " + devolucion.getCodigo() + " no tiene pago en Mercado Pago, se reembolsa a mano");
        } else {
            String clave = "aura-" + devolucion.getVenta().getReferenciaPago() + "-" + devolucion.getCodigo();
            ReembolsoMP reembolso = mercadoPago.reembolsar(pagoId, devolucion.getMontoReembolso(), clave);
            devolucion.setMpReembolsoId(reembolso.id());
            LOG.info(() -> "[REEMBOLSO] " + devolucion.getCodigo() + " reembolso " + reembolso.id()
                    + " en Mercado Pago (" + reembolso.estado() + ")");
        }
        devolucion.reembolsar();
        servicioDeNotificaciones.reembolsoEmitido(devolucion);
    }

    private PoliticaDeReembolso politicaPara(MotivoDevolucion motivo) {
        List<PoliticaDeReembolso> aplicables = politicas.stream()
                .filter(p -> p.aplicaA(motivo))
                .toList();
        if (aplicables.size() != 1) {
            throw new IllegalStateException(
                "Se esperaba una politica de reembolso para " + motivo + " y hay " + aplicables.size() + ".");
        }
        return aplicables.get(0);
    }
}
