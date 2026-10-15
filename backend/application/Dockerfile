# syntax=docker/dockerfile:1
# Imagem do backend (R-15). Contexto de build: diretório `backend/`.

FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /build
COPY pom.xml .
COPY core/pom.xml core/
COPY application/pom.xml application/
RUN --mount=type=cache,target=/root/.m2 mvn -B -ntp -q dependency:go-offline -pl application -am || true
COPY core/src core/src
COPY application/src application/src
RUN --mount=type=cache,target=/root/.m2 mvn -B -ntp -q -DskipTests -Djacoco.skip=true package

FROM eclipse-temurin:25-jre
RUN apt-get update \
    && apt-get install -y --no-install-recommends ffmpeg \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system reports \
    && useradd --system --gid reports --home-dir /app --shell /usr/sbin/nologin reports \
    && mkdir -p /app /tmp/audios /data/documents \
    && chown -R reports:reports /app /tmp/audios /data/documents
WORKDIR /app
COPY --from=build /build/application/target/application.jar /app/application.jar
COPY application/docker-entrypoint.sh /app/docker-entrypoint.sh
RUN chmod 0755 /app/docker-entrypoint.sh
USER reports
EXPOSE 8080
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -Dfile.encoding=UTF-8"
ENTRYPOINT ["/app/docker-entrypoint.sh"]
