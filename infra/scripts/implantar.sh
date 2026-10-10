#!/bin/sh
# Implantação na VPS (R-15): atualiza o código, reconstrói as imagens e sobe a pilha de
# produção. Roda na própria VPS, na raiz do repositório, com infra/.env de produção já preenchido.
#   infra/scripts/implantar.sh            # branch atual
#   infra/scripts/implantar.sh main       # troca para a branch indicada antes de atualizar
set -eu

RAIZ="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$RAIZ"

if [ ! -f infra/.env ]; then
  echo "infra/.env não encontrado: copie infra/.env.example e preencha os segredos de produção." >&2
  exit 1
fi

if [ "${1:-}" != "" ]; then
  git checkout "$1"
fi
git pull --ff-only

COMPOSE="docker compose -f infra/compose.yaml -f infra/compose.prod.yaml"
$COMPOSE build --pull
$COMPOSE up -d --remove-orphans
docker image prune -f >/dev/null

echo "Aguardando os serviços ficarem saudáveis…"
tentativas=0
while [ "$tentativas" -lt 30 ]; do
  if $COMPOSE ps --format '{{.Service}} {{.Health}}' | grep -Eq ' (starting|unhealthy)$'; then
    tentativas=$((tentativas + 1))
    sleep 10
  else
    $COMPOSE ps
    echo "Implantação concluída."
    exit 0
  fi
done

$COMPOSE ps
echo "Algum serviço não ficou saudável em 5 minutos; veja: $COMPOSE logs --tail=100" >&2
exit 1
