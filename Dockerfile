# Multi-stage build so the deliverable runs from a clean checkout with no
# local Java/Maven installation — `docker compose up` is genuinely one command.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# Copy the POM first so the dependency layer is cached independently of source
# changes — rebuilds after a code edit skip the dependency download entirely.
COPY pom.xml .
RUN mvn -B dependency:go-offline

COPY src ./src
RUN mvn -B clean package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app

# Run as a non-root user — containers that run as root are a needless
# privilege-escalation surface if the process is ever compromised.
RUN useradd --system --uid 1001 --create-home appuser
USER appuser

COPY --from=build /build/target/fund-api-1.0.0.jar app.jar

EXPOSE 8083
ENTRYPOINT ["java", "-jar", "app.jar"]
