#!/bin/sh
# Roda uma receita da fatia 2 no contêiner do Maven (T036, T037, T038). Uso, a partir da raiz do repositório:
#
#   sh specs/003-migrate-code-to-english/tools/rewrite/run-recipe.sh Packages|Types|Members
#
# Compila cada módulo (test-compile) e roda o rewrite-maven-plugin em seguida, módulo a módulo, na ordem do reator:
# o `core` é reescrito primeiro e a `aplicacao` compila contra as classes antigas do `core` (consistentes com as
# fontes da `aplicacao` ainda não reescritas). O plugin é passado por linha de comando: nada entra nos pom do produto.
# A receita Java própria (`recipes/`) deve estar instalada no cache do Maven (`m2-cache`):
#   docker run --rm -v "$PWD/specs/003-migrate-code-to-english/tools/rewrite/recipes":/workspace \
#     -v m2-cache:/root/.m2 -w /workspace maven:3.9-eclipse-temurin-25 mvn -B -q install
set -eu
RECIPE="${1:?Informe Packages, Types ou Members}"
export MSYS_NO_PATHCONV=1
docker run --rm \
  -v "$PWD":/work \
  -v m2-cache:/root/.m2 \
  -w /work/backend \
  maven:3.9-eclipse-temurin-25 \
  mvn -B -ntp test-compile \
    org.openrewrite.maven:rewrite-maven-plugin:6.46.1:run \
    -Drewrite.configLocation=/work/specs/003-migrate-code-to-english/tools/rewrite/slice-2.yml \
    -Drewrite.activeRecipes="br.com.felipeblaft.migration.Slice2.${RECIPE}" \
    -Drewrite.recipeArtifactCoordinates=br.com.felipeblaft.migration:rename-recipes:1.0.0 \
    -Drename.mapFile=/work/specs/003-migrate-code-to-english/tools/rewrite/slice-2-members.json \
    -Drewrite.exportDatatables=false
