# Ensaio da troca do webhook para o n8n e da reversão, sob tráfego (feature 004, FR-024 a FR-026, US5 cenários 1 a 3, quickstart V11).
# Cada update injetado precisa ter EXATAMENTE uma resposta na transcrição em todas as fases (sem perda, sem duplicidade).
cd /work || exit 1
. infra/scripts/lib/common.sh
. infra/tests/rehearsal/lib.sh

WEBHOOK="sh infra/scripts/telegram-webhook.sh"
DIRECT_URL="http://backend-rehearsal:8080/telegram/webhook"
N8N_URL="http://n8n-rehearsal:5678/webhook/telegram-update"

select_environment local
prepare_state DIRECT

section "estado inicial (DIRECT)"
run $WEBHOOK status
check "status sai com 0" 0 "$CODE"
check_contains "o estado é DIRECT" "DIRECT" "$OUT"
check "o webhook registrado é o do backend" "$DIRECT_URL" "$(webhook_url)"

traffic_start
sleep 3

section "switch sob tráfego"
run $WEBHOOK switch --yes
check "switch sai com 0" 0 "$CODE"
check "o webhook registrado passou a ser o do n8n" "$N8N_URL" "$(webhook_url)"
check "a entrega proativa do backend é ORCHESTRATOR" "ORCHESTRATOR" "$($COMPOSE exec -T "$BACKEND_SERVICE" printenv TELEGRAM_CHANNEL_DELIVERY | tr -d '\r\n')"
run $WEBHOOK status
check_contains "o estado agora é N8N" "N8N" "$OUT"
run $WEBHOOK verify
check "verify sai com 0" 0 "$CODE"
sleep 3

section "rollback sob tráfego"
run $WEBHOOK rollback --yes
check "rollback sai com 0" 0 "$CODE"
check "o webhook registrado voltou ao do backend" "$DIRECT_URL" "$(webhook_url)"
check "a entrega proativa voltou a ser DIRECT" "DIRECT" "$($COMPOSE exec -T "$BACKEND_SERVICE" printenv TELEGRAM_CHANNEL_DELIVERY | tr -d '\r\n')"
run $WEBHOOK verify
check "verify sai com 0 no estado DIRECT" 0 "$CODE"
sleep 3

section "switch de novo e idempotência"
run $WEBHOOK switch --yes
check "segundo switch sai com 0" 0 "$CODE"
check "o webhook é o do n8n de novo" "$N8N_URL" "$(webhook_url)"
run $WEBHOOK switch --yes
check "repetir o switch sai com 0" 0 "$CODE"
check_contains "repetir o switch não altera nada" "já é o n8n" "$OUT"
sleep 3

section "nada se perde e nada duplica"
traffic_stop
wait_pending_zero 180
check "não restou update pendente no simulador" 0 "$(pending_updates)"
check_exactly_one_reply_each

summary
