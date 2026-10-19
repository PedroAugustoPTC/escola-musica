# .github/

Automação do GitHub, versionada no repositório (Princípio VIII).

- `workflows/ci.yml` — quatro jobs, todos em contêiner via `infra/compose.test.yaml`: **Backend** (unitários,
  arquitetura, integração, contrato, cobertura; publica o artefato `jacoco-core`), **Frontend** (lint, tipos,
  Vitest) **Ponta a ponta + acessibilidade** (Playwright + axe) e **n8n** (feature 004: utilitários do `infra/n8n/ops` e a suíte `tests-n8n` pelo n8n real em contêiner, depois do Backend e do Frontend). O job Backend começa por um passo que reprova
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

- O job `n8n` repete um passo só quando o registro de imagens responde `429 Too Many Requests` (limite de taxa do `docker.n8n.io`), via `infra/scripts/retry-rate-limit.sh`; qualquer outra falha reprova na hora.
