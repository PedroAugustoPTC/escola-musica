# Data Model: Administração — visão da escola

**Feature**: `006-admin-report-history` | **Data**: 2026-11-03 | Decisões em [research.md](./research.md)

A feature **não cria entidade de negócio** e **não grava** sobre relatórios. Ela lê os relatórios aprovados que já
existem (spec 001), acrescenta um modelo de leitura e estende o registro de autoria da 005. Nomes de código em inglês
(Princípio VI); todos os termos já estão no `glossary.json` (`history`, `school`, `query`, `filter`, `page`,
`document`, `open`, `download`, `period`, `approved`).

## 1. Lido, sem mudança de estrutura

| Entidade (tabela) | Campos lidos | Regra |
|---|---|---|
| `LessonReport` (`lesson_report`) + `Lesson` (`lesson`) | `id`, `teacher_id`, `student_id`, `status`, `approved_at`, `approved_version`; `lesson.lesson_date` | só `status = 'APPROVED'` |
| `SemesterReport` (`semester_report`) | `id`, `teacher_id`, `student_id`, `status`, `period_start`, `period_end`, `period_key`, `approved_at`, `approved_version` | só `status = 'APPROVED'`; um por aluno, professor e período (`uk_semester_report_approved`, D1) |
| `ReportVersion` + `PdfDocument` | versão `approved_version` do relatório; documento `OFFICIAL` | o administrador só alcança o oficial da versão aprovada |
| `Teacher` (`teacher`) | `id`, `name`, `active` | nome do autor e marca "Inativo" |
| `Student` (`student`) | `id`, `name`, `active` | nome do aluno e marca "Inativo"; **nunca** CPF, nascimento ou responsável |

Nenhum estado muda: a consulta e a entrega de PDF não alteram `status`, versão, `approved_at` nem qualquer coluna dos
relatórios (FR-004, SC-004).

## 2. Modelo de leitura (núcleo, novo)

### 2.1 `ApprovedReportCriteria` (objeto de valor)

| Campo | Tipo | Regra |
|---|---|---|
| `studentId` | UUID, opcional | identificador opaco |
| `teacherId` | UUID, opcional | identificador opaco; para o professor é sempre o próprio (forçado pelo caso de uso da 001) |
| `type` | `ReportType` (`LESSON`, `SEMESTER`), opcional | ausente = os dois |
| `from`, `to` | `LocalDate`, opcionais | inclusive; comparados com a **data de referência** (aula: `lesson_date`; semestral: `period_start`, FR-012). `from > to` → `VALIDATION_FAILED` com `field = from` |

Construído por `SchoolHistoryFilter` (**novo**, valida os campos vindos da borda) para o administrador e por
`ApprovedReportCriteria.ofTeacherStudent(teacherId, studentId)` para o professor.

### 2.2 `ApprovedReportRow` (item do histórico)

| Campo | Tipo | Observação |
|---|---|---|
| `type` | `ReportType` | |
| `reportId` | UUID | |
| `studentId`, `studentName`, `studentActive` | UUID, texto, booleano | o nome só vai para a tela, nunca para log ou autoria |
| `teacherId`, `teacherName`, `teacherActive` | UUID, texto, booleano | autor |
| `lessonDate` | data | só na aula |
| `period` | `SemesterPeriod` (`start`, `end`, `key`) | só no semestral |
| `approvedAt` | instante | exibido em dd/mm/aaaa hh:mm (fuso da escola, como no professor) |
| `approvedVersion` | inteiro ≥ 1 | "versão aprovada" (FR-006) |
| `reference()` | data | `lessonDate` ou `period.start` |

`toString()` não inclui nomes (mesmo cuidado de `AdminPageResponse`).

### 2.3 `HistoryOrder.NEWEST_FIRST`

`reference` desc. → `approvedAt` desc. → `reportId` desc. Regra única (FR-007); o adaptador a traduz em SQL e um
teste compara as duas (R-01). O histórico do professor passa a usar o mesmo comparador e a mesma consulta.

### 2.4 `SchoolHistoryPage`

`PageResult<ApprovedReportRow>` (já existe: `items`, `page`, `size`, `total`) mais `student` (resumo do aluno filtrado:
`id`, `name`, `active`), presente só quando o filtro de aluno aponta para um aluno existente.

## 3. Autorização (estende a 005)

`Operation` ganha `SCHOOL_HISTORY_QUERY`, `SCHOOL_REPORT_DOCUMENT_OPEN` e `SCHOOL_REPORT_DOCUMENT_DOWNLOAD`, todas
`ALLOW` só para `administratorWeb` e `DENY` para o professor nos dois canais, sem origem de sistema. A tabela completa
está em [contracts/permission-matrix-additions.md](./contracts/permission-matrix-additions.md);
`LESSON_AND_SEMESTER_REPORTS` não muda.

## 4. Registro de autoria (estende `authorship_record`, V10)

### 4.1 Campos novos (migração `V16__authorship_read_detail.sql`)

| Coluna | Tipo | Preenchida em | Regra |
|---|---|---|---|
| `filter_teacher_id` | `uuid` | `SCHOOL_HISTORY_QUERY` | filtro de professor, se houver |
| `filter_report_type` | `varchar(16)` | `SCHOOL_HISTORY_QUERY` | `LESSON` ou `SEMESTER`; nulo = os dois |
| `filter_period_start` | `date` | `SCHOOL_HISTORY_QUERY` | |
| `filter_period_end` | `date` | `SCHOOL_HISTORY_QUERY` | |
| `page_number` | `integer` | `SCHOOL_HISTORY_QUERY` | ≥ 0 |
| `document_version` | `integer` | `SCHOOL_REPORT_DOCUMENT_*` com sucesso | versão entregue (≥ 1) |

CHECKs novos: as seis colunas só podem ser não nulas quando `operation` é uma das três operações da feature;
`filter_report_type IN ('LESSON','SEMESTER')`; `page_number >= 0`; `document_version >= 1`. O CHECK
`ck_authorship_record_target_type` é recriado aceitando também `LESSON_REPORT` e `SEMESTER_REPORT`. Os gatilhos de
somente acréscimo da V10 continuam valendo.

No núcleo: `AuthorshipTargetType` ganha `LESSON_REPORT` e `SEMESTER_REPORT`; `AuthorshipRecord` ganha o componente
opcional `AuthorshipReadDetail(filterTeacherId, filterReportType, filterPeriodStart, filterPeriodEnd, pageNumber,
documentVersion)`, validado no construtor (mesmas regras dos CHECKs); `AuthorshipService` ganha `recordRead(...)`.

### 4.2 O que cada evento grava

| Evento | `operation` | `target_type` / `target_id` | Detalhe | `result` / `error_code` | Transação |
|---|---|---|---|---|---|
| Lista entregue (abrir, filtrar, paginar) | `SCHOOL_HISTORY_QUERY` | `STUDENT` + id, ou nulos sem filtro de aluno | filtros e página | `SUCCESS` | a mesma da leitura |
| Lista recusada por validação | `SCHOOL_HISTORY_QUERY` | idem | filtros recebidos que forem válidos | `FAILURE` / `VALIDATION_FAILED` | independente |
| PDF entregue | `SCHOOL_REPORT_DOCUMENT_OPEN` ou `_DOWNLOAD` | `LESSON_REPORT`/`SEMESTER_REPORT` + id | `document_version` | `SUCCESS` | a mesma da leitura |
| PDF negado (inexistente, não aprovado) | idem | idem (id pedido) | — | `FAILURE` / `RESOURCE_NOT_FOUND` | independente |
| PDF aprovado indisponível | idem | idem | — | `FAILURE` / `INTERNAL_ERROR` | independente |
| Credencial de professor na rota | `AUTHORIZATION_DENIED` (005) | — | — | `DENIED` | independente (já existe) |

Todos levam `author_id`, `author_role = ADMINISTRATOR`, `origin = WEB`, `occurred_at` e `correlation_id` (id da
requisição do `AdminSessionFilter`). Nenhum leva nome, CPF, nascimento, responsável, transcrição ou conteúdo de relatório
(FR-022). Se a gravação de sucesso falhar, nada é entregue (FR-023).

## 5. Índices (V16)

Parciais `WHERE status = 'APPROVED'`: `ix_lesson_report_approved_student (student_id)`,
`ix_lesson_report_approved_teacher (teacher_id)`, `ix_semester_report_approved_student (student_id)`,
`ix_semester_report_approved_teacher (teacher_id)`, `ix_semester_report_approved_period (period_start)`.
