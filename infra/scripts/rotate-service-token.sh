#!/bin/sh
# Rotação do token de serviço do n8n sem janela de recusa (feature 004, R-09), usando os dois tokens simultâneos
# da API interna. Rode da raiz do repositório:
#
#   sh infra/scripts/rotate-service-token.sh status              # IDLE ou DUAL (sem imprimir token)
#   sh infra/scripts/rotate-service-token.sh start               # gera o token novo; o backend aceita os dois; o n8n passa a enviar o novo
#   sh infra/scripts/rotate-service-token.sh finish              # o novo vira o atual; o antigo passa a ser recusado
#   sh infra/scripts/rotate-service-token.sh abort               # descarta o novo e volta ao atual
#
# Opções: --env local (padrão) | prod · --yes (dispensa a confirmação digitada) · --dry-run (mostra sem alterar).
# Códigos de saída: 0 sucesso · 1 pré-condição não atendida (nada mudou) · 2 uso incorreto · 3 a verificação reprovou.
# Nenhum comando imprime token. O infra/.env é reescrito de forma atômica e a cópia anterior fica em
# infra/.env.previous (contém segredos; ignorada pelo Git).
set -eu

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT"
. infra/scripts/lib/common.sh

command="${1:-help}"
[ "$#" -gt 0 ] && shift

environment=local
YES=0
dry_run=0
while [ "$#" -gt 0 ]; do
  case "$1" in
    --env) environment="${2:-}"; shift ;;
    --env=*) environment="${1#--env=}" ;;
    --yes) YES=1 ;;
    --dry-run) dry_run=1 ;;
    *) echo "Opção desconhecida: $1" >&2; exit 2 ;;
  esac
  shift
done

case "$command" in
  help|-h|--help)
    sed -n '2,13p' "$0" | sed 's/^# \{0,1\}//'
    exit 0
    ;;
  status|start|finish|abort) ;;
  *) echo "Comando desconhecido: $command" >&2; sed -n '2,13p' "$0" | sed 's/^# \{0,1\}//' >&2; exit 2 ;;
esac

select_environment "$environment" || exit 2
BACKEND_SERVICE="${BACKEND_SERVICE:-backend}"
N8N_SERVICE="${N8N_SERVICE:-n8n}"
export N8N_SERVICE
require_env_file || exit 1

rotation_state() {
  if [ -n "$(env_value TELEGRAM_CHANNEL_NEXT_TOKEN)" ]; then
    echo DUAL
  else
    echo IDLE
  fi
}

filled() {
  if [ -n "$(env_value "$1")" ]; then echo filled; else echo empty; fi
}

ensure_healthy() {
  if ! wait_healthy "$BACKEND_SERVICE" 120; then
    step_fail BACKEND_NOT_HEALTHY "o backend não está saudável"
    exit 1
  fi
  if ! wait_healthy "$N8N_SERVICE" 120; then
    step_fail N8N_NOT_HEALTHY "o n8n não está saudável"
    exit 1
  fi
}

confirm_or_exit() {
  if [ "$environment" = "prod" ] && [ -z "${TRANSPORT_COMPOSE:-}" ]; then
    confirm_typed rotacionar "$1" || { echo "Cancelado."; exit 0; }
  fi
}

expect_status() {
  # expect_status <código-do-passo> <esperado> <token> <frase>
  actual="$(token_status "$3")"
  if [ "$actual" = "$2" ]; then
    step_ok "$4"
  else
    step_fail "$1" "$4 — verificação reprovou (status $actual, esperado $2)"
    return 1
  fi
}

case "$command" in
  status)
    step_ok "estado da rotação: $(rotation_state)"
    echo "  token atual (CURRENT): $(filled TELEGRAM_CHANNEL_CURRENT_TOKEN)"
    echo "  token seguinte (NEXT): $(filled TELEGRAM_CHANNEL_NEXT_TOKEN)"
    echo "  token de entrega (ORCHESTRATOR): $(filled TELEGRAM_CHANNEL_ORCHESTRATOR_TOKEN)"
    ;;

  start)
    if [ "$(rotation_state)" != "IDLE" ]; then
      step_fail NOT_IN_IDLE "já há uma rotação em andamento (DUAL); use finish ou abort"
      exit 1
    fi
    [ -n "$(env_value TELEGRAM_CHANNEL_CURRENT_TOKEN)" ] || { step_fail TOKENS_MISSING "TELEGRAM_CHANNEL_CURRENT_TOKEN está vazio"; exit 1; }
    ensure_healthy
    if [ "$dry_run" = "1" ]; then
      echo "(simulação) gravaria TELEGRAM_CHANNEL_NEXT_TOKEN, recriaria $BACKEND_SERVICE e depois $N8N_SERVICE e verificaria os dois tokens"
      exit 0
    fi
    confirm_or_exit "Isto inicia a rotação do token de serviço em PRODUÇÃO."
    new_token="$($COMPOSE exec -T "$N8N_SERVICE" node /opt/ops/generate-token.mjs | tr -d '\r\n')"
    [ -n "$new_token" ] || { step_fail TOKEN_NOT_GENERATED "não foi possível gerar o token novo"; exit 1; }
    backup_env_file
    write_env_key TELEGRAM_CHANNEL_NEXT_TOKEN "$new_token"
    step_ok "token novo gravado (cópia anterior em infra/.env.previous; contém segredos)"
    recreate_service "$BACKEND_SERVICE"
    wait_healthy "$BACKEND_SERVICE" 180 || { step_fail BACKEND_NOT_HEALTHY "o backend não voltou saudável; o estado ficou DUAL (seguro): use abort"; exit 3; }
    step_ok "backend recriado: aceita o token atual e o novo"
    recreate_service "$N8N_SERVICE"
    wait_healthy "$N8N_SERVICE" 180 || { step_fail N8N_NOT_HEALTHY "o n8n não voltou saudável; o estado ficou DUAL (seguro): use abort"; exit 3; }
    step_ok "n8n recriado: passa a enviar o token novo"
    failed=0
    expect_status NEW_TOKEN_REJECTED 404 "$new_token" "o backend aceita o token novo" || failed=1
    expect_status CURRENT_TOKEN_REJECTED 404 "$(env_value TELEGRAM_CHANNEL_CURRENT_TOKEN)" "o backend ainda aceita o token atual" || failed=1
    [ "$failed" = "0" ] || { echo "O estado ficou DUAL; use abort para voltar." >&2; exit 3; }
    step_ok "rotação iniciada (DUAL); conclua com: sh infra/scripts/rotate-service-token.sh finish"
    ;;

  finish)
    if [ "$(rotation_state)" != "DUAL" ]; then
      step_fail NOT_IN_DUAL "não há rotação em andamento; use start"
      exit 1
    fi
    ensure_healthy
    if [ "$dry_run" = "1" ]; then
      echo "(simulação) o token seguinte viraria o atual, o seguinte ficaria vazio e $BACKEND_SERVICE seria recriado"
      exit 0
    fi
    confirm_or_exit "Isto conclui a rotação em PRODUÇÃO: o token antigo passa a ser recusado."
    old_token="$(env_value TELEGRAM_CHANNEL_CURRENT_TOKEN)"
    new_token="$(env_value TELEGRAM_CHANNEL_NEXT_TOKEN)"
    backup_env_file
    write_env_key TELEGRAM_CHANNEL_CURRENT_TOKEN "$new_token"
    write_env_key TELEGRAM_CHANNEL_NEXT_TOKEN ""
    step_ok "o token novo virou o atual"
    recreate_service "$BACKEND_SERVICE"
    wait_healthy "$BACKEND_SERVICE" 180 || { step_fail BACKEND_NOT_HEALTHY "o backend não voltou saudável"; exit 3; }
    step_ok "backend recriado: aceita só o token novo (o n8n já envia esse valor e não precisa reiniciar)"
    failed=0
    expect_status NEW_TOKEN_REJECTED 404 "$new_token" "o backend aceita o token novo" || failed=1
    expect_status OLD_TOKEN_STILL_ACCEPTED 401 "$old_token" "o backend recusa o token antigo" || failed=1
    [ "$failed" = "0" ] || exit 3
    step_ok "rotação concluída (IDLE)"
    ;;

  abort)
    if [ "$(rotation_state)" != "DUAL" ]; then
      step_fail NOT_IN_DUAL "não há rotação em andamento; nada a abortar"
      exit 1
    fi
    if [ "$dry_run" = "1" ]; then
      echo "(simulação) o token seguinte seria descartado e $BACKEND_SERVICE e $N8N_SERVICE seriam recriados"
      exit 0
    fi
    confirm_or_exit "Isto aborta a rotação em PRODUÇÃO."
    discarded="$(env_value TELEGRAM_CHANNEL_NEXT_TOKEN)"
    backup_env_file
    write_env_key TELEGRAM_CHANNEL_NEXT_TOKEN ""
    step_ok "token seguinte descartado"
    # primeiro o n8n volta a enviar o token atual (o backend ainda aceita os dois); só então o backend deixa de aceitar o
    # descartado. Na ordem inversa o n8n enviaria, por alguns segundos, um token que o backend já recusa.
    recreate_service "$N8N_SERVICE"
    wait_healthy "$N8N_SERVICE" 180 || { step_fail N8N_NOT_HEALTHY "o n8n não voltou saudável"; exit 3; }
    recreate_service "$BACKEND_SERVICE"
    wait_healthy "$BACKEND_SERVICE" 180 || { step_fail BACKEND_NOT_HEALTHY "o backend não voltou saudável"; exit 3; }
    step_ok "n8n e backend recriados: voltam ao token atual"
    failed=0
    expect_status CURRENT_TOKEN_REJECTED 404 "$(env_value TELEGRAM_CHANNEL_CURRENT_TOKEN)" "o backend aceita o token atual" || failed=1
    expect_status NEW_TOKEN_REJECTED 401 "$discarded" "o backend recusa o token descartado" || failed=1
    [ "$failed" = "0" ] || exit 3
    step_ok "rotação abortada (IDLE)"
    ;;
esac
