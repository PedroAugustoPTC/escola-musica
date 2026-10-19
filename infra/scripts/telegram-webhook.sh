#!/bin/sh
# Registro e verificação do webhook do Telegram (feature 004, FR-024, R-08). O n8n é o único transporte do canal, e este
# script é o ÚNICO dono do registro do webhook (o backend não o registra). Rode da raiz do repositório:
#
#   sh infra/scripts/telegram-webhook.sh status               # webhook registrado e saúde do n8n (sem alterar nada)
#   sh infra/scripts/telegram-webhook.sh verify               # verifica o webhook e as sondas (somente leitura, exceto as sondas)
#   sh infra/scripts/telegram-webhook.sh apply                # registra o webhook do n8n e verifica (usado por local.sh e deploy.sh)
#
# Opções: --env local (padrão) | prod · --chat-id <id> (ida e volta real no verify).
# Códigos de saída: 0 sucesso · 1 pré-condição não atendida (nada foi alterado) · 2 uso incorreto · 3 a verificação reprovou
# · 4 falha inesperada. Nenhum comando imprime token ou segredo.
# Não há mais troca nem reversão: depois da remoção do caminho antigo, reverter é reimplantar a versão anterior.
set -eu

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT"
. infra/scripts/lib/common.sh

command="${1:-help}"
[ "$#" -gt 0 ] && shift

environment=local
chat_id=""
while [ "$#" -gt 0 ]; do
  case "$1" in
    --env) environment="${2:-}"; shift ;;
    --env=*) environment="${1#--env=}" ;;
    --chat-id) chat_id="${2:-}"; shift ;;
    --chat-id=*) chat_id="${1#--chat-id=}" ;;
    *) echo "Opção desconhecida: $1" >&2; exit 2 ;;
  esac
  shift
done

case "$command" in
  help|-h|--help)
    sed -n '2,12p' "$0" | sed 's/^# \{0,1\}//'
    exit 0
    ;;
  status|verify|apply) ;;
  *) echo "Comando desconhecido: $command" >&2; sed -n '2,12p' "$0" | sed 's/^# \{0,1\}//' >&2; exit 2 ;;
esac

select_environment "$environment" || exit 2
BACKEND_SERVICE="${BACKEND_SERVICE:-backend}"
N8N_SERVICE="${N8N_SERVICE:-n8n}"
export N8N_SERVICE
require_env_file || exit 1

# Endereços: o nome do serviço do compose é o nome de rede.
N8N_WEBHOOK="http://$N8N_SERVICE:5678/webhook/telegram-update"
N8N_DELIVERY="http://$N8N_SERVICE:5678/webhook/channel-delivery"

# ---------------------------------------------------------------------------------------------------------------------
# Chamadas feitas de DENTRO do contêiner do n8n (o host só precisa de Docker). Segredos vão pelo ambiente, nunca por argumento.

in_n8n() {
  $COMPOSE exec -T "$N8N_SERVICE" "$@"
}

# bot_call <método> [corpo-json]: imprime só o corpo da resposta da API de bots (o token fica na URL, dentro do contêiner)
bot_call() {
  if [ -n "${2:-}" ]; then
    BODY="$2" $COMPOSE exec -T -e BODY "$N8N_SERVICE" node /opt/ops/probe.mjs POST \
      "{TELEGRAM_API_URL}/bot{TELEGRAM_BOT_TOKEN}/$1" --header Content-Type=application/json --data-env BODY 2>/dev/null | sed '1d'
  else
    in_n8n node /opt/ops/probe.mjs POST "{TELEGRAM_API_URL}/bot{TELEGRAM_BOT_TOKEN}/$1" \
      --header Content-Type=application/json --data '{}' 2>/dev/null | sed '1d'
  fi
}

registered_webhook() {
  bot_call getWebhookInfo | sed -n 's/.*"url":"\([^"]*\)".*/\1/p'
}

# post_update <segredo-ou-vazio> <corpo-json>: status HTTP do POST ao webhook do n8n
post_update() {
  SECRET="$1" BODY="$2" $COMPOSE exec -T -e SECRET -e BODY "$N8N_SERVICE" node /opt/ops/probe.mjs POST "$N8N_WEBHOOK" \
    --header Content-Type=application/json --header-env X-Telegram-Bot-Api-Secret-Token=SECRET --data-env BODY 2>/dev/null \
    | sed -n '1s/^HTTP //p'
}

unique_update_id() {
  # faixa reservada 2147000000 + n (abaixo de 2^31): update de tipo que o backend ignora, sem efeito em conversa
  echo $((2147000000 + $(date +%s) % 400000 + RANDOM_BUMP))
}
RANDOM_BUMP=0

health_status() {
  # health_status deep|live -> status HTTP
  in_n8n node /opt/ops/probe.mjs GET "http://127.0.0.1:5678/webhook/transport-health/$1" 2>/dev/null | sed -n '1s/^HTTP //p'
}

# ---------------------------------------------------------------------------------------------------------------------
# Registro do webhook e verificação

set_webhook() {
  secret="$(env_value TELEGRAM_WEBHOOK_SECRET)"
  body="{\"url\":\"$N8N_WEBHOOK\",\"secret_token\":\"$secret\",\"allowed_updates\":[\"message\",\"callback_query\"],\"max_connections\":10}"
  reply="$(bot_call setWebhook "$body")"
  case "$reply" in
    *'"ok":true'*) return 0 ;;
    *) return 1 ;;
  esac
}

# verify_webhook [chat-id]: imprime ✔/✘ por verificação; devolve 0 se todas passam
verify_webhook() {
  failed=0
  RANDOM_BUMP=$((RANDOM_BUMP + 1))

  # 1. webhook registrado
  info="$(bot_call getWebhookInfo)"
  registered="$(printf '%s' "$info" | sed -n 's/.*"url":"\([^"]*\)".*/\1/p')"
  if [ "$registered" = "$N8N_WEBHOOK" ] && printf '%s' "$info" | grep -q 'callback_query' && printf '%s' "$info" | grep -q '"message"'; then
    waited=0
    while printf '%s' "$info" | grep -q 'last_error_message' && [ "$waited" -lt 10 ]; do
      sleep 1
      waited=$((waited + 1))
      info="$(bot_call getWebhookInfo)"
    done
  fi
  if [ "$registered" = "$N8N_WEBHOOK" ] && printf '%s' "$info" | grep -q 'callback_query' && ! printf '%s' "$info" | grep -q 'last_error_message'; then
    step_ok "o webhook registrado é o do n8n"
  else
    step_fail WEBHOOK_MISMATCH "o webhook registrado não é o do n8n, ou há erro de entrega recente"
    failed=1
  fi

  # 2. sonda de segredo negativa
  update_id="$(unique_update_id)"
  probe_body="{\"update_id\":$update_id,\"poll\":{\"id\":\"probe\"}}"
  negative="$(post_update "wrong-secret-probe" "$probe_body")"
  case "$negative" in
    4??|5??) step_ok "segredo errado é recusado (status $negative)" ;;
    *) step_fail SECRET_NOT_ENFORCED "segredo errado não foi recusado (status '$negative')"; failed=1 ;;
  esac

  # 3. sonda de segredo positiva, sem efeito
  positive="$(post_update "$(env_value TELEGRAM_WEBHOOK_SECRET)" "{\"update_id\":$((update_id + 1)),\"poll\":{\"id\":\"probe\"}}")"
  case "$positive" in
    2??) step_ok "o segredo registrado é o que o backend conhece (status $positive)" ;;
    *) step_fail SECRET_MISMATCH "o segredo do .env não é aceito pelo caminho do webhook (status '$positive')"; failed=1 ;;
  esac

  # 4. saúde profunda do n8n
  deep="$(health_status deep)"
  if [ "$deep" = "200" ]; then
    step_ok "saúde profunda do n8n: backend e API de bots ok"
  else
    step_fail DEEP_HEALTH_FAILED "a saúde profunda do n8n respondeu '$deep'"
    failed=1
  fi

  # 5. para onde o backend entrega as proativas, lido do ambiente do backend (a URL não tem segredo)
  target="$($COMPOSE exec -T "$BACKEND_SERVICE" printenv TELEGRAM_CHANNEL_ORCHESTRATOR_URL 2>/dev/null | tr -d '\r\n' || true)"
  if [ "${target:-}" = "$N8N_DELIVERY" ]; then
    step_ok "a entrega proativa do backend aponta para o n8n"
  else
    step_fail DELIVERY_TARGET_MISMATCH "a entrega proativa do backend aponta para '${target:-}', esperado o n8n"
    failed=1
  fi

  # 6. ida e volta real (só com --chat-id)
  if [ -n "${1:-}" ]; then
    round="$(post_update "$(env_value TELEGRAM_WEBHOOK_SECRET)" "{\"update_id\":$((update_id + 2)),\"message\":{\"message_id\":1,\"date\":1,\"from\":{\"id\":$1,\"is_bot\":false,\"first_name\":\"Verifier\"},\"chat\":{\"id\":$1,\"type\":\"private\"},\"text\":\"help\"}}")"
    case "$round" in
      2??) step_ok "ida e volta real aceita (a resposta ao chat é a do catálogo do backend)" ;;
      *) step_fail ROUND_TRIP_FAILED "a ida e volta real respondeu '$round'"; failed=1 ;;
    esac
  fi
  return "$failed"
}

# ---------------------------------------------------------------------------------------------------------------------
# Pré-condições do apply: registrar o webhook num n8n que não atende perderia updates

preconditions_for_apply() {
  if [ "$(env_value TELEGRAM_ENABLED)" != "true" ] || [ -z "$(env_value TELEGRAM_BOT_TOKEN)" ]; then
    step_fail TELEGRAM_DISABLED "o Telegram está desligado ou sem token do bot no infra/.env"
    return 1
  fi
  if ! wait_healthy "$N8N_SERVICE" 20; then
    step_fail N8N_NOT_HEALTHY "o n8n não existe ou não está saudável; suba-o antes (o webhook não foi alterado)"
    return 1
  fi
  if [ "$(health_status live)" != "200" ]; then
    step_fail WORKFLOWS_NOT_ACTIVE "os workflows não estão publicados no n8n"
    return 1
  fi
  if [ "$(health_status deep)" != "200" ]; then
    step_fail DEEP_HEALTH_FAILED "a saúde profunda do n8n reprovou (backend ou API de bots fora, ou token recusado)"
    return 1
  fi
  secret="$(env_value TELEGRAM_WEBHOOK_SECRET)"
  case "$secret" in
    ""|*[!A-Za-z0-9_-]*) step_fail INVALID_WEBHOOK_SECRET "TELEGRAM_WEBHOOK_SECRET precisa ter de 1 a 256 caracteres A-Z, a-z, 0-9, _ ou -"; return 1 ;;
  esac
  [ "${#secret}" -le 256 ] || { step_fail INVALID_WEBHOOK_SECRET "TELEGRAM_WEBHOOK_SECRET passa de 256 caracteres"; return 1; }
  [ "${#secret}" -ge 32 ] || echo "⚠ TELEGRAM_WEBHOOK_SECRET tem menos de 32 caracteres; prefira um segredo mais longo" >&2
  current="$(env_value TELEGRAM_CHANNEL_CURRENT_TOKEN)"
  delivery_token="$(env_value TELEGRAM_CHANNEL_ORCHESTRATOR_TOKEN)"
  if [ -z "$current" ] || [ -z "$delivery_token" ] || [ "$current" = "$delivery_token" ]; then
    step_fail TOKENS_MISSING "TELEGRAM_CHANNEL_CURRENT_TOKEN e TELEGRAM_CHANNEL_ORCHESTRATOR_TOKEN precisam estar preenchidos e ser diferentes"
    return 1
  fi
  return 0
}

# ---------------------------------------------------------------------------------------------------------------------

case "$command" in
  status)
    if wait_healthy "$N8N_SERVICE" 2; then echo "  n8n: saudável (live $(health_status live))"; else echo "  n8n: ausente ou sem saúde"; fi
    registered="$(registered_webhook || true)"
    echo "  webhook registrado: ${registered:-desconhecido}"
    if [ "${registered:-}" = "$N8N_WEBHOOK" ]; then
      step_ok "o webhook registrado é o do n8n"
    else
      step_fail WEBHOOK_MISMATCH "o webhook registrado não é o do n8n; rode: sh infra/scripts/telegram-webhook.sh apply"
      exit 3
    fi
    ;;

  verify)
    chat="${chat_id:-$(env_value TELEGRAM_VERIFY_CHAT_ID)}"
    if verify_webhook "$chat"; then
      step_ok "verificação aprovada"
    else
      exit 3
    fi
    ;;

  apply)
    if [ "$(env_value TELEGRAM_ENABLED)" != "true" ]; then
      echo "Telegram desligado: nada a registrar."
      exit 0
    fi
    preconditions_for_apply || exit 1
    set_webhook || { step_fail WEBHOOK_MISMATCH "o setWebhook falhou"; exit 3; }
    step_ok "webhook do n8n registrado"
    verify_webhook "" || exit 3
    ;;
esac
