#!/bin/sh
# Operação do n8n (transporte do canal Telegram, feature 004). Rode da raiz do repositório:
#
#   sh infra/scripts/n8n.sh health [--deep]   # live (padrão) ou deep (backend e API de bots); sai 0 ou 1
#   sh infra/scripts/n8n.sh check             # a instância diverge dos workflows do repositório? (edição manual)
#   sh infra/scripts/n8n.sh export            # exporta da instância, normaliza e grava em infra/n8n/workflows/
#   sh infra/scripts/n8n.sh import            # reconstrói a imagem e recria o n8n (importa e publica na subida)
#   sh infra/scripts/n8n.sh reset             # APAGA o volume n8n-data e sobe o n8n de novo (confirmação digitada)
#   sh infra/scripts/n8n.sh logs [N]          # últimas N linhas (padrão 200), com qualquer token mascarado
#
# Opção: --env local (padrão) | prod. Nenhum comando imprime token, segredo ou URL com segredo.
set -eu

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT"
. infra/scripts/lib/common.sh

command="${1:-help}"
[ "$#" -gt 0 ] && shift

environment=local
deep=0
lines=200
while [ "$#" -gt 0 ]; do
  case "$1" in
    --env) environment="${2:-}"; shift ;;
    --env=*) environment="${1#--env=}" ;;
    --deep) deep=1 ;;
    -*) echo "Opção desconhecida: $1" >&2; exit 2 ;;
    *) lines="$1" ;;
  esac
  shift
done

# N8N_COMPOSE e N8N_SERVICE existem para os testes apontarem para a pilha de teste; o uso normal não os define.
if [ -n "${N8N_COMPOSE:-}" ]; then
  COMPOSE="$N8N_COMPOSE"
else
  select_environment "$environment" || exit 2
fi
SERVICE="${N8N_SERVICE:-n8n}"

in_n8n() {
  $COMPOSE exec -T "$SERVICE" "$@"
}

case "$command" in
  health)
    path=live
    [ "$deep" = "1" ] && path=deep
    if ! output="$(in_n8n node /opt/ops/probe.mjs GET "http://127.0.0.1:5678/webhook/transport-health/$path" 2>/dev/null)"; then
      step_fail N8N_UNREACHABLE "não foi possível falar com o n8n (o contêiner está de pé?)"
      exit 1
    fi
    status="$(printf '%s\n' "$output" | sed -n '1p' | sed 's/^HTTP //')"
    body="$(printf '%s\n' "$output" | sed '1d')"
    if [ "$status" = "200" ]; then
      step_ok "n8n ($path): $body"
    else
      step_fail N8N_NOT_HEALTHY "n8n ($path) respondeu $status: $body"
      exit 1
    fi
    ;;
  check)
    if in_n8n sh /opt/ops/check-workflows.sh; then
      step_ok "a instância está igual ao repositório"
    else
      step_fail INSTANCE_DIVERGED "a instância diverge do repositório; o próximo reinício descarta a diferença"
      exit 1
    fi
    ;;
  export)
    in_n8n sh -c 'rm -rf /tmp/raw /tmp/normalized && n8n export:workflow --backup --output=/tmp/raw >/dev/null 2>&1' \
      || { step_fail EXPORT_FAILED "não foi possível exportar os workflows da instância"; exit 1; }
    in_n8n node /opt/ops/normalize-workflow.mjs normalize /tmp/raw /tmp/normalized \
      || { step_fail UNSAFE_CONTENT "exportação recusada; nada foi gravado em infra/n8n/workflows/"; exit 1; }
    $COMPOSE cp "$SERVICE:/tmp/normalized/." infra/n8n/workflows/
    step_ok "workflows gravados em infra/n8n/workflows/ (revise com git diff)"
    ;;
  import)
    $COMPOSE up -d --build "$SERVICE"
    step_ok "n8n recriado; a importação e a publicação rodam na subida (acompanhe: n8n.sh health)"
    ;;
  reset)
    if ! confirm_typed apagar "Isto apaga o volume n8n-data (workflows importados e usuário do editor local)."; then
      echo "Cancelado."
      exit 0
    fi
    $COMPOSE rm -sf "$SERVICE"
    docker volume rm -f reports_n8n-data >/dev/null
    $COMPOSE up -d "$SERVICE"
    step_ok "volume apagado e n8n recriado"
    ;;
  logs)
    $COMPOSE logs --no-log-prefix --tail="$lines" "$SERVICE" \
      | sed -E 's/[0-9]{6,}:[A-Za-z0-9_-]{30,}/***/g'
    ;;
  help|-h|--help)
    sed -n '2,12p' "$0" | sed 's/^# \{0,1\}//'
    ;;
  *)
    echo "Comando desconhecido: $command" >&2
    sed -n '2,12p' "$0" | sed 's/^# \{0,1\}//' >&2
    exit 2
    ;;
esac
