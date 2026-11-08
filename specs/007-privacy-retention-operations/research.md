# Research: Privacidade, retenção e operação (LGPD)

**Feature**: `007-privacy-retention-operations` | **Data**: 2026-11-03 | **Plano**: [plan.md](./plan.md)

Cada decisão segue o formato **Decisão / Justificativa / Alternativas consideradas**. Não restou nenhum "NEEDS
CLARIFICATION" no Technical Context: o clarify de 2026-11-03 fechou D6, D7, D8, D9, backup (fora) e destino dos alertas;
D11 (pessoa responsável pela LGPD) só afeta valores de configuração.

## Levantamento do que já existe

| Pedido da spec | O que existe hoje | Lacuna que a 007 fecha |
|---|---|---|
| Trilha (FR-040 a FR-046) | `authorship_record` (V10, V16): só acréscimo por gatilho, sem FK, colunas que só aceitam códigos; `AuthorshipRecorderPort` (`record` na mesma transação, `recordIndependent` para falha e negativa); `AuthorshipService` no núcleo | leituras do **professor** não são registradas; faltam operações de consentimento, eliminação, retenção e consulta da trilha; nada detecta alteração feita por quem tem privilégio de dono; não há prazo de guarda; não há tela |
| Medição de IA (FR-051, spec 001 FR-056) | `ai_call` por chamada: `task`, `provider_model`, `audio_seconds`, `input_tokens`, `output_tokens`, `duration_ms`, `estimated_cost_usd`, `success`, `error_code`, `report_type`, `report_id`, `job_id`; `processing_time_ms` em `lesson_report` e `semester_report`; preço único por provedor em `ApplicationProperties` (`priceUsdPerHour`, `priceUsdPerMillionInput`) | **canal** não está na chamada (só no `processing_job.request_channel`); preço **sem vigência**; sem custo fixo de infraestrutura; sem consulta agregada nem tela |
| Logs (FR-050) | JSON no stdout (`LogstashEncoder`), MDC `reportId`, `jobId`, `channel`, `step`; mascaramento (`SensitiveDataMaskingConverter`) | falta o **identificador de correlação** no MDC; falta o teste canário que prova "zero dado de aluno" (SC-001); o Docker não rotaciona os logs |
| Tarefas agendadas | `@Scheduled` em `*Task` (`AccessSweepTask`, `StudentDraftSweepTask`, `ChannelActionsCleanupTask` às 03:23 no fuso da escola), perfil `!seed` | nenhuma de retenção, verificação da trilha ou alertas |
| Armazenamento de PDF | `DocumentStoragePort` (`save`, `open`, `exists`), `pdf_document` por versão | não há **remoção** |
| Outbox do Telegram | `channel_action` com `payload-retention-hours: 24` e `record-retention-days: 7` | já é a "fila de entrega" do alerta (FR-055); o texto das mensagens some em 24 h |
| Consentimento | nenhum | tudo |
| Eliminação | inativar aluno (feature 005) | tudo |

## R-01 — Retenção como tarefa agendada do backend, regra no núcleo

**Decisão**: um caso de uso `RunRetentionUseCase` (núcleo, `usecase/privacy`) chamado por `RetentionTask`
(`application/outbound/persistence`, `@Scheduled(cron = "${reports.retention.cron:0 41 3 * * *}", zone = fuso da escola)`,
`@Profile("!seed")`). A tarefa só chama o caso de uso com o ator de sistema `RETENTION` (nova `ActionOrigin`); a regra
inteira — o que venceu, o que é protegido, a ordem e o registro — mora no núcleo:

- `RetentionPolicy` (domínio) com os prazos efetivos (FR-001/FR-002) e `RetentionDecision` por item, calculada por
  métodos de domínio (`LessonReport.retentionAction(policy, now)`, idem semestral e `PdfDocument`).
- **Seleção** por métodos novos nas portas **existentes** (`LessonReportRepository.findRetentionCandidates`,
  `SemesterReportRepository...`, `PdfDocumentRepository.findRemovableVersions`, `ReportInteractionRepository`), em lotes
  de 200, ordenados por id.
- **Reavaliação no tratamento** (FR-011, cenário 4): cada item é tratado na própria transação (`TransactionPort`),
  relido com `findForUpdate` (bloqueio de linha) e a decisão é recalculada; se o relatório mudou para aprovado
  vigente, nada é feito. O `lock_version` existente cobre a corrida com a aprovação.
- **Idempotência e retomada** (FR-015): o tratamento deixa marcas (`source_removed_at`, `content_removed_at`,
  `anonymized_at`) e a seleção só pega o que não tem marca, então repetir não trata duas vezes. Cada item é atômico.
- **PDF fora da transação do banco**: a linha do `pdf_document` recebe `content_removed_at` na transação; o arquivo é
  removido depois do commit pela porta `DocumentStoragePort.delete(key)` (**único método novo** na porta,
  idempotente: chave ausente não é erro). A execução seguinte remove de novo os arquivos marcados que ainda existirem
  (`exists`), então falha de armazenamento não deixa arquivo órfão para sempre e gera alerta (R-07).
- **Uma execução por vez**: a linha `retention_run` com situação `RUNNING` tem índice único parcial; uma execução
  `RUNNING` mais velha que 6 h é marcada `INTERRUPTED` e a nova retoma (a seleção é por estado, não por cursor).
- **Registro**: `retention_run` + `retention_run_item` (tipo e identificador, sem conteúdo) e um evento
  `RETENTION_RUN` na trilha com o id da execução como alvo (FR-015). Falha → evento com resultado `FAILURE`, alerta
  `RETENTION_FAILED`, e o sistema segue (FR-016).
- O mesmo caso de uso, ao final, remove eventos da trilha mais velhos que a guarda (R-02) e verifica a cadeia (R-03).

**O que é "transcrição"** (interpretação registrada): a fonte textual que o professor forneceu: `lesson_report.transcription`,
`report_interaction.text` (respostas e pedidos de alteração, transcritos ou digitados). As **versões**
(`report_version.content`) são o histórico do Princípio III e só saem no cancelamento anonimizado ou na eliminação
(FR-012, FR-013).

**`semester_report.general_information` não é fonte** (correção do `/speckit-analyze`, achado C1): o campo compõe o
semestral oficial — é lido pelo gerador de PDF (`OpenHtmlToPdfGenerationAdapter`) e pela exibição na Web
(`SemesterReportController`) e no Telegram (`TelegramSemesterReportPresentation`). Apagá-lo de um semestral aprovado
alteraria o registro oficial (FR-011, Princípio III). Por isso a retenção **nunca** o toca em semestral aprovado; ele só
sai na anonimização do semestral **cancelado** (FR-013) e na eliminação do aluno (FR-022). No semestral aprovado, a
retenção trata apenas `report_interaction.text` e os PDFs das versões não aprovadas.

**Justificativa**: o projeto já usa `@Scheduled` em `*Task` finas (`AccessSweepTask`); manter o padrão evita agendador
externo (cron do host, n8n) e mantém a regra no núcleo (Princípio I/II). A transação por item torna a rotina
retomável sem cursor persistido.

**Alternativas consideradas**: cron do host chamando um comando (`seed` profile) — exige configurar a VPS e quebra a
regra "só Docker e Compose"; workflow agendado no n8n — proibido (Princípio II, n8n só transporta); `DELETE` em massa
por SQL — não reavalia a situação por item nem protege o aprovado vigente com a regra do domínio; ShedLock ou Quartz —
dependência nova para um único backend; o índice único resolve.

## R-02 — Trilha: a `authorship_record` existente vira a trilha única

**Decisão**: não criar tabela paralela (spec, Assumptions D9). A `authorship_record` continua sendo o registro; a 007
acrescenta (migração aditiva):

- operações novas (lista fechada em [contracts/audit-events.md](./contracts/audit-events.md)), inclusive as leituras do
  **professor** nos dois canais (histórico, abrir/baixar PDF, documento enviado no Telegram), gravadas **na mesma
  transação** da leitura, como a 006 já faz para o administrador (FR-044);
- tipos de alvo novos: `CONSENT`, `CONSENT_TERM`, `ERASURE_REQUEST`, `RETENTION_RUN`, `AUDIT_TRAIL`, `ALERT`;
- origem nova `RETENTION` (ator de sistema) e colunas tipadas `report_version` (já existe `document_version`, reaproveitada)
  e `item_count` (só para `RETENTION_RUN`, `ERASURE_COMPLETED`);
- `sequence` (`bigint`, gerado), `previous_hash` e `record_hash` (R-03).

A consulta (FR-045) é `QueryAuditTrailUseCase` sobre a porta nova `AuditTrailQueryPort` (só leitura, filtros
obrigatórios de período ≤ 90 dias, paginação por deslocamento como na 005/006, mais recente primeiro). Os nomes são
resolvidos **na exibição**, pelo adaptador de leitura, juntando os ids às tabelas atuais (aluno eliminado →
"Aluno removido"); a trilha nunca guarda nome. A própria consulta grava `AUDIT_TRAIL_QUERY` com os filtros em colunas
tipadas (reaproveita `filter_period_start/end`, `page_number` da V16 e acrescenta `filter_operation`,
`filter_actor_id`).

**Guarda de 5 anos** (FR-043): a remoção dos eventos vencidos só existe numa função do banco
`purge_authorship_record(cutoff)` (`SECURITY DEFINER`, dona = papel de migração), que (a) recusa `cutoff` mais recente
que **1 ano** atrás (piso fixo contra configuração errada), (b) apaga em ordem de `sequence`, (c) grava a âncora da
cadeia (`audit_chain_anchor`: último `sequence` e `record_hash` removidos) e (d) devolve a quantidade. O núcleo a
alcança pela porta `AuditTrailRetentionPort.purgeOlderThan(cutoff)`, usada **só** por `RunRetentionUseCase` (ArchUnit).
A remoção vira evento `AUDIT_TRAIL_PURGED`.

**Justificativa**: a 005 e a 006 já gravam ali com as garantias certas (CHECK de colunas, gatilhos); unificar evita
dois lugares para perguntar "quem viu" (SC-007).

**Alternativas**: tabela `audit_event` nova com cópia dos registros antigos — duplica dado e quebra a regra "só
acréscimo" na migração; event store externo (EventStoreDB, Kafka) — ferramenta nova sem exigência da spec
(Princípio VII); log de auditoria só em arquivo — não é consultável pela tela nem filtrável.

## R-03 — Proteção contra alteração: três camadas, nenhuma ferramenta nova

**Decisão**:

1. **Gatilhos** (já existentes, V10): `UPDATE`, `DELETE` e `TRUNCATE` recusados. O gatilho de `DELETE` passa a deixar
   passar só quando a variável de sessão `reports.audit_purge = 'on'`, que **só** a função de guarda define
   (`set_config(..., true)`, local à transação).
2. **Separação de privilégios no banco**: hoje o backend usa o mesmo usuário dono das tabelas, que poderia desligar o
   gatilho. A 007 separa:
   - `reports` (dono, já existe): só o **Flyway** usa (`spring.flyway.user/password`);
   - `reports_app` (novo, sem posse): o pool da aplicação. Na `authorship_record`, `audit_chain_anchor` e
     `erased_student`: só `SELECT, INSERT`; nas demais tabelas: `SELECT, INSERT, UPDATE, DELETE`; `EXECUTE` na
     função de guarda. Sem `TRUNCATE`, `TRIGGER`, `ALTER` nem `CREATE`.
   - O papel nasce numa migração (`CREATE ROLE reports_app NOLOGIN` se não existir, `GRANT`s e
     `ALTER DEFAULT PRIVILEGES`); a senha e o `LOGIN` são aplicados por um passo idempotente
     `infra/scripts/lib/db-roles.sh` chamado por `local.sh` e `deploy.sh`, com a senha vinda de
     `DB_APP_PASSWORD` (ambiente, nunca no repositório nem na imagem).
3. **Cadeia de resumos (detecção, FR-043)**: cada evento guarda `previous_hash` e
   `record_hash = SHA-256(previous_hash || forma canônica do evento)`. A forma canônica e o cálculo ficam no núcleo
   (`AuditChain`, JDK `MessageDigest`, sem dependência); o adaptador obtém o último resumo sob
   `pg_advisory_xact_lock` (gravações em série, volume baixo: dezenas por minuto no pico). `VerifyAuditChainUseCase`
   recalcula a cadeia desde a âncora a cada execução da retenção (e sob demanda na tela); quebra abre o alerta
   `AUDIT_CHAIN_BROKEN` e grava evento. O resumo da cabeça vai ao log diário (`INFO`, sem dado pessoal), que sai do
   banco: alterar o banco e recalcular a cadeia não apaga o que já foi logado.

**Justificativa**: (1) barra erro de programa; (2) barra injeção ou bug que tente `ALTER TABLE ... DISABLE TRIGGER`;
(3) torna detectável o que (1) e (2) não impedem (alguém com a senha de dono). Tudo dentro do PostgreSQL e do backend,
em contêiner, sem nada exposto.

**Risco residual aceito**: quem tem acesso ao servidor tem as duas senhas; a cadeia e o log diário tornam a alteração
detectável, não impossível. Cópia da cabeça da cadeia para fora da VPS fica com o backup (próxima fase).

**Alternativas**: tabela particionada com `REVOKE` apenas (sem cadeia) — não detecta; extensão `pgaudit` — registra
comandos SQL, não eventos de negócio, e exige imagem do Postgres customizada; armazenamento WORM externo (S3 Object
Lock) — recurso de provedor (restrição de VPS genérica) e custo; assinatura com chave por evento — exige gestão de
chave que a 007 não pede.

## R-04 — Consentimento no cadastro do aluno

**Decisão**:

- Domínio novo em `domain/privacy`: `ConsentTermVersion` (número, data, texto, `requiresNewConsent`, `current`) e
  `GuardianConsent` (aluno, quem consentiu, relação, data, versão do termo, forma de coleta, quem registrou, situação,
  revogação). `ConsentPolicy.isValid(student, consents, terms, today)` decide: aluno ≥ 18 anos → não exige; menor →
  exige consentimento não revogado cuja versão seja a vigente ou compatível (não superada por versão
  `requiresNewConsent`).
- **No cadastro** (FR-030): `RegisterStudentCommand` e `UpdateStudentCommand` (os mesmos da 005, usados por Web
  administrativa, Web do professor e Telegram) ganham `ConsentInput` opcional; `RegisterStudentUseCase` e
  `UpdateStudentUseCase` chamam `ConsentPolicy.requireOnRegistration` e recusam com `VALIDATION_FAILED`
  (`field = consent.*`) quando o aluno é ou passa a ser menor sem consentimento. O consentimento é gravado na **mesma
  transação** do cadastro, com evento `CONSENT_RECORDED`.
- Casos de uso próprios: `RecordConsentUseCase` (fora do cadastro, a partir do aviso de bloqueio), `RevokeConsentUseCase`
  e `PublishConsentTermUseCase` (administrador), `ListPendingConsentsUseCase` (FR-035), `GetCurrentConsentTermUseCase`
  (professor e administrador, para mostrar a versão vigente).
- **Telegram**: o cadastro guiado da 005 (`TelegramStudentRegistryFlow`) ganha, depois da data de nascimento e só
  quando o aluno é menor, os passos do consentimento ([contracts/telegram-consent.md](./contracts/telegram-consent.md));
  a versão do termo é a vigente, mostrada pelo bot com o contato do responsável pela LGPD. Paridade: os mesmos casos de
  uso e as mesmas validações (`ConsentInput` valida a si mesmo).
- **Bloqueio de IA** (FR-034), duas barreiras no núcleo:
  1. `ConsentGuardService.requireForAi(studentId)` como primeira regra, depois da autorização, em
     `RegisterLessonByAudioUseCase`, `AnswerQuestionsUseCase`, `RequestLessonReportChangeUseCase`,
     `RetryLessonReportUseCase`, `GenerateSemesterReportUseCase`, `RequestSemesterReportChangeUseCase` → erro
     `CONSENT_REQUIRED` antes de qualquer porta de IA; o áudio temporário é descartado pelo caminho de erro que já existe
     (`TemporaryFile` fechado no `finally`).
  2. `JobProcessorService` e `SemesterProcessorService` checam de novo antes de cada chamada de porta de IA (revogação
     entre o enfileiramento e o processamento) e encerram o trabalho com falha `CONSENT_REQUIRED`, sem chamar provedor.
  Um teste de arquitetura garante que todo caso de uso que alcança `TranscriptionPort`/`LlmPort` passa pelo guarda.
- **Carga da implantação** (FR-036): comando operacional `consent-load` (perfil `seed`, como `seed` e `issue-code`), que
  lê um CSV de um caminho montado **fora do repositório** e chama `RecordConsentUseCase` com o ator `INITIAL_LOAD`.
  Formato e regras em [contracts/consent-load.md](./contracts/consent-load.md). A tela de pendências cobre o que faltar.

**Justificativa**: o consentimento é parte do cadastro (D8: "obrigatório no cadastro"); reaproveitar os comandos e
casos de uso da 005 mantém a paridade sem duplicar validação. O guarda duplo cobre revogação durante o processamento.

**Alternativas**: checar o consentimento no adaptador da Web e do bot — regra fora do núcleo (Princípio I); só a
primeira barreira — deixa passar trabalho já enfileirado; texto do termo como arquivo no repositório — o administrador
precisa publicar versão nova sem PR (FR-032).

## R-05 — Eliminação a pedido

**Decisão**: três casos de uso do administrador (somente Web): `RegisterErasureRequestUseCase` (situação `RECEIVED`),
`PreviewErasureUseCase` (alcance em números e um **token de confirmação** de uso único com validade de 10 min, guardado
como HMAC, igual aos códigos da 005) e `ConfirmErasureUseCase` (palavra de confirmação `ELIMINAR` + token).
`ConfirmErasureUseCase`, numa transação:

1. passa o pedido a `IN_PROGRESS`; cancela os relatórios em aberto e os trabalhos de processamento do aluno;
2. anonimiza o aluno: `name`, `birth_date`, `cpf_encrypted`, `cpf_hash`, `guardian_*`, `instrument` → `NULL`;
   `erased_at` preenchido; o rótulo "Aluno removido · #XXXXXX" (6 primeiros caracteres do id) é montado na exibição;
3. em todos os relatórios do aluno (aula e semestral, de todos os professores, inclusive aprovados): fonte removida
   (R-01), `report_version.content` → `{}` com `content_removed_at`, `pdf_document.content_removed_at`;
4. remove as associações professor-aluno e os `channel_document` dos relatórios; apaga o `payload` das
   `channel_action` já entregues ou finalizadas (o texto delas pode citar o nome; as pendentes têm minutos de vida e
   o payload some em 24 h pela limpeza existente);
5. consentimentos do aluno: `consenting_name` → `NULL` (FR-023);
6. grava `erased_student (student_id, erased_at)` (FR-026) e os eventos `ERASURE_CONFIRMED`.

Depois do commit, os arquivos de PDF são removidos (`DocumentStoragePort.delete`); concluído isso, o pedido vai a
`COMPLETED`, o nome de quem pediu é apagado (fica o papel) e grava-se `ERASURE_COMPLETED` com o alcance em números.
Falha na remoção de arquivo deixa o pedido `IN_PROGRESS` com alerta `STORAGE_FAILED`; confirmar de novo (ou a retenção
seguinte) termina (cenário "eliminação que falha no meio"). **Trabalho de IA em curso**: o cancelamento do trabalho e
o `lock_version` do relatório fazem a gravação tardia falhar; o processador descarta o resultado (aluno
`erased_at` ≠ nulo é checado antes de gravar).

O **comprovante** (FR-025) é uma tela imprimível da Web (CSS de impressão, sem PDF novo), com protocolo, datas, alcance
em números e contato do responsável pela LGPD, sem dado do aluno.

**Justificativa**: uma transação para tudo o que é banco cumpre "vale inteira ou não vale"; arquivos não são
transacionais e por isso ficam marcados e são removidos depois, com repetição segura. `NULL` em vez de valor falso
deixa o CPF livre (a restrição única ignora `NULL`).

**Alternativas**: apagar as linhas do aluno e dos relatórios — quebra a autoria e os identificadores que a spec manda
manter; cifrar e jogar a chave fora (crypto-shredding) — exigiria chave por aluno e mudança no esquema inteiro; PDF de
comprovante — mais um modelo de PDF sem ganho.

## R-06 — Métricas sobre o que o FR-056 já grava

**Decisão**: `ai_call` continua sendo a medição de cada chamada. O que falta:

| Lacuna | Como fecha |
|---|---|
| canal da chamada | coluna `channel` em `ai_call`, preenchida pelo `JobProcessorService`/`SemesterProcessorService` a partir do `ProcessingJob` (que já tem `request_channel`); migração preenche as linhas antigas pelo `job_id` |
| unidade medida | já existe (`audio_seconds`; `input_tokens` + `output_tokens`); nada novo |
| preço com vigência (FR-052) | `reports.costs.prices[]` com `provider`, `unit` (`AUDIO_HOUR`, `INPUT_MILLION_TOKENS`, `OUTPUT_MILLION_TOKENS`), `valueUsd`, `effectiveFrom`; a tabela é validada no núcleo (`PriceTable`) e o adaptador de IA escolhe o preço vigente na data da chamada. O custo já é gravado por chamada, então mudar o preço não muda o passado. A configuração antiga (`priceUsdPerHour` etc.) vira um preço com `effectiveFrom` = `1970-01-01` se a lista estiver vazia |
| custo fixo de infraestrutura | `reports.costs.infrastructure-monthly-usd` (valor e `effectiveFrom`), somado ao total do mês |
| por áudio, aula, aluno, semestral, mês, canal, provedor (FR-053) | `GetMonthlyCostReportUseCase` sobre a porta nova `CostReportQueryPort` (agregação SQL por mês no fuso da escola). "Áudio" = chamada `TRANSCRIPTION`; "aula" = soma das chamadas de um `lesson_report` + `processing_time_ms`; "aluno" = soma por `student_id` dos relatórios; "semestral" = soma por `semester_report` |
| tempo por unidade | `duration_ms` por chamada (áudio) e `processing_time_ms` do relatório (aula e semestral), ambos existentes |

Moeda: **USD**, como já é gravado. Converter para real fica como evolução (não está na spec; exigiria cotação).
SC-008 é verificado por teste que compara o total do painel com `SUM(estimated_cost_usd)`.

**Justificativa**: a fonte já existe e é por chamada; agregar no banco evita tabela de agregados que precisaria ser
mantida em sincronia.

**Alternativas**: Micrometer + Prometheus + Grafana — ver R-08; tabela de agregados mensal — duplicação sem ganho no
volume da escola (centenas de chamadas por mês).

## R-07 — Alertas com o menor mecanismo que atende

**Decisão**: alertas vivem no **PostgreSQL** e são avaliados pelo **backend**:

- Tabela `operational_alert` (tipo, componente, `started_at`, `last_seen_at`, `occurrences`, situação `ACTIVE` /
  `NORMALIZED`, `normalized_at`); índice único parcial por (tipo, componente) onde `ACTIVE` garante "um alerta só"
  (FR-056).
- **Regra no núcleo**: `OperationalAlertService` (abrir, atualizar, normalizar) e `EvaluateOperationalAlertsUseCase`,
  chamado a cada 60 s por `OperationalAlertTask` (ator de sistema), lendo sinais pela porta nova
  `OperationalSignalQueryPort`:
  - `AI_PROVIDER_FAILING`: por provedor, últimas 3 chamadas com falha **ou** > 20% de falhas em 15 min, de `ai_call`;
    normaliza quando a última chamada tem sucesso e a taxa cai abaixo do limite;
  - `CHANNEL_QUEUE_BACKLOG`: `channel_action` `PENDING` com `created_at` > 5 min **ou** > 50 itens; normaliza abaixo
    dos dois limites;
  - `STORAGE_FAILED` e `RETENTION_FAILED` e `AUDIT_CHAIN_BROKEN`: abertos no momento da falha, pelo núcleo, onde a
    exceção da porta é tratada (`OperationalAlertService.report`); `STORAGE_FAILED` normaliza depois de 15 min sem
    ocorrência nova ou na primeira operação de armazenamento bem-sucedida; `RETENTION_FAILED`, na próxima execução
    bem-sucedida; `AUDIT_CHAIN_BROKEN` só por ação do administrador (registrada).
- Limites em `reports.alerts.*` (FR-055), validados no núcleo como os prazos.
- **Painel**: `GET /admin/operations/alerts` (ativos e histórico) e `GET /admin/operations/alerts/summary` (contagem de
  ativos), que o cabeçalho da área administrativa consulta ao navegar e a cada 60 s enquanto a aba está visível
  (FR-056: "indicar em qualquer tela"). Sem envio ativo (decisão do clarify).
- Em até 5 min (SC-006): avaliação a cada 60 s + atualização do cabeçalho a cada 60 s → no pior caso ~2 min.
- Histórico dos alertas normalizados segue a guarda da trilha (5 anos), apagado pela retenção.

**Justificativa**: o destino é só uma tela da Web; os dados de origem (`ai_call`, `channel_action`) já estão no banco.
Uma tabela e uma tarefa de 60 s atendem sem nenhum contêiner novo.

**Alternativas**: Prometheus + Alertmanager — avalia regras, mas o destino seria de novo o banco/tela; Grafana —
ver R-08; Server-Sent Events/WebSocket para o indicador — complexidade para um aviso que tolera 5 min.

## R-08 — Nenhuma ferramenta de observabilidade nova (Princípio VII)

**Decisão**: **não** adicionar Prometheus, Grafana, Loki, Alertmanager, OpenTelemetry Collector, Uptime Kuma nem
exportadores. A observabilidade da 007 usa só o que já roda em contêiner: backend (Spring), PostgreSQL, logs JSON no
stdout do Docker. Duas mudanças de configuração, sem serviço novo:

1. **Rotação de logs** no `compose.yaml` (driver `json-file` com `max-size: 20m`, `max-file: 5` em todos os
   serviços), para os logs não lotarem o disco da VPS. Recurso do Docker Engine, neutro de provedor.
2. **Correlação**: o `RequestCorrelation` (Web) e o identificador do update (Telegram) passam a ir ao MDC como
   `correlationId`, exigido pelo FR-050.

Para o caso de, no futuro, se querer uma ferramenta, a exigência fica registrada: contêiner com versão fixada, só na
rede interna do Compose, sem porta publicada, acesso só por túnel SSH, e spec que a peça (Princípio VII).

**Justificativa**: cada pedido da spec tem destino na tela do administrador (FR-053, FR-056) e fonte no banco.
Uma pilha de métricas traria 3 a 4 contêineres, memória na VPS, autenticação própria e uma superfície a mais para não
expor (Princípio IV), sem atender nada que a tabela não atenda. Spring Boot Actuator também não é exposto: o
`healthcheck` do Compose continua como está.

**Alternativas**: as listadas — rejeitadas por não haver requisito que as exija; Actuator `/metrics` na rede interna —
nenhum consumidor.

## R-09 — Política e limites validados no núcleo

**Decisão**: o adaptador lê `reports.retention.*`, `reports.alerts.*`, `reports.costs.*` e `reports.privacy.*` como
texto bruto e entrega ao núcleo; `RetentionPolicy.from(raw)` e `AlertThresholds.from(raw)` devolvem os valores
efetivos e a lista de avisos (valor ausente → padrão; zero, negativo ou inválido → padrão + aviso; FR-001). O
adaptador só loga os avisos (`WARN`, citando a variável, nunca o valor de segredo). A tela de alertas mostra os prazos
efetivos e a origem (padrão/configuração).

**Justificativa**: "zero não é apagar tudo" é regra de negócio; fica no núcleo e é testada por unidade.

**Alternativas**: `@Validated` do Spring recusando a subida — a spec manda manter o padrão e avisar, não recusar.

## R-10 — Canais e permissões

**Decisão**: operações novas na `PermissionMatrix` e em `permission-matrix.json` da 005 (por nota de emenda), lista em
[contracts/permission-matrix-additions.md](./contracts/permission-matrix-additions.md). Administrador: `ALLOW` só na
Web; Telegram: `DENY` (negativa neutra da 005, FR-037). Professor: `CONSENT_RECORD` e `CONSENT_TERM_READ` com
`ALLOW_OWN_STUDENTS` na Web e no Telegram. `CrossCredentialAnswerer` e os testes gerados da matriz cobrem as rotas
novas automaticamente.

## R-11 — Contratos e telas

**Decisão**: contrato novo [api-privacy-operations.openapi.yaml](./contracts/api-privacy-operations.openapi.yaml) sob
`/api/v1/admin/` (trilha, eliminação, consentimentos, termos, pendências, custos, alertas) e um esquema `ConsentInput`
que o contrato da 005 passa a referenciar no cadastro e na edição de aluno (administrador e "Meus alunos"), por nota de
emenda. O frontend gera os tipos deste contrato como na 006 (uma linha no `package.json`). Telas em
[contracts/portal-screens.md](./contracts/portal-screens.md).

**Alternativas**: acrescentar tudo ao contrato da 005 — arquivo já grande e de outra feature.

## R-12 — Inventário de tratamento

**Decisão**: pasta nova `privacy/` na raiz com `CLAUDE.md` e `processing-inventory.md` (FR-070), em português, com uma
seção por provedor (transcrição, LLM, mensageria, armazenamento) e uma pelo sistema (dados, prazos efetivos padrão,
contato do responsável). O item de revisão do FR-071 entra (a) num `.github/pull_request_template.md` novo com a
caixa "mudou o que vai a um provedor? atualizei `privacy/processing-inventory.md`" e (b) no agente `privacy-auditor`,
que passa a acusar diff em `outbound/ai/**` ou `prompts/**` sem diff no inventário.

**Justificativa**: o inventário é documento vivo da operação, não histórico de feature; por isso fora de `specs/`.

**Alternativas**: dentro de `specs/007-*/` — vira histórico e ninguém atualiza; dentro de `infra/` — não é infraestrutura.

## R-13 — Testes que provam os critérios

| Critério | Teste |
|---|---|
| SC-001 (zero dado de aluno) | `PrivacyCanaryIT` (application, `privacy/`): planta valores marcados (`CANARIO-…`) em nome, CPF, nascimento, responsável, transcrição simulada e relatório; percorre os fluxos dos dois canais (Web e API interna do n8n) e varre o `ListAppender` dos logs, `ai_call`, `operational_alert`, `authorship_record`, `retention_run_item` |
| SC-002 (100% dos eventos) | `AuditEventCoverageIT`: tabela de [audit-events.md](./contracts/audit-events.md) como fonte; para cada linha e canal, executa a operação e conta exatamente 1 evento |
| SC-003 (retenção) | unitários do domínio com `ClockPort` falso + `RetentionIT` com relógio avançado, aprovados, cancelados, versões e corrida de aprovação |
| SC-004 (eliminação) | `ErasureIT`: busca pelos valores do aluno em todas as tabelas (consulta ao catálogo `information_schema` com `LIKE` nas colunas de texto, `jsonb` incluído), no armazenamento, nas respostas da API, no bot e nos logs |
| SC-005 (bloqueio) | dublês de `TranscriptionPort`/`LlmPort` que contam chamadas; testes de paridade Web ≡ Telegram |
| SC-006 (alertas) | `OperationalAlertIT` com sinais simulados e relógio falso |
| SC-008 (custo) | `CostReportIT`: soma do painel vs `SUM(estimated_cost_usd)` |
| SC-009 (canais) | testes de credencial cruzada gerados da matriz + paridade |
| Proteção da trilha | `AuditTrailTamperIT`: com o papel `reports_app`, `UPDATE`/`DELETE`/`TRUNCATE`/`ALTER TABLE` falham; com o papel dono, alterar uma linha faz `VerifyAuditChainUseCase` abrir `AUDIT_CHAIN_BROKEN` |
