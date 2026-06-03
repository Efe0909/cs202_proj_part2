# ── Stage 1: build ────────────────────────────────────────────────────────────
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app

# Cache dependencies (backend profile: skips JavaFX, excludes ui sources)
COPY pom.xml .
RUN mvn dependency:go-offline -P backend -q

# Build the jar
COPY src/ src/
RUN mvn package -P backend -DskipTests -q

# ── Stage 2: run ──────────────────────────────────────────────────────────────
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar

RUN mkdir -p /app/logs

# Tells Main.java not to launch the JavaFX window (also used by BackendApplication)
ENV HEADLESS=true \
    LANG=C.UTF-8 \
    LC_ALL=C.UTF-8 \
    TZ=UTC

EXPOSE 8080
ENTRYPOINT ["java", \
    "-Duser.language=en", \
    "-Duser.country=US", \
    "-Duser.timezone=UTC", \
    "-Dfile.encoding=UTF-8", \
    "-jar", "app.jar"]
