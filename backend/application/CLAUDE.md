# backend/application

Aplicação Spring Boot que **liga o `core` ao mundo**: recebe requisições, implementa as portas e
sobe o serviço. Contém `Dockerfile`, `docker-entrypoint.sh` e `application.yaml`.

Pacotes em `src/main/java/br/com/felipeblaft/reports/`:

| Pacote | Conteúdo |
|---|---|
| `inbound/web` | API do Portal do professor (`*Controller`, inclusive `MyStudent*` = "Meus alunos", `/api/v1/my-students`), com sessão e segurança web; `RequestCorrelation` carimba cada requisição com o identificador de correlação da autoria |
| `inbound/web/admin` | API da área administrativa (`/api/v1/admin/**`, somente Web): `AdminSessionController` (entrada, ativação, sessão, troca de senha), `AdminTeacherController`, `AdminStudentController`, `AdminAssignmentController`, `AdminAdministratorController` e seus `*Dtos` |
| `inbound/web/security` | Cadeias de segurança da Web (ver "Cadeia de segurança"): `SecurityConfig` (professor), `AdminSecurityConfig`, `AdminSessionFilter`, `AdminSessionCookie`, `CrossCredentialAnswerer`, `RequestOriginResolver` |
| `inbound/telegram` | Conversa do canal Telegram (interpretador e fluxos, inclusive `TelegramStudentRegistryFlow` = "Meus alunos" e `CpfShape`); a entrada é a rota interna do n8n em `inbound/telegram/channel` |
| `inbound/internal` | API interna consumida pelo n8n (transporte do Telegram), com segurança própria |
| `inbound/bootstrap` | `FirstAdministratorBootstrap`: na subida, lê `ADMIN_BOOTSTRAP_*` e chama o caso de uso com o ator `SYSTEM_STARTUP` (quem decide é o núcleo; valor inválido recusa a subida citando a variável, nunca o valor) |
| `inbound/seed` | Comandos operacionais: `seed` (carga inicial), `issue-code` (emergência: reemite o código de acesso e encerra as sessões Web), `rotate-cpf-key` (rotação das chaves do CPF) e `demo-data` (massa de demonstração, só local) |
| `outbound/persistence` | `*RepositoryAdapter` (JPA/Flyway) |
| `outbound/ai`, `outbound/audio`, `outbound/pdf`, `outbound/storage`, `outbound/queue`, `outbound/telegram`, `outbound/security` | `*Adapter` das portas do `core` |
| `config` | Beans, propriedades (`reports.*`) e logging (`log/`) |

Recursos em `src/main/resources`: `db/migration` (Flyway `V<n>__*.sql`; **`V1` a `V16` são imutáveis**, nunca edite
migração já aplicada), `ai/schemas` (JSON Schema das saídas de IA), `ai/simulated` (respostas do provedor simulado),
`messages` (catálogo `messages_pt_BR.properties`, texto ao usuário), `prompts` e `pdf` (modelos HTML, estilos, fontes e imagens).

Migrações da feature 005 (todas aditivas; não apagam dado existente):

| Migração | O que faz |
|---|---|
| `V9__administrator` | `administrator` e `administrator_session` (e-mail único em minúsculas; senha só como hash Argon2id; código de ativação só como HMAC) |
| `V10__authorship_record` | `authorship_record`, só de acréscimo (gatilhos barram `UPDATE`, `DELETE` e `TRUNCATE`); sem chave estrangeira e sem coluna que aceite CPF ou dado de aluno |
| `V11__access_attempt_scope` | contagem de tentativas por escopo (`TEACHER_CODE`, `ADMIN_SIGN_IN`, `ADMIN_ACTIVATION`, `TEACHER_CPF_PROBE`, `TEACHER_CPF_LOOKUP`); as linhas antigas viram `TEACHER_CODE` |
| `V12__student_contact_and_registration_draft` | contato do responsável no aluno e `student_registration_draft` (cadastro em andamento no Telegram, com CPF cifrado) |
| `V14__web_session_revocation` | `revoked_at` e `revocation_reason` na sessão do professor (a sessão é marcada, nunca removida) |
| `V15__channel_action_delete_message` | acrescenta `DELETE_MESSAGE` à `CHECK` do tipo de ação do canal |

Não existe `V13`: a migração da ação `DELETE_MESSAGE` nasceu como `V13` e foi **renumerada para `V15`**, depois da `V14`, para
que um banco já migrado até a `V14` não recuse a migração fora de ordem.

Migração da feature 006 (aditiva):

| Migração | O que faz |
|---|---|
| `V16__authorship_read_detail` | colunas tipadas de detalhe de leitura em `authorship_record` (filtros, página, versão do documento), tipos de alvo `LESSON_REPORT`/`SEMESTER_REPORT` e índices parciais de relatórios aprovados |

Migração nova começa na `V17`. A consulta de aprovados da escola (`ApprovedReportQueryAdapter`) e as rotas de leitura do
administrador (`AdminSchoolHistoryController`) seguem `specs/006-admin-report-history/contracts/`.

Privacidade, retenção e consentimento (feature 007; migração `V17`, imutável depois de aplicada: banco antigo exige `docker compose -f infra/compose.test.yaml down -v`):

| Peça | O que é |
|---|---|
| `V17` | trilha encadeada e só de acréscimo, consentimento, retenção (`retention_run`, `retention_run_item`), eliminação (`erasure_request`, `erased_student`) e `operational_alert` |
| papel `reports_app` | o pool da aplicação usa o papel sem `UPDATE`/`DELETE` na trilha; o Flyway segue com o dono do banco (`infra/scripts/lib/db-roles.sh`, `DB_APP_USER`/`DB_APP_PASSWORD`) |
| `outbound/persistence/RetentionTask` | agenda a rotina de retenção e só chama `RunRetentionUseCase`; perfil `!seed` |
| `inbound/seed/ConsentLoadCommand` | comando `consent-load` (perfil `seed`): carga dos consentimentos já coletados, contrato em `specs/007-privacy-retention-operations/contracts/consent-load.md` |
| `inbound/web/admin` | controladores de trilha, consentimento, eliminação e operação (`/api/v1/admin/audit-trail`, `/consents`, `/erasure-requests`, `/operations`) |

Custo e alertas de operação (feature 007, US5; sem migração nova, usa a V17):

| Peça | O que é |
|---|---|
| `outbound/persistence/OperationalAlertRepositoryAdapter` | `INSERT ... ON CONFLICT` sobre o índice único parcial dos ativos: nunca há dois alertas ativos iguais sob concorrência |
| `OperationalSignalQueryAdapter`, `CostReportQueryAdapter` | sinais de `ai_call` e `channel_action` e as linhas de custo do mês (só contagens e identificadores; o nome do aluno só em `studentNames`) |
| `OperationalAlertTask` | `fixedDelay` de `reports.alerts.evaluation-seconds`, perfil `!seed`; só chama `EvaluateOperationalAlertsUseCase` |
| `inbound/web/admin/AdminOperationsController` | `/api/v1/admin/operations/{costs,alerts,alerts/summary,alerts/{id}/acknowledgement}`; o resumo não grava evento |
| `outbound/ai/AiPricing` | os adaptadores de IA leem o preço da `PriceTable` pela data de hoje no fuso da escola (o das propriedades é reserva); custo já gravado nunca muda |

## Cadeia de segurança

Há **três** `SecurityFilterChain`, avaliadas por `@Order`; cada uma só aceita o seu cookie ou token:

| Ordem | Cadeia | Rotas | Credencial |
|---|---|---|---|
| 1 | `InternalSecurityConfig` | `/internal/**` | token de serviço do n8n (`X-N8N-Service-Token`) |
| 2 | `AdminSecurityConfig` | `/api/v1/admin/**` | cookie `admin_session` (`HttpOnly; Secure; SameSite=Strict; Path=/api/v1/admin`, sem `Max-Age`), validado **em toda requisição** pelo `AdminSessionFilter`, sem cache |
| 3 | `SecurityConfig` | as demais rotas (`/api/v1/**` do professor) | cookie `session` do professor |

- Só `POST /api/v1/admin/session` e `POST /api/v1/admin/activation` são públicas na cadeia administrativa. O
  `AdminSessionFilter` também é a segunda barreira da senha provisória: deriva do núcleo
  (`ActorAuthorizationService.ALLOWED_WHILE_RESTRICTED`) as únicas rotas liberadas, e `AdminSessionFilterRestrictedRoutesTest`
  reprova operação nova sem rota mapeada.
- **Credencial cruzada** (`CrossCredentialAnswerer`): cookie válido do outro papel, sem credencial própria, recebe a resposta de rota
  inexistente, byte a byte igual ao 404 padrão; sem credencial alguma, 401. Nada revela que a rota existe para outro papel.
- A autorização de verdade continua no núcleo; as cadeias só autenticam. Cada controller monta o `Actor` da sessão
  (`AuthenticatedAdministrator`, `AuthenticatedTeacher`) e traduz o `DomainException` pelo `ErrorHandlerController`.
- Origem das tentativas: `RequestOriginResolver` só aceita `X-Forwarded-For` de conexão interna (o Caddy); a origem é guardada como hash.
- **Correlação e referência de erro**: `RequestCorrelation` (Web) dá um identificador por requisição à autoria; falha interna
  gera um código curto por `config/log/ErrorReference` (fonte única da Web e do Telegram), que vai ao log e ao campo `reference` da
  resposta `INTERNAL_ERROR`, nunca com detalhe técnico.
- Varreduras agendadas (`*Task`): `AccessSweepTask` (sessões vencidas e updates antigos) e `StudentDraftSweepTask` (cadastros do
  Telegram parados).

Regras:

- Nomes seguem o Princípio VI: identificadores em inglês, sufixos `*Controller`, `*Adapter`, `*Mapper`, `*Request`,
  `*Response`. O guardião de idioma (ver `.specify/CLAUDE.md`) reprova palavra em português em nome, arquivo, tabela,
  coluna, YAML, shell, JSON e `.properties`; texto mostrado ao usuário e log continuam em português.
- Adaptador de entrada só traduz requisição → caso de uso; nenhuma regra de negócio aqui.
- Comandos operacionais (`seed`, `issue-code`, `rotate-cpf-key`, `demo-data`) rodam no perfil `seed`, que o `docker-entrypoint.sh`
  ativa; o `SeedCommandsExecutor` só existe nesse perfil e fora dos testes (`seed & !test`). Mudar o nome do perfil exige mudar o
  entrypoint, as anotações `@Profile` e `SeedCommandsExecutorProfileTest` juntos. A carga e o bootstrap não têm regra própria:
  chamam os casos de uso do núcleo com o ator de sistema (`INITIAL_LOAD` e `SYSTEM_STARTUP`).
- Cadastro do professor: `MyStudentController` (Web) e `TelegramStudentRegistryFlow` (Telegram) são adaptadores finos sobre os
  **mesmos** casos de uso de `usecase.registry`; o CPF nunca vai a URL, log nem resposta. No Telegram, `CpfShape` só reconhece a
  *forma* do CPF para que ele não seja usado como busca nem ecoado, e a mensagem que o contém é apagada pela ação `DELETE_MESSAGE`.
- Rede de segurança do `ErrorHandlerController`: fora de `/api/v1/admin/` e no Telegram, `DUPLICATE_CPF` vira `CPF_UNAVAILABLE`
  e `DUPLICATE_EMAIL` usa o texto neutro, sem nome do aluno e sem falar de CPF.
- Provedores de IA (transcrição, LLM) são trocáveis por configuração; o provedor real fica atrás da porta.
- Contratos: a API do Portal segue `specs/001-fluxo-relatorios/contracts/api-portal.openapi.yaml`, a API administrativa e o
  "Meus alunos" seguem `specs/005-admin-registrations-access/contracts/` (`api-administration.openapi.yaml`,
  `permission-matrix.json`, `messages.md`, `telegram-student-registry.md`) e a API interna segue
  `specs/002-api-interna-canal-telegram/contracts/`; mudou a API, atualize o contrato e os testes de contrato.
  Nomes de campo, rota, código de erro e chave de documento são contrato: não mude sem mudar o contrato. Nenhuma propriedade de
  resposta se chama `cpf`.
- Testes especiais em `src/test`: `architecture` (ArchUnit e guardião de idioma), `parity` (Web ≡ Telegram), `privacy` e
  `security` (credencial cruzada, gerada da matriz via `support/MatrixLoader`), `realai` (chamadas reais, fora da cobertura
  unitária). O `AdapterRulesTest` guarda, além de nomes e dependências: o ator de sistema só é criado na inicialização e na carga
  inicial; todo caso de uso de administração é conhecido e o conjunto proibido a cada papel segue a matriz; as superfícies do
  professor (Web e Telegram) não alcançam casos de uso nem repositórios de administrador. `InboundPersonalDataToStringTest` proíbe
  `toString` que revele dado pessoal nos objetos de borda.
- Telegram: o n8n é o **único** transporte (feature 004). O backend nunca recebe webhook direto do Telegram nem envia
  mensagem pela API de bots; as notificações proativas viram ações na outbox, entregues ao n8n. O backend só chama a
  API de bots para baixar o áudio (`getFile`, `TelegramFileAdapter`) e registrar os comandos (`setMyCommands`); o
  webhook é registrado por `infra/scripts/telegram-webhook.sh apply`. Nos testes, `TelegramConversationBase` usa a
  API interna com o executor em memória e entrega a outbox na publicação (`ProactiveOutboxDrain`).
- Segredos e áudio nunca em log nem no código; áudio original não é retido após o processamento.
