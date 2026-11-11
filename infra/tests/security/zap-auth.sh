#!/bin/sh
# Faz login (professora e administrador), injeta as credenciais por regras replacer e roda o plano do ZAP, uma vez por papel.
set -eu
OUT=/out/dast
mkdir -p "$OUT"
T=https://web-e2e.test
TMP=$(mktemp -d)
for who in teacher admin; do
  if [ "$who" = teacher ]; then
    url=$T/api/v1/session
    body='{"code":"K7QM2XPA"}'
  else
    url=$T/api/v1/admin/session
    body="{\"email\":\"$ADMIN_EMAIL\",\"password\":\"$ADMIN_PASSWORD\"}"
  fi
  # o login exige o par cookie/cabeçalho de CSRF: uma requisição prévia semeia o XSRF-TOKEN
  curl -sk -c "$TMP/$who.jar" -o /dev/null "$url"
  sed -i 's/^#HttpOnly_//' "$TMP/$who.jar"
  token=$(awk '$6=="XSRF-TOKEN"{print $7}' "$TMP/$who.jar" | tail -1)
  curl -sk -b "$TMP/$who.jar" -c "$TMP/$who.jar" -H "X-CSRF-TOKEN: $token" -H 'Content-Type: application/json' -d "$body" "$url" \
    -o /dev/null -w "$who login: %{http_code}\n"
  sed -i 's/^#HttpOnly_//' "$TMP/$who.jar"
done
for who in teacher admin; do
  name=session
  [ "$who" = admin ] && name=admin_session
  session=$(awk -v n="$name" '$6==n{print $7}' "$TMP/$who.jar" | tail -1)
  xsrf=$(awk '$6=="XSRF-TOKEN"{print $7}' "$TMP/$who.jar" | tail -1)
  portal=$(ls /work/specs/001-*/contracts/api-portal.openapi.yaml | head -1)
  sed -e "s|__PORTAL_CONTRACT__|$portal|" -e "s|__COOKIE__|$name=$session; XSRF-TOKEN=$xsrf|" -e "s|__CSRF__|$xsrf|" -e "s|__CONTEXT__|$who|g" \
    /work/infra/tests/security/zap/automation.yaml > "$TMP/plan-$who.yaml"
  zap.sh -cmd -silent -autorun "$TMP/plan-$who.yaml" || true
done
rm -rf "$TMP"
ls -l "$OUT"
