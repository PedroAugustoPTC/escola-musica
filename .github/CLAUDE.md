# .github/

Automação do GitHub, versionada no repositório (Princípio VIII).

- `workflows/ci.yml` — três jobs, todos em contêiner via `infra/compose.test.yaml`: **Backend** (unitários,
  arquitetura, integração, contrato, cobertura), **Frontend** (lint, tipos, Vitest) e **Ponta a ponta +
  acessibilidade** (Playwright + axe).
- `workflows/implantar.yml` — implantação na VPS Hostinger (usa `infra/scripts/implantar.sh`).

Regras:

- O CI é a fonte de verdade e bloqueia o merge se a cobertura cair abaixo de 90% ou qualquer teste falhar.
- Segredos só via secrets do GitHub; nunca no YAML. Fixe versões de actions e imagens.
- O CI deve rodar os mesmos comandos de contêiner do ambiente local; ao mudar um, mude o outro.
