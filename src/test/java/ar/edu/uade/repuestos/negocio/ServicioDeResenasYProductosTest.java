package ar.edu.uade.repuestos.negocio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ar.edu.uade.repuestos.datos.ProductoRepository;
import ar.edu.uade.repuestos.datos.ResenaRepository;
import ar.edu.uade.repuestos.datos.UsuarioRepository;
import ar.edu.uade.repuestos.datos.VentaRepository;
import ar.edu.uade.repuestos.modelo.EstadoResena;
import ar.edu.uade.repuestos.modelo.Producto;
import ar.edu.uade.repuestos.modelo.Resena;
import ar.edu.uade.repuestos.util.ReglaNegocioException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

class ServicioDeResenasYProductosTest {

    @Nested
    @ExtendWith(MockitoExtension.class)
    class Resenas {
        @Mock ResenaRepository resenas;
        @Mock VentaRepository ventas;
        @Mock UsuarioRepository usuarios;
        @Mock ProductoRepository productos;
        @InjectMocks ServicioDeResenas servicio;

        @ParameterizedTest
        @ValueSource(ints = {1, 5})
        void puntajeEnElBordeSeAcepta(int puntaje) {
            when(ventas.usuarioComproProducto(1L, 1L)).thenReturn(true);
            when(usuarios.porId(1L)).thenReturn(Optional.of(Datos.ana()));
            when(productos.porId(1L)).thenReturn(Optional.of(Datos.cubierta()));
            when(resenas.guardar(any())).thenAnswer(i -> i.getArgument(0));

            Resena r = servicio.escribir(1L, 1L, puntaje, "Titulo", "Texto");

            assertEquals(puntaje, r.getPuntaje());
            assertEquals(EstadoResena.PENDIENTE, r.getEstado());
        }

        @ParameterizedTest
        @ValueSource(ints = {0, 6, -3})
        void puntajeFueraDeRango(int puntaje) {
            assertEquals("El puntaje va de 1 a 5 estrellas.",
                    assertThrows(ReglaNegocioException.class, () -> servicio.escribir(1L, 1L, puntaje, "t", "x")).getMessage());
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = "   ")
        void sinTexto(String texto) {
            assertThrows(ReglaNegocioException.class, () -> servicio.escribir(1L, 1L, 4, "t", texto));
        }

        @Test
        void soloSiLoCompro() {
            when(ventas.usuarioComproProducto(1L, 1L)).thenReturn(false);
            assertEquals("Solo podes resenar repuestos que compraste.",
                    assertThrows(ReglaNegocioException.class, () -> servicio.escribir(1L, 1L, 4, "t", "x")).getMessage());
            verify(resenas, never()).guardar(any());
        }

        @Test
        void usuarioOProductoQueNoExisten() {
            when(ventas.usuarioComproProducto(1L, 1L)).thenReturn(true);
            when(usuarios.porId(1L)).thenReturn(Optional.empty());
            assertThrows(ReglaNegocioException.class, () -> servicio.escribir(1L, 1L, 4, "t", "x"));
            when(usuarios.porId(1L)).thenReturn(Optional.of(Datos.ana()));
            when(productos.porId(1L)).thenReturn(Optional.empty());
            assertThrows(ReglaNegocioException.class, () -> servicio.escribir(1L, 1L, 4, "t", "x"));
        }

        @Test
        void consultas() {
            Resena r = new Resena(Datos.ana(), Datos.cubierta(), 5, "t", "x");
            when(resenas.aprobadasPorProducto(1L)).thenReturn(List.of(r));
            when(ventas.usuarioComproProducto(1L, 1L)).thenReturn(true);
            when(ventas.usuarioComproProducto(1L, 2L)).thenReturn(false);
            assertEquals(List.of(r), servicio.publicadasDe(1L));
            assertTrue(servicio.puedeResenar(1L, 1L));
            assertEquals(false, servicio.puedeResenar(1L, 2L));
        }
    }

    @Nested
    @ExtendWith(MockitoExtension.class)
    class Productos {
        @Mock ProductoRepository productos;
        @Mock ResenaRepository resenas;
        @InjectMocks ServicioDeProductos servicio;

        @Test
        void catalogoYBusqueda() {
            Producto p = Datos.cubierta();
            when(productos.todos()).thenReturn(List.of(p));
            when(productos.buscar("cub")).thenReturn(List.of(p));
            assertEquals(List.of(p), servicio.listar());
            assertEquals(List.of(p), servicio.buscar("cub"));
        }

        @Test
        void detalle() {
            Producto p = Datos.cubierta();
            when(productos.porId(1L)).thenReturn(Optional.of(p));
            when(productos.porId(9L)).thenReturn(Optional.empty());
            assertSame(p, servicio.porId(1L));
            assertEquals("El repuesto no existe.", assertThrows(ReglaNegocioException.class, () -> servicio.porId(9L)).getMessage());
        }

        @Test
        void puntajeYCantidadDeResenas() {
            when(resenas.promedioPuntaje(1L)).thenReturn(4.5);
            Resena r = new Resena(Datos.ana(), Datos.cubierta(), 5, "t", "x");
            when(resenas.aprobadasPorProducto(1L)).thenReturn(List.of(r, r));
            assertEquals(4.5, servicio.promedioPuntaje(1L));
            assertEquals(2, servicio.cantidadResenas(1L));
        }
    }
}
