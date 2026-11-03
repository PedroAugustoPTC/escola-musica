# Contrato: operações novas da Matriz de permissões

A matriz normativa continua sendo `specs/005-admin-registrations-access/contracts/permission-matrix.json` (FR-048 da
005). No PR de implementação, as três linhas abaixo entram nesse arquivo (com `"note": "feature 006"`), na
`Operation` e na `PermissionMatrix.standardRules()` do núcleo; o teste de deriva e o teste de acesso cruzado gerado da
matriz passam a cobri-las.

```json
{ "operation": "SCHOOL_HISTORY_QUERY", "administratorWeb": "ALLOW", "teacherWeb": "DENY", "teacherTelegram": "DENY", "webRoutes": ["GET /admin/school-history"], "system": [], "note": "feature 006: somente leitura de aprovados" },
{ "operation": "SCHOOL_REPORT_DOCUMENT_OPEN", "administratorWeb": "ALLOW", "teacherWeb": "DENY", "teacherTelegram": "DENY", "webRoutes": ["GET /admin/school-history/lesson-reports/{reportId}/pdf", "GET /admin/school-history/semester-reports/{reportId}/pdf"], "system": [], "note": "feature 006: PDF oficial da versão aprovada" },
{ "operation": "SCHOOL_REPORT_DOCUMENT_DOWNLOAD", "administratorWeb": "ALLOW", "teacherWeb": "DENY", "teacherTelegram": "DENY", "webRoutes": ["GET /admin/school-history/lesson-reports/{reportId}/pdf/download", "GET /admin/school-history/semester-reports/{reportId}/pdf/download"], "system": [], "note": "feature 006: PDF oficial da versão aprovada" }
```

Sem mudança: `LESSON_AND_SEMESTER_REPORTS` (`DENY` para o administrador; o professor só os seus) — o administrador não
alcança nenhuma rota de escrita, de versão ou de documento da 001 (Princípio III).

## O que o teste de acesso cruzado prova (SC-003, SC-004, SC-007)

| Pedido | Esperado |
|---|---|
| Cada rota acima sem cookie | 401 `NOT_AUTHENTICATED` |
| Cada rota acima com cookie `session` de professor (inclusive o autor) | 404 `RESOURCE_NOT_FOUND`, corpo igual ao de rota inexistente; registro `AUTHORIZATION_DENIED` |
| Rotas de documentos e de histórico do professor (`/lesson-reports/**`, `/semester-reports/**`, `/students/{id}/history`) com só o cookie `admin_session` válido | 404 de rota inexistente (`CrossCredentialAnswerer`, FR-003 da 005); nenhum PDF entregue |
| Qualquer `POST`/`PATCH`/`PUT`/`DELETE` sob `/admin/school-history/**` | 404 de rota inexistente; nenhum relatório muda |
| `/admin/school-history/{lesson,semester}-reports/{id}/versions/{n}/pdf` | 404 de rota inexistente |
| Telegram: qualquer texto ou botão pedindo histórico da escola | negativa neutra da 005 (FR-037); nenhum caso de uso desta feature é chamado |
