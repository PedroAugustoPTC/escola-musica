# .specify/

Infraestrutura do **Spec Kit** (gerada pela ferramenta, `speckit_version` em `init-options.json`).

- `memory/constitution.md` — **constituição do projeto** (9 princípios, restrições, quality gates e
  governança). É a regra suprema; toda spec, plano e PR é avaliado contra ela.
- `memory/glossary.json` — **glossário do teste guardião de idioma** (complemento oficial do glossário do
  Princípio VI, criado pela feature 003). `words`: palavra em
  português (minúscula, sem acento, singular) → equivalente em inglês mostrado na falha; `contextual`:
  palavras polissêmicas; `exceptions`: exceções documentadas, cada uma com `reason` e
  `constitutionCategory` **obrigatórios** (sem eles o guardião nem carrega), `mode` (`term`, `skip-file` ou
  `builtin`) e `appliesTo` (guardiões que DEVEM usá-la: exceção que não casa com nada é "morta" e reprova).
  Termo novo entra aqui **antes** do uso (FR-017), com um único equivalente em inglês.
- **Sem exclusões**: o guardião varre o repositório inteiro. O arquivo `glossary-temporary-exclusions.json` que a
  feature 003 usou durante a migração foi apagado na última fatia; o `ci.yml` reprova o PR se ele reaparecer
  (`test ! -e .specify/memory/glossary-temporary-exclusions.json`). Não crie lista de exclusão: o que não pode ser
  traduzido (nome de terceiro, nome próprio, texto do usuário) entra como **exceção documentada** no
  `glossary.json`, com `reason` e `constitutionCategory`.
- `templates/` — modelos de spec, plano, tarefas, checklist e constituição.
- `scripts/powershell/` — scripts usados pelas skills `speckit-*` (ex.: `create-new-feature.ps1`).
- `workflows/` e `integrations/` — fluxo do Spec Kit e integração com o Claude.

Regras:

- **Emendar a constituição** exige proposta explícita, aprovação do dono do produto, atualização do texto,
  do Sync Impact Report e da versão (semver), e conferência dos templates. Use a skill `speckit-constitution`.
- Não edite scripts, templates nem workflows para contornar o fluxo; mude-os só de forma deliberada.
- As skills `speckit-*` ficam em `.claude/skills/`.
- **Quem consome o glossário**: três guardiões que rodam nos contêineres de teste, a partir do volume
  `..:/work`: `LanguageGuardTest` (Java: bytecode com ArchUnit e varredura de fonte) e
  `ArtifactsLanguageGuardIT` (catálogo do banco, YAML, JSON, `.properties`, `.env.example`, scripts, XML,
  HTML/CSS de PDF, contratos), ambos em `backend/application/src/test/.../architecture`, via
  `sh infra/scripts/local.sh tests backend`; e `frontend/tests/language-guard.test.ts` (TypeScript, JSX e CSS),
  via `tests frontend`. Cada um tem autotestes (`*SelfTest`, `language-guard.selftest.test.ts`).
- **Modo relatório**: `GUARD_REPORT=1` faz o guardião gravar todas as violações em
  `backend/application/target/guard-violations-{java,artifacts}.json` e
  `frontend/test-results/guard-violations.json` em vez de reprovar; serve para listar tudo de uma vez ao
  introduzir uma regra nova.
  Exemplo: `docker compose -f infra/compose.test.yaml run --rm -e GUARD_REPORT=1 tests-frontend`.
- O que o guardião **não** examina (FR-023): texto mostrado ao usuário (literal com espaço, acento ou uma
  palavra iniciada em maiúscula), logs, comentários, documentação e as palavras que o professor digita.
