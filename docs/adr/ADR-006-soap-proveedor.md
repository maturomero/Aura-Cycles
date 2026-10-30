# ADR-006 · Validación de garantía por SOAP con el proveedor mayorista

**Estado:** Aceptada · 28/10/2026 · reintento por cola desde el 30/10/2026
**Integración:** SOAP sincrónico con sistema legado y cola punto a punto para los reintentos (AUR-32, AUR-49, SEC-08)

## Contexto
Antes de aceptar una devolución hay que validar la garantía con el sistema legado del proveedor, que expone SOAP 1.1 con WSDL (`src/main/resources/wsdl/ProveedorMayorista.wsdl`). Devuelve un código RMA o el Fault `GarantiaNoValidaFault`. El proveedor puede tardar, caerse o devolver basura, y el cliente está esperando en la pantalla.

## Decisión
- Cliente SOAP chico (`ClienteProveedor`) con `java.net.http` y DOM, en lugar de generar el cliente con wsimport: usamos una sola operación y así controlamos el timeout y el parser.
- Timeout de 5 s. La llamada corre fuera de la transacción (`NOT_SUPPORTED`).
- Fault de negocio (`garantiaNoValida`): no se crea la devolución y el cliente ve un mensaje nuestro según el código (CONSUMIBLE, FUERA_DE_PLAZO). El texto del Fault nunca llega a la pantalla.
- Fault técnico, timeout, conexión caída o respuesta inválida: la devolución se guarda en `PENDIENTE_VALIDACION` y, en la misma transacción, su id va a la cola punto a punto `cola.validaciones` con 2 minutos de espera. `ValidacionesMDB` es el único consumidor: reintenta y, si el proveedor sigue sin contestar, la vuelve a encolar. A los 5 intentos pasa al moderador sin RMA y marcada "Sin validar con el proveedor".
- Si además el broker está caído, no se crea la devolución y el cliente ve "la mensajería no responde": nada queda a medias.
- Parser sin DTD ni entidades externas (`XmlSeguro`): un XXE en la respuesta se rechaza.
- Los valores que mandamos se escapan, y un test valida el pedido y las respuestas contra el esquema del WSDL.

## Consecuencias
+ La pantalla nunca espera más de 5 s y nada queda colgado.
+ Con el proveedor caído se pueden seguir pidiendo devoluciones; se validan solas cuando vuelve.
+ Cada pendiente es un mensaje que procesa un solo consumidor una sola vez, y la espera la pone el mensaje: no hay un timer revisando la base.
+ El contrato está probado en cada build.
− Si el WSDL del proveedor cambia hay que tocar el cliente a mano.
− En la base hay devoluciones que esperan validación; el moderador no las ve hasta que se validan o se agotan los intentos.

## Descartadas
- wsimport/JAX-WS: muchas clases generadas para una operación, y el timeout depende de propiedades de cada implementación.
- Timer cada 2 minutos que revisa la base (lo usamos hasta el 30/10): anda, pero consulta la base aunque no haya nada pendiente y el TP pide una cola punto a punto; esta es la que corresponde.
- Reintentos del broker con rollback: perderíamos el contador de intentos en la base y habría que configurar la espera por dirección en cada broker.
- Reintentar dentro del mismo pedido: el cliente esperaría 10 o 15 s.
