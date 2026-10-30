package ar.edu.uade.repuestos.negocio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ar.edu.uade.repuestos.modelo.Devolucion;
import ar.edu.uade.repuestos.modelo.EstadoVenta;
import ar.edu.uade.repuestos.modelo.MotivoDevolucion;
import ar.edu.uade.repuestos.modelo.Resena;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

// SEC-09: los avisos dicen a quien va, pero el email sale enmascarado
class ServicioDeNotificacionesTest {

    private final List<String> lineas = new ArrayList<>();
    private final Handler captura = new Handler() {
        public void publish(LogRecord r) { lineas.add(r.getMessage()); }
        public void flush() { }
        public void close() { }
    };
    private final Logger log = Logger.getLogger(ServicioDeNotificaciones.class.getName());
    private final ServicioDeNotificaciones servicio = new ServicioDeNotificaciones();

    @BeforeEach
    void escuchar() {
        log.addHandler(captura);
    }

    @AfterEach
    void dejar() {
        log.removeHandler(captura);
    }

    @Test
    void ningunAvisoLlevaElEmailEntero() {
        Devolucion d = Datos.devolucion(2L, Datos.venta(9L, Datos.ana(), 1, EstadoVenta.PAGADA), MotivoDevolucion.DEFECTUOSO);
        d.setMontoReembolso(new BigDecimal("46800.00"));
        Resena r = new Resena(Datos.ana(), Datos.cubierta(), 5, "t", "x");
        r.setId(3L);

        servicio.devolucionRecibida(d);
        servicio.devolucionResuelta(d);
        servicio.reembolsoEmitido(d);
        servicio.resenaResuelta(r);

        assertEquals(4, lineas.size());
        for (String l : lineas) {
            assertTrue(l.contains("a***@mail.com"), l);
            assertFalse(l.contains("ana@mail.com"), l);
        }
        assertTrue(lineas.get(0).contains("DEV-000002"));
        assertTrue(lineas.get(2).contains("46800.00"));
        assertTrue(lineas.get(3).contains("RES-000003"));
    }
}
