package ar.edu.uade.repuestos.modelo;

import jakarta.persistence.*;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "venta")
@NamedQueries({
    @NamedQuery(name = "Venta.porUsuario",
                query = "SELECT v FROM Venta v WHERE v.usuario.id = :usuarioId "
                      + "ORDER BY v.fecha DESC"),
    @NamedQuery(name = "Venta.comproProducto",
                query = "SELECT COUNT(v) FROM Venta v "
                      + "WHERE v.usuario.id = :usuarioId AND v.producto.id = :productoId "
                      + "AND v.estado = :pagada"),
    @NamedQuery(name = "Venta.pendientesAntesDe",
                query = "SELECT v FROM Venta v WHERE v.estado = :pendiente AND v.fecha < :limite "
                      + "ORDER BY v.fecha")
})
public class Venta implements Serializable {
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.EAGER)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @ManyToOne(optional = false, fetch = FetchType.EAGER)
    @JoinColumn(name = "producto_id", nullable = false)
    private Producto producto;

    @Column(nullable = false)
    private int cantidad;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal importe;

    @Column(nullable = false)
    private LocalDateTime fecha;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoVenta estado;

    // Es el external_reference en Mercado Pago. UUID y no el id, porque el grupo comparte la cuenta de prueba
    @Column(name = "referencia_pago", nullable = false, unique = true, length = 36)
    private String referenciaPago;

    @Column(name = "mp_preferencia_id", length = 100)
    private String mpPreferenciaId;

    @Column(name = "mp_pago_id", unique = true, length = 30)
    private String mpPagoId;

    public Venta() {
    }

    // Nace reservando stock y esperando el pago
    public Venta(Usuario usuario, Producto producto, int cantidad, BigDecimal importe) {
        this.usuario = usuario;
        this.producto = producto;
        this.cantidad = cantidad;
        this.importe = importe;
        this.fecha = LocalDateTime.now();
        this.estado = EstadoVenta.PENDIENTE_PAGO;
        this.referenciaPago = UUID.randomUUID().toString();
    }

    public void marcarPagada(String pagoId) {
        this.estado = estado.pagar();
        this.mpPagoId = pagoId;
    }

    public void marcarRechazada() {
        this.estado = estado.rechazar();
    }

    public void vencer() {
        this.estado = estado.vencer();
    }

    // email es el usuario que conoce el contenedor (getCallerPrincipal)
    public boolean esDe(String email) {
        return email != null && usuario != null && usuario.getEmail() != null
                && email.trim().toLowerCase(Locale.ROOT).equals(usuario.getEmail().toLowerCase(Locale.ROOT));
    }

    public boolean isPendienteDePago() {
        return estado == EstadoVenta.PENDIENTE_PAGO;
    }

    @Transient
    public String getNumeroOrden() {
        return id == null ? "-" : String.format("ORD-%06d", id);
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Usuario getUsuario() { return usuario; }
    public void setUsuario(Usuario usuario) { this.usuario = usuario; }

    public Producto getProducto() { return producto; }
    public void setProducto(Producto producto) { this.producto = producto; }

    public int getCantidad() { return cantidad; }
    public void setCantidad(int cantidad) { this.cantidad = cantidad; }

    public BigDecimal getImporte() { return importe; }
    public void setImporte(BigDecimal importe) { this.importe = importe; }

    public LocalDateTime getFecha() { return fecha; }
    public void setFecha(LocalDateTime fecha) { this.fecha = fecha; }

    public EstadoVenta getEstado() { return estado; }
    public void setEstado(EstadoVenta estado) { this.estado = estado; }

    public String getReferenciaPago() { return referenciaPago; }
    public void setReferenciaPago(String referenciaPago) { this.referenciaPago = referenciaPago; }

    public String getMpPreferenciaId() { return mpPreferenciaId; }
    public void setMpPreferenciaId(String mpPreferenciaId) { this.mpPreferenciaId = mpPreferenciaId; }

    public String getMpPagoId() { return mpPagoId; }
    public void setMpPagoId(String mpPagoId) { this.mpPagoId = mpPagoId; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Venta)) return false;
        Venta otra = (Venta) o;
        return id != null && id.equals(otra.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
