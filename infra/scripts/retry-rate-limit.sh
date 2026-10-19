#!/bin/sh
# Repete o comando quando o registro de imagens responde 429 (limite de taxa), que acontece de vez em quando no CI ao
# baixar a imagem do n8n (docker.n8n.io). Qualquer outra falha sai na hora, com o mesmo código, sem repetir.
#
#   sh infra/scripts/retry-rate-limit.sh <comando> [argumentos...]
#
# Até 5 tentativas, esperando 30 s, 60 s, 90 s e 120 s entre elas. Variável RETRY_WAIT_SECONDS troca os 30 s (testes).
set -u

attempts=5
base_wait="${RETRY_WAIT_SECONDS:-30}"
log="$(mktemp)"
trap 'rm -f "$log" "$log.status"' EXIT

attempt=1
while :; do
  { "$@"; echo $? > "$log.status"; } 2>&1 | tee "$log"
  status="$(cat "$log.status")"
  if [ "$status" = "0" ]; then
    exit 0
  fi
  if [ "$attempt" -ge "$attempts" ] || ! grep -q "429 Too Many Requests" "$log"; then
    exit "$status"
  fi
  wait=$((base_wait * attempt))
  echo "retry-rate-limit: o registro limitou a taxa (429); tentativa $attempt de $attempts, aguardando ${wait}s." >&2
  sleep "$wait"
  attempt=$((attempt + 1))
done
