# Declaración de uso de IA generativa

Grupo 3 · Aura Cycles · actualizada al 11/11/2026

## Qué usamos

Claude, de Anthropic, desde claude.ai, a lo largo de todo el cuatrimestre.

## Para qué lo usamos

Lo usamos mucho y lo decimos así. Con asistencia de IA se generó:

- La mayor parte del código de la aplicación: entidades, repositorios, servicios, integraciones con Mercado Pago y con el proveedor SOAP, mensajería, la API REST con JWT y las pantallas con su hoja de estilos.
- La mayor parte de los tests (unitarios, de integración y de punta a punta), los simuladores de los sistemas externos y los workflows del CI.
- Borradores de la documentación: ADR, plan de pruebas, matriz de trazabilidad, manual de usuario y este documento.
- Varias tareas y casos de prueba del Jira.

## Qué hicimos nosotros

- Elegir el dominio, el alcance y el stack, y las decisiones que están en los ADR: qué es stateful, por qué un tópico y por qué una cola, cobrar con Mercado Pago, qué quedaba afuera.
- Decirle a la IA qué no hacer cuando proponía cosas de más. El criterio acordado con la cátedra fue implementar solo lo que el problema necesita.
- Leer, correr y probar lo generado. Cada integrante revisa los tests de su parte, los hace fallar a propósito y sube su commit (ver [REVISION.md](REVISION.md)).
- Los bocetos de las pantallas y el Figma.

## Qué no afirmamos

Ningún "cumple" de este repositorio es una afirmación de la IA: cada uno se puede verificar corriendo el comando que figura al lado (README, sección de pruebas) o mirando la corrida del CI.

La sugerencia de moderación con IA, en la demo con `docker compose`, corre contra un **modelo simulado** que clasifica por palabras clave. Con una clave en `IA_API_KEY` usa el modelo real (ADR-007). Lo que mostramos en vivo con el simulado es el gateway: el enmascarado de datos, la validación del contrato, el timeout y el fallback.

## Completar antes de la entrega final

Esta declaración la firma el grupo. Antes del tag `v1.0-final` cada integrante revisa que lo que dice arriba sea cierto para su parte y agrega lo que falte.
