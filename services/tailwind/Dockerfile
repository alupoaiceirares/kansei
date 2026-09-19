# Build context for this Dockerfile is services/ (the parent folder) NOT tailwind/ itself, tailwind's pom.xml depends on the parent pom.xml one level up, so Docker needs to see both.
# Build from services/ with: docker build -f tailwind/Dockerfile -t kansei-tailwind .
# To run since env is missing we might need:  docker run --env-file .env -p 8084:8084 kansei-tailwind

# ---- Stage 1: build the jar ----
FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /build

# Copy only the POMs first, this layer only gets invalidated when a pom.xml changes, not on every source change, so dependency downloads get cached across rebuilds.
COPY pom.xml ./pom.xml
COPY tailwind/pom.xml ./tailwind/pom.xml
RUN mvn -f tailwind/pom.xml dependency:go-offline -B

# Copy actual source and build.
COPY tailwind/src ./tailwind/src
RUN mvn -f tailwind/pom.xml clean package -DskipTests -B

# ---- Stage 2: run ----
# JRE only (not JDK), smaller image, no compiler needed to just run a jar.
FROM eclipse-temurin:25-jre AS run
WORKDIR /app

# wget only, kept (not purged), needed at runtime by the HEALTHCHECK below, not just at build time
RUN apt-get update \
    && apt-get install -y --no-install-recommends wget \
    && rm -rf /var/lib/apt/lists/*

COPY --from=build /build/tailwind/target/tailwind-*.jar app.jar

EXPOSE 8084
HEALTHCHECK --interval=10s --timeout=5s --retries=5 --start-period=20s \
    CMD wget --spider -q http://localhost:8084/actuator/health || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]
