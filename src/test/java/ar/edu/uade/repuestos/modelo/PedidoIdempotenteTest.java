package ar.edu.uade.repuestos.modelo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PedidoIdempotenteTest {

    @Test
    void comparaPorLaHuellaDelContenido() {
        PedidoIdempotente pedido = new PedidoIdempotente("ana@mail.com", "pedido-0001", "abc", 12L, 202);
        assertTrue(pedido.esElMismoPedido("abc"));
        assertFalse(pedido.esElMismoPedido("abd"));
        assertEquals("ana@mail.com", pedido.getEmail());
        assertEquals("pedido-0001", pedido.getClave());
        assertEquals("abc", pedido.getHuella());
        assertEquals(12L, pedido.getDevolucionId());
        assertEquals(202, pedido.getCodigoHttp());
        assertNotNull(pedido.getCreado());
        assertNull(pedido.getId());
        assertNull(new PedidoIdempotente().getClave());
    }
}
