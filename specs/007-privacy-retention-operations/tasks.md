---

description: "Tarefas da feature 007 — privacidade, retenção e operação (LGPD)"
---

# Tasks: Privacidade, retenção e operação (LGPD)

**Input**: `specs/007-privacy-retention-operations/` — [spec.md](./spec.md), [plan.md](./plan.md),
[research.md](./research.md), [data-model.md](./data-model.md), [contracts/](./contracts/), [quickstart.md](./quickstart.md)

**Tests**: pedidos (Princípio V e pedido do `/speckit-tasks`). Em cada história os testes vêm **antes** e devem falhar
antes da implementação. A varredura automática de **logs, métricas (`ai_call`), alertas e trilha** atrás de CPF, nome de
aluno, transcrição e texto de relatório nasce na Fundação (T014–T016) e cada história acrescenta os seus fluxos a ela;
a varredura completa, nos dois canais, fecha em T130 (SC-001).

**Organização**: uma fase por história. O pedido listou as histórias como retenção; eliminação; consentimento;
auditoria; observabilidade e custos; inventário. As fases seguem a **prioridade e as dependências da spec**, porque
retenção e eliminação gravam na trilha e a eliminação reaproveita a remoção da retenção:

| Fase | História (spec) | Pedido | Prioridade |
|---|---|---|---|
| 3 | US1 — Trilha de auditoria | auditoria | P1 (MVP) |
| 4 | US2 — Consentimento | consentimento | P1 (MVP) |
| 5 | US3 — Retenção | retenção | P2 |
| 6 | US4 — Eliminação a pedido | eliminação a pedido | P2 |
| 7 | US5 — Tempo, custo e alertas | observabilidade e custos | P3 |
| 8 | US6 — Inventário | inventário | P3 |

## Formato: `- [ ] T### [P?] [US#?] Descrição com caminho do arquivo`

- **[P]**: pode rodar em paralelo — arquivo(s) exclusivo(s) e nenhuma dependência de tarefa ainda aberta, salvo a escrita
  como "(após T###)".
- **[US#]**: história da spec. Setup, Fundação e Fecho não levam rótulo.
- Identificadores em **inglês**; textos de tela e de log em **português acentuado** (Princípio VI). Termo novo entra no
  `glossary.json` em T003, antes de qualquer código.
- Administrador **somente Web**; professor com paridade Web ≡ Telegram pelos mesmos casos de uso (Princípio I).

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
| `SPEC/` | `specs/007-privacy-retention-operations/` |
| `SPEC005/` | `specs/005-admin-registrations-access/` |

Testes de integração terminam em `IT` e usam Postgres real. Comandos sempre em contêiner:
`sh infra/scripts/local.sh tests backend|frontend|e2e`. Dublês do núcleo ficam em `CORE_T/doubles/` (padrão
`InMemory*Repository`, `Fake*`, `FixedClock`), com o próprio `*Test`.

---

## Phase 1: Setup (linha de base, glossário e ambiente)

- [X] T001 Atualizar a branch `feat/007-privacy-retention-operations` a partir da `main` (`git checkout main && git pull --ff-only origin main`, depois `git merge main` na branch) e registrar a linha de base em `SPEC/evidence/baseline.md`: resultado de `sh infra/scripts/local.sh tests backend` e `tests frontend` (contagem de testes, cobertura de linha e branch do núcleo, guardiões de idioma verdes).
- [X] T002 Rodar o guardião em modo relatório (`GUARD_REPORT=1`) com os nomes planejados em [data-model.md](./data-model.md) e [plan.md](./plan.md) e listar as palavras novas em `SPEC/evidence/glossary-candidates.md`.
- [X] T003 Acrescentar ao `.specify/memory/glossary.json` os termos pt→en usados pela feature (consentimento→`consent`, responsável→`guardian` se ausente, termo→`term`, revogação→`revocation`, eliminação→`erasure`, pedido→`request`, protocolo→`protocol`, comprovante→`receipt`, retenção→`retention`, execução→`run`, expurgo→`purge`, âncora→`anchor`, cadeia→`chain`, trilha/auditoria→`audit`, alerta→`alert`, operação→`operational`, limite→`threshold`, preço→`price`, vigência→`effective`, inventário→`inventory`, encarregado→`officer`, sinal→`signal`), cada um com um único equivalente; rodar `tests backend` e `tests frontend` (guardiões verdes).
- [X] T004 [P] Acrescentar a `infra/.env.example` as variáveis de [contracts/configuration.md](./contracts/configuration.md) (sem valor real; `DB_APP_PASSWORD` com marcador) e documentá-las em `infra/README.md` (seção de variáveis).
- [X] T005 [P] Configurar a rotação de logs com `x-logging` (`json-file`, `max-size: 20m`, `max-file: 5`) aplicada a todos os serviços de `infra/compose.yaml`, e estender `APP_T/architecture/ComposeGuardTest.java` para reprovar serviço sem `logging` (R-08).

---

## Phase 2: Foundational (bloqueia todas as histórias)

**Propósito**: migração, papel de banco, política validada no núcleo, novas origens de ator, correlação nos logs e a
varredura de vazamento reaproveitável.

### Testes da Fundação (escrever primeiro; devem falhar)

- [X] T006 [P] Teste de unidade de `RetentionPolicy.from(Map<String,String>)` em `CORE_T/domain/privacy/RetentionPolicyTest.java`: ausente → padrão (30/30/90/1826 dias); zero, negativo e não numérico → padrão + aviso com o nome da variável; valor válido → configurado; origem `DEFAULT`/`CONFIGURED` por campo (FR-001, FR-002).
- [X] T007 [P] Teste de unidade de `AlertThresholds.from(...)` em `CORE_T/domain/operations/AlertThresholdsTest.java` com os padrões de FR-055 (3 falhas, 20%, 15 min, 5 min, 50 itens, 15 min de silêncio, 60 s) e as mesmas regras de inválido.
- [X] T008 [P] Teste de unidade de `PriceTable.from(...)` em `CORE_T/domain/operations/PriceTableTest.java`: preço vigente por data (fronteira inclusiva em `effectiveFrom`), lista vazia cai nas propriedades antigas com `1970-01-01`, valor ≤ 0 ignorado com aviso, custo fixo mensal com vigência (FR-052).
- [X] T009 [P] Teste de unidade de `AuditChain` em `CORE_T/domain/audit/AuditChainTest.java`: forma canônica na ordem de [data-model.md](./data-model.md) §1.4 (`NULL` = vazio, datas ISO-8601 UTC com microssegundos), `record_hash = sha256_hex(previous + "|" + canônica)`, primeiro evento com 64 zeros ou com o hash da âncora, `verify(sequence)` aponta a primeira linha quebrada.
- [X] T010 [P] Teste de migração em `APP_T/outbound/persistence/V17MigrationIT.java` (Postgres real): banco migrado até `V16` com linhas de autoria, relatórios, versões, PDFs e `ai_call` → aplica `V17`; nenhuma linha some; `ai_call.channel` preenchido pelo `processing_job.request_channel`; `sequence`/`previous_hash`/`record_hash` das linhas antigas batem com `AuditChain` (mesma forma canônica); CHECKs novos de `student` (`ck_student_erased`) e de `authorship_record` valem; gatilhos seguem recusando `UPDATE`/`TRUNCATE`.
- [X] T011 [P] Teste do papel de banco em `APP_T/outbound/persistence/DatabaseRolesIT.java`: conectado como `reports_app`, `INSERT`/`SELECT` em `authorship_record` funcionam; `UPDATE`, `DELETE`, `TRUNCATE`, `ALTER TABLE ... DISABLE TRIGGER` e `CREATE TABLE` falham por permissão; `UPDATE` em `student` funciona; o pool da aplicação usa `reports_app` e o Flyway usa o dono (R-03).
- [X] T012 [P] Teste de configuração em `APP_T/config/PrivacyPropertiesIT.java`: propriedades `reports.retention.*`, `reports.alerts.*`, `reports.costs.*` e `reports.privacy.*` chegam brutas ao núcleo; valor inválido sobe a aplicação com `WARN` citando a variável e sem o valor; `PRIVACY_OFFICER_*` vazio gera aviso.
- [X] T013 [P] Teste de correlação em `APP_T/inbound/web/RequestCorrelationTest.java` (estender): toda linha de log JSON emitida durante uma requisição Web e durante um update da API interna do Telegram tem `correlationId` igual ao da autoria (FR-050).
- [X] T014 [P] Criar a varredura reaproveitável `APP_T/support/PersonalDataSweep.java` (usa `LeakAudit`, `LogCapture`, `CanaryStudent` e `CanaryCpf`): procura os valores marcados (nome do aluno, CPF com e sem máscara, data de nascimento nos três formatos, nome/telefone/e-mail do responsável, trecho de transcrição simulada e trecho de texto de relatório simulado) em (a) logs capturados, (b) todas as colunas de texto e `jsonb` de `ai_call`, (c) `operational_alert`, (d) `authorship_record`, (e) `retention_run_item` e `erasure_request` (exceto `requester_name` antes de concluído); cada achado cita passo, rótulo e lugar, nunca o valor.
- [X] T015 [P] Autoteste em `APP_T/support/PersonalDataSweepTest.java`: valor plantado em cada um dos cinco lugares é encontrado; tabela vazia e log limpo passam; mensagem de falha não contém o valor.
- [X] T016 [P] Acrescentar a `APP_T/support/CanaryStudent.java` os valores `TRANSCRIPTION_SNIPPET` e `REPORT_TEXT_SNIPPET` (frases fictícias únicas), e fazer o provedor simulado de transcrição e de LLM (`RES/ai/simulated/`) devolvê-los quando o áudio canário for usado, incluindo-os em `sweepValues()`.

### Implementação da Fundação

- [X] T017 Criar `RES/db/migration/V17__privacy_retention_operations.sql` conforme [data-model.md](./data-model.md): §1.1–§1.4 (colunas novas, `DROP NOT NULL`, CHECKs, `ai_call.channel` com preenchimento e `NOT NULL`, índices), §2 (todas as tabelas novas com CHECKs e índices únicos parciais), gatilhos de só acréscimo em `audit_chain_anchor` e `erased_student`, gatilho de `DELETE` da trilha condicionado a `reports.audit_purge`, função `purge_authorship_record(cutoff)` `SECURITY DEFINER` com piso de 365 dias, cálculo de `sequence`/`previous_hash`/`record_hash` das linhas antigas (desliga e religa os gatilhos dentro da transação), e §5 (`CREATE ROLE reports_app NOLOGIN` se ausente, `GRANT`s, `ALTER DEFAULT PRIVILEGES`). Comentários em português.
- [X] T018 Criar `infra/scripts/lib/db-roles.sh` (idempotente: `ALTER ROLE reports_app LOGIN PASSWORD` com `DB_APP_PASSWORD` via `docker compose exec` no contêiner do Postgres, senha nunca impressa) e chamá-lo em `infra/scripts/local.sh` (após o Flyway, antes de liberar o backend) e em `infra/scripts/deploy.sh`; documentar em `infra/scripts/README.md`.
- [X] T019 Separar as credenciais em `RES/application.yaml` (`spring.flyway.user/password` = `DB_USER`/`DB_PASSWORD`; `spring.datasource.username/password` = `DB_APP_USER`/`DB_APP_PASSWORD`) e passar as variáveis em `infra/compose.yaml`, `infra/compose.prod.yaml` e `infra/compose.test.yaml`.
- [X] T020 [P] Implementar `CORE/domain/privacy/RetentionPolicy.java` (+ `PolicyValue` com valor e origem) e `CORE/domain/operations/AlertThresholds.java` (passam T006, T007).
- [X] T021 [P] Implementar `CORE/domain/operations/PriceTable.java` e `PriceEntry.java` (passa T008).
- [X] T022 [P] Implementar `CORE/domain/audit/AuditChain.java` com `MessageDigest` do JDK (passa T009).
- [X] T023 Acrescentar `RETENTION` e `ALERT_EVALUATION` a `CORE/domain/access/ActionOrigin.java` e as fábricas de ator de sistema em `CORE/domain/access/Actor.java`; atualizar a regra do `APP_T/architecture/AdapterRulesTest.java` que restringe onde o ator de sistema nasce (só `RetentionTask`, `OperationalAlertTask`, inicialização e carga).
- [X] T024 Criar as propriedades em `APP/config/ApplicationProperties.java` (`Retention`, `Alerts`, `Costs` com lista de preços, `Privacy`) e `APP/config/PrivacyOperationsConfig.java`, que monta `RetentionPolicy`, `AlertThresholds`, `PriceTable` e o contato do responsável e loga os avisos sem valor (passa T012).
- [X] T025 Pôr `correlationId` no MDC em `APP/inbound/web/RequestCorrelation.java` e na entrada da API interna do Telegram (`APP/inbound/telegram/channel/`), e incluí-lo em `RES/logback-spring.xml` (passa T013).
- [X] T026 Rodar `tests backend` com `V17MigrationIT` e `DatabaseRolesIT` verdes (passam T010, T011) e registrar em `SPEC/evidence/foundation.md`.

**Checkpoint**: banco migrado, papel `reports_app` em uso, política e preços validados no núcleo, varredura pronta.

---

## Phase 3: User Story 1 — Trilha de auditoria completa e consultável (Priority: P1) 🎯 MVP

**Goal**: toda operação da lista de [audit-events.md](./contracts/audit-events.md) gera exatamente um evento, encadeado e
sem dado pessoal; o administrador consulta e verifica a integridade na Web; o Telegram nega.

**Independent Test**: executar cada operação da lista em cada canal e conferir, pela tela A14, um evento com os campos
obrigatórios e nenhum dado pessoal; alterar uma linha como dono e ver a cadeia quebrada.

### Testes da US1 (escrever primeiro; devem falhar)

- [X] T027 [P] [US1] Teste de unidade de `QueryAuditTrailUseCase` em `CORE_T/usecase/audit/QueryAuditTrailUseCaseTest.java`: autorização primeiro (`AUDIT_TRAIL_QUERY`; professor → `RESOURCE_NOT_FOUND`); período obrigatório e ≤ 90 dias (`VALIDATION_FAILED` em `period`); filtros repassados à porta; a própria consulta grava `AUDIT_TRAIL_QUERY` com filtros tipados na mesma transação; falha da gravação → nada devolvido.
- [X] T028 [P] [US1] Teste de unidade de `VerifyAuditChainUseCase` em `CORE_T/usecase/audit/VerifyAuditChainUseCaseTest.java`: cadeia íntegra desde a âncora → `INTACT` e evento `AUDIT_TRAIL_VERIFY` com `item_count`; linha adulterada → `BROKEN` com a primeira sequência quebrada e alerta `AUDIT_CHAIN_BROKEN` aberto pelo `OperationalAlertService`; verificação em lotes.
- [X] T029 [P] [US1] Teste de unidade em `CORE_T/service/AuthorshipServiceReadsTest.java`: aprovar e cancelar relatório de aula e semestral, consultar histórico e abrir/baixar/enviar PDF **pelo professor** gravam os eventos novos com `document_version`, na mesma transação; sem gravação, a leitura não é entregue (FR-044).
- [X] T030 [P] [US1] Teste de integração `APP_T/outbound/persistence/AuthorshipChainIT.java` (Postgres real): gravações concorrentes (20 threads) produzem cadeia contínua e verificável; `previous_hash` de cada linha = `record_hash` da anterior.
- [X] T031 [P] [US1] Teste `APP_T/privacy/AuditTrailTamperIT.java`: como `reports_app`, `UPDATE`/`DELETE`/`TRUNCATE`/`ALTER` falham; como dono, alterar uma linha → `VerifyAuditChainUseCase` devolve `BROKEN` e abre `AUDIT_CHAIN_BROKEN`; `purge_authorship_record` com corte de 30 dias atrás é recusada pelo piso; com corte válido apaga em ordem, grava `audit_chain_anchor` e a verificação seguinte continua `INTACT`.
- [X] T032 [P] [US1] Teste de contrato `APP_T/inbound/web/admin/AdminAuditTrailContractIT.java` contra `SPEC/contracts/api-privacy-operations.openapi.yaml`: `GET /admin/audit-trail` (filtros, paginação, ordem do mais recente, `actorLabel`/`targetLabel` resolvidos na exibição, "Aluno removido" para eliminado, `Cache-Control: no-store, private`, 400 de período), `GET /admin/audit-trail/integrity` (`verifyNow`); filtro por aluno traz também os eventos dos relatórios dele (SC-007).
- [X] T033 [P] [US1] Teste `APP_T/privacy/AuditEventCoverageIT.java` (SC-002): lê a tabela de [audit-events.md](./contracts/audit-events.md) (parser da tabela Markdown) e, para cada operação e canal marcados, executa a operação (Web pelo `AdminWebBase`/`ContractApiBase`, Telegram pelo `TelegramChannelDriver`) e conta **exatamente um** evento com `operation`, `origin`, `target_type`, `result` e detalhe esperados; operação da tabela sem cenário reprova o teste.
- [X] T034 [P] [US1] Teste de paridade `APP_T/parity/AuditReadsParityScriptIT.java`: o mesmo histórico e o mesmo PDF lidos pela Web e pelo Telegram geram eventos iguais exceto `origin` e a operação de entrega (`REPORT_DOCUMENT_DOWNLOAD` × `REPORT_DOCUMENT_SENT`).
- [X] T035 [P] [US1] Varredura da US1 em `APP_T/privacy/AuditTrailCanaryIT.java` (usa `PersonalDataSweep`): com o aluno canário, executa login com código errado, cadastro, edição, aprovação, cancelamento, leituras e consultas da trilha nos dois canais e exige zero achados em logs, `ai_call` e `authorship_record`; o código digitado também é procurado.
- [X] T036 [P] [US1] Teste de credencial cruzada: acrescentar as rotas de trilha a `SPEC005/contracts/permission-matrix.json` (ver T040) e conferir que `APP_T/security/` gera os casos (401 sem cookie, 404 com cookie de professor); teste do Telegram em `APP_T/inbound/telegram/AdminRequestsNeutralDenialIT.java` com "auditoria", "trilha" e "/auditoria" → negativa neutra da 005 e nenhum caso de uso da feature chamado.
- [X] T037 [P] [US1] Teste de tela `FE_T/pages/admin/AuditTrailScreen.test.tsx`: filtros (período padrão 7 dias, máximo 90 com mensagem), rótulos dos eventos em português, paginação com foco gerenciado, selo de integridade, atalho "Quem acessou os relatórios deste aluno" a partir do detalhe do aluno preenchendo os filtros (≤ 3 interações).

### Implementação da US1

- [X] T038 [P] [US1] Criar `CORE/domain/audit/AuditTrailFilter.java` (período ≤ 90 dias obrigatório, valida a si mesmo) e `CORE/domain/audit/AuditEventType.java` (grupos e operações de [audit-events.md](./contracts/audit-events.md)); estender `CORE/domain/access/AuthorshipRecord.java` com `itemCount`, `filterOperation`, `filterActorId`, `filterTargetId` e os alvos novos (`CONSENT`, `CONSENT_TERM`, `ERASURE_REQUEST`, `RETENTION_RUN`, `AUDIT_TRAIL`, `ALERT`), mantendo a validação de só códigos.
- [X] T039 [P] [US1] Criar as portas `CORE/port/AuditTrailQueryPort.java` (`query`, `chainFrom(anchor, batchSize)`, `latestAnchor`) e `CORE/port/AuditTrailRetentionPort.java` (`purgeOlderThan(Instant)`), com dublês `CORE_T/doubles/InMemoryAuditTrail.java` (+ `InMemoryAuditTrailTest.java`) que implementa as duas e `AuthorshipRecorderPort`, encadeando com `AuditChain`.
- [X] T040 [US1] Acrescentar as operações de [permission-matrix-additions.md](./contracts/permission-matrix-additions.md) a `CORE/domain/access/Operation.java`, `CORE/domain/access/PermissionMatrix.java` e `SPEC005/contracts/permission-matrix.json` (com `"note": "feature 007"`), e a nota de emenda `> **Nota (feature 007)**` em `SPEC005/contracts/messages.md` apontando para `SPEC/contracts/permission-matrix-additions.md`; `PermissionMatrixDriftTest` verde.
- [X] T041 [US1] Implementar `CORE/usecase/audit/QueryAuditTrailUseCase.java` (passa T027).
- [X] T042 [US1] Implementar `CORE/usecase/audit/VerifyAuditChainUseCase.java` (passa T028; o alerta é aberto pela porta do serviço criado em T110 — até lá, usar a interface `CORE/service/OperationalAlertService.java` com o método `report`, criada aqui vazia de regra e completada na US5).
- [X] T043 [US1] Gravar os eventos novos de relatórios e leituras do professor em `CORE/service/AuthorshipService.java` e nos casos de uso `ApproveLessonReportUseCase`, `CancelLessonReportUseCase`, `ApproveSemesterReportUseCase`, `CancelSemesterReportUseCase`, `QueryStudentHistoryUseCase`, `GetReportDocumentUseCase`, `GetSemesterReportUseCase` e `EmitChannelDocumentUseCase` (`CORE/usecase/lesson/`, `semester/`, `history/`, `document/`, `channel/`), na mesma transação (passa T029); `TEACHER_SIGN_OUT` no caso de uso de saída do professor.
- [X] T044 [US1] Encadear a gravação em `APP/outbound/persistence/AuthorshipRecorderAdapter.java` (ou o adaptador existente da porta): `pg_advisory_xact_lock`, leitura do último `record_hash`, `AuditChain.next`, `INSERT` com `sequence` gerado; manter `recordIndependent` em transação própria (passa T030).
- [X] T045 [US1] Implementar `APP/outbound/persistence/AuditTrailQueryAdapter.java` (consulta paginada com filtros tipados e junção aluno→relatórios/consentimentos para `targetId`; resolução de rótulos na leitura sem gravar nome) e `APP/outbound/persistence/AuditTrailRetentionAdapter.java` (chama `purge_authorship_record`).
- [X] T046 [US1] Implementar `APP/inbound/web/admin/AdminAuditController.java` e `AdminAuditDtos.java` (rotas de T032, `no-store`, `Actor` da sessão, erros pelo `ErrorHandlerController`) (passa T032, T036).
- [X] T047 [US1] Garantir a negativa neutra no Telegram para os pedidos de trilha em `APP/inbound/telegram/` (interpretador já nega comandos administrativos; acrescentar as palavras ao conjunto de testes, sem fluxo novo) (passa T036).
- [X] T048 [P] [US1] Textos dos eventos, filtros e selo em `RES/messages/messages_pt_BR.properties` e `FE/messages/` (chaves em inglês, textos em português).
- [X] T049 [US1] Gerar os tipos do contrato novo no frontend (linha no `frontend/package.json`, como a 006) e criar `FE/services/adminAuditApi.ts`.
- [X] T050 [US1] Implementar `FE/pages/admin/AuditTrailScreen.tsx` (A14 de [portal-screens.md](./contracts/portal-screens.md)), rota `/admin/audit`, item no menu "Privacidade e operação" e o atalho no `FE/pages/admin/StudentDetailScreen.tsx` (passa T037).
- [X] T051 [P] [US1] E2E e acessibilidade em `FE_T/e2e/admin-audit.spec.ts` e `FE_T/e2e/admin-audit.a11y.spec.ts` (390 e 1440 px, dois temas, axe sem violação).
- [X] T052 [US1] Rodar `tests backend`, `tests frontend` e `tests e2e`; T031, T033, T034 e T035 verdes; registrar em `SPEC/evidence/us1-audit.md`.

**Checkpoint**: trilha completa, encadeada, protegida e consultável — MVP de auditoria entregue.

---

## Phase 4: User Story 2 — Consentimento do responsável e bloqueio (Priority: P1)

**Goal**: menor não é cadastrado sem consentimento (Web do administrador, Web do professor, Telegram); sem consentimento
válido nenhuma chamada de IA acontece; administrador gere termos, revogações e pendências; carga da implantação.

**Independent Test**: cadastrar menor sem consentimento nos dois canais (recusado), com consentimento (aceito); revogar,
enviar áudio nos dois canais (bloqueado, zero chamadas de IA), registrar de novo e repetir (permitido).

### Testes da US2 (escrever primeiro; devem falhar)

- [ ] T053 [P] [US2] Teste de unidade de `ConsentPolicy` em `CORE_T/domain/privacy/ConsentPolicyTest.java`: adulto (≥ 18 no fuso da escola, aniversário no dia) não exige; menor com consentimento válido; revogado; versão superada por termo `requiresNewConsent`; versão compatível; edição que torna menor exige `ConsentInput`; aluno que faz 18 deixa de exigir.
- [ ] T054 [P] [US2] Teste de unidade de `ConsentInput`, `GuardianConsent` e `ConsentTermVersion` em `CORE_T/domain/privacy/ConsentValueObjectsTest.java`: nome (`PersonName`), relação, data não futura, forma de coleta; `toString` sem nome de quem consentiu (`PersonalDataToStringTest` estendido).
- [ ] T055 [P] [US2] Teste de unidade em `CORE_T/usecase/registry/RegisterStudentConsentTest.java` e `UpdateStudentConsentTest.java`: `RegisterStudentUseCase`/`UpdateStudentUseCase` recusam menor sem consentimento com `VALIDATION_FAILED` em `consent.*`, aceitam com consentimento e gravam aluno, consentimento e eventos `STUDENT_REGISTER` + `CONSENT_RECORD` na mesma transação; versão do termo não vigente/compatível recusada; mesmos resultados para administrador, professor Web e professor Telegram.
- [ ] T056 [P] [US2] Testes de unidade em `CORE_T/usecase/privacy/`: `RecordConsentUseCaseTest.java` (professor só os próprios alunos; administrador qualquer; `INITIAL_LOAD`), `RevokeConsentUseCaseTest.java` (só administrador; já revogado → `CONSENT_ALREADY_REVOKED`), `PublishConsentTermUseCaseTest.java` (número sequencial, texto 1–20 000, data não futura), `ListPendingConsentsUseCaseTest.java` (motivos `MISSING`, `REVOKED`, `SUPERSEDED`; vazia em operação normal).
- [ ] T057 [P] [US2] Teste de unidade de `ConsentGuardService` em `CORE_T/service/ConsentGuardServiceTest.java` e dos casos de uso que chamam IA (`RegisterLessonByAudioUseCase`, `AnswerQuestionsUseCase`, `RequestLessonReportChangeUseCase`, `RetryLessonReportUseCase`, `GenerateSemesterReportUseCase`, `RequestSemesterReportChangeUseCase`): sem consentimento → `CONSENT_REQUIRED`, dublês de `TranscriptionPort`/`LlmPort` com **zero** chamadas, áudio temporário fechado, evento `CONSENT_BLOCKED_AI`; aprovar e cancelar continuam permitidos.
- [ ] T058 [P] [US2] Teste de unidade da segunda barreira em `CORE_T/service/JobProcessorConsentTest.java` e `SemesterProcessorConsentTest.java`: revogação entre enfileirar e processar → trabalho encerra com `CONSENT_REQUIRED` sem chamar provedor.
- [ ] T059 [P] [US2] Regra ArchUnit em `CORE_T/architecture/ArchitectureRulesTest.java`: todo caso de uso que alcança `TranscriptionPort` ou `LlmPort` (direto ou via `JobProcessorService`/`SemesterProcessorService`) depende de `ConsentGuardService`.
- [ ] T060 [P] [US2] Teste de contrato `APP_T/inbound/web/admin/AdminConsentContractIT.java`: rotas de consentimento, revogação, pendências e termos de `api-privacy-operations.openapi.yaml`; e estender `APP_T/inbound/web/admin/AdminStudentContractIT.java` e `APP_T/inbound/web/MyStudentContractIT.java` com `consent` (`ConsentInput`) no cadastro e na edição, `POST /my-students/{id}/consents`, `GET /my-students/consent-term` e o erro `CONSENT_REQUIRED` nas rotas de gravação de aula e semestral.
- [ ] T061 [P] [US2] Teste de paridade `APP_T/parity/ConsentParityScriptIT.java` (Web ≡ Telegram): cadastro de menor sem consentimento recusado; com consentimento aceito; bloqueio após revogação com o mesmo código e o mesmo evento; registro pelo botão do aviso; aluno adulto sem bloqueio (SC-005).
- [ ] T062 [P] [US2] Teste do cadastro guiado em `APP_T/inbound/telegram/TelegramConsentFlowIT.java`: passos C1–C5 de [telegram-consent.md](./contracts/telegram-consent.md), só para menor; `consent:no` descarta o rascunho; termo publicado no meio → reapresenta C1; resumo final sem o nome de quem consentiu; data futura repete a pergunta.
- [ ] T063 [P] [US2] Teste `APP_T/privacy/ConsentNoAiCallIT.java` (SC-005): com dublês que contam chamadas, percorre todas as entradas de IA nos dois canais para menor sem consentimento e exige zero chamadas e zero linhas em `ai_call`; varredura `PersonalDataSweep` com o aluno canário e o nome do responsável canário como quem consentiu (zero achados em logs, `ai_call`, trilha).
- [ ] T064 [P] [US2] Teste do comando em `APP_T/inbound/seed/ConsentLoadCommandIT.java`: CSV de [consent-load.md](./contracts/consent-load.md) com linhas válidas, inválidas e repetidas; resumo só com número da linha e código; caminho dentro de `/work` recusado; evento `CONSENT_RECORD` com `INITIAL_LOAD`; contagem final de pendentes; nada do arquivo em log.
- [ ] T065 [P] [US2] Testes de tela: `FE_T/pages/MyStudentFormScreen.test.tsx` (seção obrigatória quando menor, termo vigente, erros por campo), `FE_T/pages/MyStudentDetailScreen.test.tsx` (situação e botão), `FE_T/pages/LessonRegistration.test.tsx` (aviso `CONSENT_REQUIRED` sem envio), `FE_T/pages/admin/ConsentScreens.test.tsx` (seção no detalhe do aluno, A16b, A16c, A16d com confirmação que mostra quantos ficarão pendentes).

### Implementação da US2

- [ ] T066 [P] [US2] Criar `CORE/domain/privacy/ConsentTermVersion.java`, `GuardianConsent.java`, `ConsentInput.java`, `ConsentStatus.java`, `ConsentPolicy.java`, `ConsentRelationship.java`, `ConsentCollectionMethod.java` (passam T053, T054).
- [ ] T067 [P] [US2] Criar `CORE/port/ConsentRepository.java` e `CORE/port/ConsentTermRepository.java` com dublês `CORE_T/doubles/InMemoryConsentRepository.java` e `InMemoryConsentTermRepository.java` (+ `*Test.java`).
- [ ] T068 [US2] Acrescentar `ConsentInput` opcional aos comandos e aplicar `ConsentPolicy.requireOnRegistration` em `CORE/usecase/registry/RegisterStudentUseCase.java`, `UpdateStudentUseCase.java` e `ConfirmStudentRegistrationUseCase.java`, gravando o consentimento na mesma transação (passa T055).
- [ ] T069 [US2] Implementar `CORE/usecase/privacy/RecordConsentUseCase.java`, `RevokeConsentUseCase.java`, `PublishConsentTermUseCase.java`, `ListPendingConsentsUseCase.java`, `GetCurrentConsentTermUseCase.java` e `ListStudentConsentsUseCase.java` (passa T056).
- [ ] T070 [US2] Implementar `CORE/service/ConsentGuardService.java` e chamá-lo, depois da autorização, nos seis casos de uso de T057 e em `CORE/service/JobProcessorService.java` e `SemesterProcessorService.java` antes de cada porta de IA (passam T057, T058, T059).
- [ ] T071 [US2] Implementar `APP/outbound/persistence/ConsentRepositoryAdapter.java` e `ConsentTermRepositoryAdapter.java` (+ entidades JPA `*Entity`), com `ListPendingConsents` por consulta no banco.
- [ ] T072 [US2] Implementar `APP/inbound/web/admin/AdminConsentController.java` (+ `AdminConsentDtos.java`) e estender `AdminStudentController`, `MyStudentController` e o controlador de "Meus alunos" com `consent`, `POST /my-students/{id}/consents` e `GET /my-students/consent-term`; mapear `CONSENT_REQUIRED` no `ErrorHandlerController` (passa T060).
- [ ] T073 [US2] Emendar `SPEC005/contracts/api-administration.openapi.yaml` e `SPEC005/contracts/telegram-student-registry.md` com `> **Nota (feature 007)**` e a referência a `ConsentInput`/[telegram-consent.md](./contracts/telegram-consent.md) (contrato antes do código, regra de `specs/CLAUDE.md`).
- [ ] T074 [US2] Implementar os passos C1–C5, o botão `consent:record:{studentId}` e a mensagem de bloqueio em `APP/inbound/telegram/TelegramStudentRegistryFlow.java` e no fluxo de registro de aula, guardando o consentimento no rascunho existente (`student_registration_draft`, coluna `jsonb` nova se preciso na `V17`) (passa T061, T062).
- [ ] T075 [US2] Implementar `APP/inbound/seed/ConsentLoadCommand.java` (perfil `seed`) e o subcomando `consent-load` em `infra/scripts/local.sh` e `infra/scripts/deploy.sh`; documentar em `infra/README.md` o passo de carga e de apagar o arquivo da VPS (passa T064).
- [ ] T076 [P] [US2] Textos `telegram.consent.*`, `consent.*` e do bloqueio em `RES/messages/messages_pt_BR.properties` e `FE/messages/` (mesmo conteúdo nos dois canais, sem idade nem motivo).
- [ ] T077 [US2] Implementar no frontend: seção de consentimento em `FE/pages/MyStudentFormScreen.tsx` e `FE/pages/admin/StudentFormScreen.tsx`; situação em `FE/pages/MyStudentDetailScreen.tsx`; seção A16 em `FE/pages/admin/StudentDetailScreen.tsx`; `FE/pages/admin/PendingConsentsScreen.tsx`, `FE/pages/admin/ConsentTermsScreen.tsx`; aviso `CONSENT_REQUIRED` em `RecordingScreen`, `QuestionsScreen`, `ReviewScreen`, `SemesterScreen`; `FE/services/` com as chamadas (passa T065).
- [ ] T078 [P] [US2] E2E em `FE_T/e2e/consent.spec.ts` e `FE_T/e2e/admin-consent.a11y.spec.ts` (390 e 1440 px, dois temas).
- [ ] T079 [US2] Registrar o termo inicial na massa local (`demo-data`) e no `seed` de exemplo (`infra/seed/`, só dados fictícios); rodar as suítes; T061, T063 verdes; registrar em `SPEC/evidence/us2-consent.md`.

**Checkpoint**: nenhum menor sem consentimento, nenhuma chamada de IA sem base registrada, paridade provada.

---

## Phase 5: User Story 3 — Retenção automática (Priority: P2)

**Goal**: rotina diária no backend que apaga fontes e PDFs vencidos, anonimiza cancelados, expurga trilha e históricos
vencidos e verifica a cadeia, sem tocar o aprovado vigente, idempotente e com registro sem conteúdo.

**Independent Test**: com relógio avançado, relatórios aprovados (versões 1–3), cancelados e em aberto; rodar a rotina;
só o vencido é tratado, o aprovado vigente e seu PDF ficam íntegros, a trilha registra a execução.

### Testes da US3 (escrever primeiro; devem falhar)

- [ ] T080 [P] [US3] Teste de unidade das decisões em `CORE_T/domain/privacy/RetentionDecisionTest.java`: `LessonReport`/`SemesterReport.retentionAction(policy, now)` e `PdfDocument` — aprovado há 29/30/31 dias, cancelado há 89/90/91 dias, em aberto (rascunho, aguardando respostas, em conferência, com falha) → `KEEP`; versão aprovada vigente e seu PDF nunca removíveis; semestral aprovado nunca recebe decisão que remova `general_information` (só `ANONYMIZE` de cancelado o remove); fronteiras exatas no fuso da escola.
- [ ] T081 [P] [US3] Teste de unidade de `RunRetentionUseCase` em `CORE_T/usecase/privacy/RunRetentionUseCaseTest.java`: ator `RETENTION`; seleção em lotes; reavaliação no tratamento (relatório aprovado entre a seleção e o tratamento → nada removido, FR-011); fonte = `transcription` + `report_interaction.text` (aula) e só `report_interaction.text` (semestral aprovado: `general_information` intacto, research R-01); PDFs não aprovados removidos e conteúdo das versões mantido (FR-012); cancelado anonimizado (FR-013); arquivo removido **depois** do commit e marcado antes; execução `RUNNING` concorrente recusada; `RUNNING` antiga vira `INTERRUPTED` e a nova retoma; repetição não trata nada duas vezes (FR-015); falha → evento `FAILURE`, alerta `RETENTION_FAILED`, demais itens seguem; sem conseguir gravar o evento → para e alerta; ao final, expurgo da trilha (`AuditTrailRetentionPort`), dos alertas normalizados e dos `retention_run` vencidos, e `VerifyAuditChainUseCase`.
- [ ] T082 [P] [US3] Regra ArchUnit em `CORE_T/architecture/ArchitectureRulesTest.java`: só `RunRetentionUseCase` depende de `AuditTrailRetentionPort`; `RetentionTask` só depende do caso de uso e do `ClockPort`.
- [ ] T083 [P] [US3] Teste de integração `APP_T/outbound/persistence/RetentionIT.java` (Postgres real, `AdjustableClock`, armazenamento real em volume temporário) (SC-003): massa com aprovados (versões 1–3), cancelados, semestrais, em aberto e eventos antigos de trilha; roda a rotina; 100% dos vencidos tratados; aprovado vigente com `content` e arquivo de PDF intactos (hash `sha256` igual); semestral aprovado com `general_information` igual ao de antes, PDF oficial regenerado byte a byte igual ao guardado e resposta de `GET` do semestral (Web) e apresentação no Telegram iguais às de antes da rotina; CHECK `ck_semester_report_general_information` recusa anular o campo sem `anonymized_at`; `retention_run_item` com ids e quantidades; evento `RETENTION_RUN`; segunda execução trata zero; falha simulada de `DocumentStoragePort.delete` deixa o item marcado e a execução seguinte remove o arquivo.
- [ ] T084 [P] [US3] Teste de concorrência `APP_T/outbound/persistence/RetentionApprovalRaceIT.java`: aprovação em paralelo com a rotina (latch) → o relatório aprovado não perde fonte nem PDF da versão aprovada antes do prazo.
- [ ] T085 [P] [US3] Teste de privacidade `APP_T/privacy/RetentionCanaryIT.java`: relatórios do aluno canário com `TRANSCRIPTION_SNIPPET` e `REPORT_TEXT_SNIPPET`; após a rotina, o trecho de transcrição não existe em nenhuma coluna do relatório vencido; `PersonalDataSweep` com zero achados em logs, trilha e `retention_run_item`.
- [ ] T086 [P] [US3] Teste do adaptador `APP_T/outbound/storage/DocumentStorageDeleteIT.java`: `delete` remove, chave ausente não é erro, falha de E/S vira exceção da porta.
- [ ] T087 [P] [US3] Teste de agendamento `APP_T/outbound/persistence/RetentionTaskTest.java`: cron de `reports.retention.cron` no fuso da escola; perfil `!seed`; a tarefa só chama o caso de uso; exceção não derruba o agendador.

### Implementação da US3

- [ ] T088 [P] [US3] Criar `CORE/domain/privacy/RetentionDecision.java` e os métodos `retentionAction` nas entidades de relatório e de PDF em `CORE/domain/report/` e `CORE/domain/semester/`, com os campos `sourceRemovedAt`, `anonymizedAt`, `contentRemovedAt`; em `SemesterReport`, `removeSource` não toca `generalInformation` e só `anonymize` o anula (passa T080). Incluir o CHECK `ck_semester_report_general_information` de [data-model.md](./data-model.md) §1.2 em T017.
- [ ] T089 [P] [US3] Acrescentar `delete(String key)` a `CORE/port/DocumentStoragePort.java`, ao dublê do núcleo e a `APP/outbound/storage/` (passa T086).
- [ ] T090 [US3] Acrescentar às portas existentes (`LessonReportRepository`, `SemesterReportRepository`, `ReportVersionRepository`, `PdfDocumentRepository`, `ReportInteractionRepository`) os métodos `findRetentionCandidates`, `findForUpdate`, `removeSource`, `anonymize`, `markContentRemoved`, `findPendingFileRemovals`, com dublês em `CORE_T/doubles/`; criar `CORE/port/RetentionRunRepository.java` com dublê.
- [ ] T091 [US3] Implementar `CORE/usecase/privacy/RunRetentionUseCase.java` (passa T081, T082).
- [ ] T092 [US3] Implementar os métodos novos nos `*RepositoryAdapter` de `APP/outbound/persistence/` (SQL com `FOR UPDATE`, lotes de 200 por id) e `RetentionRunRepositoryAdapter.java`; ajustar as entidades JPA para `transcription`/`general_information`/`text` anuláveis e o domínio para `Optional`, sem quebrar a leitura atual (histórico e revisão continuam iguais quando a fonte existe).
- [ ] T093 [US3] Implementar `APP/outbound/persistence/RetentionTask.java` (`@Scheduled(cron = "${reports.retention.cron}", zone = fuso da escola)`, `@Profile("!seed")`, ator `RETENTION`, log `INFO` com o resumo da execução e o hash da cabeça da cadeia, sem dado pessoal) (passa T087).
- [ ] T094 [US3] Rodar as suítes; T083, T084, T085 verdes; registrar em `SPEC/evidence/us3-retention.md` a contagem por tipo de uma execução na massa de demonstração.

**Checkpoint**: retenção automática, idempotente, registrada e segura para o aprovado vigente.

---

## Phase 6: User Story 4 — Eliminação a pedido do titular (Priority: P2)

**Goal**: o administrador registra o pedido, vê o alcance, confirma com palavra e token de 10 min; o aluno e todos os
relatórios dele ficam sem dado pessoal recuperável; comprovante sem dado do aluno; lista de eliminados para a restauração.

**Independent Test**: aluno com relatórios de aula e semestrais em várias situações e dois professores; eliminar; buscar os
valores no banco, no armazenamento, nas telas, no bot, nos logs e na trilha: zero.

### Testes da US4 (escrever primeiro; devem falhar)

- [ ] T095 [P] [US4] Teste de unidade do domínio em `CORE_T/domain/privacy/ErasureRequestTest.java`: transições `RECEIVED → IN_PROGRESS → COMPLETED`, prévia emite token novo e invalida o anterior, validade de 10 min, palavra exatamente `ELIMINAR`, protocolo `^[A-Za-z0-9./-]{1,40}$`, `requesterName` nulo após concluir, um pedido aberto por aluno.
- [ ] T096 [P] [US4] Testes de unidade em `CORE_T/usecase/privacy/`: `RegisterErasureRequestUseCaseTest.java` (só administrador; aluno eliminado → `STUDENT_ALREADY_ERASED`; aberto → `ERASURE_ALREADY_OPEN`), `PreviewErasureUseCaseTest.java` (alcance por situação, PDFs, professores, consentimentos; token como HMAC), `ConfirmErasureUseCaseTest.java` (token vencido/usado → `CONFIRMATION_EXPIRED`; numa transação: anonimiza aluno, cancela em aberto e trabalhos, remove fonte, conteúdo de todas as versões e marca PDFs de **todos** os relatórios de **todos** os professores, inclusive aprovados; remove associações e `channel_document`; limpa `payload` de `channel_action` finalizadas; apaga o nome de quem consentiu; grava `erased_student`; eventos `ERASURE_CONFIRM`; depois do commit remove arquivos e conclui com `ERASURE_COMPLETED` e `item_count`; falha de arquivo → `IN_PROGRESS` + `STORAGE_FAILED`; repetir conclui), `GetErasureReceiptUseCaseTest.java` (só `COMPLETED`, sem dado do aluno).
- [ ] T097 [P] [US4] Teste de unidade em `CORE_T/usecase/registry/ErasedStudentVisibilityTest.java`: `findVisible`, buscas, listas, histórico e "Meus alunos" não retornam aluno eliminado; CPF antigo aceito em novo cadastro (FR-024).
- [ ] T098 [P] [US4] Teste de unidade em `CORE_T/service/JobProcessorErasureTest.java`: resultado de IA que chega após a eliminação é descartado (aluno `erased_at` preenchido / trabalho cancelado / `lock_version`), nada é gravado.
- [ ] T099 [P] [US4] Teste de contrato `APP_T/inbound/web/admin/AdminErasureContractIT.java`: todas as rotas de eliminação de `api-privacy-operations.openapi.yaml` (201, 400, 404, 409 com os códigos), `no-store`, comprovante.
- [ ] T100 [P] [US4] Teste ponta a ponta `APP_T/privacy/ErasureIT.java` (SC-004): aluno canário com relatórios aprovados, cancelados, um em aberto e um semestral, associado a dois professores, com PDFs no armazenamento e mensagens já entregues na outbox; executa a eliminação; varre **todas** as colunas de texto e `jsonb` do esquema (`information_schema`) e o diretório de armazenamento atrás dos valores canário (nome, CPF, nascimento, responsável, transcrição, texto de relatório) → zero; respostas da API do administrador e dos dois professores e o bot (busca por nome e CPF) → nada; `PersonalDataSweep` em logs, trilha e `ai_call` → zero; novo cadastro com o mesmo CPF aceito; trilha antiga mostra "Aluno removido · #XXXXXX".
- [ ] T101 [P] [US4] Teste de falha no meio `APP_T/privacy/ErasureFailureIT.java`: erro forçado no meio da transação não deixa o aluno meio anonimizado; erro na remoção de arquivo deixa `IN_PROGRESS` com alerta; a segunda confirmação (nova prévia) conclui.
- [ ] T102 [P] [US4] Teste do Telegram em `APP_T/inbound/telegram/AdminRequestsNeutralDenialIT.java` (estender T036): "apagar aluno", "eliminar", "excluir dados" → negativa neutra; o professor segue só com inativar.
- [ ] T103 [P] [US4] Testes de tela `FE_T/pages/admin/ErasureScreen.test.tsx` (três passos, contagem regressiva, palavra de confirmação, estado `IN_PROGRESS` com repetir) e `FE_T/pages/admin/ErasureReceiptScreen.test.tsx` (sem nome, CPF ou dado do aluno; CSS de impressão presente).

### Implementação da US4

- [ ] T104 [P] [US4] Criar `CORE/domain/privacy/ErasureRequest.java`, `ErasureStatus.java`, `ErasureScope.java`, `ErasureConfirmation.java`, `ErasedStudent.java` (passa T095).
- [ ] T105 [P] [US4] Criar `CORE/port/ErasureRequestRepository.java`; acrescentar `erase`, `findAllByStudent` e remoções por aluno/relatório a `StudentRepository`, `AssignmentRepository`, `ChannelDocumentRepository`, `ChannelActionRepository`, `ConsentRepository` e aos repositórios de relatório; dublês em `CORE_T/doubles/`.
- [ ] T106 [US4] Implementar `CORE/usecase/privacy/RegisterErasureRequestUseCase.java`, `PreviewErasureUseCase.java`, `ConfirmErasureUseCase.java` (reaproveitando as remoções da US3), `GetErasureReceiptUseCase.java`, `ListErasureRequestsUseCase.java`; filtrar eliminados no `StudentAccessService` e nas consultas; descarte tardio no `JobProcessorService`/`SemesterProcessorService` (passam T096, T097, T098).
- [ ] T107 [US4] Implementar os adaptadores em `APP/outbound/persistence/` (`ErasureRequestRepositoryAdapter.java`, `erase` no adaptador de aluno com colunas a `NULL`, consultas com `erased_at IS NULL`, limpeza de `payload` da outbox) e permitir que `RunRetentionUseCase` conclua remoções de arquivo pendentes de eliminação (`ERASURE_CONFIRM` para o ator `RETENTION`).
- [ ] T108 [US4] Implementar `APP/inbound/web/admin/AdminErasureController.java` (+ `AdminErasureDtos.java`), códigos `ERASURE_ALREADY_OPEN`, `STUDENT_ALREADY_ERASED`, `ERASURE_ALREADY_COMPLETED`, `CONFIRMATION_EXPIRED` no catálogo e no `ErrorHandlerController`; rótulo "Aluno removido · #XXXXXX" no catálogo, aplicado na exibição pela Web e pelo bot (passa T099, T100, T101, T102).
- [ ] T109 [US4] Implementar `FE/pages/admin/ErasureScreen.tsx`, `ErasureReceiptScreen.tsx` (impressão) e `ErasureRequestsScreen.tsx`; botão "Eliminar dados" separado de "Inativar" em `FE/pages/admin/StudentDetailScreen.tsx`; E2E em `FE_T/e2e/admin-erasure.spec.ts` e `admin-erasure.a11y.spec.ts`; rodar as suítes e registrar em `SPEC/evidence/us4-erasure.md` (passa T103).

**Checkpoint**: eliminação completa, atômica no banco, retomável nos arquivos e comprovada.

---

## Phase 7: User Story 5 — Observabilidade, custos e alertas (Priority: P3)

**Goal**: painel mensal de tempo e custo por áudio, aula, aluno, semestral, canal e provedor sobre a `ai_call`; alertas
num painel da Web, sem ferramenta nova e sem dado pessoal; logs estruturados provados limpos.

**Independent Test**: processar aulas e semestrais com custo conhecido e conferir os totais (< 1% da soma); simular
falha de provedor, fila e armazenamento e ver um alerta por condição em ≤ 5 min, que normaliza.

### Testes da US5 (escrever primeiro; devem falhar)

- [ ] T110 [P] [US5] Teste de unidade de `OperationalAlertService` e `EvaluateOperationalAlertsUseCase` em `CORE_T/service/OperationalAlertServiceTest.java` e `CORE_T/usecase/operations/EvaluateOperationalAlertsUseCaseTest.java`: 3 falhas seguidas e > 20% em 15 min abrem `AI_PROVIDER_FAILING` por provedor; ocorrência nova atualiza o mesmo (`occurrences`, `lastSeenAt`); normaliza com sucesso e taxa abaixo; fila com item > 5 min ou > 50 itens abre `CHANNEL_QUEUE_BACKLOG`; `STORAGE_FAILED` normaliza após 15 min sem ocorrência ou com sucesso; `RETENTION_FAILED` na próxima execução boa; `AUDIT_CHAIN_BROKEN` só por `OPERATIONS_ALERT_ACK`; eventos `ALERT_OPENED`/`ALERT_NORMALIZED`; nenhum campo aceita id de aluno ou texto livre.
- [ ] T111 [P] [US5] Teste de unidade de `GetMonthlyCostReportUseCase` em `CORE_T/usecase/operations/GetMonthlyCostReportUseCaseTest.java`: mês no fuso da escola (virada às 00:00 de São Paulo), agregados por áudio (`TRANSCRIPTION`), aula, semestral, aluno, canal e provedor, médias com contagem zero, custo fixo de infraestrutura pela vigência, falhas contadas.
- [ ] T112 [P] [US5] Teste de unidade em `CORE_T/service/JobProcessorCostTest.java`: cada `AiCall` gravada leva o `channel` do `ProcessingJob` e o custo calculado com o preço vigente na data da chamada; mudar a tabela não altera chamadas já gravadas.
- [ ] T113 [P] [US5] Teste `APP_T/outbound/persistence/CostReportIT.java` (SC-008): massa de 10 aulas e 2 semestrais em dois canais e dois provedores com custos conhecidos; total do painel difere < 1% de `SUM(estimated_cost_usd)` + infraestrutura; agregados por unidade conferem.
- [ ] T114 [P] [US5] Teste `APP_T/outbound/persistence/OperationalAlertIT.java` (SC-006, `AdjustableClock`): cada condição simulada (provedor simulado falhando, outbox com itens pendentes antigos, armazenamento falhando, retenção falhando) aparece como **um** alerta ativo em até 5 min de relógio e passa a normalizado quando some; índice único impede dois ativos iguais sob concorrência.
- [ ] T115 [P] [US5] Teste de contrato `APP_T/inbound/web/admin/AdminOperationsContractIT.java`: `GET /admin/operations/costs` (mês inválido → 400), `GET /admin/operations/alerts` (com `effectivePolicy`), `GET /admin/operations/alerts/summary` (não grava evento), `POST .../acknowledgement` (409 para tipo que normaliza sozinho).
- [ ] T116 [P] [US5] Teste de privacidade `APP_T/privacy/MetricsAndAlertsCanaryIT.java`: com o aluno canário, processa aulas e semestral pelos dois canais, provoca falhas de provedor e de armazenamento; `PersonalDataSweep` em logs (todas as linhas JSON, inclusive pilhas), `ai_call`, `operational_alert` e na resposta dos painéis (exceto o nome do aluno resolvido na tela de custo do administrador, que é permitido e marcado como tal) → zero.
- [ ] T117 [P] [US5] Testes de tela `FE_T/pages/admin/CostsScreen.test.tsx` (seletor de mês, cartões, tabelas ordenáveis, USD), `FE_T/pages/admin/AlertsScreen.test.tsx` (ativos no topo, histórico, prazos efetivos, apuração só de quebra da trilha) e `FE_T/components/AlertIndicator.test.tsx` (texto + ícone, `aria-live`, consulta a cada 60 s só com a aba visível).

### Implementação da US5

- [ ] T118 [P] [US5] Criar `CORE/domain/operations/OperationalAlert.java`, `AlertType.java`, `MonthlyCostReport.java`; portas `CORE/port/OperationalAlertRepository.java`, `OperationalSignalQueryPort.java`, `CostReportQueryPort.java` com dublês em `CORE_T/doubles/`.
- [ ] T119 [US5] Completar `CORE/service/OperationalAlertService.java` (aberto em T042) e implementar `CORE/usecase/operations/EvaluateOperationalAlertsUseCase.java`, `ListOperationalAlertsUseCase.java`, `AcknowledgeOperationalAlertUseCase.java`, `GetMonthlyCostReportUseCase.java`; reportar `STORAGE_FAILED` onde a exceção de `DocumentStoragePort` é tratada no núcleo e `RETENTION_FAILED` em `RunRetentionUseCase` (passam T110, T111).
- [ ] T120 [US5] Acrescentar `channel` a `CORE/domain/job/AiCall.java` e preenchê-lo em `JobProcessorService`/`SemesterProcessorService`; usar `PriceTable` nos adaptadores de `APP/outbound/ai/` para o preço vigente na data (passa T112).
- [ ] T121 [US5] Implementar `APP/outbound/persistence/OperationalAlertRepositoryAdapter.java`, `OperationalSignalQueryAdapter.java` (consultas sobre `ai_call` e `channel_action`), `CostReportQueryAdapter.java` (agregação SQL por mês no fuso da escola) e `APP/outbound/persistence/OperationalAlertTask.java` (`fixedDelay` de `reports.alerts.evaluation-seconds`, ator `ALERT_EVALUATION`, perfil `!seed`) (passam T113, T114).
- [ ] T122 [US5] Implementar `APP/inbound/web/admin/AdminOperationsController.java` (+ `AdminOperationsDtos.java`) (passa T115, T116).
- [ ] T123 [US5] Implementar `FE/pages/admin/CostsScreen.tsx`, `FE/pages/admin/AlertsScreen.tsx` e `FE/components/AlertIndicator.tsx` no cabeçalho administrativo; rotas e menu; E2E em `FE_T/e2e/admin-operations.spec.ts` e `admin-operations.a11y.spec.ts` (passa T117).
- [ ] T124 [US5] Rodar as suítes; registrar em `SPEC/evidence/us5-operations.md` o tempo medido até o alerta aparecer e a conferência de custo; confirmar que nenhum contêiner nem porta publicada foi acrescentado (`ComposeGuardTest`).

**Checkpoint**: custo e tempo por unidade, alertas no painel, observabilidade sem ferramenta nova.

---

## Phase 8: User Story 6 — Inventário de tratamento (Priority: P3)

**Goal**: documento versionado do que vai a cada provedor, por que, onde e por quanto tempo, com item de revisão.

**Independent Test**: comparar o inventário com o que os adaptadores enviam (inspeção + testes de contrato de IA); nada
enviado sem estar listado.

### Testes da US6 (escrever primeiro; devem falhar)

- [ ] T125 [P] [US6] Teste `APP_T/privacy/ProcessingInventoryConsistencyTest.java`: lê `privacy/processing-inventory.md` e exige uma seção por porta de provedor configurada (`TranscriptionPort`, `LlmPort`, `ChannelOrchestratorPort`/mensageria, `DocumentStoragePort`), os prazos padrão iguais aos de `RetentionPolicy` e a declaração de não envio de nome, CPF, data de nascimento e dados do responsável; cruza com `StudentDataNotSentToAiIT` (os campos enviados ao LLM listados no inventário).
- [ ] T126 [P] [US6] Teste do guardião de revisão em `APP_T/architecture/InventoryReviewGuardTest.java` (ou verificação equivalente no `ci.yml`): diff que toca `APP/outbound/ai/**` ou `RES/prompts/**` sem tocar `privacy/processing-inventory.md` é sinalizado (modo aviso no CI, com mensagem em português).

### Implementação da US6

- [ ] T127 [US6] Criar `privacy/CLAUDE.md` (o que é a pasta, regra do FR-071) e `privacy/processing-inventory.md` (FR-070: por provedor, dados enviados, finalidade, base legal proposta a validar, local e região, prazo no provedor, uso para treinamento, referência aos termos; pelo sistema, dados guardados, prazos de FR-002 e contato do responsável pela LGPD; aviso de que não é parecer jurídico) (passa T125).
- [ ] T128 [US6] Acrescentar a linha de `privacy/` na tabela do `CLAUDE.md` da raiz; criar `.github/pull_request_template.md` com o item "mudou o que vai a um provedor? atualizei `privacy/processing-inventory.md`"; atualizar `.github/CLAUDE.md`.
- [ ] T129 [US6] Acrescentar ao agente `.claude/agents/privacy-auditor.md` a checagem do inventário e ajustar o roteamento em `.claude/skills/pre-pr-review/SKILL.md` se preciso; implementar o aviso de T126 (passa T126).

**Checkpoint**: inventário publicado e protegido por revisão.

---

## Phase 9: Polish & Cross-Cutting Concerns

- [ ] T130 Varredura completa `APP_T/privacy/PrivacyCanaryIT.java` (SC-001): com o aluno canário e o responsável canário, percorre **todos** os fluxos das seis histórias nos dois canais (cadastro com consentimento, aula e semestral com transcrição e texto de relatório canário, revisão, aprovação, leituras, revogação e bloqueio, retenção com relógio avançado, eliminação, alertas forçados, consulta de trilha e de custo) e roda `PersonalDataSweep` em **logs, métricas (`ai_call`), alertas e trilha** procurando CPF, nome do aluno, transcrição e texto de relatório (e os demais valores canário); exige zero achados e lista todos de uma vez se houver.
- [ ] T131 [P] Atualizar `backend/core/CLAUDE.md` (pacotes `privacy`, `audit`, `operations`; regras do guarda de consentimento e da porta de guarda da trilha), `backend/application/CLAUDE.md` (`V17`, `RetentionTask`, `OperationalAlertTask`, `consent-load`, papel `reports_app`), `infra/CLAUDE.md` (papel de banco, `db-roles.sh`, rotação de logs) e `frontend/CLAUDE.md` (telas A14–A18).
- [ ] T132 [P] Atualizar `infra/README.md`: segunda credencial do banco, carga de consentimentos, prazos e alertas, e a nota de que o backup automático segue manual (seção 5) até a próxima fase, devendo reaplicar `erased_student` após restaurar.
- [ ] T133 Rodar o roteiro manual de [quickstart.md](./quickstart.md) completo no ambiente local e registrar o resultado em `SPEC/evidence/quickstart-run.md` (sem dado pessoal).
- [ ] T134 Rodar `sh infra/scripts/local.sh tests backend`, `tests frontend` e `tests e2e`; conferir cobertura do núcleo ≥ 90% (linha e branch) e guardiões de idioma verdes; comparar com `SPEC/evidence/baseline.md`.
- [ ] T135 Rodar a skill `pre-pr-review` (backend-reviewer, frontend-reviewer, infra-security-auditor, contract-drift-checker, privacy-auditor, constitution-auditor) e corrigir os achados.
- [ ] T136 Commitar, abrir o PR para a `main` com a skill `open-pr` (sem merge e sem merge automático; descrição em português citando a segunda credencial do banco e os passos de implantação) e rodar `code-review-graph build`.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Fase 1)**: sem dependência; T003 antes de qualquer código.
- **Fundação (Fase 2)**: depende do Setup; **bloqueia todas as histórias**.
- **US1 Trilha (Fase 3)**: depende da Fundação. As demais histórias gravam na trilha encadeada e usam a matriz estendida (T040).
- **US2 Consentimento (Fase 4)**: depende de US1 (eventos e matriz). Independente de US3–US6.
- **US3 Retenção (Fase 5)**: depende de US1 (evento, porta de guarda, verificação da cadeia).
- **US4 Eliminação (Fase 6)**: depende de US1 e US3 (reaproveita as remoções e a conclusão de arquivos pendentes); usa o consentimento da US2 só para apagar o nome de quem consentiu (se US2 ainda não existir, a etapa é vazia).
- **US5 Custos e alertas (Fase 7)**: depende da Fundação; o serviço de alertas completa o esqueleto de T042. Pode correr em paralelo com US2–US4.
- **US6 Inventário (Fase 8)**: só depende de T003; pode correr a qualquer momento (T125 cruza com os prazos da Fundação).
- **Fecho (Fase 9)**: depois de todas as histórias desejadas; T130 exige US1–US5.

### Dentro de cada história

- Testes primeiro, falhando; depois domínio → portas e dublês → casos de uso → adaptadores → controladores → telas → evidência.

### Parallel Opportunities

- Fase 1: T004 e T005.
- Fase 2: T006–T016 juntos (arquivos distintos); depois T020, T021, T022 juntos.
- US1: T027–T037 juntos; T038 e T039 juntos; T048 e T051 em paralelo com o backend.
- US2: T053–T065 juntos; T066 e T067 juntos.
- US3: T080–T087 juntos; T088 e T089 juntos.
- US4: T095–T103 juntos; T104 e T105 juntos.
- US5: T110–T117 juntos; pode correr inteira em paralelo com US2/US3/US4 (equipe diferente).
- US6: inteira em paralelo com qualquer história.

### Exemplo paralelo — US1

```text
Juntos: T027 QueryAuditTrailUseCaseTest, T028 VerifyAuditChainUseCaseTest, T029 AuthorshipServiceReadsTest,
        T030 AuthorshipChainIT, T031 AuditTrailTamperIT, T032 AdminAuditTrailContractIT,
        T033 AuditEventCoverageIT, T034 AuditReadsParityScriptIT, T035 AuditTrailCanaryIT, T037 AuditTrailScreen.test
Depois, juntos: T038 AuditTrailFilter/AuditEventType, T039 portas e dublê da trilha
```

### Exemplo paralelo — US2

```text
Juntos: T053 ConsentPolicyTest, T054 ConsentValueObjectsTest, T055 Register/UpdateStudentConsentTest,
        T056 testes dos casos de uso de consentimento, T057 ConsentGuardServiceTest, T058 processadores,
        T059 regra ArchUnit, T060 contratos, T061 paridade, T062 fluxo do Telegram, T063 ConsentNoAiCallIT,
        T064 ConsentLoadCommandIT, T065 telas
```

### Exemplo paralelo — US3, US4, US5

```text
US3 juntos: T080 RetentionDecisionTest, T081 RunRetentionUseCaseTest, T082 ArchUnit, T083 RetentionIT,
            T084 RetentionApprovalRaceIT, T085 RetentionCanaryIT, T086 DocumentStorageDeleteIT, T087 RetentionTaskTest
US4 juntos: T095–T103 (domínio, casos de uso, visibilidade, processador, contrato, ErasureIT, falha, Telegram, telas)
US5 juntos: T110–T117 (alertas, custo, processador, CostReportIT, OperationalAlertIT, contrato, canário, telas)
```

---

## Implementation Strategy

### MVP (US1 + US2, ambas P1)

1. Fases 1 e 2.
2. Fase 3 (trilha) → validar com A14 e `AuditEventCoverageIT`.
3. Fase 4 (consentimento) → validar com `ConsentParityScriptIT` e `ConsentNoAiCallIT`.
4. **Parar e validar**: com trilha e consentimento, a escola já pode operar com dados reais de menores com base
   registrada e rastro de quem acessou (a retenção e a eliminação são P2).

### Entrega incremental

1. Fundação → US1 → US2 (MVP, PR revisável).
2. US3 (retenção) → US4 (eliminação): a política deixa de ser só documento.
3. US5 (custos e alertas) e US6 (inventário), em paralelo.
4. Fecho com T130 (varredura completa) e PR.

Cada fase termina com evidência em `SPEC/evidence/` e as suítes verdes; uma fase pode virar PR próprio se o revisor
preferir fatias menores (mesmo encaminhamento da 005).

---

## Notes

- [P] = arquivos distintos, sem dependência aberta.
- Teste que passa antes da implementação está errado: refaça-o.
- Nenhum valor real de aluno em teste, massa, evidência ou log: só os valores canário fictícios.
- Commits em português, por tarefa ou grupo lógico; nunca na `main`; PR sem merge pelo autor (Princípio VIII).
