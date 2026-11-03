# Quickstart (Q1 a Q13): como foi verificado

O quickstart **não foi executado à mão** num ambiente local. Cada passo é coberto por teste automatizado, todos verdes
em 2026-11-03:

| Passo | Coberto por |
|---|---|
| Q1–Q4 (histórico de um aluno, ordem, ativo e inativo) | `QuerySchoolHistoryUseCaseTest`, `ApprovedReportQueryAdapterIT`, `AdminSchoolHistoryContractIT`, `admin-school-history.spec.ts` |
| Q5–Q6 (filtros e paginação, desempenho) | `SchoolHistoryFilterTest`, `AdminSchoolHistoryFiltersContractIT`, `ApprovedReportQueryPerformanceIT`, e2e de filtros |
| Q7–Q10 (PDF, acesso cruzado, 404 indistinguível) | `GetSchoolReportDocumentUseCaseTest`, `AdminSchoolReportDocumentContractIT`, `SchoolHistoryCrossAccessIT`, `SchoolReportDocumentIndistinguishableIT`, `SchoolHistoryOnlyApprovedIT` |
| Q11 (registro de autoria, atomicidade, privacidade) | `AuthorshipSchoolHistoryIT`, `SchoolHistoryAuthorshipAtomicityIT`, `SchoolHistoryCanaryIT` |
| Q12 (Telegram sem acesso administrativo, histórico do professor igual) | `SchoolHistoryNeutralRefusalIT`, `AdapterRulesTest`, `HistoryParityScriptIT` |
| Q13 (acessibilidade) | `admin-school-history.a11y.spec.ts` |

Pendente de conferência humana: contraste das opções ativas do seletor de aluno e leitura em leitor de tela.

Desvios conhecidos (aceitos): a credencial cruzada ainda não grava `AUTHORIZATION_DENIED` (pendência da 005); o evento
E2 só é gravado para `from > to`, porque os erros de forma (data, tipo, página) são recusados no controlador.
