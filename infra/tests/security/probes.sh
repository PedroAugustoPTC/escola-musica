#!/bin/sh
# Sondas dirigidas contra a pilha isolada de testes. Saída: /out/probes/results.jsonl (uma linha por sonda).
# Credenciais fictícias da semeadura do e2e; nenhum valor sensível é gravado nos resultados.
set -u
OUT=/out/probes
mkdir -p "$OUT"
RESULTS="$OUT/results.jsonl"
: > "$RESULTS"
T="$TARGET"
CURL="curl -sk --max-time 30"
TMP=$(mktemp -d)

record() { # id verdict title detail
  jq -cn --arg id "$1" --arg verdict "$2" --arg title "$3" --arg detail "$4" \
    '{id:$id,verdict:$verdict,title:$title,detail:$detail}' >> "$RESULTS"
  echo "[$2] $1 $3 - $4"
}
code_of() { $CURL -o /dev/null -w '%{http_code}' "$@"; }
cookie_value() { awk -v n="$2" '$6==n{print $7}' "$1" | tail -1; }
# curl grava cookies HttpOnly com o prefixo "#HttpOnly_": normaliza para a leitura acima
normalize() { sed -i 's/^#HttpOnly_//' "$1"; }

# o login também exige o par cookie/cabeçalho de CSRF: uma requisição prévia semeia o XSRF-TOKEN no jar
prime_csrf() { $CURL -c "$1" -o /dev/null "$T/api/v1/session"; normalize "$1"; }
teacher_login() { # jar code
  prime_csrf "$1"
  $CURL -b "$1" -c "$1" -H "X-CSRF-TOKEN: $(cookie_value "$1" XSRF-TOKEN)" -H 'Content-Type: application/json' \
    -d "{\"code\":\"$2\"}" "$T/api/v1/session" -o /dev/null -w '%{http_code}'
  normalize "$1"
}

# --- sessões -------------------------------------------------------------------------------------------------------
CLARA="$TMP/clara.jar"; BRUNO="$TMP/bruno.jar"; ADMIN="$TMP/admin.jar"
[ "$(teacher_login "$CLARA" K7QM2XPA)" = 200 ] || { record SETUP FAIL "login da professora A" "login falhou"; exit 1; }
[ "$(teacher_login "$BRUNO" BRUN2XPA)" = 200 ] || { record SETUP FAIL "login do professor B" "login falhou"; exit 1; }
prime_csrf "$ADMIN"
admin_status=$($CURL -b "$ADMIN" -c "$ADMIN" -H "X-CSRF-TOKEN: $(cookie_value "$ADMIN" XSRF-TOKEN)" -H 'Content-Type: application/json' \
  -d "{\"email\":\"$ADMIN_EMAIL\",\"password\":\"$ADMIN_PASSWORD\"}" "$T/api/v1/admin/session" -o "$TMP/admin-login.json" -w '%{http_code}')
normalize "$ADMIN"
[ "$admin_status" = 200 ] || { record SETUP FAIL "login do administrador" "HTTP $admin_status"; exit 1; }
CSRF_A=$(cookie_value "$CLARA" XSRF-TOKEN); CSRF_ADM=$(cookie_value "$ADMIN" XSRF-TOKEN)

list_ids() { $CURL -b "$1" "$T/api/v1/students" | jq -r '.. | objects | select(has("id")) | .id' 2>/dev/null | head -3; }
STUDENT_A=$(list_ids "$CLARA" | head -1)
STUDENT_B=$(list_ids "$BRUNO" | head -1)
echo "alunos: A=${STUDENT_A:-?} B=${STUDENT_B:-?}"

# --- 1. BOLA/IDOR --------------------------------------------------------------------------------------------------
MISSING=00000000-0000-4000-8000-000000000000
base_missing=$($CURL -b "$BRUNO" -w '|%{http_code}' "$T/api/v1/students/$MISSING" | tr -d '\n')
for path in "students/$STUDENT_A" "students/$STUDENT_A/history" "students/$STUDENT_A/lesson-reports" \
            "students/$STUDENT_A/semesters" "students/$STUDENT_A/open-report" "my-students/$STUDENT_A"; do
  code=$(code_of -b "$BRUNO" "$T/api/v1/$path")
  id="BOLA-$(echo "$path" | tr '/' '-' | cut -c1-60)"
  case "$code" in
    404|403) record "$id" PASS "professor B lê recurso de A" "HTTP $code" ;;
    *) record "$id" FAIL "professor B lê recurso de A" "HTTP $code" ;;
  esac
done
for path in "jobs/$MISSING" "lesson-reports/$MISSING" "semester-reports/$MISSING" "lesson-reports/$MISSING/official-pdf"; do
  code=$(code_of -b "$BRUNO" "$T/api/v1/$path")
  record "BOLA-missing-$(echo "$path" | cut -d/ -f1-3 | tr '/' '-' | cut -c1-40)" INFO "recurso inexistente" "HTTP $code"
done
a_missing=$($CURL -b "$BRUNO" -w '|%{http_code}' "$T/api/v1/students/$STUDENT_A" | tr -d '\n')
if [ "$a_missing" = "$base_missing" ]; then
  record BOLA-identical PASS "404 de aluno alheio idêntico ao de inexistente" "corpo e status iguais"
else
  record BOLA-identical FAIL "404 de aluno alheio difere do de inexistente" "o corpo ou o status revela a existência"
fi

# --- 2. escalada de papel -------------------------------------------------------------------------------------------
for route in admin/teachers admin/students admin/administrators admin/audit-trail admin/operations/alerts; do
  code=$(code_of -b "$CLARA" "$T/api/v1/$route")
  if [ "$code" = 404 ]; then
    record "ROLE-teacher-$(echo "$route" | tr '/' '-')" PASS "cookie de professor em rota administrativa" "HTTP 404"
  else
    record "ROLE-teacher-$(echo "$route" | tr '/' '-')" FAIL "cookie de professor em rota administrativa" "HTTP $code"
  fi
done
code=$(code_of -b "$ADMIN" "$T/api/v1/students")
if [ "$code" = 404 ] || [ "$code" = 401 ] || [ "$code" = 403 ]; then
  record ROLE-admin-students PASS "cookie de administrador em rota do professor" "negado com HTTP $code"
else
  record ROLE-admin-students FAIL "cookie de administrador em rota do professor" "HTTP $code"
fi

# --- 3. cookies e sessão --------------------------------------------------------------------------------------------
FLAGS="$TMP/flags.jar"; prime_csrf "$FLAGS"
hdr=$($CURL -b "$FLAGS" -D - -o /dev/null -H "X-CSRF-TOKEN: $(cookie_value "$FLAGS" XSRF-TOKEN)" -H 'Content-Type: application/json' \
  -d '{"code":"K7QM2XPA"}' "$T/api/v1/session" | tr -d '\r' | grep -i '^set-cookie: session=' || true)
flags_ok=1
for flag in httponly secure samesite; do echo "$hdr" | grep -qi "$flag" || flags_ok=0; done
if [ "$flags_ok" = 1 ]; then
  record SESSION-flags PASS "flags do cookie de sessão do professor" "HttpOnly, Secure e SameSite presentes"
else
  record SESSION-flags FAIL "flags do cookie de sessão do professor" "falta HttpOnly, Secure ou SameSite"
fi
REUSE="$TMP/reuse.jar"; teacher_login "$REUSE" K7QM2XPA >/dev/null
SAVED=$(cookie_value "$REUSE" session)
$CURL -b "$REUSE" -H "X-CSRF-TOKEN: $(cookie_value "$REUSE" XSRF-TOKEN)" -X DELETE "$T/api/v1/session" -o /dev/null
code=$(code_of -H "Cookie: session=$SAVED" "$T/api/v1/session")
if [ "$code" = 401 ]; then
  record SESSION-logout-reuse PASS "token reutilizado após logout" "HTTP 401"
else
  record SESSION-logout-reuse FAIL "token reutilizado após logout" "HTTP $code"
fi
FIX="$TMP/fix.jar"; teacher_login "$FIX" K7QM2XPA >/dev/null
if [ "$(cookie_value "$FIX" session)" != "$(cookie_value "$CLARA" session)" ]; then
  record SESSION-new-id PASS "identificador novo a cada login" "id diferente"
else
  record SESSION-new-id FAIL "identificador repetido no login" "id igual"
fi

# --- 4. CSRF ---------------------------------------------------------------------------------------------------------
for spec in "POST students/$STUDENT_A/lesson-reports" "POST jobs/$MISSING/discard" "POST lesson-reports/$MISSING/approval"; do
  set -- $spec
  none=$(code_of -b "$CLARA" -X "$1" -H 'Content-Type: application/json' -d '{}' "$T/api/v1/$2")
  bad=$(code_of -b "$CLARA" -X "$1" -H 'X-CSRF-TOKEN: forjado' -H 'Content-Type: application/json' -d '{}' "$T/api/v1/$2")
  if [ "$none" = 403 ] && [ "$bad" = 403 ]; then
    record "CSRF-$(echo "$2" | cut -d/ -f1,3 | tr '/' '-')" PASS "gravação sem token ou com token forjado" "403/403"
  else
    record "CSRF-$(echo "$2" | cut -d/ -f1,3 | tr '/' '-')" FAIL "gravação sem token ou com token forjado" "sem=$none forjado=$bad"
  fi
done
none=$(code_of -b "$ADMIN" -X POST -H 'Content-Type: application/json' -d '{}' "$T/api/v1/admin/teachers")
bad=$(code_of -b "$ADMIN" -X POST -H 'X-CSRF-TOKEN: forjado' -H 'Content-Type: application/json' -d '{}' "$T/api/v1/admin/teachers")
if [ "$none" = 403 ] && [ "$bad" = 403 ]; then
  record CSRF-admin PASS "administrador sem token ou com token forjado" "403/403"
else
  record CSRF-admin FAIL "administrador sem token ou com token forjado" "sem=$none forjado=$bad"
fi

# --- 5. força bruta e limites ----------------------------------------------------------------------------------------
BF="$TMP/bf.jar"; prime_csrf "$BF"; BF_TOKEN=$(cookie_value "$BF" XSRF-TOKEN)
codes=""
for i in 1 2 3 4 5 6 7 8; do
  codes="$codes $(code_of -b "$BF" -H "X-CSRF-TOKEN: $BF_TOKEN" -H 'Content-Type: application/json' -H "X-Forwarded-For: 10.9.9.$i" -d '{"code":"WRONGCODE"}' "$T/api/v1/session")"
done
if echo "$codes" | grep -q 429; then
  record BRUTE-teacher-code PASS "bloqueio do código do professor" "429 após tentativas:$codes"
else
  record BRUTE-teacher-code FAIL "sem bloqueio do código do professor" "respostas: $codes"
fi
record BRUTE-xff INFO "contorno por X-Forwarded-For forjado" "o Caddy sobrescreve X-Forwarded-For; 8 tentativas com origens forjadas:$codes"
burst=""
for i in $(seq 1 30); do
  burst="$burst $(code_of -b "$BF" -H "X-CSRF-TOKEN: $BF_TOKEN" -H 'Content-Type: application/json' -d '{"email":"naoexiste@example.test","password":"x"}' "$T/api/v1/admin/session")"
done
if echo "$burst" | grep -q 429; then
  record BRUTE-admin-signin PASS "bloqueio do login do administrador" "429 presente"
else
  record BRUTE-admin-signin FAIL "sem bloqueio do login do administrador" "30 tentativas:$burst"
fi

# --- 6. enumeração ----------------------------------------------------------------------------------------------------
t1=$($CURL -b "$BF" -o /dev/null -w '%{time_total}' -H "X-CSRF-TOKEN: $BF_TOKEN" -H 'Content-Type: application/json' -d '{"email":"admin@example.test","password":"errada-errada-1"}' "$T/api/v1/admin/session")
t2=$($CURL -b "$BF" -o /dev/null -w '%{time_total}' -H "X-CSRF-TOKEN: $BF_TOKEN" -H 'Content-Type: application/json' -d '{"email":"missing@example.test","password":"errada-errada-1"}' "$T/api/v1/admin/session")
record ENUM-admin-timing INFO "tempo de resposta e-mail existente x inexistente" "existente=${t1}s inexistente=${t2}s"

# --- 7. upload --------------------------------------------------------------------------------------------------------
echo '<html><script>alert(1)</script></html>' > "$TMP/evil.html"
head -c 2048 /dev/urandom > "$TMP/garbage.bin"
for name in evil.html garbage.bin; do
  resp=$($CURL -b "$CLARA" -H "X-CSRF-TOKEN: $CSRF_A" -F "audio=@$TMP/$name;type=audio/mpeg" -w '|%{http_code}' \
    "$T/api/v1/students/$STUDENT_A/lesson-reports")
  record "UPLOAD-$name" INFO "arquivo falso declarado como audio/mpeg" "$(echo "$resp" | tail -c 200 | tr '\n' ' ')"
done
code=$($CURL -b "$CLARA" -H "X-CSRF-TOKEN: $CSRF_A" -X POST -H 'Content-Type: multipart/form-data; boundary=x' --data-binary '' -o /dev/null -w '%{http_code}' "$T/api/v1/students/$STUDENT_A/lesson-reports")
record UPLOAD-empty-multipart INFO "multipart vazio" "HTTP $code"
dd if=/dev/zero of="$TMP/big.wav" bs=1M count=56 2>/dev/null
code=$($CURL -b "$CLARA" -H "X-CSRF-TOKEN: $CSRF_A" -F "audio=@$TMP/big.wav;type=audio/wav" -o /dev/null -w '%{http_code}' "$T/api/v1/students/$STUDENT_A/lesson-reports")
if [ "$code" = 413 ]; then
  record UPLOAD-too-big PASS "arquivo de 56 MB" "HTTP 413"
else
  record UPLOAD-too-big INFO "arquivo de 56 MB" "HTTP $code"
fi

# --- 8. injeção -------------------------------------------------------------------------------------------------------
for payload in "'" "' OR '1'='1" "'; select pg_sleep(5)--" "%" "_"; do
  s=$(date +%s)
  code=$(code_of -b "$ADMIN" -G --data-urlencode "search=$payload" "$T/api/v1/admin/students")
  e=$(( $(date +%s) - s ))
  sid="SQLI-$(printf '%s' "$payload" | md5sum | cut -c1-6)"
  if [ "$code" -lt 500 ] && [ "$e" -lt 4 ]; then
    record "$sid" PASS "SQLi em search do administrador" "HTTP $code em ${e}s"
  else
    record "$sid" FAIL "SQLi em search do administrador" "HTTP $code em ${e}s"
  fi
done
XSS='<img src=x onerror=alert(1)><script>alert(1)</script>'
# CPF fictício com dígitos verificadores corretos e aleatório (evita colidir com a semeadura)
cpf_digit() { # dígitos pesoInicial
  sum=0; w=$2; for d in $(echo "$1" | sed 's/./& /g'); do sum=$((sum + d * w)); w=$((w - 1)); done
  r=$(( (sum * 10) % 11 )); [ "$r" -ge 10 ] && r=0; echo "$r"
}
base=$(printf '%09d' $(( ($(od -An -N4 -tu4 /dev/urandom | tr -d ' ') % 900000000) + 100000000 )))
d1=$(cpf_digit "$base" 10); d2=$(cpf_digit "$base$d1" 11)
PROBE_CPF="$base$d1$d2"
code=$($CURL -b "$ADMIN" -H "X-CSRF-TOKEN: $CSRF_ADM" -H 'Content-Type: application/json' -o "$TMP/xss.json" -w '%{http_code}' \
  -d "{\"name\":\"$XSS\",\"birthDate\":\"1990-01-01\",\"cpf\":\"$PROBE_CPF\",\"instrument\":\"Piano\"}" "$T/api/v1/admin/students")
record XSS-student-name INFO "nome de aluno com HTML (armazenado)" "HTTP $code $(head -c 120 "$TMP/xss.json" | tr '\n' ' '); conferir no SPA e no PDF (revisão manual)"

# --- 9. exposição -----------------------------------------------------------------------------------------------------
for path in actuator actuator/health actuator/prometheus actuator/env internal/ error swagger-ui.html v3/api-docs .env .git/config \
            assets/index.js.map api/v1/error api/v1/actuator/prometheus; do
  # o proxy devolve o index.html do SPA para rotas desconhecidas: só é exposição real se o corpo NÃO for o SPA
  code=$($CURL -o "$TMP/expose.out" -w '%{http_code}' "$T/$path")
  if [ "$code" = 200 ] && ! grep -qi '<div id="root"' "$TMP/expose.out"; then
    record "EXPOSE-$(echo "$path" | tr '/.' '--')" FAIL "exposição de /$path pelo proxy" "HTTP 200 com conteúdo próprio ($(head -c 80 "$TMP/expose.out" | tr '\n' ' '))"
  else
    record "EXPOSE-$(echo "$path" | tr '/.' '--')" PASS "/$path não exposto pelo proxy" "HTTP $code$([ "$code" = 200 ] && echo ' (fallback do SPA)')"
  fi
done
for method in TRACE OPTIONS PUT; do
  code=$(code_of -X $method "$T/api/v1/session")
  record "METHOD-$method" INFO "método $method em /api/v1/session" "HTTP $code"
done
cors=$($CURL -D - -o /dev/null -H 'Origin: https://evil.example' "$T/api/v1/session" | tr -d '\r' | grep -i 'access-control' || true)
if [ -z "$cors" ]; then
  record CORS-arbitrary PASS "CORS com Origin arbitrário" "sem Access-Control-*"
else
  record CORS-arbitrary FAIL "CORS com Origin arbitrário" "$cors"
fi
err=$($CURL -b "$CLARA" -X POST -H "X-CSRF-TOKEN: $CSRF_A" -H 'Content-Type: application/json' -d '{"broken' "$T/api/v1/students/$STUDENT_A/lesson-reports")
if echo "$err" | grep -qiE 'exception|at br\.com|stacktrace|org\.springframework'; then
  record ERROR-leak FAIL "vazamento em erro de JSON malformado" "stack trace ou classe no corpo"
else
  record ERROR-leak PASS "erro de JSON malformado" "sem stack trace"
fi

# --- 10. cabeçalhos ---------------------------------------------------------------------------------------------------
H=$($CURL -D - -o /dev/null "$T/" | tr -d '\r')
for h in strict-transport-security content-security-policy x-content-type-options referrer-policy permissions-policy; do
  if echo "$H" | grep -qi "^$h:"; then
    record "HEADER-$h" PASS "cabeçalho $h" "cabeçalho presente"
  else
    record "HEADER-$h" FAIL "cabeçalho $h" "cabeçalho ausente"
  fi
done
if echo "$H" | grep -i '^content-security-policy' | grep -q "style-src[^;]*unsafe-inline"; then
  record HEADER-csp-unsafe-inline INFO "CSP com style-src unsafe-inline" "cabeçalho presente"
fi
cc=$($CURL -b "$CLARA" -D - -o /dev/null "$T/api/v1/students" | tr -d '\r' | grep -i '^cache-control' || true)
record HEADER-cache-personal-data INFO "Cache-Control em resposta com dado pessoal" "$cc"

# --- 11. canal interno (direto no backend) -----------------------------------------------------------------------------
for path in internal/channel/updates internal/health internal/channel/actions; do
  none=$(code_of -X POST -H 'Content-Type: application/json' -d '{}' "$BACKEND_DIRECT/$path")
  bad=$(code_of -X POST -H 'X-N8N-Service-Token: errado' -H 'Content-Type: application/json' -d '{}' "$BACKEND_DIRECT/$path")
  record "INTERNAL-$(echo "$path" | tr '/' '-')" INFO "/$path sem token e com token errado" "sem=$none errado=$bad"
done
code=$(code_of "$BACKEND_DIRECT/actuator/prometheus")
if [ "$code" = 200 ]; then
  record INTERNAL-prometheus FAIL "/actuator/prometheus sem autenticação na rede interna" "HTTP 200 (métricas visíveis a qualquer contêiner da rede)"
else
  record INTERNAL-prometheus PASS "/actuator/prometheus sem autenticação" "HTTP $code"
fi
$CURL -o "$TMP/prom.out" "$T/actuator/prometheus"
if grep -q '^# HELP' "$TMP/prom.out"; then
  record PROXY-prometheus FAIL "/actuator/prometheus pelo proxy público" "métricas expostas"
else
  record PROXY-prometheus PASS "/actuator/prometheus pelo proxy público" "sem métricas (fallback do SPA ou negado)"
fi

rm -rf "$TMP"
echo "Resultados: $RESULTS ($(wc -l < "$RESULTS") sondas)"
