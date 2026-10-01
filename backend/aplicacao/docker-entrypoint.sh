#!/bin/sh
# Sem argumentos: sobe o servidor. `carga ...`, `emitir-codigo ...` e `massa ...` rodam os comandos operacionais
# (contracts/carga-inicial.md) no perfil `carga`, sem servidor web.
set -e
case "$1" in
  carga|emitir-codigo|massa)
    exec java -jar /app/aplicacao.jar --spring.profiles.active=carga --spring.main.web-application-type=none "$@"
    ;;
  "")
    exec java -jar /app/aplicacao.jar
    ;;
  *)
    exec "$@"
    ;;
esac
