#!/bin/sh
# Implantação na VPS (R-15): atualiza o código, reconstrói as imagens e sobe a pilha de
# produção. Roda na própria VPS, na raiz do repositório, com infra/.env de produção já preenchido.
#   infra/scripts/deploy.sh            # branch atual
#   infra/scripts/deploy.sh main       # troca para a branch indicada antes de atualizar
set -eu

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT"

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
# o papel de banco da aplicação (reports_app) precisa existir, com senha, antes de o backend subir (feature 007)
$COMPOSE up -d --wait postgres
. infra/scripts/lib/db-roles.sh
apply_database_roles "$COMPOSE"
$COMPOSE up -d --remove-orphans
docker image prune -f >/dev/null

echo "Aguardando os serviços ficarem saudáveis…"
attempts=0
while [ "$attempts" -lt 30 ]; do
  if $COMPOSE ps --format '{{.Service}} {{.Health}}' | grep -Eq ' (starting|unhealthy)$'; then
    attempts=$((attempts + 1))
    sleep 10
  else
    $COMPOSE ps
    if [ "$(sed -n 's/^TELEGRAM_ENABLED=//p' infra/.env | tail -n 1)" = "true" ]; then
      # o script do webhook é o único dono do registro do webhook, que aponta para o n8n
      sh infra/scripts/telegram-webhook.sh apply --env prod         || { echo "A implantação subiu, mas o registro/verificação do webhook falhou; veja: sh infra/scripts/telegram-webhook.sh status --env prod" >&2; exit 1; }
    fi
    echo "Implantação concluída."
    exit 0
  fi
done

$COMPOSE ps
echo "Algum serviço não ficou saudável em 5 minutos; veja: $COMPOSE logs --tail=100" >&2
exit 1
