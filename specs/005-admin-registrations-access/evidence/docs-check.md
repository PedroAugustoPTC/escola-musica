# Conferência da documentação (T227) — feature 005

Verificado em 2026-10-27, na worktree do fluxo X-3 (branch `feat/005-s5-x3`, saída da `feat/005-s5-closing`, que é a `main` com S1 a S4).

| Item | Resultado |
|---|---|
| `CLAUDE.md` da raiz | Nenhuma estrutura nova de primeiro nível foi criada pela feature 005 (ela só acrescentou módulos, pastas e arquivos dentro de `backend/`, `frontend/`, `infra/` e `specs/`). **Sem linha nova** na tabela "Mapa do projeto". |
| `.claude/CLAUDE.md` | O roteamento (`pre-pr-review` e a tabela "Quem chama quem") não mudou: nenhum agente nem skill novo, e as `description` continuam curtas. **Sem alteração.** |
| `specs/CLAUDE.md` | A linha da 005 já existia; acrescentadas as regras da emenda por `> **Nota (feature 005)**` nas specs 001 e 002 e da pasta `evidence/`. |
| `backend/core/CLAUDE.md` | Atualizado (T223): pacotes `access`, `registry`, `administrator`, `teacher`; `Actor`, `PermissionMatrix`, `StudentScope`; regras de autorização, escopo, autoria e CPF no núcleo. |
| `backend/application/CLAUDE.md` | Atualizado (T224): adaptadores `inbound/web/admin`, `MyStudent*`, `inbound/bootstrap`, `TelegramStudentRegistryFlow`; cadeia de segurança por `@Order`; migrações `V9` a `V15` (sem `V13`); ArchUnit novo. |
| `frontend/CLAUDE.md` | Atualizado (T225): áreas `admin` e `my-students`, `AdminSessionGuard`, `OneTimeSecretPanel`, segundo gerador de tipos, privacidade do CPF. |
| `infra/CLAUDE.md`, `infra/n8n/CLAUDE.md`, `infra/telegram-simulator/CLAUDE.md` | Atualizados (T226): variáveis novas, query string `search`, ação `DELETE_MESSAGE` (n8n continua só transporte) e `deleteMessage` do simulador. |
| `infra/README.md`, `infra/scripts/README.md`, `infra/.env.example` | Atualizados e conferidos (T228, T229). |
| Specs 001 e 002 | Notas `> **Nota (feature 005)**` acrescentadas, sem reescrita (T230 a T232). |
