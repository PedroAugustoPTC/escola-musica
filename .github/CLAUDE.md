# .github/

Automação do GitHub, versionada no repositório (Princípio VIII).

- `workflows/ci.yml` — três jobs, todos em contêiner via `infra/compose.test.yaml`: **Backend** (unitários,
  arquitetura, integração, contrato, cobertura; publica o artefato `jacoco-core`), **Frontend** (lint, tipos,
  Vitest) e **Ponta a ponta + acessibilidade** (Playwright + axe). O job Backend começa por um passo que reprova
  o PR se `.specify/memory/glossary-temporary-exclusions.json` existir: o guardião de idioma não aceita lista de
  exclusão (ver `.specify/CLAUDE.md`).
- `workflows/deploy.yml` — implantação manual na VPS (usa `infra/scripts/deploy.sh`); ambiente `production` e segredos
  `VPS_HOST`, `VPS_USER`, `VPS_DIRECTORY`, `VPS_SSH_KEY` e `VPS_KNOWN_HOST`.

Regras:

- O CI é a fonte de verdade e bloqueia o merge se a cobertura cair abaixo de 90% ou qualquer teste falhar.
- Segredos só via secrets do GitHub; nunca no YAML. Fixe versões de actions e imagens.
- O CI deve rodar os mesmos comandos de contêiner do ambiente local; ao mudar um, mude o outro.
- Nomes de workflow, job, passo (`id`), artefato e segredo em inglês (Princípio VI); o texto do `name:` mostrado às
  pessoas segue em português.
