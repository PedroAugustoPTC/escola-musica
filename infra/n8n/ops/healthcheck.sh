#!/bin/sh
# Saúde local do n8n (feature 004, R-10): os TRÊS receptores precisam estar registrados, senão o contêiner pode
# parecer saudável com o webhook do Telegram ainda fora do ar. Não chama o backend nem a API de bots.
set -eu
for path in transport-health/live telegram-inbound/live channel-delivery/live; do
  wget -q -O /dev/null "http://127.0.0.1:5678/webhook/$path"
done
