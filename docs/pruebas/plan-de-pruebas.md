# Plan de pruebas

Versión 1 · 11/11/2026 · Grupo 3 (García, Espinel, Ariotti, Oubiña, Romero)

Primera versión para el 3er checkpoint. La versión final se cierra con el documento de Criterios de Testing de la cátedra.

## 1. Qué se prueba

Las 12 historias de usuario comprometidas en el diseño (HU-01 a HU-12, `AUR-113` a `AUR-124`) y lo que piden las entregas: el State con su 409, JWT y roles, las tres integraciones (SOAP, REST y mensajería), la resiliencia y la sugerencia de IA. El detalle de qué caso cubre qué historia está en [trazabilidad.md](trazabilidad.md).

Queda afuera de lo automatizado: Mercado Pago real y el envío real de emails (las notificaciones salen por log). El cobro y el reembolso se prueban contra el simulador del compose; aparte hay una corrida manual contra Mercado Pago real con las credenciales de prueba (`mercado-pago-real.md`, `AUR-233`): el cobro anduvo y el reembolso lo rechaza el ambiente de prueba.

## 2. Niveles y herramientas

| Nivel | Qué cubre | Con qué | Cuántos | Cómo se corre |
|---|---|---|---|---|
| Unitario | Reglas de negocio, el State, las políticas de reembolso, los clientes de integración contra simuladores, la API REST con la capa de negocio mockeada | JUnit 5 y Mockito | 389 | `mvn test` |
| Integración | Repositorios contra MySQL real y mensajería contra Artemis real | Testcontainers | 20 | `mvn verify` (con Docker) |
| Punta a punta | Los flujos completos por las pantallas y por la API, con todo levantado | Playwright y pytest | 38 | `docker compose up -d --build` y `cd e2e && pytest` |
| Accesibilidad | WCAG 2 A y AA en 360, 768 y 1280 px | axe | 3 (6 pantallas cada uno) | dentro de los E2E |
| Mutación | Que los tests del dominio detecten cambios en el código | PIT | 368 mutantes | `mvn test-compile org.pitest:pitest-maven:mutationCoverage` |
| Estático | Bugs y fallas de seguridad en el código | SpotBugs con FindSecBugs | | `mvn compile spotbugs:check` |
| Seguridad dinámica | La app corriendo, sin sesión y logueado como cliente y como moderador | OWASP ZAP pasivo y activo | | `seguridad/zap.sh` y `seguridad/zap-activo.sh` |
| Carga | 50 usuarios durante 5 minutos | k6 | | `carga/correr.sh` |
| Secretos y dependencias | Todo el historial del repo y las librerías | gitleaks y Trivy | | en el CI |

## 3. Criterios de aceptación

| Qué | Umbral | Hoy |
|---|---|---|
| Tests | todos pasan | 391 unitarios, 20 de integración y 39 E2E pasan |
| Cobertura de la capa de negocio (`negocio` y `modelo`) | 80 % de líneas y 70 % de ramas; si no, falla el build | negocio 97 % y 93 %; modelo 92 % y 97 % |
| Cobertura de la API (`api`) | sin umbral propio | 94 % de líneas y 92 % de ramas |
| Mutación | 60 % | 94 % (347 de 368) |
| SpotBugs | 0 hallazgos sin justificar | 0 |
| ZAP | 0 High y cada Medium justificado | pasa en el CI sobre las pantallas (pasivo y activo); falta sumar la API al escaneo |
| Carga | p95 menor a 500 ms y menos de 1 % de errores | pendiente de volver a correr en esta rama |
| Defectos | ninguno Bloqueante ni Crítico abierto | 21 encontrados (DEF-01 a DEF-21), todos cerrados |

Los números de "Hoy" son de la corrida del 11/11 sobre la rama `entrega-2`. Las pantallas JSF (`presentacion`) no entran en la cobertura unitaria: se prueban de punta a punta.

## 4. Ambiente

Un solo comando levanta lo mismo en la máquina de cualquiera y en el CI: `docker compose up --build`. Son la app en WildFly 37, MySQL 8.4 con `sql/schema.sql`, el broker Artemis aparte (para poder apagarlo) y tres simuladores: Mercado Pago, el proveedor SOAP y el modelo de IA. Cada simulador tiene modos `lento`, `error` y `roto` para forzar las fallas.

Usuarios de prueba: `ana`, `carlos`, `lucia` y `javier` (clientes) y `mod` (moderador), todos `@mail.com` y con la clave `Clave1234`.

## 5. Quién prueba qué

La revisión de los tests por integrante está en [../REVISION.md](../REVISION.md). Cada uno corre los suyos, rompe el código una vez para ver que el test falla y sube su commit.

## 6. Defectos

Se cargan en Jira como tipo Error con la etiqueta `defecto`, con pasos para reproducir, severidad y el test que lo cubre una vez arreglado. Un defecto no se cierra sin un test que falle antes del arreglo y pase después.

## 7. Riesgos

| Riesgo | Qué hacemos |
|---|---|
| Los E2E dependen de tiempos (broker, redirecciones) y pueden fallar de vez en cuando | Workflow `inestables.yml`: corre la suite 3 veces en paralelo |
| El último E2E bloquea la IP 5 minutos | Va último (`test_9`); para repetir, `docker compose restart app` |
| La IA real cuesta por llamada y no es determinística | Los tests usan el modelo simulado; el set de 20 casos contra el modelo real se corre a mano antes de la final |
| El escaneo activo de ZAP cierra la sesión y prueba el login hasta disparar el bloqueo por fuerza bruta | El escaneo se pone en pausa, espera a que el bloqueo se libere y sigue. La app no se toca |
| Maven Central a veces responde 502 en el CI | Un reintento del build antes de darlo por fallado |
