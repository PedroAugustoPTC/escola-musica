# Implementation Plan: Administração — visão da escola (histórico de relatórios aprovados, somente leitura, somente Web)

**Branch**: `feat/006-admin-report-history` | **Date**: 2026-11-03 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `specs/006-admin-report-history/spec.md` (constituição **v2.2.0**; `/speckit-clarify`
de 2026-11-03 fechou canal, D1, D9, D10 e aluno inativo)

## Summary

O **Administrador**, **somente pela Web**, consulta os relatórios **aprovados** de toda a escola — o histórico de
qualquer aluno, ativo ou inativo, e a lista filtrada por aluno, professor, tipo e período, paginada — e abre ou baixa o
**PDF oficial da versão aprovada**. É estritamente somente leitura (Princípio III). Cada página entregue, cada PDF e
cada negativa deixam registro de autoria para a trilha da 007; sem registro, nada é entregue.

**Abordagem técnica** (decisões em [research.md](./research.md)):

1. **Uma consulta de aprovados para os dois papéis** (R-01): a porta nova `ApprovedReportQueryPort` faz, no banco, o
   `UNION ALL` de aulas e semestrais aprovados, com filtros, ordem e paginação. O histórico do professor (001) passa a
   usá-la com `teacherId = ator`; o administrador a usa com filtros livres. A ordem fica no núcleo
   (`HistoryOrder.NEWEST_FIRST`, com desempate pelo id) e um teste compara o SQL com ela.
2. **Quem vê o quê no núcleo** (R-02): três operações novas na `PermissionMatrix`, só para o administrador na Web;
   `LESSON_AND_SEMESTER_REPORTS` continua negada a ele.
3. **PDF sem duplicar a leitura** (R-03): `OfficialDocumentService` (extraído do caminho do professor) lê o PDF oficial
   da versão aprovada; o administrador só troca a checagem de acesso (aprovado, qualquer professor) e não tem rota por
   versão.
4. **Autoria das leituras** (R-04): mesma tabela da 005, com colunas tipadas novas (`V16`), tipos de alvo
   `LESSON_REPORT`/`SEMESTER_REPORT`, gravação na mesma transação da leitura.
5. **Contrato próprio** [api-school-history.openapi.yaml](./contracts/api-school-history.openapi.yaml) sob `/admin/`
   (R-05) e tela A13 `/admin/history` ([portal-screens.md](./contracts/portal-screens.md), R-08).
6. **Telegram**: nenhum fluxo novo (R-09). O pedido do plano citava "o fluxo do bot com botões", mas o clarify fechou a
   feature como somente Web (Princípio I); o que se entrega no bot é só a garantia de que nada mudou.

### O que já existe e é reaproveitado

| Pedido | Existe hoje | Como a 006 usa |
|---|---|---|
| Caso de uso de histórico | `QueryStudentHistoryUseCase` (ordena em memória) | passa a ler pela `ApprovedReportQueryPort` (`all`), com a mesma checagem de aluno associado; o comparador vira `HistoryOrder.NEWEST_FIRST`, com desempate estável |
| Autorização de PDF por dono | `ReportAccessService`, `SemesterAccessService`, `GetReportDocumentUseCase`, `GetSemesterReportUseCase` | a leitura do documento oficial sai para `OfficialDocumentService`; o professor mantém a checagem de dono; o administrador ganha `GetSchoolReportDocumentUseCase` (aprovado de qualquer professor) |
| Resposta HTTP de PDF | `DocumentController.pdf(...)` (no-store, privado, inline) | reaproveitada, com variante `attachment` para baixar |
| Matriz e autorização | `PermissionMatrix`, `ActorAuthorizationService`, `Actor.administrator` | três operações novas; teste de deriva e acesso cruzado gerado da matriz |
| Registro de autoria | `AuthorshipService`, `AuthorshipRecorderPort`, `authorship_record` (V10) | `recordRead(...)` e `AuthorshipReadDetail`; V16 com colunas tipadas e CHECKs |
| Sessão e área administrativa | `AdminSessionFilter`, `AdminSecurityConfig`, `CrossCredentialAnswerer`, `AdminPageResponse`, `PageRequest` | rotas novas sob `/api/v1/admin` herdam sessão, CSRF, 404 de credencial cruzada e paginação |
| Busca de aluno e lista de professores | `ListStudentsUseCase` (`status=ALL`), `GET /admin/teachers` | a A13 só as consome; nenhuma mudança |
| Frontend | `AdminLayout`, `AdminNav`, `AdminSessionGuard`, `useSearchParamTerm`, `looksLikeCpf`, `openPdf`, estados de lista | tela nova A13 e serviço `services/admin/schoolHistory.ts` |
| Rótulo do item | `MessageFormatting.historyLabel` | mesmo rótulo do professor ("Aula de …", "Semestral (… a …)") |

## Technical Context

**Language/Version**: Java 25 + Spring Boot 4 (Maven multi-módulo `core` / `application`); TypeScript, React 19, Vite
(`frontend`); SQL (PostgreSQL, Flyway).

**Primary Dependencies**: as existentes (Spring Web/Security/Data JPA, Flyway, ArchUnit, JaCoCo, networknt
json-schema-validator, TanStack Query, React Router, openapi-typescript, Vitest, Playwright + axe). **Nenhuma
dependência nova.**

**Storage**: PostgreSQL. Migração nova `V16__authorship_read_detail.sql` (colunas de detalhe da leitura, tipos de alvo,
CHECKs, índices parciais de aprovados). `V1` a `V15` intocadas. Nenhuma tabela nova.

**Testing**: núcleo ≥ 90% linha e branch (JaCoCo) com dublês; adaptador com Postgres real (só aprovados, ordem SQL ≡
`HistoryOrder`, filtros, V16, atomicidade, desempenho com 10.000 aprovados); contrato (respostas × OpenAPI da 006;
matriz × `permission-matrix.json`); acesso cruzado gerado da matriz; ArchUnit; privacidade com valores canário;
indistinguibilidade do 404 (corpo, cabeçalhos e ordem de grandeza do tempo); guardiões de idioma; Vitest; Playwright +
axe (claro/escuro, 390 e 1440 px). Tudo em contêiner (`local.sh tests backend|frontend|e2e`).

**Target Platform**: contêineres Linux (Docker 27+, Compose v2), local e VPS genérica; navegador moderno.

**Project Type**: aplicação web (backend hexagonal + SPA). O canal Telegram não é tocado.

**Performance Goals**: primeira página da lista ≤ 2 s em 95% das consultas com 10.000 aprovados (SC-008; meta interna
p95 ≤ 300 ms no teste de integração); PDF entregue no mesmo tempo do caminho do professor (leitura do armazenamento).

**Constraints**: somente leitura; somente Web; nada de dado pessoal em parâmetro de lista, log ou autoria; registro de
autoria e entrega valem juntos; 404 indistinguível para inexistente, não aprovado, rota inexistente e credencial de
professor; nenhum cache de PDF ou de lista; histórico do professor idêntico ao de antes.

**Scale/Scope**: uma escola (dezenas de professores, até ~1.000 alunos, alguns milhares de relatórios por ano); 1 caso de
uso de consulta, 1 de PDF, 1 serviço extraído, 1 porta e 1 adaptador de leitura, 1 migração, 1 controlador, 1 tela.

## Constitution Check

*GATE: avaliado antes da Fase 0 e reavaliado depois da Fase 1. Base: constituição v2.2.0.*

| Princípio | Como o plano cumpre | Situação |
|---|---|---|
| **I. Domínio independente de canal e fornecedor** | Casos de uso novos no núcleo (`QuerySchoolHistoryUseCase`, `GetSchoolReportDocumentUseCase`), recebendo `Actor` e sem conhecer HTTP; o controlador só traduz. A porta `ApprovedReportQueryPort` esconde o SQL. **Exceção do Administrador**: as três operações existem só na Web (`teacherTelegram = DENY`, nenhum adaptador do Telegram as referencia — ArchUnit). A paridade do Professor não é afetada: o histórico dele continua igual nos dois canais (SC-006). | ✅ |
| **II. Fonte única da verdade** | Autorização na `PermissionMatrix` do núcleo; o n8n não é tocado; o único dado gravado é o registro de autoria, pelo backend. | ✅ |
| **III. Humano no controle da IA** | Nenhuma operação de escrita sobre relatório para o administrador (D10); só o PDF `OFFICIAL` da versão aprovada é alcançável; não há rota por versão; `LESSON_AND_SEMESTER_REPORTS` segue negada ao administrador. Teste: nenhuma rota administrativa muda estado, versão ou data de relatório (SC-004). | ✅ |
| **IV. Privacidade desde o design** | Nenhum dado é enviado a IA. Parâmetros da lista só com ids, enumerações e datas; busca de aluno com a regra anti-CPF da 005. Registro de autoria só com colunas tipadas (CHECK no banco); logs e erros sem nome, CPF ou conteúdo (teste canário). PDF e lista com `no-store, private`; PDF só com sessão válida em toda requisição; 404 indistinguível. Leitura de dado de menor sempre registrada (D9), e sem registro nada é entregue. O escopo do professor (só os seus) continua imposto no núcleo. | ✅ |
| **V. Qualidade obrigatória** | Testes antes do código por história; núcleo ≥ 90%; contrato para a API nova e para a matriz; casos de erro e borda (inexistente, não aprovado, empate, período invertido, registro que falha, PDF indisponível). | ✅ |
| **VI. Nomenclatura e idioma** | Identificadores em inglês, já cobertos pelo `glossary.json` (`school`, `history`, `query`, `filter`, `document`, `open`, `download`, `page`, `period`, `approved`); sufixos `*UseCase`, `*Service`, `*Port`, `*Adapter`, `*Controller`, `*Dto`; textos e logs em português acentuado; rota SPA `/admin/history`. | ✅ |
| **VII. Simplicidade e foco** | Sem tabela, dependência ou cache novos; paginação por deslocamento igual à da 005; reaproveita sessão, matriz, autoria, busca de alunos e `openPdf`. O contrato próprio exige um terceiro gerador de tipos (uma linha no `package.json`), justificado em R-05. | ✅ |
| **VIII. Fluxo de trabalho** | Branch própria a partir da `main` atualizada; PR para a `main` sem merge pelo autor; `code-review-graph build` após o PR. | ✅ |
| **IX. Identidade visual e acessibilidade** | A13 com tokens do design system, mobile-first a partir de 390 px, cartões abaixo de 640 px, WCAG 2.1 AA nos dois temas (e2e com axe a 390 e 1440 px), "Inativo" com texto e ícone, foco gerenciado na paginação. | ✅ |

**Reavaliação pós-Fase 1**: os contratos, o modelo e o quickstart não introduziram desvio. Sem itens em Complexity
Tracking.

## Project Structure

### Documentation (this feature)

```text
specs/006-admin-report-history/
├── spec.md
├── plan.md                         # este arquivo
├── research.md                     # R-01 a R-11
├── data-model.md
├── quickstart.md
├── contracts/
│   ├── api-school-history.openapi.yaml
│   ├── permission-matrix-additions.md
│   ├── authorship-events.md
│   └── portal-screens.md
├── checklists/requirements.md
└── tasks.md                        # /speckit-tasks (ainda não criado)
```

### Source Code (repository root)

```text
backend/core/src/main/java/br/com/felipeblaft/reports/
├── domain/access/
│   ├── Operation.java                  # + SCHOOL_HISTORY_QUERY, SCHOOL_REPORT_DOCUMENT_OPEN, _DOWNLOAD
│   ├── PermissionMatrix.java           # + 3 linhas (só administrador)
│   ├── AuthorshipTargetType.java       # + LESSON_REPORT, SEMESTER_REPORT
│   ├── AuthorshipRecord.java           # + componente AuthorshipReadDetail
│   └── AuthorshipReadDetail.java       # novo
├── domain/history/                     # novo
│   ├── ApprovedReportCriteria.java
│   ├── ApprovedReportRow.java
│   ├── HistoryOrder.java
│   └── SchoolHistoryFilter.java
├── port/ApprovedReportQueryPort.java   # novo
├── service/
│   ├── AuthorshipService.java          # + recordRead(...)
│   └── OfficialDocumentService.java    # novo (extraído do caminho do professor)
└── usecase/
    ├── history/
    │   ├── QueryStudentHistoryUseCase.java      # passa a usar a porta (professor, sem mudança de resultado)
    │   └── QuerySchoolHistoryUseCase.java       # novo (administrador)
    ├── document/
    │   ├── GetReportDocumentUseCase.java        # usa OfficialDocumentService
    │   └── GetSchoolReportDocumentUseCase.java  # novo (administrador)
    └── semester/GetSemesterReportUseCase.java   # usa OfficialDocumentService

backend/application/src/main/
├── java/br/com/felipeblaft/reports/
│   ├── inbound/web/admin/
│   │   ├── AdminSchoolHistoryController.java    # novo: lista + 4 rotas de PDF
│   │   └── AdminSchoolHistoryDtos.java          # novo
│   ├── outbound/persistence/
│   │   ├── ApprovedReportQueryAdapter.java      # novo: UNION ALL, filtros, ordem, página, total
│   │   ├── AuthorshipRecordEntity.java          # + colunas de detalhe
│   │   └── AuthorshipRecordAdapter.java         # grava o detalhe
│   └── config/UseCaseConfig.java                # + beans
└── resources/
    ├── db/migration/V16__authorship_read_detail.sql
    └── messages/messages_pt_BR.properties       # + chaves da A13 e dos erros de filtro

backend/{core,application}/src/test/...          # unitários, adaptador, contrato, acesso cruzado, ArchUnit, privacidade, desempenho

frontend/
├── package.json                                 # + generate:school-history-api
├── src/services/admin/schoolHistory.ts          # novo (tipos gerados de api-school-history.openapi.yaml)
├── src/pages/admin/SchoolHistoryScreen.tsx      # novo (A13)
├── src/components/admin/                        # StudentPicker, SchoolHistoryFilters, SchoolHistoryList (novos)
├── src/app/adminRoutes.tsx, components/admin/AdminNav.tsx, pages/admin/StudentDetailScreen.tsx, AdminHomeScreen.tsx  # rota, item e atalhos
└── tests/ (Vitest) e tests/e2e/admin-school-history{,.a11y}.spec.ts

specs/001-fluxo-relatorios/spec.md               # nota (feature 006) no FR-048
specs/005-admin-registrations-access/spec.md     # nota (feature 006) na Matriz e na premissa do aluno inativo
specs/005-admin-registrations-access/contracts/permission-matrix.json  # + 3 operações
specs/CLAUDE.md, frontend/CLAUDE.md, backend/*/CLAUDE.md               # contrato novo, rota e gerador
```

**Structure Decision**: aplicação web existente (backend hexagonal `core`/`application` + SPA `frontend`). Nenhuma
estrutura de primeiro nível nova; por isso nenhum `CLAUDE.md` novo, só a atualização dos existentes que citam
contratos, rotas e geradores.

## Ordem de entrega (para o `/speckit-tasks`)

1. **Fundação** (bloqueia as histórias): operações e matriz (+ `permission-matrix.json`), `AuthorshipReadDetail` e V16,
   `HistoryOrder`, `ApprovedReportQueryPort` + adaptador com os testes de "só aprovados" e de ordem; migração do
   histórico do professor para a porta com os testes existentes verdes (SC-006).
2. **US1 (P1)** histórico por aluno: `QuerySchoolHistoryUseCase` com filtro de aluno, rota, A13 com busca de aluno.
3. **US2 (P1)** PDF: `OfficialDocumentService`, `GetSchoolReportDocumentUseCase`, quatro rotas, botões; acesso cruzado.
4. **US3 (P2)** filtros e paginação: `SchoolHistoryFilter`, professor/tipo/período, lista da escola, paginação.
5. **US4 (P2)** autoria: eventos E1 a E6, atomicidade, teste canário. (Pode andar junto com US1 e US2, mas fecha por
   último porque verifica todos os eventos.)
6. **Fechamento**: notas de emenda nas specs 001 e 005, `CLAUDE.md`, e2e de acessibilidade, desempenho (SC-008),
   `/pre-pr-review`.

## Complexity Tracking

Nenhum desvio da constituição a justificar.
