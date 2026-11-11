# Manual de usuario

Versión 1 · 11/11/2026 · Aura Cycles, repuestos de bicicleta

La tienda se usa desde el navegador, en la computadora o en el celular. Hay dos tipos de usuario: el **cliente**, que compra, reseña y pide devoluciones, y el **moderador**, que revisa reseñas y resuelve devoluciones.

## Para empezar

### Crear una cuenta
1. Entrá a la tienda. Aparece la pantalla de ingreso.
2. Tocá **Crear una cuenta**.
3. Completá nombre, email y una contraseña de 8 caracteres o más.
4. Tocá **Crear cuenta**. Quedás adentro, en el catálogo.

### Ingresar y salir
Ingresá con tu email y tu contraseña. Para salir, **Cerrar sesión**, arriba a la derecha.

Si te equivocás de contraseña 5 veces seguidas, el ingreso se bloquea 5 minutos, aunque después pongas la correcta.

### Tema oscuro
Arriba a la derecha, **Tema oscuro**. Con **Tema claro** volvés.

## Cliente

### Buscar un repuesto
En **Productos** está el catálogo. Escribí parte del nombre o la categoría (cubiertas, frenos, transmisión) y tocá **Buscar**. Tocando el nombre de un repuesto ves la descripción, el stock y las reseñas.

### Comprar y pagar
1. Tocá **Comprar** en el repuesto.
2. Te lleva a Mercado Pago. Pagá ahí.
3. Al volver a la tienda ves "Pago acreditado" y la orden queda **Pagada**.

Si el pago se rechaza, la orden queda **Pago rechazado** y no se te cobra nada.

Si Mercado Pago no responde, la compra queda **reservada 30 minutos**. La pagás desde **Mis compras** con el botón **Pagar**. Si pasan los 30 minutos sin pagar, la reserva se vence sola.

### Ver mis compras
**Mis compras** muestra cada orden con su número (ORD-000012), la fecha, el importe y el estado.

### Escribir una reseña
1. Abrí un repuesto que hayas comprado y pagado.
2. Tocá **Escribir reseña**.
3. Elegí el puntaje de 1 a 5, poné un título si querés y escribí tu opinión.
4. Enviala. No aparece enseguida: primero la revisa un moderador.

### Pedir una devolución
Tenés 30 días desde la compra, y la orden tiene que estar pagada.

1. En **Mis compras**, tocá **Solicitar devolución** en la orden.
2. Elegí el motivo. Si el repuesto llegó defectuoso, contá qué le pasa.
3. Tocá **Enviar solicitud**.

Qué puede pasar:

| Lo que ves | Qué significa |
|---|---|
| "Pendiente de revisión" con un código RMA | El proveedor aceptó la garantía. Falta que la apruebe un moderador |
| "Validando la garantía con el proveedor" | El proveedor no contestó. Se vuelve a consultar sola cada 2 minutos; no tenés que hacer nada |
| "Es un repuesto de desgaste" | Pastillas y cámaras no tienen garantía del fabricante. No se crea la solicitud |

Cuando el moderador la aprueba, el reembolso sale por Mercado Pago al mismo medio con el que pagaste. Si el motivo fue arrepentimiento se retiene el 10 %.

## Moderador

El moderador ve además **Panel** en el menú. No es cliente: puede mirar el catálogo, pero no tiene el botón **Comprar** ni **Mis compras**.

### Revisar reseñas
En **Reseñas pendientes** está la cola. En cada una:

- **Aprobar** la publica en la página del repuesto.
- **Rechazar** la descarta.
- **Pedir sugerencia a la IA** muestra qué haría un asistente (aprobar, revisar o rechazar), por qué y con cuánta confianza. Es solo una sugerencia: la decisión la tomás vos con los botones de siempre. Si dice "IA sin respuesta", el asistente no contestó y se revisa como cualquier otra.

### Resolver devoluciones
En **Devoluciones pendientes**:

- **Aprobar**: el stock se repone, el reembolso sale por Mercado Pago y al cliente le llega el aviso. Todo eso pasa solo, en segundo plano, y la devolución termina **Reembolsada**.
- **Rechazar**: la solicitud se cierra y al cliente le llega el aviso.

Una devolución marcada **Sin validar con el proveedor** es una que el proveedor nunca contestó: conviene consultarla con él antes de aprobar.

Si al aprobar aparece "la mensajería no responde", no se cambió nada. Probá de nuevo en un rato.

## Si algo sale mal

| Mensaje | Qué hacer |
|---|---|
| "Demasiados intentos fallidos" | Esperar 5 minutos |
| "No pudimos abrir la solicitud" | El plazo de 30 días venció, la compra no está pagada o ya tiene una devolución |
| "Mercado Pago no responde" | La compra quedó reservada: pagala desde Mis compras dentro de los 30 minutos |
| "No pudimos mostrar esa página" | Volver al inicio. Si se repite, avisar al administrador |

## Para integrar otro sistema

Además de las pantallas hay una API REST para devoluciones y reseñas. El contrato está en [api/openapi.yaml](api/openapi.yaml) y un recorrido de ejemplo en `demo/api.sh`.
