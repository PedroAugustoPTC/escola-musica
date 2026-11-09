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
# uid:gid 101 é proposital, não um acidente do --system: é o mesmo uid:gid que a imagem
# aiogram/telegram-bot-api:10.3 usa para o usuário "telegram-bot-api" (fixo nela, conferido em
# /etc/passwd). Os dois contêineres compartilham o volume telegram-bot-api-data (R-05); sem o
# mesmo dono, o diretório que o telegram-bot-api cria por bot (0750) fica ilegível/sem permissão
# de apagar para o backend, que lê e remove o arquivo baixado direto do disco (TelegramFileAdapter).
RUN apt-get update \
    && apt-get install -y --no-install-recommends ffmpeg \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system --gid 101 reports \
    && useradd --system --uid 101 --gid reports --home-dir /app --shell /usr/sbin/nologin reports \
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
