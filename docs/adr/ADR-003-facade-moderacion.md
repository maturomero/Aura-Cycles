# ADR-003 · Facade para moderar devoluciones

**Estado:** Aceptada · 17/09/2026
**Patrón:** GoF estructural

## Contexto
Aprobar una devolución tiene varios pasos: buscarla, calcular el monto, cambiarle el estado, guardarla y avisar al resto del sistema. La pantalla de moderación no tiene por qué conocer esos pasos.

## Decisión
ServicioDeModeracion es la fachada. La pantalla (ModeracionBean) llama a un solo método, `aprobarDevolucion(id)`, y la fachada coordina el resto. También concentra la transacción y el control de rol con `@RolesAllowed("MODERADOR")`.

## Consecuencias
+ La pantalla queda simple: un botón, un método.
+ Si cambia cómo se aprueba (por ejemplo cuando entre Mercado Pago), se toca la fachada y no la pantalla.
+ La seguridad y la transacción están en un solo lugar.
− Si se le cargan muchas cosas se vuelve una clase gigante. Por eso lo que pasa después de aprobar (stock, reembolso y aviso) lo hacen los MDB que escuchan el tópico.

## Descartadas
- Que el bean de la pantalla llame a cada servicio por separado: la lógica queda en la presentación, cada paso en una transacción distinta y el control de rol repetido.
