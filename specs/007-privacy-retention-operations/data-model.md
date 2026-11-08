# Data Model: Privacidade, retenção e operação (LGPD)

**Feature**: `007-privacy-retention-operations` | **Plano**: [plan.md](./plan.md) | **Decisões**: [research.md](./research.md)

Uma migração, `V17__privacy_retention_operations.sql`, **aditiva**: não apaga linha existente; afrouxa `NOT NULL` só
onde a retenção e a eliminação precisam remover valor. Nomes em inglês (Princípio VI); termos novos entram no
`glossary.json` antes (consent, erasure, retention, audit, alert, operational, inventory, purge, anchor).

## 1. Alterações em tabelas existentes

### 1.1 `student` (eliminação, FR-022)

| Coluna | Mudança |
|---|---|
| `name`, `birth_date`, `cpf_encrypted`, `cpf_hash` | `DROP NOT NULL` (ficam `NULL` só no aluno eliminado) |
| `erased_at timestamptz` | nova; `NULL` = não eliminado |
| CHECK `ck_student_erased` | `erased_at IS NULL` ⇒ `name`, `birth_date`, `cpf_encrypted`, `cpf_hash` não nulos; `erased_at IS NOT NULL` ⇒ esses, `guardian_name`, `guardian_phone`, `guardian_email`, `instrument` nulos e `active = false` |

A restrição única `uk_student_cpf_hash` já ignora `NULL`: o CPF antigo fica livre (FR-024). As consultas de aluno
(`findVisible`, buscas, listas, histórico) passam a filtrar `erased_at IS NULL`; o rótulo "Aluno removido · #XXXXXX" é
montado na exibição (catálogo de mensagens), nunca gravado.

### 1.2 Relatórios e versões (retenção e eliminação)

| Tabela | Mudança |
|---|---|
| `lesson_report` | `transcription` `DROP NOT NULL`; `source_removed_at timestamptz`; `anonymized_at timestamptz` |
| `semester_report` | `general_information` `DROP NOT NULL`; `source_removed_at`; `anonymized_at` |
| `report_interaction` | `text` `DROP NOT NULL`; `text_removed_at timestamptz` |
| `report_version` | `content_removed_at timestamptz` (o `content` vira `'{}'::jsonb`, mantido `NOT NULL`) |
| `pdf_document` | `content_removed_at timestamptz` (a linha fica; o arquivo some) |

Regras de domínio (núcleo):

- **Aprovado vigente** = relatório `APPROVED`, versão `approved_version`; sua `report_version` e o `pdf_document` dela
  nunca recebem `content_removed_at` pela retenção (FR-011). Só a eliminação os alcança.
- `source_removed_at` ⇒ `lesson_report.transcription` nulo e `report_interaction.text` nulos do relatório. No
  semestral, `source_removed_at` só anula `report_interaction.text`: `general_information` faz parte do semestral
  oficial (PDF e exibição) e **nunca** é removido de semestral aprovado; só fica nulo com `anonymized_at` (cancelado
  vencido ou eliminação). CHECK `ck_semester_report_general_information`: `general_information IS NULL` ⇒
  `anonymized_at IS NOT NULL`.
- `anonymized_at` (cancelado vencido ou eliminação) ⇒ fonte removida, todas as versões com `content_removed_at`,
  todos os PDFs com `content_removed_at`.

### 1.3 `ai_call` (métricas, FR-051)

| Coluna | Mudança |
|---|---|
| `channel varchar(12)` | nova; preenchida das linhas antigas por `processing_job.request_channel` (`UPDATE ... FROM`); depois `NOT NULL` |
| índice `ix_ai_call_created` | `(created_at)` para o painel mensal e os alertas |
| índice `ix_ai_call_provider_recent` | `(provider_model, created_at DESC)` para "3 falhas seguidas" |

Nada mais muda: `task`, `provider_model`, `audio_seconds`, `input_tokens`, `output_tokens`, `duration_ms`,
`estimated_cost_usd`, `success`, `error_code`, `report_type`, `report_id` já existem.

### 1.4 `authorship_record` → trilha única (FR-040 a FR-046)

| Coluna | Mudança |
|---|---|
| `sequence bigint` | nova, `GENERATED ALWAYS AS IDENTITY`, única; ordem da cadeia. Linhas antigas recebem a sequência em ordem de `(occurred_at, id)` na migração (a migração é a única exceção aos gatilhos, que ela desliga e religa dentro da própria transação) |
| `previous_hash varchar(64)`, `record_hash varchar(64)` | novas; `^[0-9a-f]{64}$`; para as linhas antigas, calculadas pela migração com a mesma forma canônica do núcleo (teste compara) |
| `item_count integer` | nova; só em `RETENTION_RUN`, `ERASURE_COMPLETED`, `AUDIT_TRAIL_PURGED` |
| `filter_operation varchar(48)`, `filter_actor_id uuid`, `filter_target_id uuid` | novas; só em `AUDIT_TRAIL_QUERY` |
| CHECK `origin` | + `RETENTION`, `ALERT_EVALUATION` (atores de sistema) |
| CHECK `target_type` | + `CONSENT`, `CONSENT_TERM`, `ERASURE_REQUEST`, `RETENTION_RUN`, `AUDIT_TRAIL`, `ALERT` |
| CHECK de detalhe | estende `ck_authorship_record_read_detail_operation` às leituras do professor (`HISTORY_QUERY`, `REPORT_DOCUMENT_OPEN`, `REPORT_DOCUMENT_DOWNLOAD`, `REPORT_DOCUMENT_SENT`) e a `AUDIT_TRAIL_QUERY` |

Gatilhos: `UPDATE` e `TRUNCATE` continuam recusados sempre; `DELETE` só passa com
`current_setting('reports.audit_purge', true) = 'on'`, que só `purge_authorship_record` define.

**Forma canônica do evento** (núcleo, `AuditChain`): campos em ordem fixa, separados por `|`, `NULL` como string vazia,
datas em ISO-8601 UTC com microssegundos: `sequence|occurred_at|author_id|author_role|origin|operation|target_type|
target_id|result|error_code|origin_hash|correlation_id|filter_*|page_number|document_version|item_count`.
`record_hash = sha256_hex(previous_hash + "|" + canônica)`; o primeiro evento (ou o primeiro após a âncora) usa o
`record_hash` da âncora, ou 64 zeros.

## 2. Tabelas novas

### 2.1 `audit_chain_anchor`

| Coluna | Tipo | Regra |
|---|---|---|
| `id` | uuid PK | |
| `purged_until_sequence` | bigint | último `sequence` removido |
| `last_record_hash` | varchar(64) | `record_hash` desse evento |
| `purged_at` | timestamptz | |
| `purged_count` | integer | ≥ 1 |

Só acréscimo (mesmos gatilhos). A verificação parte da âncora mais recente.

### 2.2 `consent_term_version` (FR-032)

| Coluna | Tipo | Regra |
|---|---|---|
| `id` | uuid PK | |
| `number` | integer | único, ≥ 1, crescente |
| `published_on` | date | não futura |
| `text` | text | 1–20 000 caracteres; texto institucional, sem dado de aluno |
| `requires_new_consent` | boolean | se `true`, consentimentos de versões anteriores deixam de valer |
| `created_by` | uuid | administrador |
| `created_at` | timestamptz | |

Versão **vigente** = maior `number`. Versão **compatível** = qualquer versão com `number` ≥ o da última versão com
`requires_new_consent = true`. Imutável depois de publicada (nova versão em vez de editar).

### 2.3 `student_consent` (FR-030 a FR-033)

| Coluna | Tipo | Regra |
|---|---|---|
| `id` | uuid PK | |
| `student_id` | uuid FK `student` | |
| `consenting_name` | varchar(255) | `PersonName`; `NULL` só após eliminação (FR-023) |
| `relationship` | varchar(16) | `MOTHER`, `FATHER`, `LEGAL_GUARDIAN`, `OTHER` |
| `consented_on` | date | não futura |
| `term_version_id` | uuid FK `consent_term_version` | |
| `collection_method` | varchar(16) | `SIGNED_PAPER`, `DIGITAL`, `OTHER` |
| `recorded_by_id`, `recorded_by_role` | uuid, varchar(16) | `ADMINISTRATOR`, `TEACHER` ou `NULL` + origem `INITIAL_LOAD` |
| `recorded_origin` | varchar(24) | `WEB`, `TELEGRAM`, `INITIAL_LOAD` |
| `recorded_at` | timestamptz | |
| `revoked_on` | date | não futura, ≥ `consented_on` |
| `revoked_by_name_role` | varchar(16) | quem revogou, por papel (`MOTHER`… ou `STUDENT`); sem nome |
| `revocation_recorded_by` | uuid | administrador |

Índice `(student_id, recorded_at DESC)`. Cada registro é novo; o anterior fica (histórico). **Situação derivada** (não
gravada) por `ConsentPolicy`:

```text
VALID      → não revogado, versão compatível
REVOKED    → revoked_on preenchido
SUPERSEDED → versão anterior à última versão que exige novo consentimento
```

Aluno menor ("idade como na 005 FR-017", no fuso da escola) **exige** VALID para qualquer chamada de IA; aluno ≥ 18
não exige. Mudança da data de nascimento que torna menor exige `ConsentInput` na mesma edição.

### 2.4 `erasure_request` (FR-020 a FR-025)

| Coluna | Tipo | Regra |
|---|---|---|
| `id` | uuid PK | |
| `student_id` | uuid FK `student` | um pedido não concluído por aluno (índice único parcial) |
| `requester_role` | varchar(16) | `STUDENT`, `MOTHER`, `FATHER`, `LEGAL_GUARDIAN`, `OTHER` |
| `requester_name` | varchar(255) | `NULL` depois de `COMPLETED` |
| `requested_on` | date | não futura |
| `received_via` | varchar(16) | `IN_PERSON`, `EMAIL`, `PHONE`, `LETTER`, `OTHER` |
| `protocol_reference` | varchar(40) | `^[A-Za-z0-9./-]{1,40}$` (sem espaço, sem dado pessoal) |
| `status` | varchar(16) | `RECEIVED`, `IN_PROGRESS`, `COMPLETED` |
| `registered_by` | uuid | administrador |
| `confirmation_token_hash` | varchar(64) | HMAC do token; `NULL` fora da prévia |
| `confirmation_expires_at` | timestamptz | prévia + 10 min |
| `confirmed_by`, `confirmed_at` | uuid, timestamptz | |
| `completed_at` | timestamptz | |
| `scope_lesson_reports`, `scope_semester_reports`, `scope_pdfs`, `scope_assignments`, `scope_consents` | integer | alcance em números, gravado na confirmação |
| `created_at`, `lock_version` | | |

Transições:

```text
RECEIVED --preview--> RECEIVED (token novo; o anterior deixa de valer)
RECEIVED --confirm(token válido, palavra ELIMINAR)--> IN_PROGRESS   [transação do banco]
IN_PROGRESS --arquivos removidos--> COMPLETED                        [requester_name := NULL]
IN_PROGRESS --confirm de novo--> IN_PROGRESS → COMPLETED              [retomada idempotente]
```

### 2.5 `erased_student` (FR-026)

`student_id uuid PK`, `erased_at timestamptz NOT NULL`. Só acréscimo (gatilho igual ao da trilha). Base para reaplicar
eliminações após uma restauração (próxima fase).

### 2.6 `retention_run` e `retention_run_item` (FR-015)

`retention_run`: `id`, `started_at`, `finished_at`, `status` (`RUNNING`, `SUCCEEDED`, `FAILED`, `INTERRUPTED`),
`error_code`, `policy_snapshot jsonb` (só números de dias). Índice único parcial `WHERE status = 'RUNNING'`.

`retention_run_item`: `run_id`, `item_type` (`LESSON_SOURCE`, `SEMESTER_SOURCE`, `UNAPPROVED_PDF`,
`CANCELLED_LESSON_REPORT`, `CANCELLED_SEMESTER_REPORT`, `PDF_FILE`, `AUDIT_EVENTS`, `ALERT_HISTORY`, `RUN_HISTORY`),
`target_id uuid` (`NULL` para os agregados), `count integer`. Sem conteúdo. Os registros de execução seguem o prazo
`reports.retention.run-history-days` (padrão = guarda da trilha).

### 2.7 `operational_alert` (FR-055, FR-056)

| Coluna | Tipo | Regra |
|---|---|---|
| `id` | uuid PK | |
| `type` | varchar(32) | `AI_PROVIDER_FAILING`, `CHANNEL_QUEUE_BACKLOG`, `STORAGE_FAILED`, `RETENTION_FAILED`, `AUDIT_CHAIN_BROKEN` |
| `component` | varchar(64) | `^[a-z0-9._-]{1,64}$` (provedor configurado, `telegram`, `pdf-storage`, `retention`, `audit-trail`) |
| `detail_code` | varchar(40) | código do tipo de falha (`TIMEOUT`, `HTTP_5XX`, `BACKLOG_AGE`, `BACKLOG_SIZE`…) |
| `status` | varchar(12) | `ACTIVE`, `NORMALIZED` |
| `started_at`, `last_seen_at`, `normalized_at` | timestamptz | |
| `occurrences` | integer | ≥ 1 |

Índice único parcial `(type, component) WHERE status = 'ACTIVE'` (um alerta ativo por condição). Sem identificador de
aluno nem texto livre (CHECK). Alertas normalizados mais velhos que a guarda da trilha saem pela retenção.

## 3. Entidades do núcleo (sem anotação de framework)

| Pacote | Tipo | Responsabilidade |
|---|---|---|
| `domain/privacy` | `RetentionPolicy` | prazos efetivos + avisos; `from(Map<String,String>)` aplica FR-001 |
| | `RetentionDecision` | `KEEP`, `REMOVE_SOURCE`, `REMOVE_UNAPPROVED_PDFS`, `ANONYMIZE` |
| | `ConsentTermVersion`, `GuardianConsent`, `ConsentInput`, `ConsentPolicy`, `ConsentStatus` | ver 2.2 e 2.3 |
| | `ErasureRequest`, `ErasureStatus`, `ErasureScope`, `ErasureConfirmation` | ver 2.4; palavra `ELIMINAR` e validade 10 min no domínio |
| `domain/audit` | `AuditChain`, `AuditTrailFilter` (período ≤ 90 dias obrigatório), `AuditEventType` | |
| `domain/operations` | `OperationalAlert`, `AlertType`, `AlertThresholds`, `PriceTable`, `PriceEntry`, `MonthlyCostReport` | |
| `domain/access` | `ActionOrigin` + `RETENTION`, `ALERT_EVALUATION`; `Operation` + as da [matriz](./contracts/permission-matrix-additions.md) | |

## 4. Portas

| Porta | Situação | Métodos |
|---|---|---|
| `DocumentStoragePort` | existente | + `delete(String key)` (idempotente) |
| `LessonReportRepository`, `SemesterReportRepository` | existentes | + `findRetentionCandidates(policy, now, limit)`, `findForUpdate(id)`, `removeSource`, `anonymize`, `findAllByStudent` |
| `ReportVersionRepository`, `PdfDocumentRepository`, `ReportInteractionRepository` | existentes | + remoção de conteúdo/texto marcada, `findPendingFileRemovals` |
| `StudentRepository` | existente | + `erase(id, now)`; consultas excluem `erased_at` |
| `AssignmentRepository`, `ChannelDocumentRepository`, `ChannelActionRepository` | existentes | + remoções por aluno/relatório e limpeza de `payload` finalizado |
| `CostRecordPort` | existente | `AiCall` ganha `channel` |
| `AuthorshipRecorderPort` | existente | inalterada na assinatura; o adaptador passa a encadear (R-03) |
| `AuditTrailQueryPort` | nova | `query(AuditTrailFilter, page)`; `chainFrom(anchor, batch)` |
| `AuditTrailRetentionPort` | nova | `purgeOlderThan(Instant cutoff)` → quantidade (só `RunRetentionUseCase`) |
| `ConsentRepository`, `ConsentTermRepository` | novas | |
| `ErasureRequestRepository`, `RetentionRunRepository`, `OperationalAlertRepository` | novas | |
| `OperationalSignalQueryPort` | nova | falhas recentes por provedor; idade e tamanho da fila `PENDING` |
| `CostReportQueryPort` | nova | agregações mensais (FR-053) |

## 5. Papéis do banco (R-03)

| Papel | Uso | Privilégios |
|---|---|---|
| `reports` (existente, dono) | só Flyway | tudo |
| `reports_app` (novo) | pool da aplicação | `SELECT, INSERT` em `authorship_record`, `audit_chain_anchor`, `erased_student`; `SELECT, INSERT, UPDATE, DELETE` nas demais; `USAGE` nas sequências; `EXECUTE` em `purge_authorship_record`; sem `TRUNCATE`, `TRIGGER`, `REFERENCES` novos nem DDL |

`ALTER DEFAULT PRIVILEGES FOR ROLE reports` concede o mesmo às tabelas futuras (as de acréscimo são revogadas na
própria migração que as cria).
