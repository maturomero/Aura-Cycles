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
| Primeros tests unitarios | 391 unitarios y 20 de integración | `mvn verify` |
| Jira al día | Proyecto AUR | El tablero |
| Feedback del diseño incorporado | Al 12/11 no tenemos la devolución del diseño. Cuando llegue, se anota acá cada observación y dónde se corrigió (`AUR-214`) | |

Guion sugerido para los 30 minutos: levantar (ya levantado antes de entrar), recorrido por las pantallas (5 min), `./demo/api.sh` explicando cada paso (8 min), código del State y de los patrones (7 min), tests y Jira (5 min), preguntas.

## 3er checkpoint · viernes 20/11

| Lo que pide | Estado | Dónde está |
|---|---|---|
| SOAP, REST y mensajería funcionando | Listo | `ClienteProveedor` (SOAP), `ClienteMercadoPago` y `api/` (REST), tópico y cola |
| Timeout de 5 s con fallback | Listo | Proveedor: queda PENDIENTE_VALIDACION y va a la cola. Mercado Pago: reserva de 30 minutos. IA: "sin sugerencia". Se fuerza con `/__modo?valor=lento` en cada simulador |
| Consumidores idempotentes y DLQ | Listo | `StockMDB` (repone una sola vez), `ReembolsosMDB` (X-Idempotency-Key hacia Mercado Pago), DLQ al décimo intento |
| Con el broker apagado no se pierden mensajes | Listo | `docker compose stop artemis` y aprobar: no se aprueba nada. Con un consumidor caído, el mensaje espera (suscripción durable) |
| Idempotency-Key y ETag en la API | Listo | `POST /devoluciones` y `GET`/`PATCH /devoluciones/{id}`; `./demo/api.sh`, pasos 2, 3 y 5 |
| Errores en Problem Details | Listo | `api/ErroresDeLaApi` |
| La IA funcionando a través de su gateway | Listo con el modelo simulado | Panel de moderación → "Pedir sugerencia a la IA". Para mostrarla con el modelo real hace falta `IA_API_KEY` en un `.env` |
| Pipeline de CI con reporte de cobertura | Listo, corre en cada PR | `.github/workflows/ci.yml`; el reporte de JaCoCo queda como artefacto y en el resumen de la corrida |
| Plan de pruebas y matriz de trazabilidad, primera versión | Listo | `docs/pruebas/` |
| Manual de usuario, primera versión | Listo | `docs/manual-de-usuario.md` |

## Entrega final · viernes 27/11

Condiciones eliminatorias:

| Condición | Estado |
|---|---|
| Build reproducible | Listo: `docker compose up --build` |
| Tag `v1.0-final` en `main` antes de las 08:00 del 27/11 | **Falta**: primero hay que mergear el PR a `main` |
| Pipeline de CI en verde | En verde en el PR #2 al 11/11; hay que mirarlo de nuevo después del merge a `main` |
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
