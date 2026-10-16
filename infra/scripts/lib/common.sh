# Funções comuns dos scripts de operação do transporte (feature 004). Arquivo para ser lido com `.` (sourced) por
# n8n.sh, telegram-webhook.sh e rotate-service-token.sh, sempre a partir da raiz do repositório.
#
# Regra de ouro: nenhuma função imprime o valor de uma chave secreta, token ou o conteúdo do infra/.env.
# Saída: uma linha por passo (✔ ou ✘); erros terminam com um código de motivo estável entre colchetes, em inglês.

# Git Bash (Windows) converte caminhos como "/opt/..." antes de passar ao Docker.
export MSYS_NO_PATHCONV=1

ENV_FILE="infra/.env"

# Escolhe as camadas do compose: local (compose.yaml + compose.dev.yaml) ou prod (compose.yaml + compose.prod.yaml).
select_environment() {
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
