package ar.edu.uade.repuestos.datos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ar.edu.uade.repuestos.modelo.Devolucion;
import ar.edu.uade.repuestos.modelo.EstadoDevolucion;
import ar.edu.uade.repuestos.modelo.EstadoVenta;
import ar.edu.uade.repuestos.modelo.MotivoDevolucion;
import ar.edu.uade.repuestos.modelo.Producto;
import ar.edu.uade.repuestos.modelo.Usuario;
import ar.edu.uade.repuestos.modelo.Venta;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.LockModeType;
import jakarta.persistence.Persistence;
import jakarta.persistence.PersistenceException;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.MountableFile;

// Repositorios contra un MySQL 8.4 real cargado con sql/schema.sql (los mismos datos de prueba que la app)
@Testcontainers(disabledWithoutDocker = true) // en Eclipse sin Docker se saltean; en el CI corren siempre
class RepositoriosIT {

    @Container
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4")
            .withDatabaseName("repuestos_bici")
            .withUsername("aura")
            .withPassword("aura-local")
            .withCopyFileToContainer(MountableFile.forHostPath("sql/schema.sql"), "/docker-entrypoint-initdb.d/schema.sql");

    static EntityManagerFactory emf;
    EntityManager em;
    VentaRepository ventas;
    ProductoRepository productos;
    DevolucionRepository devoluciones;
    ResenaRepository resenas;
    UsuarioRepository usuarios;

    @BeforeAll
    static void unidad() {
        emf = Persistence.createEntityManagerFactory("repuestosIT", Map.of(
                "jakarta.persistence.jdbc.url", MYSQL.getJdbcUrl(),
                "jakarta.persistence.jdbc.user", "aura",
                "jakarta.persistence.jdbc.password", "aura-local"));
    }

    @AfterAll
    static void cerrar() {
        emf.close();
    }

    @BeforeEach
    void abrir() throws Exception {
        em = emf.createEntityManager();
        ventas = conEm(new VentaRepository());
        productos = conEm(new ProductoRepository());
        devoluciones = conEm(new DevolucionRepository());
        resenas = conEm(new ResenaRepository());
        usuarios = conEm(new UsuarioRepository());
        em.getTransaction().begin();
    }

    // Cada test deja la base como estaba
    @AfterEach
    void deshacer() {
        if (em.getTransaction().isActive()) {
            em.getTransaction().rollback();
        }
        em.close();
    }

    @Test
    void elEsquemaYLosDatosDePruebaEstanCargados() {
        assertEquals(8, productos.todos().size());
        assertEquals(8, ventas.porUsuario(1L).size() + ventas.porUsuario(2L).size() + ventas.porUsuario(3L).size() + ventas.porUsuario(4L).size());
        assertTrue(usuarios.porEmail("mod@mail.com").orElseThrow().esModerador());
    }

    @Test
    void soloLasReservasViejasSinPagarSeVencen() {
        Usuario ana = usuarios.porId(1L).orElseThrow();
        Producto p = productos.porId(3L).orElseThrow();
        Venta vieja = nueva(ana, p, LocalDateTime.now().minusMinutes(40));
        Venta reciente = nueva(ana, p, LocalDateTime.now().minusMinutes(5));
        Venta viejaPagada = nueva(ana, p, LocalDateTime.now().minusMinutes(50));
        viejaPagada.marcarPagada("1000000099");
        em.flush();

        List<Venta> vencidas = ventas.pendientesDePagoAntesDe(LocalDateTime.now().minusMinutes(35));

        assertTrue(vencidas.contains(vieja));
        assertFalse(vencidas.contains(reciente));
        assertFalse(vencidas.contains(viejaPagada));
    }

    // Para reseñar hace falta una compra PAGADA: una pendiente de pago no alcanza
    @Test
    void comprarSinPagarNoHabilitaAResenar() {
        Usuario ana = usuarios.porId(1L).orElseThrow();
        Producto desviador = productos.porId(5L).orElseThrow();
        assertFalse(ventas.usuarioComproProducto(1L, 5L));
        nueva(ana, desviador, LocalDateTime.now());
        em.flush();
        assertFalse(ventas.usuarioComproProducto(1L, 5L));
        assertTrue(ventas.usuarioComproProducto(1L, 1L));
    }

    @Test
    void elBuscadorEsPorNombreOCategoriaYNoSeDejaInyectar() {
        assertEquals(3, productos.buscar("TRANSMISION").size());
        assertEquals(2, productos.buscar("cubierta").size() + productos.buscar("rodado").size());
        assertEquals(0, productos.buscar("' OR '1'='1").size());
        assertEquals(0, productos.buscar("x'; DROP TABLE producto; --").size());
        assertEquals(8, productos.buscar(" ").size());
        assertEquals(8, productos.todos().size());
    }

    // Regresion del defecto de stock viejo: find con lock no relee una entidad ya cargada, refresh si
    @Test
    void elBloqueoRelleeElStockAunqueElProductoYaEsteCargado() throws Exception {
        Producto cargado = em.find(Producto.class, 4L);
        int antes = cargado.getStock();
        try (Connection c = DriverManager.getConnection(MYSQL.getJdbcUrl(), "aura", "aura-local");
             Statement st = c.createStatement()) {
            st.executeUpdate("UPDATE producto SET stock = stock - 3 WHERE id = 4");
        }

        assertEquals(antes, em.find(Producto.class, 4L, LockModeType.PESSIMISTIC_WRITE).getStock(),
                "find con lock devuelve el stock viejo: por eso el repositorio usa refresh");
        assertEquals(antes - 3, productos.porIdBloqueado(4L).orElseThrow().getStock());

        em.getTransaction().rollback();
        try (Connection c = DriverManager.getConnection(MYSQL.getJdbcUrl(), "aura", "aura-local");
             Statement st = c.createStatement()) {
            st.executeUpdate("UPDATE producto SET stock = stock + 3 WHERE id = 4");
        }
    }

    @Test
    void laVentaBloqueadaTambienSeRellee() throws Exception {
        Venta v = em.find(Venta.class, 1L);
        assertEquals(EstadoVenta.PAGADA, v.getEstado());
        try (Connection c = DriverManager.getConnection(MYSQL.getJdbcUrl(), "aura", "aura-local");
             Statement st = c.createStatement()) {
            st.executeUpdate("UPDATE venta SET mp_preferencia_id = 'pref-x' WHERE id = 1");
        }
        assertEquals("pref-x", ventas.porIdBloqueado(1L).orElseThrow().getMpPreferenciaId());
        em.getTransaction().rollback();
        try (Connection c = DriverManager.getConnection(MYSQL.getJdbcUrl(), "aura", "aura-local");
             Statement st = c.createStatement()) {
            st.executeUpdate("UPDATE venta SET mp_preferencia_id = NULL WHERE id = 1");
        }
    }

    // La base no deja asociar el mismo pago de Mercado Pago a dos compras
    @Test
    void unPagoDeMercadoPagoNoSePuedeUsarEnDosCompras() {
        Usuario ana = usuarios.porId(1L).orElseThrow();
        Producto p = productos.porId(1L).orElseThrow();
        nueva(ana, p, LocalDateTime.now()).marcarPagada("1000000777");
        nueva(ana, p, LocalDateTime.now()).marcarPagada("1000000777");
        assertThrows(PersistenceException.class, () -> em.flush());
    }

    @Test
    void devolucionesPorEstadoUsuarioYVenta() {
        assertTrue(devoluciones.existeParaVenta(7L));
        assertFalse(devoluciones.existeParaVenta(1L));
        List<Devolucion> pendientes = devoluciones.porEstado(EstadoDevolucion.PENDIENTE);
        assertEquals(1, pendientes.size());
        assertEquals("RMA-2026-00001", pendientes.get(0).getRma());
        assertEquals(1, devoluciones.porUsuario(4L).size());
        assertEquals(0, devoluciones.porUsuario(1L).size());

        Devolucion nueva = new Devolucion(ventas.porId(1L).orElseThrow(), MotivoDevolucion.NO_COMPATIBLE, "");
        devoluciones.guardar(nueva);
        assertTrue(nueva.getId() != null);
        assertEquals(EstadoDevolucion.PENDIENTE_VALIDACION,
                devoluciones.porEstado(EstadoDevolucion.PENDIENTE_VALIDACION).get(0).getEstado());
        assertTrue(devoluciones.porIdBloqueado(nueva.getId()).isPresent());
    }

    @Test
    void elPromedioCuentaSoloResenasAprobadas() {
        assertEquals(5.0, resenas.promedioPuntaje(1L));
        assertEquals(1, resenas.aprobadasPorProducto(1L).size());
        assertEquals(0.0, resenas.promedioPuntaje(3L));
        assertEquals(0, resenas.aprobadasPorProducto(3L).size());
        assertEquals(1, resenas.cantidadAprobadas(1L));
        assertEquals(0, resenas.cantidadAprobadas(3L));
        // el resumen del catalogo da lo mismo que las consultas de a un producto
        var resumen = resenas.resumenAprobadas();
        assertEquals(5.0, resumen.get(1L).promedio());
        assertEquals(1, resumen.get(1L).cantidad());
        assertFalse(resumen.containsKey(3L));
    }

    @Test
    void usuarios() {
        assertTrue(usuarios.existeEmail("ana@mail.com"));
        assertFalse(usuarios.existeEmail("nadie@mail.com"));
        assertTrue(usuarios.porEmail("ana@mail.com").orElseThrow().getPasswordHash().startsWith("$2a$12$"));
        Usuario nuevo = usuarios.guardar(new Usuario("nuevo@mail.com", "$2a$12$x", "Nuevo", ar.edu.uade.repuestos.modelo.Rol.CLIENTE));
        assertTrue(nuevo.getId() != null);
    }

    private Venta nueva(Usuario u, Producto p, LocalDateTime fecha) {
        Venta v = new Venta(u, p, 1, p.getPrecio());
        v.setFecha(fecha);
        return ventas.guardar(v);
    }

    private <T> T conEm(T repo) throws Exception {
        Field f = repo.getClass().getDeclaredField("em");
        f.setAccessible(true);
        f.set(repo, em);
        return repo;
    }
}
