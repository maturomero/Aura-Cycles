# Plan de pruebas

Versión 2.0 · 26/11/2026 · Grupo 3 (García, Espinel, Ariotti, Oubiña, Romero) · QA: Matías Romero (`AUR-260`)

Versión final, para la prueba del sistema sobre el tag `v1.0-final`.

## 1. Alcance

Entra: las 12 historias de usuario comprometidas en el diseño (HU-01 a HU-12, `AUR-113` a `AUR-124`) con sus 57 criterios de aceptación, y lo que piden las entregas: el State con su 409, la API REST con JWT, Idempotency-Key, ETag y Problem Details, las tres integraciones (SOAP con el proveedor, REST con Mercado Pago y mensajería con Artemis), la resiliencia, la seguridad y la sugerencia de IA para moderar reseñas. Qué caso cubre qué criterio está en [trazabilidad.md](trazabilidad.md).

Queda afuera:

- Mercado Pago real en lo automatizado. Se prueba contra el simulador del compose; hay una corrida manual con credenciales de prueba en [mercado-pago-real.md](mercado-pago-real.md): el cobro anduvo y el reembolso lo rechaza el ambiente de prueba de Mercado Pago.
- El envío real de emails: las notificaciones salen por log.
- El modelo de IA real en el CI: cuesta por llamada y no es determinístico. El set de evaluación corre contra el modelo simulado y se puede correr contra el real con `IA_EVAL_REAL=true`.
- Navegadores distintos de Chromium y aplicaciones móviles (no hay app móvil).

## 2. Niveles y herramientas

| Nivel | Qué cubre | Con qué | Cuántos | Cómo se corre |
|---|---|---|---|---|
| Unitario | Reglas de negocio, el State, las políticas de reembolso, los clientes de integración contra simuladores, la API REST con la capa de negocio mockeada, el gateway de IA | JUnit 5 y Mockito | 428 | `mvn test` |
| Integración | Repositorios contra MySQL real y mensajería contra Artemis real | Testcontainers; la base se arma con las migraciones de Flyway | 22 | `mvn verify` (con Docker) |
| Punta a punta | Los flujos completos por las pantallas y por la API, con todo levantado | Playwright y pytest | 50 | `docker compose up -d --build` y `cd e2e && pytest` |
| Contrato | Cada respuesta real de la API contra `docs/api/openapi.yaml`, y los mensajes SOAP contra el WSDL | openapi-core (`e2e/test_8_contrato.py`) y `ContratoWsdlTest` | 9 + 1 | dentro de los E2E y de `mvn test` |
| API | Cada endpoint con al menos un caso de éxito y uno de error | Colección Postman (`postman/`) con Newman | 28 pedidos, 51 aserciones | `npx newman run postman/AuraCycles.postman_collection.json --insecure` |
| IA | Inyección de prompt directa e indirecta, pedidos fuera de dominio, fuga de datos, modelo caído o lento | `EvaluacionIATest` (20 casos de `src/test/resources/ia/evaluacion.tsv`) y `AIGatewayTest` | 20 + unitarios | dentro de `mvn test` |
| Accesibilidad | WCAG 2.2 A y AA en 360, 768 y 1280 px | axe en los E2E y Lighthouse a mano | 3 (6 pantallas cada uno) | dentro de los E2E |
| Mutación | Que los tests del dominio detecten cambios en el código | PIT | ver informe | `mvn test-compile org.pitest:pitest-maven:mutationCoverage` |
| Estático | Bugs, vulnerabilidades, duplicación y estilo | SonarCloud, SpotBugs con FindSecBugs, Checkstyle (corta el build) y jscpd | | en el CI |
| Seguridad dinámica | Pantallas sin sesión, como cliente y como moderador; la API con el JWT de cada rol | OWASP ZAP baseline, activo autenticado y API scan | | `seguridad/zap.sh`, `seguridad/zap-activo.sh`, `seguridad/zap-api.sh` |
| Carga | 50 usuarios durante 5 minutos sobre las 3 pantallas y los 3 endpoints críticos de la API | k6 | 2 escenarios | `carga/correr.sh` y `carga/correr.sh carga-api.js` |
| Secretos y dependencias | Todo el historial del repo y las librerías | gitleaks y Trivy | | en el CI |

## 3. Riesgos

Probabilidad (P) e impacto (I) de 1 a 3. La prioridad es P × I; de 6 para arriba se prueba en más de un nivel.

| Id | Riesgo | P | I | P × I | Cómo lo cubren las pruebas |
|---|---|---|---|---|---|
| R1 | Reembolsar dos veces o perder un reembolso (evento repetido, Mercado Pago caído, dos moderadores a la vez) | 2 | 3 | 6 | CP-13, CP-14, CP-15, CP-32, CP-52, CP-85; `MensajeriaIT`; E2E de resiliencia. DEF-05, DEF-07, DEF-20 |
| R2 | Stock corrupto con compras simultáneas | 2 | 3 | 6 | CP-09 a CP-12 en unitarios y `RepositoriosIT` contra MySQL real. DEF-06 |
| R3 | Un usuario ve o toca datos de otro (BOLA) o usa funciones de otro rol (BFLA) | 2 | 3 | 6 | CP-42, CP-43, CP-44, CP-76, CP-78; ZAP con sesión y API scan |
| R4 | Robo de cuentas: fuerza bruta, JWT falsos, fijación de sesión | 2 | 3 | 6 | CP-39, CP-40, CP-41, CP-50, CP-51, CP-73, CP-74. DEF-08, DEF-09 |
| R5 | Se cae el broker y se pierden mensajes o la operación queda a medias | 2 | 3 | 6 | CP-30, CP-31, CP-85, CP-88; `e2e/test_3_resiliencia.py` apaga Artemis de verdad. DEF-02, DEF-03 |
| R6 | Pago adulterado (URL de vuelta, otro monto, el mismo pago dos veces) | 2 | 3 | 6 | CP-04, CP-05, CP-08 |
| R7 | Inyección SQL, XSS o mass assignment | 2 | 3 | 6 | CP-45, CP-46, CP-83, CP-92; ZAP activo y API scan |
| R8 | Proveedor SOAP lento, caído o con respuesta maliciosa (XXE) | 3 | 2 | 6 | CP-21, CP-22, CP-23, CP-69, CP-77, CP-87. DEF-10 |
| R9 | Mercado Pago lento o caído al cobrar | 3 | 2 | 6 | CP-03, CP-06, CP-07, CP-64. DEF-04 |
| R10 | La IA sugiere aprobar una reseña con instrucciones ajenas, filtra datos o revela el prompt | 3 | 2 | 6 | CP-82, CP-91 (20 casos, 6 adversariales). DEF-23 |
| R11 | Pantallas o API lentas con 50 usuarios | 2 | 2 | 4 | `carga/carga.js` y CP-94. DEF-17 |
| R12 | La API no cumple su contrato y rompe a quien la usa | 2 | 2 | 4 | CP-89 y la colección Postman |
| R13 | Pantallas no usables en celular o con lector de pantalla | 2 | 2 | 4 | CP-49, CP-57. DEF-18, DEF-19, DEF-24 |
| R14 | Datos personales o tokens en los logs | 1 | 3 | 3 | SEC-09 (`AUR-66`): `DatosPersonalesTest` (el log enmascara el email) y `AIGatewayTest` (al modelo no le llega email ni teléfono) |
| R15 | Una migración de base rompe datos existentes | 1 | 3 | 3 | CP-90 (`RepositoriosIT` y el servicio `flyway` del compose) |

Riesgos del proyecto (no del producto):

| Riesgo | Qué hacemos |
|---|---|
| Los E2E dependen de tiempos (broker, redirecciones) y pueden fallar de vez en cuando | Workflow `inestables.yml`: corre la suite 3 veces; además las esperas son por estado, no por tiempo fijo |
| El test de fuerza bruta bloquea la IP 5 minutos | Va último (`test_9`); para repetir a mano, `docker compose restart app` |
| El escaneo activo de ZAP prueba el login hasta disparar el bloqueo | El script pide los tokens antes de escanear; en las pantallas el escaneo espera a que se libere el bloqueo |
| Maven Central responde 429 o 502 en el CI | Reintento del build en el Dockerfile y en el CI |

## 4. Ambiente y datos de prueba

Un solo comando levanta lo mismo en cualquier máquina y en el CI: `docker compose up --build` (README, sección 2). Son la app en WildFly 39, MySQL 8.4 con el esquema que arman las migraciones de Flyway (`sql/migraciones`, V1 a V4), el broker Artemis aparte (para poder apagarlo) y tres simuladores: Mercado Pago, el proveedor SOAP y el modelo de IA. Cada simulador tiene modos `lento`, `error` y `roto` para forzar las fallas.

Datos de prueba: los carga la migración. Usuarios `ana`, `carlos`, `lucia` y `javier` (clientes) y `mod` (moderador), todos `@mail.com` con la clave `Clave1234`; repuestos con stock, sin stock y consumibles; compras pagadas de hace 30 y 31 días. Los E2E crean lo que necesitan sobre esa base y no dependen del orden, salvo `test_9`, que va último.

## 5. Criterios de entrada y de salida

Entrada (para empezar a probar una versión):

- El build compila y pasa Checkstyle.
- `docker compose up --build` levanta todo y `GET /api/v1/salud` da 200.
- Cada HU tiene al menos 3 criterios en Dado / Cuando / Entonces y cada criterio tiene al menos un caso en el Jira.

Salida (para dar por aprobada la versión del tag):

| Qué | Umbral |
|---|---|
| Criterios de aceptación | 100 % de las HU ejecutadas; 95 % o más de criterios pasados; ninguno de una HU Must fallado |
| Tests | todos pasan; la suite corrida 3 veces da lo mismo |
| Cobertura de `negocio` y `modelo` | 80 % de líneas y 70 % de ramas (si no, falla el build) |
| Mutación (PIT sobre el dominio) | 60 % o más |
| SonarCloud | Quality Gate aprobado: 0 bugs Blocker o Critical, 0 vulnerabilidades, duplicación de 3 % o menos, A en confiabilidad y seguridad, hotspots revisados |
| ZAP | 0 High; cada Medium justificado en `seguridad/zap-justificadas.tsv` |
| Dependencias | 0 CVE críticas o altas sin mitigación ([dependencias.md](dependencias.md)) |
| Secretos | gitleaks sin hallazgos en todo el historial |
| Carga | p95 menor a 500 ms y menos de 1 % de errores en cada endpoint |
| IA | 80 % o más de acierto en el set de evaluación y 0 aprobaciones indebidas |
| Pipeline | 15 minutos o menos |
| Defectos | ninguno Bloqueante ni Crítico abierto; Mayor, a lo sumo 3 abiertos con workaround |

Los resultados de cada umbral están en el informe de ejecución del documento de testing (`Clase15_Grupo3_AuraCycles_Testing.pdf`).

## 6. Quién prueba qué

QA responsable: Matías Romero. Cada uno responde por los tests de lo que hizo:

| Integrante | Área |
|---|---|
| Francisco Espinel | Usuarios y perfil, reseñas, proveedor SOAP y circuit breaker, IA |
| Lautaro García | State de la devolución, mensajería y consumidores, moderación, concurrencia |
| Valentino Oubiña | Seguridad de las pantallas, JWT, salud y contrato de la API |
| Valentino Ariotti | Migraciones con Flyway, revisión del plan y prueba del manual de usuario |
| Matías Romero | Ventas, cobros con Mercado Pago, devoluciones por la API, carga, ZAP y el documento de testing |

## 7. Defectos

Se cargan en el Jira como tipo Error con la etiqueta `defecto`: cómo se encontró, pasos, esperado y obtenido, causa, arreglo, severidad y el test que lo cubre. La severidad sigue los criterios de la cátedra (Bloqueante, Crítica, Mayor, Menor). Un defecto no se cierra sin un test que falle antes del arreglo y pase después.
