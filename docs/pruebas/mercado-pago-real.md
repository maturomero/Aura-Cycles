# Prueba contra Mercado Pago real

Fecha: 11/11/2026. Jira: `AUR-233`.

Todo lo automático corre contra el simulador. Esta corrida es para ver que lo que mandamos y lo que leemos coincide con el Mercado Pago de verdad. Se hizo en el ambiente de prueba: no hay plata real.

## Con qué

- La aplicación de Checkout Pro del grupo, con sus credenciales de prueba (vendedor de prueba `3835565026`).
- Una cuenta compradora de prueba (`3835565028`) y la Mastercard de prueba con titular `APRO`.
- El mismo `ClienteMercadoPago` de la app apuntando a `https://api.mercadopago.com`, llamado desde un programa chico. La tienda completa no estaba levantada en esta corrida.

## Qué dio

| Paso | Llamada | Resultado |
|---|---|---|
| Crear la preferencia | `POST /checkout/preferences` | 201. Guardó la referencia, las tres URLs de vuelta, `auto_return`, `binary_mode` y el vencimiento a 30 minutos tal cual los mandamos. Devolvió `init_point` |
| Pagar | Checkout de Mercado Pago, sesión de la compradora de prueba | Dos pagos acreditados: `189020138158` ($1500) y `189020289484` ($900) |
| Volver a la tienda | Redirección a `AURA_URL/pago.xhtml?venta=...` | Llegó con `payment_id` y `status=approved` en la URL, que es lo que lee `pago.xhtml` |
| Buscar por referencia | `GET /v1/payments/search?external_reference=` | Un pago `approved` por cada referencia, con el monto y la moneda bien |
| Consultar un pago | `GET /v1/payments/{id}` | `approved`, referencia y monto coinciden. Con un id que no existe, vacío |
| Reembolsar | `POST /v1/payments/{id}/refunds` | **401** `Unauthorized use of live credentials` (código 7), con monto y sin monto |

## Lo que queda sin probar contra el real

- **El reembolso.** Mercado Pago lo rechaza con las credenciales de prueba de esta aplicación, así que sigue probado solo contra el simulador. En la app ese rechazo termina igual que cualquier falla de Mercado Pago: se deshace la transacción, el broker reintenta y el mensaje termina en la DLQ.
- **El pago rechazado.** Los dos pagos se hicieron con el titular `APRO`.
- **La vuelta con la tienda levantada.** La redirección se vio en el navegador, pero no había tienda escuchando en esa dirección. Falta correrlo con `docker compose` y el `.env`.

## Qué cambió en el código por esta corrida

- El log ahora muestra el motivo que da Mercado Pago cuando rechaza una llamada (`[MP] reembolsar: HTTP 401 (Unauthorized use of live credentials)`). Antes decía solo el número.
