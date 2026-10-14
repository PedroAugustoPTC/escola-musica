#!/bin/sh
# Extrai o catálogo do banco depois de uma versão de migração para um JSON (T058, e a prova do V7/V8).
#
#   sh specs/003-migrate-code-to-english/tools/db-catalog.sh <versão> <saída.json> [<pasta-de-migrações>]
#
# Sobe um PostgreSQL 18 descartável (mesma imagem do `postgres-teste`), aplica `V1`…`V<versão>` com `psql` e grava o
# resultado de `db-catalog.sql`. Nada é gravado no repositório além da saída pedida.
set -eu
VERSION="${1:?Informe a versão (ex.: 6)}"
OUT="${2:?Informe o arquivo de saída}"
MIGRATIONS="${3:-backend/application/src/main/resources/db/migration}"
export MSYS_NO_PATHCONV=1
NAME="catalog-pg-$$"
trap 'docker rm -f "$NAME" >/dev/null 2>&1 || true' EXIT
docker run -d --name "$NAME" -e POSTGRES_PASSWORD=pw -e POSTGRES_DB=catalog \
  -v "$PWD/$MIGRATIONS":/migrations:ro \
  -v "$PWD/specs/003-migrate-code-to-english/tools":/tools:ro \
  postgres:18 >/dev/null
for _ in $(seq 1 60); do
  if docker exec "$NAME" pg_isready -U postgres -d catalog >/dev/null 2>&1; then break; fi
  sleep 1
done
sleep 2
for file in $(docker exec "$NAME" sh -c 'ls /migrations' | sort -V); do
  number="${file#V}"; number="${number%%__*}"
  if [ "$number" -le "$VERSION" ]; then
    docker exec "$NAME" psql -q -v ON_ERROR_STOP=1 -U postgres -d catalog -f "/migrations/$file" >/dev/null
  fi
done
docker exec "$NAME" psql -At -U postgres -d catalog -f /tools/db-catalog.sql > "$OUT"
node -e "const fs=require('fs');const j=JSON.parse(fs.readFileSync(process.argv[1],'utf8'));fs.writeFileSync(process.argv[1],JSON.stringify(j,null,2)+'\n');console.log(j.tables.length+' tabelas, '+j.tables.reduce((s,t)=>s+t.columns.length,0)+' colunas, '+j.constraints.length+' restrições, '+j.indexes.length+' índices, '+(j.sequences?j.sequences.length:0)+' sequências')" "$OUT"
