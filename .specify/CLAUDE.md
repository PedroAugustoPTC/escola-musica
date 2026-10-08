# .specify/

Infraestrutura do **Spec Kit** (gerada pela ferramenta, `speckit_version` em `init-options.json`).

- `memory/constitution.md` — **constituição do projeto** (9 princípios, restrições, quality gates e
  governança). É a regra suprema; toda spec, plano e PR é avaliado contra ela.
- `templates/` — modelos de spec, plano, tarefas, checklist e constituição.
- `scripts/powershell/` — scripts usados pelas skills `speckit-*` (ex.: `create-new-feature.ps1`).
- `workflows/` e `integrations/` — fluxo do Spec Kit e integração com o Claude.

Regras:

- **Emendar a constituição** exige proposta explícita, aprovação do dono do produto, atualização do texto,
  do Sync Impact Report e da versão (semver), e conferência dos templates. Use a skill `speckit-constitution`.
- Não edite scripts, templates nem workflows para contornar o fluxo; mude-os só de forma deliberada.
- As skills `speckit-*` ficam em `.claude/skills/`.
