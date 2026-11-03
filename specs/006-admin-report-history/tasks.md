---

description: "Tarefas da feature 006 — visão da escola para o administrador"
---

# Tasks: Administração — visão da escola (histórico de relatórios aprovados, somente leitura, somente Web)

**Input**: `specs/006-admin-report-history/` — [spec.md](./spec.md), [plan.md](./plan.md), [research.md](./research.md),
[data-model.md](./data-model.md), [contracts/](./contracts/), [quickstart.md](./quickstart.md)

**Tests**: pedidos (Princípio V e pedido do `/speckit-tasks`). Em cada história, os testes vêm **antes** e devem falhar
antes da implementação.

**Organização**: uma fase por história da spec (US1 a US4), na ordem de prioridade. O pedido citava também "paridade com
o Telegram (paginação por botões)"; o `/speckit-clarify` fechou a feature como **somente Web** (Princípio I), então a
Fase 7 não cria fluxo no bot: ela **prova** que o bot não ganhou nada e que o histórico do professor não mudou.

## Formato: `- [ ] T### [P?] [US#?] Descrição com caminho do arquivo`

- **[P]**: pode rodar em paralelo — arquivo(s) exclusivo(s) e nenhuma dependência de tarefa ainda aberta, salvo a escrita
  como "(após T###)".
- **[US#]**: história da spec. Setup, Fundação, Telegram e Fecho não levam rótulo.
- Identificadores em **inglês**; textos de tela e de log em **português acentuado** (Princípio VI). Todos os termos já
  estão no `glossary.json`; palavra portuguesa nova em identificador exige o guardião com `GUARD_REPORT=1` antes.

## Aliases de caminho (expanda ao executar)

| Alias | Caminho real |
|---|---|
| `CORE/` | `backend/core/src/main/java/br/com/felipeblaft/reports/` |
| `CORE_T/` | `backend/core/src/test/java/br/com/felipeblaft/reports/` |
| `APP/` | `backend/application/src/main/java/br/com/felipeblaft/reports/` |
| `APP_T/` | `backend/application/src/test/java/br/com/felipeblaft/reports/` |
| `RES/` | `backend/application/src/main/resources/` |
| `FE/` | `frontend/src/` |
| `FE_T/` | `frontend/tests/` |
| `SPEC/` | `specs/006-admin-report-history/` |
| `SPEC005/` | `specs/005-admin-registrations-access/` |

Testes de integração terminam em `IT` e usam Postgres real quando a tarefa disser "Postgres real". Comandos sempre em
contêiner: `sh infra/scripts/local.sh tests backend|frontend|e2e`.

---

## Phase 1: Setup (linha de base e ferramentas)

- [ ] T001 Atualizar a branch `feat/006-admin-report-history` a partir da `main` (`git checkout main && git pull --ff-only origin main`, depois `git merge main` na branch) e registrar a linha de base em `SPEC/evidence/baseline.md`: resultado de `sh infra/scripts/local.sh tests backend` e `tests frontend` (contagem de testes, cobertura de linha e branch do núcleo, guardiões de idioma), com `APP_T/parity/HistoryParityScriptIT.java` e `APP_T/inbound/web/HistoryContractIT.java` verdes.
- [X] T002 [P] Acrescentar em `frontend/package.json` o script `generate:school-history-api` (`openapi-typescript ../specs/006-admin-report-history/contracts/api-school-history.openapi.yaml -o src/services/school-history-api-types.ts`), encadeá-lo em `generate`, e incluir `frontend/src/services/school-history-api-types.ts` no `.gitignore` da raiz (junto de `administration-api-types.ts`). Rodar `npm run generate` no contêiner de testes do frontend.
- [X] T003 [P] Rodar os guardiões em modo relatório (`GUARD_REPORT=1`, ver `.specify/CLAUDE.md`) e confirmar que `SPEC/contracts/api-school-history.openapi.yaml` passa no `APP_T/architecture/ArtifactsLanguageGuardIT.java`; se acusar algo, corrigir com a skill `fix-language-guard` antes de seguir.

---

## Phase 2: Foundational (bloqueia todas as histórias)

**Propósito**: matriz, modelo de leitura, consulta única de aprovados, autoria das leituras e a migração do histórico do
professor para a mesma consulta. Ao fim, o histórico do professor está idêntico ao da linha de base.

### Testes primeiro

- [X] T004 [P] Escrever `CORE_T/domain/history/HistoryOrderTest.java`: referência desc. (data da aula; início do período), depois `approvedAt` desc., depois `reportId` desc.; empates de data entre aula e semestral; ordem total e estável (FR-007).
- [X] T005 [P] Escrever `CORE_T/domain/access/AuthorshipReadDetailTest.java` e acrescentar casos em `CORE_T/domain/access/AuthorshipRecordTest.java`: o detalhe só é aceito nas operações `SCHOOL_HISTORY_QUERY`, `SCHOOL_REPORT_DOCUMENT_OPEN` e `SCHOOL_REPORT_DOCUMENT_DOWNLOAD`; `pageNumber ≥ 0`; `documentVersion ≥ 1`; tipo de filtro só `LESSON`/`SEMESTER`; `toString` sem valores (SPEC/data-model.md §4).
- [X] T006 [P] Atualizar `CORE_T/domain/access/PermissionMatrixTest.java` com as três operações novas (`ALLOW` só para administrador na Web; `DENY` para professor nos dois canais; sem origem de sistema) e confirmar que `CORE_T/domain/access/PermissionMatrixDriftTest.java` passa a falhar até T008 e T009.
- [X] T007 [P] Escrever `APP_T/outbound/persistence/MigrationV16IT.java` (Postgres real): colunas novas existem e são anuláveis; os CHECKs recusam detalhe em operação de outra feature, `filter_report_type` fora de `LESSON`/`SEMESTER`, `page_number < 0` e `document_version < 1`; `target_type` aceita `LESSON_REPORT` e `SEMESTER_REPORT`; os gatilhos de somente acréscimo da V10 continuam recusando `UPDATE`, `DELETE` e `TRUNCATE`; os índices parciais de aprovados existem (SPEC/data-model.md §4.1 e §5).

### Implementação

- [X] T008 Acrescentar as três linhas de `SPEC/contracts/permission-matrix-additions.md` em `SPEC005/contracts/permission-matrix.json`, com `"note": "feature 006 …"`.
- [X] T009 Acrescentar `SCHOOL_HISTORY_QUERY`, `SCHOOL_REPORT_DOCUMENT_OPEN` e `SCHOOL_REPORT_DOCUMENT_DOWNLOAD` em `CORE/domain/access/Operation.java` e as regras `onlyAdministrator(Set.of())` em `CORE/domain/access/PermissionMatrix.java`; `LESSON_AND_SEMESTER_REPORTS` não muda. T006 fica verde. (após T008)
- [X] T010 [P] Criar `CORE/domain/history/HistoryOrder.java`, `ApprovedReportCriteria.java` (campos opcionais `studentId`, `teacherId`, `type`, `from`, `to`; fábrica `ofTeacherStudent`) e `ApprovedReportRow.java` (`reference()`, `toString` sem nomes), conforme SPEC/data-model.md §2. T004 fica verde.
- [X] T011 Criar `CORE/domain/access/AuthorshipReadDetail.java`; acrescentar `LESSON_REPORT` e `SEMESTER_REPORT` em `CORE/domain/access/AuthorshipTargetType.java`; acrescentar o componente opcional `readDetail` em `CORE/domain/access/AuthorshipRecord.java` (ajustar só as chamadas existentes para compilar, passando `null`); acrescentar `recordRead(actor, operation, targetType, targetId, detail, result, errorCode, correlationId)` em `CORE/service/AuthorshipService.java` (sucesso na mesma transação, demais em transação independente) com casos em `CORE_T/service/AuthorshipServiceTest.java`. T005 fica verde. (após T009)
- [X] T012 Criar `RES/db/migration/V16__authorship_read_detail.sql`: colunas `filter_teacher_id`, `filter_report_type`, `filter_period_start`, `filter_period_end`, `page_number`, `document_version`; CHECKs da T007; recriar `ck_authorship_record_target_type` com os dois tipos novos; índices parciais `WHERE status = 'APPROVED'` (`lesson_report (student_id)`, `semester_report (student_id)`, `semester_report (teacher_id)`, `semester_report (period_start)`) e `ix_authorship_record_operation (operation, occurred_at)`; comentário de cabeçalho em português. T007 fica verde.
- [X] T013 Mapear o detalhe em `APP/outbound/persistence/AuthorshipRecordEntity.java` e `AuthorshipRecordAdapter.java`, com casos novos em `APP_T/outbound/persistence/AuthorshipRecordAdapterIT.java` (grava e relê o detalhe; registro sem detalhe continua igual). (após T011, T012)
- [X] T014 Criar a porta `CORE/port/ApprovedReportQueryPort.java` (`page(criteria, PageRequest) → PageResult<ApprovedReportRow>`, `all(criteria) → List<ApprovedReportRow>`) e o dublê `CORE_T/doubles/InMemoryApprovedReportQuery.java` (só aprovados, filtros pela data de referência inclusive, ordena por `HistoryOrder`, pagina). (após T010)
- [X] T015 Escrever `APP_T/outbound/persistence/ApprovedReportQueryAdapterIT.java` (Postgres real), que falha até T016: (a) **nenhum relatório não aprovado aparece** — semear aula e semestral em todos os estados (`PROCESSING`, `PROCESSING_FAILED`, `AWAITING_ANSWERS`, `AWAITING_REVIEW`, `CANCELLED`, `APPROVED`) e conferir itens e total; (b) base aleatória com empates de data e de aprovação: a ordem do SQL é igual à de `HistoryOrder.NEWEST_FIRST`; (c) cada filtro sozinho e combinados, período pela data de referência, datas inclusive, semestral que começa antes do intervalo fica de fora; (d) `page`/`total`; (e) nome e situação de aluno e professor (inclusive inativos e professor desassociado) e `approvedVersion`. (após T014)
- [X] T016 Implementar `APP/outbound/persistence/ApprovedReportQueryAdapter.java`: consulta nativa com `UNION ALL` de `lesson_report ⨝ lesson` e `semester_report`, sempre `status = 'APPROVED'`, junção com `teacher` e `student` (só `id`, `name`, `active`), `ORDER BY reference_date DESC, approved_at DESC, report_id DESC`, `LIMIT/OFFSET` e `COUNT(*)` com o mesmo critério; parâmetros sempre ligados, nunca concatenados. T015 fica verde. (após T012, T015)
- [X] T017 Migrar o histórico do professor para a porta, **sem mudar o resultado**: `CORE/usecase/history/QueryStudentHistoryUseCase.java` passa a chamar `all(ApprovedReportCriteria.ofTeacherStudent(teacherId, studentId))` depois da mesma checagem de aluno associado, e o comparador `NEWEST_FIRST` sai para `HistoryOrder`; ajustar `HistoryItem`/`MessageFormatting.historyLabel` em `APP/config/MessageFormatting.java` se a assinatura mudar; `CORE_T/usecase/history/QueryStudentHistoryUseCaseTest.java` usa o dublê de T014. `APP_T/inbound/web/HistoryContractIT.java`, `APP_T/parity/HistoryParityScriptIT.java` e os testes do `TelegramHistoryFlow` continuam verdes (SC-006). (após T014, T016)
- [X] T018 Registrar os beans da porta e do adaptador em `APP/config/UseCaseConfig.java` (ou num `APP/config/SchoolHistoryUseCaseConfig.java` novo, sem duplicar) e conferir `APP_T/config/UseCaseWiringTest.java`. (após T016, T017)
- [X] T019 [P] Acrescentar em `RES/messages/messages_pt_BR.properties` todas as chaves de `SPEC/contracts/portal-screens.md` §3 (textos da A13 e códigos de campo `INVALID_DATE`, `PERIOD_START_AFTER_END`, `INVALID_REPORT_TYPE`), em português acentuado; rodar `npm run generate:messages` e o teste do `MessageCatalog`.

**Checkpoint**: matriz, V16, consulta única e autoria das leituras prontas; histórico do professor igual à linha de base.

---

## Phase 3: User Story 1 — Histórico de um aluno, de todos os professores (Priority: P1) 🎯 MVP

**Goal**: o administrador procura qualquer aluno (ativo ou inativo) e vê todos os relatórios aprovados dele, de todos os
professores, na ordem certa, com autor, aprovação e versão.

**Independent Test**: com o aluno X (aprovados de A e de B, dois semestrais do mesmo período, um aprovado na v3 e
relatórios em todos os estados não aprovados), abrir o histórico de X e conferir exatamente os aprovados, na ordem, com
os dados do FR-006, em até 3 interações (quickstart Q1 a Q4).

### Testes da US1 (antes da implementação)

- [X] T020 [P] [US1] Escrever `CORE_T/usecase/history/QuerySchoolHistoryUseCaseTest.java` com o dublê de T014: administrador com `studentId` recebe os aprovados de todos os professores, inclusive inativos e desassociados, na ordem de `HistoryOrder`, com `approvedVersion`; os dois semestrais do mesmo período vêm como itens separados (D1); aluno inativo é consultável; aluno sem aprovados devolve página vazia com o resumo do aluno; aluno inexistente devolve página vazia sem resumo (FR-014); professor (Web e Telegram) recebe `RESOURCE_NOT_FOUND`; administrador com senha provisória recebe `PASSWORD_CHANGE_REQUIRED`; grava um `SCHOOL_HISTORY_QUERY` `SUCCESS` com alvo `STUDENT` e `pageNumber`; nenhum repositório de relatório recebe escrita (FR-004).
- [X] T021 [P] [US1] Escrever `APP_T/inbound/web/admin/AdminSchoolHistoryContractIT.java` (base `AdminWebBase`): `GET /api/v1/admin/school-history?studentId=…` valida contra `SPEC/contracts/api-school-history.openapi.yaml` (json-schema-validator, como `AdminStudentContractIT`); `Cache-Control: no-store, private`; 401 sem cookie; `student` presente só para aluno existente; corpo sem CPF, nascimento, e-mail ou responsável.
- [X] T022 [P] [US1] Escrever `APP_T/security/SchoolHistoryOnlyApprovedIT.java` (Postgres real, por HTTP): para o aluno X com relatórios em **todos** os estados não aprovados, a lista e o `total` trazem só os aprovados (SC-002); a lista do administrador para X é exatamente a união dos históricos que A e B veem de X pelo `GET /api/v1/students/{id}/history` (SC-010).

### Implementação da US1

- [X] T023 [US1] Criar `CORE/usecase/history/QuerySchoolHistoryUseCase.java`: `require(actor, SCHOOL_HISTORY_QUERY)`; tudo dentro do `TransactionPort`; resumo do aluno filtrado pelo `StudentRepository` (só `id`, `name`, `active`); `ApprovedReportQueryPort.page(...)`; `AuthorshipService.recordRead(... SUCCESS ...)` antes de devolver. T020 fica verde. (após T018)
- [X] T024 [US1] Criar `APP/inbound/web/admin/AdminSchoolHistoryDtos.java` (`SchoolHistoryPage`, `SchoolHistoryItem`, `PersonSummary`; `toString` sem nomes, regra do `InboundPersonalDataToStringTest`) e `APP/inbound/web/admin/AdminSchoolHistoryController.java` com `GET /api/v1/admin/school-history` (por ora só `studentId`, `page`, `size`), rótulo por `MessageFormatting.historyLabel`, `Cache-Control: no-store, private`, correlação pelo `AdminSessionFilter.CORRELATION_ATTRIBUTE`. T021 e T022 ficam verdes. (após T023)
- [X] T025 [US1] Registrar o bean de `QuerySchoolHistoryUseCase` em `APP/config/` e acrescentar `QuerySchoolHistoryUseCase → SCHOOL_HISTORY_QUERY` no mapa caso de uso → operação de `APP_T/architecture/AdapterRulesTest.java`. (após T023)
- [X] T026 [P] [US1] Escrever `FE_T/services/admin/schoolHistory.test.ts` e criar `FE/services/admin/schoolHistory.ts` (tipos de `school-history-api-types.ts`; chave de consulta só com identificadores, tipo, datas e página; nunca nome). (após T002)
- [X] T027 [P] [US1] Escrever `FE_T/pages/admin/SchoolHistoryScreen.test.tsx` e `FE_T/components/admin/StudentPicker.test.tsx`: busca com `status=ALL` e recusa de termo com forma de CPF (`looksLikeCpf`); opção mostra nome, "Inativo" e professores; escolher fixa `studentId` na rota (só id); lista com Relatório, Professor (+ "Inativo"), Aprovado em, Versão; coluna Aluno some com filtro de aluno; estados carregando, vazio ("Nenhum relatório aprovado encontrado para este aluno."), falha com código de referência.
- [X] T028 [US1] Criar `FE/components/admin/StudentPicker.tsx` (combobox acessível que reaproveita `FE/services/admin/students.ts` e `useSearchParamTerm`), `FE/components/admin/SchoolHistoryList.tsx` (cartões abaixo de 640 px, tabela com `<caption>` acima) e `FE/pages/admin/SchoolHistoryScreen.tsx`, conforme `SPEC/contracts/portal-screens.md` §2. T026 e T027 ficam verdes. (após T019, T026, T027)
- [X] T029 [US1] Rota `/admin/history` em `FE/app/adminRoutes.tsx`; item "Histórico" em `FE/components/admin/AdminNav.tsx`; ação "Ver histórico" em `FE/pages/admin/StudentDetailScreen.tsx`; atalho em `FE/pages/admin/AdminHomeScreen.tsx`; ajustar `FE_T/pages/admin/StudentDetailScreen.test.tsx` e `AdminHomeScreen.test.tsx`. (após T028)
- [X] T030 [US1] Escrever e fazer passar `FE_T/e2e/admin-school-history.spec.ts` (cenário do histórico): com aulas aprovadas por Clara e por Bruno para o mesmo aluno (helpers de `FE_T/e2e/support.ts` e `registerApprovedLesson`), o administrador chega à lista em 3 interações a partir de `/admin` (SC-001) e vê os itens dos dois professores. (após T024, T029)

**Checkpoint**: US1 entregável sozinha (MVP): histórico de qualquer aluno, sem PDF.

---

## Phase 4: User Story 2 — Abrir e baixar o PDF aprovado, só com autorização (Priority: P1)

**Goal**: o administrador abre ou baixa o PDF oficial da versão aprovada; nada é entregue sem sessão de administrador ou
para relatório não aprovado.

**Independent Test**: abrir e baixar o PDF de um relatório aprovado na v3 e compará-lo ao do professor; depois pedir o
mesmo PDF sem sessão, com sessão de professor, com sessão expirada e pedir PDFs de relatórios não aprovados e
inexistentes (quickstart Q7 a Q10).

### Testes da US2 (antes da implementação)

- [X] T031 [P] [US2] Escrever `CORE_T/service/OfficialDocumentServiceTest.java`: lê o `PdfDocument` `OFFICIAL` da versão aprovada (aula e semestral) e monta o nome sugerido igual ao do professor; documento ausente ou ilegível falha sem tentar outra versão.
- [X] T032 [P] [US2] Escrever `CORE_T/usecase/document/GetSchoolReportDocumentUseCaseTest.java`: administrador recebe o oficial da v3 de qualquer professor; relatório em cada estado não aprovado e relatório inexistente recebem `RESOURCE_NOT_FOUND` pela mesma via (uma consulta só); professor recebe `RESOURCE_NOT_FOUND`; senha provisória recebe `PASSWORD_CHANGE_REQUIRED`; PDF aprovado indisponível recebe `INTERNAL_ERROR`; sucesso grava `SCHOOL_REPORT_DOCUMENT_OPEN` ou `_DOWNLOAD` com alvo `LESSON_REPORT`/`SEMESTER_REPORT` e `documentVersion`; nenhum relatório muda.
- [X] T033 [P] [US2] Escrever `APP_T/inbound/web/admin/AdminSchoolReportDocumentContractIT.java`: as quatro rotas de PDF de `SPEC/contracts/api-school-history.openapi.yaml` devolvem `application/pdf`, `Content-Disposition` `inline` (abrir) e `attachment` (baixar) com o mesmo nome sugerido do professor, `Cache-Control: no-store, private`; os bytes são iguais aos de `GET /api/v1/lesson-reports/{id}/official-pdf` do professor autor (FR-016).
- [X] T034 [P] [US2] Escrever `APP_T/security/SchoolHistoryCrossAccessIT.java` — **acesso cruzado nos dois sentidos**: (1) cookie de professor (inclusive o autor) em todas as rotas `/api/v1/admin/school-history/**` → 404 com corpo e cabeçalhos iguais aos de rota inexistente, e registro `AUTHORIZATION_DENIED`; (2) só o cookie de administrador nas rotas do professor (`/lesson-reports/{id}/official-pdf`, `/lesson-reports/{id}/versions/{n}/pdf`, `/semester-reports/{id}/official-pdf`, `/semester-reports/{id}/versions/{n}/pdf`, `/students/{id}/history`) → 404 de rota inexistente e nenhum PDF entregue; (3) sem cookie, com sessão expirada e depois de "Sair" → 401; (4) `/admin/school-history/{lesson,semester}-reports/{id}/versions/1/pdf` → 404; (5) `POST`, `PATCH`, `PUT` e `DELETE` em `/admin/school-history/**` → 404 e nenhum relatório muda de estado, versão ou data (SC-004).
- [X] T035 [P] [US2] Escrever `APP_T/security/SchoolReportDocumentIndistinguishableIT.java` (no molde de `IndistinguishableStudentResponseIT`): relatório inexistente, relatório em cada estado não aprovado e rota inexistente devolvem o mesmo status, corpo e cabeçalhos relevantes, com tempos na mesma ordem de grandeza (FR-018, SC-003).

### Implementação da US2

- [X] T036 [US2] Extrair `CORE/service/OfficialDocumentService.java` da leitura do oficial em `CORE/usecase/document/GetReportDocumentUseCase.java` e `CORE/usecase/semester/GetSemesterReportUseCase.java`; o caminho do professor mantém a checagem de dono. `APP_T/inbound/web/ReviewDocumentContractIT.java`, `CORE_T/usecase/lesson/LessonReportUseCasesTest.java` e `CORE_T/usecase/semester/SemesterUseCasesTest.java` continuam verdes; T031 fica verde. (após T018)
- [X] T037 [US2] Criar `CORE/usecase/document/GetSchoolReportDocumentUseCase.java` (R-03: autorização, uma consulta por id, estado aprovado, `OfficialDocumentService`, `recordRead` de sucesso; negativa e indisponível com `recordIndependent`). T032 fica verde. (após T011, T036)
- [X] T038 [US2] Extrair a montagem da resposta de PDF de `APP/inbound/web/DocumentController.java` para `APP/inbound/web/PdfResponses.java` (variantes `inline` e `attachment`, `no-store, private`) e acrescentar as quatro rotas de PDF em `APP/inbound/web/admin/AdminSchoolHistoryController.java`; registrar o bean do caso de uso e acrescentar `GetSchoolReportDocumentUseCase` no mapa de `APP_T/architecture/AdapterRulesTest.java`. T033 a T035 ficam verdes. (após T024, T037)
- [X] T039 [US2] Conferir que `APP_T/security/PermissionMatrixCrossAccessIT.java` (gerado da matriz) cobre as três operações novas com identificadores de exemplo válidos para `{reportId}`; acrescentar o que faltar no carregador de rotas do teste.
- [X] T040 [P] [US2] Escrever `FE_T/components/admin/SchoolHistoryList.test.tsx`: "Abrir PDF" chama `openPdf` com a rota `/pdf`; "Baixar PDF" usa a rota `/pdf/download` e o nome sugerido; carregamento por item; falha mostra `role="alert"` sem sair da lista; nomes acessíveis com o rótulo do relatório.
- [X] T041 [US2] Acrescentar caminhos de PDF e `downloadPdf` em `FE/services/admin/schoolHistory.ts` (reaproveitar `FE/services/pdf.ts`; criar ali `downloadPdf` se não existir) e os botões em `FE/components/admin/SchoolHistoryList.tsx`. T040 fica verde. (após T028, T040)
- [X] T042 [US2] Estender `FE_T/e2e/admin-school-history.spec.ts`: abrir o PDF (aba com `blob:`, como em `history.spec.ts`), baixar com o nome sugerido, e pedir a rota do PDF num contexto sem cookie → 401. (após T030, T041)

**Checkpoint**: US1 + US2 = histórico com PDF, protegido nos dois sentidos de credencial.

---

## Phase 5: User Story 3 — Filtros: aluno, professor, tipo e período (Priority: P2)

**Goal**: filtrar por professor, tipo e período, com ou sem aluno, com lista da escola paginada.

**Independent Test**: aplicar cada filtro sozinho e combinado sobre uma base com vários alunos e professores e conferir
o subconjunto exato, na mesma ordem, com paginação estável (quickstart Q5 e Q6).

### Testes da US3 (antes da implementação)

- [X] T043 [P] [US3] Escrever `CORE_T/domain/history/SchoolHistoryFilterTest.java`: `from > to` → `VALIDATION_FAILED` com `PERIOD_START_AFTER_END` no campo `from`; datas iguais aceitas; tipo opcional; página e tamanho pelos limites de `PageRequest`.
- [X] T044 [P] [US3] Acrescentar em `CORE_T/usecase/history/QuerySchoolHistoryUseCaseTest.java`: só professor (todos os alunos dele, atuais e antigos, com o nome do aluno); só tipo; só período (data de referência, inclusive); todos combinados; professor ou aluno inexistente → página vazia; paginação com total; registro com `filter_*` e `page_number` corretos.
- [X] T045 [P] [US3] Escrever `APP_T/inbound/web/admin/AdminSchoolHistoryFiltersContractIT.java`: 400 `VALIDATION_FAILED` com `details.fields` para data inválida (`INVALID_DATE` em `from`/`to`), `from > to`, `type` desconhecido (`INVALID_REPORT_TYPE`), `page < 0` e `size = 101`; respostas válidas contra o OpenAPI.
- [X] T046 [P] [US3] Escrever `APP_T/outbound/persistence/ApprovedReportQueryPerformanceIT.java` (Postgres real): base com 10.000 relatórios aprovados (aula e semestral, vários professores e alunos); primeira página sem filtro, só professor e só período com p95 ≤ 300 ms em 20 execuções (meta interna; o SC-008 pede ≤ 2 s).

### Implementação da US3

- [X] T047 [US3] Criar `CORE/domain/history/SchoolHistoryFilter.java` (valida e produz `ApprovedReportCriteria` + `PageRequest`) e usá-lo em `CORE/usecase/history/QuerySchoolHistoryUseCase.java`. T043 e T044 ficam verdes. (após T023)
- [X] T048 [US3] Aceitar `teacherId`, `type`, `from`, `to` em `APP/inbound/web/admin/AdminSchoolHistoryController.java`; data mal formada e tipo desconhecido viram `VALIDATION_FAILED` com o código de campo (sem repetir o valor). T045 fica verde; T046 verde (ajustar índices na V16 se preciso, antes do merge). (após T047)
- [X] T049 [P] [US3] Escrever `FE_T/components/admin/SchoolHistoryFilters.test.tsx` e casos em `FE_T/pages/admin/SchoolHistoryScreen.test.tsx`: professor (ativos e inativos marcados, de `GET /admin/teachers`), tipo, De/Até, "Aplicar", "Limpar filtros", erros de `details.fields` junto do campo, mensagem "Nenhum relatório aprovado encontrado para estes filtros.", paginação "1–20 de N" com foco no início da lista, rota atualizada com `replace` e só com identificadores.
- [X] T050 [US3] Criar `FE/components/admin/SchoolHistoryFilters.tsx` (recolhível abaixo de 640 px, contador de filtros ativos) e a paginação em `FE/components/admin/SchoolHistoryList.tsx`; sincronizar filtros e página com a rota em `FE/pages/admin/SchoolHistoryScreen.tsx`. T049 fica verde. (após T041, T049)
- [X] T051 [US3] Estender `FE_T/e2e/admin-school-history.spec.ts`: sem aluno, filtrar por professor, por "Semestral" e por período, e conferir os itens e o total. (após T042, T050)

**Checkpoint**: lista da escola completa, com filtros e paginação.

---

## Phase 6: User Story 4 — Registro de autoria das consultas e das entregas de PDF (Priority: P2)

**Goal**: cada página entregue, cada PDF e cada negativa deixam exatamente um registro, sem dado pessoal nem conteúdo;
sem registro, nada é entregue.

**Independent Test**: executar consultas, filtros, trocas de página, aberturas, downloads e pedidos negados e conferir os
registros um a um em `authorship_record`; forçar falha do registro e conferir que nada foi entregue (quickstart Q11).

### Testes da US4 (antes da implementação)

- [X] T052 [P] [US4] Escrever `APP_T/security/AuthorshipSchoolHistoryIT.java` (Postgres real): eventos E1 a E7 de `SPEC/contracts/authorship-events.md`, um por requisição, com autor, papel, canal, operação, alvo, detalhe, resultado e correlação corretos; a busca de aluno (`STUDENT_LIST`) e a lista de professores (`TEACHER_LIST`) não geram registro de leitura.
- [X] T053 [P] [US4] Escrever `APP_T/security/SchoolHistoryAuthorshipAtomicityIT.java`: com um `AuthorshipRecorderPort` que falha no `record`, a lista e o PDF respondem 500 `INTERNAL_ERROR` com `reference`, sem itens e sem bytes de PDF (FR-023).
- [X] T054 [P] [US4] Escrever `APP_T/privacy/SchoolHistoryCanaryIT.java` com `CanaryStudent`, `CanaryCpf` e um trecho canário de conteúdo de relatório: depois de todas as requisições da feature (sucesso, validação, negativas, falha forçada), varrer `authorship_record`, os logs capturados (`LogCapture`) e os corpos de erro com `ResponseSweeper`; nenhum canário aparece fora da resposta autorizada da lista e do PDF (SC-005).
- [X] T055 [US4] Acrescentar as três operações em `APP_T/security/AuthorshipCoverageIT.java` (toda operação da matriz com registro esperado).

### Implementação da US4

- [X] T056 [US4] Completar as negativas nos casos de uso: `QuerySchoolHistoryUseCase` grava E2 (`FAILURE`, `VALIDATION_FAILED`, com os filtros válidos) em transação independente antes de relançar; `GetSchoolReportDocumentUseCase` grava E5 e E6; nenhum `catch` engole a falha de gravação de sucesso. T052 a T055 ficam verdes. (após T037, T047)
- [X] T057 [US4] Conferir em `APP/inbound/web/ErrorHandlerController.java` que a falha de gravação do registro chega como `INTERNAL_ERROR` com `reference` e que o log técnico não leva nome, filtro de texto nem conteúdo; ajustar e cobrir em `APP_T/inbound/web/ErrorHandlerIT.java` se preciso.

**Checkpoint**: trilha de leitura do administrador completa para a feature 007.

---

## Phase 7: Canal Telegram — somente Web (substitui "paridade com paginação por botões")

**Purpose**: provar que o bot não ganhou nenhum acesso administrativo e que o histórico do professor continua igual nos
dois canais (FR-001, FR-026, SC-006, SC-007).

- [X] T058 [P] Em `APP_T/architecture/AdapterRulesTest.java`, garantir que a regra derivada da matriz reprova qualquer dependência de `APP/inbound/telegram/**` em `QuerySchoolHistoryUseCase`, `GetSchoolReportDocumentUseCase`, `ApprovedReportQueryPort` e `OfficialDocumentService` (os dois primeiros pelo mapa de T025/T038; os dois últimos por regra explícita). (após T038)
- [X] T059 [P] Escrever `APP_T/inbound/telegram/SchoolHistoryNeutralRefusalIT.java`: professor vinculado e conta sem vínculo pedem "histórico da escola", "histórico de todos os alunos" e similares → negativa neutra da 005 (FR-037) seguida do menu do professor, sem citar Web nem administrador; nenhum caso de uso desta feature é chamado.
- [X] T060 Rodar `APP_T/parity/HistoryParityScriptIT.java` e comparar com a linha de base de T001: o histórico do professor é o mesmo na Web e no Telegram, com os mesmos itens e a mesma ordem (SC-006). (após T017)

---

## Phase 8: Polish & Cross-Cutting Concerns

- [X] T061 [P] Escrever e fazer passar `FE_T/e2e/admin-school-history.a11y.spec.ts` — **acessibilidade da tela nova**: axe (`expectNoSeriousViolations`, `SCREEN_MATRIX`: claro e escuro, 390 e 1440 px) na A13 com lista, com aluno escolhido, vazia, com filtros abertos e com erro de filtro; navegação só por teclado do combobox, dos filtros, da paginação e dos botões de PDF (`tabUntilFocused`); "Inativo" com texto e ícone (SC-009). (após T051)
- [X] T062 [P] Acrescentar as notas `> **Nota (feature 006)**` em `specs/001-fluxo-relatorios/spec.md` (FR-048: o PDF oficial também é entregue ao administrador pela rota administrativa) e em `SPEC005/spec.md` (linha "Relatórios de aula e semestrais" da Matriz de permissões e a premissa sobre aluno inativo no histórico), com link relativo para `SPEC/`.
- [X] T063 [P] Atualizar `specs/CLAUDE.md` (o contrato `006-admin-report-history/contracts/api-school-history.openapi.yaml` entre as fontes de verdade), `frontend/CLAUDE.md` (gerador `generate:school-history-api`, arquivo gerado ignorado, rota `/admin/history`) e os `CLAUDE.md` de `backend/core/` e `backend/application/` se listarem pacotes, portas ou migrações.
- [X] T064 Rodar `sh infra/scripts/local.sh tests backend`, `tests frontend` e `tests e2e`; conferir cobertura do núcleo ≥ 90% (linha e branch) e os três guardiões de idioma; corrigir falhas (skills `run-tests` e `fix-language-guard`).
- [ ] T065 Executar o `SPEC/quickstart.md` (Q1 a Q13) no ambiente local e registrar o resultado, sem dado pessoal, em `SPEC/evidence/quickstart.md`.
- [X] T066 Rodar `/pre-pr-review` (backend, frontend, contrato, privacidade e constituição) e corrigir os achados.
- [ ] T067 Fechar com a skill `open-pr`: commit em português, PR para a `main` (sem merge nem merge automático) e `code-review-graph build`.

---

## Dependencies & Execution Order

### Fases

- **Setup (F1)** → **Fundação (F2)** → histórias. A Fundação bloqueia tudo.
- **US1 (F3)** depende só da Fundação. **US2 (F4)** depende da Fundação e do controlador da US1 (T024) para as rotas de
  PDF; o núcleo da US2 (T031, T032, T036, T037) pode andar em paralelo com a US1.
- **US3 (F5)** depende de T023 (caso de uso) e de T041 (lista com PDF) no frontend.
- **US4 (F6)** depende de T037 e T047; os testes T052 a T054 podem ser escritos logo após a Fundação.
- **Telegram (F7)** depende de T017, T025 e T038.
- **Fecho (F8)** depende de todas.

### Dentro de cada história

Testes (falhando) → núcleo → controlador → frontend → e2e.

### Paralelismo

- F1: T002 e T003 juntos.
- F2: T004, T005, T006 e T007 juntos; depois T010 e T019 em paralelo com T008→T009→T011.
- US1: T020, T021, T022, T026 e T027 juntos.
- US2: T031 a T035 e T040 juntos.
- US3: T043 a T046 e T049 juntos.
- US4: T052, T053 e T054 juntos.
- F7: T058 e T059 juntos.
- F8: T061, T062 e T063 juntos.

### Exemplo: arrancar a US1

```text
Em paralelo: T020 (caso de uso), T021 (contrato), T022 (só aprovados), T026 (serviço do frontend), T027 (tela)
Depois: T023 → T024 → T025; T028 → T029 → T030
```

### Exemplo: arrancar a US2

```text
Em paralelo: T031, T032, T033, T034 (acesso cruzado nos dois sentidos), T035, T040
Depois: T036 → T037 → T038 → T039; T041 → T042
```

---

## Implementation Strategy

### MVP (US1)

1. F1 e F2 (o histórico do professor igual à linha de base é condição de saída).
2. F3 (US1): histórico por aluno, sem PDF.
3. **Parar e validar** com quickstart Q1 a Q4; o MVP já entrega a "visão da escola" pedida.

### Incremental

1. + US2 (PDF com acesso cruzado) → Q7 a Q10.
2. + US3 (filtros e paginação) → Q5, Q6 e desempenho.
3. + US4 (autoria completa) → Q11.
4. F7 e F8 → Q12, Q13, acessibilidade, revisão e PR.

Um único PR para a feature (Princípio VIII), aberto ao fim do Fecho; se a revisão pedir, a US1 com a Fundação pode ir
antes num PR próprio.

---

## Phase 9: Convergence

**Propósito**: lacunas encontradas por `/speckit-converge` em 2026-11-03 entre a spec/plano e o código. Nenhum requisito está ausente ou contradito; as pendências abaixo são parciais. T001, T065 e T067 seguem abertos acima.

- [ ] T068 Fazer toda recusa de filtro da lista deixar registro de autoria: mover a validação sintática (`SchoolHistoryFilter.parseSyntax`, hoje chamada em `APP/inbound/web/admin/AdminSchoolHistoryController.java`) para dentro de `CORE/usecase/history/QuerySchoolHistoryUseCase.java`, depois da autorização, recebendo os textos no `Command`; gravar o evento E2 (`FAILURE`, `VALIDATION_FAILED`) também para data inválida, tipo desconhecido, página e tamanho; ajustar `AdminSchoolHistoryFiltersContractIT`, `QuerySchoolHistoryUseCaseTest` e `AuthorshipSchoolHistoryIT`. Se a decisão for manter a exceção, registrá-la em `SPEC/contracts/authorship-events.md` e na spec em vez de mover o código. per FR-020/FR-021/FR-025 (partial)
- [ ] T069 Registrar `AUTHORIZATION_DENIED` (evento E7) quando a credencial de professor chega às rotas `/api/v1/admin/school-history/**`, ou, se a pendência da 005 continuar aceita, registrar a exceção por escrito em `SPEC/contracts/authorship-events.md` e ajustar `SPEC/quickstart.md` Q11; cobrir em `APP_T/security/AuthorshipSchoolHistoryIT.java`. per FR-003/FR-021 (partial)
- [ ] T070 Decidir o termo de busca de aluno na URL (`?search=`, `FE/pages/admin/SchoolHistoryScreen.tsx`): documentar na spec (FR-015) que ele segue a regra da 005, ou mantê-lo só em estado local do `StudentPicker`; em qualquer caso, fechar a aba em branco quando o PDF falha já foi feito em `FE/services/pdf.ts`. per FR-015 (partial)
- [ ] T071 Provar estaticamente a ausência de escrita do administrador sobre relatórios: regra no `APP_T/architecture/AdapterRulesTest.java` que reprova método `POST`, `PUT`, `PATCH` ou `DELETE` em controller de `inbound/web/admin` que dependa de repositório ou caso de uso de relatório de aula ou semestral, e garante que `AdminSchoolHistoryController` só tem `GET`. per SC-004 (partial)
- [ ] T072 Tratar a aula ausente de um relatório aprovado como falha registrada: em `CORE/usecase/document/GetSchoolReportDocumentUseCase.java`, trocar o `orElseThrow()` da busca da aula por recusa `INTERNAL_ERROR` com `recordIndependent` (E6), com caso em `GetSchoolReportDocumentUseCaseTest`. per FR-021 (partial)
- [ ] T073 Mover os textos de tela soltos para o catálogo de mensagens (`RES/messages/messages_pt_BR.properties` e `msg()`): "Carregando histórico", "Filtros", "(inativo)", "PDF", "Paginação", "Anterior", "Próxima", "de", "versão", "Professores:", "Nenhum aluno encontrado" e "Inativo" em `FE/components/admin/{SchoolHistoryFilters,SchoolHistoryList,StudentPicker,ui}.tsx` e `FE/pages/admin/SchoolHistoryScreen.tsx`; rodar `npm run generate:messages`. per plan: Princípio VI/IX (partial)
- [ ] T074 Remover `listApproved` (porta, adaptadores e dublês) de `CORE/port/LessonReportRepository.java` e `SemesterReportRepository.java`, que perderam o único chamador de produção depois da migração do histórico do professor, ou justificar por escrito o que fica. per plan: R-01 / Princípio VII (unrequested)
