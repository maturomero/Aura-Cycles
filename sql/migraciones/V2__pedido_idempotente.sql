-- V2: tabla para la Idempotency-Key de POST /api/v1/devoluciones (2.a entrega)
-- Migracion versionada: la aplica Flyway (servicio flyway del docker-compose o mvn flyway:migrate).
-- No editar una migracion ya aplicada: cualquier cambio va en una V nueva.

-- ---------- PEDIDO IDEMPOTENTE ----------
-- Una fila por cada Idempotency-Key que llego a POST /api/v1/devoluciones.
-- Si el cliente reintenta con la misma clave, se le devuelve la misma devolucion.
CREATE TABLE pedido_idempotente (
  id            BIGINT AUTO_INCREMENT PRIMARY KEY,
  email         VARCHAR(120) NOT NULL,
  clave         VARCHAR(80)  NOT NULL,
  huella        CHAR(64)     NOT NULL,
  devolucion_id BIGINT       NOT NULL,
  codigo_http   INT          NOT NULL,
  creado        DATETIME     NOT NULL,
  CONSTRAINT uq_pedido_clave UNIQUE (email, clave),
  CONSTRAINT fk_pedido_devolucion FOREIGN KEY (devolucion_id) REFERENCES devolucion(id)
) ENGINE=InnoDB;
