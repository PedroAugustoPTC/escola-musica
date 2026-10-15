# specs/

Documentação de cada feature, produzida pelo fluxo **Spec Kit** (`specify → clarify → plan → tasks → implement`).
Uma pasta por feature, numerada em sequência: `NNN-nome-da-feature/`.

Conteúdo típico de cada pasta: `spec.md` (o quê e por quê), `plan.md` (como; inclui o "Constitution
Check"), `research.md`, `data-model.md`, `quickstart.md`, `tasks.md`, `contracts/` (OpenAPI, JSON
Schema, mensagens) e `checklists/`.

| Feature | Assunto |
|---|---|
| `001-fluxo-relatorios` | Fluxo de relatórios por voz (aula e semestral), Portal e Telegram |
| `002-api-interna-canal-telegram` | API interna que o n8n usa como transporte do Telegram |
| `003-migrate-code-to-english` | Migração do código existente de português para inglês (refatoração, comportamento idêntico) |

Regras:

- Não implemente sem spec aprovada; use as skills `speckit-*` (em `.claude/skills/`).
- Idioma (Princípio VI): specs, planos, tasks e demais documentos em **português**; nomes de contratos
  (rotas, campos, códigos de erro) em **inglês**. Os contratos de fio (`contracts/*.yaml`, `*.json`) passaram a
  inglês e o guardião de idioma os varre; as specs 001 e 002 são **histórico**: citam nomes anteriores à migração e
  trazem uma nota no topo remetendo ao glossário (`.specify/memory/glossary.json`). Não as reescreva.
  A pasta de cada feature mantém o nome em português (`001-fluxo-relatorios`).
- Os `contracts/` são fonte de verdade: o backend os implementa e o frontend gera tipos a partir deles
  (`001-fluxo-relatorios/contracts/api-portal.openapi.yaml`). Mudança de API começa pelo contrato.
- Specs descrevem comportamento de negócio, sem fixar stack; a stack é decidida e justificada no `plan.md`.
- Desvios da constituição entram na seção de complexidade do plano, com justificativa.
- Cada feature nova usa branch própria criada a partir da `main` atualizada (ver `CLAUDE.md` da raiz).
