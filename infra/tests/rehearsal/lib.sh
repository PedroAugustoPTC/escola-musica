# Funções dos ensaios (feature 004, R-12). Lido com `.` pelos roteiros, dentro do contêiner tests-rehearsal, a partir de /work.
# Roteiros usam `set -u` (sem `-e`): cada verificação conta como ✔ ou ✘ e o resumo final decide a saída.

SIMULATOR="${SIMULATOR_URL:-http://telegram-simulator:8081}"
BOT="123456:REHEARSAL"
TRAFFIC_FLAG=/tmp/traffic.on
TRAFFIC_LOG=/tmp/traffic.chats
FAILURES=0
CHECKS=0

control_get() {
  wget -q -O - "$SIMULATOR$1"
}

control_post() {
  wget -q -O - --header='Content-Type: application/json' --post-data="$2" "$SIMULATOR$1"
}

bot_call() {
  data="${2:-}"
  [ -n "$data" ] || data='{}'
  wget -q -O - --header='Content-Type: application/json' --post-data="$data" "$SIMULATOR/bot$BOT/$1"
}

# run <comando...>: guarda a saída em OUT e o código em CODE, sem abortar o roteiro
run() {
  OUT="$("$@" 2>&1)"
  CODE=$?
  echo "$OUT" | sed 's/^/  | /'
}

section() {
  echo
  echo "── $*"
}

check() {
  # check <descrição> <esperado> <obtido>
  CHECKS=$((CHECKS + 1))
  if [ "$2" = "$3" ]; then
    echo "✔ $1"
  else
    FAILURES=$((FAILURES + 1))
    echo "✘ $1 (esperado '$2', obtido '$3')"
  fi
}

check_contains() {
  # check_contains <descrição> <trecho> <texto>
  CHECKS=$((CHECKS + 1))
  case "$3" in
    *"$2"*) echo "✔ $1" ;;
    *) FAILURES=$((FAILURES + 1)); echo "✘ $1 (não achei '$2' em: $3)" ;;
  esac
}

summary() {
  echo
  if [ "$FAILURES" -eq 0 ]; then
    echo "✔ $CHECKS verificação(ões) passaram"
    return 0
  fi
  echo "✘ $FAILURES de $CHECKS verificação(ões) falharam"
  return 1
}

webhook_url() {
  bot_call getWebhookInfo | sed -n 's/.*"url":"\([^"]*\)".*/\1/p'
}

pending_updates() {
  control_get /_control/status | sed -n 's/.*"pending":\([0-9]*\).*/\1/p'
}

wait_pending_zero() {
  waited=0
  while [ "$waited" -lt "${1:-120}" ]; do
    [ "$(pending_updates)" = "0" ] && return 0
    sleep 1
    waited=$((waited + 1))
  done
  return 1
}

# Leva a pilha ao estado inicial: zera o simulador, limpa o token seguinte, recria o backend e o n8n e registra o webhook
# do n8n no simulador (o registro é do telegram-webhook.sh; o backend não registra webhook).
prepare_state() {
  control_post /_control/reset '{}' >/dev/null
  write_env_key TELEGRAM_CHANNEL_NEXT_TOKEN ""
  recreate_service "$BACKEND_SERVICE"
  recreate_service "$N8N_SERVICE"
  wait_healthy "$BACKEND_SERVICE" 300
  wait_healthy "$N8N_SERVICE" 300
  sh infra/scripts/telegram-webhook.sh apply --env local >/dev/null 2>&1 || true
  waited=0
  while [ "$waited" -lt 60 ] && [ -z "$(webhook_url)" ]; do
    sleep 1
    waited=$((waited + 1))
  done
}

# Tráfego contínuo de professores NÃO vinculados: cada update vem de um chat novo e recebe uma resposta previsível
# (pedido do código de acesso), então "exatamente uma resposta por update" se confere sem massa de dados.
# Com contrapressão: não injeta enquanto houver mais de TRAFFIC_MAX_PENDING updates pendentes. Cada update custa ≈ 0,3 s de
# CPU no n8n; num executor lento, injetar a 3 updates/s passa da capacidade, a fila só cresce e o ensaio passaria a medir a
# velocidade da máquina, não a correção (uma resposta por update). As verificações continuam as mesmas.
TRAFFIC_MAX_PENDING="${TRAFFIC_MAX_PENDING:-30}"
traffic_start() {
  : > "$TRAFFIC_LOG"
  : > "$TRAFFIC_FLAG"
  (
    n=0
    while [ -f "$TRAFFIC_FLAG" ]; do
      if [ "$(pending_updates)" -gt "$TRAFFIC_MAX_PENDING" ] 2>/dev/null; then
        sleep 0.5
        continue
      fi
      n=$((n + 1))
      chat=$((900000 + n))
      if control_post /_control/updates "{\"chatId\":$chat,\"text\":\"oi\"}" >/dev/null 2>&1; then
        echo "$chat" >> "$TRAFFIC_LOG"
      fi
      sleep 0.3
    done
  ) &
  TRAFFIC_PID=$!
}

traffic_stop() {
  rm -f "$TRAFFIC_FLAG"
  wait "$TRAFFIC_PID" 2>/dev/null || true
}

# Cada update injetado tem exatamente uma resposta na transcrição do chat.
check_exactly_one_reply_each() {
  total=0
  wrong=0
  for chat in $(cat "$TRAFFIC_LOG"); do
    total=$((total + 1))
    replies=$(control_get "/_control/transcript?chat=$chat" | grep -o '"kind"' | wc -l)
    if [ "$replies" -ne 1 ]; then
      wrong=$((wrong + 1))
      echo "  chat $chat: $replies resposta(s) | entregas: $(control_get "/_control/history?chat=$chat" | sed 's/.*"attempts"://')"
    fi
  done
  check "todos os $total updates injetados têm exatamente uma resposta" 0 "$wrong"
}
