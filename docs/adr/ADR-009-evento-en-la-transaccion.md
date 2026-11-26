# ADR-009 · El evento de la devolución sale en la misma transacción que la base

**Estado:** Aceptada · 26/11/2026
**Integración:** tópico `topico.devoluciones` y cola `cola.validaciones` (ADR-006), publicados desde `ServicioDeModeracion` y `ValidadorDeGarantias`

## Contexto
Al aprobar o rechazar una devolución se guarda el nuevo estado y se publica un evento para stock, reembolso y aviso. Si se guarda y el evento no sale, el cliente queda aprobado sin reembolso. Si el evento sale y la base vuelve atrás, se repone stock y se reembolsa algo que no se aprobó. Lo mismo pasa al dejar una validación pendiente en la cola.

## Decisión
- La base y el broker participan de la misma transacción JTA: la fábrica `java:/RemoteJmsXA` es XA, y el `JMSContext` inyectado en el EJB se suma a la transacción del contenedor.
- El mensaje se envía dentro del método de negocio y Artemis lo entrega recién cuando la transacción confirma. Si algo falla después del envío, se deshacen las dos cosas.
- Si el broker no responde al publicar, se lanza `MensajeriaNoDisponibleException`, la transacción vuelve atrás y el moderador ve "la mensajería no responde". No queda nada a medias (DEF-02, `e2e/test_3_resiliencia.py`).
- No usamos tabla outbox: con XA el problema que resuelve el outbox ya está cubierto, y el TP no necesita publicar a un broker que no soporte transacciones distribuidas.

## Consecuencias
+ Rollback completo ante cualquier falla a mitad de la operación, sin código de compensación.
+ Los consumidores nunca reciben un evento de algo que no quedó guardado.
− Con el broker caído no se puede aprobar ni rechazar: es la contracara de no dejar nada inconsistente. El cliente sí puede seguir comprando.
− XA cuesta un poco más que una transacción local, pero el volumen de aprobaciones es bajo.

## Descartadas
- Tabla outbox con un proceso que la lea y publique: suma una tabla, un timer y la idempotencia del lado del que publica, para resolver lo mismo que ya hace XA.
- Publicar después del commit: si el broker falla en ese momento, el evento se pierde y la devolución queda aprobada sin reembolso.
