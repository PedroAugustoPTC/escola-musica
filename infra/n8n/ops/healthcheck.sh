#!/bin/sh
# Saúde local do n8n (feature 004, R-10 e R-13a): a porta 5678 é a da porta de entrada (ops/gate.mjs), que só abre depois
# que os TRÊS receptores de webhook estão registrados. Basta, então, que ela responda no /healthz do n8n, que é uma
# rota barata e não executa workflow. Sondar os receptores `*/live` aqui competia por CPU com o tráfego do Telegram e,
# num n8n saturado depois de um reinício, a própria sonda estourava o tempo e o contêiner nunca ficava saudável.
# Não chama o backend nem a API de bots.
set -eu
wget -q -O /dev/null "http://127.0.0.1:5678/healthz"
