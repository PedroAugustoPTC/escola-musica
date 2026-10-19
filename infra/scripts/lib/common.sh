# Funções comuns dos scripts de operação do transporte (feature 004). Arquivo para ser lido com `.` (sourced) por
# n8n.sh, telegram-webhook.sh e rotate-service-token.sh, sempre a partir da raiz do repositório.
#
# Regra de ouro: nenhuma função imprime o valor de uma chave secreta, token ou o conteúdo do infra/.env.
# Saída: uma linha por passo (✔ ou ✘); erros terminam com um código de motivo estável entre colchetes, em inglês.

# Git Bash (Windows) converte caminhos como "/opt/..." antes de passar ao Docker.
export MSYS_NO_PATHCONV=1

# Os ensaios (tests rehearsal) apontam estas duas variáveis para a pilha de teste; o uso normal não as define.
ENV_FILE="${TRANSPORT_ENV_FILE:-infra/.env}"

# Escolhe as camadas do compose: local (compose.yaml + compose.dev.yaml) ou prod (compose.yaml + compose.prod.yaml).
# TRANSPORT_COMPOSE (só nos ensaios) substitui o comando inteiro.
select_environment() {
  if [ -n "${TRANSPORT_COMPOSE:-}" ]; then
    COMPOSE="$TRANSPORT_COMPOSE"
    return 0
  fi
  case "$1" in
    local) COMPOSE="docker compose -f infra/compose.yaml -f infra/compose.dev.yaml" ;;
    prod) COMPOSE="docker compose -f infra/compose.yaml -f infra/compose.prod.yaml" ;;
    *) echo "Ambiente desconhecido: $1 (use local ou prod)" >&2; return 2 ;;
  esac
}

step_ok() {
  echo "✔ $*"
}

# step_fail <CÓDIGO> <frase>
step_fail() {
  code="$1"
  shift
  echo "✘ $* [$code]" >&2
}

require_env_file() {
  if [ ! -f "$ENV_FILE" ]; then
    step_fail ENV_FILE_MISSING "infra/.env não encontrado; crie com: cp infra/.env.example infra/.env (e preencha)"
    return 1
  fi
}

# Valor de uma chave do infra/.env (vazio se não existir). Só para uso interno: nunca imprima o resultado.
env_value() {
  sed -n "s/^$1=//p" "$ENV_FILE" | tail -n 1
}

# Guarda a cópia do infra/.env em infra/.env.previous (contém segredos; ignorado pelo Git).
backup_env_file() {
  cp -p "$ENV_FILE" "$ENV_FILE.previous"
}

# Grava uma chave no infra/.env de forma atômica: arquivo temporário + mv, permissões preservadas.
# Troca a linha existente ou acrescenta ao fim. O valor não passa pela linha de comando do awk (vai pelo ambiente).
write_env_key() {
  tmp="$(mktemp "${ENV_FILE}.XXXXXX")"
  cp -p "$ENV_FILE" "$tmp"
  ENV_KEY="$1" ENV_VALUE="$2" awk '
    BEGIN { key = ENVIRON["ENV_KEY"]; value = ENVIRON["ENV_VALUE"]; found = 0 }
    index($0, key "=") == 1 { print key "=" value; found = 1; next }
    { print }
    END { if (!found) print key "=" value }
  ' "$ENV_FILE" > "$tmp"
  mv "$tmp" "$ENV_FILE"
}

# Pede que o operador digite a palavra para continuar (alterações em produção). `--yes` (YES=1) dispensa.
confirm_typed() {
  word="$1"
  prompt="$2"
  if [ "${YES:-0}" = "1" ]; then
    return 0
  fi
  printf '%s Digite "%s" para confirmar: ' "$prompt" "$word"
  read -r answer
  [ "$answer" = "$word" ]
}

# Espera o serviço ficar saudável (padrão: 180 s). Devolve 1 se não ficar.
wait_healthy() {
  service="$1"
  limit="${2:-180}"
  waited=0
  while [ "$waited" -lt "$limit" ]; do
    if $COMPOSE ps --format '{{.Service}} {{.Health}}' 2>/dev/null | grep -q "^$service healthy"; then
      return 0
    fi
    sleep 2
    waited=$((waited + 2))
  done
  return 1
}

# Recria só o serviço indicado (mesma imagem), sem tocar nas dependências.
recreate_service() {
  $COMPOSE up -d --no-deps --force-recreate "$1" >/dev/null 2>&1
}

# Status HTTP de `GET .../actions/u0-0` com o token informado, chamado de DENTRO do contêiner do n8n:
# 404 = token aceito (a ação não existe), 401 = recusado. O token vai pelo ambiente, nunca pela linha de comando.
token_status() {
  TOKEN_UNDER_TEST="$1" $COMPOSE exec -T -e TOKEN_UNDER_TEST "${N8N_SERVICE:-n8n}"     node /opt/ops/probe.mjs GET '{BACKEND_INTERNAL_URL}/internal/v1/channel/telegram/actions/u0-0'     --header-env X-N8N-Service-Token=TOKEN_UNDER_TEST 2>/dev/null | sed -n '1s/^HTTP //p'
}
