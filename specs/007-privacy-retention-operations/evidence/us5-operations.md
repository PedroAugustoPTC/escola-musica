# Evidência da US5 — tempo, custo e alertas

Data: 2026-11-07. Sem dado pessoal.

| Verificação | Resultado |
|---|---|
| Núcleo: `OperationalAlertServiceTest`, `EvaluateOperationalAlertsUseCaseTest`, `OperationalAlertReadUseCasesTest`, `GetMonthlyCostReportUseCaseTest`, `JobProcessorCostTest` | verdes |
| `OperationalAlertIT` (SC-006, relógio ajustável, passo de avaliação de 60 s, limite de 5 min): cada condição simulada vira um único alerta ativo e normaliza quando some | 7 testes verdes |
| `CostReportIT` (SC-008): 10 aulas e 2 semestrais, dois canais e dois provedores; total do painel difere menos de 1% da soma das chamadas | 2 testes verdes |
| `AdminOperationsContractIT` (custos e alertas, `no-store`, permissões) | 8 testes verdes |
| `MetricsAndAlertsCanaryIT`: aluno canário não aparece em métricas nem em alertas | verde |
| `AuditEventCoverageIT` e `PermissionMatrixCrossAccessIT` (operações novas) | 6 e 51 testes verdes |
| `ComposeGuardTest`: nenhum contêiner nem porta publicada acrescentado | verde |
| Frontend (Vitest) | 480 testes, 56 arquivos, verdes |
| E2E (Playwright, inclui `admin-operations.spec.ts` e `admin-operations.a11y.spec.ts`) | 124 testes, verdes (9,1 min) |

## Como foi rodado (anti-travamento)

Cada suíte rodou sozinha, com `timeout` dentro do contêiner e `down -v` antes, sem a suíte backend completa.
Os relatórios `failsafe` de ontem (`AuditTrailTamperIT` com 606 s e `AdminSecurityChainIT` com deadlock no `truncate`)
são de execuções antigas, não desta.

## Lacunas conhecidas

- A suíte backend completa não foi rerodada nesta história; fica para o fecho (T130–T136).
