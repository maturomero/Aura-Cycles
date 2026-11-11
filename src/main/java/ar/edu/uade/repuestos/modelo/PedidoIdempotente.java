package ar.edu.uade.repuestos.modelo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.NamedQuery;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.LocalDateTime;

// Registro de cada Idempotency-Key que llego a la API, para no crear dos veces lo mismo si el cliente reintenta
@Entity
@Table(name = "pedido_idempotente")
@NamedQuery(name = "PedidoIdempotente.porClave",
            query = "SELECT p FROM PedidoIdempotente p WHERE p.email = :email AND p.clave = :clave")
public class PedidoIdempotente implements Serializable {
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String email;

    @Column(nullable = false, length = 80)
    private String clave;

    // SHA-256 del contenido del pedido
    @Column(nullable = false, length = 64)
    private String huella;

    @Column(name = "devolucion_id", nullable = false)
    private Long devolucionId;

    @Column(name = "codigo_http", nullable = false)
    private int codigoHttp;

    @Column(nullable = false)
    private LocalDateTime creado;

    public PedidoIdempotente() {
    }

    public PedidoIdempotente(String email, String clave, String huella, Long devolucionId, int codigoHttp) {
        this.email = email;
        this.clave = clave;
        this.huella = huella;
        this.devolucionId = devolucionId;
        this.codigoHttp = codigoHttp;
        this.creado = LocalDateTime.now();
    }

    public boolean esElMismoPedido(String otraHuella) {
        return huella.equals(otraHuella);
    }

    public Long getId() { return id; }

    public String getEmail() { return email; }

    public String getClave() { return clave; }

    public String getHuella() { return huella; }

    public Long getDevolucionId() { return devolucionId; }

    public int getCodigoHttp() { return codigoHttp; }

    public LocalDateTime getCreado() { return creado; }
}
