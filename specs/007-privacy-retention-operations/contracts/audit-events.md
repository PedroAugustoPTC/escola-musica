# Contrato: eventos da trilha de auditoria (FR-040 a FR-046, SC-002)

A trilha é a tabela `authorship_record` da 005/006, estendida pela `V17` ([data-model.md](../data-model.md) §1.4).
Esta tabela é a **fonte** do teste `AuditEventCoverageIT`: para cada linha e cada canal marcado, a operação executada
gera **exatamente um** evento com os campos indicados. Linha nova nesta lista exige linha nova no teste.

## Campos

Comuns: `id`, `sequence`, `occurred_at`, `author_id`, `author_role` (`ADMINISTRATOR`, `TEACHER` ou nulo = sistema),
`origin` (canal ou ator de sistema), `operation`, `target_type`, `target_id`, `result`, `error_code`,
`correlation_id`, `previous_hash`, `record_hash`. Só autenticações levam `origin_hash`. Detalhes tipados só onde a
coluna "Detalhe" indica.

**Nunca** (FR-042): conteúdo de relatório, transcrição, áudio, nome do aluno, do responsável ou do professor, CPF, data
de nascimento, telefone, e-mail, código, senha, token, texto digitado.

## Gravação

- **Mesma transação** (sucesso): a operação e o evento valem juntos; sem evento, a operação não vale (500
  `INTERNAL_ERROR` com `reference`; no Telegram, a mensagem de falha interna com o código).
- **Independente** (falha, negativa, bloqueio): gravada mesmo com a transação desfeita.
- **Exceção** (spec, Edge Cases): a retenção que não consegue gravar o seu evento **para** e abre `RETENTION_FAILED`.

## Lista

Legenda de canal: W = Web, T = Telegram, S = sistema. "Existe" = já gravado pela 005/006; "Novo" = a 007 acrescenta.

| Grupo | `operation` | Canais | Alvo | Detalhe | Situação |
|---|---|---|---|---|---|
| Acesso | `ADMIN_SIGN_IN` (sucesso e falha), `ADMIN_ACTIVATE`, `ADMIN_SIGN_OUT`, `ADMIN_CHANGE_PASSWORD` | W | `ADMINISTRATOR` | `origin_hash` | Existe |
| | `TEACHER_SIGN_IN` (sucesso e falha) | W, T | `TEACHER` | `origin_hash` | Existe |
| | `TEACHER_SIGN_OUT` | W | `TEACHER` | — | **Novo** |
| | `ATTEMPT_BLOCKED` | W, T | — | `origin_hash` | Existe |
| | `TEACHER_LINK_TELEGRAM`, `TEACHER_UNLINK_TELEGRAM` | T, W | `TEACHER` | — | Existe |
| | `AUTHORIZATION_DENIED` | W, T | conforme pedido | — | Existe |
| Cadastros | `TEACHER_REGISTER`, `TEACHER_UPDATE`, `TEACHER_INACTIVATE`, `TEACHER_REACTIVATE`, `TEACHER_REISSUE_ACCESS_CODE` | W | `TEACHER` | — | Existe |
| | `STUDENT_REGISTER`, `STUDENT_UPDATE`, `STUDENT_INACTIVATE`, `STUDENT_REACTIVATE` | W, T | `STUDENT` | — | Existe |
| | `ASSIGNMENT_ASSIGN`, `ASSIGNMENT_REMOVE` | W | `ASSIGNMENT` | — | Existe |
| | `ADMINISTRATOR_CREATE` (emite código de ativação), `ADMINISTRATOR_INACTIVATE`, `ADMINISTRATOR_REACTIVATE` | W | `ADMINISTRATOR` | — | Existe |
| Relatórios | `LESSON_REPORT_APPROVE`, `LESSON_REPORT_CANCEL` | W, T | `LESSON_REPORT` | `document_version` (aprovada) | **Novo** |
| | `SEMESTER_REPORT_APPROVE`, `SEMESTER_REPORT_CANCEL` | W, T | `SEMESTER_REPORT` | `document_version` | **Novo** |
| Leituras do professor | `HISTORY_QUERY` | W, T | `STUDENT` | `page_number` | **Novo** |
| | `REPORT_DOCUMENT_OPEN`, `REPORT_DOCUMENT_DOWNLOAD` | W | `LESSON_REPORT`/`SEMESTER_REPORT` | `document_version` | **Novo** |
| | `REPORT_DOCUMENT_SENT` (documento enviado no chat) | T | idem | `document_version` | **Novo** |
| Leituras do administrador | `SCHOOL_HISTORY_QUERY`, `SCHOOL_REPORT_DOCUMENT_OPEN`, `SCHOOL_REPORT_DOCUMENT_DOWNLOAD` | W | conforme 006 | conforme 006 | Existe |
| Consentimento | `CONSENT_RECORD` | W, T | `CONSENT` (+ aluno em `filter_target_id`) | — | **Novo** |
| | `CONSENT_REVOKE` | W | `CONSENT` | — | **Novo** |
| | `CONSENT_TERM_PUBLISH` | W | `CONSENT_TERM` | — | **Novo** |
| | `CONSENT_BLOCKED_AI` (pedido de IA recusado por falta de consentimento) | W, T, S | `STUDENT` | — | **Novo** (`result = BLOCKED`, `error_code = CONSENT_REQUIRED`) |
| Eliminação | `ERASURE_REQUEST_REGISTER` | W | `ERASURE_REQUEST` | — | **Novo** |
| | `ERASURE_PREVIEW` | W | `ERASURE_REQUEST` | — | **Novo** |
| | `ERASURE_CONFIRM` | W | `ERASURE_REQUEST` | — | **Novo** |
| | `ERASURE_COMPLETED` | W/S | `ERASURE_REQUEST` | `item_count` | **Novo** |
| | `ERASURE_RECEIPT_READ` | W | `ERASURE_REQUEST` | — | **Novo** |
| Retenção | `RETENTION_RUN` | S (`RETENTION`) | `RETENTION_RUN` | `item_count` | **Novo** |
| | `AUDIT_TRAIL_PURGED` | S | `AUDIT_TRAIL` | `item_count` | **Novo** |
| Trilha | `AUDIT_TRAIL_QUERY` | W | `AUDIT_TRAIL` | `filter_period_*`, `filter_operation`, `filter_actor_id`, `filter_target_id`, `page_number` | **Novo** |
| | `AUDIT_TRAIL_VERIFY` | W, S | `AUDIT_TRAIL` | `item_count` (eventos verificados) | **Novo** |
| Operação | `OPERATIONS_COST_READ`, `OPERATIONS_ALERT_READ` (lista; o resumo do cabeçalho não gera evento) | W | — / `ALERT` | — | **Novo** |
| | `OPERATIONS_ALERT_ACK` | W | `ALERT` | — | **Novo** |
| | `ALERT_OPENED`, `ALERT_NORMALIZED` | S (`ALERT_EVALUATION`) | `ALERT` | — | **Novo** |

Fora da trilha: expiração de sessão (regra da 005), o resumo de alertas do cabeçalho (consultado a cada 60 s; registrar
cada consulta encheria a trilha sem valor), a busca de aluno por nome (`STUDENT_LIST`, já sem registro na 006).

Filtrar a trilha por um **aluno** (`targetId = studentId`) devolve os eventos cujo alvo é o aluno **e** os cujo alvo é
um relatório ou consentimento dele (junção na consulta, sem gravar o aluno no evento do relatório).
