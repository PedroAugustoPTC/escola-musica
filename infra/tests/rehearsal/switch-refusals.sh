# Recusas e reprovações da troca do webhook (feature 004, US5 cenários 4 e 5, quickstart V11): nada fica pela metade.
cd /work || exit 1
. infra/scripts/lib/common.sh
. infra/tests/rehearsal/lib.sh

WEBHOOK="sh infra/scripts/telegram-webhook.sh"
DIRECT_URL="http://backend-rehearsal:8080/telegram/webhook"
N8N_URL="http://n8n-rehearsal:5678/webhook/telegram-update"

select_environment local
prepare_state DIRECT
state_keys() {
  printf '%s|%s|%s|%s' "$(env_value TELEGRAM_TRANSPORT)" "$(env_value TELEGRAM_CHANNEL_DELIVERY)" "$(env_value TELEGRAM_WEBHOOK_URL)" "$(env_value TELEGRAM_CHANNEL_ORCHESTRATOR_URL)"
}
before="$(state_keys)"

section "switch com o n8n parado: recusa e não altera o webhook"
$COMPOSE stop "$N8N_SERVICE" >/dev/null 2>&1
run $WEBHOOK switch --yes
check "switch sai com 1" 1 "$CODE"
check_contains "o motivo é N8N_NOT_HEALTHY" "N8N_NOT_HEALTHY" "$OUT"
check "o webhook continua o do backend" "$DIRECT_URL" "$(webhook_url)"
check "as chaves do estado não mudaram" "$before" "$(state_keys)"
$COMPOSE up -d --no-deps "$N8N_SERVICE" >/dev/null 2>&1
wait_healthy "$N8N_SERVICE" 240

section "segredo do webhook com caractere inválido"
valid_secret="$(env_value TELEGRAM_WEBHOOK_SECRET)"
write_env_key TELEGRAM_WEBHOOK_SECRET "segredo inválido!"
run $WEBHOOK switch --yes
check "switch sai com 1" 1 "$CODE"
check_contains "o motivo é INVALID_WEBHOOK_SECRET" "INVALID_WEBHOOK_SECRET" "$OUT"
check "o webhook continua o do backend" "$DIRECT_URL" "$(webhook_url)"
write_env_key TELEGRAM_WEBHOOK_SECRET "$valid_secret"

section "tokens iguais"
delivery_token="$(env_value TELEGRAM_CHANNEL_ORCHESTRATOR_TOKEN)"
write_env_key TELEGRAM_CHANNEL_ORCHESTRATOR_TOKEN "$(env_value TELEGRAM_CHANNEL_CURRENT_TOKEN)"
run $WEBHOOK switch --yes
check "switch sai com 1" 1 "$CODE"
check_contains "o motivo é TOKENS_MISSING" "TOKENS_MISSING" "$OUT"
write_env_key TELEGRAM_CHANNEL_ORCHESTRATOR_TOKEN "$delivery_token"

section "switch cuja verificação reprova restaura o estado anterior"
# a primeira consulta ao getWebhookInfo do verify falha: o verify reprova depois do setWebhook e a troca é desfeita
control_post /_control/fail-next '{"method":"getWebhookInfo","status":500,"description":"Internal Server Error"}' >/dev/null
run $WEBHOOK switch --yes
check "switch sai com 3" 3 "$CODE"
check_contains "o script diz que restaurou" "restaurado" "$OUT"
check "as chaves do estado voltaram" "$before" "$(state_keys)"
check "o webhook voltou ao do backend" "$DIRECT_URL" "$(webhook_url)"

section "verify com o segredo do .env trocado depois da última aplicação"
run $WEBHOOK switch --yes
check "switch (sem falhas programadas) sai com 0" 0 "$CODE"
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

section "rollback repetido é idempotente"
run $WEBHOOK rollback --yes
check "rollback sai com 0" 0 "$CODE"
run $WEBHOOK rollback --yes
check "repetir o rollback sai com 0" 0 "$CODE"
check_contains "repetir o rollback não altera nada" "já é o caminho antigo" "$OUT"

summary
