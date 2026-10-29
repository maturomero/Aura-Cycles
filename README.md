# Aura Cycles — Reseñas y Devoluciones de Repuestos de Bicicleta

TP Integrador · Desarrollo de Aplicaciones II · UADE · Grupo 3

## Levantar todo con un comando

Solo hace falta **Docker** (Docker Desktop en Windows o Mac). Desde la carpeta del repo:

```bash
docker compose up --build
```

La primera vez tarda unos minutos (baja las imágenes y compila con Maven adentro del contenedor). Cuando en la consola aparece `WFLYSRV0025: WildFly ... started`, está listo:

| Qué | Dónde |
|---|---|
| La tienda | https://localhost:8443/repuestos (http://localhost:8080 redirige) |
| Mercado Pago simulado (modos para la demo) | http://localhost:8089/__modo |
| Proveedor SOAP simulado (WSDL y modos) | http://localhost:8090/proveedor?wsdl · http://localhost:8090/__modo |
| Consola del broker Artemis | http://localhost:8161 |

El certificado es autofirmado (lo genera WildFly), así que la primera vez el navegador avisa: "Avanzado" > "Continuar a localhost".

Usuarios de prueba, todos con la clave `Clave1234`: `ana@mail.com`, `carlos@mail.com`, `lucia@mail.com`, `javier@mail.com` (clientes) y `mod@mail.com` (moderador).

Qué levanta: WildFly 37 con la app, MySQL 8.4 con `sql/schema.sql`, Artemis aparte (así se puede apagar en la demo: `docker compose stop artemis` / `docker compose start artemis`) y los dos sistemas externos simulados. Para empezar de cero: `docker compose down -v`.

Para desarrollar se sigue usando Eclipse como siempre (secciones de abajo). Sin configurar nada, la app desde Eclipse usa los simulados en `localhost:8089` y `localhost:8090`: se levantan con Run As > Java Application sobre `MockMercadoPago.java` y `MockProveedorSoap.java` (en `src/test/java`).

Arquitectura en capas sobre Jakarta EE 10, según el diagrama de capas del grupo.

| Capa | Tecnología | Paquete |
|---|---|---|
| Presentación | JSF (Jakarta Faces 4.0) + CDI | `presentacion/` + `webapp/*.xhtml` |
| Negocio | EJB (Jakarta Enterprise Beans) | `negocio/` |
| Datos | JPA / Hibernate + MySQL | `datos/` + `modelo/` |

---

## 1. Qué hace falta instalar

| Herramienta | Versión | Nota |
|---|---|---|
| JDK | 17 o 21 | El proyecto compila con `release 17` |
| WildFly | variante **EE 10** | Desde WildFly 40 el estándar pasó a EE 11 y eliminó `@ManagedBean`. La variante EE 10 evita sorpresas |
| MySQL | 8.x | Más MySQL Workbench para correr el schema |
| Eclipse | IDE **for Enterprise Java and Web Developers** | La edición "Java Developers" pelada no trae herramientas de servidor |
| JBoss Tools | desde el Eclipse Marketplace | Arranca y para WildFly desde el IDE |

Maven ya viene embebido en Eclipse.

---

## 2. Crear la base

Abrí `sql/schema.sql` en MySQL Workbench y ejecutalo entero. Crea el esquema
y carga datos de prueba: 8 repuestos, 5 usuarios, 8 compras, 4 reseñas y
1 devolución pendiente.

**Todos los usuarios tienen la contraseña `Clave1234`:**

| Email | Rol |
|---|---|
| `ana@mail.com` | CLIENTE |
| `carlos@mail.com` | CLIENTE |
| `lucia@mail.com` | CLIENTE |
| `javier@mail.com` | CLIENTE |
| `mod@mail.com` | **MODERADOR** |

---

## 3. Conectar MySQL a WildFly

Esto es lo que le come la tarde a todo el mundo, así que seguilo tal cual.

Bajá el `.jar` del driver `mysql-connector-j`. Levantá WildFly en una terminal:

```bash
cd WILDFLY_HOME/bin
./standalone.sh
```

En **otra** terminal:

```bash
cd WILDFLY_HOME/bin
./jboss-cli.sh --connect
```

Y adentro del CLI, en este orden:

```
module add --name=com.mysql --resources=/ruta/al/mysql-connector-j-9.x.x.jar --dependencies=java.sql

/subsystem=datasources/jdbc-driver=mysql:add(driver-name=mysql,driver-module-name=com.mysql,driver-xa-datasource-class-name=com.mysql.cj.jdbc.MysqlXADataSource)

data-source add --name=RepuestosDS --jndi-name=java:/RepuestosDS --driver-name=mysql --connection-url=jdbc:mysql://localhost:3306/repuestos_bici?serverTimezone=America/Argentina/Buenos_Aires --user-name=root --password=TU_PASSWORD --use-ccm=true --min-pool-size=5 --max-pool-size=20

data-source test-connection-in-pool --name=RepuestosDS
```

El último comando tiene que devolver `true`. Si devuelve error, el problema
está en el usuario, la contraseña o en que MySQL no está levantado — no en
la aplicación.

El nombre JNDI `java:/RepuestosDS` es el mismo que figura en
`persistence.xml`. Si lo cambiás, cambialo en los dos lados.

---

## 4. Configurar la seguridad declarativa

El sistema usa `@RolesAllowed("MODERADOR")` en las operaciones sensibles de
`ServicioDeModeracion`. Para que el contenedor pueda aplicarlo hace falta un
security domain. En el CLI:

```
/subsystem=elytron/jdbc-realm=repuestos-realm:add(principal-query=[{sql="SELECT password_hash, rol FROM usuario WHERE email = ?",data-source=RepuestosDS,modular-crypt-mapper={password-index=1},attribute-mapping=[{index=2,to=Roles}]}])

/subsystem=elytron/security-domain=repuestos-domain:add(default-realm=repuestos-realm,permission-mapper=default-permission-mapper,realms=[{realm=repuestos-realm}])

/subsystem=undertow/application-security-domain=repuestos-domain:add(security-domain=repuestos-domain)

/subsystem=ejb3/application-security-domain=repuestos-domain:add(security-domain=repuestos-domain)

reload
```

> **Para la defensa oral.** `PasswordHash` guarda bcrypt con costo 12 en el
> formato estándar `$2a$12$...` (sal y costo van adentro del mismo texto).
> Elytron lo entiende con el `modular-crypt-mapper`, así la aplicación y el
> contenedor validan el mismo hash. bcrypt es lento a propósito: unas 250 ms
> por intento, contra millones por segundo con un SHA-256 de una pasada.
>
> Si el realm ya estaba creado, no hace falta borrarlo: `configurar-entrega4.cli`
> le cambia la consulta con `write-attribute` y hace `reload`.

---

## 5. Importar en Eclipse

1. `File → Import → Maven → Existing Maven Projects`
2. Elegí la carpeta `repuestos-bici`
3. `Window → Show View → Servers → New Server → JBoss Community → WildFly`
4. Apuntá al directorio de WildFly y al JDK
5. Botón derecho sobre el servidor → `Add and Remove` → agregá `repuestos-bici`
6. `Start`

O por línea de comandos:

```bash
mvn clean package
cp target/repuestos.war WILDFLY_HOME/standalone/deployments/
```

La app queda en **http://localhost:8080/repuestos/login.xhtml**

---

## 6. Recorrido para la demo

Este es el orden que conviene mostrar en la defensa, porque recorre el caso
de uso resaltado del diagrama de punta a punta.

1. Entrá como `ana@mail.com`
2. **Productos** → abrí "Cubierta MTB 29 × 2.10" → mirá las reseñas aprobadas
3. **Mis compras** → "Solicitar devolución" en la orden más reciente
4. Elegí un motivo, escribí un comentario, enviá → sale el código `DEV-000002`
5. Cerrá sesión, entrá como `mod@mail.com`
6. **Moderación** → solapa Devoluciones → Aprobar
7. Mirá la consola de WildFly: aparecen los avisos de
   `ServicioDeNotificaciones`, y el stock del repuesto volvió a subir

Probá también el botón **Tema oscuro** de la barra superior, y achicá la
ventana a menos de 640 px para ver la barra lateral convertida en menú
hamburguesa y las tablas en tarjetas apiladas.

---

## 7. Cómo se mapea contra el checklist del TP

| Requisito | Dónde está |
|---|---|
| Mínimo 6 componentes de negocio | 8 servicios en `negocio/` |
| 1 stateful + 1 stateless justificados | `ServicioDeDevoluciones` es `@Stateful`; los otros 7 `@Stateless` |
| Arquitectura en capas explícita | `presentacion/` → `negocio/` → `datos/`, sin saltos |
| 3 patrones de diseño | DAO (repositorios), Facade (`ServicioDeModeracion`), Strategy (`ServicioDeReembolsos.calcularMonto`) |
| Seguridad declarativa en 2+ operaciones | `@RolesAllowed` en 6 métodos de `ServicioDeModeracion` |
| Transacciones declarativas en flujo crítico | `aprobarDevolucion()`: 6 pasos en una transacción |
| Diseño responsive | Tres breakpoints en `app.css` |
| Temas claro y oscuro (punto extra) | `SesionBean.alternarTema()`, en vivo |

| Mensajería pub/sub | Tópico `topico.devoluciones` con tres suscriptores durables en `mensajeria/` |
| Integración REST con sistema externo | Mercado Pago (cobro y reembolso), `integracion/ClienteMercadoPago` |
| Integración SOAP con WSDL | Proveedor mayorista, `validarGarantia` (`integracion/ClienteProveedor`, AUR-32) |

---

## 7 bis. Mercado Pago

La compra reserva el stock y queda `PENDIENTE_PAGO`. El cliente paga en
Checkout Pro y, al volver, `ServicioDeCobros` le pregunta a Mercado Pago el
estado real del pago; lo que dice la URL de vuelta no se cree. Si Mercado
Pago no responde en 5 s, la compra queda reservada 30 minutos y se puede
pagar desde Mis compras. Las decisiones están en `docs/adr/ADR-005`.

Se configura con VM arguments del servidor en Eclipse (`-DMP_ACCESS_TOKEN=...`)
o variables de entorno. Sin token, la app usa el Mercado Pago simulado.

| Variable | Para qué | Por defecto |
|---|---|---|
| `MP_ACCESS_TOKEN` | Access Token de prueba. **Nunca en el repo** | — (usa el simulado) |
| `MP_BASE_URL` | API de Mercado Pago o el simulado | con token `https://api.mercadopago.com`, sin token `http://localhost:8089` |
| `AURA_URL` | Dirección de la tienda para la vuelta | `http://localhost:8080/repuestos` |

El simulado solo usa el JDK. En Eclipse: `MockMercadoPago.java` (en
`src/test/java`) > Run As > Java Application. Fuera de Eclipse:

```bash
java src/test/java/ar/edu/uade/repuestos/integracion/MockMercadoPago.java 8089
```

En `http://localhost:8089/__modo` se lo pone lento, con error 500 o con
respuestas rotas, para mostrar la resiliencia en vivo. En el compose:

```yaml
  mercadopago-mock:
    image: eclipse-temurin:21-jdk
    command: java /mock/MockMercadoPago.java 8089
    environment:
      MOCK_URL_PUBLICA: http://localhost:8089
    volumes:
      - ./src/test/java/ar/edu/uade/repuestos/integracion:/mock:ro
    ports:
      - "8089:8089"
```

Y en el servicio de WildFly: `MP_BASE_URL=http://mercadopago-mock:8089`.

---

## 8. Las tres preguntas que te van a hacer

**¿Por qué `ServicioDeDevoluciones` es stateful y el resto no?**
Porque pedir una devolución es un asistente de tres pasos: elegís la compra,
cargás el motivo, confirmás. Entre paso y paso hay un borrador que tiene que
sobrevivir. Si fuera stateless habría que guardarlo en la sesión HTTP, que es
meter estado de negocio en la capa de presentación — el anti-patrón de la
diapositiva 36. Los otros siete resuelven cada operación en una sola llamada.

**¿Por qué Moderación, Reembolsos y Notificaciones no tienen repositorio?**
Moderación opera sobre entidades ajenas y usa `ResenaRepository` y
`DevolucionRepository`. El monto del reembolso se guarda en la fila de
`devolucion` porque un reembolso no existe fuera de la devolución que lo
origina. Notificaciones no persiste nada, solo emite avisos. No creamos
repositorios para clases que no tienen entidad propia.

**¿Dónde está la regla de negocio?**
En `negocio/`, nunca en las pantallas. `DevolucionBean` no valida plazos ni
duplicados: llama a `ServicioDeDevoluciones.seleccionarCompra()` y ese método
decide. Comparalo con `ProductosBean.comprar()`, que tampoco chequea stock:
lo hace `ServicioDeVentas` dentro de una transacción con bloqueo pesimista.

---

## 9. Pruebas y CI (Clase 15)

| Qué | Cómo se corre | Qué exige |
|---|---|---|
| Unitarios (JUnit 5 + Mockito) | `mvn test` | |
| Integración con Testcontainers (MySQL y Artemis reales) | `mvn verify` (necesita Docker) | |
| Cobertura JaCoCo | `mvn verify`, reporte en `target/site/jacoco` | 80 % líneas y 70 % ramas en `negocio` y `modelo`, si no falla el build |
| Mutación PIT | `mvn org.pitest:pitest-maven:mutationCoverage`, reporte en `target/pit-reports` | 60 % |
| Análisis estático SpotBugs + FindSecBugs | `mvn compile spotbugs:check` | 0 hallazgos (los excluidos están justificados en `spotbugs-excluir.xml`) |
| E2E (Playwright) | `docker compose up -d --build` y después `cd e2e && pytest` | |
| DAST con ZAP | con la app levantada: `seguridad/zap.sh` | 0 High, y cada Medium justificado en `seguridad/zap-justificadas.tsv` |
| Carga con k6 | con la app levantada: `carga/correr.sh` | 50 usuarios, 5 min, p95 < 500 ms y < 1 % de errores |
| Secretos | `docker run --rm -v "$PWD:/repo" ghcr.io/gitleaks/gitleaks:v8.30.1 git /repo` | nada encontrado en todo el historial |

Los E2E dejan la IP de la máquina bloqueada 5 minutos (el último prueba fuerza bruta). Para correrlos de nuevo enseguida: `docker compose restart app`. Para arrancar con la base limpia: `docker compose down -v`.

**CI (GitHub Actions).** `.github/workflows/ci.yml` corre en cada push a `main` y `entrega-testing`, en cada PR y en los tags `v*`. Son tres trabajos en paralelo (unos 10 minutos): pruebas + cobertura + mutación + SpotBugs; gitleaks + Trivy + E2E contra el compose; y ZAP sin sesión, como cliente y como moderador. Los reportes quedan como artefactos de cada corrida.

Dos workflows más, a mano desde la pestaña Actions ("Run workflow") o empujando un tag:

- `inestables.yml` (tag `inestables-N`): corre toda la suite 3 veces en paralelo para detectar tests inestables.
- `carga.yml` (tag `carga-N`): la prueba de carga con k6.
