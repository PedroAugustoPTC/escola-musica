# Implementation Plan: Administração — acesso e cadastros (administrador na Web; professor na Web e no Telegram)

**Branch**: `spec/005-admin-registrations-access` | **Date**: 2026-10-20 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `specs/005-admin-registrations-access/spec.md` (constituição **v2.2.0**)

## Summary

A escola deixa de depender da carga inicial: o **Administrador** (novo ator, **somente Web**) cadastra e mantém
professores, alunos, associações e outros administradores e emite os códigos de acesso; o **Professor** passa a
cadastrar e manter **somente os próprios alunos**, **na Web e no Telegram, com os mesmos casos de uso**. O sistema tem
exatamente **dois papéis**; aluno e responsável não têm conta, rota, comando, tela nem aviso.

**Abordagem técnica** (decisões em [research.md](./research.md)):

1. **Um caso de uso por operação, com ator e escopo no núcleo** (R-01): cada operação recebe `Actor(role, id, origin)`,
   autoriza pela `PermissionMatrix` (espelho de [contracts/permission-matrix.json](./contracts/permission-matrix.json)) e aplica
   `StudentScope` (administrador: escola inteira; professor: só os associados). Web, Telegram e carga inicial só traduzem
   para os mesmos casos de uso. Aluno alheio ≡ aluno inexistente (`findVisible`, uma consulta, R-14).
2. **Reaproveitamento máximo do que existe** (tabela abaixo): serviço de tentativas e bloqueio estendido com *escopo* e
   *janela* (R-05, R-06); `DataProtectionAdapter` (AES-GCM + HMAC) inalterado para o CPF (R-12); geração/renovação de código
   de acesso (`AccessCodeIssuanceService`) e um `ActivationCode` espelho (R-07); vínculo do Telegram, fila de ações do canal e
   idempotência da 002 (R-09, R-21); carga inicial **convertida para os mesmos casos de uso** (R-17).
3. **Sessão do administrador**: opaca, **revogável**, tabela própria, **fixa em 2 h sem renovação**, validada no servidor em
   toda requisição; cookie `admin_session` com `Path=/api/v1/admin` (R-03, R-19). Senha com **Argon2id** (R-04).
4. **Vínculo e confirmação no Telegram (D2)**: só o professor; confirmação por **rascunho persistido** com token de uso
   único e a ação nova `DELETE_MESSAGE` para apagar a mensagem com o CPF (R-09, R-21).
5. **Revogação "na hora"** em todos os caminhos, por um único `TelegramLinkTerminationService` e marcação (não remoção)
   das sessões (R-08); **autoria** em tabela somente de acréscimo, com o **papel do autor**, gravada na mesma transação
   (R-10); **validação no núcleo** por objetos de valor (R-11); **CPF duplicado** sem vazamento (R-13).
6. **Contratos**: [api-administration.openapi.yaml](./contracts/api-administration.openapi.yaml) (área administrativa e
   "Meus alunos"), [permission-matrix.json](./contracts/permission-matrix.json), [telegram-student-registry.md](./contracts/telegram-student-registry.md),
   [portal-screens.md](./contracts/portal-screens.md), [messages.md](./contracts/messages.md), [initial-load.md](./contracts/initial-load.md).
7. **Execução em paralelo por subagentes**: fundação sequencial que congela assinaturas e arquivos compartilhados, seguida de
   **15 fluxos de trabalho de propriedade disjunta de arquivos** (seção "Execução em paralelo").

### O que já existe e é reaproveitado

| Pedido | Existe hoje | Como a 005 usa |
|---|---|---|
| Serviço de tentativas e bloqueio (Web e Telegram) | `AccessAttemptsControlService`, `AccessAttempt`, `AccessAttemptRepository` | **mesmo serviço**, ganha `AttemptScope`/`AttemptPolicy` (admin, ativação, sondagem de CPF); gravação atômica com lock de linha; contagem separada por papel |
| Cifragem e hash do CPF | `Cpf`, `DataProtectionPort`/`DataProtectionAdapter` (AES-256-GCM, HMAC-SHA-256) | **sem mudança de regra**; `Student` e o rascunho do bot usam os mesmos métodos; procedimento de rotação (`rotate-cpf-key`) |
| Geração e renovação de código de acesso | `AccessCode`, `AccessCodeGeneratorPort`, `AccessCodeIssuanceService`, `AccessRenewalService` | professor: reuso integral na emissão/reemissão; administrador: `ActivationCode` (12 caracteres) com serviço espelho |
| Casos de uso da carga inicial | `LoadInitialRegistryUseCase`, `EmitAccessCodeUseCase`, comandos `seed`/`issue-code` | a carga **orquestra** `Register/UpdateTeacher`, `Register/UpdateStudent`, `AssignTeacherToStudent` com `Actor.system(INITIAL_LOAD)`; `--simulate` = rollback; `EmitAccessCodeUseCase` vira `ReissueAccessCodeUseCase` |
| Vínculo do Telegram | `TelegramLink`, `LinkTelegramUseCase`, `EndTelegramLinkUseCase`, `TelegramConversation` | regra de acesso do professor inalterada; passa a gravar autoria (`TEACHER_LINK_TELEGRAM`, FR-040) e a encerrar o vínculo só pelo novo `TelegramLinkTerminationService` (vínculo + rascunho + ações pendentes), usado por todos os caminhos de revogação, inclusive "sair" |
| Fila de ações do canal | `ChannelAction`, outbox, idempotência por `update_id`, compensação | reaproveitada; acréscimo compatível `DELETE_MESSAGE` (+ compensação `MESSAGE_NOT_DELETABLE`) |
| Sessão e cookie do professor | `WebSession`, `SessionCookie`, `SessionCookieFilter`, `SecurityConfig` | padrão copiado para o administrador em cadeia, tabela e cookie próprios; o do professor ganha só a revogação com motivo |
| Resolução da origem | `RequestOriginResolver` + Caddy (`header_up X-Forwarded-For`) | mesmo resolvedor para o administrador, com teste de cabeçalho forjado |
| Erros, catálogo e HTTP | `ErrorCode`, `MessageCatalog`, `ErrorHandlerController` | estendidos (fundação), `EXPIRED_CODE` e `STUDENT_NOT_ASSIGNED` removidos |
| Autorização por associação | `AuthorizationService`, `ReportAccessService` | `AuthorizationService.requireAssignment` passa a lançar `RESOURCE_NOT_FOUND` (FR-050); `StudentAccessService` aplica o escopo nas novas operações |

## Technical Context

**Language/Version**: Java 25 + Spring Boot 4 (Maven multi-módulo `core` / `application`); TypeScript, React 19, Vite
(`frontend`); SQL (PostgreSQL, Flyway); scripts POSIX `sh`; workflows do n8n em JSON (nós `Code` em JavaScript)

**Primary Dependencies**: as da 001 a 004 (Spring Web/Security/Data JPA, Flyway, ArchUnit, JaCoCo, networknt
json-schema-validator, WireMock, TanStack Query, React Router, Vitest, Playwright + axe). **Única dependência nova de
produção**: `org.bouncycastle:bcprov-jdk18on` (Argon2 do Spring Security Crypto), com versão fixada — contingência PBKDF2
em R-04. Nenhuma dependência nova no frontend.

**Storage**: PostgreSQL. Migrações novas `V9` a `V14` (administrador e sessão; autoria; escopo das tentativas; contato do
responsável e rascunho do bot; ação `DELETE_MESSAGE`; revogação das sessões do professor). `V1` a `V8` intocadas.

**Testing**: núcleo ≥ 90% (linha e branch, JaCoCo) com dublês; testes de adaptador/slice; **contrato** (respostas × OpenAPI,
matriz × `PermissionMatrix`); **acesso cruzado gerado da matriz**; ArchUnit (Role = 2, `Actor.system`, Telegram sem
administrador, autoria sem UPDATE/DELETE); **concorrência com Postgres real**; paridade Web ≡ Telegram (`parity/`);
privacidade com CPF canário (`privacy/`); indistinguibilidade com medição de tempo; guardiões de idioma; Vitest;
Playwright + axe (claro/escuro, 390 e 1440 px); `tests-n8n` para `DELETE_MESSAGE`. Tudo em contêiner (`local.sh tests …`).

**Target Platform**: contêineres Linux (Docker 27+, Compose v2), local e VPS genérica; navegador moderno e Telegram.

**Project Type**: aplicação web (backend hexagonal + SPA) com canal Telegram por transporte externo (n8n).

**Performance Goals**: entrada do administrador p95 ≤ 500 ms incluindo Argon2 (≈ 50–100 ms); lista de até 1 000 alunos em
≤ 300 ms (busca em memória, R-22); cadastro completo pelo administrador ou pelo professor em ≤ 3 min de uso (SC-001, SC-002);
revogação efetiva na requisição seguinte ao commit (SC-010).

**Constraints**: sessão do administrador fixa em 2 h, sem cache de autorização; nenhum CPF fora do campo de entrada e do
armazenamento cifrado; resposta de aluno alheio indistinguível da de inexistente; o n8n só transporta; administrador
inalcançável pelo Telegram; WCAG 2.1 AA nos dois temas; textos em português acentuado, identificadores em inglês.

**Scale/Scope**: uma escola; dezenas de professores, algumas centenas de alunos, poucos administradores; ~33 casos de uso;
4 tabelas novas e 4 alteradas; ~30 operações HTTP administrativas e 6 de "Meus alunos"; 15 telas; 1 fluxo de bot; 1 ação de transporte nova.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

Constituição **v2.2.0** (a emenda do PR #28 já está na `main`). Avaliação **antes** da pesquisa (Pré) e **refeita depois** do
design (Pós). Os princípios pedidos (I, II, III, IV, VI, IX) vêm por extenso; os demais, resumidos.

| Princípio / Restrição | Como o plano atende | Pré | Pós |
|---|---|---|---|
| **I. Domínio independente de canal e de fornecedor** | (a) Regra só no núcleo: ~33 `*UseCase` (um por operação), `PermissionMatrix`, `StudentScope`, validadores; `domain` sem Spring/JPA (ArchUnit existente); `usecase` não depende de `inbound`/`outbound`. (b) Web, Telegram e carga são **adaptadores de entrada** que só montam o `Actor` e traduzem DTO ↔ comando (Controllers, `TelegramStudentRegistryFlow`, `SeedCommand`). (c) **Paridade do Professor**: listar, buscar, abrir, cadastrar, editar, inativar e reativar os próprios alunos existem na Web **e** no Telegram, com os **mesmos** casos de uso (tabela em `contracts/telegram-student-registry.md` §7 e teste `parity/`). (d) **Exceção do Administrador (v2.2.0)**: funções administrativas **somente Web**; o Telegram não vincula, não autentica nem oferece menu/comando/botão ao administrador, e responde com negativa neutra; ArchUnit proíbe `inbound.telegram` de referenciar casos de uso/repositórios do administrador. Isto é **regra** do Princípio I, **não desvio** (justificativa em R-09/R-24). (e) Nenhum fornecedor no núcleo (Argon2 e BouncyCastle ficam atrás de `PasswordHashPort`). (f) **Continuidade entre canais** ("trabalho iniciado em um canal pode ser continuado no outro"): o cadastro em andamento no Telegram (`student_registration_draft`) é **estado de conversa**, não trabalho de negócio: nada é gravado antes da confirmação, ele vence em 30 minutos e some com o vínculo (FR-036). O que o professor produz, o **aluno**, é gravado pelo mesmo caso de uso e fica consultável, editável, inativável e reativável nos dois canais (`contracts/telegram-student-registry.md` §7). O rascunho não tem equivalente na Web porque o formulário já é a revisão (FR-009). Se o dono do produto entender que o rascunho é "trabalho" no sentido do Princípio I, o caminho é emenda da constituição, não desvio. | ✅ | ✅ |
| **II. Fonte única da verdade** | (a) Só o backend escreve no banco; o n8n continua **só transporte**: a única mudança é executar `deleteMessage` (R-21), sem decisão, texto ou validação no workflow; o guardião de workflows da 004 continua valendo. (b) **Autorização no backend**: matriz no núcleo; identificador do Telegram nunca autoriza operação administrativa (não há vínculo de administrador). (c) **Estado da conversa persistido**: o cadastro em andamento vive em `student_registration_draft`, sobrevive a reinício do n8n e some com o vínculo (cascata). (d) Validação de CPF, datas, telefone, e-mail, senha e unicidade **no núcleo**, nunca no frontend nem no DTO. (e) Revogação, último administrador e contagem de tentativas são garantidos por transação/lock no backend. | ✅ | ✅ |
| **III. Humano no controle da IA** | A feature **não toca** o ciclo de vida de relatório. O administrador **nunca** conclui, aprova, altera nem cancela relatório (matriz: `LESSON_AND_SEMESTER_REPORTS` negado). Inativar professor/aluno e retirar associação **não** criam, alteram, aprovam ou cancelam versão (SC-013); o "congelamento" é efeito do escopo (R-23). Dados do responsável não vão a IA nem a relatório. | ✅ | ✅ |
| **IV. Privacidade desde o design** | (a) CPF: AES-256-GCM + HMAC com chave fora do banco/repositório/backup; **nunca** devolvido, exibido, logado, em URL, cache, cabeçalho ou autoria; varredura com CPF canário em todo o conjunto de testes (R-25, SC-004). (b) **Escopo do professor no backend** em toda operação que lê/altera aluno (`findVisible`, `StudentScope`), indistinguível de inexistente (R-14, SC-006). (c) Senha só como Argon2id; códigos só como HMAC; exibição única, fora de URL/cache/log (R-04, R-07). (d) Sessão do administrador: cookie `HttpOnly; Secure; SameSite=Strict`, CSRF, id novo por login, validade fixa, revogável (R-03, R-19). (e) **Mínimo de dados**: responsável só nome/telefone/e-mail, fora de IA e de relatórios; autoria guarda o hash da origem, não o IP. (f) HTTPS único pelo Caddy; nenhum serviço novo exposto; segredos em `.env`/cofre, `.env.example` com as variáveis vazias. (g) CPF no Telegram: só conversa privada, mensagem apagada, estado cifrado e descartado com o vínculo; risco do serviço externo aceito e documentado. (h) Riscos aceitos pela spec (sem 2FA, sem reautenticação, bloqueio só por origem, leituras sem rastro até a 007) estão registrados nas Assumptions. | ✅ | ✅ |
| **V. Qualidade obrigatória** | Núcleo ≥ 90%; testes de sucesso, erro e borda (aluno de outro professor, menor sem responsável, 31/02, CPF repetido, concorrência); testes de contrato (OpenAPI, matriz); CI bloqueia merge. Chamadas reais a provedores continuam fora da cobertura. | ✅ | ✅ |
| **VI. Nomenclatura, linguagem ubíqua e idioma** | Código, arquivos, tabelas, colunas, rotas, campos de API, códigos de erro, variáveis de ambiente, scripts, classes CSS e **ações de botão do Telegram** em **inglês** (`/my-students`, `authorship_record`, `INVALID_ACTIVATION`, `ADMIN_BOOTSTRAP_EMAIL`, `reg-confirm`); **todo texto ao usuário e toda mensagem de log em português acentuado** (catálogo único, `contracts/messages.md`; log sem dado pessoal); documentação, specs, comentários, commits e PRs em português. Sufixos padronizados: `*UseCase`, `*Service`, `*Repository`, `*Port`, `*Adapter`, `*Controller`, `*Filter`, `*Config`, `*Request`/`*Response`/`*Dto`. Termos novos (`activation`, `authorship`, `author`, `role`, `phone`, `revocation`, `probing`, `correlation`, `unlink`, `frozen`) entram em `glossary.json` **na fundação, antes do uso** (`data-model.md` §10); as palavras-chave que o professor digita ("meus alunos", "cadastrar aluno", "pular") ficam em português. O guardião (Java e TypeScript) varre os novos contratos, YAML, JSON, `.env.example` e shell. | ✅ | ✅ |
| **VII. Simplicidade e foco** | Escolhas mais simples que atendem à spec: reuso do serviço de tentativas e do padrão de sessão; busca em memória; sem segundo fator, sem recuperação de senha por autoatendimento, sem papel novo. Acréscimos justificados em Complexity Tracking. | ✅ | ✅ |
| **VIII. Fluxo de trabalho** | Branch `spec/005-…` criada da `main` atualizada; a implementação sai em **fatias, cada uma em branch e PR próprios a partir da `main` atualizada**, o PR de cada fatia é aberto ao concluí-la, sem esperar nova solicitação; sem merge pelo autor nem auto-merge; `code-review-graph build` após cada PR; skills `pre-pr-review` e `open-pr`. Nada é commitado na `main` nem em branch de outra fatia. | ✅ | ✅ |
| **IX. Identidade visual consistente e acessível** | Telas novas seguem `portal-do-professor-design-system` (tokens de `tokens.css`, `Secretaria.dc.html` como referência estrutural da área administrativa) e os componentes existentes; **mobile-first a partir de 390 px**; **WCAG 2.1 AA** nos modos **claro e escuro** (contraste, foco visível, alvos ≥ 44 px, rótulos, `aria-live`, navegação por teclado, sem informação só por cor); estados de carregamento, vazio, sem resultado, falha e sucesso; mensagens do bot no tom e na terminologia do portal; Axe no e2e em 390/1440 px e nos dois temas (SC-012). A expiração fixa de 2 h é exceção de tempo essencial (WCAG 2.2.1), mitigada por aviso prévio (R-20). | ✅ | ✅ |
| **Restrição: containerização e implantação** | Sem serviço novo; testes em contêiner; `.env.example` ganha as variáveis (vazias); `local.sh` passa a preencher o administrador fictício local. Argon2 (19 MiB) cabe no limite de memória do backend. | ✅ | ✅ |
| **Restrição: orquestrador (n8n)** | Workflows continuam em JSON versionado, sem segredo; a única alteração é a ação `DELETE_MESSAGE` em `execute-actions.json` (transporte puro). | ✅ | ✅ |
| **Quality gate: paridade Web × Telegram** | Nenhuma entrega de Professor em um canal só. Diferenças são de **controle**, declaradas na spec (confirmação, expiração do vínculo, bloqueio por conta × origem). Funções do Administrador somente Web **não são desvio**. | ✅ | ✅ |
| **Quality gate: segurança/privacidade, separação de responsabilidades, identidade visual** | Revisões obrigatórias (`privacy-auditor`, `infra-security-auditor`, `frontend-reviewer`, `constitution-auditor`) no fecho, listadas na seção de execução. | ✅ | ✅ |

**Resultado**: aprovado antes e depois do desenho, **sem violação e sem nova emenda**. Pendências registradas (nenhuma
bloqueante): (1) a divergência entre o pedido (que cita "administração só na Web" como alternativa descartada) e a spec/constituição
vigentes, que a **adotam** — decisão mantida em R-09; (2) a aprovação da dependência BouncyCastle (contingência PBKDF2); (3) as verificações da lista "Primeiras tarefas" abaixo.

## Project Structure

### Documentation (this feature)

```text
specs/005-admin-registrations-access/
├── plan.md                                    # Este arquivo
├── research.md                                # Fase 0: decisões R-01 a R-26 e alternativas descartadas
├── data-model.md                              # Fase 1: tabelas, domínio, estados, validações, portas, erros
├── quickstart.md                              # Fase 1: validação ponta a ponta
├── contracts/
│   ├── api-administration.openapi.yaml        # /admin/** e /my-students/**
│   ├── permission-matrix.json                 # fonte do teste de acesso cruzado
│   ├── telegram-student-registry.md           # fluxo do bot e ação DELETE_MESSAGE
│   ├── portal-screens.md                      # rotas e telas (admin e Meus alunos)
│   ├── messages.md                            # códigos de erro, HTTP e textos
│   └── initial-load.md                        # carga, bootstrap, recuperação, rotação de chaves
├── checklists/                                # requirements.md, security.md (já existem)
└── tasks.md                                   # Fase 2 (/speckit-tasks — NÃO criado aqui)
```

### Source Code (repository root)

Legenda: **N** novo · **C** alterado. Estrutura existente da 001 a 004 mantida.

```text
backend/
├── core/src/main/java/br/com/felipeblaft/reports/
│   ├── domain/
│   │   ├── access/        Role N · ActionOrigin N · Actor N · Operation N · PermissionMatrix N · Administrator N ·
│   │   │                  AdministratorSession N · ActivationCode N · PasswordPolicy N · AttemptScope N · AttemptPolicy N ·
│   │   │                  AuthorshipRecord N · AuthorshipResult N · AccessAttempt C · WebSession C
│   │   ├── registry/      PersonName N · EmailAddress N · BrazilianPhone N · BirthDate N · Instrument N · Guardian N ·
│   │   │                  StudentData N · StudentScope N · StudentStatusFilter N · RegistryLimits N · Student C · Teacher C
│   │   ├── telegram/      StudentRegistrationDraft N · ConversationFlow C · ConversationFlowStep C
│   │   ├── channel/       ChannelActionType C (DELETE_MESSAGE) · ChannelAction C (compensação)
│   │   └── common/        ErrorCode C
│   ├── port/              AdministratorRepository N · AdministratorSessionRepository N · PasswordHashPort N ·
│   │                      AuthorshipRecorderPort N · StudentRegistrationDraftRepository N · ActivationCodeGeneratorPort N ·
│   │                      AccessAttemptRepository C · DataProtectionPort C · StudentRepository C · TeacherRepository C ·
│   │                      AssignmentRepository C · WebSessionRepository C · ChannelActionRepository C · TransactionPort C ·
│   │                      OpenReportsQueryPort C
│   ├── service/           ActorAuthorizationService N · StudentAccessService N · AuthorshipService N ·
│   │                      TelegramLinkTerminationService N · SessionRevocationService N · ActivationCodeIssuanceService N ·
│   │                      AccessAttemptsControlService C · CodeAuthenticationService C
│   └── usecase/
│       ├── administrator/ SignIn · Validate/Get/SignOut Session · Activate · ChangeProvisionalPassword · Bootstrap ·
│       │                  List/Create/Inactivate/Reactivate  (…UseCase)  N
│       ├── teacher/       List · Get · Register · Update · Inactivate · Reactivate · ReissueAccessCode · UnlinkTelegram  N
│       ├── registry/      ListStudents · GetStudent · RegisterStudent · UpdateStudent · Inactivate/ReactivateStudent ·
│       │                  AssignTeacherToStudent · RemoveTeacherFromStudent · PreviewAssignmentRemoval ·
│       │                  Start/Collect/Confirm/CancelStudentRegistration  N
│       ├── access/        ValidateSessionWebUseCase C · SignInPortalUseCase C
│       ├── seed/          LoadInitialRegistryUseCase C (orquestra) · RotateCpfKeysUseCase N · EmitAccessCodeUseCase removido
│       └── home|lesson|semester/   filtros de aluno ativo / associação (R-23) C
├── application/src/main/java/br/com/felipeblaft/reports/
│   ├── inbound/web/admin/        AdminSessionController · AdminTeacherController · AdminAdministratorController ·
│   │                             AdminStudentController · AdminAssignmentController · DTOs/Mappers  N
│   ├── inbound/web/security/     AdminSecurityConfig N · AdminSessionFilter N · AdminSessionCookie N ·
│   │                             AuthenticatedAdministrator N · CrossCredentialAnswerer N · SecurityConfig C
│   ├── inbound/web/              MyStudentController N · DTOs/Mapper N · ErrorHandlerController C
│   ├── inbound/telegram/         TelegramStudentRegistryFlow N · TelegramKeywords C · TelegramPresenter C ·
│   │                             TelegramConversationInterpreter C · TelegramOutput C (DeleteMessage)
│   ├── inbound/telegram/channel/ ChannelActionMapper C · ActionDto C (DELETE_MESSAGE)
│   ├── inbound/bootstrap/        FirstAdministratorBootstrap N
│   ├── inbound/seed/             SeedCommand C · IssueCodeCommand C · DemoDataCommand C · RotateCpfKeyCommand N
│   ├── outbound/persistence/     Administrator*/AuthorshipRecord*/StudentRegistrationDraft* (Entity, Jpa, Adapter) N ·
│   │                             AccessAttempt* C · Student* C · TeacherRepositoryAdapter C · AssignmentRepositoryAdapter C ·
│   │                             WebSession* C · TelegramLinkRepositoryAdapter C · ChannelActionRepositoryAdapter C ·
│   │                             OpenReportsQueryAdapter C · TransactionAdapter C · AccessSweepTask C · StudentDraftSweepTask N
│   ├── outbound/security/        Argon2PasswordHashAdapter N · ActivationCodeGeneratorAdapter N · DataProtectionAdapter C ·
│   │                             CpfKeyRotationAdapter N
│   ├── config/                   AdministrationUseCaseConfig N · TeacherManagementUseCaseConfig N · StudentRegistryUseCaseConfig N ·
│   │                             ApplicationProperties C · UseCaseConfig C · log/SensitiveDataMaskingConverter C
│   └── resources/                db/migration/V9…V14 N · messages/messages_pt_BR.properties C · application.yaml C
└── (testes espelham o pacote; `architecture/`, `parity/`, `privacy/`, `access/` novos onde indicado)

frontend/src/
├── app/            AdminSessionGuard N · AdminLayout N · adminRoutes.tsx N · myStudentsRoutes.tsx N · routes.tsx C
├── pages/admin/    AdminSignIn · Activate · ChangePassword · Home · Teachers · TeacherDetail · TeacherForm ·
│                   Administrators · Students · StudentForm · StudentDetail  (…Screen.tsx)  N
├── pages/          MyStudentsScreen N · MyStudentFormScreen N · MyStudentDetailScreen N · SignInScreen C · DashboardScreen C
├── components/     admin/ (OneTimeSecretPanel, AdminNav, SessionClock) N · student/ (StudentForm, StudentList, SituationBadge) N
├── services/       administration-api-types.ts (gerado, ignorado) · admin/*.ts N · myStudents.ts N · apiClient.ts C
├── messages/       messages.json (gerado)
└── styles/         admin.css N (só tokens existentes)

infra/             .env.example C · scripts/local.sh C · seed/example.json, demo.json C · README.md C ·
                   n8n/workflows/execute-actions.json C · telegram-simulator/ (deleteMessage) C · tests/n8n C
specs/             001 (nota em POST /session) C · 002 (actions-v1: DeleteMessageAction) C · 004 (n8n-workflows.md: deleteMessage) C · CLAUDE.md (linha da 005) C
.specify/memory/   glossary.json C
```

**Structure Decision**: aplicação web com backend hexagonal e SPA já existentes; a feature só acrescenta pacotes por
contexto (`administrator`, `teacher`, `registry`) no núcleo, adaptadores de entrada separados por papel
(`inbound/web/admin` × `inbound/web`), e módulos de rotas separados na SPA. Nenhuma pasta de primeiro nível ou módulo
Maven novo, logo nenhum `CLAUDE.md` novo é exigido; os `CLAUDE.md` existentes são atualizados no fecho.

## Execução em paralelo por subagentes

As tarefas serão executadas por subagentes simultâneos. Para que isso funcione sem conflito, o plano separa **uma fundação
sequencial** de **fluxos de trabalho (W-*) com propriedade disjunta de arquivos**. O `/speckit-tasks` deve gerar as
tarefas agrupadas por fluxo, marcar `[P]` o que roda em paralelo e respeitar as dependências abaixo.

### Regras de convivência (valem para todos os fluxos)

1. **Um único escritor por arquivo.** A coluna "Dono de" da tabela é exclusiva; um fluxo que precisa de mudança em arquivo de
   outro pede ao dono (ou ao coordenador) em vez de editar.
2. **Arquivos compartilhados só na fundação**: `ErrorCode`, `messages_pt_BR.properties`, `ErrorHandlerController` (mapa de
   status), `ApplicationProperties`/`application.yaml`/`.env.example`, `glossary.json`, `UseCaseConfig` (cada fluxo ganha o seu
   `*UseCaseConfig`), `routes.tsx`, `apiClient.ts`, ports, `Operation`, `PermissionMatrix`, esqueleto de todos os casos de uso.
3. **Assinaturas congeladas** ao fim da fundação (classes de comando/resultado dos casos de uso, métodos das portas,
   OpenAPI). Mudança de assinatura = decisão do coordenador, comunicada a todos os fluxos que dela dependem.
4. **Cada fluxo escreve os próprios testes e os próprios dublês** (`InMemory…Fake` com nome do fluxo), mantém a cobertura do
   que criou e roda só as suas suítes (`local.sh tests backend|frontend`, escopo por módulo) mais os guardiões de idioma.
5. **Migrações com número fixo por fluxo** (V9 a V14), sem renumerar; nome do arquivo em inglês.
6. Cada fluxo trabalha em **worktree própria** a partir da `main` com a fundação mesclada; o coordenador integra por fatia.
7. Nenhum fluxo mescla PR nem habilita merge automático (Princípio VIII); ao fim de cada fatia roda-se `pre-pr-review` e o coordenador abre o PR da fatia, sem esperar nova solicitação.

### Fatias (um PR por fatia, a partir da `main` atualizada)

| Fatia | Conteúdo | Pré-requisito de merge |
|---|---|---|
| **S1 — Fundação** | fluxo F (esqueleto compilável, arquivos compartilhados, contratos, glossário) | PR #28 já mesclado; este plano aprovado |
| **S2 — Núcleo e persistência** | W-A, W-B, W-C1, W-C2, W-D, W-E, W-F, W-G, W-K | S1 mesclada |
| **S3 — Canais** | W-H, W-I, W-J, W-L (adaptadores testados contra dublês dos casos de uso) | S1 mesclada (compila contra o esqueleto) |
| **S4 — Frontend** | W-M, W-N (testados contra o contrato OpenAPI, sem backend) | S1 mesclada |
| **S5 — Fecho** | X-1 integração e testes transversais, X-2 e2e + axe, X-3 documentação, X-4 revisão | S2, S3 e S4 mescladas |

S2, S3 e S4 são independentes entre si (compilam contra a fundação) e podem ser desenvolvidas ao mesmo tempo; a integração
real acontece em S5.

### Fundação F (sequencial, um agente — bloqueia o resto)

Entrega um build **verde** com tudo o que é compartilhado e o esqueleto das assinaturas:

- **F1** `glossary.json` com os termos de `data-model.md` §10; `ErrorCode` (+ remoção de `EXPIRED_CODE` e `STUDENT_NOT_ASSIGNED`, com `AuthorizationService` passando a lançar `RESOURCE_NOT_FOUND`), status HTTP em
  `ErrorHandlerController`, textos de `contracts/messages.md` em `messages_pt_BR.properties` (o teste do catálogo passa);
  remoção das ocorrências de "secretaria" nos textos do professor.
- **F2** `ApplicationProperties`/`application.yaml`/`.env.example`/passagem de variáveis no compose (nomes em R-04, R-06, R-16,
  quickstart §1); dependência BouncyCastle no `pom.xml` (versão fixada).
- **F3** Núcleo: `Role`, `ActionOrigin`, `Actor`, `Operation`, `PermissionMatrix` (**completa**, com o teste de deriva contra
  `permission-matrix.json`), `AttemptScope`/`AttemptPolicy`, `StudentScope`/`StudentStatusFilter`, `RegistryLimits`, assinaturas
  das portas (novas e alteradas) e **esqueleto de todos os casos de uso** (classe, comando/resultado, corpo
  `UnsupportedOperationException`), mais `Student`/`Teacher` com os métodos de estado triviais.
- **F4** Adaptadores com **stubs compiláveis** das portas alteradas; `AdministrationUseCaseConfig`,
  `TeacherManagementUseCaseConfig` e `StudentRegistryUseCaseConfig` (beans de todos os casos de uso) e a regra ArchUnit
  nova (Role = 2; `Actor.system` só em `inbound.seed`/bootstrap; `inbound.telegram` sem referência ao administrador).
- **F5** Frontend: segundo gerador de tipos (`generate:administration-api`), `adminRoutes.tsx`/`myStudentsRoutes.tsx`
  (listas vazias) ligados em `routes.tsx`, interface de props de `StudentForm` e do `OneTimeSecretPanel`, hooks de erro/evento de
  sessão do administrador em `apiClient.ts`.
- **Saída (gate)**: `tests backend` e `tests frontend` verdes (testes existentes sem alteração de expectativa), guardiões de
  idioma verdes, cobertura do núcleo não regride, contratos validados por `redocly`/validador OpenAPI.

### Fluxos de trabalho (todos dependem só de F, salvo onde dito)

| Fluxo | Dono de (arquivos exclusivos) | Entrega e testes | Depende de |
|---|---|---|---|
| **W-A** núcleo: acesso do administrador | `domain/access/Administrator*`, `ActivationCode`, `PasswordPolicy`; `service/ActivationCodeIssuanceService`; `usecase/administrator/*`; `AdministrationUseCaseConfig` (corpos) | entrar/validar/sair, ativar, trocar senha provisória, bootstrap, listar/criar/inativar/reativar administrador (último administrador, R-15); dublês `InMemoryAdministrator*` | F |
| **W-B** núcleo: professores e revogação | `usecase/teacher/*`; `service/SessionRevocationService`, `TelegramLinkTerminationService`; ajustes em `CodeAuthenticationService`, `ValidateSessionWebUseCase`, `SignInPortalUseCase`, `LinkTelegramUseCase`, `EndTelegramLinkUseCase`, `domain/access/WebSession`, `domain/registry/Teacher`; `TeacherManagementUseCaseConfig` | CRUD de professor, reemitir código (opção Telegram), desvincular, inativar/reativar com todos os efeitos (R-08); `ACCESS_ENDED`; `EXPIRED_CODE` removido | F |
| **W-C1** núcleo: alunos (cadastro e consulta) | `domain/registry/*` (valores, `StudentData`, `Student`), `service/StudentAccessService`; `usecase/registry/{List,Get,Register,Update}StudentUseCase`; probe de CPF; `StudentRegistryUseCaseConfig` (corpos) | validação completa (R-11), duplicidade e CPF alheio (R-13), escopo/indistinguibilidade (R-14), sondagem (R-06); autoria dessas operações | F |
| **W-C2** núcleo: situação e associações | `usecase/registry/{Inactivate,Reactivate}StudentUseCase`, `{Assign,Remove}…`, `PreviewAssignmentRemovalUseCase`; ajustes de 001: `home/*`, `lesson/RegisterLessonByAudioUseCase`, `semester/Generate…`, `OpenReportsQueryPort` | regra FR-026 (único professor ativo), não bloqueio, congelamento (R-23), aluno inativo fora de novas aulas | F |
| **W-D** núcleo: rascunho do Telegram | `domain/telegram/StudentRegistrationDraft`; `usecase/registry/{Start,Collect,Confirm,Cancel}StudentRegistration*UseCase` | início por tipo (`CREATE`, `UPDATE`, `INACTIVATE`, `REACTIVATE`) com escopo do professor, coleta campo a campo, token de uso único, prazo, dono, atomicidade com `RegisterStudent`/`UpdateStudent`/`InactivateStudent`/`ReactivateStudent` | F |
| **W-E** persistência: administradores e sessões | `V9`, `V14`; `Administrator*`/`AdministratorSession*` (Entity/Jpa/Adapter); `WebSessionRepositoryAdapter`, `TeacherRepositoryAdapter`, `AccessSweepTask`; `Argon2PasswordHashAdapter`, `ActivationCodeGeneratorAdapter`, `DataProtectionAdapter` | repositórios, `lockAllActiveForUpdate`, revogação, Argon2 (parâmetros, dummy, rehash); testes com Postgres real | F |
| **W-F** persistência: autoria e tentativas | `V10`, `V11`; `AuthorshipRecord*`; `AccessAttempt*` (lock de linha, janela, escopo); `SensitiveDataMaskingConverter` | somente acréscimo (gatilho), modos `record`/`recordIndependent`, concorrência das tentativas | F |
| **W-G** persistência: alunos, rascunho, associações | `V12`; `Student*`, `StudentRegistrationDraft*`, `AssignmentRepositoryAdapter`, `TelegramLinkRepositoryAdapter` (cascata), `OpenReportsQueryAdapter`, `TransactionAdapter.runAndRollBack`, `StudentDraftSweepTask`; `ChannelActionRepositoryAdapter.discardPendingByChat` | `insertIfAbsent`, consultas escopadas, contagens, cascata do rascunho; testes com Postgres real | F |
| **W-H** Web: administração | `inbound/web/admin/*`, `inbound/web/security/Admin*`, `CrossCredentialAnswerer`, `SecurityConfig`, `ErrorHandlerController` (só lógica, não o mapa) | 5 controllers, cadeia de segurança, cookie, CSRF, `Cache-Control: no-store`, 404 de credencial cruzada, origem; contrato OpenAPI e MockMvc contra dublês | F |
| **W-I** Web: Meus alunos | `inbound/web/MyStudentController`, DTOs, mapper | rotas `/my-students/**` (404 uniforme, 403 compartilhado, 429 sondagem); contrato e MockMvc | F |
| **W-J** Telegram: fluxo do professor | `inbound/telegram/TelegramStudentRegistryFlow`, `TelegramKeywords`, `TelegramPresenter`, `TelegramConversationInterpreter`, `TelegramOutput`, `ConversationFlow*`; catálogo de textos já na fundação | conversa guiada, lista paginada, edição, confirmação, negativa neutra, grupos recusados; testes `TelegramConversationBase`, `parity/` do fluxo | F, W-L (só para o tipo de ação) |
| **W-K** carga, bootstrap, rotação | `inbound/bootstrap/*`, `inbound/seed/*`, `usecase/seed/*`, `CpfKeyRotationAdapter`, `infra/seed/*.json`, `scripts/local.sh` | `LoadInitialRegistryUseCase` por casos de uso e `--simulate` por rollback, `issue-code`, bootstrap (4 situações), `rotate-cpf-key`; testes de comando | F |
| **W-L** transporte: `DELETE_MESSAGE` | `domain/channel/ChannelAction*` (compensação), `V13`, `inbound/telegram/channel/*`, `actions-v1.schema.json`, `infra/n8n/workflows/execute-actions.json`, `infra/telegram-simulator`, `infra/tests/n8n` | ação, falha `MESSAGE_NOT_DELETABLE` + compensação, guardiões do n8n, contrato 002 | F |
| **W-M** frontend: área administrativa | `pages/admin/*`, `components/admin/*`, `services/admin/*`, `app/AdminSessionGuard`, `AdminLayout`, `adminRoutes.tsx`, `styles/admin.css` | A01–A12, sessão fixa com relógio, `OneTimeSecretPanel`, confirmações, estados; Vitest contra o contrato (sem backend) | F, W-N (só `StudentForm` real) |
| **W-N** frontend: Meus alunos | `pages/MyStudents*`, `components/student/*`, `services/myStudents.ts`, `myStudentsRoutes.tsx`, `SignInScreen`, `DashboardScreen` | P01–P03, `StudentForm` compartilhado, link do FR-043, atalho; Vitest | F |

**Fecho (S5)**: **X-1** testes transversais (acesso cruzado gerado da matriz; indistinguibilidade com tempo; CPF canário;
concorrência; paridade; contrato × resposta real; authorship por operação — SC-004 a SC-007, SC-009, SC-010, SC-014);
**X-2** e2e Playwright + axe das 15 telas, claro/escuro, 390/1440 px (SC-012); **X-3** documentação (`CLAUDE.md` de
`backend/core`, `backend/application`, `frontend`, `infra`, `specs`, `.claude`; `infra/README.md` com recuperação e
rotação; nota no contrato da 001 e na 002); **X-4** `pre-pr-review` (backend-reviewer, frontend-reviewer,
infra-security-auditor, contract-drift-checker, privacy-auditor, constitution-auditor).

### Grafo de dependências

```text
F ──┬─ W-A ─┐
    ├─ W-B ─┤
    ├─ W-C1─┤                       S2 (núcleo + persistência + carga)  ──┐
    ├─ W-C2─┤                                                             │
    ├─ W-D ─┤   W-E, W-F, W-G, W-K ────────────────────────────────────────┤
    │       │                                                              ├──► X-1, X-2, X-3 ──► X-4
    ├─ W-L ─┬─ W-J                  S3 (canais)  W-H, W-I ────────────────┤
    └─ W-N ─┴─ W-M                  S4 (frontend)  ────────────────────────┘
```

### Primeiras tarefas de verificação (antes de codar, dentro dos próprios fluxos)

1. **W-L**: descobrir o nome real da `CHECK` do tipo de ação após `V6`/`V7`/`V8` e confirmar que `ChannelActionRepository` aceita o novo tipo.
2. **W-B / W-G**: confirmar se alguém já descarta ações `PENDING` ao remover o vínculo (`ChannelAction.discard` não tem chamador fora do domínio) e,
   se não, implementar em `TelegramLinkTerminationService`.
3. **W-C2**: confirmar que `OpenReportsQueryAdapter.listTeacherOpenReports` não filtra por associação nem por professor ativo (R-23) e cobrir com teste.
4. **W-H**: confirmar com teste que `X-Forwarded-For` forjado não altera a origem (R-05) e que o 404 cruzado é byte a byte igual ao de rota inexistente.
5. **F / W-E**: obter a aprovação da dependência BouncyCastle ou acionar a contingência PBKDF2 (R-04).
6. **W-J**: confirmar que callbacks (e não só mensagens) são recusados fora de conversa privada (FR-036).

## Complexity Tracking

> Acréscimos que a constituição ou a spec não exigem por si, mas que o desenho precisa, com a alternativa mais simples rejeitada.

| Violation / acréscimo | Why Needed | Simpler Alternative Rejected Because |
|---|---|---|
| Dependência nova `bcprov-jdk18on` (Argon2id) | FR-004: hash adaptativo para senha de até 128 caracteres | bcrypt trunca em 72 bytes; PBKDF2 é mais fraco (mantido como contingência, R-04) |
| Tabelas novas `administrator_session` e `authorship_record` (esta com gatilho de somente acréscimo) | sessão fixa e revogável por papel (R-03); trilha atômica e imutável exigida pelo FR-040 | reaproveitar `web_session` mistura expirações e FKs; log de aplicação não é registro estruturado nem imutável. O gatilho é guarda de integridade, não regra de negócio |
| Segunda `SecurityFilterChain` + cookie `Path`-restrito | FR-003: cada área aceita só a sua credencial; credencial cruzada = rota inexistente | `hasRole` em uma cadeia única revela a rota (403) e mistura credenciais |
| Casos de uso que orquestram casos de uso (`LoadInitialRegistryUseCase`, `ConfirmStudentRegistrationUseCase`) | FR-044 exige que a carga use as mesmas operações; a confirmação do bot precisa ser atômica com o cadastro | duplicar as regras (vetado) ou encadear no adaptador (regra no adaptador, vetado pelo Princípio I) |
| Tabela `student_registration_draft` própria | CPF cifrado fora do JSON genérico, prazo e varredura independentes, cascata com o vínculo (R-21) | `TelegramConversation.data` guardaria CPF e contato em JSON sem prazo próprio |
| Ação `DELETE_MESSAGE` no contrato 002/004 | FR-036: tentar apagar a mensagem com o CPF; o backend não chama a API de bots (Princípio II) | não apagar apenas avisando (a spec manda tentar) |
| Fundação sequencial antes do paralelismo | evita conflito em `UseCaseConfig`, `ErrorCode`, catálogo e rotas, e retrabalho por assinatura que muda (R-26) | paralelizar sem congelar assinaturas |
