# BandUp production image: build the jar with the JDK, run it on a small JRE as a non-root user.
# Build:  docker build -t bandup .
# Run:    see deploy/compose.yaml (needs the variables from deploy/.env).

FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY gradlew settings.gradle build.gradle lombok.config ./
COPY gradle gradle
RUN chmod +x gradlew && ./gradlew --no-daemon -q dependencies > /dev/null
COPY src/main src/main
RUN ./gradlew --no-daemon -q bootJar \
    && cp "$(ls build/libs/*.jar | grep -v -- '-plain.jar')" /src/app.jar

FROM eclipse-temurin:21-jre
RUN groupadd --system bandup && useradd --system --gid bandup --home /app bandup \
    && mkdir -p /app/data/media && chown -R bandup:bandup /app
WORKDIR /app
COPY --from=build --chown=bandup:bandup /src/app.jar app.jar
USER bandup
ENV SPRING_PROFILES_ACTIVE=prod \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
EXPOSE 8080
# Uploaded cover images live here: mount a volume so they survive new versions.
VOLUME /app/data/media
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
