#!/bin/sh
# Reproduz, do zero, as renomeações Java da fatia 3 (T062 a T064, T075): restaura o estado-base, roda as receitas e
# confere a compilação. Uso, a partir da raiz do repositório:
#
#   sh specs/003-migrate-code-to-english/tools/rewrite/apply-slice-3-java.sh <árvore-base>
#
# <árvore-base> = `git archive` do commit da fatia 2 (tem `backend/aplicacao` e `backend/core` no estado-base). As
# edições de literais e de recursos são o passo seguinte (`edit-literals.mjs`, `edit-resources.mjs`).
set -eu
BASE="${1:?Informe a árvore-base}"
TOOLS=specs/003-migrate-code-to-english/tools
export MSYS_NO_PATHCONV=1
mvn_in_container() {
  docker run --rm -v "$PWD":/work -v m2-cache:/root/.m2 -w /work/backend maven:3.9-eclipse-temurin-25 mvn -B -ntp -q "$@"
}
# 1. estado-base: aplicação (módulo já movido, fontes antigas) e core
rm -rf backend/application/src
cp -r "$BASE/backend/aplicacao/src" backend/application/src
git checkout HEAD -- backend/core/src
git clean -fdq backend/core/src
# 2. configuração das receitas a partir do estado-base
node $TOOLS/rewrite/gen-recipe-slice-3.mjs --source-root "$BASE" --module-dir backend/aplicacao
# 3. tipos, membros e pacotes da aplicação
sh $TOOLS/rewrite/run-recipe.sh --slice 3 All
find backend/application/src -type d -empty -delete 2>/dev/null || true
for _ in 1 2 3; do find backend/application/src -type d -empty -exec rmdir {} + 2>/dev/null || true; done
mvn_in_container spotless:apply test-compile
# 4. B2: componentes de record retidos no core (esquemas de IA, jsonb, arquivo de carga)
sh $TOOLS/rewrite/run-recipe.sh --slice 3 B2
mvn_in_container spotless:apply test-compile
echo "apply-slice-3-java: concluído"
