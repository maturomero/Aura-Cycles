# Matriz de trazabilidad

Versión 2.0 · 26/11/2026 · Grupo 3

HU → criterio de aceptación → casos de prueba → tests automatizados → resultado → evidencia → defecto. Las HU, los criterios (Dado / Cuando / Entonces) y los casos están en el Jira, proyecto AUR: cada caso es una subtarea de su historia y cada defecto es un Error con la etiqueta `defecto`. La evidencia es el artefacto del run del CI sobre el tag `v1.0-final` (`reportes-pruebas`: surefire, failsafe y JaCoCo; `reportes-e2e`: Playwright, Newman y contrato; `reportes-zap*`; `carga.yml`: k6).

| HU | Criterio | Tipo | Casos | Tests automatizados | Resultado | Evidencia | Defecto |
|---|---|---|---|---|---|---|---|
| HU-01 Registrarme (`AUR-113`, Must) | **HU-01.1** Alta con email nuevo y clave de 8 a 64: queda cliente y la clave en bcrypt | Feliz | CP-36 | `ServicioDeUsuariosTest` | Pasa | reportes-pruebas | - |
|  | **HU-01.2** Email con mayúsculas o espacios se guarda normalizado | Alternativo | CP-36 | `ServicioDeUsuariosTest` | Pasa | reportes-pruebas | - |
|  | **HU-01.3** Email repetido: "Ya hay una cuenta registrada con ese email." | Error | CP-58 | `ServicioDeUsuariosTest` | Pasa | reportes-pruebas | - |
|  | **HU-01.4** Clave de 7 o 65, email inválido o largo, nombre vacío o largo: mensaje y no se crea | Error | CP-37, CP-38, CP-61 | `ServicioDeUsuariosTest` | Pasa | reportes-pruebas | - |
|  | **HU-01.5** rol=MODERADOR agregado y `<script>` en el nombre: queda CLIENTE y escapado | Error | CP-45 | `test_4_seguridad` | Pasa | reportes-e2e | - |
| HU-02 Ingresar y salir (`AUR-114`, Must) | **HU-02.1** Login correcto: catálogo; la cookie cambia y sale Secure, HttpOnly, SameSite=Lax | Feliz | CP-48, CP-51, CP-59, CP-72 | `test_4_seguridad`, `ServicioDeUsuariosTest`, todos los E2E, `AuthRecursoTest`, `test_7_api` | Pasa | reportes-e2e, reportes-pruebas | DEF-09, DEF-13, DEF-15 |
|  | **HU-02.2** Cerrar sesión: la cookie vieja ya no abre Mis compras | Alternativo | CP-50 | `test_4_seguridad` | Pasa | reportes-e2e | - |
|  | **HU-02.3** Clave mala o email inexistente: el mismo mensaje | Error | CP-41 | `PasswordHashTest`, `ServicioDeUsuariosTest` | Pasa | reportes-pruebas | DEF-01 |
|  | **HU-02.4** 5 fallos en 10 min: 429 durante 5 min, aunque la clave sea correcta | Error | CP-39, CP-40, CP-74 | `test_9_fuerza_bruta`, `LimitadorDeIntentosTest`, `AuthRecursoTest` | Pasa | reportes-e2e, reportes-pruebas | DEF-08 |
|  | **HU-02.5** Cookie o token inventado o adulterado: login (401 en la API), ningún dato | Error | CP-50, CP-73, CP-93 | `test_4_seguridad`, `TokensJwtTest`, `SeguridadDeLaApiTest`, `test_7_api`, `zap-api.sh (job zap-api)` | Pasa | reportes-e2e, reportes-pruebas, reportes-zap-api | - |
|  | **HU-02.6** Pantalla vencida: aviso de inactividad, sin 500 | Alternativo | CP-47 | `test_4_seguridad` | Pasa | reportes-e2e | DEF-14 |
| HU-03 Editar perfil y contraseña (`AUR-115`, Should) | **HU-03.1** Cambiar el nombre: "Guardamos tus datos." | Feliz | CP-53 | `test_6_cuenta_y_catalogo`, `ServicioDeUsuariosTest` | Pasa | reportes-e2e, reportes-pruebas | - |
|  | **HU-03.2** Cambiar la clave con la actual bien: ingresa con la nueva | Alternativo | CP-60 | `ServicioDeUsuariosTest` | Pasa | reportes-pruebas | - |
|  | **HU-03.3** Email en uso o mal formado, nombre vacío o largo: mensaje y no cambia | Error | CP-38, CP-61 | `ServicioDeUsuariosTest` | Pasa | reportes-pruebas | DEF-12, DEF-21 |
|  | **HU-03.4** Clave actual mal o nuevas distintas: mensaje y la clave no cambia | Error | CP-54 | `test_6_cuenta_y_catalogo`, `ServicioDeUsuariosTest` | Pasa | reportes-e2e, reportes-pruebas | - |
| HU-04 Buscar y ver el detalle (`AUR-116`, Must) | **HU-04.1** Búsqueda por nombre o categoría sin importar mayúsculas | Feliz | CP-62 | `ServicioDeResenasYProductosTest`, `RepositoriosIT` | Pasa | reportes-pruebas, reportes-pruebas (IT) | DEF-17 |
|  | **HU-04.2** Stock 0: "Sin stock" y Comprar deshabilitado | Alternativo | CP-55 | `test_6_cuenta_y_catalogo` | Pasa | reportes-e2e | - |
|  | **HU-04.3** El promedio cuenta solo aprobadas; sin reseñas, aviso | Alternativo | CP-35, CP-56 | `ServicioDeModeracionTest`, `RepositoriosIT`, `test_6_cuenta_y_catalogo` | Pasa | reportes-e2e, reportes-pruebas, reportes-pruebas (IT) | - |
|  | **HU-04.4** Búsqueda inexistente o ' OR '1'='1: "No hay repuestos que coincidan" | Error | CP-46 | `test_4_seguridad`, `RepositoriosIT` | Pasa | reportes-e2e, reportes-pruebas (IT) | - |
| HU-05 Comprar un repuesto (`AUR-117`, Must) | **HU-05.1** Comprar con stock: orden pendiente de pago, baja el stock, va al checkout | Feliz | CP-01, CP-84, CP-90 | `test_1_compra`, `ServicioDeVentasTest`, `ServicioDeCobrosFlujoTest`, `test_6_cuenta_y_catalogo`, `RepositoriosIT`, `servicio flyway del compose` | Pasa | reportes-e2e, reportes-pruebas, reportes-pruebas (IT) | - |
|  | **HU-05.2** Comprar la última unidad: stock 0 | Borde | CP-09 | `ServicioDeVentasTest` | Pasa | reportes-pruebas | - |
|  | **HU-05.3** Sin unidades o cantidad inválida: mensaje y no se crea la orden | Error | CP-10, CP-11, CP-12 | `ServicioDeVentasTest`, `RepositoriosIT` | Pasa | reportes-pruebas, reportes-pruebas (IT) | DEF-06 |
| HU-06 Pagar con Mercado Pago (`AUR-118`, Must) | **HU-06.1** Pago aprobado: orden pagada con id de pago, sin datos de tarjeta | Feliz | CP-01 | `test_1_compra`, `ServicioDeVentasTest`, `ServicioDeCobrosFlujoTest` | Pasa | reportes-e2e, reportes-pruebas | DEF-11 |
|  | **HU-06.2** Pago rechazado: vuelve el stock | Alternativo | CP-02 | `test_1_compra`, `ServicioDeCobrosTest` | Pasa | reportes-e2e, reportes-pruebas | - |
|  | **HU-06.3** 30 min sin pagar: reserva vencida y vuelve el stock | Alternativo | CP-06, CP-07 | `ServicioDeCobrosFlujoTest`, `RepositoriosIT` | Pasa | reportes-pruebas, reportes-pruebas (IT) | DEF-04 |
|  | **HU-06.4** URL de vuelta adulterada: la orden no cambia | Error | CP-04, CP-08 | `test_1_compra`, `ServicioDeCobrosTest`, `RepositoriosIT` | Pasa | reportes-e2e, reportes-pruebas, reportes-pruebas (IT) | - |
|  | **HU-06.5** Pago por otro monto o moneda: no se acepta | Error | CP-05 | `ServicioDeCobrosTest` | Pasa | reportes-pruebas | - |
|  | **HU-06.6** MP no responde en 5 s: aviso y la orden queda reservada | Falla externa | CP-03 | `test_1_compra`, `ClienteMercadoPagoTest` | Pasa | reportes-e2e, reportes-pruebas | - |
| HU-07 Mis compras y pagar reserva (`AUR-119`, Should) | **HU-07.1** Mis compras muestra solo las órdenes propias | Feliz | CP-63 | `ServicioDeVentasTest` | Pasa | reportes-pruebas | DEF-17 |
|  | **HU-07.2** Pagar una pendiente; si ya estaba pagada no se cobra de nuevo | Alternativo | CP-03, CP-64 | `test_1_compra`, `ClienteMercadoPagoTest`, `ServicioDeCobrosFlujoTest` | Pasa | reportes-e2e, reportes-pruebas | - |
|  | **HU-07.3** Orden ajena en la URL: "La compra no existe." | Error | CP-42 | `test_2_devolucion`, `ServicioDeDevolucionesTest`, `ServicioDeCobrosFlujoTest` | Pasa | reportes-e2e, reportes-pruebas | - |
| HU-08 Escribir una reseña (`AUR-120`, Should) | **HU-08.1** Reseña de un repuesto comprado: queda pendiente de moderación | Feliz | CP-33, CP-81, CP-92 | `ServicioDeResenasYProductosTest`, `ResenasRecursoTest`, `test_7_api` | Pasa | reportes-e2e, reportes-pruebas | - |
|  | **HU-08.2** Puntaje 1 y 5 sí; 0, 6 y -3 no | Borde | CP-33 | `ServicioDeResenasYProductosTest` | Pasa | reportes-pruebas | - |
|  | **HU-08.3** Sin haber comprado o pagado: rechazada | Error | CP-34, CP-81 | `ServicioDeResenasYProductosTest`, `RepositoriosIT`, `ResenasRecursoTest`, `test_7_api` | Pasa | reportes-e2e, reportes-pruebas, reportes-pruebas (IT) | - |
|  | **HU-08.4** Texto vacío o largos fuera de límite: mensaje y no se guarda | Error | CP-65 | `ServicioDeResenasYProductosTest` | Pasa | reportes-pruebas | DEF-21 |
| HU-09 Moderar reseñas (`AUR-121`, Should) | **HU-09.1** Aprobar: "Reseña aprobada." y cuenta en el promedio | Feliz | CP-35 | `ServicioDeModeracionTest`, `RepositoriosIT` | Pasa | reportes-pruebas, reportes-pruebas (IT) | - |
|  | **HU-09.2** Rechazar: no se publica | Alternativo | CP-66 | `ServicioDeModeracionTest` | Pasa | reportes-pruebas | - |
|  | **HU-09.3** Un cliente en el panel o el servicio: 403 y el EJB rechaza | Error | CP-43, CP-44, CP-78 | `test_4_seguridad`, `ServicioDeModeracionTest`, `SeguridadDeLaApiTest`, `test_7_api` | Pasa | reportes-e2e, reportes-pruebas | - |
|  | **HU-09.4** Reseña ya resuelta: "Esa reseña ya fue resuelta." | Error | CP-67 | `ServicioDeModeracionTest` | Pasa | reportes-pruebas | - |
|  | **HU-09.5** Sugerencia de la IA: sugiere sin decidir; con inyección nunca aprueba; si falla, "IA sin respuesta" | Falla externa | CP-82, CP-91 | `AIGatewayTest`, `ClienteLLMHttpTest`, `ServicioDeModeracionTest`, `EvaluacionIATest` | Pasa | reportes-pruebas | DEF-23 |
| HU-10 Pedir una devolución (`AUR-122`, Must) | **HU-10.1** Compra pagada con garantía: DEV-nnnnnn pendiente de revisión con RMA | Feliz | CP-18, CP-24, CP-75, CP-83, CP-89, CP-92, CP-94 | `test_2_devolucion`, `ClienteProveedorTest`, `ServicioDeDevolucionesTest`, `ContratoWsdlTest`, `ServicioDeSolicitudesTest`, `DevolucionesRecursoTest`, `RepositoriosIT`, `test_7_api`, `CuerpoTest`, `test_8_contrato`, `Postman con Newman`, `carga-api.js (workflow carga.yml)` | Pasa | reportes-e2e, reportes-pruebas, reportes-pruebas (IT), carga.yml | - |
|  | **HU-10.2** 30 días se puede; 31 no | Borde | CP-25 | `ServicioDeDevolucionesTest` | Pasa | reportes-pruebas | - |
|  | **HU-10.3** Consumible o garantía vencida: mensaje del proveedor | Error | CP-19, CP-20 | `test_2_devolucion`, `ClienteProveedorTest` | Pasa | reportes-e2e, reportes-pruebas | - |
|  | **HU-10.4** Otra solicitud o compra sin pagar: mensaje | Error | CP-26, CP-27 | `ServicioDeDevolucionesTest` | Pasa | reportes-pruebas | - |
|  | **HU-10.5** Comentario faltante o de más de 1000: mensaje | Error | CP-68 | `ServicioDeDevolucionesTest` | Pasa | reportes-pruebas | - |
|  | **HU-10.6** Compra ajena: "La compra no existe." (404 en la API) | Error | CP-42, CP-76, CP-93 | `test_2_devolucion`, `ServicioDeDevolucionesTest`, `ServicioDeCobrosFlujoTest`, `ServicioDeSolicitudesTest`, `test_7_api`, `zap-api.sh (job zap-api)` | Pasa | reportes-e2e, reportes-pruebas, reportes-zap-api | - |
|  | **HU-10.7** Proveedor sin respuesta o Fault técnico: queda validándose y reintenta | Falla externa | CP-21, CP-22, CP-23, CP-69, CP-77, CP-87 | `test_2_devolucion`, `ValidadorDeGarantiasTest`, `ClienteProveedorTest`, `DevolucionTest`, `ServicioDeSolicitudesTest`, `DevolucionesRecursoTest`, `test_7_api`, `CircuitoDelProveedorTest` | Pasa | reportes-e2e, reportes-pruebas | DEF-10 |
| HU-11 Resolver devoluciones y reembolsar (`AUR-123`, Must) | **HU-11.1** Aprobar: reembolsada en menos de 30 s y stock repuesto | Feliz | CP-13, CP-29, CP-86, CP-94 | `test_2_devolucion`, `ServicioDeReembolsosTest`, `DevolucionTest`, `EventoDevolucionTest`, `MensajeriaIT`, `carga-api.js (workflow carga.yml)` | Pasa | reportes-e2e, reportes-pruebas, reportes-pruebas (IT), carga.yml | - |
|  | **HU-11.2** Arrepentimiento 90 % a centavos; otro motivo 100 % | Borde | CP-16, CP-17 | `PoliticasDeReembolsoTest`, `ServicioDeReembolsosTest`, `ClienteMercadoPagoTest` | Pasa | reportes-pruebas | - |
|  | **HU-11.3** Rechazar: sin reembolso ni stock | Alternativo | CP-70 | `ServicioDeModeracionTest`, `MensajeriaIT` | Pasa | reportes-pruebas, reportes-pruebas (IT) | - |
|  | **HU-11.4** Dos moderadores a la vez o ya resuelta: un solo reembolso (409 en la API) | Error | CP-28, CP-52, CP-71, CP-79, CP-80 | `DevolucionTest (parametrizado)`, `EstadoDevolucionTest`, `test_2_devolucion`, `ServicioDeModeracionTest`, `DevolucionesRecursoTest`, `test_7_api`, `ErroresDeLaApiTest` | Pasa | reportes-e2e, reportes-pruebas | DEF-20 |
|  | **HU-11.5** Evento duplicado: reembolsa y repone una sola vez | Error | CP-14, CP-32, CP-86 | `ServicioDeReembolsosTest`, `ClienteMercadoPagoTest`, `ServicioDeVentasTest`, `MensajeriaIT`, `EventoDevolucionTest` | Pasa | reportes-pruebas, reportes-pruebas (IT) | DEF-07, DEF-22 |
|  | **HU-11.6** Broker apagado: aviso en 5 s y no cambia nada | Falla externa | CP-30, CP-85, CP-88 | `test_3_resiliencia`, `ServicioDeModeracionTest`, `ServicioDeSaludTest`, `SaludRecursoTest`, `test_8_contrato`, `Postman` | Pasa | reportes-e2e, reportes-pruebas | DEF-02, DEF-03 |
|  | **HU-11.7** MP caído al reembolsar: reintenta cada 10 s y al décimo DLQ | Falla externa | CP-15, CP-31 | `ServicioDeReembolsosTest`, `MensajeriaIT` | Pasa | reportes-pruebas, reportes-pruebas (IT) | DEF-05 |
| HU-12 Responsive y accesible (`AUR-124`, Should) | **HU-12.1** 360, 768 y 1280 px sin scroll horizontal ni violaciones serias de axe | Feliz | CP-49 | `test_5_accesibilidad` | Pasa | reportes-e2e | DEF-16, DEF-18, DEF-19 |
|  | **HU-12.2** Lighthouse accesibilidad 90 o más | Alternativo | CP-57 | Manual (Lighthouse) | Pasa | captura Lighthouse | - |
|  | **HU-12.3** Un problema serio de accesibilidad hace fallar el CI | Error | CP-49 | `test_5_accesibilidad` | Pasa | reportes-e2e | - |

Son 12 historias, 57 criterios y 94 casos de prueba. Todas las historias y todos los criterios tienen al menos un caso. 76 de los 94 casos (81 %) son negativos o de borde. El único caso manual es CP-57 (Lighthouse).

## Casos y su clave en el Jira

CP-01 a CP-71 son `AUR-125` a `AUR-195`; CP-72 a CP-83, `AUR-221` a `AUR-232`; CP-84 es `AUR-235`; CP-85, `AUR-239`; CP-86 a CP-94, `AUR-250` a `AUR-258`. La especificación completa de cada caso (precondición, datos, pasos, esperado, prioridad, tipo y si está automatizado) está en la subtarea y en el documento de testing.

## Requisitos de las entregas que no son historias

| Requisito | Dónde se prueba |
|---|---|
| El State devuelve 409 ante una transición inválida | `EstadoDevolucionTest` (las 15 transiciones inválidas), `ErroresDeLaApiTest`, `e2e/test_7_api.py` |
| JWT y control de acceso por rol (BOLA, BFLA, JWT inválidos) | `TokensJwtTest`, `SeguridadDeLaApiTest`, `e2e/test_7_api.py` |
| Mass assignment | `e2e/test_7_api.py::test_mass_assignment_en_la_api` (CP-92) |
| Timeout de 5 s con fallback y circuit breaker | `ClienteProveedorTest`, `CircuitoDelProveedorTest`, `ClienteMercadoPagoTest`, `ClienteLLMHttpTest`, `e2e/test_3_resiliencia.py` |
| Consumidores idempotentes y DLQ | `MensajeriaIT`, `ServicioDeReembolsosTest`, `ServicioDeVentasTest`, `ServicioDeNotificacionesTest` |
| Con el broker apagado no se pierden mensajes | `MensajeriaIT`, `e2e/test_3_resiliencia.py` (CP-30 y CP-85) |
| Rollback completo si falla la mensajería en la transacción | `ServicioDeModeracionTest`, `e2e/test_3_resiliencia.py` (ADR-009) |
| Idempotency-Key y ETag en la API | `ServicioDeSolicitudesTest`, `DevolucionesRecursoTest`, `RepositoriosIT`, `e2e/test_7_api.py` |
| Errores en Problem Details | `ErroresDeLaApiTest`, `e2e/test_7_api.py`, `e2e/test_8_contrato.py` |
| Contrato OpenAPI y WSDL | `e2e/test_8_contrato.py`, colección Postman, `ContratoWsdlTest` |
| Funcionalidad de IA: inyección, fuera de dominio, fuga de datos, modelo caído | `EvaluacionIATest` (CP-91), `AIGatewayTest`, `ClienteLLMHttpTest` |
| Sin secretos en el repo | gitleaks sobre todo el historial, en el CI |
| Dependencias sin CVE críticas o altas | Trivy en el CI |
| Carga: p95 menor a 500 ms | `carga/carga.js` (pantallas) y `carga/carga-api.js` (API), workflow `carga.yml` |
| ZAP sin alertas High | `seguridad/zap.sh`, `seguridad/zap-activo.sh` y `seguridad/zap-api.sh`, en el CI |
