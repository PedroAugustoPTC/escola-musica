#!/bin/sh
# Sem argumentos: sobe o servidor. `seed ...`, `issue-code ...` e `demo-data ...` rodam os comandos operacionais
# (contracts/carga-inicial.md) no perfil `seed`, sem servidor web.
set -e
case "$1" in
  seed|issue-code|demo-data)
    exec java -jar /app/application.jar --spring.profiles.active=seed --spring.main.web-application-type=none "$@"
    ;;
  "")
    exec java -jar /app/application.jar
    ;;
  *)
    exec "$@"
    ;;
esac
