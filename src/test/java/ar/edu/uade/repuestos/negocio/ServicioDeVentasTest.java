package ar.edu.uade.repuestos.negocio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ar.edu.uade.repuestos.datos.DevolucionRepository;
import ar.edu.uade.repuestos.datos.ProductoRepository;
import ar.edu.uade.repuestos.datos.UsuarioRepository;
import ar.edu.uade.repuestos.datos.VentaRepository;
import ar.edu.uade.repuestos.modelo.Devolucion;
import ar.edu.uade.repuestos.modelo.EstadoVenta;
import ar.edu.uade.repuestos.modelo.MotivoDevolucion;
import ar.edu.uade.repuestos.modelo.Producto;
import ar.edu.uade.repuestos.modelo.Venta;
import ar.edu.uade.repuestos.util.ReglaNegocioException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ServicioDeVentasTest {

    @Mock VentaRepository ventas;
    @Mock ProductoRepository productos;
    @Mock UsuarioRepository usuarios;
    @Mock DevolucionRepository devoluciones;
    @InjectMocks ServicioDeVentas servicio;

    private Producto cubierta;

    @BeforeEach
    void preparar() {
        cubierta = Datos.cubierta();
    }

    @Test
    void comprarReservaElStockYDejaLaVentaEsperandoElPago() {
        when(usuarios.porId(1L)).thenReturn(Optional.of(Datos.ana()));
        when(productos.porIdBloqueado(1L)).thenReturn(Optional.of(cubierta));
        when(ventas.guardar(any())).thenAnswer(i -> i.getArgument(0));

        Venta v = servicio.comprar(1L, 1L, 3);

        assertEquals(11, cubierta.getStock());
        assertEquals(0, new BigDecimal("140400.00").compareTo(v.getImporte()));
        assertEquals(EstadoVenta.PENDIENTE_PAGO, v.getEstado());
        verify(productos).guardar(cubierta);
    }

    // Borde: se puede comprar exactamente lo que queda
    @Test
    void sePuedeComprarTodoElStock() {
        when(usuarios.porId(1L)).thenReturn(Optional.of(Datos.ana()));
        when(productos.porIdBloqueado(1L)).thenReturn(Optional.of(cubierta));
        when(ventas.guardar(any())).thenAnswer(i -> i.getArgument(0));
        servicio.comprar(1L, 1L, 14);
        assertEquals(0, cubierta.getStock());
    }

    @Test
    void unaMasQueElStockNoSePuede() {
        when(usuarios.porId(1L)).thenReturn(Optional.of(Datos.ana()));
        when(productos.porIdBloqueado(1L)).thenReturn(Optional.of(cubierta));
        var e = assertThrows(ReglaNegocioException.class, () -> servicio.comprar(1L, 1L, 15));
        assertTrue(e.getMessage().contains("Quedan 14"), e.getMessage());
        assertEquals(14, cubierta.getStock());
        verify(ventas, never()).guardar(any());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void laCantidadTieneQueSerPositiva(int cantidad) {
        assertEquals("La cantidad tiene que ser mayor a cero.",
                assertThrows(ReglaNegocioException.class, () -> servicio.comprar(1L, 1L, cantidad)).getMessage());
        verify(productos, never()).porIdBloqueado(any());
    }

    @Test
    void usuarioOProductoInexistente() {
        when(usuarios.porId(1L)).thenReturn(Optional.empty());
        assertEquals("El usuario no existe.",
                assertThrows(ReglaNegocioException.class, () -> servicio.comprar(1L, 1L, 1)).getMessage());

        when(usuarios.porId(1L)).thenReturn(Optional.of(Datos.ana()));
        when(productos.porIdBloqueado(1L)).thenReturn(Optional.empty());
        assertEquals("El repuesto no existe.",
                assertThrows(ReglaNegocioException.class, () -> servicio.comprar(1L, 1L, 1)).getMessage());
    }

    @Test
    void consultas() {
        Venta v = Datos.pagadaHace(1);
        when(ventas.porUsuario(1L)).thenReturn(List.of(v));
        when(ventas.porId(9L)).thenReturn(Optional.of(v));
        when(ventas.porId(99L)).thenReturn(Optional.empty());
        assertEquals(List.of(v), servicio.comprasDe(1L));
        assertSame(v, servicio.porId(9L));
        assertEquals("La compra no existe.", assertThrows(ReglaNegocioException.class, () -> servicio.porId(99L)).getMessage());
    }

    @Test
    void reponerStockDevuelveLasUnidadesDeLaVenta() {
        Venta v = Datos.venta(9L, Datos.ana(), 2, EstadoVenta.PAGADA);
        when(productos.porIdBloqueado(1L)).thenReturn(Optional.of(cubierta));
        servicio.reponerStockDe(v);
        assertEquals(16, cubierta.getStock());
        verify(productos).guardar(cubierta);
    }

    // Consumidor idempotente: el mismo evento dos veces repone una sola vez
    @Test
    void elMismoEventoDosVecesReponeUnaSola() {
        Devolucion d = Datos.devolucion(5L, Datos.venta(9L, Datos.ana(), 2, EstadoVenta.PAGADA), MotivoDevolucion.DEFECTUOSO);
        when(devoluciones.porIdBloqueado(5L)).thenReturn(Optional.of(d));
        when(productos.porIdBloqueado(1L)).thenReturn(Optional.of(cubierta));

        assertTrue(servicio.reponerStockDeDevolucion(5L));
        assertFalse(servicio.reponerStockDeDevolucion(5L));

        assertEquals(16, cubierta.getStock());
        verify(productos, times(1)).guardar(cubierta);
        assertTrue(d.isStockRepuesto());
    }

    @Test
    void unEventoDeUnaDevolucionQueNoExisteSeIgnora() {
        when(devoluciones.porIdBloqueado(77L)).thenReturn(Optional.empty());
        assertFalse(servicio.reponerStockDeDevolucion(77L));
        verify(productos, never()).porIdBloqueado(any());
    }
}
