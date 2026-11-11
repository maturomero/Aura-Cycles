# ADR-008 · API REST propia con JWT

**Estado:** Aceptada · 11/11/2026
**Reemplaza:** la decisión de no exponer una API propia (las pantallas siguen igual)

## Contexto
Las pantallas son JSF y llaman directo a los EJB, por eso hasta la Entrega 1 no teníamos API REST. El diseño del TPO dejó escrita la API de devoluciones y reseñas (la de la Clase 10) y la 2.ª entrega pide verla andando: autenticación con JWT, control por rol, 409 cuando el State rechaza una transición y, para el checkpoint, Idempotency-Key, ETag y errores en Problem Details. Además un cliente que no es un navegador (una app, otro sistema) no puede usar la sesión con cookie de las pantallas.

## Decisión
Se expone `/api/v1` con JAX-RS (paquete `api/`), como una segunda entrada a la misma capa de negocio. Los recursos no tienen reglas: validan el formato del pedido y delegan.

- **JWT:** `POST /auth/login` devuelve un token HS256 que vale 15 minutos, con el email, el id y el rol. Lo firma y lo valida `TokensJwt` con la librería Nimbus. La clave sale de `JWT_SECRET`; si no está, la app genera una al arrancar.
- **Rol:** el filtro `SeguridadDeLaApi` corre antes de cada recurso. Sin token válido responde 401 y, si el rol del token no está en el `@RolesAllowed` del método, 403.
- **Dueño de los datos:** el email sale siempre del token, nunca del cuerpo. Lo ajeno responde igual que lo que no existe (404).
- **409:** `TransicionInvalidaException` (la que tira el State) se traduce a 409 en `ErroresDeLaApi`. Todos los errores salen como `application/problem+json`.
- **Idempotency-Key:** obligatoria en `POST /devoluciones`. La clave y la devolución se guardan en la misma transacción (`pedido_idempotente`); el reintento devuelve la misma devolución y la misma clave con otro contenido es un 409.
- **ETag:** el GET de una devolución lo devuelve y el PATCH exige `If-Match` (428 si falta, 412 si quedó viejo).
- **Moderación:** `ServicioDeModeracion` le exige el rol MODERADOR al contenedor y en la API no hay sesión del contenedor. El PATCH pasa por `PuenteDeModeracion`, un EJB con `@RunAs("MODERADOR")` al que solo se llega después de que el filtro validó el rol del token.

## Consecuencias
+ La 2.ª entrega y el checkpoint se muestran con curl, sin tocar las pantallas ni las reglas de negocio.
+ La creación por API reusa el mismo borrador stateful de la pantalla (`ServicioDeSolicitudes` lo usa en una sola llamada).
+ El login de la API comparte el limitador de intentos con el de las pantallas: 5 fallos, 429.
− Hay dos formas de autenticarse (sesión en las pantallas, JWT en la API) y hay que probar las dos.
− `PuenteDeModeracion` corre con un rol fijo: si alguien lo llamara sin pasar por el filtro, saltearía el control. Por eso es un EJB aparte, chico, y lo cubre un test E2E (un cliente recibe 403).
− Un JWT no se puede revocar antes de que venza. Por eso dura 15 minutos.

## Descartadas
- JWT validado por el contenedor (Elytron con `BEARER_TOKEN` o MicroProfile JWT): es lo más prolijo, pero el WAR ya usa login por formulario y combinar los dos mecanismos obliga a tocar la configuración de WildFly en docker y en cada Eclipse.
- Pasar las pantallas a JWT: es rehacer lo que ya funciona y está probado.
- Escribir la firma del JWT a mano con `javax.crypto`: es poco código, pero validar tokens es justo donde un descuido se paga caro.
