#!/bin/sh
# Reproduz, do zero, a troca dos nomes de fio no frontend (T133 a T137), a partir do commit da fatia 3.
#   sh specs/003-migrate-code-to-english/tools/rewrite/apply-slice-4-frontend.sh
# Passos: restaura frontend/src e frontend/tests; gera os tipos ANTIGOS (contrato do HEAD) para o ts-morph achar os usos;
# renomeia propriedades, chaves e literais de fio; regenera tipos e catálogo (contrato e catálogo novos); roda o tsc.
set -eu
export MSYS_NO_PATHCONV=1
git checkout -- frontend/src frontend/tests
mkdir -p frontend/test-results
git show HEAD:specs/001-fluxo-relatorios/contracts/api-portal.openapi.yaml > frontend/test-results/old-api-portal.openapi.yaml
docker compose -f infra/compose.test.yaml -p relatorios-testes-fe run --rm testes-frontend sh -c "
  npm ci >/dev/null 2>&1; npm i --no-save ts-morph >/dev/null 2>&1
  npx openapi-typescript test-results/old-api-portal.openapi.yaml -o src/servicos/api-tipos.ts >/dev/null 2>&1
  node /work/specs/003-migrate-code-to-english/tools/rewrite/frontend-wire.mjs --apply
  npm run gerar >/dev/null 2>&1
  npx tsc -b --noEmit 2>&1 | grep -E '^(src|tests)' | cut -c1-230 || true"

# ---- edições manuais (acompanham o fio; cada uma está registrada em evidence/slice-4.md) ----------------------------------
# tipos estruturais locais que espelham a forma da API
sed -i '17,40s/relatorio\.relatorioId/relatorio.reportId/g; 17,22s/  relatorioId: string;/  reportId: string;/; 17,40s/trabalhoEmCursoId/activeJobId/g' frontend/src/paginas/TelaPerguntas.tsx
# o catálogo cai nesta chave quando o código não existe
sed -i 's/catalogo as Record<string, string>)\.ERRO_INTERNO/catalogo as Record<string, string>).INTERNAL_ERROR/' frontend/src/mensagens/mensagens.ts
# tema guardado no navegador: chave e valores em inglês (T136); o tema interno só muda na fatia 5
node "$(dirname "$0")/patch-tema.mjs"
