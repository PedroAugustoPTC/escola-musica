#!/bin/sh
# Sem argumentos: sobe o servidor. `seed ...`, `issue-code ...`, `demo-data ...` e `rotate-cpf-key ...` rodam os
# comandos operacionais (contracts/carga-inicial.md) no perfil `seed`, sem servidor web. Todo comando que o
# SeedCommandsExecutor entende precisa estar na primeira cláusula do `case` (DockerEntrypointRoutingTest).
set -e
case "$1" in
  seed|issue-code|demo-data|rotate-cpf-key)
    exec java -jar /app/application.jar --spring.profiles.active=seed --spring.main.web-application-type=none "$@"
    ;;
  "")
    exec java -jar /app/application.jar
    ;;
  *)
    exec "$@"
    ;;
esac
