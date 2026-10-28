# ADR-005 · Cobro con Mercado Pago Checkout Pro

**Estado:** Aceptada · 28/10/2026
**Integración:** REST sincrónica con sistema externo

## Contexto
El cobro de la compra lo resuelve Mercado Pago (acordado con el profe en lugar de una página propia que valide tarjetas). Mercado Pago es un sistema externo: puede tardar, caerse o devolver basura, y el cliente puede volver a la tienda con una URL manipulada o no volver nunca. Además el grupo comparte una sola cuenta de prueba.

## Decisión
- Checkout Pro con redirección. Al comprar se reserva el stock y la venta queda `PENDIENTE_PAGO`; se crea una preferencia (POST) y el cliente paga en Mercado Pago.
- El estado del pago siempre se le pregunta a Mercado Pago (GET del pago o búsqueda por `external_reference`). De la URL de vuelta solo se usa `payment_id`, y solo si es un número. Se verifica que el pago sea de esa compra y por el monto exacto en ARS.
- `external_reference` es un UUID por venta, no el id: con la cuenta compartida, el id 9 de una base choca con el 9 de otra.
- Sin webhook: necesita una URL pública y no suma para el TP. Lo que el webhook resolvería (que el cliente pague y no vuelva) lo cubren el botón Pagar de Mis compras, que primero busca pagos existentes, y un timer cada 5 minutos.
- Timeout de 5 s en todas las llamadas. Si Mercado Pago falla, la compra queda reservada 30 minutos (la preferencia vence a la misma hora) y el cliente ve un aviso, no un error.
- El reembolso sale del MDB del tópico de devoluciones con `X-Idempotency-Key`. Si Mercado Pago no responde, rollback y el broker reentrega (10 s entre intentos, 10 intentos, después DLQ).

## Consecuencias
+ Ningún dato de tarjeta pasa por nuestro servidor.
+ Recargar la vuelta, volver con el pago de otra compra o tocar `status=approved` no cambia nada (bloqueo pesimista sobre la venta al confirmar).
+ Con Mercado Pago caído la tienda sigue andando y nada queda inconsistente.
− El stock queda reservado hasta 35 minutos si el cliente abandona.
− Mercado Pago no acepta `localhost` en las URLs de vuelta con `auto_return`: en la demo con sandbox hay que usar la IP de la máquina (`AURA_URL`) o el cliente vuelve con el botón "Volver al sitio".

## Descartadas
- Página propia que valida la tarjeta: solo puede chequear formato (Luhn), no si la tarjeta tiene fondos, y obliga a recibir datos de tarjeta.
- Checkout API / Bricks: más código y nos acerca a manejar datos de tarjeta.
- Creerle al `status` de la URL de vuelta: cualquiera lo cambia en la barra del navegador.
