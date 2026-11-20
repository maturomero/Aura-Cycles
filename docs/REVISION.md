# Revisión de tests por integrante

Cada uno revisa los tests de su parte, los corre y sube **su propio commit** antes del PR a `main`. En la defensa te pueden pedir que expliques cualquiera de estos tests, así que la idea es que los leas, los rompas a propósito una vez (cambiá una línea del código y fijate que el test falla) y los vuelvas a dejar bien.

Para el commit de revisión alcanza con algo chico pero real: un caso de borde más en alguno de tus tests, un nombre más claro o un comentario de una línea donde algo no se entendía. En el mensaje poné qué revisaste, por ejemplo `Reviso tests de SOAP y agrego caso de RMA vacío`.

Las tareas de Jira para revisar son las subtareas "Revisar casos de prueba ..." (AUR-92 a AUR-109). Cada caso de prueba está en Jira como subtarea de su HU (AUR-125 a AUR-195), asignado a quien lo revisa.

## Valen

| Qué | Dónde |
| --- | --- |
| SEC-03: pantallas por rol y headers | `e2e/test_4_seguridad.py` (403, headers, login del contenedor) |
| SEC-06: errores sin detalles | `e2e/test_4_seguridad.py` (404 propio, vista vencida) y `ManejadorDeVistaVencida` |
| Sesión de las pantallas y CORS | `e2e/test_4_seguridad.py` (cookie inventada, adulterada o cerrada; sesión nueva al ingresar; sin CORS) |
| JWT de la API | `TokensJwtTest`, `SeguridadDeLaApiTest`, `AuthRecursoTest` |
| HU y manual de usuario | documentos de HU y manual (AUR-71 y AUR-57) |
| Plan y matriz del doc de QA | secciones 1 y 2 |

Te pueden preguntar: por qué el filtro de headers corre también en FORWARD y ERROR, qué pasaba antes con un ViewState vencido, qué lleva adentro el JWT, por qué se rechaza un token con `alg: none` y por qué las pantallas siguen con sesión (ADR-008).

## Lautaro

| Qué | Dónde |
| --- | --- |
| Repositorios contra MySQL real | `src/test/java/.../datos/RepositoriosIT.java` |
| Mensajería contra Artemis real | `src/test/java/.../mensajeria/MensajeriaIT.java` |
| State de la devolución | `DevolucionTest`, `EstadoDevolucionTest` |
| Broker caído de punta a punta | `e2e/test_3_resiliencia.py` |
| Dos moderadores a la vez (DEF-20) | `e2e/test_2_devolucion.py` y `porIdBloqueado` en los repositorios |
| El 409 del State y los errores de la API | `ErroresDeLaApiTest`, `DevolucionesRecursoTest` (PATCH con If-Match) |
| Aviso repetido al cliente (DEF-22) | `MensajeriaIT` (`notificacionesMdbConElMensajeRepetidoAvisaUnaSolaVez`), `ServicioDeNotificacionesTest` |
| Mensaje pendiente con el broker reiniciado (CP-85) | `e2e/test_3_resiliencia.py` (`test_un_mensaje_pendiente_sobrevive_al_reinicio_del_broker`) y el volumen `artemis-datos` en `docker-compose.yml` |

Te pueden preguntar: qué es Testcontainers y por qué no alcanza con H2, cómo se prueba que un mensaje no se pierde con el consumidor caído, por qué el bloqueo relee el stock, qué pasaba con dos moderadores a la vez antes del bloqueo, en qué línea nace el 409, para qué sirve el If-Match, qué pasa si a `NotificacionesMDB` le llega dos veces el mismo mensaje y por qué los tres consumidores bloquean la devolución antes de tocarla.

## Fran

| Qué | Dónde |
| --- | --- |
| SOAP y contrato WSDL | `ClienteProveedorTest`, `ContratoWsdlTest`, `ValidadorDeGarantiasTest` |
| Ventas, reseñas y usuarios | `ServicioDeVentasTest`, `ServicioDeResenasYProductosTest`, `ServicioDeUsuariosTest` |
| SEC-01, SEC-02 y SEC-04 | `PasswordHashTest`, `LimitadorDeIntentosTest`, `ServicioDeModeracionTest` |
| Análisis estático | `spotbugs-excluir.xml` (por qué se excluye cada cosa) |
| Largos máximos (DEF-21) | `ServicioDeUsuariosTest`, `ServicioDeResenasYProductosTest`, `ServicioDeDevolucionesTest` |
| Reseñas por la API y validación del JSON | `ResenasRecursoTest`, `CuerpoTest` |
| IA: gateway, fallback y modelo simulado | `AIGatewayTest`, `ClienteLLMHttpTest`, `ServicioDeModeracionTest` (sugerirParaResena) |

Te pueden preguntar: qué es un mutante que sobrevive en PIT, por qué `emailValido` no usa una regex con grupos repetidos, qué valida `ContratoWsdlTest`, qué pasa si la IA tarda 8 segundos y por qué la reseña viaja como dato y no pegada a las instrucciones.

## Matías

| Qué | Dónde |
| --- | --- |
| Cobro y reembolso con Mercado Pago | `ServicioDeCobrosTest`, `ServicioDeCobrosFlujoTest`, `ServicioDeReembolsosTest`, `ClienteMercadoPagoTest` |
| SEC-05 | `ServicioDeDevolucionesTest`, `e2e/test_2_devolucion.py` |
| Idempotency-Key y BOLA por la API | `ServicioDeSolicitudesTest`, `RepositoriosIT` (clave única por usuario), `e2e/test_7_api.py` |
| E2E, accesibilidad, ZAP y carga | `e2e/`, `seguridad/zap.sh`, `seguridad/zap-activo.sh`, `carga/carga.js` |
| CI | `.github/workflows/` |

Te pueden preguntar: cómo se evita reembolsar dos veces, qué pasa si el cliente manda dos veces el mismo POST, por qué una devolución ajena da 404 y no 403, qué mide k6 y qué se arregló cuando dio 1,1 s, y qué hace cada trabajo del CI.

## Ariotti

| Qué | Dónde |
| --- | --- |
| Plan de pruebas y criterios de aceptación | `docs/pruebas/plan-de-pruebas.md` |
| Matriz de trazabilidad | `docs/pruebas/trazabilidad.md` (por ejemplo, la fila de HU-11 y el caso CP-85) |
| Manual de usuario | `docs/manual-de-usuario.md`, sobre todo "Si algo sale mal" |
| Tareas de Jira | `AUR-240` (plan y matriz) y `AUR-241` (manual) |

Te pueden preguntar: qué diferencia hay entre un caso de prueba y un test, para qué sirve la matriz de trazabilidad, qué pasa si una historia no tiene ningún caso, qué es un criterio de aceptación y qué tiene que hacer el moderador si una devolución aprobada no pasa a Reembolsada.

## Correcciones de la 2.ª entrega (20/11)

Cada uno sube su corrección con su cuenta, en su rama y con un PR que revisa otro (ver la tarea E2 en Jira). En la defensa te pueden pedir que expliques el código y los tests de tu parte, o que rompas una línea y muestres que un test falla.

| Quién | Corrección | Código | Tests | Para romper a propósito |
|---|---|---|---|---|
| Lautaro | M3: versión y correlationId del evento | `ServicioDeModeracion.publicar`, `EventoDevolucion` y los tres MDB | `ServicioDeModeracionTest`, `EventoDevolucionTest`, `MensajeriaIT` | Sacá `.setJMSCorrelationID(...)` en `publicar`: falla `elEventoLlevaVersionYUnCorrelationIdDistintoCadaVez` |
| Fran | P7: circuit breaker hacia el proveedor | `CircuitoDelProveedor`, `ClienteProveedor.validarGarantia` | `CircuitoDelProveedorTest`, `ClienteProveedorTest` | Cambiá `>=` por `>` en `registrarFalla`: falla `aLaTerceraFallaSeAbreYNoDejaLlamar` |
| Valen | O4 y R4: `/api/v1/salud` y contrato OpenAPI | `ServicioDeSalud`, `SaludRecurso`, `docs/api/openapi.yaml` | `ServicioDeSaludTest`, `SaludRecursoTest`, `e2e/test_8_contrato.py` | Devolvé 200 siempre en `SaludRecurso`: falla `conElBrokerCaidoDa503YDiceCualFalla` |
| Ariotti | D3: migraciones con Flyway | `sql/migraciones`, servicio `flyway` del compose | `RepositoriosIT` (arma la base con Flyway) | Cambiá un nombre de columna en V1: falla `RepositoriosIT` |
| Fran | Colección Postman (API, éxito y error por endpoint) | `postman/AuraCycles.postman_collection.json` | Newman en el CI | Cambiá un código esperado en la colección y corré Newman |
| Matías | Q2: Checkstyle | `checkstyle.xml`, `pom.xml` | El build mismo | Dejá un import sin usar: `mvn package` falla |

## Cómo correr lo tuyo

- Desde Eclipse: clic derecho en la clase de test > Run As > JUnit Test. Las `*IT` necesitan Docker abierto.
- Todo junto: `mvn verify` (o Run As > Maven build > `verify`).
- E2E: `docker compose up -d --build` y después `cd e2e && pytest`.
