# Declaración de uso de IA generativa

Grupo 3 · Aura Cycles · versión final, 26/11/2026

## 1. Qué usamos

| Herramienta | Versión | Para qué |
|---|---|---|
| Claude (Anthropic), en claude.ai | Opus 5.5 en las últimas semanas; antes, la versión que ofrecía claude.ai en cada momento | Código, tests, simuladores, workflows del CI, borradores de documentación y casos de prueba de Jira |
| Ninguna otra | | No usamos Copilot, Postbot ni la IA de Sonar o Snyk |

## 2. Para qué la usamos

Como asistente de programación y de testing. Cada cosa que salió de una conversación con la IA la revisó, la corrió y la subió un integrante, que responde por ella en la defensa.

- Código: ayuda para escribir y revisar partes de la app (integraciones con Mercado Pago y el proveedor SOAP, mensajería, la API REST con JWT) y para buscar errores.
- Tests: propuestas de casos unitarios, de integración, de punta a punta, de contrato y de la colección Postman, los simuladores de los sistemas externos y los scripts de ZAP y k6.
- IA del producto: el diseño del gateway que sugiere aprobar, revisar o rechazar reseñas, y el set de 20 casos para evaluarlo (`src/test/resources/ia/evaluacion.tsv`).
- Documentación: borradores de ADR, plan de pruebas, matriz de trazabilidad y manual de usuario, que después corregimos.

## 3. Prompts representativos

- "Revisá ServicioDeDevoluciones y decime qué casos de borde no tienen test. Verificalo en el código, no en el README."
- "Armá un set de 20 casos para evaluar la IA de moderación: normales, difíciles y adversariales, con la salida esperada y un umbral."
- "Agregá un endpoint de salud que pruebe la base y el broker de verdad, con su caso de error en el contrato."
- "Corré la colección de Postman dos veces seguidas sobre la misma base y fijate que no dependa del orden."

## 4. Qué aceptamos, qué corregimos y cómo lo verificamos

Nada se dio por bueno porque lo dijera la IA. Cada resultado se verificó corriendo el test o el comando que figura en el README, o mirando la corrida del CI. Casos donde la IA se equivocó y lo detectamos:

| Qué propuso la IA | Cómo lo detectamos | Qué se hizo |
|---|---|---|
| Un chequeo de salud que daba "broker UP" con Artemis apagado: abría una conexión del pool, que devuelve una vieja aunque el broker no esté | El caso de error de `/salud` en el contrato: con el broker apagado esperaba 503 y daba 200 | El chequeo crea una cola temporal, que obliga a hablar con el broker. Test `siElBrokerNoContestaAlCrearLaColaTemporalEstaCaido` |
| Una colección Postman que tomaba el ETag apenas aprobada la devolución | Falló en el CI con 412: los consumidores del tópico cambiaban la devolución después | Espera a que terminen los consumidores antes de tomar el ETag. Pasa dos veces seguidas sobre la misma base |
| Informar 391 tests unitarios | La corrección contó 268 métodos y 29 parametrizados | Las dos cifras salen de una misma corrida y se explica cómo se cuentan |
| Dar por buena la moderación con el modelo simulado | El set de evaluación: aprobaba un intento de sacar el prompt, un JSON falso, un email escrito con "arroba" y una consulta médica | Barrera en el gateway: si el texto trae órdenes para el asistente, nunca sale "aprobar"; enmascarado del email escrito con palabras (DEF-23) |

## 5. Qué hicimos nosotros

- Elegir el dominio, el alcance y el stack, y las decisiones de los ADR.
- Decirle a la IA qué no hacer cuando proponía de más. El criterio acordado con la cátedra fue implementar solo lo que el problema necesita.
- Leer, correr y romper a propósito lo generado: cada integrante tiene una parte del sistema y responde por su código y sus tests.
- Los bocetos de las pantallas y el Figma.

## 6. Qué no afirmamos

Ningún "cumple" de este repositorio es una afirmación de la IA: cada uno se verifica con un test ejecutado o con un reporte del CI.

La moderación con IA, en la demo con `docker compose`, corre contra un **modelo simulado** que clasifica por palabras clave. Con una clave en `IA_API_KEY` usa el modelo real (ADR-007). Con el simulado, el set de evaluación prueba el gateway: el enmascarado, la barrera contra inyecciones, el contrato de la respuesta, el timeout y el fallback. La calidad del modelo real se mide corriendo el mismo set con `IA_EVAL_REAL=true`.

## 7. IA por usar

| Qué | Objetivo medible | Qué riesgo controla |
|---|---|---|
| Correr el set de evaluación contra el modelo real en cada cambio del prompt | 80 % de aciertos y 0 aprobaciones indebidas, igual que con el simulado | Que un cambio en el prompt o en el modelo empeore la moderación sin que nadie lo note |
| Generar casos de prueba a partir de los criterios de aceptación de cada HU | Todo criterio nuevo con al menos un caso negativo antes de pasar a "Listo" | Criterios sin probar |
| Comparación visual de pantallas con capturas de Playwright | 0 diferencias no esperadas en 360, 768 y 1280 px | Roturas de diseño que axe no detecta |
