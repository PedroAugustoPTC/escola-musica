#!/bin/sh
# Avaliação de segurança de ponta a ponta (chamada por `sh infra/scripts/local.sh tests security`). Rode da raiz do repositório.
# Tudo em contêiner. Saídas brutas em security/out/ (ignorado pelo Git: pode conter achados sensíveis).
# O DAST e as sondas atacam SÓ a pilha isolada de testes (postgres-e2e, backend-e2e, web-e2e), nunca o ambiente local nem a VPS.
set -u
export MSYS_NO_PATHCONV=1
SEC="docker compose -f infra/compose.test.yaml -f infra/compose.security.yaml"
status=0

mkdir -p security/out
rm -rf security/out/*

# senha aleatória do administrador de teste, só desta execução (nunca em arquivo)
SECURITY_ADMIN_PASSWORD="Sx$(head -c 18 /dev/urandom | base64 | tr -d '/+=\n')9"
export SECURITY_ADMIN_PASSWORD

step() { echo; echo "════ $1 ════"; }
run() { "$@" || { echo "falhou: $*" >&2; status=1; }; }

step "Estáticas: segredos, SAST, SCA, IaC"
run $SEC run --rm security-secrets
run $SEC run --rm security-sast
run $SEC run --rm security-sca
run $SEC run --rm security-sca-npm
run $SEC run --rm security-iac

step "Pilha isolada de testes (build e subida)"
if $SEC up -d --build --wait postgres-e2e backend-e2e web-e2e; then
  step "Imagens construídas"
  run $SEC run --rm security-images
  step "TLS"
  run $SEC run --rm security-tls
  step "Semeadura + DAST"
  run $SEC run --rm security-seed
  run $SEC run --rm security-dast
  step "Semeadura limpa + sondas"
  run $SEC run --rm security-seed
  run $SEC run --rm security-probes
else
  echo "a pilha de testes não subiu" >&2
  status=1
  $SEC logs --no-color --tail 40 backend-e2e web-e2e 2>&1 | cut -c1-300
fi

$SEC down -v >/dev/null 2>&1 || true
echo
echo "Saídas brutas: security/out/ (um diretório por ferramenta). Relatório: security/reports/."
exit $status
