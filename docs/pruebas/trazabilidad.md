# Matriz de trazabilidad

Versión 1 · 11/11/2026 · Grupo 3

Cada historia de usuario comprometida en el diseño, con sus casos de prueba y dónde están automatizados. Las HU y los casos son los del Jira (proyecto AUR): cada caso es una subtarea de su historia. CP-01 a CP-71 son `AUR-125` a `AUR-195`; los de la API y la IA, CP-72 a CP-83, se agregaron para la 2.ª entrega y son `AUR-221` a `AUR-232`.

| HU | Historia | MoSCoW | Casos de prueba | Tests automatizados | Estado |
|---|---|---|---|---|---|
| HU-01 (`AUR-113`) | Registrarme | Must | CP-36, CP-37, CP-38, CP-45, CP-58 | `ServicioDeUsuariosTest`, `PasswordHashTest`, `e2e/test_6_cuenta_y_catalogo.py` | Pasa |
| HU-02 (`AUR-114`) | Ingresar y salir | Must | CP-39, CP-40, CP-41, CP-47, CP-48, CP-50, CP-51, CP-59, CP-72, CP-73, CP-74 | `ServicioDeUsuariosTest`, `LimitadorDeIntentosTest`, `e2e/test_4_seguridad.py`, `e2e/test_9_fuerza_bruta.py` · API: `TokensJwtTest`, `SeguridadDeLaApiTest`, `AuthRecursoTest`, `e2e/test_7_api.py` | Pasa |
| HU-03 (`AUR-115`) | Editar mi perfil y cambiar la contraseña | Should | CP-53, CP-54, CP-60, CP-61 | `ServicioDeUsuariosTest`, `e2e/test_6_cuenta_y_catalogo.py` | Pasa |
| HU-04 (`AUR-116`) | Buscar repuestos y ver el detalle | Must | CP-46, CP-55, CP-56, CP-62 | `ServicioDeResenasYProductosTest`, `RepositoriosIT`, `e2e/test_6_cuenta_y_catalogo.py` | Pasa |
| HU-05 (`AUR-117`) | Comprar un repuesto | Must | CP-01, CP-09, CP-10, CP-11, CP-12 | `ServicioDeVentasTest`, `VentaTest`, `RepositoriosIT`, `e2e/test_1_compra.py` | Pasa |
| HU-06 (`AUR-118`) | Pagar con Mercado Pago | Must | CP-02, CP-03, CP-04, CP-05, CP-06, CP-07, CP-08 | `ServicioDeCobrosTest`, `ServicioDeCobrosFlujoTest`, `ClienteMercadoPagoTest`, `e2e/test_1_compra.py` | Pasa |
| HU-07 (`AUR-119`) | Ver mis compras y pagar una reserva | Should | CP-63, CP-64 | `ServicioDeCobrosTest`, `e2e/test_1_compra.py`, `e2e/test_6_cuenta_y_catalogo.py` | Pasa |
| HU-08 (`AUR-120`) | Escribir una reseña | Should | CP-33, CP-34, CP-65, CP-81 | `ServicioDeResenasYProductosTest` · API: `ResenasRecursoTest`, `e2e/test_7_api.py` | Pasa |
| HU-09 (`AUR-121`) | Moderar reseñas | Should | CP-35, CP-43, CP-44, CP-66, CP-67, CP-82 | `ServicioDeModeracionTest`, `e2e/test_4_seguridad.py` · IA: `AIGatewayTest`, `ClienteLLMHttpTest` | Pasa |
| HU-10 (`AUR-122`) | Pedir una devolución | Must | CP-18, CP-19, CP-20, CP-21, CP-22, CP-23, CP-24, CP-25, CP-26, CP-27, CP-42, CP-68, CP-69, CP-75, CP-76, CP-77, CP-83 | `ServicioDeDevolucionesTest`, `ValidadorDeGarantiasTest`, `ClienteProveedorTest`, `ContratoWsdlTest`, `e2e/test_2_devolucion.py` · API: `ServicioDeSolicitudesTest`, `DevolucionesRecursoTest`, `CuerpoTest`, `RepositoriosIT`, `e2e/test_7_api.py` | Pasa |
| HU-11 (`AUR-123`) | Aprobar o rechazar devoluciones y reembolsar | Must | CP-13, CP-14, CP-15, CP-16, CP-17, CP-28, CP-29, CP-30, CP-31, CP-32, CP-52, CP-70, CP-71, CP-78, CP-79, CP-80 | `ServicioDeModeracionTest`, `ServicioDeReembolsosTest`, `PoliticasDeReembolsoTest`, `EstadoDevolucionTest`, `DevolucionTest`, `MensajeriaIT`, `e2e/test_2_devolucion.py`, `e2e/test_3_resiliencia.py` · API: `DevolucionesRecursoTest`, `ErroresDeLaApiTest`, `e2e/test_7_api.py` | Pasa |
| HU-12 (`AUR-124`) | Usar la tienda en celular, tablet o computadora | Should | CP-49, CP-57 | `e2e/test_5_accesibilidad.py` (axe en 360, 768 y 1280 px) y Lighthouse a mano | Pasa |

Son 12 historias y 83 casos de prueba. Ninguna historia queda sin casos y ningún caso queda sin historia.

## Casos agregados para la API REST y la IA

| Caso | HU | Qué prueba |
|---|---|---|
| CP-72 | HU-02 | Login por la API devuelve un JWT; clave mala, 401 genérico |
| CP-73 | HU-02 | JWT inválido (sin token, firma cambiada, payload adulterado, alg none, vencido): 401 |
| CP-74 | HU-02 | Fuerza bruta contra el login de la API: 429 |
| CP-81 | HU-08 | Reseña por la API queda pendiente; sin haber comprado, 422 |
| CP-82 | HU-09 | Sugerencia de la IA en el panel: sugiere y no decide; con el modelo caído, lento o roto, fallback |
| CP-75 | HU-10 | Idempotency-Key: el reintento no duplica; otra carga con la misma clave, 409; sin clave, 400 |
| CP-76 | HU-10 | Compra o devolución ajena por la API: 404 (BOLA) |
| CP-77 | HU-10 | Proveedor caído por la API: 202 con Retry-After |
| CP-83 | HU-10 | Pedidos mal armados e inyecciones por la API: Problem Details, nunca un 500 |
| CP-78 | HU-11 | El cliente no puede resolver ni el moderador solicitar: 403 (BFLA) |
| CP-79 | HU-11 | ETag: 304 con If-None-Match, 428 sin If-Match, 412 con ETag viejo |
| CP-80 | HU-11 | Transición que el State no permite por la API: 409 en Problem Details |

## Requisitos de las entregas que no son historias

| Requisito | Dónde se prueba |
|---|---|
| El State devuelve 409 ante una transición inválida | `EstadoDevolucionTest` (las 15 transiciones inválidas), `ErroresDeLaApiTest`, `e2e/test_7_api.py` |
| JWT y control de acceso por rol | `TokensJwtTest`, `SeguridadDeLaApiTest`, `e2e/test_7_api.py` |
| Timeout de 5 s con fallback | `ClienteProveedorTest`, `ClienteMercadoPagoTest`, `ClienteLLMHttpTest`, `e2e/test_3_resiliencia.py` |
| Consumidores idempotentes y DLQ | `MensajeriaIT`, `ServicioDeReembolsosTest`, `ServicioDeVentasTest` |
| Con el broker apagado no se pierden mensajes | `MensajeriaIT`, `e2e/test_3_resiliencia.py` |
| Idempotency-Key y ETag en la API | `ServicioDeSolicitudesTest`, `DevolucionesRecursoTest`, `RepositoriosIT`, `e2e/test_7_api.py` |
| Errores en Problem Details | `ErroresDeLaApiTest`, `e2e/test_7_api.py` |
| Sin secretos en el repo | gitleaks sobre todo el historial, en el CI |
| Carga: p95 menor a 500 ms | `carga/carga.js` con k6 (workflow `carga.yml`) |
| ZAP sin alertas High | `seguridad/zap.sh` y `seguridad/zap-activo.sh`, en el CI |
