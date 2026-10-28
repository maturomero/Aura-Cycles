package ar.edu.uade.repuestos.negocio;

import ar.edu.uade.repuestos.modelo.Devolucion;
import ar.edu.uade.repuestos.modelo.EstadoVenta;
import ar.edu.uade.repuestos.modelo.MotivoDevolucion;
import ar.edu.uade.repuestos.modelo.Producto;
import ar.edu.uade.repuestos.modelo.Rol;
import ar.edu.uade.repuestos.modelo.Usuario;
import ar.edu.uade.repuestos.modelo.Venta;
import java.math.BigDecimal;
import java.time.LocalDateTime;

// Datos de prueba armados a mano, iguales a los de schema.sql
final class Datos {

    private Datos() {
    }

    static Usuario ana() {
        Usuario u = new Usuario("ana@mail.com", "x", "Ana Torres", Rol.CLIENTE);
        u.setId(1L);
        return u;
    }

    static Usuario carlos() {
        Usuario u = new Usuario("carlos@mail.com", "x", "Carlos Duarte", Rol.CLIENTE);
        u.setId(2L);
        return u;
    }

    static Producto cubierta() {
        Producto p = new Producto();
        p.setId(1L);
        p.setCodigo("CUB-29-210");
        p.setNombre("Cubierta MTB 29 x 2.10");
        p.setPrecio(new BigDecimal("46800.00"));
        p.setStock(14);
        return p;
    }

    static Venta venta(Long id, Usuario u, int cantidad, EstadoVenta estado) {
        Producto p = cubierta();
        Venta v = new Venta(u, p, cantidad, p.getPrecio().multiply(BigDecimal.valueOf(cantidad)));
        v.setId(id);
        v.setEstado(estado);
        return v;
    }

    static Venta pagadaHace(int dias) {
        Venta v = venta(9L, ana(), 1, EstadoVenta.PAGADA);
        v.setFecha(LocalDateTime.now().minusDays(dias));
        v.setMpPagoId("1000000001");
        return v;
    }

    static Devolucion devolucion(Long id, Venta v, MotivoDevolucion motivo) {
        Devolucion d = new Devolucion(v, motivo, "comentario");
        d.setId(id);
        return d;
    }
}
