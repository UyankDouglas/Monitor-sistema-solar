# ---------- build ----------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace

# Cache de dependências: copia só o pom primeiro
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

# Compila e empacota (testes rodam no pipeline de CI, não na imagem)
COPY src ./src
RUN mvn -B -q clean package -DskipTests

# ---------- runtime ----------
FROM eclipse-temurin:21-jre AS runtime
WORKDIR /app
ENV JAVA_OPTS=""
COPY --from=build /workspace/target/app.jar app.jar
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
