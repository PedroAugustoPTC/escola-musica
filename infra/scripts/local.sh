#!/bin/sh
# Ambiente local em contêineres (a máquina só precisa do Docker). Rode da raiz do repositório:
#
#   sh infra/scripts/local.sh up              # build + sobe web, backend, postgres (e o bot, se habilitado)
#        --demo-data=true                     #   zera os dados locais e sobe com a massa de demonstração
#        --demo-data=false                    #   zera os dados locais e sobe só o sistema, vazio
#        (sem --demo-data)                    #   mantém os dados que já existem
#   sh infra/scripts/local.sh seed [file]     # carga inicial (padrão: infra/seed/example.json) e códigos
#   sh infra/scripts/local.sh code <email>    # reemite o código de acesso de um professor
#   sh infra/scripts/local.sh status          # estado dos serviços
#   sh infra/scripts/local.sh logs [serviço]  # acompanha os logs (padrão: backend)
#   sh infra/scripts/local.sh database        # abre o psql no banco local (como administrador)
#   sh infra/scripts/local.sh database-port   # publica o banco em 127.0.0.1 para DBeaver/pgAdmin
#   sh infra/scripts/local.sh database-close  # volta a deixar o banco só na rede interna
#   sh infra/scripts/local.sh tests [suite]   # backend | frontend | e2e | n8n | rehearsal | real-ai | all (padrão: all)
#   sh infra/scripts/local.sh stop            # para a pilha local (mantém os dados)
#   sh infra/scripts/local.sh clean           # para e APAGA os dados locais (banco, PDFs, certificados)
#
# Com o Telegram habilitado, sobe também o n8n (transporte do canal; infra/scripts/n8n.sh opera o n8n).
# Usa infra/.env (copie de infra/.env.example). Passo a passo em infra/scripts/README.md.
set -eu

# Git Bash (Windows) converte "/seed/..." em caminho do Windows antes de passar ao Docker.
export MSYS_NO_PATHCONV=1

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT"

LOCAL="docker compose -f infra/compose.yaml -f infra/compose.dev.yaml"
TESTS="docker compose -f infra/compose.test.yaml"

value() {
  sed -n "s/^$1=//p" infra/.env | tail -n 1
}

require_env() {
  if [ ! -f infra/.env ]; then
    echo "infra/.env não encontrado. Crie com: cp infra/.env.example infra/.env (e preencha)." >&2
    exit 1
  fi
  missing=""
  for key in POSTGRES_PASSWORD CPF_ENCRYPTION_KEY HMAC_SECRET; do
    [ -n "$(value "$key")" ] || missing="$missing $key"
  done
  if [ "$(value TELEGRAM_ENABLED)" = "true" ]; then
    for key in TELEGRAM_BOT_TOKEN TELEGRAM_API_ID TELEGRAM_API_HASH TELEGRAM_WEBHOOK_SECRET N8N_ENCRYPTION_KEY TELEGRAM_CHANNEL_CURRENT_TOKEN TELEGRAM_CHANNEL_ORCHESTRATOR_TOKEN; do
      [ -n "$(value "$key")" ] || missing="$missing $key"
    done
  fi
  if [ "$(value AI_PROVIDERS)" = "real" ]; then
    for key in TRANSCRIPTION_API_KEY LLM_API_KEY; do
      [ -n "$(value "$key")" ] || missing="$missing $key"
    done
  fi
  if [ -n "$missing" ]; then
    echo "Preencha em infra/.env:$missing" >&2
    echo "(sem o bot: TELEGRAM_ENABLED=false; sem chaves de IA: AI_PROVIDERS=simulated)" >&2
    echo "Se o seu infra/.env é anterior à feature 003, regere-o a partir de infra/.env.example." >&2
    exit 1
  fi
}

# Apaga só o banco e os PDFs locais (mantém os certificados do Caddy e o volume do bot).
reset_data() {
  echo "Zerando os dados locais (banco e PDFs)..."
  $LOCAL down --remove-orphans
  docker volume rm -f reports_pgdata reports_documents >/dev/null
}

services() {
  if [ "$(value TELEGRAM_ENABLED)" = "true" ]; then
    echo "web backend postgres telegram-bot-api n8n"
  else
    echo "web backend postgres"
  fi
}

wait_until_healthy() {
  printf 'Aguardando os serviços ficarem saudáveis'
  attempts=0
  while [ "$attempts" -lt 60 ]; do
    states="$($LOCAL ps --format '{{.Service}} {{.Health}}' 2>/dev/null || true)"
    n8n_ready=true
    if [ "$(value TELEGRAM_ENABLED)" = "true" ] && ! echo "$states" | grep -q '^n8n healthy'; then
      n8n_ready=false
    fi
    if echo "$states" | grep -q '^backend healthy' && echo "$states" | grep -q '^web healthy' && $n8n_ready; then
      echo " pronto."
      return 0
    fi
    if echo "$states" | grep -q ' unhealthy$'; then
      echo
      $LOCAL ps
      echo "Algum serviço ficou unhealthy. Veja: sh infra/scripts/local.sh logs" >&2
      return 1
    fi
    printf '.'
    attempts=$((attempts + 1))
    sleep 5
  done
  echo
  $LOCAL ps
  echo "Os serviços não ficaram saudáveis em 5 minutos. Veja: sh infra/scripts/local.sh logs" >&2
  return 1
}

command="${1:-help}"
[ "$#" -gt 0 ] && shift

case "$command" in
  up)
    demo_data=""
    while [ "$#" -gt 0 ]; do
      case "$1" in
        --demo-data=true|--demo-data=false) demo_data="${1#--demo-data=}" ;;
        --demo-data) demo_data="${2:-}"; shift ;;
        *) echo "Opção desconhecida: $1 (use --demo-data=true ou --demo-data=false)" >&2; exit 2 ;;
      esac
      shift
    done
    case "$demo_data" in
      ""|true|false) ;;
      *) echo "Use --demo-data=true ou --demo-data=false" >&2; exit 2 ;;
    esac
    require_env
    [ -n "$demo_data" ] && reset_data
    # shellcheck disable=SC2046
    $LOCAL up -d --build --remove-orphans $(services)
    wait_until_healthy
    if [ "$(value TELEGRAM_ENABLED)" = "true" ]; then
      # o script do webhook é o único dono do registro do webhook, que aponta para o n8n
      sh infra/scripts/telegram-webhook.sh apply --env local         || echo "Aviso: o registro/verificação do webhook falhou; veja: sh infra/scripts/telegram-webhook.sh status" >&2
    fi
    if [ "$demo_data" = "true" ]; then
      echo "Gerando a massa de demonstração (cadastros, aulas, semestrais e PDFs)..."
      if demo_data_output="$($LOCAL run --rm backend demo-data --file /seed/demo.json 2>&1)"; then
        echo "$demo_data_output" | grep -v -e '^{' -e '^Picked up' -e 'Container ' -e 'WARN ' -e 'INFO::' || true
      else
        echo "$demo_data_output" | grep -v -e '^{' -e '^Picked up' -e 'Container ' -e 'WARN ' -e 'INFO::' >&2 || true
        echo "A massa de demonstração falhou; o sistema está no ar, mas vazio." >&2
        exit 1
      fi
    fi
    echo
    echo "Portal: https://$(value DOMAIN | sed 's/^$/localhost/')  (certificado local do Caddy: aceite no navegador)"
    echo "IA: $(value AI_PROVIDERS) · Telegram: $(value TELEGRAM_ENABLED)"
    case "$demo_data" in
      true) echo "Massa carregada: entre com o código da Clara mostrado acima." ;;
      false) echo "Sistema vazio. Para cadastros de exemplo: sh infra/scripts/local.sh seed" ;;
      *) echo "Dados mantidos. Massa de demonstração: sh infra/scripts/local.sh up --demo-data=true" ;;
    esac
    ;;
  seed)
    require_env
    file="${1:-infra/seed/example.json}"
    case "$file" in
      infra/seed/*) ;;
      *) echo "O arquivo precisa estar em infra/seed/ (montado em /seed no contêiner)." >&2; exit 1 ;;
    esac
    $LOCAL run --rm backend seed --file "/seed/${file#infra/seed/}"
    ;;
  code)
    require_env
    [ "$#" -ge 1 ] || { echo "Uso: sh infra/scripts/local.sh code <email-do-professor>" >&2; exit 2; }
    $LOCAL run --rm backend issue-code --email "$1"
    ;;
  status)
    $LOCAL ps
    ;;
  logs)
    $LOCAL logs -f --tail=200 "${1:-backend}"
    ;;
  database)
    # com terminal: psql interativo; sem terminal (ex.: echo "select ..." | local.sh database): -T
    if [ -t 0 ]; then
      $LOCAL exec postgres psql -U reports -d reports
    else
      $LOCAL exec -T postgres psql -U reports -d reports
    fi
    ;;
  database-port)
    require_env
    $LOCAL -f infra/compose.database.yaml up -d postgres
    port="$(value LOCAL_DATABASE_PORT)"
    echo "Banco publicado só nesta máquina: host localhost, porta ${port:-5432}, banco reports,"
    echo "usuário reports, senha = POSTGRES_PASSWORD do infra/.env."
    echo "Ao terminar: sh infra/scripts/local.sh database-close"
    ;;
  database-close)
    require_env
    $LOCAL up -d postgres
    echo "Banco de volta só na rede interna dos contêineres."
    ;;
  tests)
    suite="${1:-all}"
    case "$suite" in
      backend) $TESTS run --rm tests-backend ;;
      frontend) $TESTS run --rm tests-frontend ;;
      e2e) $TESTS run --rm --build tests-e2e ;;
      n8n)
        # utilitários do n8n e do simulador (node:test nas próprias imagens) e, depois, a suíte pelo n8n real em contêiner
        $TESTS run --rm --build tests-n8n-ops
        $TESTS run --rm --build tests-simulator
        $TESTS run --rm --build --use-aliases tests-n8n
        ;;
      rehearsal)
        # ensaio do registro do webhook e da rotação do token: backend real, n8n real e simulador do Telegram
        cp infra/tests/rehearsal/rehearsal.env.template infra/tests/rehearsal/rehearsal.env
        REHEARSAL="$TESTS --env-file infra/tests/rehearsal/rehearsal.env"
        rehearsal_status=0
        $REHEARSAL up -d --build --wait postgres-rehearsal telegram-simulator backend-rehearsal n8n-rehearsal \
          && $REHEARSAL run --rm tests-rehearsal || rehearsal_status=$?
        $TESTS down -v >/dev/null 2>&1 || true
        rm -f infra/tests/rehearsal/rehearsal.env infra/tests/rehearsal/rehearsal.env.previous
        exit $rehearsal_status
        ;;
      real-ai)
        require_env
        # a suíte lê as chaves do ambiente; sem elas, é ignorada
        TRANSCRIPTION_API_KEY="$(value TRANSCRIPTION_API_KEY)" LLM_API_KEY="$(value LLM_API_KEY)" \
          $TESTS run --rm -e TRANSCRIPTION_API_KEY -e LLM_API_KEY tests-real-ai
        echo "Relatório: backend/application/target/real-ai/report.md"
        ;;
      all)
        $TESTS run --rm tests-backend
        $TESTS run --rm tests-frontend
        $TESTS run --rm --build tests-e2e
        $TESTS run --rm --build tests-n8n-ops
        $TESTS run --rm --build tests-simulator
        $TESTS run --rm --build --use-aliases tests-n8n
        sh "$0" tests rehearsal
        ;;
      *) echo "Use: backend | frontend | e2e | n8n | rehearsal | real-ai | all" >&2; exit 2 ;;
    esac
    $TESTS down -v >/dev/null 2>&1 || true
    ;;
  stop)
    $LOCAL down
    ;;
  clean)
    printf 'Isto apaga o banco, os PDFs e os certificados LOCAIS. Digite "apagar" para confirmar: '
    read -r confirmation
    [ "$confirmation" = "apagar" ] || { echo "Cancelado."; exit 0; }
    $LOCAL down -v
    ;;
  help|-h|--help)
    sed -n '2,20p' "$0" | sed 's/^# \{0,1\}//'
    ;;
  *)
    echo "Comando desconhecido: $command" >&2
    sed -n '2,20p' "$0" | sed 's/^# \{0,1\}//' >&2
    exit 2
    ;;
esac
