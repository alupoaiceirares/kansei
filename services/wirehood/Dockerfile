# Build context for this Dockerfile is services/ (the parent folder) NOT wirehood/ itself - wirehood's pom.xml depends on the parent pom.xml one level up, so Docker needs to see both
# Build from services/ with: docker build -f wirehood/Dockerfile -t kansei-wirehood .
# To run since env is missing:  docker run --env-file .env -p 8082:8082 kansei-wirehood

# ---- Stage 1: build the jar ----
FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /build

# Copy only the POMs first - this layer only gets invalidated (re-run) when a pom.xml changes, not on every source code change, so dependency downloads get cached across rebuilds
COPY pom.xml ./pom.xml
COPY wirehood/pom.xml ./wirehood/pom.xml
RUN mvn -f wirehood/pom.xml dependency:go-offline -B

# Copy actual source and build
COPY wirehood/src ./wirehood/src
RUN mvn -f wirehood/pom.xml clean package -DskipTests -B

# ---- Stage 2: run ----
# JRE only (not JDK) - smaller image, no compiler needed to just run a jar
FROM eclipse-temurin:25-jre AS run
WORKDIR /app

# yt-dlp/FFmpeg - search and download both shell out to a bare "yt-dlp" command via ProcessBuilder, FFmpeg is invoked by yt-dlp itself, not called directly the code, both just need to be on PATH
# Standalone yt-dlp binary, not pip - avoids the PEP 668 --break-system-packages mess entirely, no Python needed either
# wget kept (not purged, unlike curl), needed at runtime by the HEALTHCHECK below, not just at build time
RUN apt-get update \
    && apt-get install -y --no-install-recommends ffmpeg curl wget \
    && curl -L https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp_linux -o /usr/local/bin/yt-dlp \
    && chmod a+rx /usr/local/bin/yt-dlp \
    && apt-get purge -y curl \
    && apt-get autoremove -y \
    && rm -rf /var/lib/apt/lists/*

COPY --from=build /build/wirehood/target/wirehood-*.jar app.jar

EXPOSE 8082
HEALTHCHECK --interval=10s --timeout=5s --retries=5 --start-period=20s \
    CMD wget --spider -q http://localhost:8082/actuator/health || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]
