# Recusas e reprovações do registro do webhook (feature 004, US5, quickstart V11): nada fica pela metade.
cd /work || exit 1
. infra/scripts/lib/common.sh
. infra/tests/rehearsal/lib.sh

WEBHOOK="sh infra/scripts/telegram-webhook.sh"
N8N_URL="http://n8n-rehearsal:5678/webhook/telegram-update"

select_environment local
prepare_state

section "apply com o n8n parado: recusa e não altera o webhook"
before="$(webhook_url)"
$COMPOSE stop "$N8N_SERVICE" >/dev/null 2>&1
run $WEBHOOK apply
check "apply sai com 1" 1 "$CODE"
check_contains "o motivo é N8N_NOT_HEALTHY" "N8N_NOT_HEALTHY" "$OUT"
check "o webhook registrado não mudou" "$before" "$(webhook_url)"
$COMPOSE up -d --no-deps "$N8N_SERVICE" >/dev/null 2>&1
wait_healthy "$N8N_SERVICE" 300

section "segredo do webhook com caractere inválido"
valid_secret="$(env_value TELEGRAM_WEBHOOK_SECRET)"
write_env_key TELEGRAM_WEBHOOK_SECRET "segredo inválido!"
run $WEBHOOK apply
check "apply sai com 1" 1 "$CODE"
check_contains "o motivo é INVALID_WEBHOOK_SECRET" "INVALID_WEBHOOK_SECRET" "$OUT"
check "o webhook registrado não mudou" "$before" "$(webhook_url)"
write_env_key TELEGRAM_WEBHOOK_SECRET "$valid_secret"

section "tokens iguais"
delivery_token="$(env_value TELEGRAM_CHANNEL_ORCHESTRATOR_TOKEN)"
write_env_key TELEGRAM_CHANNEL_ORCHESTRATOR_TOKEN "$(env_value TELEGRAM_CHANNEL_CURRENT_TOKEN)"
run $WEBHOOK apply
check "apply sai com 1" 1 "$CODE"
check_contains "o motivo é TOKENS_MISSING" "TOKENS_MISSING" "$OUT"
write_env_key TELEGRAM_CHANNEL_ORCHESTRATOR_TOKEN "$delivery_token"

section "setWebhook que falha reprova o apply"
control_post /_control/fail-next '{"method":"setWebhook","status":500,"description":"Internal Server Error"}' >/dev/null
run $WEBHOOK apply
check "apply sai com 3" 3 "$CODE"
check_contains "o motivo é WEBHOOK_MISMATCH" "WEBHOOK_MISMATCH" "$OUT"
run $WEBHOOK apply
check "apply (sem falhas programadas) sai com 0" 0 "$CODE"
check "o webhook registrado é o do n8n" "$N8N_URL" "$(webhook_url)"

section "verify com o segredo do .env trocado depois da última aplicação"
registered_secret="$(env_value TELEGRAM_WEBHOOK_SECRET)"
write_env_key TELEGRAM_WEBHOOK_SECRET "other-secret-value"
run $WEBHOOK verify
check "verify sai com 3" 3 "$CODE"
check_contains "o motivo é SECRET_MISMATCH" "SECRET_MISMATCH" "$OUT"
write_env_key TELEGRAM_WEBHOOK_SECRET "$registered_secret"
run $WEBHOOK apply
check "apply sai com 0" 0 "$CODE"
run $WEBHOOK verify
check "verify volta a sair com 0" 0 "$CODE"

summary
