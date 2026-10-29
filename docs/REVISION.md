# Revisión de tests por integrante (Clase 15)

Cada uno revisa los tests de su parte, los corre y sube **su propio commit** antes del PR a `main`. En la defensa te pueden pedir que expliques cualquiera de estos tests, así que la idea es que los leas, los rompas a propósito una vez (cambiá una línea del código y fijate que el test falla) y los vuelvas a dejar bien.

Para el commit de revisión alcanza con algo chico pero real: un caso de borde más en alguno de tus tests, un nombre más claro o un comentario de una línea donde algo no se entendía. En el mensaje poné qué revisaste, por ejemplo `Reviso tests de SOAP y agrego caso de RMA vacío`.

Las tareas de Jira para revisar son las subtareas "Revisar casos de prueba ..." (AUR-92 a AUR-109). Cada caso de prueba está en Jira como subtarea de su HU (AUR-125 a AUR-195), asignado a quien lo revisa.

## Valen

| Qué | Dónde |
| --- | --- |
| SEC-03: pantallas por rol y headers | `e2e/test_4_seguridad.py` (403, headers, login del contenedor) |
| SEC-06: errores sin detalles | `e2e/test_4_seguridad.py` (404 propio, vista vencida) y `ManejadorDeVistaVencida` |
| Sesión en lugar de JWT y CORS | `e2e/test_4_seguridad.py` (cookie inventada, adulterada o cerrada; sesión nueva al ingresar; sin CORS) |
| HU y manual de usuario | documentos de HU y manual (AUR-71 y AUR-57) |
| Plan y matriz del doc de QA | secciones 1 y 2 |

Te pueden preguntar: por qué el filtro de headers corre también en FORWARD y ERROR, qué pasaba antes con un ViewState vencido y por qué no aplican las pruebas de JWT.

## Lautaro

| Qué | Dónde |
| --- | --- |
| Repositorios contra MySQL real | `src/test/java/.../datos/RepositoriosIT.java` |
| Mensajería contra Artemis real | `src/test/java/.../mensajeria/MensajeriaIT.java` |
| State de la devolución | `DevolucionTest`, `EstadoDevolucionTest` |
| Broker caído de punta a punta | `e2e/test_3_resiliencia.py` |
| Dos moderadores a la vez (DEF-20) | `e2e/test_2_devolucion.py` y `porIdBloqueado` en los repositorios |

Te pueden preguntar: qué es Testcontainers y por qué no alcanza con H2, cómo se prueba que un mensaje no se pierde con el consumidor caído, por qué el bloqueo relee el stock y qué pasaba con dos moderadores a la vez antes del bloqueo.

## Fran

| Qué | Dónde |
| --- | --- |
| SOAP y contrato WSDL | `ClienteProveedorTest`, `ContratoWsdlTest`, `ValidadorDeGarantiasTest` |
| Ventas, reseñas y usuarios | `ServicioDeVentasTest`, `ServicioDeResenasYProductosTest`, `ServicioDeUsuariosTest` |
| SEC-01, SEC-02 y SEC-04 | `PasswordHashTest`, `LimitadorDeIntentosTest`, `ServicioDeModeracionTest` |
| Análisis estático | `spotbugs-excluir.xml` (por qué se excluye cada cosa) |
| Largos máximos (DEF-21) | `ServicioDeUsuariosTest`, `ServicioDeResenasYProductosTest`, `ServicioDeDevolucionesTest` |

Te pueden preguntar: qué es un mutante que sobrevive en PIT, por qué `emailValido` no usa una regex con grupos repetidos, y qué valida `ContratoWsdlTest`.

## Matías

| Qué | Dónde |
| --- | --- |
| Cobro y reembolso con Mercado Pago | `ServicioDeCobrosTest`, `ServicioDeCobrosFlujoTest`, `ServicioDeReembolsosTest`, `ClienteMercadoPagoTest` |
| SEC-05 | `ServicioDeDevolucionesTest`, `e2e/test_2_devolucion.py` |
| E2E, accesibilidad, ZAP y carga | `e2e/`, `seguridad/zap.sh`, `seguridad/zap-activo.sh`, `carga/carga.js` |
| CI | `.github/workflows/` |

Te pueden preguntar: cómo se evita reembolsar dos veces, qué mide k6 y qué se arregló cuando dio 1,1 s, y qué hace cada trabajo del CI.

## Cómo correr lo tuyo

- Desde Eclipse: clic derecho en la clase de test > Run As > JUnit Test. Las `*IT` necesitan Docker abierto.
- Todo junto: `mvn verify` (o Run As > Maven build > `verify`).
- E2E: `docker compose up -d --build` y después `cd e2e && pytest`.
