# Etapa 1: build. Se descarta al final — nunca llega a producción con Maven ni el JDK completo.
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
# Descarga de dependencias en su propia capa: solo se repite si pom.xml cambia,
# no en cada cambio de código fuente (cache de capas de Docker).
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn package -DskipTests -B

# Etapa 2: runtime. JRE (no JDK) sobre Alpine: no necesitamos compilador en producción.
FROM eclipse-temurin:17-jre-alpine
RUN addgroup -S mediflow && adduser -S mediflow -G mediflow
WORKDIR /app
COPY --from=build /app/target/mediflow-api.jar app.jar
USER mediflow
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=3s --start-period=30s --retries=3 \
    CMD wget -q -O- http://localhost:8080/actuator/health | grep -q '"status":"UP"' || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]
