# Contrato: operações novas da Matriz de permissões (FR-080, FR-081)

A matriz normativa continua sendo `specs/005-admin-registrations-access/contracts/permission-matrix.json`. No PR de
implementação, as linhas abaixo entram nesse arquivo (com `"note": "feature 007"`), na `Operation` e na
`PermissionMatrix.standardRules()` do núcleo; o teste de deriva e o teste de acesso cruzado gerado da matriz passam a
cobri-las.

```json
{ "operation": "AUDIT_TRAIL_QUERY", "administratorWeb": "ALLOW", "teacherWeb": "DENY", "teacherTelegram": "DENY", "webRoutes": ["GET /admin/audit-trail"], "system": [], "note": "feature 007" },
{ "operation": "AUDIT_TRAIL_VERIFY", "administratorWeb": "ALLOW", "teacherWeb": "DENY", "teacherTelegram": "DENY", "webRoutes": ["GET /admin/audit-trail/integrity"], "system": ["RETENTION"], "note": "feature 007" },
{ "operation": "ERASURE_REQUEST_REGISTER", "administratorWeb": "ALLOW", "teacherWeb": "DENY", "teacherTelegram": "DENY", "webRoutes": ["POST /admin/students/{studentId}/erasure-requests"], "system": [], "note": "feature 007" },
{ "operation": "ERASURE_REQUEST_READ", "administratorWeb": "ALLOW", "teacherWeb": "DENY", "teacherTelegram": "DENY", "webRoutes": ["GET /admin/erasure-requests", "GET /admin/erasure-requests/{requestId}"], "system": [], "note": "feature 007" },
{ "operation": "ERASURE_PREVIEW", "administratorWeb": "ALLOW", "teacherWeb": "DENY", "teacherTelegram": "DENY", "webRoutes": ["POST /admin/erasure-requests/{requestId}/preview"], "system": [], "note": "feature 007" },
{ "operation": "ERASURE_CONFIRM", "administratorWeb": "ALLOW", "teacherWeb": "DENY", "teacherTelegram": "DENY", "webRoutes": ["POST /admin/erasure-requests/{requestId}/confirmation"], "system": ["RETENTION"], "note": "feature 007: a retenção termina remoções de arquivo pendentes" },
{ "operation": "ERASURE_RECEIPT_READ", "administratorWeb": "ALLOW", "teacherWeb": "DENY", "teacherTelegram": "DENY", "webRoutes": ["GET /admin/erasure-requests/{requestId}/receipt"], "system": [], "note": "feature 007" },
{ "operation": "CONSENT_READ", "administratorWeb": "ALLOW", "teacherWeb": "DENY", "teacherTelegram": "DENY", "webRoutes": ["GET /admin/students/{studentId}/consents"], "system": [], "note": "feature 007: o professor vê só a situação no próprio aluno, pela leitura do aluno da 005" },
{ "operation": "CONSENT_RECORD", "administratorWeb": "ALLOW", "teacherWeb": "ALLOW_OWN_STUDENTS", "teacherTelegram": "ALLOW_OWN_STUDENTS", "webRoutes": ["POST /admin/students/{studentId}/consents", "POST /my-students", "PATCH /my-students/{studentId}", "POST /my-students/{studentId}/consents"], "system": ["INITIAL_LOAD"], "note": "feature 007: paridade do professor" },
{ "operation": "CONSENT_REVOKE", "administratorWeb": "ALLOW", "teacherWeb": "DENY", "teacherTelegram": "DENY", "webRoutes": ["POST /admin/consents/{consentId}/revocation"], "system": [], "note": "feature 007" },
{ "operation": "CONSENT_PENDING_READ", "administratorWeb": "ALLOW", "teacherWeb": "DENY", "teacherTelegram": "DENY", "webRoutes": ["GET /admin/consents/pending"], "system": [], "note": "feature 007" },
{ "operation": "CONSENT_TERM_READ", "administratorWeb": "ALLOW", "teacherWeb": "ALLOW", "teacherTelegram": "ALLOW", "webRoutes": ["GET /admin/consent-terms", "GET /my-students/consent-term"], "system": [], "note": "feature 007: texto institucional, sem dado de aluno" },
{ "operation": "CONSENT_TERM_PUBLISH", "administratorWeb": "ALLOW", "teacherWeb": "DENY", "teacherTelegram": "DENY", "webRoutes": ["POST /admin/consent-terms"], "system": [], "note": "feature 007" },
{ "operation": "OPERATIONS_COST_READ", "administratorWeb": "ALLOW", "teacherWeb": "DENY", "teacherTelegram": "DENY", "webRoutes": ["GET /admin/operations/costs"], "system": [], "note": "feature 007" },
{ "operation": "OPERATIONS_ALERT_READ", "administratorWeb": "ALLOW", "teacherWeb": "DENY", "teacherTelegram": "DENY", "webRoutes": ["GET /admin/operations/alerts", "GET /admin/operations/alerts/summary"], "system": [], "note": "feature 007" },
{ "operation": "OPERATIONS_ALERT_ACK", "administratorWeb": "ALLOW", "teacherWeb": "DENY", "teacherTelegram": "DENY", "webRoutes": ["POST /admin/operations/alerts/{alertId}/acknowledgement"], "system": [], "note": "feature 007" },
{ "operation": "RETENTION_RUN", "administratorWeb": "DENY", "teacherWeb": "DENY", "teacherTelegram": "DENY", "webRoutes": [], "system": ["RETENTION"], "note": "feature 007: só a tarefa agendada" },
{ "operation": "ALERT_EVALUATE", "administratorWeb": "DENY", "teacherWeb": "DENY", "teacherTelegram": "DENY", "webRoutes": [], "system": ["ALERT_EVALUATION"], "note": "feature 007: só a tarefa agendada" }
```

As leituras e decisões do professor sobre relatórios (`HISTORY_QUERY`, `REPORT_DOCUMENT_*`, aprovar, cancelar) continuam
sob `LESSON_AND_SEMESTER_REPORTS`; a 007 só passa a registrá-las (ver [audit-events.md](./audit-events.md)).

Durante o processamento, `ConsentGuardService` não é uma operação da matriz: é regra de negócio aplicada a quem já
foi autorizado.

## O que o teste de acesso cruzado prova (SC-009)

| Pedido | Esperado |
|---|---|
| Cada rota `/admin/**` acima sem cookie | 401 `NOT_AUTHENTICATED` |
| Cada rota `/admin/**` acima com cookie `session` de professor | 404 de rota inexistente; `AUTHORIZATION_DENIED` |
| `POST /my-students/{id}/consents` de aluno alheio | 404 `RESOURCE_NOT_FOUND` (escopo de alunos da 005) |
| Telegram: "apagar aluno", "eliminar", "auditoria", "trilha", "custos", "alertas" ou qualquer comando administrativo | negativa neutra da 005 (FR-037); nenhum caso de uso desta feature é chamado |
| Telegram: cadastro guiado de aluno menor sem consentimento | passo do consentimento obrigatório (ver [telegram-consent.md](./telegram-consent.md)) |
