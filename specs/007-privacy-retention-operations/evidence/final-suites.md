# Evidência final das suítes (T134)

Data: 2026-11-08. Cada suíte rodou em contêiner, uma por vez.

| Suíte | Resultado |
|---|---|
| Backend (`mvn verify`) | 1020 testes, 1 falha: `StudentConcurrencyIT.theBotConfirmationTouchedTwiceAtTheSameTimeSavesOnce` (corrida de concorrência; não toca a 007). Reexecutado isolado: 2/2 verdes. Sem os deadlocks de `truncate` anteriores. |
| Cobertura do núcleo | `jacoco:check` (minimum-coverage) do `core` passou; o build seguiu para o `application`. |
| Guardiões de idioma | Verdes (Java e TypeScript). |
| Frontend (Vitest) | 56 arquivos, 480 testes verdes. |
| E2E (Playwright) | 122 de 124 na rodada completa. As 2 falhas (`consent.spec.ts` e `my-students.spec.ts`) vinham do helper `fillConsent`, que usava a data UTC (08/11) enquanto o servidor usa a de São Paulo (07/11). Corrigido em `support.ts`; reexecução dos dois specs: 5/5 verdes. |

Pendente: T133 (roteiro manual do quickstart).
