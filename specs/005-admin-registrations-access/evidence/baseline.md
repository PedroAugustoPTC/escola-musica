# Linha de base (T003) — antes da feature 005

Medida em 2026-10-20, em worktree limpa do commit `7cfb7c8` (spec 005 + `main`), com
`COMPOSE_PROJECT_NAME` isolado.

## Backend (`sh infra/scripts/local.sh tests backend`)

| Módulo | Testes | Falhas | Observação |
|---|---|---|---|
| `core` | 324 | 0 | Gate `minimum-coverage` do JaCoCo atendido (179 classes) |
| `application` | 274 | 5 | Veja abaixo |

As 5 falhas esperadas vêm da emenda da 005 aos contratos da 001 (aluno não associado passa de
`403 STUDENT_NOT_ASSIGNED` para `404 RESOURCE_NOT_FOUND`): o contrato já foi emendado, o código ainda
responde 403. Elas ficam verdes com T008/T023: `HistoryContractIT`, `HomeStudentsContractIT`,
`LessonReportContractIT`, `ResumptionContractIT`, `SemesterReportContractIT`.

## Armadilha de ambiente

`infra/compose.test.yaml` fixa `name: reports-tests`. Duas execuções de `local.sh tests …` ao mesmo
tempo na mesma máquina derrubam o `postgres-test` uma da outra (`UnknownHostException: postgres-test`,
250 erros). Em paralelo, use `COMPOSE_PROJECT_NAME` distinto por worktree, ou serialize as execuções.

## Frontend (`tests frontend`)

88 testes verdes ao fim de T027–T030 (antes: o conjunto existente).
