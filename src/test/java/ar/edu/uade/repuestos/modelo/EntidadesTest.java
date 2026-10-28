package ar.edu.uade.repuestos.modelo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

class EntidadesTest {

    // Igualdad por id: dos instancias con el mismo id son la misma fila; sin id no son iguales a nada salvo a si mismas
    static Stream<Arguments> entidades() {
        return Stream.of(
            Arguments.of("Usuario", (Supplier<Object>) Usuario::new, (Function<Object, Object>) o -> { ((Usuario) o).setId(1L); return o; }),
            Arguments.of("Producto", (Supplier<Object>) Producto::new, (Function<Object, Object>) o -> { ((Producto) o).setId(1L); return o; }),
            Arguments.of("Venta", (Supplier<Object>) Venta::new, (Function<Object, Object>) o -> { ((Venta) o).setId(1L); return o; }),
            Arguments.of("Resena", (Supplier<Object>) Resena::new, (Function<Object, Object>) o -> { ((Resena) o).setId(1L); return o; }),
            Arguments.of("Devolucion", (Supplier<Object>) Devolucion::new, (Function<Object, Object>) o -> { ((Devolucion) o).setId(1L); return o; }));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("entidades")
    void igualdadPorId(String nombre, Supplier<Object> nueva, Function<Object, Object> conId1) {
        Object a = conId1.apply(nueva.get());
        Object b = conId1.apply(nueva.get());
        Object sinId = nueva.get();
        Object otroSinId = nueva.get();

        assertEquals(a, a);
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertNotEquals(a, sinId);
        assertNotEquals(sinId, a);
        assertNotEquals(sinId, otroSinId);
        assertEquals(sinId, sinId);
        assertNotEquals(a, "otra cosa");
        assertNotEquals(a, null);
        Set<Object> set = new HashSet<>();
        set.add(a);
        set.add(b);
        assertEquals(1, set.size());
    }

    @ParameterizedTest
    @CsvSource({"5, 5, true", "5, 4, true", "5, 6, false", "0, 1, false", "0, 0, true"})
    void hayStock(int stock, int pedido, boolean hay) {
        Producto p = new Producto();
        p.setStock(stock);
        assertEquals(hay, p.hayStock(pedido));
    }

    @Test
    void descontarYReponerStock() {
        Producto p = new Producto();
        p.setStock(10);
        p.descontarStock(3);
        assertEquals(7, p.getStock());
        p.reponerStock(2);
        assertEquals(9, p.getStock());
    }

    @Test
    void elProductoGuardaSusDatos() {
        Producto p = new Producto();
        p.setCodigo("CUB-29-210");
        p.setNombre("Cubierta");
        p.setCategoria("Cubiertas");
        p.setDescripcion("Rodado 29");
        p.setPrecio(new BigDecimal("46800.00"));
        assertEquals("CUB-29-210", p.getCodigo());
        assertEquals("Cubierta", p.getNombre());
        assertEquals("Cubiertas", p.getCategoria());
        assertEquals("Rodado 29", p.getDescripcion());
        assertEquals(new BigDecimal("46800.00"), p.getPrecio());
    }

    @Test
    void soloElModeradorEsModerador() {
        assertTrue(new Usuario("m@mail.com", "h", "Mod", Rol.MODERADOR).esModerador());
        assertFalse(new Usuario("c@mail.com", "h", "Cli", Rol.CLIENTE).esModerador());
        Usuario u = new Usuario();
        assertFalse(u.esModerador());
        u.setRol(Rol.MODERADOR);
        u.setNombre("Marina");
        u.setPasswordHash("$2a$12$x");
        assertTrue(u.esModerador());
        assertEquals("Marina", u.getNombre());
        assertEquals("$2a$12$x", u.getPasswordHash());
    }

    @Test
    void unaResenaNacePendienteYElModeradorLaResuelve() {
        Usuario ana = new Usuario("ana@mail.com", "h", "Ana", Rol.CLIENTE);
        Producto p = new Producto();
        Resena r = new Resena(ana, p, 4, "Buen agarre", "Anda bien en mojado");
        assertEquals(EstadoResena.PENDIENTE, r.getEstado());
        assertEquals("-", r.getCodigo());
        assertTrue(r.getFecha().isAfter(LocalDateTime.now().minusMinutes(1)));
        assertEquals(ana, r.getUsuario());
        assertEquals(p, r.getProducto());
        assertEquals("Buen agarre", r.getTitulo());
        assertEquals("Anda bien en mojado", r.getTexto());

        r.rechazar("Lenguaje ofensivo");
        assertEquals(EstadoResena.RECHAZADA, r.getEstado());
        assertEquals("Lenguaje ofensivo", r.getMotivoRechazo());

        r.aprobar();
        assertEquals(EstadoResena.APROBADA, r.getEstado());
        assertNull(r.getMotivoRechazo());

        r.setId(42L);
        assertEquals("RES-000042", r.getCodigo());
    }

    @Test
    void codigosDeOrdenYDevolucion() {
        Venta v = new Venta();
        assertEquals("-", v.getNumeroOrden());
        v.setId(9L);
        assertEquals("ORD-000009", v.getNumeroOrden());
        Devolucion d = new Devolucion();
        assertEquals("-", d.getCodigo());
        d.setId(123L);
        assertEquals("DEV-000123", d.getCodigo());
    }

    @Test
    void laDevolucionGuardaLoQueElClienteCargo() {
        Venta v = new Venta(new Usuario(), new Producto(), 1, BigDecimal.TEN);
        Devolucion d = new Devolucion(v, MotivoDevolucion.DEFECTUOSO, "Se pincho");
        assertEquals(v, d.getVenta());
        assertEquals(MotivoDevolucion.DEFECTUOSO, d.getMotivo());
        assertEquals("Se pincho", d.getComentario());
        assertEquals("Llego defectuoso", d.getMotivo().getEtiqueta());
        assertTrue(d.getFechaSolicitud().isAfter(LocalDateTime.now().minusMinutes(1)));
        assertNull(d.getFechaResolucion());
    }

    @Test
    void stockRepuestoSoloUnaVez() {
        Devolucion d = new Devolucion();
        assertTrue(d.marcarStockRepuesto());
        assertFalse(d.marcarStockRepuesto());
        assertTrue(d.isStockRepuesto());
    }

    @Test
    void laVentaRecuerdaLaPreferenciaYElPago() {
        Venta v = new Venta(new Usuario(), new Producto(), 2, new BigDecimal("93600.00"));
        v.setMpPreferenciaId("pref-1");
        assertEquals("pref-1", v.getMpPreferenciaId());
        assertEquals(2, v.getCantidad());
        assertEquals(new BigDecimal("93600.00"), v.getImporte());
        assertTrue(v.getFecha().isAfter(LocalDateTime.now().minusMinutes(1)));
        assertEquals("Pendiente de pago", v.getEstado().getDescripcion());
        v.marcarRechazada();
        assertEquals("Pago rechazado", v.getEstado().getDescripcion());
    }
}
