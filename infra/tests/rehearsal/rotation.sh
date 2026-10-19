# Ensaio da rotação do token de serviço sob tráfego (feature 004, FR-004/US5, R-09, R-12, quickstart V12).
# Roda em tests-rehearsal: cd /work; lê common.sh e lib.sh.
cd /work || exit 1
. infra/scripts/lib/common.sh
. infra/tests/rehearsal/lib.sh

ROTATE="sh infra/scripts/rotate-service-token.sh"
select_environment local
prepare_state
old_token="$(env_value TELEGRAM_CHANNEL_CURRENT_TOKEN)"

section "estado inicial"
run $ROTATE status
check "status sai com 0" 0 "$CODE"
check_contains "o estado inicial é IDLE" "IDLE" "$OUT"
check "o webhook registrado é o do n8n" "http://n8n-rehearsal:5678/webhook/telegram-update" "$(webhook_url)"

traffic_start
sleep 3

section "start sob tráfego"
run $ROTATE start --yes
check "start sai com 0" 0 "$CODE"
run $ROTATE status
check_contains "depois do start o estado é DUAL" "DUAL" "$OUT"
new_token="$(env_value TELEGRAM_CHANNEL_NEXT_TOKEN)"
check "o backend aceita o token novo" 404 "$(token_status "$new_token")"
check "o backend ainda aceita o token atual" 404 "$(token_status "$old_token")"

section "start recusado fora de IDLE"
run $ROTATE start --yes
check "start fora de IDLE sai com 1" 1 "$CODE"
check_contains "o motivo é NOT_IN_IDLE" "NOT_IN_IDLE" "$OUT"

section "finish"
run $ROTATE finish --yes
check "finish sai com 0" 0 "$CODE"
run $ROTATE status
check_contains "depois do finish o estado é IDLE" "IDLE" "$OUT"
check "o backend aceita o token novo (agora o atual)" 404 "$(token_status "$new_token")"
check "o backend recusa o token antigo" 401 "$(token_status "$old_token")"
run $ROTATE finish --yes
check "finish fora de DUAL sai com 1" 1 "$CODE"

section "abort"
run $ROTATE start --yes
check "novo start sai com 0" 0 "$CODE"
discarded="$(env_value TELEGRAM_CHANNEL_NEXT_TOKEN)"
run $ROTATE abort --yes
check "abort sai com 0" 0 "$CODE"
run $ROTATE status
check_contains "depois do abort o estado é IDLE" "IDLE" "$OUT"
check "o backend recusa o token descartado" 401 "$(token_status "$discarded")"
check "o backend aceita o token atual" 404 "$(token_status "$new_token")"

section "nada vaza e nada se perde"
traffic_stop
wait_pending_zero 180
check "não restou update pendente no simulador" 0 "$(pending_updates)"
check_exactly_one_reply_each
summary
