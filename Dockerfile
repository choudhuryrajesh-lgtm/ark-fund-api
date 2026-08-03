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

# New Relic's Java APM agent — its own stage so the final runtime image
# doesn't need curl/unzip installed just to fetch it once. Opt-in via a
# build arg (default off): a plain `docker compose up --build` for local
# evaluation should never depend on downloading ~38MB from New Relic's CDN
# just to produce an inert file nothing local uses — see docker-compose.yml,
# which never sets JAVA_TOOL_OPTIONS. terraform/scripts/deploy-backend.sh
# passes --build-arg INCLUDE_NEW_RELIC=true for the environments that
# actually configure a license key (see terraform/modules/ecs).
FROM alpine:3.20 AS newrelic-agent
ARG INCLUDE_NEW_RELIC=false
RUN mkdir -p /newrelic && if [ "$INCLUDE_NEW_RELIC" = "true" ]; then \
    apk add --no-cache curl unzip \
    && curl -sSL -o /tmp/newrelic-java.zip https://download.newrelic.com/newrelic/java-agent/newrelic-agent/current/newrelic-java.zip \
    && unzip -q /tmp/newrelic-java.zip -d /tmp \
    && mv /tmp/newrelic/* /newrelic/ ; \
    fi

FROM eclipse-temurin:21-jre
WORKDIR /app

# Run as a non-root user — containers that run as root are a needless
# privilege-escalation surface if the process is ever compromised.
RUN useradd --system --uid 1001 --create-home appuser
USER appuser

COPY --from=build /build/target/fund-api-1.0.0.jar app.jar
COPY --from=newrelic-agent /newrelic /app/newrelic

EXPOSE 8083
ENTRYPOINT ["java", "-jar", "app.jar"]
