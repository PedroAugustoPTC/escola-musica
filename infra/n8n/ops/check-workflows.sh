#!/bin/sh
# Compara os workflows da INSTÂNCIA com os do repositório (os da imagem, em /opt/workflows).
# Exporta o que está no banco do n8n, normaliza e lista só nomes e caminhos de campo divergentes.
# Saída 0 = iguais; 1 = divergência. Usado por `infra/scripts/n8n.sh check` e pelo N8nImportIT.
set -eu
dir="$(mktemp -d)"
trap 'rm -rf "$dir"' EXIT
n8n export:workflow --backup --output="$dir" >/dev/null 2>&1
node /opt/ops/normalize-workflow.mjs compare "$dir" /opt/workflows
