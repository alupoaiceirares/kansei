# Build context for this Dockerfile is services/ (the parent folder) NOT auth-service/ itself - auth-service's pom.xml depends on the parent pom.xml one level up, so Docker needs to see both.
# Build from services/ with: docker build -f auth-service/Dockerfile -t kansei-auth-service .
# To run since env is missing we might need:  docker run --env-file .env -p 8081:8081 kansei-auth-service

# ---- Stage 1: build the jar ----
FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /build

# Copy only the POMs first - this layer only gets invalidated (re-run) when a pom.xml changes, not on every source code change, so dependency downloads get cached across rebuilds.
COPY pom.xml ./pom.xml
COPY auth-service/pom.xml ./auth-service/pom.xml
RUN mvn -f auth-service/pom.xml dependency:go-offline -B

# Copy actual source and build.
COPY auth-service/src ./auth-service/src
RUN mvn -f auth-service/pom.xml clean package -DskipTests -B

# ---- Stage 2: run ----
# JRE only (not JDK) - smaller image, no compiler needed to just run a jar.
FROM eclipse-temurin:25-jre AS run
WORKDIR /app
COPY --from=build /build/auth-service/target/auth-service-*.jar app.jar

EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]