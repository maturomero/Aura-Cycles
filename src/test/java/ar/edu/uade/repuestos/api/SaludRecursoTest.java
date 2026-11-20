package ar.edu.uade.repuestos.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ar.edu.uade.repuestos.negocio.ServicioDeSalud;
import jakarta.annotation.security.PermitAll;
import jakarta.json.JsonObject;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

// GET /api/v1/salud responde 200 o 503 segun la base y el broker, sin pedir token
class SaludRecursoTest {

    private Response pedir(boolean base, boolean broker) {
        ServicioDeSalud servicio = mock(ServicioDeSalud.class);
        when(servicio.revisar()).thenReturn(new ServicioDeSalud.Estado(base, broker));
        return new SaludRecurso(servicio).salud();
    }

    @Test
    void conTodoArribaDa200YUp() {
        Response r = pedir(true, true);
        JsonObject cuerpo = (JsonObject) r.getEntity();
        assertEquals(200, r.getStatus());
        assertEquals("UP", cuerpo.getString("estado"));
        assertEquals("UP", cuerpo.getString("base"));
        assertEquals("UP", cuerpo.getString("broker"));
        assertEquals("no-store", r.getHeaderString("Cache-Control"));
    }

    @Test
    void conElBrokerCaidoDa503YDiceCualFalla() {
        Response r = pedir(true, false);
        JsonObject cuerpo = (JsonObject) r.getEntity();
        assertEquals(503, r.getStatus());
        assertEquals("DOWN", cuerpo.getString("estado"));
        assertEquals("UP", cuerpo.getString("base"));
        assertEquals("DOWN", cuerpo.getString("broker"));
    }

    @Test
    void conLaBaseCaidaDa503() {
        Response r = pedir(false, true);
        assertEquals(503, r.getStatus());
        assertEquals("DOWN", ((JsonObject) r.getEntity()).getString("base"));
    }

    // El filtro SeguridadDeLaApi deja pasar sin token solo lo que tiene @PermitAll
    @Test
    void noPideToken() throws Exception {
        assertNotNull(SaludRecurso.class.getMethod("salud").getAnnotation(PermitAll.class));
    }
}
