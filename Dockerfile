# Etapa 1: compila el WAR y baja el driver de MySQL
FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /src
COPY pom.xml checkstyle.xml ./
# Maven Central a veces responde 429 a los runners del CI: se reintenta con espera creciente
RUN for i in 1 2 3 4 5; do mvn -q -B org.apache.maven.plugins:maven-dependency-plugin:3.8.1:go-offline && exit 0; sleep $((i * 15)); done; exit 1
COPY src src
RUN mvn -q -B package -DskipTests \
 && mvn -q -B org.apache.maven.plugins:maven-dependency-plugin:3.8.1:copy -Dartifact=com.mysql:mysql-connector-j:9.1.0 -DoutputDirectory=/drivers

# Etapa 2: WildFly 39 (Jakarta EE 10) configurado con docker/wildfly/configurar.cli
FROM quay.io/wildfly/wildfly:39.0.1.Final-2-jdk21
COPY --from=build --chown=jboss:root /drivers/mysql-connector-j-9.1.0.jar /tmp/mysql-connector-j.jar
COPY --chown=jboss:root docker/wildfly/configurar.cli /tmp/configurar.cli
RUN /opt/jboss/wildfly/bin/jboss-cli.sh --file=/tmp/configurar.cli \
 && rm -rf /opt/jboss/wildfly/standalone/configuration/standalone_xml_history /tmp/mysql-connector-j.jar
COPY --from=build --chown=jboss:root /src/target/repuestos.war /opt/jboss/wildfly/standalone/deployments/
EXPOSE 8080 8443
CMD ["/opt/jboss/wildfly/bin/standalone.sh", "-c", "standalone-full.xml", "-b", "0.0.0.0"]
