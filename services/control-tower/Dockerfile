# Build context for this Dockerfile is services/ (the parent folder) NOT control-tower/ itself - control-tower's pom.xml depends on the parent pom.xml one level up, so Docker needs to see both
# Build from services/ with: docker build -f control-tower/Dockerfile -t kansei-control-tower .
# To run since env is missing we might need:  docker run --env-file .env -p 8080:8080 kansei-control-tower

# ---- Stage 1: build the jar ----
FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /build

# Copy only the POMs first - this layer only gets invalidated (re-run) when a pom.xml changes, not on every source code change, so dependency downloads get cached across rebuilds
COPY pom.xml ./pom.xml
COPY control-tower/pom.xml ./control-tower/pom.xml
RUN mvn -f control-tower/pom.xml dependency:go-offline -B

# Copy actual source and build
COPY control-tower/src ./control-tower/src
RUN mvn -f control-tower/pom.xml clean package -DskipTests -B

# ---- Stage 2: run ----
# JRE only (not JDK) - smaller image, no compiler needed to just run a jar
FROM eclipse-temurin:25-jre AS run
WORKDIR /app

# wget only, kept (not purged), needed at runtime by the HEALTHCHECK below, not just at build time
RUN apt-get update \
    && apt-get install -y --no-install-recommends wget \
    && rm -rf /var/lib/apt/lists/*

COPY --from=build /build/control-tower/target/control-tower-*.jar app.jar

EXPOSE 8080
HEALTHCHECK --interval=10s --timeout=5s --retries=5 --start-period=20s \
    CMD wget --spider -q http://localhost:8080/actuator/health || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]
