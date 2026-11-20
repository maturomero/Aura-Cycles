-- V1: esquema de la 1.a entrega (usuario, producto, venta, resena, devolucion)
-- Migracion versionada: la aplica Flyway (servicio flyway del docker-compose o mvn flyway:migrate).
-- No editar una migracion ya aplicada: cualquier cambio va en una V nueva.

-- ---------- USUARIO ----------
CREATE TABLE usuario (
  id            BIGINT AUTO_INCREMENT PRIMARY KEY,
  email         VARCHAR(120) NOT NULL UNIQUE,
  password_hash VARCHAR(255) NOT NULL,
  nombre        VARCHAR(120) NOT NULL,
  rol           VARCHAR(20)  NOT NULL
) ENGINE=InnoDB;

-- ---------- PRODUCTO ----------
CREATE TABLE producto (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  codigo      VARCHAR(40)  NOT NULL UNIQUE,
  nombre      VARCHAR(160) NOT NULL,
  categoria   VARCHAR(60),
  descripcion VARCHAR(500),
  precio      DECIMAL(12,2) NOT NULL,
  stock       INT NOT NULL DEFAULT 0,
  INDEX idx_producto_categoria (categoria)
) ENGINE=InnoDB;

-- ---------- VENTA ----------
CREATE TABLE venta (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  usuario_id  BIGINT NOT NULL,
  producto_id BIGINT NOT NULL,
  cantidad    INT NOT NULL,
  importe     DECIMAL(12,2) NOT NULL,
  fecha       DATETIME NOT NULL,
  -- PENDIENTE_PAGO reserva stock hasta que Mercado Pago aprueba, rechaza o vence la reserva
  estado            VARCHAR(20)  NOT NULL DEFAULT 'PAGADA',
  referencia_pago   VARCHAR(36)  NOT NULL,
  mp_preferencia_id VARCHAR(100),
  mp_pago_id        VARCHAR(30),
  CONSTRAINT uq_venta_referencia UNIQUE (referencia_pago),
  CONSTRAINT uq_venta_mp_pago    UNIQUE (mp_pago_id),
  CONSTRAINT fk_venta_usuario  FOREIGN KEY (usuario_id)  REFERENCES usuario(id),
  CONSTRAINT fk_venta_producto FOREIGN KEY (producto_id) REFERENCES producto(id),
  INDEX idx_venta_usuario (usuario_id),
  INDEX idx_venta_estado (estado, fecha)
) ENGINE=InnoDB;

-- ---------- RESENA ----------
CREATE TABLE resena (
  id             BIGINT AUTO_INCREMENT PRIMARY KEY,
  usuario_id     BIGINT NOT NULL,
  producto_id    BIGINT NOT NULL,
  puntaje        INT NOT NULL,
  titulo         VARCHAR(160),
  texto          TEXT,
  estado         VARCHAR(20) NOT NULL,
  fecha          DATETIME NOT NULL,
  motivo_rechazo VARCHAR(200),
  CONSTRAINT fk_resena_usuario  FOREIGN KEY (usuario_id)  REFERENCES usuario(id),
  CONSTRAINT fk_resena_producto FOREIGN KEY (producto_id) REFERENCES producto(id),
  CONSTRAINT ck_resena_puntaje  CHECK (puntaje BETWEEN 1 AND 5),
  INDEX idx_resena_estado (estado),
  INDEX idx_resena_producto (producto_id)
) ENGINE=InnoDB;

-- ---------- DEVOLUCION ----------
-- El monto del reembolso vive aca y no en una tabla propia: un reembolso no
-- existe fuera de la devolucion que lo origina. Por eso ServicioDeReembolsos
-- no tiene repositorio propio en el diagrama de capas.
CREATE TABLE devolucion (
  id              BIGINT AUTO_INCREMENT PRIMARY KEY,
  venta_id        BIGINT NOT NULL,
  motivo          VARCHAR(40) NOT NULL,
  comentario      TEXT,
  estado          VARCHAR(20) NOT NULL,
  monto_reembolso DECIMAL(12,2),
  mp_reembolso_id VARCHAR(30),
  rma             VARCHAR(40),
  intentos_validacion INT NOT NULL DEFAULT 0,
  stock_repuesto  BOOLEAN NOT NULL DEFAULT FALSE,
  fecha_solicitud DATETIME NOT NULL,
  fecha_resolucion DATETIME,
  CONSTRAINT fk_devolucion_venta FOREIGN KEY (venta_id) REFERENCES venta(id),
  CONSTRAINT uq_devolucion_venta UNIQUE (venta_id),
  INDEX idx_devolucion_estado (estado)
) ENGINE=InnoDB;
