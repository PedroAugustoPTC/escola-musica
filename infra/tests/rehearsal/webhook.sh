# Ensaio do registro do webhook do n8n sob tráfego (feature 004, FR-024, US5, quickstart V11): o n8n é o único transporte, e
# o telegram-webhook.sh é o único dono do registro. Cada update injetado precisa ter EXATAMENTE uma resposta na
# transcrição em todas as fases (sem perda, sem duplicidade).
cd /work || exit 1
. infra/scripts/lib/common.sh
. infra/tests/rehearsal/lib.sh

WEBHOOK="sh infra/scripts/telegram-webhook.sh"
N8N_URL="http://n8n-rehearsal:5678/webhook/telegram-update"

select_environment local
prepare_state

section "estado inicial"
run $WEBHOOK status
check "status sai com 0" 0 "$CODE"
check "o webhook registrado é o do n8n" "$N8N_URL" "$(webhook_url)"
run $WEBHOOK verify
check "verify sai com 0" 0 "$CODE"

traffic_start
sleep 3

section "apply sob tráfego"
run $WEBHOOK apply
check "apply sai com 0" 0 "$CODE"
check "o webhook registrado continua o do n8n" "$N8N_URL" "$(webhook_url)"
sleep 3

section "apply repetido é idempotente"
run $WEBHOOK apply
check "repetir o apply sai com 0" 0 "$CODE"
check "o webhook continua o do n8n" "$N8N_URL" "$(webhook_url)"
run $WEBHOOK verify
check "verify sai com 0" 0 "$CODE"
sleep 3

section "nada se perde e nada duplica"
traffic_stop
wait_pending_zero 180
check "não restou update pendente no simulador" 0 "$(pending_updates)"
check_exactly_one_reply_each

summary
