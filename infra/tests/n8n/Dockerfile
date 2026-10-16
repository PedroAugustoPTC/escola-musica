# Imagem do `tests-n8n` (feature 004): Maven + JDK 25 + FFmpeg (as mesmas suítes de conversa do tests-backend) + o
# cliente `docker`, copiado da imagem oficial, para derrubar e subir o n8n no meio dos testes (FR-028b).
# O socket do Docker é montado só em infra/compose.test.yaml; nenhuma outra camada o expõe.
FROM docker:27-cli AS docker

FROM maven:3.9-eclipse-temurin-25
COPY --from=docker /usr/local/bin/docker /usr/local/bin/docker
RUN apt-get update -qq && apt-get install -y -qq ffmpeg >/dev/null && rm -rf /var/lib/apt/lists/*
