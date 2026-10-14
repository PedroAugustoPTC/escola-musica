#!/bin/sh
# Edições de literais Java e de recursos/configuração da fatia 3 (T065 a T074, T076 a T079), a partir do estado que
# `apply-slice-3-java.sh` deixa. Idempotente quando reaplicado sobre o mesmo ponto de partida. A partir da raiz:
#
#   sh specs/003-migrate-code-to-english/tools/rewrite/apply-slice-3-files.sh
set -eu
TOOLS=specs/003-migrate-code-to-english/tools
R=backend/application/src/main/resources
T=backend/application/src/test/resources

# 1. literais Java que são código (consultas, nomes de tabela/coluna, chaves de configuração, CLI, modelo de PDF…) e
#    as edições manuais listadas em explicit-pairs.json → literals (slice 3)
node $TOOLS/rewrite/edit-literals.mjs --slice 3 --dir backend/core,backend/application --apply --fresh

# 2. arquivos de recurso: mover para os nomes novos (identifier-map.md §8)
mkdir -p $R/ai/schemas $R/ai/simulated $R/messages
mv $R/ia/schemas/relatorio-aula.schema.json $R/ai/schemas/lesson-report.schema.json
mv $R/ia/schemas/relatorio-semestral.schema.json $R/ai/schemas/semester-report.schema.json
mv $R/ia/simulado/estruturacao-aula.json $R/ai/simulated/lesson-structuring.json
mv $R/ia/simulado/consolidacao-semestral.json $R/ai/simulated/semester-consolidation.json
mv $R/ia/simulado/transcricao.txt $R/ai/simulated/transcription.txt
rmdir $R/ia/schemas $R/ia/simulado $R/ia
mv $R/prompts/consolidacao-semestral.v1.txt $R/prompts/semester-consolidation.v1.txt
mv $R/prompts/correcao-json.v1.txt $R/prompts/json-correction.v1.txt
mv $R/prompts/estruturacao-aula.v1.txt $R/prompts/lesson-structuring.v1.txt
mv $R/prompts/incorporacao-respostas.v1.txt $R/prompts/answer-incorporation.v1.txt
mv $R/prompts/revisao-aula.v1.txt $R/prompts/lesson-revision.v1.txt
mv $R/prompts/revisao-semestral.v1.txt $R/prompts/semester-revision.v1.txt
mv $R/pdf/estilos-pdf.css $R/pdf/pdf-styles.css
mv $R/pdf/relatorio-aula.html $R/pdf/lesson-report.html
mv $R/pdf/relatorio-semestral.html $R/pdf/semester-report.html
mv $R/pdf/fontes $R/pdf/fonts
mv $R/pdf/imagens $R/pdf/images
mv $R/mensagens/mensagens_pt_BR.properties $R/messages/messages_pt_BR.properties
rmdir $R/mensagens
mv $T/application-teste.yaml $T/application-test.yaml

# 3. identificadores dentro dos recursos
node $TOOLS/rewrite/edit-resources.mjs --slice 3 --profile config --apply $R/application.yaml $T/application-test.yaml
node $TOOLS/rewrite/edit-env-comments.mjs --apply $R/application.yaml $T/application-test.yaml
node $TOOLS/rewrite/edit-resources.mjs --slice 3 --profile ident --json-schema --apply $R/ai/schemas/lesson-report.schema.json $R/ai/schemas/semester-report.schema.json
node $TOOLS/rewrite/edit-resources.mjs --slice 3 --profile ident --apply $R/ai/simulated/lesson-structuring.json $R/ai/simulated/semester-consolidation.json
node $TOOLS/rewrite/edit-prompts.mjs --slice 3 --apply $R/prompts/*.txt
node $TOOLS/rewrite/edit-resources.mjs --slice 3 --profile ident --apply $R/pdf/pdf-styles.css $R/pdf/lesson-report.html $R/pdf/semester-report.html
node $TOOLS/rewrite/edit-resources.mjs --slice 3 --profile ident --apply infra/carga/exemplo.json infra/carga/demonstracao.json backend/application/docker-entrypoint.sh

# 4. edições de texto exato (poms, Dockerfile, esquemas, logback, compose, scripts)
node $TOOLS/rewrite/apply-file-edits.mjs
echo "apply-slice-3-files: concluído"
