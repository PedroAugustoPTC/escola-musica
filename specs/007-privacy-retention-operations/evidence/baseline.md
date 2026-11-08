# Linha de base (T001)

Branch `feat/007-privacy-retention-operations`, criada da `main` em `5f007c8` (já atualizada).
Suítes rodadas em contêiner (`sh infra/scripts/local.sh tests ...`).

| Suíte | Resultado |
|---|---|
| `tests backend` — módulo `core` | 1554 testes, 0 falhas; cobertura mínima (JaCoCo) atendida |
| `tests backend` — módulo `application` (unidade/arquitetura) | 372 testes; guardiões de idioma e `ComposeGuard` verdes; única falha: `OrphanConfigurationGuardTest` por causa das variáveis novas do `.env.example` (T004) que só passam a ser lidas em T019/T024 |
| `tests frontend` | 49 arquivos, 439 testes, 0 falhas |

Observação: a primeira rodada do `application` já incluía o guardião de rotação de logs (T005).
