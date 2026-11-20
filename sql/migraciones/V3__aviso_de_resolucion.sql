-- V3: marca de aviso enviado, para que NotificacionesMDB avise una sola vez (3er checkpoint, DEF-22)
-- Migracion versionada: la aplica Flyway (servicio flyway del docker-compose o mvn flyway:migrate).
-- No editar una migracion ya aplicada: cualquier cambio va en una V nueva.

ALTER TABLE devolucion
  ADD COLUMN resolucion_avisada BOOLEAN NOT NULL DEFAULT FALSE AFTER stock_repuesto;
