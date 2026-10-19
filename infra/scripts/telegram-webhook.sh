#!/bin/sh
# Troca controlada do transporte do Telegram, com reversão (feature 004, FR-024 a FR-026, R-08). Rode da raiz do repositório:
#
#   sh infra/scripts/telegram-webhook.sh status               # estado do transporte, webhook registrado e saúde (sem alterar nada)
#   sh infra/scripts/telegram-webhook.sh switch               # DIRECT -> N8N: o n8n passa a ser o transporte (webhook e entrega proativa juntos)
#   sh infra/scripts/telegram-webhook.sh rollback             # N8N -> DIRECT: volta ao caminho antigo
#   sh infra/scripts/telegram-webhook.sh verify               # verifica o estado atual (somente leitura, exceto as sondas)
#   sh infra/scripts/telegram-webhook.sh apply                # registra o webhook do estado atual e verifica (usado por local.sh e deploy.sh)
#
# Opções: --env local (padrão) | prod · --yes (dispensa a confirmação digitada) · --dry-run · --chat-id <id> (ida e volta real no verify).
# Códigos de saída: 0 sucesso · 1 pré-condição não atendida (nada foi alterado) · 2 uso incorreto · 3 a verificação reprovou
# (o estado anterior já foi restaurado quando era uma troca) · 4 falha inesperada.
# O estado vive em quatro chaves do infra/.env, que mudam JUNTAS: TELEGRAM_TRANSPORT, TELEGRAM_CHANNEL_DELIVERY,
# TELEGRAM_WEBHOOK_URL e TELEGRAM_CHANNEL_ORCHESTRATOR_URL. Nenhum comando imprime token ou segredo.
set -eu

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT"
. infra/scripts/lib/common.sh

command="${1:-help}"
[ "$#" -gt 0 ] && shift

environment=local
YES=0
dry_run=0
chat_id=""
while [ "$#" -gt 0 ]; do
  case "$1" in
    --env) environment="${2:-}"; shift ;;
    --env=*) environment="${1#--env=}" ;;
    --yes) YES=1 ;;
    --dry-run) dry_run=1 ;;
    --chat-id) chat_id="${2:-}"; shift ;;
    --chat-id=*) chat_id="${1#--chat-id=}" ;;
    *) echo "Opção desconhecida: $1" >&2; exit 2 ;;
  esac
  shift
done

case "$command" in
  help|-h|--help)
    sed -n '2,15p' "$0" | sed 's/^# \{0,1\}//'
    exit 0
    ;;
  status|switch|rollback|verify|apply) ;;
  *) echo "Comando desconhecido: $command" >&2; sed -n '2,15p' "$0" | sed 's/^# \{0,1\}//' >&2; exit 2 ;;
esac

select_environment "$environment" || exit 2
BACKEND_SERVICE="${BACKEND_SERVICE:-backend}"
N8N_SERVICE="${N8N_SERVICE:-n8n}"
export N8N_SERVICE
STATE_FILE="${TRANSPORT_STATE_FILE:-infra/.transport-state}"
require_env_file || exit 1

# Endereços de cada estado: o nome do serviço do compose é o nome de rede.
DIRECT_WEBHOOK="http://$BACKEND_SERVICE:8080/telegram/webhook"
N8N_WEBHOOK="http://$N8N_SERVICE:5678/webhook/telegram-update"
N8N_DELIVERY="http://$N8N_SERVICE:5678/webhook/channel-delivery"

# ---------------------------------------------------------------------------------------------------------------------
# Estado

current_transport() { v="$(env_value TELEGRAM_TRANSPORT)"; echo "${v:-DIRECT}"; }
current_delivery() { v="$(env_value TELEGRAM_CHANNEL_DELIVERY)"; echo "${v:-DIRECT}"; }
current_webhook_url() { v="$(env_value TELEGRAM_WEBHOOK_URL)"; echo "${v:-$DIRECT_WEBHOOK}"; }
current_orchestrator_url() { env_value TELEGRAM_CHANNEL_ORCHESTRATOR_URL; }

# DIRECT, N8N ou INCONSISTENT (as quatro chaves precisam concordar)
transport_state() {
  if [ "$(current_transport)" = "DIRECT" ] && [ "$(current_delivery)" = "DIRECT" ] \
    && [ "$(current_webhook_url)" = "$DIRECT_WEBHOOK" ] && [ -z "$(current_orchestrator_url)" ]; then
    echo DIRECT
  elif [ "$(current_transport)" = "N8N" ] && [ "$(current_delivery)" = "ORCHESTRATOR" ] \
    && [ "$(current_webhook_url)" = "$N8N_WEBHOOK" ] && [ "$(current_orchestrator_url)" = "$N8N_DELIVERY" ]; then
    echo N8N
  else
    echo INCONSISTENT
  fi
}

write_state() {
  # write_state DIRECT|N8N
  if [ "$1" = "N8N" ]; then
    write_env_key TELEGRAM_TRANSPORT N8N
    write_env_key TELEGRAM_CHANNEL_DELIVERY ORCHESTRATOR
    write_env_key TELEGRAM_WEBHOOK_URL "$N8N_WEBHOOK"
    write_env_key TELEGRAM_CHANNEL_ORCHESTRATOR_URL "$N8N_DELIVERY"
  else
    write_env_key TELEGRAM_TRANSPORT DIRECT
    write_env_key TELEGRAM_CHANNEL_DELIVERY DIRECT
    write_env_key TELEGRAM_WEBHOOK_URL "$DIRECT_WEBHOOK"
    write_env_key TELEGRAM_CHANNEL_ORCHESTRATOR_URL ""
  fi
}

expected_webhook() {
  if [ "$1" = "N8N" ]; then echo "$N8N_WEBHOOK"; else echo "$DIRECT_WEBHOOK"; fi
}

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

# post_update <segredo-ou-vazio> <corpo-json>: status HTTP do POST ao webhook REGISTRADO para o estado atual
post_update() {
  target="$(expected_webhook "$(transport_state_or_direct)")"
  SECRET="$1" BODY="$2" $COMPOSE exec -T -e SECRET -e BODY "$N8N_SERVICE" node /opt/ops/probe.mjs POST "$target" \
    --header Content-Type=application/json --header-env X-Telegram-Bot-Api-Secret-Token=SECRET --data-env BODY 2>/dev/null \
    | sed -n '1s/^HTTP //p'
}

transport_state_or_direct() {
  s="$(transport_state)"
  if [ "$s" = "N8N" ]; then echo N8N; else echo DIRECT; fi
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
  # set_webhook <url>
  secret="$(env_value TELEGRAM_WEBHOOK_SECRET)"
  body="{\"url\":\"$1\",\"secret_token\":\"$secret\",\"allowed_updates\":[\"message\",\"callback_query\"],\"max_connections\":10}"
  reply="$(bot_call setWebhook "$body")"
  case "$reply" in
    *'"ok":true'*) return 0 ;;
    *) return 1 ;;
  esac
}

# verify_state <DIRECT|N8N> [com-ida-e-volta]: imprime ✔/✘ por verificação; devolve 0 se todas passam
verify_state() {
  state="$1"
  failed=0
  target="$(expected_webhook "$state")"
  RANDOM_BUMP=$((RANDOM_BUMP + 1))

  # 1. webhook registrado
  info="$(bot_call getWebhookInfo)"
  registered="$(printf '%s' "$info" | sed -n 's/.*"url":"\([^"]*\)".*/\1/p')"
  if [ "$registered" = "$target" ] && printf '%s' "$info" | grep -q 'callback_query' && printf '%s' "$info" | grep -q '"message"'; then
    waited=0
    while printf '%s' "$info" | grep -q 'last_error_message' && [ "$waited" -lt 10 ]; do
      sleep 1
      waited=$((waited + 1))
      info="$(bot_call getWebhookInfo)"
    done
  fi
  if [ "$registered" = "$target" ] && printf '%s' "$info" | grep -q 'callback_query' && ! printf '%s' "$info" | grep -q 'last_error_message'; then
    step_ok "o webhook registrado é o esperado para $state"
  else
    step_fail WEBHOOK_MISMATCH "o webhook registrado não é o esperado para $state, ou há erro de entrega recente"
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

  # 4. saúde profunda do n8n (só no estado N8N)
  if [ "$state" = "N8N" ]; then
    deep="$(health_status deep)"
    if [ "$deep" = "200" ]; then
      step_ok "saúde profunda do n8n: backend e API de bots ok"
    else
      step_fail DEEP_HEALTH_FAILED "a saúde profunda do n8n respondeu '$deep'"
      failed=1
    fi
  fi

  # 5. quem entrega as proativas, lido do ambiente do backend (só o nome do modo)
  delivery="$($COMPOSE exec -T "$BACKEND_SERVICE" printenv TELEGRAM_CHANNEL_DELIVERY 2>/dev/null | tr -d '\r\n' || true)"
  want="DIRECT"
  [ "$state" = "N8N" ] && want="ORCHESTRATOR"
  if [ "${delivery:-DIRECT}" = "$want" ]; then
    step_ok "a entrega proativa é $want"
  else
    step_fail DELIVERY_MODE_MISMATCH "a entrega proativa do backend é '${delivery:-}', esperado $want"
    failed=1
  fi

  # 6. ida e volta real (só com --chat-id)
  if [ -n "${2:-}" ]; then
    round="$(post_update "$(env_value TELEGRAM_WEBHOOK_SECRET)" "{\"update_id\":$((update_id + 2)),\"message\":{\"message_id\":1,\"date\":1,\"from\":{\"id\":$2,\"is_bot\":false,\"first_name\":\"Verifier\"},\"chat\":{\"id\":$2,\"type\":\"private\"},\"text\":\"help\"}}")"
    case "$round" in
      2??) step_ok "ida e volta real aceita (a resposta ao chat é a do catálogo do backend)" ;;
      *) step_fail ROUND_TRIP_FAILED "a ida e volta real respondeu '$round'"; failed=1 ;;
    esac
  fi
  return "$failed"
}

# ---------------------------------------------------------------------------------------------------------------------
# Pré-condições do switch

preconditions_for_n8n() {
  if [ "$(env_value TELEGRAM_ENABLED)" != "true" ] || [ -z "$(env_value TELEGRAM_BOT_TOKEN)" ]; then
    step_fail TELEGRAM_DISABLED "o Telegram está desligado ou sem token do bot no infra/.env"
    return 1
  fi
  if ! wait_healthy "$N8N_SERVICE" 20; then
    step_fail N8N_NOT_HEALTHY "o n8n não existe ou não está saudável; suba-o antes (a troca não foi feita)"
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

confirm_or_exit() {
  if [ "$environment" = "prod" ] && [ -z "${TRANSPORT_COMPOSE:-}" ]; then
    confirm_typed "$1" "$2" || { echo "Cancelado."; exit 0; }
  fi
}

# Aplica o estado alvo: grava, recria o backend, registra o webhook e verifica. Restaura o anterior se a verificação reprovar.
apply_target() {
  target_state="$1"
  previous_state="$2"
  backup_env_file
  {
    echo "TELEGRAM_TRANSPORT=$(current_transport)"
    echo "TELEGRAM_CHANNEL_DELIVERY=$(current_delivery)"
    echo "TELEGRAM_WEBHOOK_URL=$(current_webhook_url)"
    echo "TELEGRAM_CHANNEL_ORCHESTRATOR_URL=$(current_orchestrator_url)"
  } > "$STATE_FILE"
  write_state "$target_state"
  step_ok "estado do transporte gravado no infra/.env: $target_state"
  recreate_service "$BACKEND_SERVICE"
  if ! wait_healthy "$BACKEND_SERVICE" 240; then
    restore_previous "$previous_state"
    step_fail BACKEND_NOT_HEALTHY "o backend não voltou saudável; o estado anterior foi restaurado"
    exit 3
  fi
  step_ok "backend recriado com o novo estado"
  if ! set_webhook "$(expected_webhook "$target_state")"; then
    restore_previous "$previous_state"
    step_fail WEBHOOK_MISMATCH "o setWebhook falhou; o estado anterior foi restaurado"
    exit 3
  fi
  step_ok "webhook registrado: $target_state"
  if ! verify_state "$target_state" "$chat_id"; then
    restore_previous "$previous_state"
    echo "A verificação reprovou; o estado anterior ($previous_state) foi restaurado." >&2
    exit 3
  fi
}

restore_previous() {
  if [ "$1" = "N8N" ] || [ "$1" = "DIRECT" ]; then
    write_state "$1"
  fi
  recreate_service "$BACKEND_SERVICE"
  wait_healthy "$BACKEND_SERVICE" 240 || true
  set_webhook "$(expected_webhook "$1")" || true
}

# ---------------------------------------------------------------------------------------------------------------------

case "$command" in
  status)
    state="$(transport_state)"
    step_ok "estado do transporte: $state"
    echo "  TELEGRAM_TRANSPORT=$(current_transport) · TELEGRAM_CHANNEL_DELIVERY=$(current_delivery)"
    if wait_healthy "$N8N_SERVICE" 2; then echo "  n8n: saudável (live $(health_status live))"; else echo "  n8n: ausente ou sem saúde"; fi
    registered="$(registered_webhook || true)"
    echo "  webhook registrado: ${registered:-desconhecido}"
    [ "$state" != "INCONSISTENT" ] || { step_fail INCONSISTENT_STATE "as chaves do estado não concordam; use switch ou rollback"; exit 3; }
    ;;

  verify)
    state="$(transport_state)"
    [ "$state" != "INCONSISTENT" ] || { step_fail INCONSISTENT_STATE "as chaves do estado não concordam"; exit 3; }
    chat="${chat_id:-$(env_value TELEGRAM_VERIFY_CHAT_ID)}"
    if verify_state "$state" "$chat"; then
      step_ok "verificação aprovada ($state)"
    else
      exit 3
    fi
    ;;

  apply)
    if [ "$(env_value TELEGRAM_ENABLED)" != "true" ]; then
      echo "Telegram desligado: nada a registrar."
      exit 0
    fi
    state="$(transport_state)"
    [ "$state" != "INCONSISTENT" ] || { step_fail INCONSISTENT_STATE "as chaves do estado não concordam; use switch ou rollback"; exit 3; }
    set_webhook "$(expected_webhook "$state")" || { step_fail WEBHOOK_MISMATCH "o setWebhook falhou"; exit 3; }
    step_ok "webhook registrado para o estado atual ($state)"
    verify_state "$state" "" || exit 3
    ;;

  switch)
    state="$(transport_state)"
    if [ "$state" = "N8N" ]; then
      step_ok "o transporte já é o n8n; nada a fazer"
      exit 0
    fi
    preconditions_for_n8n || exit 1
    if [ "$dry_run" = "1" ]; then
      echo "(simulação) gravaria as quatro chaves do estado N8N, recriaria $BACKEND_SERVICE, registraria o webhook e verificaria"
      exit 0
    fi
    confirm_or_exit trocar "Isto liga o n8n como transporte do Telegram em PRODUÇÃO."
    apply_target N8N "$state"
    step_ok "troca concluída: o n8n é o transporte"
    ;;

  rollback)
    state="$(transport_state)"
    if [ "$state" = "DIRECT" ]; then
      step_ok "o transporte já é o caminho antigo; nada a fazer"
      exit 0
    fi
    if [ "$dry_run" = "1" ]; then
      echo "(simulação) gravaria as quatro chaves do estado DIRECT, recriaria $BACKEND_SERVICE, registraria o webhook e verificaria"
      exit 0
    fi
    confirm_or_exit reverter "Isto devolve o transporte do Telegram ao caminho antigo em PRODUÇÃO."
    apply_target DIRECT "$state"
    step_ok "reversão concluída: o transporte é o caminho antigo"
    ;;
esac
