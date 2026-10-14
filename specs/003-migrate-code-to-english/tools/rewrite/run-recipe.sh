#!/bin/sh
# Roda uma receita da fatia no contêiner do Maven (fatia 2: T036 a T038; fatia 3: T062 a T064 e T075). Uso, a partir da
# raiz do repositório:
#
#   sh specs/003-migrate-code-to-english/tools/rewrite/run-recipe.sh [--slice 2|3] Packages|Members|All|B2
#
# Compila cada módulo (test-compile) e roda o rewrite-maven-plugin em seguida, módulo a módulo, na ordem do reator:
# o `core` é reescrito primeiro e a aplicação compila contra as classes antigas do `core` (consistentes com as
# fontes da aplicação ainda não reescritas). O plugin é passado por linha de comando: nada entra nos pom do produto.
# `B2` (fatia 3) usa `slice-3-b2.yml` e `slice-3-b2-members.json`. A receita Java própria (`recipes/`) deve estar
# instalada no cache do Maven (`m2-cache`):
#   docker run --rm -v "$PWD/specs/003-migrate-code-to-english/tools/rewrite/recipes":/workspace \
#     -v m2-cache:/root/.m2 -w /workspace maven:3.9-eclipse-temurin-25 mvn -B -q install
set -eu
SLICE=2
if [ "${1:-}" = "--slice" ]; then
  SLICE="${2:?Informe a fatia}"
  shift 2
fi
RECIPE="${1:?Informe Packages, Members, All ou B2}"
CONFIG="slice-${SLICE}"
if [ "$RECIPE" = "B2" ]; then
  CONFIG="slice-${SLICE}-b2"
fi
export MSYS_NO_PATHCONV=1
docker run --rm \
  -v "$PWD":/work \
  -v m2-cache:/root/.m2 \
  -w /work/backend \
  maven:3.9-eclipse-temurin-25 \
  mvn -B -ntp test-compile \
    org.openrewrite.maven:rewrite-maven-plugin:6.46.1:run \
    -Drewrite.configLocation="/work/specs/003-migrate-code-to-english/tools/rewrite/${CONFIG}.yml" \
    -Drewrite.activeRecipes="br.com.felipeblaft.migration.Slice${SLICE}.${RECIPE}" \
    -Drewrite.recipeArtifactCoordinates=br.com.felipeblaft.migration:rename-recipes:1.0.0 \
    -Drename.mapFile="/work/specs/003-migrate-code-to-english/tools/rewrite/${CONFIG}-members.json" \
    -Drewrite.exportDatatables=false
