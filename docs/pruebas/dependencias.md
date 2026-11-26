# Dependencias y CVE

Versión 1.0 · 26/11/2026 · Grupo 3

## Dependencias de la app

Trivy sobre `pom.xml` (`--severity HIGH,CRITICAL --ignore-unfixed`) corta el CI si aparece alguna. Resultado del 26/11: **0 críticas y 0 altas**.

## Imagen de la app

Trivy sobre la imagen `aura-app` es informativo en el CI (paso "Imagen de la app"): la mayoría de lo que encuentra viene de la imagen base de WildFly (paquetes de Red Hat UBI 9 y módulos que trae el servidor), no de lo que agregamos nosotros.

| Imagen base | Críticas | Altas |
|---|---|---|
| `wildfly:37.0.1.Final-jdk21` (hasta el 26/11) | 9 | 160 |
| `wildfly:39.0.1.Final-2-jdk21` (desde el 26/11) | 7 | 140 |

Pasamos a WildFly 39.0.1: corrige la crítica de Undertow (CVE-2025-12543), que es el servidor HTTP que atiende a los usuarios. Probamos también WildFly 42.0.0, que corrige la de Netty, pero 6 E2E de mensajería y de la cookie de sesión fallan; queda para después de la entrega.

Las 7 críticas que quedan y por qué no se pueden explotar en esta app:

| CVE | Componente | Qué es | Mitigación |
|---|---|---|---|
| CVE-2026-114406 | `ca-certificates` (sistema) | Certificados intermedios de eMudhra que hay que dejar de confiar | La app solo llama al proveedor SOAP y a la IA dentro de la red del compose y a Mercado Pago, que no usa esa CA. Se corrige al actualizar la imagen base |
| CVE-2026-75595 | `netty-handler` 4.1.130 | Salteo del ruteo por SNI con un ClientHello partido | Netty lo usa el cliente de Artemis para hablar con el broker dentro de la red del compose; no hay ruteo por SNI. Corregido en WildFly 42 |
| CVE-2026-27446 | `artemis-server` 2.44 | Falla en el broker embebido de WildFly | La app usa el broker Artemis aparte; el embebido del perfil full no tiene puertos publicados fuera del contenedor. No hay versión corregida |
| CVE-2026-49875 | `cxf-core` 4.0.10 | Fuga de información por entidades externas | CXF es el subsistema de web services de WildFly; la app no publica servicios SOAP. El cliente SOAP propio parsea con DTD deshabilitado (CP-23) |
| CVE-2025-14813, CVE-2026-8763 | `bcprov` 1.83 | Fallas en algoritmos de Bouncy Castle (GOST y otros) | La app no usa Bouncy Castle: las claves se hashean con jBCrypt y el JWT se firma con nimbus (HMAC del JDK) |

Las altas siguen el mismo criterio: son del sistema base o de módulos del servidor que la app no expone. Se revisan en cada actualización de la imagen base.
