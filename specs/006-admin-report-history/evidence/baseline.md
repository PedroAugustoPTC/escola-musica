# Linha de base e resultado das suítes (feature 006)

**Linha de base anterior à mudança (T001): não foi medida.** A implementação foi feita antes da primeira execução das
suítes, a pedido do dono do produto (testes só no fim, em contêiner). A equivalência do histórico do professor
(SC-006) é provada pelos testes que já existiam e continuaram verdes: `HistoryContractIT`, `HistoryParityScriptIT` e
os testes do `TelegramHistoryFlow`.

## Resultado depois da implementação (2026-11-03)

| Suíte | Comando | Resultado |
|---|---|---|
| Backend | `sh infra/scripts/local.sh tests backend` | `core` 1554, `application` 370 e integração 886 (1 ignorado), todos verdes; cobertura mínima do núcleo atendida (JaCoCo `check`); guardiões de idioma e `HistoryParityScriptIT` verdes |
| Frontend | `sh infra/scripts/local.sh tests frontend` | 49 arquivos, 439 testes verdes (lint, tipos e guardião de idioma incluídos) |
| E2E | `sh infra/scripts/local.sh tests e2e` | 102 verdes, incluindo `admin-school-history` e `admin-school-history.a11y` (axe, claro e escuro, 390 e 1440 px) |

Depois da última execução do frontend, `frontend/src/services/pdf.ts` ganhou o fechamento da aba em branco quando a
requisição do PDF falha; o frontend foi executado de novo antes do PR.
