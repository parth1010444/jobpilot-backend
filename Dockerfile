# syntax=docker/dockerfile:1

FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /workspace

COPY gradlew settings.gradle.kts build.gradle.kts gradle.properties ./
COPY gradle ./gradle
COPY src ./src

RUN chmod +x gradlew \
    && ./gradlew bootJar --no-daemon -x test

FROM eclipse-temurin:21-jre-jammy AS runtime
WORKDIR /app

RUN groupadd --system jobpilot \
    && useradd --system --gid jobpilot --home-dir /app --shell /usr/sbin/nologin jobpilot

COPY --from=build /workspace/build/libs/*.jar /app/app.jar

USER jobpilot
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
