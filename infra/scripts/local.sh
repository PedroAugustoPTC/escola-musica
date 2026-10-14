#!/bin/sh
# Ambiente local em contêineres (a máquina só precisa do Docker). Rode da raiz do repositório:
#
#   sh infra/scripts/local.sh subir          # build + sobe web, backend, postgres (e o bot, se habilitado)
#        --massa=true                        #   zera os dados locais e sobe com a massa de demonstração
#        --massa=false                       #   zera os dados locais e sobe só o sistema, vazio
#        (sem --massa)                       #   mantém os dados que já existem
#   sh infra/scripts/local.sh carga [arq]    # carga inicial (padrão: infra/carga/exemplo.json) e códigos
#   sh infra/scripts/local.sh codigo <email> # reemite o código de acesso de um professor
#   sh infra/scripts/local.sh status         # estado dos serviços
#   sh infra/scripts/local.sh logs [serviço] # acompanha os logs (padrão: backend)
#   sh infra/scripts/local.sh banco          # abre o psql no banco local (como administrador)
#   sh infra/scripts/local.sh banco-porta    # publica o banco em 127.0.0.1 para DBeaver/pgAdmin
#   sh infra/scripts/local.sh banco-fechar   # volta a deixar o banco só na rede interna
#   sh infra/scripts/local.sh testes [qual]  # backend | frontend | e2e | ia-real | todos (padrão: todos)
#   sh infra/scripts/local.sh parar          # para a pilha local (mantém os dados)
#   sh infra/scripts/local.sh limpar         # para e APAGA os dados locais (banco, PDFs, certificados)
#
# Usa infra/.env (copie de infra/.env.example). Passo a passo em infra/scripts/README.md.
set -eu

# Git Bash (Windows) converte "/carga/..." em caminho do Windows antes de passar ao Docker.
export MSYS_NO_PATHCONV=1

RAIZ="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$RAIZ"

LOCAL="docker compose -f infra/compose.yaml -f infra/compose.dev.yaml"
TESTES="docker compose -f infra/compose.test.yaml"

valor() {
  sed -n "s/^$1=//p" infra/.env | tail -n 1
}

exigir_env() {
  if [ ! -f infra/.env ]; then
    echo "infra/.env não encontrado. Crie com: cp infra/.env.example infra/.env (e preencha)." >&2
    exit 1
  fi
  faltando=""
  for chave in POSTGRES_PASSWORD CPF_ENCRYPTION_KEY HMAC_SECRET; do
    [ -n "$(valor "$chave")" ] || faltando="$faltando $chave"
  done
  if [ "$(valor TELEGRAM_ENABLED)" = "true" ]; then
    for chave in TELEGRAM_BOT_TOKEN TELEGRAM_API_ID TELEGRAM_API_HASH TELEGRAM_WEBHOOK_SECRET; do
      [ -n "$(valor "$chave")" ] || faltando="$faltando $chave"
    done
  fi
  if [ "$(valor AI_PROVIDERS)" = "real" ]; then
    for chave in TRANSCRIPTION_API_KEY LLM_API_KEY; do
      [ -n "$(valor "$chave")" ] || faltando="$faltando $chave"
    done
  fi
  if [ -n "$faltando" ]; then
    echo "Preencha em infra/.env:$faltando" >&2
    echo "(sem o bot: TELEGRAM_ENABLED=false; sem chaves de IA: AI_PROVIDERS=simulated)" >&2
    exit 1
  fi
}

# Apaga só o banco e os PDFs locais (mantém os certificados do Caddy e o volume do bot).
zerar_dados() {
  echo "Zerando os dados locais (banco e PDFs)..."
  $LOCAL down --remove-orphans
  docker volume rm -f relatorios_pgdata relatorios_documentos >/dev/null
}

servicos() {
  if [ "$(valor TELEGRAM_ENABLED)" = "true" ]; then
    echo "web backend postgres telegram-bot-api"
  else
    echo "web backend postgres"
  fi
}

aguardar() {
  printf 'Aguardando os serviços ficarem saudáveis'
  tentativas=0
  while [ "$tentativas" -lt 60 ]; do
    estados="$($LOCAL ps --format '{{.Service}} {{.Health}}' 2>/dev/null || true)"
    if echo "$estados" | grep -q '^backend healthy' && echo "$estados" | grep -q '^web healthy'; then
      echo " pronto."
      return 0
    fi
    if echo "$estados" | grep -q ' unhealthy$'; then
      echo
      $LOCAL ps
      echo "Algum serviço ficou unhealthy. Veja: sh infra/scripts/local.sh logs" >&2
      return 1
    fi
    printf '.'
    tentativas=$((tentativas + 1))
    sleep 5
  done
  echo
  $LOCAL ps
  echo "Os serviços não ficaram saudáveis em 5 minutos. Veja: sh infra/scripts/local.sh logs" >&2
  return 1
}

comando="${1:-ajuda}"
[ "$#" -gt 0 ] && shift

case "$comando" in
  subir)
    massa=""
    while [ "$#" -gt 0 ]; do
      case "$1" in
        --massa=true|--massa=false) massa="${1#--massa=}" ;;
        --massa) massa="${2:-}"; shift ;;
        *) echo "Opção desconhecida: $1 (use --massa=true ou --massa=false)" >&2; exit 2 ;;
      esac
      shift
    done
    case "$massa" in
      ""|true|false) ;;
      *) echo "Use --massa=true ou --massa=false" >&2; exit 2 ;;
    esac
    exigir_env
    [ -n "$massa" ] && zerar_dados
    # shellcheck disable=SC2046
    $LOCAL up -d --build --remove-orphans $(servicos)
    aguardar
    if [ "$massa" = "true" ]; then
      echo "Gerando a massa de demonstração (cadastros, aulas, semestrais e PDFs)..."
      if saida_massa="$($LOCAL run --rm backend demo-data --file /carga/demonstracao.json 2>&1)"; then
        echo "$saida_massa" | grep -v -e '^{' -e '^Picked up' -e 'Container ' -e 'WARN ' -e 'INFO::' || true
      else
        echo "$saida_massa" | grep -v -e '^{' -e '^Picked up' -e 'Container ' -e 'WARN ' -e 'INFO::' >&2 || true
        echo "A massa de demonstração falhou; o sistema está no ar, mas vazio." >&2
        exit 1
      fi
    fi
    echo
    echo "Portal: https://$(valor DOMAIN | sed 's/^$/localhost/')  (certificado local do Caddy: aceite no navegador)"
    echo "IA: $(valor AI_PROVIDERS) · Telegram: $(valor TELEGRAM_ENABLED)"
    case "$massa" in
      true) echo "Massa carregada: entre com o código da Clara mostrado acima." ;;
      false) echo "Sistema vazio. Para cadastros de exemplo: sh infra/scripts/local.sh carga" ;;
      *) echo "Dados mantidos. Massa de demonstração: sh infra/scripts/local.sh subir --massa=true" ;;
    esac
    ;;
  carga)
    exigir_env
    arquivo="${1:-infra/carga/exemplo.json}"
    case "$arquivo" in
      infra/carga/*) ;;
      *) echo "O arquivo precisa estar em infra/carga/ (montado em /carga no contêiner)." >&2; exit 1 ;;
    esac
    $LOCAL run --rm backend seed --file "/carga/${arquivo#infra/carga/}"
    ;;
  codigo)
    exigir_env
    [ "$#" -ge 1 ] || { echo "Uso: sh infra/scripts/local.sh codigo <email-do-professor>" >&2; exit 2; }
    $LOCAL run --rm backend issue-code --email "$1"
    ;;
  status)
    $LOCAL ps
    ;;
  logs)
    $LOCAL logs -f --tail=200 "${1:-backend}"
    ;;
  banco)
    # com terminal: psql interativo; sem terminal (ex.: echo "select ..." | local.sh banco): -T
    if [ -t 0 ]; then
      $LOCAL exec postgres psql -U relatorios -d relatorios
    else
      $LOCAL exec -T postgres psql -U relatorios -d relatorios
    fi
    ;;
  banco-porta)
    exigir_env
    $LOCAL -f infra/compose.banco.yaml up -d postgres
    porta="$(valor LOCAL_DATABASE_PORT)"
    echo "Banco publicado só nesta máquina: host localhost, porta ${porta:-5432}, banco relatorios,"
    echo "usuário relatorios, senha = POSTGRES_PASSWORD do infra/.env."
    echo "Ao terminar: sh infra/scripts/local.sh banco-fechar"
    ;;
  banco-fechar)
    exigir_env
    $LOCAL up -d postgres
    echo "Banco de volta só na rede interna dos contêineres."
    ;;
  testes)
    qual="${1:-todos}"
    case "$qual" in
      backend) $TESTES run --rm testes-backend ;;
      frontend) $TESTES run --rm testes-frontend ;;
      e2e) $TESTES run --rm --build testes-e2e ;;
      ia-real)
        exigir_env
        # a suíte lê as chaves do ambiente; sem elas, é ignorada
        TRANSCRIPTION_API_KEY="$(valor TRANSCRIPTION_API_KEY)" LLM_API_KEY="$(valor LLM_API_KEY)" \
          $TESTES run --rm -e TRANSCRIPTION_API_KEY -e LLM_API_KEY testes-ia-real
        echo "Relatório: backend/application/target/ia-real/relatorio.md"
        ;;
      todos)
        $TESTES run --rm testes-backend
        $TESTES run --rm testes-frontend
        $TESTES run --rm --build testes-e2e
        ;;
      *) echo "Use: backend | frontend | e2e | ia-real | todos" >&2; exit 2 ;;
    esac
    $TESTES down -v >/dev/null 2>&1 || true
    ;;
  parar)
    $LOCAL down
    ;;
  limpar)
    printf 'Isto apaga o banco, os PDFs e os certificados LOCAIS. Digite "apagar" para confirmar: '
    read -r confirmacao
    [ "$confirmacao" = "apagar" ] || { echo "Cancelado."; exit 0; }
    $LOCAL down -v
    ;;
  ajuda|-h|--help)
    sed -n '2,19p' "$0" | sed 's/^# \{0,1\}//'
    ;;
  *)
    echo "Comando desconhecido: $comando" >&2
    sed -n '2,19p' "$0" | sed 's/^# \{0,1\}//' >&2
    exit 2
    ;;
esac
