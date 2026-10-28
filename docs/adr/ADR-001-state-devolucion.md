# ADR-001 · State para el ciclo de vida de la devolución

**Estado:** Aceptada · 23/10/2026
**Patrón:** GoF de comportamiento

## Contexto
Una devolución pasa por PENDIENTE, APROBADA o RECHAZADA y, cuando se emite el reembolso, REEMBOLSADA. Antes, las reglas de qué se podía hacer en cada estado estaban sueltas en ServicioDeModeracion con ifs (`exigirPendiente`). Para la Entrega 4 se suma un estado nuevo, PENDIENTE_VALIDACION, cuando el SOAP del proveedor no contesta.

## Decisión
La interfaz `TransicionesDeDevolucion` define aprobar, rechazar y reembolsar, y por defecto las rechaza con `TransicionInvalidaException`. El enum `EstadoDevolucion` la implementa y cada estado habilita solo sus transiciones. `Devolucion` no pregunta en qué estado está: delega (`estado = estado.aprobar()`).

## Consecuencias
+ Las reglas de transición están en un solo lugar y se prueban sin servidor (EstadoDevolucionTest).
+ Un estado nuevo se agrega en el enum sin tocar los servicios.
+ Se persiste igual que antes (`@Enumerated(STRING)`), sin cambiar la base.
− Las transiciones quedan en un archivo con todos los estados: si crecen mucho, convendría pasar a una clase por estado.

## Descartadas
- Ifs por estado en cada servicio: es lo que había y repite la regla en varios lugares.
- Una clase por estado con un converter JPA: más clases y más configuración para cuatro estados.
