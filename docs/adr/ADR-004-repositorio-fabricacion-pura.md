# ADR-004 · Patrón Repository/DAO (principio GRASP de Fabricación pura)

**Estado:** Aceptada · 17/09/2026
**Patrón:** Repository/DAO (capa de datos), aplicando el principio GRASP de Fabricación pura

## Contexto
Guardar y buscar devoluciones en la base no es parte del negocio de una devolución. Si las consultas iban en la entidad o en los servicios, mezclábamos reglas de negocio con SQL y JPA.

## Decisión
Siguiendo el principio de Fabricación pura, una clase que no existe en el negocio, creada solo para esto: `DevolucionRepository` (patrón Repository/DAO), con el EntityManager adentro. Los servicios le piden "buscar por id", "buscar por estado" o "guardar", sin saber cómo se hace. Lo mismo para cada entidad.

## Consecuencias
+ Si cambia una consulta o la base, se toca un solo lugar.
+ Los servicios y la entidad quedan con su responsabilidad y nada más.
− Una clase más por entidad.

## Descartadas
- Usar el EntityManager directo en los servicios: mezcla reglas con consultas y obliga a repetirlas.
