# Build context is the repo root (see docker-compose.yml): the pom bundles ../sample-data/expenses.csv into the jar.
# --- build stage: Maven on JDK 21 ---
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src/backend
COPY backend/pom.xml .
RUN mvn -q -B dependency:go-offline
COPY backend/src ./src
COPY sample-data/expenses.csv /src/sample-data/expenses.csv
# Tests need a Postgres test DB; they run outside Docker (./mvnw test).
RUN mvn -q -B package -DskipTests \
 && cp target/expense-manager-*.jar /app.jar

# --- runtime stage: slim JRE 21 ---
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S app && adduser -S app -G app
USER app
WORKDIR /app
COPY --from=build /app.jar app.jar
EXPOSE 8080
# DB_URL, DB_USER, DB_PASSWORD, CORS_ORIGINS (and optionally SERVER_PORT) come from the environment.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
