# syntax=docker/dockerfile:1

########## BUILD ##########
FROM gradle:jdk23 AS build
USER root
WORKDIR /app

# Cache dependencies: metadata first, then source — source edits don't re-download deps.
COPY build.gradle settings.gradle ./
RUN gradle --no-daemon dependencies -q || true

COPY src src
RUN gradle --no-daemon bootJar -x test

########## RUNTIME ##########
FROM eclipse-temurin:23-jre
RUN apt-get update  && apt-get install -y --no-install-recommends curl  && rm -rf /var/lib/apt/lists/*  && groupadd --system --gid 10001 app  && useradd --system --uid 10001 --gid app app

WORKDIR /app
COPY --from=build /app/build/libs/*-0.0.1-SNAPSHOT.jar /app/app.jar

RUN chown -R app:app /app

USER app
EXPOSE 8081 9091
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3   CMD curl -fsS http://localhost:8081/actuator/health || exit 1
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
