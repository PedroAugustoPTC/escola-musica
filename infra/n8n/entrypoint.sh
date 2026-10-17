#!/bin/sh
# Subida do n8n (feature 004, R-04, FR-008): o repositório sempre prevalece sobre a instância.
#   1. importa os workflows da imagem (substitui, por id, os que já existem);
#   2. publica cada workflow (a CLI só vale antes do `start`);
#   3. despublica o que existir na instância e não estiver no repositório;
#   4. inicia o servidor.
# Falha de importação ou de publicação encerra o processo: é melhor não subir do que subir com fluxo antigo.
# Este script não lê nem imprime segredo.
set -eu

WORKFLOWS_DIR=/opt/workflows

echo "n8n: importando os workflows do repositório..."
if ! n8n import:workflow --separate --input="$WORKFLOWS_DIR" >/dev/null; then
  echo "n8n: falha ao importar os workflows; a subida foi interrompida." >&2
  exit 1
fi

# Cada chamada da CLI inicia o aplicativo inteiro (cerca de 5 s) e a CLI não publica vários de uma vez; as
# publicações rodam em paralelo para caber no orçamento de 60 s do reinício (FR-003).
ids=""
pids=""
for file in "$WORKFLOWS_DIR"/*.json; do
  id="$(node -e 'process.stdout.write(JSON.parse(require("fs").readFileSync(process.argv[1], "utf8")).id)' "$file")"
  echo "n8n: publicando o workflow $id..."
  n8n publish:workflow --id="$id" >/dev/null &
  pids="$pids $!"
  ids="$ids $id"
done
for pid in $pids; do
  if ! wait "$pid"; then
    echo "n8n: falha ao publicar um workflow; a subida foi interrompida." >&2
    exit 1
  fi
done

# Workflow que existe só na instância (removido do repositório, ou criado à mão) não pode ficar ativo.
# shellcheck disable=SC2086
extra="$(node --no-warnings -e '
const { DatabaseSync } = require("node:sqlite");
const keep = new Set(process.argv.slice(1));
const db = new DatabaseSync(process.env.HOME + "/.n8n/database.sqlite", { readOnly: true });
for (const row of db.prepare("select id from workflow_entity").all()) {
  if (!keep.has(row.id)) console.log(row.id);
}
' $ids)"
for id in $extra; do
  echo "n8n: AVISO: o workflow $id existe só na instância e não está no repositório; foi despublicado." >&2
  n8n unpublish:workflow --id="$id" >/dev/null || true
done

echo "n8n: iniciando o servidor."
exec n8n start
