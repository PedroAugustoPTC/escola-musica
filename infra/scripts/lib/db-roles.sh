# Papel de banco da aplicação (feature 007, R-03). Arquivo para ser lido com `.` (sourced) por local.sh e deploy.sh,
# sempre a partir da raiz do repositório.
#
# A aplicação conecta como `reports_app` (sem UPDATE/DELETE na trilha de auditoria); o Flyway e o dono seguem com
# `reports`. A migração V17 concede os privilégios; este passo só garante que o papel EXISTE com LOGIN e senha antes de
# o backend subir, e é idempotente. A senha vem de DB_APP_PASSWORD em infra/.env, chega ao contêiner por variável de
# ambiente (nunca na linha de comando) e nunca é impressa.
#
#   apply_database_roles "<comando do compose>"

apply_database_roles() {
  compose="$1"
  password="$(sed -n 's/^DB_APP_PASSWORD=//p' infra/.env | tail -n 1 | tr -d '\r')"
  if [ -z "$password" ]; then
    echo "✘ DB_APP_PASSWORD vazia em infra/.env; gere um valor longo e aleatório [DB_APP_PASSWORD_MISSING]" >&2
    return 1
  fi
  # \set com crase lê a variável do ambiente do contêiner; :'pw' escapa o valor como literal SQL.
  if DB_APP_PASSWORD="$password" $compose exec -T -e DB_APP_PASSWORD postgres \
      psql -q -v ON_ERROR_STOP=1 -U reports -d reports >/dev/null <<'SQL'
\set pw `printenv DB_APP_PASSWORD`
DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'reports_app') THEN
    CREATE ROLE reports_app NOLOGIN;
  END IF;
END
$$;
ALTER ROLE reports_app LOGIN PASSWORD :'pw';
SQL
  then
    echo "✔ papel de banco da aplicação pronto"
  else
    echo "✘ não foi possível preparar o papel de banco da aplicação [DB_ROLE_FAILED]" >&2
    return 1
  fi
}
