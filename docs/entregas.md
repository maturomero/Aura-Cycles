# Qué se muestra en cada entrega

Grupo 3 · exponemos 13/11 a las 09:00, 20/11 a las 10:00 y la final el 27/11 a las 09:20. Todo se muestra andando con `docker compose up --build`.

## 2.ª entrega · viernes 13/11

| Lo que pide | Dónde está | Cómo se muestra |
|---|---|---|
| Componentes en capas desplegados y un flujo de punta a punta | `presentacion/` y `api/` → `negocio/` → `datos/`, con `integracion/` y `mensajeria/` | README, sección 3: comprar, pagar, pedir la devolución, aprobarla |
| Patrones implementados | State (`EstadoDevolucion`), Strategy (`negocio/reembolso/`), Facade (`ServicioDeModeracion`, `AIGateway`), Repository (`datos/`), Adapter (`ClienteLLM`). ADR-001 a ADR-008 | Abrir `EstadoDevolucion.java` y `docs/adr` |
| El State devuelve 409 ante una transición inválida | `TransicionInvalidaException` → `ErroresDeLaApi` | `./demo/api.sh`, último paso: rechazar una devolución REEMBOLSADA |
| JWT y control de acceso por rol | `api/TokensJwt`, `api/SeguridadDeLaApi`, ADR-008 | `./demo/api.sh`, pasos 1 y 4: 401 sin token o con la firma cambiada, 403 si el cliente quiere aprobar |
| Mensajería en al menos un proceso | `topico.devoluciones` con tres suscriptores y `cola.validaciones` | Aprobar una devolución y ver en el log los tres consumidores; consola de Artemis en http://localhost:8161 |
| Primeros tests unitarios | 391 unitarios y 20 de integración (al 13/11) | `mvn verify` |
| Jira al día | Proyecto AUR | El tablero |
| Feedback del diseño incorporado | Pendiente (`AUR-214`) | |

Guion sugerido para los 30 minutos: levantar (ya levantado antes de entrar), recorrido por las pantallas (5 min), `./demo/api.sh` explicando cada paso (8 min), código del State y de los patrones (7 min), tests y Jira (5 min), preguntas.

## 3er checkpoint · viernes 20/11

| Lo que pide | Estado | Dónde está |
|---|---|---|
| SOAP, REST y mensajería funcionando | Listo | `ClienteProveedor` (SOAP), `ClienteMercadoPago` y `api/` (REST), tópico y cola |
| Timeout de 5 s con fallback | Listo | Proveedor: queda PENDIENTE_VALIDACION y va a la cola. Mercado Pago: reserva de 30 minutos. IA: "sin sugerencia". Se fuerza con `/__modo?valor=lento` en cada simulador |
| Consumidores idempotentes y DLQ | Listo | Los tres del tópico: `StockMDB` (repone una sola vez), `ReembolsosMDB` (no reembolsa dos veces y manda X-Idempotency-Key a Mercado Pago), `NotificacionesMDB` (un solo aviso, DEF-22). Bloquean la devolución antes de tocarla. DLQ al décimo intento |
| Con el broker apagado no se pierden mensajes | Listo | `docker compose stop artemis` y aprobar: no se aprueba nada (la publicación es XA). Con un consumidor caído, el mensaje espera (suscripción durable). Un mensaje pendiente sobrevive al reinicio del broker: journal en el volumen `artemis-datos` (CP-85, `AUR-238`) |
| Idempotency-Key y ETag en la API | Listo | `POST /devoluciones` y `GET`/`PATCH /devoluciones/{id}`; `./demo/api.sh`, pasos 2, 3 y 5 |
| Errores en Problem Details | Listo | `api/ErroresDeLaApi` |
| La IA funcionando a través de su gateway | Listo con el modelo simulado | Panel de moderación → "Pedir sugerencia a la IA". Para mostrarla con el modelo real hace falta `IA_API_KEY` en un `.env` |
| Pipeline de CI con reporte de cobertura | Listo, corre en cada PR | `.github/workflows/ci.yml`; el reporte de JaCoCo queda como artefacto (`reportes-pruebas`) y en el resumen de la corrida. Al 19/11: 413 tests sin fallas, negocio 97 % de líneas y 93 % de ramas, modelo 92 % y 97 %, mutación 94 % |
| Plan de pruebas y matriz de trazabilidad, primera versión | Listo | `docs/pruebas/` |
| Manual de usuario, primera versión | Listo | `docs/manual-de-usuario.md` |

Guion sugerido (todo levantado antes de entrar):

1. Recorrido rápido: comprar con `ana`, pedir la devolución (SOAP, sale el RMA), aprobar con `mod` y ver en el log los tres consumidores (2 min).
2. Timeout de 5 s: proveedor en `lento` y pedir una devolución, queda "Validando la garantía"; Mercado Pago en `lento` y comprar, queda reservada; IA en `lento`, "IA sin respuesta" (5 min).
3. Broker: `docker compose stop artemis` y aprobar, no cambia nada. Después Mercado Pago en `error`, aprobar otra, apagar y prender el broker con Mercado Pago en `normal`: termina Reembolsada. Mostrar la DLQ en la consola de Artemis (5 min).
4. `./demo/api.sh`: Idempotency-Key, ETag (304, 428, 412) y Problem Details (5 min).
5. La sugerencia de la IA en Reseñas pendientes y `AIGateway` en el código (3 min).
6. La última corrida del CI con el resumen de cobertura, y `docs/pruebas/` y el manual (5 min).

Reparto de la presentación: Matías la tapa, lo que pide el checkpoint, el CI y el cierre; Fran las integraciones, el timeout y la IA; Lautaro los consumidores y el broker; Valen Idempotency-Key, ETag y Problem Details; Ariotti el plan de pruebas, la trazabilidad y el manual.

## Entrega final · viernes 27/11

Condiciones eliminatorias:

| Condición | Estado |
|---|---|
| Build reproducible | Listo: `docker compose up --build` |
| Tag `v1.0-final` en `main` antes de las 08:00 del 27/11 | **Falta**: `main` ya tiene todo, queda poner el tag |
| Pipeline de CI en verde | En verde sobre `main` al 12/11 |
| Jira al día | Al día al 11/11 |
| Sin secretos en el historial | Listo: gitleaks sobre todo el historial no encuentra nada |
| Demo en vivo | Guion en el README, sección 3, y `./demo/api.sh` |
| Declaración de uso de IA | Borrador en `docs/declaracion-ia.md`; la revisa y completa cada integrante |
| Informe honesto | **Falta** escribirlo con los números de la última corrida |

Lo que falta para los ejes:

- **Mercado Pago real:** el cobro ya se probó contra el real con las credenciales de prueba (`docs/pruebas/mercado-pago-real.md`). El reembolso lo rechaza el ambiente de prueba y sigue probado solo contra el simulador. Falta la corrida con la tienda levantada y sus capturas (`AUR-233`; pasos en el README, sección 6).
- **Sonar:** el CI ya tiene el paso, pero necesita el proyecto creado en SonarCloud y el secreto `SONAR_TOKEN` en el repo.
- **ZAP y carga con la API:** volver a correr `seguridad/zap.sh`, `seguridad/zap-activo.sh` y `carga/correr.sh` y sumar la API al escaneo.
- **Testing de la IA:** correr el set de 20 casos contra el modelo real y guardar el resultado.
- **Defensa individual:** cada uno con lo suyo de `docs/REVISION.md`.
- Leer completo el PDF de Criterios de Testing y revisar los umbrales contra `docs/pruebas/plan-de-pruebas.md`.
