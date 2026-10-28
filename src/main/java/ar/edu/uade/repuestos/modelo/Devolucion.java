package ar.edu.uade.repuestos.modelo;

import jakarta.persistence.*;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(name = "devolucion")
@NamedQueries({
    @NamedQuery(name = "Devolucion.porEstado",
                query = "SELECT d FROM Devolucion d WHERE d.estado = :estado "
                      + "ORDER BY d.fechaSolicitud ASC"),
    @NamedQuery(name = "Devolucion.porUsuario",
                query = "SELECT d FROM Devolucion d WHERE d.venta.usuario.id = :usuarioId "
                      + "ORDER BY d.fechaSolicitud DESC"),
    @NamedQuery(name = "Devolucion.porVenta",
                query = "SELECT COUNT(d) FROM Devolucion d WHERE d.venta.id = :ventaId")
})
public class Devolucion implements Serializable {
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.EAGER)
    @JoinColumn(name = "venta_id", nullable = false)
    private Venta venta;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private MotivoDevolucion motivo;

    @Column(columnDefinition = "TEXT")
    private String comentario;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoDevolucion estado;

    // El reembolso no tiene tabla propia: vive en la devolucion que lo origina.
    @Column(name = "monto_reembolso", precision = 12, scale = 2)
    private BigDecimal montoReembolso;

    @Column(name = "mp_reembolso_id", length = 30)
    private String mpReembolsoId;

    // Codigo de autorizacion del proveedor. Null si se derivo al moderador sin poder validar
    @Column(length = 40)
    private String rma;

    @Column(name = "intentos_validacion", nullable = false)
    private int intentosValidacion;

    // Consumidor idempotente: si el broker reentrega el evento, el stock no se repone dos veces
    @Column(name = "stock_repuesto", nullable = false)
    private boolean stockRepuesto;

    @Column(name = "fecha_solicitud", nullable = false)
    private LocalDateTime fechaSolicitud;

    @Column(name = "fecha_resolucion")
    private LocalDateTime fechaResolucion;

    public Devolucion() {
    }

    public Devolucion(Venta venta, MotivoDevolucion motivo, String comentario) {
        this.venta = venta;
        this.motivo = motivo;
        this.comentario = comentario;
        this.estado = EstadoDevolucion.PENDIENTE_VALIDACION;
        this.fechaSolicitud = LocalDateTime.now();
    }

    public void garantiaValidada(String rma) {
        this.estado = estado.validar();
        this.rma = rma;
    }

    public void garantiaRechazada() {
        this.estado = estado.rechazar();
        this.fechaResolucion = LocalDateTime.now();
    }

    // Devuelve true si ya se agotaron los intentos y paso al moderador sin RMA
    public boolean validacionFallida(int maximo) {
        this.intentosValidacion++;
        if (intentosValidacion >= maximo) {
            this.estado = estado.validar();
            return true;
        }
        return false;
    }

    public boolean isSinValidar() {
        return rma == null && estado != EstadoDevolucion.PENDIENTE_VALIDACION;
    }

    // La entidad no pregunta en que estado esta: delega en el estado (State)
    public void aprobar(BigDecimal monto) {
        this.estado = estado.aprobar();
        this.montoReembolso = monto;
        this.fechaResolucion = LocalDateTime.now();
    }

    public void rechazar() {
        this.estado = estado.rechazar();
        this.montoReembolso = null;
        this.fechaResolucion = LocalDateTime.now();
    }

    public void reembolsar() {
        this.estado = estado.reembolsar();
    }

    @Transient
    public String getCodigo() {
        return id == null ? "-" : String.format("DEV-%06d", id);
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Venta getVenta() { return venta; }
    public void setVenta(Venta venta) { this.venta = venta; }

    public MotivoDevolucion getMotivo() { return motivo; }
    public void setMotivo(MotivoDevolucion motivo) { this.motivo = motivo; }

    public String getComentario() { return comentario; }
    public void setComentario(String comentario) { this.comentario = comentario; }

    public EstadoDevolucion getEstado() { return estado; }
    public void setEstado(EstadoDevolucion estado) { this.estado = estado; }

    public BigDecimal getMontoReembolso() { return montoReembolso; }
    public void setMontoReembolso(BigDecimal montoReembolso) { this.montoReembolso = montoReembolso; }

    public String getMpReembolsoId() { return mpReembolsoId; }
    public void setMpReembolsoId(String mpReembolsoId) { this.mpReembolsoId = mpReembolsoId; }

    // Devuelve false si ya se habia repuesto (mensaje repetido)
    public boolean marcarStockRepuesto() {
        if (stockRepuesto) {
            return false;
        }
        stockRepuesto = true;
        return true;
    }

    public boolean isStockRepuesto() { return stockRepuesto; }
    public void setStockRepuesto(boolean stockRepuesto) { this.stockRepuesto = stockRepuesto; }

    public String getRma() { return rma; }
    public void setRma(String rma) { this.rma = rma; }

    public int getIntentosValidacion() { return intentosValidacion; }
    public void setIntentosValidacion(int intentosValidacion) { this.intentosValidacion = intentosValidacion; }

    public LocalDateTime getFechaSolicitud() { return fechaSolicitud; }
    public void setFechaSolicitud(LocalDateTime fechaSolicitud) { this.fechaSolicitud = fechaSolicitud; }

    public LocalDateTime getFechaResolucion() { return fechaResolucion; }
    public void setFechaResolucion(LocalDateTime fechaResolucion) { this.fechaResolucion = fechaResolucion; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Devolucion)) return false;
        Devolucion otra = (Devolucion) o;
        return id != null && id.equals(otra.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
