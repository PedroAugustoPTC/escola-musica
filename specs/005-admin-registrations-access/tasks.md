---

description: "Lista de tarefas da feature 005 — Administração: acesso e cadastros"
---

# Tasks: Administração — acesso e cadastros (administrador na Web; professor na Web e no Telegram)

**Input**: documentos de `specs/005-admin-registrations-access/` (`spec.md`, `plan.md`, `research.md`, `data-model.md`, `quickstart.md`, `contracts/`, `checklists/`) e a constituição v2.2.0 (`.specify/memory/constitution.md`).

**Pré-requisitos**: PR #28 (constituição v2.2.0) mesclado na `main`; este `plan.md` aprovado.

**Testes**: **solicitados** (TDD). Em toda história os testes vêm **antes** da implementação e **devem falhar** (vermelho) antes de o código existir: contrato, paridade entre canais, segurança (credencial cruzada, bloqueio, CPF fora de logs e respostas), isolamento entre professores, ausência de papel/credencial/rota para aluno ou responsável, e acessibilidade.

**Organização**: fases por história de usuário, na ordem pedida (US1 a US8). Cada história entrega **Web e Telegram juntos** onde a spec manda (professor: Web e Telegram com os mesmos casos de uso; administrador: somente Web, com negativa neutra no Telegram) e é testável de forma independente.

## Formato: `- [ ] T### [P?] [US#?] **FLUXO** Descrição com caminho do arquivo`

- **[P]**: pode rodar em paralelo com outras tarefas [P] — arquivo(s) exclusivo(s) e nenhuma dependência de tarefa ainda aberta, **salvo** a que vier escrita como "(após T###)". Sem [P]: depende da tarefa anterior do mesmo fluxo ou da dependência declarada.
- **[US#]**: história da spec (US1 a US8). Fases de Setup, Fundação, Carga inicial e Fecho não levam rótulo.
- **FLUXO** (em negrito): o **dono** da tarefa = o subagente que a executa (ver "Execução por subagentes"). Um fluxo é dono exclusivo dos arquivos que cria ou altera; ninguém edita arquivo de outro fluxo.
- Nomes de arquivo, classe, tabela, rota e código de erro em **inglês**; textos de tela, de bot e de log em **português acentuado** (Princípio VI). Antes de usar palavra portuguesa nova em identificador, rode o guardião com `GUARD_REPORT=1` (ver `.specify/CLAUDE.md`).

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
| `SPEC/` | `specs/005-admin-registrations-access/` |

Testes de integração terminam em `IT` e usam Postgres real quando a tarefa disser "Postgres real" (convenção da 001). Todos os comandos rodam em contêiner: `sh infra/scripts/local.sh tests backend|frontend|e2e|n8n|all`.

---

## Execução por subagentes (leia antes de distribuir as tarefas)

### Regras de convivência

1. **Um fluxo = um subagente = uma worktree própria**, criada da `main` **depois** da fatia S1 (Fundação) mesclada. O dono de um arquivo é o único que o escreve; precisando de mudança em arquivo alheio, o subagente pede ao coordenador (`COORD`) em vez de editar.
2. **Mesmo fluxo, várias histórias**: as tarefas de um fluxo seguem a **ordem de ID** (testes primeiro). Dois fluxos diferentes nunca disputam arquivo, então rodam ao mesmo tempo; duas histórias diferentes também podem rodar ao mesmo tempo **desde que cada uma esteja em fluxos distintos** ou o mesmo subagente as execute em sequência.
3. **Assinaturas congeladas** pela Fundação (comandos/resultados dos casos de uso, métodos das portas, OpenAPI, enum `Operation`, `ErrorCode`, catálogo de mensagens). Mudança de assinatura = decisão do `COORD`, avisada a todos os fluxos dependentes.
4. **Dublês próprios**: cada fluxo cria os dublês `InMemory…` dos portos que só ele usa. Os dublês compartilhados (`FakeAuthorshipRecorder`, `FakePasswordHash`, `ActorFixtures`, `InMemory*` dos repositórios existentes e `support/*`) pertencem a `F-TEST` e já chegam prontos.
5. **Casos de uso que chamam outros casos de uso** (`ConfirmStudentRegistrationUseCase`, `LoadInitialRegistryUseCase`) são testados com **dublê do caso de uso chamado** (o esqueleto da Fundação existe); a integração real acontece no Fecho.
6. Cada fluxo roda **só as suas suítes** mais os guardiões de idioma (`GUARD_REPORT=1` antes do PR) e mantém a cobertura do núcleo ≥ 90% (linha e branch) no que criou.
7. Nenhum subagente mescla PR nem habilita merge automático (Princípio VIII). Ao fim de cada fatia: `pre-pr-review`; em seguida o `COORD` abre o PR da fatia para a `main` com `/open-pr`, sem esperar nova solicitação (Princípio VIII); depois de **cada** PR, `code-review-graph build`.

### Fluxos, fatias e o que cada subagente pode executar sozinho

Pré-condição de todos os fluxos `W-*`, `X-1` e `X-2`: **S1 (Fundação, T004 a T034) mesclada**. As fatias S2, S3 e S4 são independentes entre si.

| Fluxo | Fatia | Dono de (arquivos exclusivos) | Tarefas (IDs) | Roda em paralelo com |
|---|---|---|---|---|
| **COORD** | — | git, decisões, gates, PRs | T001–T003, T034, T073, T106, T126, T159, T171, T183, T199, T210, T217, T234, T235 | tudo (só coordena) |
| **F-CFG** | S1 | `glossary.json`, `ApplicationProperties`, `application.yaml`, `.env.example`, compose, `pom.xml` | T004–T007 | F-FRONT, F-TEST (parte), F-CORE |
| **F-CORE** | S1 | `CORE/domain`, `CORE/port`, `CORE/service` (Fundação), `CORE/usecase` (esqueletos) | T008–T017, T021 | F-CFG, F-FRONT |
| **F-APP** | S1 | stubs de adaptadores, `*UseCaseConfig`, regras ArchUnit, catálogo e mapa de status | T022–T026 | F-FRONT (após F-CORE) |
| **F-TEST** | S1 | `APP_T/support`, `CORE_T/doubles`, `FE_T/support`, `FE_T/utils.tsx`, `FE_T/e2e/support.ts` | T018–T020, T032, T033 | F-CFG, F-FRONT |
| **F-FRONT** | S1 | geração de tipos, `adminRoutes`, `myStudentsRoutes`, `routes.tsx`, `apiClient.ts`, interfaces de props | T027–T031 | F-CFG, F-CORE |
| **W-A** | S2 | núcleo do acesso do administrador (`domain/access/Administrator*`, `usecase/administrator/*`) | T035–T037, T058, T059, T184, T194, T200, T206 | todos os demais `W-*` |
| **W-B** | S2 | núcleo de professores e revogação (`usecase/teacher/*`, `SessionRevocationService`, `TelegramLinkTerminationService`) | T074–T079, T093–T097, T172, T179 | idem |
| **W-C1** | S2 | núcleo de alunos: valores, `StudentData`, listar/abrir/cadastrar/editar | T107, T108, T118, T119, T127, T149 | idem |
| **W-C2** | S2 | núcleo de situação, associações e congelamento (`Inactivate/Reactivate/Assign/Remove/Preview`, `home/*`, `OpenReportsQueryPort`) | T081, T098, T109, T120, T128, T150, T160, T167 | idem |
| **W-D** | S2 | núcleo do rascunho do Telegram (`StudentRegistrationDraft`, `Collect/Confirm/Cancel…`) | T129, T151 | idem |
| **W-E** | S2 | persistência de administradores, sessões, Argon2, `V9`, `V14` | T042–T044, T064–T067, T082, T099, T100, T185, T195 | idem |
| **W-F** | S2 | tentativas (`AccessAttemptsControlService`, adaptadores), autoria, `V10`, `V11`, máscara de log | T038–T041, T060–T063, T186, T196 | idem |
| **W-G** | S2 | persistência de alunos, rascunho, associações, vínculo, `V12`, varredura | T083, T101, T110, T111, T121, T122, T130, T152, T161, T168 | idem |
| **W-K** | S2 | carga inicial, bootstrap, rotação de chaves, `inbound/seed`, `inbound/bootstrap`, `infra/seed`, `local.sh` | T201, T207, T211, T212, T214–T216 | idem |
| **W-H** | S3 | Web da administração (`inbound/web/admin/*`, `inbound/web/security/Admin*`) | T045–T047, T068, T069, T084, T102, T112, T123, T162, T169, T173, T180, T187, T197, T202, T208 | idem |
| **W-I** | S3 | Web "Meus alunos" (`inbound/web/MyStudent*`) | T133, T155 | idem |
| **W-J** | S3 | fluxo do professor no Telegram (`inbound/telegram/*`) | T054, T070, T085, T086, T103, T134, T135, T156, T164, T174, T181, T191 | idem |
| **W-L** | S3 | transporte `DELETE_MESSAGE` (`domain/channel`, `V13`, schema 002, n8n, simulador) | T131, T132, T153, T154 | idem |
| **W-M** | S4 | frontend da área administrativa | T055, T056, T071, T072, T090, T091, T104, T105, T116, T125, T165, T170, T177, T182, T192, T198, T204, T209 | idem |
| **W-N** | S4 | frontend "Meus alunos" e componentes de aluno | T115, T124, T144, T145, T157, T158 | idem |
| **X-1** | S2…S5 | testes transversais de backend (acesso cruzado, isolamento, privacidade, paridade, autoria, concorrência) | T048–T053, T080, T087–T089, T113, T114, T136–T143, T148, T163, T175, T176, T188–T190, T203, T213, T218–T221, T236, T237 | os `W-*` (escreve os testes antes; roda no Fecho) |
| **X-2** | S4/S5 | testes e2e Playwright + axe | T057, T092, T117, T146, T147, T166, T178, T193, T205, T222 | idem |
| **X-3** | S5 | documentação (`CLAUDE.md`, `README`, notas nas specs 001/002) | T223–T232 | X-1, X-2 |
| **X-4** | S5 | revisão (agentes revisores, somente leitura) | T233 | — |

### Ondas

- **Onda 0**: Setup (T001 a T003), em paralelo.
- **Onda 1 (S1, Fundação)**: `F-CFG` ∥ `F-FRONT` ∥ `F-TEST` (parte sem dependência) ∥ `F-CORE`; depois `F-APP` e o restante de `F-TEST` (após os portos de `F-CORE`). Termina no gate T034. **Bloqueia todo o resto.**
- **Onda 2 (S2, S3, S4 ao mesmo tempo)**: todos os `W-*` e os redatores de teste `X-1`/`X-2` começam juntos, cada um em sua worktree, executando suas tarefas por ordem de ID (testes vermelhos primeiro).
- **Onda 3 (S5, Fecho)**: integração real, testes transversais verdes, e2e, documentação, revisão.

### Dependências entre fluxos que sobram (todas declaradas nas tarefas)

- `W-M` (páginas de aluno da US3) depende do `StudentForm` real de `W-N` (T124); até lá `W-M` trabalha contra a interface de props da Fundação.
- `W-D` e `W-K` usam dublê dos casos de uso que orquestram (regra 5).
- `W-J` só precisa do tipo `DELETE_MESSAGE` do enum `ChannelActionType`, que a Fundação já acrescenta (T011).
- **Integração real (não bloqueia o desenvolvimento)**: `W-J` (T156) usa o resultado de `W-D` (T151) e o mapeador de ações de `W-L` (T153); `W-K` (T207) usa o caso de uso de `W-A` (T206); `W-K` (T214) usa os casos de uso de `W-C1` (T119) e `W-B` (T094). Cada um desenvolve e testa contra o dublê do que consome (regra 5) e só integra no Fecho.

---

## Phase 1: Setup (decisões e linha de base)

- [ ] T001 **COORD** Confirmar que `.specify/memory/constitution.md` está na v2.2.0 na `main` (PR #28 mesclado) e criar a branch da fatia S1 a partir da `main` atualizada (`git checkout main && git pull --ff-only origin main`). Cada fatia (S1 a S5) usa branch e PR próprios (Princípio VIII).
- [x] T002 [P] **COORD** (decidido em 2026-10-20: Argon2id com `bcprov-jdk18on`; PBKDF2 não será usada) Pedir ao usuário a decisão sobre a dependência `org.bouncycastle:bcprov-jdk18on` (Argon2id) ou a contingência PBKDF2 (R-04) e registrar a decisão em `SPEC/research.md` (seção R-04).
- [ ] T003 [P] **COORD** Linha de base: rodar `sh infra/scripts/local.sh tests backend` e `tests frontend`; anotar cobertura do núcleo, contagem de testes e resultado do guardião de idioma em `SPEC/evidence/baseline.md`.

---

## Phase 2: Foundational — Fundação F (fatia S1; bloqueia todas as histórias)

**Propósito**: build **verde** com tudo o que é compartilhado e o esqueleto compilável das assinaturas. Cinco subagentes (`F-CFG`, `F-CORE`, `F-APP`, `F-TEST`, `F-FRONT`) trabalham ao mesmo tempo respeitando as dependências "(após …)".

**⚠️ CRÍTICO**: nenhuma história começa antes do gate T034.

**Ajuste ao plano (a confirmar pelo coordenador)**: `ActorAuthorizationService`, `AuthorshipService` e `StudentAccessService` são pequenos e usados por todos os casos de uso; por isso são **implementados na Fundação** (e não por `W-C1`/`W-F`), para que nenhum fluxo dependa de outro. `EXPIRED_CODE` é removido na Fundação com a correção mínima de compilação; o comportamento novo é de `W-B`. `STUDENT_NOT_ASSIGNED` também é removido na Fundação (FR-050): a troca por `RESOURCE_NOT_FOUND` é de uma linha por ponto de uso e os testes existentes mudam de expectativa de uma vez, para que nenhum fluxo herde um `ErrorCode` removido.

### Configuração e glossário (F-CFG)

- [ ] T004 [P] **F-CFG** Registrar em `.specify/memory/glossary.json` os termos de `SPEC/data-model.md` §10 **antes de qualquer uso**: ativacao→activation, autoria→authorship, autor→author, papel→role, telefone→phone, revogacao→revocation, sondagem→probing, correlacao→correlation, desvincular/desvinculo→unlink, congelado→frozen, inativar→inactivate, reativar→reactivate, escopo→scope, permissao→permission, matriz→matrix, inicializacao→startup (só os que ainda não existirem; rascunho e responsavel já existem; não duplicar). Rodar o guardião (`GUARD_REPORT=1`) e confirmar que os contratos novos de `SPEC/contracts/`, o `.env.example` e os scripts passam.
- [ ] T005 [P] **F-CFG** Acrescentar as propriedades novas em `APP/config/ApplicationProperties.java` e `RES/application.yaml`: `reports.administrator.session-hours` (2), `reports.administrator.bootstrap.{name,email,password}` (vazias), parâmetros Argon2 (memória 19456 KiB, iterações 2), `reports.access.cpf-probe.{max-refusals,window-minutes,lock-minutes}` (5/15/15), `reports.access.cpf-lookup.{max-lookups,window-minutes,lock-minutes}` (30/60/60), `reports.administrator.provisional-password-days` (7), `reports.telegram.student-draft-minutes` (30) e o fuso da escola (`America/Sao_Paulo`, se ainda não existir). Nomes exatos em `SPEC/research.md` (R-04, R-06, R-16) e `SPEC/quickstart.md` §1. Garantir que `OrphanConfigurationGuardTest` continue verde (propriedade declarada precisa de uso ou de exceção documentada).
- [ ] T006 [P] **F-CFG** Acrescentar em `infra/.env.example` as variáveis `ADMIN_BOOTSTRAP_NAME`, `ADMIN_BOOTSTRAP_EMAIL`, `ADMIN_BOOTSTRAP_PASSWORD` e `AUTH_HMAC_SECRET` (**vazias**, sem credencial padrão; o `local.sh up` gera a segunda no ambiente local) e as opcionais com padrão (`ADMIN_SESSION_HOURS`, `ADMIN_PASSWORD_ARGON2_MEMORY_KIB`, `ADMIN_PASSWORD_ARGON2_ITERATIONS`, `ACCESS_CPF_PROBE_MAX_REFUSALS`, `ACCESS_CPF_PROBE_WINDOW_MINUTES`, `ACCESS_CPF_PROBE_LOCK_MINUTES`, `ACCESS_CPF_LOOKUP_MAX`, `ACCESS_CPF_LOOKUP_WINDOW_MINUTES`, `ACCESS_CPF_LOOKUP_LOCK_MINUTES`, `ADMIN_PROVISIONAL_PASSWORD_DAYS`, `TELEGRAM_STUDENT_DRAFT_MINUTES`) com comentário em português; repassá-las ao backend em `infra/compose.yaml`, `infra/compose.dev.yaml` e `infra/compose.prod.yaml`. `ComposeGuardTest` e o guardião de segredos continuam verdes.
- [ ] T007 **F-CFG** (após T002) Acrescentar a dependência `bcprov-jdk18on` com **versão fixada** (gerenciada em `backend/pom.xml`, usada em `backend/application/pom.xml`), ou aplicar a contingência PBKDF2 conforme a decisão registrada. Nenhuma dependência nova no frontend.

### Núcleo: códigos de erro, matriz, tipos e portos (F-CORE)

- [ ] T008 **F-CORE** Em `CORE/domain/common/ErrorCode.java` acrescentar os códigos de `SPEC/data-model.md` §9 e **remover** `EXPIRED_CODE`; corrigir só o necessário para compilar em `CORE/service/CodeAuthenticationService.java` (código vencido passa a `INVALID_CODE`) e nos testes existentes que citam `EXPIRED_CODE`. Comportamento novo (`ACCESS_ENDED` etc.) é de `W-B`. **Remover também `STUDENT_NOT_ASSIGNED`** (FR-050): `CORE/service/AuthorizationService.java` e `CORE/usecase/home/GetStudentSummaryUseCase.java` passam a lançar `RESOURCE_NOT_FOUND`, e as expectativas mudam em `CORE_T/service/AuthorizationServiceTest.java`, `CORE_T/usecase/lesson/RegisterLessonByAudioUseCaseTest.java`, `GetOpenReportUseCaseTest.java`, `CORE_T/usecase/semester/SemesterUseCasesTest.java`, `CORE_T/usecase/home/HomeUseCasesTest.java`, `CORE_T/usecase/history/QueryStudentHistoryUseCaseTest.java` e `CORE_T/usecase/channel/GetChannelDocumentUseCaseTest.java`. Antes de remover o código, conferir se `lesson_report.failure_reason`, `ai_call.error_code` ou `channel_action.reason_code` podem guardá-lo (a `V8` os renomeou); se puderem, levar ao `COORD` a conversão para `RESOURCE_NOT_FOUND` dentro da `V14` (sem migração nova).
- [ ] T009 **F-CORE** Escrever primeiro os testes da matriz: `CORE_T/domain/access/RoleTest.java` (`Role.values().length == 2`: `ADMINISTRATOR`, `TEACHER`), `PermissionMatrixTest.java` (negado por padrão; operação ausente = `DENY`) e `PermissionMatrixDriftTest.java` (compara `PermissionMatrix` com `SPEC/contracts/permission-matrix.json`, linha a linha, nos quatro atores: administrador Web, professor Web, professor Telegram, sem credencial). (após T008)
- [ ] T010 **F-CORE** Implementar em `CORE/domain/access/`: `Role`, `ActionOrigin`, `Actor` (fábricas `administrator`, `teacher`, `system(origin)`; campo `passwordChangeRequired`), `Operation`, `Permission` e `PermissionMatrix` **completa** (`SPEC/data-model.md` §2 e §8). Os testes de T009 ficam verdes. (após T009)
- [ ] T011 **F-CORE** Criar os tipos de apoio: `CORE/domain/access/AttemptScope.java` (`TEACHER_CODE`, `ADMIN_SIGN_IN`, `ADMIN_ACTIVATION`, `TEACHER_CPF_PROBE`, `TEACHER_CPF_LOOKUP`) e `AttemptPolicy.java`; `CORE/domain/registry/StudentScope.java` (`all()`, `ofTeacher(UUID)`), `StudentStatusFilter.java`, `RegistryLimits.java` (constantes de `SPEC/data-model.md` §5, com teste); **esqueletos** (assinatura + `UnsupportedOperationException`) de `PersonName`, `EmailAddress`, `BrazilianPhone`, `BirthDate`, `Instrument`, `Guardian`, `StudentData`; enum `DELETE_MESSAGE` em `CORE/domain/channel/ChannelActionType.java`; `Student`/`Teacher` ganham `inactivate(now)`, `reactivate(now)` e os campos `guardianPhone`/`guardianEmail` (métodos de estado triviais, `toString` sem dado pessoal). (após T008)
- [ ] T012 **F-CORE** Criar os portos novos em `CORE/port/` (`AdministratorRepository`, `AdministratorSessionRepository`, `PasswordHashPort`, `AuthorshipRecorderPort`, `StudentRegistrationDraftRepository`, `ActivationCodeGeneratorPort`) e os esqueletos de domínio `Administrator`, `AdministratorSession`, `ActivationCode`, `PasswordPolicy`, `AuthorshipRecord`, `AuthorshipResult` (`CORE/domain/access/`) e `StudentRegistrationDraft` (`CORE/domain/telegram/`), com as assinaturas de `SPEC/data-model.md` §2, §3 e §7. (após T010, T011)
- [ ] T013 **F-CORE** Alterar as assinaturas dos portos existentes conforme `SPEC/data-model.md` §7: `AccessAttemptRepository`, `DataProtectionPort`, `StudentRepository` (`findVisible`, `list(scope,status)`, `insertIfAbsent`, `searchByCpfHash`), `TeacherRepository`, `AssignmentRepository`, `WebSessionRepository`, `TelegramLinkRepository`, `ChannelActionRepository`, `TransactionPort` (`runAndRollBack`), `OpenReportsQueryPort`. Compilar o `core` ajustando só as chamadas existentes. (após T012)
- [ ] T014 [P] **F-CORE** Esqueletos (classe, comando/resultado, corpo `UnsupportedOperationException`) em `CORE/usecase/administrator/`: `SignInAdministratorUseCase`, `ValidateAdministratorSessionUseCase`, `GetAdministratorSessionUseCase`, `SignOutAdministratorUseCase`, `ActivateAdministratorUseCase`, `ChangeProvisionalPasswordUseCase`, `BootstrapFirstAdministratorUseCase`, `ListAdministratorsUseCase`, `CreateAdministratorUseCase`, `InactivateAdministratorUseCase`, `ReactivateAdministratorUseCase`; serviços `CORE/service/SessionRevocationService.java`, `TelegramLinkTerminationService.java`, `ActivationCodeIssuanceService.java` (esqueletos). (após T013)
- [ ] T015 [P] **F-CORE** Esqueletos em `CORE/usecase/teacher/`: `ListTeachersUseCase`, `GetTeacherUseCase`, `RegisterTeacherUseCase`, `UpdateTeacherUseCase`, `InactivateTeacherUseCase`, `ReactivateTeacherUseCase`, `ReissueAccessCodeUseCase` (substitui `EmitAccessCodeUseCase`; ajustar as referências de `APP/inbound/seed/IssueCodeCommand.java` só para compilar), `UnlinkTeacherTelegramUseCase`. (após T013)
- [ ] T016 [P] **F-CORE** Esqueletos em `CORE/usecase/registry/`: `ListStudentsUseCase`, `GetStudentUseCase`, `RegisterStudentUseCase`, `UpdateStudentUseCase`, `InactivateStudentUseCase`, `ReactivateStudentUseCase`, `AssignTeacherToStudentUseCase`, `RemoveTeacherFromStudentUseCase`, `PreviewAssignmentRemovalUseCase`, `StartStudentRegistrationUseCase` (inicia o rascunho por tipo `CREATE`, `UPDATE`, `INACTIVATE` ou `REACTIVATE`; nos três últimos confere o escopo do professor), `CollectStudentRegistrationFieldUseCase`, `ConfirmStudentRegistrationUseCase`, `CancelStudentRegistrationUseCase`. (após T013)
- [ ] T017 [P] **F-CORE** Esqueletos em `CORE/usecase/seed/`: remodelar `LoadInitialRegistryUseCase` (orquestrará os casos de uso, sem regra própria) e criar `RotateCpfKeysUseCase`; remover `EmitAccessCodeUseCase`. (após T013)

### Dublês e suporte de teste do backend (F-TEST; após os portos de F-CORE)

- [ ] T018 [P] **F-TEST** Criar suporte de teste do backend em `APP_T/support/` (listar o que já existe antes): `CanaryCpf.java` (CPF fictício válido, constante), `CanaryStudent.java` (aluno menor fictício e constante: nome, data de nascimento, nome, telefone e e-mail do responsável, todos distintos e fáceis de achar por varredura), `LogCapture.java` (captura do logback), `ResponseSweeper.java` (varre corpo, cabeçalhos e URL em busca de um conjunto de valores, com a lista de lugares onde o valor é permitido: a resposta ao ator autorizado e o cartão do bot, nunca log, erro, autoria, `channel_action.payload` fora do cartão, rastro de exceção ou URL), `MatrixLoader.java` (lê `SPEC/contracts/permission-matrix.json`) e `AdministratorFixture.java` (insere administrador ativo com senha conhecida via `AdministratorRepository` e `PasswordHashPort`, para o teste independente da US1 sem depender do bootstrap). (após T012)
- [ ] T019 **F-TEST** Criar em `CORE_T/doubles/` os dublês compartilhados `FakeAuthorshipRecorder`, `FakePasswordHash`, `FixedActivationCodeGenerator` e `ActorFixtures` (administrador, professor, sistema). (após T012)
- [ ] T020 **F-TEST** Completar a semântica **nova** dos dublês existentes em `CORE_T/doubles/` (`InMemoryStudentRepository`: `findVisible`, `list(scope,status)`, `insertIfAbsent`, `searchByCpfHash`; `InMemoryTeacherRepository`, `InMemoryAssignmentRepository`, `InMemoryWebSessionRepository` com `revokeAllOf`, `InMemoryTelegramLinkRepository`, `InMemoryChannelActionRepository` com `discardPendingByChat`, `InMemoryAccessAttemptRepository` com escopo e janela, `FakeDataProtection` com `hashActivationCode`/`originHash`, `FakeOpenReportsQuery`). Depois disto os fluxos só **usam** esses dublês. (após T013)

### Serviços da Fundação (F-CORE)

- [ ] T021 **F-CORE** Implementar, com testes em `CORE_T/service/`, os serviços da Fundação: `ActorAuthorizationService` (consulta a `PermissionMatrix`; nega com `RESOURCE_NOT_FOUND` quando o papel não tem a operação e com `PASSWORD_CHANGE_REQUIRED` para ator restrito), `AuthorshipService` (monta o registro e escolhe `record`/`recordIndependent`; nunca grava CPF, e-mail desconhecido, senha ou código) e `StudentAccessService` (monta o `StudentScope`; `findVisible` em uma consulta; aluno alheio ≡ inexistente, R-14). (após T014, T015, T016, T019)

### Adaptadores, catálogo, configuração e regras de arquitetura (F-APP)

- [ ] T022 [P] **F-APP** Atualizar `RES/messages/messages_pt_BR.properties` com **todas** as chaves e textos de `SPEC/contracts/messages.md`; **remover a palavra "secretaria"** dos textos do professor (`CODE_REQUIRED`, `INVALID_CODE`, `TELEGRAM_ALREADY_LINKED`, `TEACHER_ALREADY_LINKED`, `NO_STUDENTS`) conforme FR-042 e remover `EXPIRED_CODE` e `STUDENT_NOT_ASSIGNED` (FR-050); ajustar as expectativas de texto em `APP_T/inbound/telegram/LinkingConversationIT.java`. O teste do catálogo (`MessageCatalog`) fica verde. (após T008)
- [ ] T023 [P] **F-APP** Estender o mapa de código de erro → status HTTP de `APP/inbound/web/ErrorHandlerController.java` conforme `SPEC/contracts/messages.md` e cobrir em `APP_T/inbound/web/ErrorHandlerIT.java`; remover o mapeamento de `STUDENT_NOT_ASSIGNED` (403) e ajustar para `404 RESOURCE_NOT_FOUND` as expectativas de `APP_T/inbound/web/ResumptionContractIT.java`, `LessonReportContractIT.java` e `HomeStudentsContractIT.java`. (após T008)
- [ ] T024 **F-APP** Criar os **stubs compiláveis** (métodos novos lançam `UnsupportedOperationException`) dos adaptadores de saída: `APP/outbound/persistence/` (`Administrator*`, `AdministratorSession*`, `AuthorshipRecord*`, `StudentRegistrationDraft*`, `StudentDraftSweepTask`) e `APP/outbound/security/` (`Argon2PasswordHashAdapter`, `ActivationCodeGeneratorAdapter`, `CpfKeyRotationAdapter`); fazer os adaptadores existentes compilarem contra os portos alterados sem mudar comportamento. (após T013)
- [ ] T025 **F-APP** Criar `APP/config/AdministrationUseCaseConfig.java`, `TeacherManagementUseCaseConfig.java` e `StudentRegistryUseCaseConfig.java` com os beans de todos os casos de uso e serviços (ajustar `UseCaseConfig` para não duplicar) e um teste de fiação `APP_T/config/UseCaseWiringTest.java` (o contexto sobe). (após T014, T015, T016, T017, T024)
- [ ] T026 [P] **F-APP** Escrever as regras ArchUnit novas: em `CORE_T/architecture/ArchitectureRulesTest.java` (`usecase` não depende de `inbound`/`outbound`; `domain` sem Spring/JPA) e em `APP_T/architecture/AdapterRulesTest.java` (`Actor.system` só em `inbound.seed` e `inbound.bootstrap`; `inbound.telegram` e `inbound.web.MyStudentController` não referenciam nenhum caso de uso cuja `Operation` tenha `teacherTelegram` ou `teacherWeb = DENY` em `permission-matrix.json` (o conjunto é lido pelo teste com o `MatrixLoader`, não de lista escrita à mão; isso cobre `usecase/administrator/*`, `usecase/teacher/*` e `Assign/Remove/Preview…`) nem repositórios do administrador; `AuthorshipRecorderPort` sem método de alteração ou remoção). Também: somente `TelegramLinkTerminationService` pode chamar o método de remoção de vínculo de `TelegramLinkRepository` (garante que "sair", inativação, reemissão e desvinculação passem todas pelo mesmo caminho). Também: classes de `usecase.registry` não chamam métodos de leitura de aluno que dispensem o `StudentScope` (`findVisible` e `list` só pelo `StudentAccessService`); a checagem de CPF duplicado por `searchByCpfHash` fica permitida nos casos de uso de cadastro e edição. Também (FR-002): toda classe `*UseCase` de `usecase.administrator`, `usecase.teacher` e `usecase.registry` depende de `ActorAuthorizationService`, salvo as de operação `PUBLIC` ou de sistema na matriz (entrar, ativar, bootstrap, carga). (após T010, T014, T018)

### Frontend (F-FRONT)

- [ ] T027 [P] **F-FRONT** Acrescentar o script `generate:administration-api` em `frontend/package.json` (gera `FE/services/administration-api-types.ts` de `SPEC/contracts/api-administration.openapi.yaml`), incluí-lo em `generate`, e ignorar o arquivo gerado no mesmo lugar em que `api-types.ts` é ignorado.
- [ ] T028 [P] **F-FRONT** Criar `FE/app/adminRoutes.tsx` e `FE/app/myStudentsRoutes.tsx` (listas de rotas vazias, exportadas) e ligá-las em `FE/app/routes.tsx` conforme `SPEC/contracts/portal-screens.md` §1. (após T027)
- [ ] T029 [P] **F-FRONT** Criar as interfaces de props (sem componente): `FE/components/student/studentFormTypes.ts` e `FE/components/admin/oneTimeSecretPanelTypes.ts`.
- [ ] T030 [P] **F-FRONT** Em `FE/services/apiClient.ts` acrescentar `ADMIN_SESSION_EXPIRED_EVENT` (401 em `/api/v1/admin/**` dispara só o evento do administrador; o evento do professor segue como está), tratamento de `PASSWORD_CHANGE_REQUIRED` e `OPERATION_TEMPORARILY_BLOCKED` (com tempo restante), e `credentials` correto para o cookie `admin_session`. (após T027)
- [ ] T031 **F-FRONT** Regenerar `FE/messages/messages.json` (`npm run generate:messages`) a partir do catálogo e ajustar as expectativas de texto de `FE_T/pages/SignInScreen.test.tsx` e `FE_T/pages/DashboardScreen.test.tsx` (sem "secretaria") e a de `FE_T/e2e/history.spec.ts`, que passa a esperar `RESOURCE_NOT_FOUND` (404) para aluno alheio, no lugar de `STUDENT_NOT_ASSIGNED`. (após T022)

### Suporte de teste do frontend (F-TEST; após a geração de tipos)

- [ ] T032 [P] **F-TEST** Em `FE_T/support/` criar `adminFixtures.ts` e `myStudentsFixtures.ts` (objetos tipados com `administration-api-types.ts`) e, em `FE_T/utils.tsx`, os auxiliares de renderização `renderAsAdministrator` e `renderAsTeacher` (provedores, roteador, cliente de consulta com `gcTime: 0`). (após T027)
- [ ] T033 [P] **F-TEST** Em `FE_T/e2e/support.ts` acrescentar `expectNoSeriousViolations(page, { themes: ['light','dark'], viewports: [390, 1440] })` (axe), `signInAsAdministrator` e `signInAsTeacher`, e a verificação de que a URL final e o armazenamento do navegador não contêm um valor dado (CPF).

### Gate da Fundação

- [ ] T034 **COORD** Gate da S1: `tests backend` e `tests frontend` verdes (testes existentes sem mudança de expectativa, exceto os textos de T022/T031 e as expectativas de `STUDENT_NOT_ASSIGNED` → `RESOURCE_NOT_FOUND` de T008/T023/T031), guardiões de idioma verdes (`GUARD_REPORT=1` sem pendência), `OrphanConfigurationGuardTest`, `ComposeGuardTest` e `WorkflowGuardTest` verdes, cobertura do núcleo não regride em relação a T003, e `api-administration.openapi.yaml` validado pelo validador OpenAPI do repositório. Rodar `pre-pr-review`; abrir o PR da S1 para a `main` com `/open-pr`, sem esperar nova solicitação, e então `code-review-graph build`; nunca mesclar. (após todas as demais tarefas da Fase 2)

**Checkpoint**: fundação mesclada → as ondas 2 (S2, S3, S4) começam em paralelo.

---

## Phase 3: User Story 1 — Acesso do administrador, somente pela Web (Priority: P1) 🎯 MVP

**Goal**: entrar com e-mail e senha em área administrativa separada; sessão fixa de 2 h sem renovação, revogável e validada em toda requisição; bloqueio de 5 falhas/15 min por origem; credencial cruzada = rota inexistente; o Telegram nunca reconhece administrador.

**Independent Test**: com um administrador criado por `AdministratorFixture` (sem depender do bootstrap): entrar com a senha certa e a errada, esgotar as tentativas, deixar a sessão vencer, usar credencial de professor em rota administrativa e a de administrador em rota do professor, e conferir que nenhuma conta do Telegram executa operação administrativa. *(Entrega comercial: US1 + US8 juntas, porque sem o bootstrap não há administrador em produção.)*

### Testes da US1 (escrever primeiro; devem FALHAR)

- [ ] T035 [P] [US1] **W-A** Testes de domínio em `CORE_T/domain/access/`: `PasswordPolicyTest.java` (12 a 128 pontos de código, NFC, sem regra de composição, recusa igual ao e-mail ou ao nome sem diferenciar caixa) e `AdministratorSessionTest.java` (validade fixa; `expires_at` nunca atualizado; válida ⇔ `now < expires_at ∧ não revogada ∧ conta ativa`).
- [ ] T036 [P] [US1] **W-A** Criar os dublês `CORE_T/doubles/InMemoryAdministratorRepository.java` e `InMemoryAdministratorSessionRepository.java` (inclui `lockAllActiveForUpdate`, `revokeAllOf`, `removeExpired`).
- [ ] T037 [P] [US1] **W-A** Testes de caso de uso em `CORE_T/usecase/administrator/`: `SignInAdministratorUseCaseTest.java` (mesma mensagem para e-mail inexistente, senha errada e conta inativa; verificação de senha roda por inteiro mesmo para e-mail desconhecido; bloqueio com tempo restante; sucesso zera a contagem; id de sessão novo a cada login; grava autoria `ADMIN_SIGN_IN`), `ValidateAdministratorSessionUseCaseTest.java`, `GetAdministratorSessionUseCaseTest.java`, `SignOutAdministratorUseCaseTest.java` ("Sair" apaga só aquela sessão e grava `ADMIN_SIGN_OUT`). (após T036)
- [ ] T038 [P] [US1] **W-F** Teste de serviço `CORE_T/service/AccessAttemptsControlServiceScopeTest.java`: 5 falhas bloqueiam por 15 min; falha **durante** bloqueio não conta nem prorroga; sucesso não zera bloqueio ativo; contagem separada por escopo/papel (`ADMIN_SIGN_IN` ≠ `TEACHER_CODE`); janelas do `TEACHER_CPF_PROBE` e do `TEACHER_CPF_LOOKUP` (30 consultas em 60 min bloqueiam por 60 min; CPF livre também conta).
- [ ] T039 [P] [US1] **W-F** Teste Postgres real `APP_T/outbound/persistence/AccessAttemptRepositoryAdapterConcurrencyIT.java`: seis tentativas simultâneas erradas → bloqueio exato na quinta, a sexta não conta; a senha correta depois da quinta é recusada; migração `V11` preserva as linhas existentes como `TEACHER_CODE`.
- [ ] T040 [P] [US1] **W-F** Teste Postgres real `APP_T/outbound/persistence/AuthorshipRecordAdapterIT.java`: `UPDATE`, `DELETE` e `TRUNCATE` em `authorship_record` falham (gatilho); modos `record` (mesma transação) e `recordIndependent`; falha do registro desfaz a gravação; `origin_hash` só em eventos de autenticação; nenhuma coluna aceita CPF.
- [ ] T041 [P] [US1] **W-F** Teste `APP_T/config/log/SensitiveDataMaskingConverterTest.java`: máscara de CPF, telefone, e-mail e senha em mensagens e argumentos de log. Nome e data de nascimento não são mascaráveis: a garantia deles é a disciplina do FR-039 (log só com identificadores e códigos), provada pelos varredores com `CanaryStudent` (T113, T141, T213, T221).
- [ ] T042 [P] [US1] **W-E** Testes Postgres real `APP_T/outbound/persistence/AdministratorRepositoryAdapterIT.java` e `AdministratorSessionRepositoryAdapterIT.java` (migração `V9`; e-mail minúsculo único; validação por requisição; `revokeAllOf`; `removeExpired`; índice único parcial do código pendente; `ON DELETE CASCADE` ao remover administradores preserva `authorship_record`).
- [ ] T043 [P] [US1] **W-E** Testes `APP_T/outbound/security/Argon2PasswordHashAdapterTest.java` (parâmetros configuráveis, sal único, senha de 128 caracteres, hash "isca" para e-mail desconhecido, necessidade de rehash) e `DataProtectionAdapterOriginHashTest.java` (`originHash` por escopo, com subchave do `AUTH_HMAC_SECRET` e não do `HMAC_SECRET`; o endereço de rede nunca é guardado).
- [ ] T044 [P] [US1] **W-E** Teste Postgres real `APP_T/outbound/persistence/AdministratorSignInPerformanceIT.java`: 50 entradas sequenciais com os parâmetros Argon2 padrão; p95 ≤ 500 ms.
- [ ] T045 [P] [US1] **W-H** Teste de contrato `APP_T/inbound/web/admin/AdminSessionContractIT.java`: `POST/GET/DELETE /admin/session` contra `SPEC/contracts/api-administration.openapi.yaml` (status, corpo, `details.fields`, `Cache-Control: no-store`; nenhuma propriedade chamada `cpf`; falha interna (500) devolve `INTERNAL_ERROR` com `reference` e texto genérico sem detalhe técnico; falha parcial devolve `OPERATION_NOT_SAVED` e nada é gravado).
- [ ] T046 [P] [US1] **W-H** Teste de segurança `APP_T/inbound/web/security/AdminSecurityChainIT.java`: cookie `admin_session` com `HttpOnly; Secure; SameSite=Strict; Path=/api/v1/admin`; CSRF em toda gravação; id novo por login; várias sessões simultâneas; validade fixa sem renovação; sem cache de autorização (revogação vale na requisição seguinte); `X-Forwarded-For` forjado não altera a origem (R-05); mensagem e tempo iguais para os três tipos de falha.
- [ ] T047 [P] [US1] **W-H** Teste `APP_T/inbound/web/security/CrossCredentialResponderIT.java`: cookie de professor em `/api/v1/admin/**` e cookie de administrador em rotas do professor devolvem **byte a byte** a resposta de rota inexistente; sem credencial → `401`; as duas sessões coexistem no mesmo navegador sem se combinar.
- [ ] T048 [P] [US1] **X-1** Teste de acesso cruzado **gerado da matriz** `APP_T/security/PermissionMatrixCrossAccessIT.java`: para cada linha de `permission-matrix.json` × (administrador Web, professor Web, professor Telegram, sem credencial) chama rota/comando e compara com a `PermissionMatrix`; operação cuja rota ainda não está mapeada é **pulada com aviso** (o Fecho exige zero puladas, SC-005).
- [ ] T049 [P] [US1] **X-1** Teste `APP_T/security/StudentGuardianAccessAbsenceIT.java` (SC-007): `Role.values()` tem 2 valores; nenhuma rota Spring, caminho OpenAPI, comando/palavra-chave do Telegram ou tabela de credencial se refere a aluno ou responsável como usuário; requisição que se diz de aluno/responsável é tratada como não autenticada.
- [ ] T050 [P] [US1] **X-1** Teste `FE_T/app/noStudentAccess.test.tsx`: as rotas de `routes.tsx`, `adminRoutes.tsx` e `myStudentsRoutes.tsx` não contêm tela, link ou aviso para aluno ou responsável como usuário.
- [ ] T051 [P] [US1] **X-1** Teste `APP_T/privacy/AdminSecretsNotLoggedIT.java`: a senha digitada (e a provisória) nunca aparece em log, resposta, mensagem de erro, URL ou `authorship_record`.
- [ ] T052 [P] [US1] **X-1** Teste ponta a ponta `APP_T/security/AdminSignInBlockingIT.java` (SC-008, Postgres real, relógio controlado): 5 erros → 6ª recusada mesmo com a senha certa, com tempo restante; fim do bloqueio zera; sessão deixa de valer 2 h após o login em 100% dos casos.
- [ ] T053 [P] [US1] **X-1** Teste `APP_T/security/AuthorshipAuthenticationIT.java` (parte do SC-014): `ADMIN_SIGN_IN` (sucesso, falha, `BLOCKED`), `ADMIN_SIGN_OUT` e `AUTHORIZATION_DENIED` deixam registro com papel, canal e resultado corretos; falha com e-mail desconhecido grava "e-mail desconhecido" e a origem, **nunca** o e-mail digitado.
- [ ] T054 [P] [US1] **W-J** Testes `APP_T/inbound/telegram/AdminOperationDeniedConversationIT.java` (conta vinculada e não vinculada enviando "criar professor", "reemitir código", e-mail e senha: resposta de texto não reconhecido + menu do professor, **sem** citar Web, administrador ou área administrativa; conta sem vínculo recebe só `CODE_REQUIRED`) e `PrivateChatOnlyIT.java` (**callbacks**, e não só mensagens, também são recusados fora de conversa privada — verificação 6 do plano).
- [ ] T055 [P] [US1] **W-M** Testes Vitest `FE_T/pages/admin/AdminSignInScreen.test.tsx` (mensagem única para qualquer falha; bloqueio com contagem regressiva em `aria-live="polite"` e envio desabilitado; link "Tenho um código de ativação"; `autocomplete` correto) e `FE_T/app/AdminSessionGuard.test.tsx` (independente do `SessionGuard`; evento de sessão expirada do administrador limpa só o cache dele e leva a `/admin/sign-in` guardando o destino).
- [ ] T056 [P] [US1] **W-M** Testes Vitest `FE_T/components/admin/AdminLayout.test.tsx` e `SessionClock.test.tsx`: selo "Área administrativa", navegação Professores · Alunos · Administradores, aviso `role="alert"` **uma vez** 5 minutos antes de expirar, "Sua sessão expira às HH:MM", dados digitados não mantidos ao expirar.
- [ ] T057 [P] [US1] **X-2** Teste e2e de acessibilidade `FE_T/e2e/admin-access.a11y.spec.ts` (A01, A04 e o layout): axe sem violação séria/crítica em claro e escuro, 390 e 1440 px; navegação só por teclado; ordem de foco; `lang="pt-BR"`; contagem regressiva anunciada.

### Implementação da US1

- [ ] T058 [US1] **W-A** Implementar em `CORE/domain/access/` as classes `Administrator`, `AdministratorSession`, `PasswordPolicy` (e `ActivationCode`, usada na US7). (após T035)
- [ ] T059 [US1] **W-A** Implementar `SignInAdministratorUseCase`, `ValidateAdministratorSessionUseCase`, `GetAdministratorSessionUseCase` e `SignOutAdministratorUseCase` em `CORE/usecase/administrator/`. (após T058, T037)
- [ ] T060 [P] [US1] **W-F** Implementar `CORE/domain/access/AccessAttempt.java` (escopo e janela) e `CORE/service/AccessAttemptsControlService.java` (`verifyLock/registerFailure/confirmSuccess` por escopo; falha em bloqueio não conta nem prorroga). (após T038)
- [ ] T061 [P] [US1] **W-F** Criar as migrações `RES/db/migration/V10__authorship_record.sql` (tabela, `CHECK`s, índices, gatilho `BEFORE UPDATE OR DELETE` e `BEFORE TRUNCATE`) e `V11__access_attempt_scope.sql` (`scope`, `window_started_at`, unicidade `(scope, channel, source_hash)`).
- [ ] T062 [US1] **W-F** Implementar `APP/outbound/persistence/AccessAttempt*` (Entity, Jpa, Adapter com lock de linha e contagem atômica) e `AuthorshipRecord*` (Entity, Jpa, Adapter com os modos `record`/`recordIndependent`). (após T060, T061, T039, T040)
- [ ] T063 [P] [US1] **W-F** Implementar `APP/config/log/SensitiveDataMaskingConverter.java` (máscaras do teste T041).
- [ ] T064 [P] [US1] **W-E** Criar `RES/db/migration/V9__administrator.sql` (`administrator`, com `password_provisional_expires_at`, e `administrator_session`, índices, `CHECK email = lower(email)` e, já nesta migração, o índice único parcial de `activation_code_hash` onde não nulo, usado pela US7).
- [ ] T065 [US1] **W-E** Implementar `APP/outbound/persistence/Administrator*` e `AdministratorSession*` (Entity, Jpa, Adapter: `insertIfNoneExists`, `insertIfAbsent`, `lockAllActiveForUpdate`, `searchByTokenHash` já com a conta, `revokeAllOf`, `removeExpired`). (após T064, T042)
- [ ] T066 [P] [US1] **W-E** Implementar `APP/outbound/security/Argon2PasswordHashAdapter.java` (Argon2id; parâmetros das propriedades; hash isca) e `originHash` em `APP/outbound/security/DataProtectionAdapter.java`. (após T043)
- [ ] T067 [US1] **W-E** Estender `APP/outbound/persistence/AccessSweepTask.java` para também apagar `administrator_session` vencidas. (após T065)
- [ ] T068 [US1] **W-H** Criar a cadeia de segurança do administrador em `APP/inbound/web/security/`: `AdminSecurityConfig`, `AdminSessionFilter`, `AdminSessionCookie`, `AuthenticatedAdministrator`, `CrossCredentialResponder`; ajustar `SecurityConfig` (cadeia própria para `/api/v1/admin/**`, CSRF, `no-store`, origem por `RequestOriginResolver` sem confiar em cabeçalho do cliente). (após T046, T047)
- [ ] T069 [US1] **W-H** Criar `APP/inbound/web/admin/AdminSessionController.java` (`POST/GET/DELETE /admin/session`) e DTOs/Mappers (`AdminSignInRequest`, `AdminSessionResponse`); no `ErrorHandlerController` só a lógica nova (o mapa de status é da Fundação). (após T068, T045)
- [ ] T070 [US1] **W-J** Garantir a negativa neutra em `APP/inbound/telegram/TelegramConversationInterpreter.java` e `TelegramPresenter.java` (texto não reconhecido + menu do professor, sem citar Web nem administrador) e recusar callbacks fora de conversa privada. (após T054)
- [ ] T071 [P] [US1] **W-M** Criar `FE/services/admin/session.ts`, `FE/app/AdminSessionGuard.tsx`, `FE/app/AdminLayout.tsx`, `FE/components/admin/AdminNav.tsx`, `FE/components/admin/SessionClock.tsx` e `FE/styles/admin.css` (só tokens de `tokens.css`; mobile-first a partir de 390 px; alvos ≥ 44 px). (após T055, T056)
- [ ] T072 [US1] **W-M** Criar `FE/pages/admin/AdminSignInScreen.tsx` (A01) e `AdminHomeScreen.tsx` (A04) e registrar as rotas em `FE/app/adminRoutes.tsx`; textos do catálogo gerado, estados de carregamento/erro/sucesso. (após T071)
- [ ] T073 [US1] **COORD** Validar a US1 contra `SPEC/quickstart.md` (cenários 1, 3, 13 e 14) e confirmar que os testes T045 a T057 estão verdes depois da integração (S5). (após T059, T062, T065, T069, T070, T072)

**Checkpoint**: a US1 é testável sozinha (administrador de fixture); a entrega comercial exige também a US8.

---

## Phase 4: User Story 2 — Professores, pelo administrador (Priority: P1)

**Goal**: cadastrar, listar, buscar, editar, inativar e reativar professores; código de 8 caracteres exibido uma única vez; inativar corta o acesso na hora nos dois canais e congela (sem bloquear) os relatórios em aberto.

**Independent Test**: cadastrar professor, anotar o código (uma vez), entrar como professor na Web e no Telegram, listar, editar, repetir o e-mail, inativar (acesso cai nos dois canais) e reativar (código novo).

### Testes da US2 (escrever primeiro; devem FALHAR)

- [ ] T074 [P] [US2] **W-B** Testes em `CORE_T/usecase/teacher/`: `RegisterTeacherUseCaseTest.java` (nasce ativo; código de 8 caracteres do alfabeto sem ambíguos, 7 dias; e-mail único sem diferenciar caixa nem espaços, inclusive entre inativos; duplicidade identifica o professor pelo nome; autoria `TEACHER_REGISTER`), `UpdateTeacherUseCaseTest.java`, `ListTeachersUseCaseTest.java` (busca; "Telegram vinculado"; nunca devolve código), `GetTeacherUseCaseTest.java`.
- [ ] T075 [P] [US2] **W-B** Testes `InactivateTeacherUseCaseTest.java` e `ReactivateTeacherUseCaseTest.java` em `CORE_T/usecase/teacher/`: inativar revoga sessões Web (`ACCOUNT_INACTIVATED`), encerra vínculo/rascunho/ações pendentes e invalida o código; **não é bloqueado** por relatório em aberto; nenhum relatório é criado, alterado, aprovado ou cancelado (SC-013); reativar emite código novo e preserva associações; sessões e vínculo não voltam.
- [ ] T076 [P] [US2] **W-B** Testes `CORE_T/service/TelegramLinkTerminationServiceTest.java` (vínculo + rascunho + ações pendentes, por qualquer causa) e `SessionRevocationServiceTest.java` (professor e administrador; marcação, não remoção, R-08).
- [ ] T077 [P] [US2] **W-B** Testes em `CORE_T/usecase/access/EndTelegramLinkUseCaseTest.java` (novo) e `CORE_T/usecase/access/LinkTelegramUseCaseTest.java` (existente). "Sair" encerra o vínculo pelo `TelegramLinkTerminationService`, descartando rascunho e ações `PENDING` do chat. Vincular conta já vinculada a outro professor segue recusado com `TELEGRAM_ALREADY_LINKED`, sem tocar no vínculo existente. Nenhum caminho remove o vínculo direto pelo repositório.
- [ ] T078 [P] [US2] **W-B** Testes `CORE_T/usecase/access/ValidateSessionWebUseCaseTest.java`, `SignInPortalUseCaseTest.java` e `CORE_T/service/CodeAuthenticationServiceTest.java`: sessão revogada por inativação → `ACCESS_ENDED`; revogada por reemissão ou vencida → `SESSION_EXPIRED`; **mensagem única** (`INVALID_CODE`) para código inexistente, vencido, substituído e de professor inativo; quem já tinha acesso e o perde vê `ACCESS_ENDED`.
- [ ] T079 [P] [US2] **W-B** Testes de autoria da entrada do professor: estender `CORE_T/usecase/access/SignInPortalUseCaseTest.java` e `CORE_T/usecase/access/LinkTelegramUseCaseTest.java` (existentes). Entrada por código na Web grava `TEACHER_SIGN_IN` e vínculo no Telegram grava `TEACHER_LINK_TELEGRAM`, com sucesso ou falha, papel `TEACHER`, canal correto e `origin_hash` só nesses eventos. Código inexistente grava autor nulo e "código desconhecido", **nunca o código digitado**. Bloqueio do escopo `TEACHER_CODE` grava `ATTEMPT_BLOCKED`. Falha ao gravar a autoria mantém o acesso recusado e vai ao log técnico sem dado sensível (FR-040, Atomicidade).
- [ ] T080 [P] [US2] **X-1** Teste `APP_T/security/AuthorshipTeacherAuthenticationIT.java` (SC-014, Postgres real): as mesmas garantias de T079 ponta a ponta, na Web e no simulador do Telegram, incluindo que `authorship_record` não contém o código de acesso.
- [ ] T081 [P] [US2] **W-C2** Teste `CORE_T/usecase/registry/FrozenReportsAfterTeacherInactivationTest.java` (e `CORE_T/usecase/home/HomeInactiveTeacherTest.java`): relatórios em aberto de professor inativo ficam preservados e fora de listas e ações; reativar devolve; `OpenReportsQueryPort` filtra por professor ativo (R-23).
- [ ] T082 [P] [US2] **W-E** Testes Postgres real `APP_T/outbound/persistence/TeacherRepositoryAdapterIT.java` (busca; `insertIfAbsent` com e-mail concorrente: um insere, o outro recebe duplicidade; inativar anula `access_code_hash/expires_at/renewed_at`), `WebSessionRepositoryRevocationIT.java` (`revokeAllOf` com motivo; migração `V14`) e `AccessSweepTaskIT.java` (cobre as duas tabelas de sessão).
- [ ] T083 [P] [US2] **W-G** Testes Postgres real `APP_T/outbound/persistence/ChannelActionDiscardPendingIT.java` (`discardPendingByChat` descarta só `PENDING` do chat) e `OpenReportsQueryAdapterIT.java` (confirmar primeiro se `listTeacherOpenReports` filtra por associação e por professor ativo — verificação 3 do plano — e cobrir o filtro novo). Confirmar também se alguém já descarta ações `PENDING` ao remover o vínculo (verificação 2 do plano).
- [ ] T084 [P] [US2] **W-H** Teste de contrato `APP_T/inbound/web/admin/AdminTeacherContractIT.java`: listar/buscar, criar (devolve `accessCode` **só** nesta resposta), obter, `PATCH`, `inactivation`, `reactivation` (devolve código novo); a lista nunca traz código; `Cache-Control: no-store`; duplicidade devolve o nome do professor existente.
- [ ] T085 [P] [US2] **W-J** Teste `APP_T/inbound/telegram/InactivatedTeacherConversationIT.java`: professor inativado no meio de uma conversa → próxima ação negada, nada gravado, mensagem orienta procurar o administrador; relatório em processamento não envia nada ao chat.
- [ ] T086 [P] [US2] **W-J** Teste `APP_T/inbound/telegram/LeaveTelegramDiscardsDraftIT.java`: "sair" no meio de um cadastro de aluno encerra o vínculo e descarta o rascunho (nada gravado, CPF cifrado removido); depois disso a conta recebe `CODE_REQUIRED`.
- [ ] T087 [P] [US2] **X-1** Teste `APP_T/security/RevocationImmediacyTeacherInactivationIT.java` (SC-010, Postgres real): depois do commit da inativação, nenhuma requisição nova com a sessão antiga (Web) nem com o vínculo (Telegram) é aceita; requisição já em curso pode terminar.
- [ ] T088 [P] [US2] **X-1** Teste `APP_T/privacy/AccessCodeExposureIT.java` (FR-011), parametrizado pelas operações que devolvem segredo (cadastro/reativação de professor, reemissão, criação/reativação de administrador; as ainda não mapeadas são puladas): o código aparece só na resposta da operação, `Cache-Control: no-store`, nunca em URL, listagem, log, histórico, recarga ou `authorship_record`.
- [ ] T089 [P] [US2] **X-1** Teste `APP_T/security/AuthorshipTeacherOperationsIT.java` (SC-014): `TEACHER_REGISTER/UPDATE/INACTIVATE/REACTIVATE` deixam registro com papel `ADMINISTRATOR`, canal `WEB`, alvo e resultado, sem código de acesso.
- [ ] T090 [P] [US2] **W-M** Teste Vitest `FE_T/components/admin/OneTimeSecretPanel.test.tsx`: valor só em `useState` (nunca em cache de consulta, `localStorage`, URL ou `title`), "Copiar" com anúncio `role="status"`, aviso `ADMIN_SECRET_SHOWN_ONCE`, "Já anotei o código" limpa o valor e libera a navegação.
- [ ] T091 [P] [US2] **W-M** Testes Vitest `FE_T/pages/admin/TeachersScreen.test.tsx`, `TeacherFormScreen.test.tsx` e `TeacherDetailScreen.test.tsx`: lista com busca (`<form role="search">`), situação com ícone e texto, "Telegram vinculado"; código mostrado uma vez após criar/reativar; diálogo de confirmação para inativar nomeando ação e alvo (Esc cancela, foco volta); e-mail repetido mostra o nome do professor existente; estados vazio/sem resultado/erro/carregando.
- [ ] T092 [P] [US2] **X-2** Teste e2e `FE_T/e2e/admin-teachers.a11y.spec.ts` (A05 a A08): axe nos dois temas e nas duas larguras, foco preso nos diálogos, `OneTimeSecretPanel` acessível por teclado.

### Implementação da US2

- [ ] T093 [US2] **W-B** Implementar `CORE/service/SessionRevocationService.java` e `CORE/service/TelegramLinkTerminationService.java`. (após T076)
- [ ] T094 [US2] **W-B** Implementar em `CORE/usecase/teacher/` `ListTeachersUseCase`, `GetTeacherUseCase`, `RegisterTeacherUseCase`, `UpdateTeacherUseCase`, `InactivateTeacherUseCase` e `ReactivateTeacherUseCase`, e `Teacher` (`inactivate/reactivate/rename/changeEmail`). (após T093, T074, T075)
- [ ] T095 [US2] **W-B** Ajustar `CORE/service/CodeAuthenticationService.java`, `CORE/usecase/access/ValidateSessionWebUseCase.java`, `SignInPortalUseCase.java` e `CORE/domain/access/WebSession.java` (`revokedAt`, `revocationReason`; `ACCESS_ENDED`; mensagem única). (após T093, T078)
- [ ] T096 [US2] **W-B** Gravar a autoria da entrada do professor em `CORE/usecase/access/SignInPortalUseCase.java` e `CORE/usecase/access/LinkTelegramUseCase.java` via `AuthorshipService` (modo `recordIndependent`; eventos `TEACHER_SIGN_IN`, `TEACHER_LINK_TELEGRAM`, `ATTEMPT_BLOCKED`), sem mudar a regra de acesso. (após T079, T095)
- [ ] T097 [US2] **W-B** Ajustar `CORE/usecase/access/EndTelegramLinkUseCase.java` para encerrar o vínculo pelo `TelegramLinkTerminationService` (hoje remove direto). (após T093, T077)
- [ ] T098 [P] [US2] **W-C2** Aplicar o filtro de professor ativo em `CORE/port/OpenReportsQueryPort.java`, `CORE/usecase/home/*`, `CORE/usecase/lesson/RegisterLessonByAudioUseCase.java` e `CORE/usecase/semester/Generate…` para que relatórios de professor inativo fiquem congelados e voltem na reativação. (após T081)
- [ ] T099 [P] [US2] **W-E** Criar `RES/db/migration/V14__web_session_revocation.sql` (`revoked_at`, `revocation_reason` em `web_session`).
- [ ] T100 [US2] **W-E** Implementar `APP/outbound/persistence/WebSessionRepositoryAdapter.java` (`revokeAllOf`) e `TeacherRepositoryAdapter.java` (`list`, `insertIfAbsent`, inativar anula o código). (após T099, T082)
- [ ] T101 [P] [US2] **W-G** Implementar `ChannelActionRepositoryAdapter.discardPendingByChat`, o encerramento do vínculo em `TelegramLinkRepositoryAdapter.java` e o filtro de `OpenReportsQueryAdapter.java` em `APP/outbound/persistence/`. (após T083)
- [ ] T102 [US2] **W-H** Criar `APP/inbound/web/admin/AdminTeacherController.java` (`GET/POST /admin/teachers`, `GET/PATCH /admin/teachers/{teacherId}`, `POST …/inactivation`, `POST …/reactivation`) e DTOs/Mappers; sem regra de negócio. (após T084, T068)
- [ ] T103 [US2] **W-J** Mapear `ACCESS_ENDED`/`TEACHER_INACTIVE` em `APP/inbound/telegram/TelegramConversationInterpreter.java` e `TelegramPresenter.java` (mensagem de acesso encerrado, nada gravado). (após T085, T070)
- [ ] T104 [P] [US2] **W-M** Criar `FE/components/admin/OneTimeSecretPanel.tsx` conforme `oneTimeSecretPanelTypes.ts`. (após T090)
- [ ] T105 [US2] **W-M** Criar `FE/services/admin/teachers.ts` e as telas `FE/pages/admin/TeachersScreen.tsx` (A05), `TeacherFormScreen.tsx` (A06/A07) e `TeacherDetailScreen.tsx` (A08: editar, inativar/reativar com `ConfirmationDialog`); registrar em `adminRoutes.tsx`. (após T104, T091)
- [ ] T106 [US2] **COORD** Validar a US2 contra `SPEC/quickstart.md` (cenários 4 e 5) depois da integração (S5). (após T094, T095, T100, T101, T102, T103, T105)

**Checkpoint**: US2 funcional e testável sozinha (reemitir código e desvincular Telegram ficam na US6).

---

## Phase 5: User Story 3 — Alunos, pelo administrador (Priority: P1)

**Goal**: o administrador cadastra, lista (busca, filtro, paginação), edita, inativa e reativa **qualquer** aluno; CPF validado, cifrado e nunca devolvido; responsável obrigatório para menor de 18 anos; inativar nunca é bloqueado por relatório em aberto.

**Independent Test**: cadastrar adulto e menor (com e sem responsável), CPF inválido, CPF repetido e 31/02; associar professores; buscar; editar; inativar e reativar; conferir que o CPF não aparece em resposta, tela, log nem autoria.

### Testes da US3 (escrever primeiro; devem FALHAR)

- [ ] T107 [P] [US3] **W-C1** Testes de valores em `CORE_T/domain/registry/`: `PersonNameTest`, `EmailAddressTest`, `BrazilianPhoneTest` (DDD 11–99; 10 ou 11 dígitos; celular começa com 9; máscara aceita), `BirthDateTest` (31/02, futura, anterior a 120 anos, 18 anos completos = adulto, fuso `America/Sao_Paulo`), `InstrumentTest`, `GuardianTest`, `CpfTest` (dígitos verificadores, sequências repetidas, pontos e traço) e `StudentDataTest` (acumula violações em `VALIDATION_FAILED`; responsável obrigatório para menor; mantido quando vira adulto). Cobrir também: espaços das pontas removidos; vazio após aparar = ausente; caracteres de controle recusados (`INVALID_TEXT`); nome com 2 e 120 caracteres aceito e com 1 e 121 recusado; instrumento com 60 aceito e 61 recusado; e-mail com 254 aceito e 255 recusado; texto normalizado em NFC.
- [ ] T108 [P] [US3] **W-C1** Testes em `CORE_T/usecase/registry/`: `RegisterStudentUseCaseTest.java` (administrador com professores; nasce ativo; tudo ou nada; duplicidade de CPF indica o **nome** do aluno e nunca o CPF; autoria `STUDENT_REGISTER`), `UpdateStudentUseCaseTest.java` (CPF em branco mantém o atual; edição que torna menor exige responsável), `ListStudentsUseCaseTest.java` (busca por nome/instrumento sem acento nem caixa; **sem** busca por CPF (termo com forma de CPF → `VALIDATION_FAILED` no campo `search`, sem repetir o termo); filtro de situação; paginação; administrador vê todos), `GetStudentUseCaseTest.java`.
- [ ] T109 [P] [US3] **W-C2** Testes `CORE_T/usecase/registry/InactivateStudentUseCaseTest.java` e `ReactivateStudentUseCaseTest.java` (administrador: qualquer aluno; **não bloqueia** por relatório em aberto; nada apagado; SC-013) e `CORE_T/usecase/lesson/RegisterLessonRejectsInactiveStudentTest.java` (aluno inativo não é oferecido para novo relatório e sai das listas padrão; o professor associado ainda vê e conclui/cancela o relatório já em aberto).
- [ ] T110 [P] [US3] **W-G** Testes Postgres real `APP_T/outbound/persistence/StudentRepositoryAdapterIT.java` (migração `V12`: `guardian_phone ~ '^[0-9]{10,11}$'`, alunos antigos sem contato continuam válidos; `insertIfAbsent` com CPF concorrente → um insere; `findVisible(scope,id)` em uma consulta; `list` com busca sem acento; `searchByCpfHash`) e `StudentCpfStorageIT.java` (CPF só cifrado e `cpf_hash` com chave; nenhum CPF em claro em coluna ou log).
- [ ] T111 [P] [US3] **W-G** Teste Postgres real `APP_T/outbound/persistence/StudentListPerformanceIT.java`: 1.000 alunos; listar e buscar por nome em 30 execuções; p95 ≤ 300 ms.
- [ ] T112 [P] [US3] **W-H** Teste de contrato `APP_T/inbound/web/admin/AdminStudentContractIT.java`: listar/buscar/filtrar/paginar, criar com professores, obter, `PATCH`, `inactivation`, `reactivation`; **nenhuma propriedade de resposta chama-se `cpf`**; erros com `details.fields`; duplicidade devolve o nome do aluno; falha no meio do cadastro com professores devolve `OPERATION_NOT_SAVED` e não deixa cadastro parcial.
- [ ] T113 [P] [US3] **X-1** Teste de privacidade `APP_T/privacy/StudentCpfCanaryAdminIT.java` (SC-004): fluxos de cadastro, edição, duplicidade e validação do administrador com `CanaryCpf` e `CanaryStudent` — o CPF canário não aparece em logs, respostas, cabeçalhos, URL, mensagens de erro nem `authorship_record`; o aluno canário (nome, nascimento e dados do responsável) só aparece na resposta ao ator autorizado, nunca em log, mensagem de erro, rastro de exceção, URL nem `authorship_record`.
- [ ] T114 [P] [US3] **X-1** Teste `APP_T/security/AuthorshipStudentAdminOperationsIT.java` (SC-014): `STUDENT_REGISTER/UPDATE/INACTIVATE/REACTIVATE` feitos pelo administrador deixam registro com papel, canal `WEB`, alvo e resultado, sem CPF nem dado do responsável.
- [ ] T115 [P] [US3] **W-N** Testes Vitest `FE_T/components/student/StudentForm.test.tsx` (CPF com `inputmode="numeric"`, `autocomplete="off"`, `autocapitalize="off"`, `spellcheck="false"`, sem máscara que vaze para a URL; na edição o CPF vem **em branco** com a dica `MY_STUDENTS_CPF_KEEP`; erros por campo de `details.fields` ligados por `aria-describedby`; resumo de erros no topo recebe o foco no envio; seção "Responsável" com telefone `type="tel"`), `StudentList.test.tsx` e `SituationBadge.test.tsx` (ícone e texto, nunca só cor).
- [ ] T116 [P] [US3] **W-M** Testes Vitest `FE_T/pages/admin/StudentsScreen.test.tsx` (A10: busca, situação, paginação, professores associados, vazio; termo com forma de CPF recusado no cliente, sem ir à URL nem ao cache de consulta), `StudentFormScreen.test.tsx` (A11/A12: multisseleção de professores ativos, erros por campo, CPF em branco na edição) e `StudentDetailScreen.test.tsx` (inativar com confirmação).
- [ ] T117 [P] [US3] **X-2** Teste e2e `FE_T/e2e/admin-students.a11y.spec.ts` (A10 a A12): axe nos dois temas e larguras; a URL final e o armazenamento do navegador não contêm o CPF digitado.

### Implementação da US3

- [ ] T118 [US3] **W-C1** Implementar em `CORE/domain/registry/` `PersonName`, `EmailAddress`, `BrazilianPhone`, `BirthDate`, `Instrument`, `Guardian`, `StudentData` e `Student` (`applyEdit`), e os limites de `RegistryLimits`. (após T107)
- [ ] T119 [US3] **W-C1** Implementar `ListStudentsUseCase`, `GetStudentUseCase`, `RegisterStudentUseCase` e `UpdateStudentUseCase` em `CORE/usecase/registry/` (caminho do administrador, com `StudentScope.all()`, autoria e atomicidade; deixar o ramo do professor para a US4). (após T118, T108)
- [ ] T120 [P] [US3] **W-C2** Implementar `InactivateStudentUseCase` e `ReactivateStudentUseCase` em `CORE/usecase/registry/` (administrador) e a exclusão do aluno inativo de novos relatórios e das listas padrão. (após T109)
- [ ] T121 [P] [US3] **W-G** Criar `RES/db/migration/V12__student_contact_and_registration_draft.sql` (colunas `guardian_phone`, `guardian_email` em `student` e a tabela `student_registration_draft` completa de `SPEC/data-model.md` §3.5, com `ON DELETE CASCADE` no vínculo).
- [ ] T122 [US3] **W-G** Atualizar `APP/outbound/persistence/Student*` (Entity, Jpa, Adapter: `guardianPhone/Email`, `insertIfAbsent`, `findVisible`, `list`, `searchByCpfHash`; CPF cifrado e hash inalterados). (após T121, T110)
- [ ] T123 [US3] **W-H** Criar `APP/inbound/web/admin/AdminStudentController.java` (`GET/POST /admin/students`, `GET/PATCH /admin/students/{studentId}`, `inactivation`, `reactivation`) e DTOs/Mappers **sem** campo `cpf` nas respostas. (após T112)
- [ ] T124 [US3] **W-N** Criar `FE/components/student/StudentForm.tsx` (conforme `studentFormTypes.ts`, usado por A11/A12 e P02/P03), `StudentList.tsx` e `SituationBadge.tsx`. (após T115)
- [ ] T125 [US3] **W-M** Criar `FE/services/admin/students.ts` e as telas `FE/pages/admin/StudentsScreen.tsx` (A10), `StudentFormScreen.tsx` (A11) e `StudentDetailScreen.tsx` (A12: editar, inativar/reativar com confirmação); registrar em `adminRoutes.tsx`. (após T124, T116)
- [ ] T126 [US3] **COORD** Validar a US3 contra `SPEC/quickstart.md` (cenário 6) depois da integração (S5). (após T119, T120, T122, T123, T125)

**Checkpoint**: US3 funcional sozinha (associar/retirar professores depois do cadastro é a US5).

---

## Phase 6: User Story 4 — Alunos, pelo professor (somente os seus; Web e Telegram) (Priority: P1)

**Goal**: o professor cadastra, lista, busca, abre, edita e inativa/reativa **somente os próprios alunos**, na Web e no Telegram, com os **mesmos casos de uso**; aluno alheio ≡ inexistente; CPF de aluno alheio recusado sem vazar dado; no Telegram, estado persistido, resumo sem CPF, confirmação e mensagem do CPF apagada.

**Independent Test**: com professores A e B, A cadastra, lista, busca, edita e inativa os seus; tenta abrir, editar, inativar, buscar e cadastrar com CPF de aluno de B (identificador manipulado na Web; nome, botão ou `callback_data` adulterado no Telegram); tudo é negado ou tratado como inexistente, sem vazamento, em cada canal.

### Testes da US4 (escrever primeiro; devem FALHAR)

**Núcleo**

- [ ] T127 [P] [US4] **W-C1** Testes do ramo do professor em `CORE_T/usecase/registry/`: `RegisterStudentAsTeacherUseCaseTest.java` (nasce associado só ao ator; professor sem alunos cadastra o primeiro; CPF de aluno **alheio** → `CPF_UNAVAILABLE` neutro + autoria `CPF_REFUSED_OTHER_STUDENT` + contagem do limite de sondagem; CPF de aluno **próprio** → `STUDENT_ALREADY_YOURS`; 5 recusas em 15 min bloqueiam por 15 min só as operações com CPF, `OPERATION_TEMPORARILY_BLOCKED`; 30 consultas de CPF, livre ou ocupado, em 60 min bloqueiam por 60 min; a nova verificação da confirmação não conta), `UpdateStudentAsTeacherUseCaseTest.java` (mesma regra ao editar o CPF) e `StudentVisibilityTest.java` (listar/buscar/abrir/editar só os associados; aluno alheio ou sem professor ≡ inexistente; inativos só ao filtrar por situação).
- [ ] T128 [P] [US4] **W-C2** Testes `CORE_T/usecase/registry/InactivateStudentAsTeacherUseCaseTest.java` e `ReactivateStudentAsTeacherUseCaseTest.java` (FR-026: com mais de um professor **ativo** associado, inativar/reativar → `SHARED_STUDENT_ACTION_DENIED` com orientação de procurar o administrador, editar é permitido; professores inativos não contam; único professor ativo pode inativar e reativar; nunca bloqueado por relatório em aberto).
- [ ] T129 [P] [US4] **W-D** Testes `CORE_T/domain/telegram/StudentRegistrationDraftTest.java` e, em `CORE_T/usecase/registry/`, `CollectStudentRegistrationFieldUseCaseTest.java` (valida campo a campo no núcleo; informa `guardianRequired` para menor na data de hoje no fuso da escola; CPF só cifrado), `ConfirmStudentRegistrationUseCaseTest.java` (dono, token de uso único e prazo; atômico com `RegisterStudentUseCase`/`UpdateStudentUseCase`/`InactivateStudentUseCase`/`ReactivateStudentUseCase`, usando dublê deles; segunda confirmação ou token anterior → `CONFIRMATION_ALREADY_DONE` sem gravar de novo; vencido → `CONFIRMATION_EXPIRED`), `StartStudentRegistrationUseCaseTest.java` (tipos `CREATE`, `UPDATE`, `INACTIVATE`, `REACTIVATE`; nos três últimos confere o escopo por `findVisible`: aluno alheio ≡ inexistente e nenhum rascunho é criado; permissão da `Operation` correspondente; um rascunho por vínculo, o novo substitui o anterior) e `CancelStudentRegistrationUseCaseTest.java`; o `ConfirmStudentRegistrationUseCaseTest.java` cobre os quatro tipos, incluindo associação retirada ou professor que deixou de ser o único ativo entre iniciar e confirmar (aluno inexistente ou `SHARED_STUDENT_ACTION_DENIED`, sem gravar).

**Persistência e transporte**

- [ ] T130 [P] [US4] **W-G** Testes Postgres real `APP_T/outbound/persistence/StudentRegistrationDraftRepositoryAdapterIT.java` (CPF só cifrado; um rascunho por vínculo; **cascata**: o rascunho some quando o vínculo termina, por qualquer causa), `StudentDraftSweepTaskIT.java` (apaga vencidos) e `TransactionAdapterRollBackIT.java` (`runAndRollBack` desfaz tudo, inclusive `authorship_record`).
- [ ] T131 [P] [US4] **W-L** Descobrir o nome real da `CHECK` do tipo de ação nas migrações `V6`/`V7`/`V8` e confirmar que `ChannelActionRepository` aceita o novo tipo (verificação 1 do plano); anotar em `SPEC/evidence/channel-action-constraint.md` e escrever os testes `CORE_T/domain/channel/ChannelActionDeleteMessageTest.java` (compensação `MESSAGE_NOT_DELETABLE` → mensagem `CPF_MESSAGE_NOT_DELETED`), `APP_T/inbound/telegram/channel/DeleteMessageActionContractIT.java` (ação v1 `DELETE_MESSAGE` contra `specs/002-api-interna-canal-telegram/contracts/actions-v1.schema.json`; receptor antigo responde `ACTION_NOT_SUPPORTED`) e `APP_T/outbound/persistence/ChannelActionDeleteMessageIT.java` (migração `V13`).
- [ ] T132 [P] [US4] **W-L** Testes de transporte: `infra/telegram-simulator/server.test.mjs` (`deleteMessage` aceito, falha → `MESSAGE_NOT_DELETABLE`), cenário em `infra/tests/n8n/` para a ação `DELETE_MESSAGE` e `APP_T/architecture/WorkflowGuardTest` (o `execute-actions.json` segue **só transporte**: sem decisão, texto ou validação).

**Web e Telegram**

- [ ] T133 [P] [US4] **W-I** Teste de contrato `APP_T/inbound/web/MyStudentContractIT.java`: `GET/POST /my-students`, `GET/PATCH /my-students/{studentId}`, `inactivation`, `reactivation`; `404 RESOURCE_NOT_FOUND` uniforme; `403 SHARED_STUDENT_ACTION_DENIED`; `409`/neutro para `CPF_UNAVAILABLE`; `429 OPERATION_TEMPORARILY_BLOCKED` com tempo restante; nenhuma propriedade `cpf`.
- [ ] T134 [P] [US4] **W-J** Testes de conversa em `APP_T/inbound/telegram/`: `StudentRegistryConversationIT.java` (cadastro guiado nome → data → CPF → responsável se menor → instrumento/`pular` → resumo **sem CPF** → Confirmar; erro de validação repete a pergunta sem repetir o dado; ação `DELETE_MESSAGE` emitida **antes** da próxima pergunta; cancelar/vencer não grava e descarta; segunda confirmação responde "já concluída"), `StudentRegistryListConversationIT.java` (6 por página em ordem alfabética, `‹ Anterior`/`Próxima ›`, filtro Ativos/Inativos, busca por texto (texto com forma de CPF não vira busca, não é guardado em `data["options"]` e a mensagem é apagada com `DELETE_MESSAGE`), cartão sem CPF e só com o nome do responsável, vazio → "cadastrar o primeiro aluno"), `StudentRegistryEditConversationIT.java` (campo a campo; resumo só com o que muda; "CPF será substituído"), `StudentRegistryInactivateConversationIT.java` (inativar **e reativar** com resumo e botões Confirmar/Cancelar; aluno compartilhado negado; segundo toque → `CONFIRMATION_ALREADY_DONE` sem gravar de novo; token vencido → `CONFIRMATION_EXPIRED`; botão de outro vínculo recusado; resumo anterior invalidado pelo novo), `StudentRegistryPrivateOnlyIT.java` (grupo/canal e callbacks recusados com negativa neutra) e `StudentRegistryRestartResilienceIT.java` (estado persistido sobrevive ao reinício do transporte). Cobrir todas as `ConversationFlowStep` novas para o `ConversationStateCoverageTest` existente.
- [ ] T135 [P] [US4] **W-J** Teste `APP_T/inbound/telegram/NoStudentsOrientationConversationIT.java` (FR-042): professor sem alunos vê "cadastrar o primeiro aluno" e nenhuma resposta cita "secretaria"; conta já vinculada a outro professor e código inválido orientam procurar o administrador.

**Segurança, isolamento, paridade e privacidade (X-1)**

- [ ] T136 [P] [US4] **X-1** Teste de isolamento na Web `APP_T/security/StudentIsolationWebIT.java` (SC-006): professores A e B; A tenta ler, editar (inclusive o CPF), inativar, reativar, buscar e cadastrar sobre aluno de B por identificador manipulado, **e também percorrer as rotas da 001** (`/students/{id}`, `open-report`, `lesson-reports`, `semesters`, `semester-reports`, `history`, documentos) — 100% negado ou tratado como inexistente (`404 RESOURCE_NOT_FOUND`, nunca 403; FR-050); nada é alterado; cada tentativa deixa `AUTHORIZATION_DENIED`, igual para aluno alheio e para inexistente (mesmo trabalho, FR-040 e FR-025).
- [ ] T137 [P] [US4] **X-1** Teste de isolamento no Telegram `APP_T/security/StudentIsolationTelegramIT.java`: nome digitado, botão ou `callback_data` adulterado (posição fora da lista persistida) sobre aluno de B → mesma resposta de "não encontramos" de um aluno inexistente.
- [ ] T138 [P] [US4] **X-1** Teste de indistinguibilidade `APP_T/security/IndistinguishableStudentResponseIT.java` (inclui as rotas da 001 por aluno): aluno alheio × aluno inexistente têm **mesmo status, corpo e cabeçalhos relevantes** (Web) ou **mesmo texto e botões** (Telegram), e tempos de resposta equivalentes: 200 medições alternadas de cada caso após 50 de aquecimento, ordem alternada com semente fixa; passa se a razão das medianas ficar em [0,5; 2,0] e a dos p95 em [0,5; 3,0]; o teste imprime as razões.
- [ ] T139 [P] [US4] **X-1** Teste `APP_T/security/CpfUnavailableNoLeakIT.java`: CPF duplicado de aluno de outro professor é recusado (cadastro **e** edição) sem nome do aluno, nome de professor ou qualquer dado na resposta, no texto do bot, no log e na autoria; CPF de aluno próprio informa "já está entre os seus"; os limites de sondagem (recusas e consultas) bloqueiam só as operações com CPF e informa o tempo restante sem dizer o motivo.
- [ ] T140 [P] [US4] **X-1** Teste de paridade `APP_T/parity/StudentRegistryParityScriptIT.java` usando `ParityScript`/`ChannelWebDriver`/`TelegramChannelDriver` (ampliar os drivers no mínimo necessário): o **mesmo roteiro** (cadastrar, listar, buscar, abrir, editar, inativar, reativar, aluno compartilhado negado, CPF indisponível, bloqueio por sondagem) produz o mesmo resultado de negócio nos dois canais. Antes, ler o `ParityEvidenceGuard`: se ele exigir evidência para script novo de paridade, criá-la em `backend/application/src/test/resources/parity-evidence/` no formato existente; se não exigir, registrar em `SPEC/evidence/parity.md` que não se aplica.
- [ ] T141 [P] [US4] **X-1** Teste de privacidade `APP_T/privacy/StudentCpfCanaryTeacherIT.java` (SC-004): fluxos do professor na Web **e** no Telegram com `CanaryCpf` e `CanaryStudent` — o CPF canário não aparece em logs, respostas, mensagens do bot, resumo de confirmação, ações de saída, n8n/simulador, URL nem autoria; o aluno canário só aparece na resposta ao professor autorizado e no cartão do bot (contrato do Telegram §3.2), nunca em log, mensagem de erro, rastro de exceção, URL, autoria nem em ação de saída fora do cartão.
- [ ] T142 [P] [US4] **X-1** Teste `APP_T/security/AuthorshipStudentTeacherOperationsIT.java` (SC-014): operações do professor (Web e Telegram) e recusas de CPF alheio deixam registro com papel `TEACHER`, canal correto e resultado, sem CPF.
- [ ] T143 [P] [US4] **X-1** Teste de concorrência `APP_T/security/StudentConcurrencyIT.java` (Postgres real): dois professores cadastrando o mesmo CPF ao mesmo tempo → um é aceito e o outro recebe a recusa neutra; confirmação duplicada do bot → uma gravação.

**Frontend**

- [ ] T144 [P] [US4] **W-N** Testes Vitest `FE_T/pages/MyStudentsScreen.test.tsx` (P01: lista paginada, busca (termo com forma de CPF recusado no cliente, sem ir à URL nem ao cache), filtro Ativos/Inativos/Todos, vazio → `MY_STUDENTS_EMPTY` com "Cadastrar o primeiro aluno"), `MyStudentFormScreen.test.tsx` (P02: seção Responsável sempre visível; `CPF_UNAVAILABLE` no campo sem nome nem professor; `OPERATION_TEMPORARILY_BLOCKED` desabilita só o envio e mostra o tempo restante) e `MyStudentDetailScreen.test.tsx` (P03: inativar com confirmação `MY_STUDENTS_CONFIRM_INACTIVATE`; aluno compartilhado → botões desabilitados com orientação, `canInactivate = false`).
- [ ] T145 [P] [US4] **W-N** Atualizar `FE_T/pages/SignInScreen.test.tsx` (link **"É administrador? Acesse a área administrativa."** levando a `/admin/sign-in`, exibido agora; nenhuma ocorrência de "secretaria") e `FE_T/pages/DashboardScreen.test.tsx` (atalho "Meus alunos"; professor sem alunos vê a orientação de cadastrar o primeiro) e `FE_T/pages/StudentsScreen.test.tsx` (a escolha de aluno para aula mostra só ativos).
- [ ] T146 [P] [US4] **X-2** Testes e2e `FE_T/e2e/my-students.a11y.spec.ts` (P01 a P03, axe nos dois temas e larguras, teclado) e `FE_T/e2e/my-students.spec.ts` (cadastro completo em ≤ 3 min de uso, SC-002; a URL final e o armazenamento do navegador **não** contêm o CPF digitado).
- [ ] T147 [P] [US4] **X-2** Teste e2e `FE_T/e2e/admin-register-flow.spec.ts` (SC-001): o administrador cadastra um professor, copia o código e cadastra um aluno associado a ele. Roteiro com digitação a 40 ms/tecla e ≤ 25 interações; falha se o total passar de 180 s.
- [ ] T148 [P] [US4] **X-1** Teste `APP_T/parity/StudentRegistryTelegramTimingIT.java` (SC-002): cadastro de aluno adulto no simulador do Telegram, com 3 s de resposta simulada por mensagem; falha se passar de 180 s ou de 8 interações.

### Implementação da US4

- [ ] T149 [US4] **W-C1** Completar `RegisterStudentUseCase`, `UpdateStudentUseCase`, `ListStudentsUseCase` e `GetStudentUseCase` com o ramo do professor (escopo `ofTeacher`, `findVisible`, `CPF_UNAVAILABLE`/`STUDENT_ALREADY_YOURS`, contagem e autoria da sondagem pelos escopos `TEACHER_CPF_PROBE` e `TEACHER_CPF_LOOKUP`). (após T119, T127)
- [ ] T150 [P] [US4] **W-C2** Completar `InactivateStudentUseCase`/`ReactivateStudentUseCase` com a regra FR-026 (único professor **ativo**). (após T120, T128)
- [ ] T151 [US4] **W-D** Implementar `CORE/domain/telegram/StudentRegistrationDraft.java` (tipos `CREATE`, `UPDATE`, `INACTIVATE`, `REACTIVATE`) e `StartStudentRegistrationUseCase`, `CollectStudentRegistrationFieldUseCase`, `ConfirmStudentRegistrationUseCase` (orquestra `RegisterStudentUseCase`/`UpdateStudentUseCase`/`InactivateStudentUseCase`/`ReactivateStudentUseCase`) e `CancelStudentRegistrationUseCase` em `CORE/usecase/registry/`. (após T129)
- [ ] T152 [US4] **W-G** Implementar `APP/outbound/persistence/StudentRegistrationDraft*` (Entity, Jpa, Adapter), `StudentDraftSweepTask.java`, a cascata no `TelegramLinkRepositoryAdapter.java` e `TransactionAdapter.runAndRollBack`. (após T121, T130)
- [ ] T153 [US4] **W-L** Implementar `V13__channel_action_delete_message.sql` (estende a `CHECK` pelo nome descoberto em T131), `CORE/domain/channel/ChannelAction.java` (compensação `MESSAGE_NOT_DELETABLE`), `APP/inbound/telegram/channel/ChannelActionMapper.java` e `ActionDto.java`, e acrescentar `DeleteMessageAction` em `specs/002-api-interna-canal-telegram/contracts/actions-v1.schema.json` (incluindo `MESSAGE_NOT_DELETABLE` na lista de códigos de falha) e atualizar `specs/004-n8n-telegram-transport/contracts/n8n-workflows.md` (E2: tipos aceitos; tabelas de tradução e de classificação de falha com `deleteMessage` e `MESSAGE_NOT_DELETABLE`). (após T131)
- [ ] T154 [US4] **W-L** Fazer o `infra/n8n/workflows/execute-actions.json` executar `deleteMessage` e confirmar como as demais ações (transporte puro) e acrescentar `deleteMessage` em `infra/telegram-simulator/server.mjs`. (após T153, T132)
- [ ] T155 [US4] **W-I** Criar `APP/inbound/web/MyStudentController.java` (`/my-students/**`) e DTOs/Mapper: `404` uniforme, `403` compartilhado, `429` de sondagem; sem `cpf` nas respostas. (após T133)
- [ ] T156 [US4] **W-J** Criar `APP/inbound/telegram/TelegramStudentRegistryFlow.java` e ajustar `TelegramKeywords` ("meus alunos", "alunos", "cadastrar aluno", "novo aluno", "pular", "voltar"), `TelegramPresenter`, `TelegramConversationInterpreter`, `TelegramOutput` (`DeleteMessage`) e `ConversationFlow`/`ConversationFlowStep` (`STUDENT_REGISTRY` e as etapas `REGISTRY_*`), com callbacks `v1:reg-*` revalidados contra o estado persistido (`CALLBACK_EXPIRED`) e menu do professor com "Meus alunos" e "Cadastrar aluno". Só adaptador: nenhuma regra. (após T134, T135; integração real com T151 e T153 no Fecho — até lá, dublê do `ConfirmStudentRegistrationUseCase` e do mapeador de ações)
- [ ] T157 [US4] **W-N** Criar `FE/services/myStudents.ts` e as telas `FE/pages/MyStudentsScreen.tsx` (P01), `MyStudentFormScreen.tsx` (P02) e `MyStudentDetailScreen.tsx` (P03) usando o `StudentForm` real, e registrar em `FE/app/myStudentsRoutes.tsx`. (após T124, T144)
- [ ] T158 [US4] **W-N** Alterar `FE/pages/SignInScreen.tsx` (link "É administrador? Acesse a área administrativa." → `/admin/sign-in`, FR-043) e `FE/pages/DashboardScreen.tsx` (atalho "Meus alunos"; orientação de cadastrar o primeiro aluno). (após T145)
- [ ] T159 [US4] **COORD** Validar a US4 contra `SPEC/quickstart.md` (cenários 7, 8 e 12) depois da integração (S5). (após T149, T150, T151, T152, T154, T155, T156, T157, T158)

**Checkpoint**: Web e Telegram do professor completos e isolados; paridade verificada.

---

## Phase 7: User Story 5 — Associações professor–aluno, pelo administrador (Priority: P2)

**Goal**: incluir e retirar professores de um aluno a qualquer momento; retirar corta o acesso na hora (sem exceção), sem bloquear; relatórios em aberto ficam congelados e voltam se o professor for associado de novo; a tela avisa quantos congelam.

**Independent Test**: associar e desassociar professores de um aluno com e sem relatório em aberto; conferir na Web e no Telegram o que cada professor passa a ver.

### Testes da US5 (escrever primeiro; devem FALHAR)

- [ ] T160 [P] [US5] **W-C2** Testes em `CORE_T/usecase/registry/`: `AssignTeacherToStudentUseCaseTest.java` (só professor **ativo**; professor inativo recusado; idempotente; autoria `ASSIGNMENT_ASSIGN`), `RemoveTeacherFromStudentUseCaseTest.java` (não bloqueia; nada apagado nem alterado em relatório; acesso termina na hora; aluno pode ficar com zero professores e só o administrador o vê; autoria `ASSIGNMENT_REMOVE`), `PreviewAssignmentRemovalUseCaseTest.java` (conta relatórios em aberto daquele professor com o aluno) e `FrozenReportsAfterRemovalTest.java` (reassociar devolve o relatório de onde parou; aprovados preservados).
- [ ] T161 [P] [US5] **W-G** Teste Postgres real `APP_T/outbound/persistence/AssignmentRepositoryAdapterIT.java` (`delete`, `listTeachersOfStudent`, `countActiveTeachers`) e ampliação de `OpenReportsQueryAdapterIT.java` com o filtro por associação (um arquivo novo `OpenReportsQueryAssociationIT.java`, para não disputar o da US2).
- [ ] T162 [P] [US5] **W-H** Teste de contrato `APP_T/inbound/web/admin/AdminAssignmentContractIT.java`: `PUT/DELETE /admin/students/{studentId}/teachers/{teacherId}` e `GET …/removal-preview` (`frozenReports`).
- [ ] T163 [P] [US5] **X-1** Teste `APP_T/security/AssignmentRemovalAccessIT.java` (SC-006 e SC-013): após retirar a associação, o professor perde o aluno e o relatório na Web **e** no Telegram (resposta idêntica à de inexistente), nenhum relatório é criado/alterado/aprovado/cancelado, e a reassociação devolve o relatório; autoria `ASSIGNMENT_*` sem dado do aluno.
- [ ] T164 [P] [US5] **W-J** Teste `APP_T/inbound/telegram/AssignmentRemovalConversationIT.java`: depois da retirada, o aluno some da lista do professor e o `callback_data` antigo responde como inexistente; com um rascunho de edição, inativação ou reativação já aberto, a retirada entre iniciar e confirmar faz a confirmação responder como aluno inexistente, sem gravar.
- [ ] T165 [P] [US5] **W-M** Teste Vitest `FE_T/pages/admin/StudentAssociationsSection.test.tsx`: aviso de quantos relatórios ficam congelados vindo de `removal-preview`, confirmação que **não bloqueia**, cancelar não grava, só professores ativos podem ser incluídos.
- [ ] T166 [P] [US5] **X-2** Teste e2e `FE_T/e2e/admin-associations.a11y.spec.ts`: diálogo de retirada acessível (foco preso, `Esc`, aviso anunciado) nos dois temas e larguras.

### Implementação da US5

- [ ] T167 [US5] **W-C2** Implementar `AssignTeacherToStudentUseCase`, `RemoveTeacherFromStudentUseCase` e `PreviewAssignmentRemovalUseCase` em `CORE/usecase/registry/` (e o filtro de associação em `OpenReportsQueryPort`). (após T160)
- [ ] T168 [P] [US5] **W-G** Implementar `delete`, `listTeachersOfStudent` e `countActiveTeachers` em `APP/outbound/persistence/AssignmentRepositoryAdapter.java` e o filtro de associação em `OpenReportsQueryAdapter.java`. (após T161, T101)
- [ ] T169 [US5] **W-H** Criar `APP/inbound/web/admin/AdminAssignmentController.java` (`PUT/DELETE /admin/students/{studentId}/teachers/{teacherId}`, `GET …/removal-preview`) e DTOs/Mappers. (após T162)
- [ ] T170 [US5] **W-M** Criar a seção de associações em `FE/pages/admin/StudentAssociationsSection.tsx` (usada por `StudentDetailScreen.tsx`) e `FE/services/admin/assignments.ts`, com `ConfirmationDialog` que mostra `frozenReports`. (após T165, T125)
- [ ] T171 [US5] **COORD** Validar a US5 contra `SPEC/quickstart.md` (cenário 9) depois da integração (S5). (após T167, T168, T169, T170)

**Checkpoint**: US5 testável sozinha, somada às US3/US4 já prontas.

---

## Phase 8: User Story 6 — Código de acesso e vínculo do Telegram do professor (Priority: P2)

**Goal**: o administrador reemite o código de qualquer professor ativo (o anterior morre na hora; sessões Web caem; vínculo do Telegram só cai se ele marcar a opção) e desvincula o Telegram, descartando ações pendentes e rascunho.

**Independent Test**: reemitir o código de um professor com sessão Web e Telegram ativos, com e sem a opção; usar o código antigo e o novo; desvincular e conferir que o professor precisa enviar o código de novo.

### Testes da US6 (escrever primeiro; devem FALHAR)

- [ ] T172 [P] [US6] **W-B** Testes `CORE_T/usecase/teacher/ReissueAccessCodeUseCaseTest.java` (código antigo inválido na hora; novo de 8 caracteres/7 dias exibido uma vez; sessões Web revogadas com `CODE_REISSUED`; vínculo mantido sem a opção e encerrado com ela; professor inativo → recusa orientando reativar; autoria `TEACHER_REISSUE_ACCESS_CODE`) e `UnlinkTeacherTelegramUseCaseTest.java` (vínculo encerrado, ações pendentes e rascunho descartados; o professor precisa de código válido; autoria `TEACHER_UNLINK_TELEGRAM`).
- [ ] T173 [P] [US6] **W-H** Teste de contrato `APP_T/inbound/web/admin/AdminTeacherAccessContractIT.java`: `POST /admin/teachers/{teacherId}/access-code` (corpo com a opção de encerrar o vínculo; código só na resposta) e `DELETE /admin/teachers/{teacherId}/telegram-link`.
- [ ] T174 [P] [US6] **W-J** Teste `APP_T/inbound/telegram/UnlinkedTeacherConversationIT.java`: após desvincular, a conta passa a receber `CODE_REQUIRED`; o código **antigo** responde `INVALID_CODE` (a mesma mensagem de código inexistente) e o novo vincula; reemissão **sem** encerrar o vínculo **não** descarta um cadastro em andamento; com encerramento, descarta.
- [ ] T175 [P] [US6] **X-1** Teste `APP_T/security/RevocationImmediacyReissueUnlinkIT.java` (SC-010, Postgres real): efeito imediato da reemissão (Web) e da desvinculação (Web e Telegram); ações `PENDING` do chat descartadas; nenhuma requisição posterior aceita.
- [ ] T176 [P] [US6] **X-1** Teste `APP_T/security/AuthorshipAccessOperationsIT.java` (SC-014): `TEACHER_REISSUE_ACCESS_CODE` e `TEACHER_UNLINK_TELEGRAM` deixam registro sem o código.
- [ ] T177 [P] [US6] **W-M** Teste Vitest `FE_T/pages/admin/TeacherAccessActions.test.tsx`: diálogo de reemissão nomeia ação e professor e oferece a caixa "Também encerrar o vínculo do Telegram"; diálogo de desvinculação; o código novo vai ao `OneTimeSecretPanel`; cancelar não grava; professor inativo mostra a orientação de reativar.
- [ ] T178 [P] [US6] **X-2** Teste e2e `FE_T/e2e/admin-teacher-access.a11y.spec.ts`: os diálogos de reemissão e desvinculação acessíveis nos dois temas e larguras.

### Implementação da US6

- [ ] T179 [US6] **W-B** Implementar `ReissueAccessCodeUseCase` e `UnlinkTeacherTelegramUseCase` em `CORE/usecase/teacher/` (usam `SessionRevocationService` e `TelegramLinkTerminationService`). (após T172, T093)
- [ ] T180 [US6] **W-H** Acrescentar `POST …/access-code` e `DELETE …/telegram-link` a `APP/inbound/web/admin/AdminTeacherController.java` e DTOs. (após T173, T102)
- [ ] T181 [US6] **W-J** Garantir em `APP/inbound/telegram/` que, após desvinculação, a conta recebe a orientação de entrada e o código antigo responde `INVALID_CODE` (ajuste em `TelegramConversationInterpreter`/`TelegramPresenter`, se o teste T174 exigir). (após T174, T156)
- [ ] T182 [US6] **W-M** Acrescentar reemissão e desvinculação a `FE/pages/admin/TeacherDetailScreen.tsx` e `FE/services/admin/teachers.ts`. (após T177, T105)
- [ ] T183 [US6] **COORD** Validar a US6 contra `SPEC/quickstart.md` (cenário 10) depois da integração (S5). (após T179, T180, T181, T182)

**Checkpoint**: acesso e vínculo do professor gerenciáveis sem a carga inicial.

---

## Phase 9: User Story 7 — Administradores (Priority: P2)

**Goal**: criar administradores com código de ativação de uso único (12 caracteres), ativar (definir a senha) na Web, inativar e reativar; nunca zero administradores ativos, inclusive sob concorrência.

**Independent Test**: criar, ativar com o código, inativar o primeiro, tentar inativar o último, tentar duas inativações simultâneas; tudo na Web.

### Testes da US7 (escrever primeiro; devem FALHAR)

- [ ] T184 [P] [US7] **W-A** Testes `CORE_T/usecase/administrator/`: `CreateAdministratorUseCaseTest.java` (e-mail único **por papel**, sem diferenciar caixa nem espaços, inclusive inativos; código de 12 caracteres, 7 dias, exibido uma vez; um pendente por administrador, emitir outro invalida o anterior; nasce ativo, sem senha), `ActivateAdministratorUseCaseTest.java` (e-mail + código; **mesma** mensagem `INVALID_ACTIVATION` para código errado, vencido ou de conta inativa; bloqueio `ADMIN_ACTIVATION` 5/15 min; senha segue `PasswordPolicy`; uso único; quem criou pode usar; autoria `ADMIN_ACTIVATE`), `InactivateAdministratorUseCaseTest.java` (`LAST_ADMINISTRATOR` para o último, inclusive a si mesmo; revoga sessões; apaga hash e código), `ReactivateAdministratorUseCaseTest.java` (volta como pendente com código novo) e `ListAdministratorsUseCaseTest.java` ("ativação pendente" / "ativado em").
- [ ] T185 [P] [US7] **W-E** Testes Postgres real `APP_T/outbound/persistence/AdministratorConcurrencyIT.java` (duas inativações simultâneas com `lockAllActiveForUpdate` → sobra ≥ 1 ativo; `insertIfAbsent` de e-mail concorrente; índice único parcial do código pendente) e `ActivationCodeGeneratorAdapterTest.java` + ampliação de `DataProtectionAdapterOriginHashTest.java` com `hashActivationCode` (resumo com chave secreta derivada do `AUTH_HMAC_SECRET`, nunca simples; trocar o `HMAC_SECRET` não invalida o código de ativação).
- [ ] T186 [P] [US7] **W-F** Teste Postgres real `APP_T/outbound/persistence/ActivationAttemptsConcurrencyIT.java`: contagem atômica do escopo `ADMIN_ACTIVATION`, separada do login.
- [ ] T187 [P] [US7] **W-H** Teste de contrato `APP_T/inbound/web/admin/AdminAdministratorContractIT.java`: `GET/POST /admin/administrators` (código de ativação só na resposta), `inactivation`, `reactivation`, e `POST /admin/activation` (pública, com e-mail e código).
- [ ] T188 [P] [US7] **X-1** Teste `APP_T/security/LastAdministratorConcurrencyIT.java` (SC-009, Postgres real): inativações simultâneas e cruzadas sempre terminam com ≥ 1 administrador ativo.
- [ ] T189 [P] [US7] **X-1** Teste `APP_T/security/RevocationImmediacyAdminInactivationIT.java` (SC-010): inativar administrador derruba todas as sessões dele na requisição seguinte.
- [ ] T190 [P] [US7] **X-1** Teste `APP_T/security/AuthorshipAdministratorOperationsIT.java` (SC-014): `ADMINISTRATOR_CREATE/INACTIVATE/REACTIVATE` e `ADMIN_ACTIVATE` deixam registro sem código nem senha.
- [ ] T191 [P] [US7] **W-J** Teste `APP_T/inbound/telegram/ActivationCodeNotAcceptedByBotIT.java`: código de ativação ou senha de administrador enviados ao bot **não** vinculam nada (só o código de acesso do professor é aceito: `INVALID_CODE`).
- [ ] T192 [P] [US7] **W-M** Testes Vitest `FE_T/pages/admin/AdministratorsScreen.test.tsx` (lista com "ativação pendente"/"ativado em"; criar → painel do código; inativar com confirmação; o último administrador mostra `LAST_ADMINISTRATOR`) e `FE_T/pages/admin/ActivateScreen.test.tsx` (A02: e-mail, código em maiúsculas com `autocomplete="off"`, nova senha e confirmação com `autocomplete="new-password"`, regra "12 a 128 caracteres" visível, sem medidor de força).
- [ ] T193 [P] [US7] **X-2** Teste e2e `FE_T/e2e/admin-administrators.a11y.spec.ts` (A09 e A02) nos dois temas e larguras.

### Implementação da US7

- [ ] T194 [US7] **W-A** Implementar `CORE/service/ActivationCodeIssuanceService.java` e `CreateAdministratorUseCase`, `ActivateAdministratorUseCase`, `InactivateAdministratorUseCase`, `ReactivateAdministratorUseCase` e `ListAdministratorsUseCase` em `CORE/usecase/administrator/`. (após T184, T059)
- [ ] T195 [US7] **W-E** Implementar `APP/outbound/security/ActivationCodeGeneratorAdapter.java`, `hashActivationCode` em `DataProtectionAdapter.java` e `lockAllActiveForUpdate` em `Administrator*`. O índice único parcial já está na `V9` (T064): **não** criar migração nova, pois as migrações são V9 a V14, sem renumerar (plano, regra 5). (após T185, T065)
- [ ] T196 [US7] **W-F** Aplicar o escopo `ADMIN_ACTIVATION` nos adaptadores `APP/outbound/persistence/AccessAttempt*`. (após T186, T062)
- [ ] T197 [US7] **W-H** Criar `APP/inbound/web/admin/AdminAdministratorController.java` e acrescentar `POST /admin/activation` a `AdminSessionController.java` (rota pública, com bloqueio e resposta única). (após T187, T069)
- [ ] T198 [US7] **W-M** Criar `FE/pages/admin/AdministratorsScreen.tsx` (A09), `FE/pages/admin/ActivateScreen.tsx` (A02) e `FE/services/admin/administrators.ts`; registrar em `adminRoutes.tsx`. (após T192, T104)
- [ ] T199 [US7] **COORD** Validar a US7 contra `SPEC/quickstart.md` (cenário 11) depois da integração (S5). (após T194, T195, T196, T197, T198)

**Checkpoint**: a escola gerencia a própria equipe administrativa pela Web.

---

## Phase 10: User Story 8 — Primeiro administrador (bootstrap) (Priority: P3)

**Goal**: criar o primeiro administrador na subida a partir do `.env` (só se não existir nenhum), com senha provisória trocada no primeiro acesso; nenhuma credencial padrão no repositório; recuperação por remoção dos administradores no banco.

**Independent Test**: em ambiente sem administradores, subir com as variáveis preenchidas, entrar com a senha provisória, definir a nova e confirmar que não há credencial padrão em arquivo versionado nem em log; subir de novo com outro `.env` e confirmar que nada muda.

### Testes da US8 (escrever primeiro; devem FALHAR)

- [ ] T200 [P] [US8] **W-A** Testes `CORE_T/usecase/administrator/BootstrapFirstAdministratorUseCaseTest.java` (as 4 situações: nenhum + variáveis válidas → cria ativo, hash, **provisória**; já existe algum → ignora; nenhum + ausentes → aviso em português; senha fora do FR-004 ou e-mail inválido → recusa subir citando a variável sem o valor; criação atômica `insertIfNoneExists`; autoria `FIRST_ADMINISTRATOR_BOOTSTRAP` com autor "sistema" e origem `SYSTEM_STARTUP`) e `ChangeProvisionalPasswordUseCaseTest.java` (nova senha ≠ provisória e conforme `PasswordPolicy`; a provisória deixa de valer; `PASSWORD_CHANGE_NOT_REQUIRED` fora do caso; todas as outras sessões do administrador revogadas (`PASSWORD_CHANGED`) e a atual com identificador novo; senha provisória vencida (7 dias) não autentica, com a mesma mensagem de credencial inválida; autoria `ADMIN_CHANGE_PASSWORD`).
- [ ] T201 [P] [US8] **W-K** Testes `APP_T/inbound/bootstrap/FirstAdministratorBootstrapIT.java` (Postgres real: 4 situações na subida; duas instâncias subindo juntas criam no máximo um; a senha nunca em log; aviso em português) e `AdministratorRecoveryIT.java` (remover os administradores preserva `authorship_record`; reiniciar com o `.env` recria o primeiro com senha provisória; não existe variável, rota ou comando que redefina a senha de um existente).
- [ ] T202 [P] [US8] **W-H** Testes `APP_T/inbound/web/admin/ProvisionalPasswordRestrictionIT.java` (enquanto provisória, toda outra rota administrativa → `403 PASSWORD_CHANGE_REQUIRED`; uma segunda sessão aberta com a provisória é revogada quando a troca acontece, e não ganha acesso total) e contrato de `PUT /admin/password` em `AdminSessionContractIT`-irmão `AdminPasswordContractIT.java`.
- [ ] T203 [P] [US8] **X-1** Teste `APP_T/security/NoDefaultCredentialsTest.java`: nenhum arquivo versionado contém senha, hash ou segredo de administrador; as variáveis `ADMIN_BOOTSTRAP_*` do `infra/.env.example` estão vazias; a senha provisória e o hash nunca aparecem em log nem em `authorship_record`.
- [ ] T204 [P] [US8] **W-M** Teste Vitest `FE_T/pages/admin/ChangePasswordScreen.test.tsx` (A03: só a nova senha e a confirmação; enquanto `mustChangePassword`, qualquer outra rota administrativa redireciona para cá).
- [ ] T205 [P] [US8] **X-2** Teste e2e `FE_T/e2e/admin-change-password.a11y.spec.ts` (A03) nos dois temas e nas duas larguras, navegação por teclado e erro de senha anunciado.

### Implementação da US8

- [ ] T206 [US8] **W-A** Implementar `BootstrapFirstAdministratorUseCase` e `ChangeProvisionalPasswordUseCase` em `CORE/usecase/administrator/`. (após T200, T194)
- [ ] T207 [US8] **W-K** Criar `APP/inbound/bootstrap/FirstAdministratorBootstrap.java` (lê `reports.administrator.bootstrap.*`; recusa subir com mensagem em português citando a variável, nunca o valor; ator `Actor.system(SYSTEM_STARTUP)`) e fazer `infra/scripts/local.sh up` preencher o administrador fictício local (`admin@example.test`, senha aleatória impressa **uma vez** no terminal). (após T201; integração real com T206 no Fecho — até lá, dublê do `BootstrapFirstAdministratorUseCase`)
- [ ] T208 [US8] **W-H** Acrescentar `PUT /admin/password` a `AdminSessionController.java` e a restrição de ator com `passwordChangeRequired` em `AdminSessionFilter`. (após T202, T197)
- [ ] T209 [US8] **W-M** Criar `FE/pages/admin/ChangePasswordScreen.tsx` (A03) e a regra de redirecionamento de `AdminSessionGuard` para `mustChangePassword`. (após T204, T072)
- [ ] T210 [US8] **COORD** Validar a US8 e a recuperação contra `SPEC/quickstart.md` (cenário 2 e §5) depois da integração (S5). (após T206, T207, T208, T209)

**Checkpoint**: com US1 + US8 o produto é entregável; as demais histórias já estão prontas.

---

## Phase 11: Carga inicial e operação (FR-044, SC-011 — transversal, sem rótulo de história)

**Propósito**: migrar a carga inicial para os mesmos casos de uso da administração (sem regra própria) e prover a rotação de chaves do CPF. Pode rodar em paralelo com as histórias; a integração real precisa dos casos de uso das US2, US3 e US5 prontos.

### Testes (escrever primeiro; devem FALHAR)

- [ ] T211 [P] **W-K** Testes `CORE_T/usecase/seed/LoadInitialRegistryUseCaseTest.java` (usa dublê de `RegisterTeacher/UpdateTeacher/RegisterStudent/UpdateStudent/AssignTeacherToStudent`; ator `Actor.system(INITIAL_LOAD)`; tudo ou nada; idempotente por e-mail e por resumo do CPF; erros por seção, índice e campo, **nunca** pelo valor do CPF; campos `guardianPhone`/`guardianEmail` obrigatórios para menor) e `RotateCpfKeysUseCaseTest.java` (só contagens; detecta colisão).
- [ ] T212 [P] **W-K** Atualizar `APP_T/inbound/seed/SeedCommandIT.java`, `DemoDataIT.java` e criar `IssueCodeCommandIT.java` e `RotateCpfKeyCommandIT.java`: `seed` usa os casos de uso e grava autoria com origem `INITIAL_LOAD`; `--simulate` roda numa transação **desfeita** (`runAndRollBack`) com as mesmas contagens e erros (inclusive de unicidade do banco) e **sem** autoria gravada; `issue-code` chama `ReissueAccessCodeUseCase` (encerra as sessões Web e **não** o vínculo); `rotate-cpf-key` lê as chaves de variáveis de ambiente, nunca imprime CPF, exige `--confirm-invalidate-codes` quando o segredo do resumo muda e avisa o efeito (invalida só os códigos de acesso do professor; ativação e origem usam o `AUTH_HMAC_SECRET` e não mudam).
- [ ] T213 [P] **X-1** Teste `APP_T/privacy/InitialLoadNoCpfLeakIT.java`: saída do terminal, log e autoria da carga e da rotação não contêm o `CanaryCpf` nem os dados do `CanaryStudent` (nome, nascimento e contato do responsável); erros da carga citam seção, índice e campo, nunca o valor.

### Implementação

- [ ] T214 **W-K** Reescrever `CORE/usecase/seed/LoadInitialRegistryUseCase.java` para orquestrar os casos de uso novos e implementar `RotateCpfKeysUseCase.java` e `APP/outbound/security/CpfKeyRotationAdapter.java`. (após T211; integração real com T119 e T094 no Fecho — até lá, dublês dos casos de uso orquestrados)
- [ ] T215 **W-K** Atualizar `APP/inbound/seed/SeedCommand.java`, `IssueCodeCommand.java`, `DemoDataCommand.java` e criar `RotateCpfKeyCommand.java` (perfil `seed`). (após T212, T214)
- [ ] T216 [P] **W-K** Atualizar `infra/seed/example.json` e `infra/seed/demo.json` com `guardianPhone` e `guardianEmail` (dados **fictícios**: CPFs de teste válidos, e-mails `@example.test`) e `infra/scripts/local.sh` (`seed`, `issue-code`, `rotate-cpf-key`, mensagem de uso). (após T215)
- [ ] T217 **COORD** Validar o cenário 15 de `SPEC/quickstart.md` (`sh infra/scripts/local.sh seed` com `--simulate` e sem) e confirmar SC-011. (após T215, T216)

---

## Phase 12: Fecho — integração, documentação e revisão (fatia S5)

**Propósito**: ligar tudo, rodar os testes transversais verdes e atualizar a documentação. Pré-condição: S2, S3 e S4 mescladas.

### Testes transversais e e2e

- [ ] T218 [P] **X-1** Rodar `APP_T/security/PermissionMatrixCrossAccessIT.java` (T048) com a regra do Fecho: **zero** linhas puladas; qualquer operação sem rota mapeada reprova (SC-005). Ajustar apenas o próprio teste.
- [ ] T219 [P] **X-1** Criar `APP_T/security/AuthorshipCoverageIT.java` (SC-014): enumera a lista de operações do FR-040 (`SPEC/data-model.md` §6) e exige que cada uma, ao ser executada, deixe um registro com papel, canal e resultado corretos e sem CPF nem dado do responsável; leituras e expiração de sessão **não** geram registro.
- [ ] T220 [P] **X-1** Criar `APP_T/inbound/web/AdministrationContractDriftIT.java`: valida **toda** resposta real contra `api-administration.openapi.yaml` e confirma que nenhuma propriedade se chama `cpf`.
- [ ] T221 [P] **X-1** Rodar e consolidar `APP_T/privacy/*` e `APP_T/security/*` desta feature com `CanaryCpf` e `CanaryStudent` em todo o conjunto (SC-004), incluindo mensagens de exceção e rastros de pilha, e a varredura de logs (`LogWithoutSensitiveDataIT`, estender o existente só se faltar cobertura).
- [ ] T222 [P] **X-2** Rodar `sh infra/scripts/local.sh tests e2e`: as 15 telas (A01 a A12, P01 a P03) em claro/escuro e 390/1440 px sem violação séria/crítica (SC-012); registrar o resultado em `SPEC/evidence/e2e-a11y.md`.
- [ ] T236 [P] **X-1** Teste de autorização no núcleo `CORE_T/usecase/UseCasePermissionMatrixTest.java` (FR-002, FR-048, SC-005), parametrizado pela `PermissionMatrix` (lida com o `MatrixLoader`): cada `*UseCase` de `usecase.administrator`, `usecase.teacher` e `usecase.registry` é chamado com cada `Actor` (administrador Web, professor Web, professor Telegram, sistema) e, onde a matriz diz `DENY`, o resultado é `RESOURCE_NOT_FOUND` sem gravar dado de negócio (só o registro de autoria `AUTHORIZATION_DENIED` da negativa) nem tocar repositório de negócio; caso de uso sem `Operation` mapeada reprova; operações `PUBLIC` e de sistema vêm da própria matriz. Prova que a autorização é do núcleo e não depende da cadeia de segurança da Web. Fica vermelho até cada fluxo implementar seus casos de uso; fecha no Fecho (não entra no gate T034, cujos esqueletos lançam `UnsupportedOperationException`).
- [ ] T237 [P] **X-1** Teste de privacidade `APP_T/privacy/StudentDataNotSentToAiIT.java` (FR-017, Princípio IV): com um dublê da porta de IA que captura a entrada e com o `CanaryStudent` e o `CanaryCpf` num aluno, percorre a geração do relatório de aula e do semestral; falha se a entrada enviada à IA, o prompt ou o relatório contiver CPF ou qualquer dado do responsável, ou campo de aluno além do que o contrato da 001 já autoriza enviar.

### Documentação (X-3)

- [ ] T223 [P] **X-3** Atualizar `backend/core/CLAUDE.md` (pacotes `access`, `registry`; `Actor`, `PermissionMatrix`, `StudentScope`; casos de uso por operação; regras de autoria e de escopo no núcleo).
- [ ] T224 [P] **X-3** Atualizar `backend/application/CLAUDE.md` (adaptadores `inbound/web/admin`, `inbound/bootstrap`, `inbound/telegram/TelegramStudentRegistryFlow`; cadeia de segurança do administrador; migrações `V9` a `V14`; ArchUnit novo).
- [ ] T225 [P] **X-3** Atualizar `frontend/CLAUDE.md` (áreas `admin` e `my-students`, `AdminSessionGuard`, `OneTimeSecretPanel`, segundo gerador de tipos, regras de privacidade do CPF no frontend).
- [ ] T226 [P] **X-3** Atualizar `infra/CLAUDE.md`, `infra/n8n/CLAUDE.md` (ação `DELETE_MESSAGE`; continua só transporte) e `infra/telegram-simulator/CLAUDE.md` (`deleteMessage`).
- [ ] T227 [P] **X-3** Atualizar `specs/CLAUDE.md` (conferir a linha da 005 já presente; acrescentar o que faltar) e, se o roteamento mudou, `.claude/CLAUDE.md`; conferir a raiz `CLAUDE.md` (nenhuma estrutura nova de primeiro nível → sem linha nova na tabela; registrar em `SPEC/evidence/` que foi verificado).
- [ ] T228 [P] **X-3** Atualizar `infra/README.md` e `infra/scripts/README.md`: variáveis novas (tabela com padrão e função), subida com o primeiro administrador, **procedimento de recuperação** (parar o backend, `DELETE FROM administrator;`, preencher `ADMIN_BOOTSTRAP_*`, reiniciar, trocar a senha e apagar a senha do `.env`), **rotação de chaves do CPF**, comandos `seed`/`issue-code` (emergência)/`rotate-cpf-key`, e o aviso de que o n8n não guarda execuções (privacidade do CPF).
- [ ] T229 [P] **X-3** Conferir `infra/.env.example`: todas as variáveis de T006 presentes, vazias onde devem ser, sem segredo, coerentes com a tabela do `infra/README.md`; ajustar só o que divergir.
- [ ] T230 [P] **X-3** Nota (sem reescrever) em `specs/001-fluxo-relatorios/spec.md` remetendo à feature 005, nos trechos que citam a secretaria: Escopo (linha ~29), jornada do código (97), cenário do bot sem vínculo (107), casos de borda (187 a 189), FR-011 (217) e Assumptions (331 a 334, **inclusive o link "É da secretaria? Acesse a área administrativa."**, que passa a "É administrador? Acesse a área administrativa." e é exibido agora, FR-043).
- [ ] T231 [P] **X-3** Nota em `specs/001-fluxo-relatorios/contracts/mensagens.md` (textos de `CODE_REQUIRED`, `INVALID_CODE`, `EXPIRED_CODE` removido, `STUDENT_NOT_ASSIGNED` já removido na emenda da 005, `TELEGRAM_ALREADY_LINKED`, `TEACHER_ALREADY_LINKED`, `NO_STUDENTS`) apontando para `SPEC/contracts/messages.md`; e em `specs/001-fluxo-relatorios/contracts/carga-inicial.md` apontando para `SPEC/contracts/initial-load.md` (versão vigente; `issue-code` agora encerra sessões Web).
- [ ] T232 [P] **X-3** Nota em `specs/001-fluxo-relatorios/contracts/api-portal.openapi.yaml` (descrição de `POST /session`: `EXPIRED_CODE` removido, mensagem única `INVALID_CODE`, novo `ACCESS_ENDED`), em `specs/001-fluxo-relatorios/contracts/rotas-portal.md` (se citar o link de entrada), em `specs/001-fluxo-relatorios/research.md` (linhas ~288 a 292), `plan.md` (linha ~97) e uma nota no topo de `specs/002-api-interna-canal-telegram/spec.md` sobre a ação `DELETE_MESSAGE`. Cada nota no formato `> **Nota (feature 005)**: …`, em português, com link relativo.

### Revisão e fechamento (X-4)

- [ ] T233 **X-4** Rodar a skill `pre-pr-review` sobre o diff de cada fatia: `backend-reviewer`, `frontend-reviewer`, `infra-security-auditor`, `contract-drift-checker`, `privacy-auditor`, `ai-pipeline-reviewer` (se tocar prompts ou IA; esperado: nada) e, por último, `constitution-auditor`; consolidar um relatório único em `SPEC/evidence/review.md`. Confirmar que `.github/workflows/ci.yml` continua cobrindo as suítes novas (`tests-backend`, `tests-frontend`, `tests-e2e`, `tests-n8n`) sem alteração; se algum teste novo precisar de serviço extra no `infra/compose.test.yaml`, registrar em `SPEC/evidence/review.md`. (após T218 a T232, T236, T237)
- [ ] T234 **COORD** Validar `SPEC/quickstart.md` por inteiro (cenários 1 a 17 e as suítes da §4: `tests backend`, `tests frontend`, `tests e2e`, `tests n8n`, `tests all`) e `GUARD_REPORT=1` sem pendência. (após T233)
- [ ] T235 **COORD** Abrir o PR de cada fatia ao concluí-la, **sem esperar nova solicitação** (`/open-pr`), nunca mesclar nem habilitar merge automático, e rodar `code-review-graph build` depois de **cada** PR (Princípio VIII). (após T234)

---

## Dependências e ordem de execução

### Entre fases

- **Setup (Phase 1)** → sem dependências.
- **Fundação (Phase 2)** → depende do Setup (T002 para T007); **bloqueia** as Phases 3 a 12.
- **Histórias (Phases 3 a 10)** → dependem só da Fundação mesclada; podem correr **todas ao mesmo tempo** em fluxos distintos. Ordem por prioridade (P1: US1 a US4; P2: US5 a US7; P3: US8) apenas quando houver um só subagente por fluxo.
- **Carga inicial (Phase 11)** → depende da Fundação; sua integração real precisa de US2, US3 e US5.
- **Fecho (Phase 12)** → depende de S2, S3 e S4 mescladas.

### Entre histórias (acoplamentos reais e como são desfeitos)

- **US1 ⇄ US8**: US1 é testável com `AdministratorFixture`; só a **entrega comercial** exige US8 junto (sem bootstrap não há administrador).
- **US2 → US6**: US6 estende `TeacherDetailScreen` e `AdminTeacherController` criados na US2 (mesmos fluxos `W-M` e `W-H`, em sequência).
- **US3 → US4/US5**: US4 e US5 estendem casos de uso e telas da US3 (mesmos fluxos, em sequência); o `StudentForm` (T124) é compartilhado.
- **US7 ← US1**: ativação e criação estendem `AdminSessionController` da US1.
- **US8 ← US1/US7**: a troca da senha provisória estende o filtro e a tela de sessão.

### Dentro de cada história

1. Testes (vermelhos) → 2. núcleo/domínio → 3. persistência/migração → 4. adaptadores de entrada (Web, Telegram) → 5. frontend → 6. validação no quickstart (após a integração).
2. Cada fluxo termina **seus** testes antes de passar à próxima história.

### Oportunidades de paralelismo

- Toda tarefa [P] de testes de uma história pode ser distribuída a subagentes diferentes (arquivos exclusivos).
- Na Onda 2 rodam ao mesmo tempo: núcleo (`W-A`, `W-B`, `W-C1`, `W-C2`, `W-D`), persistência (`W-E`, `W-F`, `W-G`), carga (`W-K`), canais (`W-H`, `W-I`, `W-J`, `W-L`), frontend (`W-M`, `W-N`) e os redatores `X-1`/`X-2`.

### Exemplo: distribuição da US4 (a maior)

```text
Subagente W-C1: T127 → T149      Subagente W-C2: T128 → T150     Subagente W-D: T129 → T151
Subagente W-G : T130 → T152      Subagente W-L : T131, T132 → T153 → T154
Subagente W-I : T133 → T155      Subagente W-J : T134, T135 → T156
Subagente W-N : T144, T145 → T157 → T158
Subagente X-1 : T136 a T143, T148      Subagente X-2 : T146, T147
```

---

## Rastreabilidade dos critérios de sucesso

| Critério | Testes que o cobrem (escritos antes da implementação) |
|---|---|
| SC-003 (paridade; zero operação administrativa pelo Telegram) | T140, T054, T070 |
| SC-004 (zero CPF em logs/respostas/telas/bot/autoria; zero dado de menor e do responsável em log/erro/autoria) | T113, T141, T213, T221, T237 |
| SC-005 (credencial cruzada, gerada da matriz; autorização no núcleo) | T048, T218, T236 |
| SC-006 (isolamento e indistinguibilidade) | T136, T137, T138, T139, T163 |
| SC-007 (nenhum papel/rota/tela para aluno ou responsável) | T049, T050 |
| SC-008 (bloqueio e sessão de 2 h) | T038, T039, T052 |
| SC-009 (nunca zero administradores) | T185, T188 |
| SC-010 (revogação imediata) | T087, T175, T189 |
| SC-011 (carga sem regra própria) | T211, T212, T217 |
| SC-012 (WCAG 2.1 AA, dois temas, 390/1440 px) | T057, T092, T117, T146, T166, T178, T193, T205, T222 |
| SC-013 (nada de relatório alterado) | T075, T081, T109, T160, T163 |
| SC-014 (autoria por operação) | T053, T080, T089, T114, T142, T176, T190, T219 |

---

## Estratégia de implementação

### Entrega mínima (MVP)

1. Setup + Fundação (S1) → gate T034.
2. S2, S3 e S4 (camadas) em paralelo, priorizando dentro de cada fatia as tarefas de **US1 + US8** (acesso e primeiro administrador).
3. S5 (Fecho): integrar e validar primeiro **US1 + US8** (entrada, sessão fixa, bloqueio, credencial cruzada, bootstrap, troca da senha provisória). **Só depois do S5 há algo implantável**: as fatias por camada não são entregáveis isoladamente.

### Entrega incremental

1. US2 (professores) → US3 (alunos pelo administrador) → US4 (alunos pelo professor, Web e Telegram) → **a escola já opera sem a carga inicial** (P1 completo).
2. US5 (associações) → US6 (código e Telegram) → US7 (administradores) (P2).
3. Carga inicial convertida e rotação de chaves; Fecho (documentação e revisão).
4. Cada história é validada de ponta a ponta no S5, na ordem de prioridade, sem quebrar as anteriores; a implantação é única, depois do Fecho.

### Estratégia com subagentes em paralelo

1. Um agente `F-*` (cinco subagentes coordenados) conclui a Fundação e o `COORD` mescla a S1.
2. Cada subagente recebe: o **fluxo** (seus IDs), o **dono de** (arquivos exclusivos), as regras de convivência acima, os documentos a ler (`spec.md`, `plan.md`, `data-model.md`, o contrato do seu fluxo) e o comando de verificação do seu escopo (`local.sh tests backend|frontend|e2e|n8n`).
3. Cada subagente trabalha em worktree própria, escreve os testes vermelhos, implementa até ficarem verdes, roda o guardião de idioma e **para** (sem PR nem merge); o `COORD` integra por fatia.

---

## Notas

- [P] = arquivo exclusivo e sem dependência aberta (as dependências remanescentes vêm escritas como "(após …)").
- O rótulo [US#] liga a tarefa à história para rastreabilidade; o **fluxo** em negrito diz quem executa.
- Verifique que os testes falham antes de implementar; commite por tarefa ou grupo lógico, na branch da fatia.
- Evite tarefas vagas, conflitos de arquivo e dependências entre histórias que quebrem a independência.
- Código em inglês; textos ao usuário e logs em português acentuado; documentação, comentários, commits e PRs em português (Princípio VI).
