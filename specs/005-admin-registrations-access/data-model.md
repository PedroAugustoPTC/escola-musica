# Data Model: Administração — acesso e cadastros

**Feature**: `005-admin-registrations-access` | **Spec**: [spec.md](./spec.md) | **Pesquisa**: [research.md](./research.md)

Modelo lógico do que a feature acrescenta ou altera. Nomes de tabela, coluna, enumeração e classe em **inglês**
(Princípio VI); texto ao usuário em português (catálogo). Migrações Flyway novas **`V9` a `V14`** (a `V1` a `V8` são
imutáveis); cada fluxo de trabalho do plano é dono de uma migração. Valores padrão e carimbos de tempo são preenchidos
pela aplicação (convenção da 001). "FK" ausente é deliberado onde dito.

## 1. Visão geral

```text
administrator 1 ── * administrator_session
teacher 1 ── * web_session                      (já existe; ganha revoked_at / revocation_reason)
teacher 1 ── 0..1 telegram_link 1 ── 0..1 student_registration_draft   (cascata)
teacher * ── * student            via teacher_student_assignment       (já existe)
authorship_record                                (sem FK: sobrevive à remoção de administradores)
access_attempt                                   (já existe; ganha scope e window_started_at)
```

Papéis autenticáveis: **`ADMINISTRATOR` e `TEACHER`**, nada mais (FR-001). Aluno e responsável não são tabelas de
conta: o responsável é `guardian_*` dentro de `student`.

## 2. Núcleo (domínio) — tipos novos e alterados

| Tipo (pacote `domain/…`) | Descrição |
|---|---|
| `access/Role` | enum `ADMINISTRATOR`, `TEACHER` (exatamente dois; teste enumera) |
| `access/ActionOrigin` | enum `WEB`, `TELEGRAM`, `SYSTEM_STARTUP`, `INITIAL_LOAD` (canal do registro de autoria) |
| `access/Actor` | `record(Role role, UUID id, ActionOrigin origin, boolean passwordChangeRequired)`; fábricas `administrator(...)`, `teacher(...)`, `system(origin)` (sem papel e sem id; só `inbound.seed` e bootstrap) |
| `access/Operation` | enum das operações da matriz (seção 8) |
| `access/PermissionMatrix` | decide `Permission` (`ALLOW`, `ALLOW_OWN_STUDENTS`, `ALLOW_SOLE_ACTIVE_TEACHER`, `PUBLIC`, `DENY`) por `(Operation, Role, ActionOrigin)`; igual a `contracts/permission-matrix.json` |
| `access/Administrator` | entidade (seção 3.1) |
| `access/AdministratorSession` | sessão fixa de 2 h, revogável (seção 3.2) |
| `access/ActivationCode` | 12 caracteres do alfabeto sem ambíguos; `looksLikeCode` e normalização (sem espaço/hífen, maiúsculas) |
| `access/PasswordPolicy` | 12–128 pontos de código (NFC), recusa igual ao e-mail/nome (sem diferenciar caixa) |
| `access/AttemptScope`, `access/AttemptPolicy` | `TEACHER_CODE`, `ADMIN_SIGN_IN`, `ADMIN_ACTIVATION`, `TEACHER_CPF_PROBE`; `(maxAttempts, lockDuration, window?)` |
| `access/AccessAttempt` (alterado) | ganha `scope`, `windowStartedAt`; falha em bloqueio ativo não conta nem prorroga; sucesso não zera bloqueio ativo |
| `access/WebSession` (alterado) | ganha `revokedAt`, `revocationReason` (`ACCOUNT_INACTIVATED`, `CODE_REISSUED`) |
| `access/AuthorshipRecord`, `AuthorshipResult` | registro de autoria (seção 3.4); `SUCCESS`, `FAILURE`, `DENIED`, `BLOCKED` |
| `registry/PersonName`, `EmailAddress`, `BrazilianPhone`, `BirthDate`, `Instrument` | objetos de valor validados (seção 5) |
| `registry/StudentData` | entrada validada de aluno (todos os campos + `Guardian`), acumula violações em `VALIDATION_FAILED` |
| `registry/Guardian` | `(PersonName name, BrazilianPhone phone, EmailAddress email)`; nunca vai a IA nem a relatório |
| `registry/StudentScope` | `all()` (administrador, sistema) ou `ofTeacher(UUID)`; passado ao repositório |
| `registry/StudentStatusFilter` | `ACTIVE`, `INACTIVE`, `ALL` |
| `registry/Student` (alterado) | `guardianPhone`, `guardianEmail`; `inactivate(now)`, `reactivate(now)`, `applyEdit(StudentData, cpfProtection?, now)`; mantém `toString` sem dado pessoal |
| `registry/Teacher` (alterado) | `inactivate(now)` (invalida o código), `reactivate(now)`, `rename/changeEmail`; `normalizeEmail` mantido |
| `telegram/StudentRegistrationDraft` | cadastro em andamento (seção 3.5) |
| `common/ErrorCode` (alterado) | acréscimos e remoção de `EXPIRED_CODE` (seção 9) |

Serviços do núcleo (`service/…`): `ActorAuthorizationService` (consulta a matriz, nega com `RESOURCE_NOT_FOUND` quando o
papel não tem a operação e `PASSWORD_CHANGE_REQUIRED` para ator restrito), `StudentAccessService` (monta `StudentScope`,
`findVisible`), `AuthorshipService` (monta o registro e escolhe o modo `record`/`recordIndependent`),
`TelegramLinkTerminationService` (vínculo + rascunho + ações pendentes), `SessionRevocationService` (professor e
administrador), `ActivationCodeIssuanceService` (espelha o de acesso), e a extensão de `AccessAttemptsControlService`
(`verifyLock(scope, …)`, `registerFailure(scope, …)`, `confirmSuccess(scope, …)`). Reaproveitados sem mudança de regra:
`AccessCodeIssuanceService`, `CodeAuthenticationService` (só deixa de distinguir vencido de inválido), `AccessRenewalService`,
`AuthorizationService`, `ReportAccessService`.

## 3. Tabelas

### 3.1 `administrator` (migração `V9__administrator.sql`)

| Coluna | Tipo | Regra |
|---|---|---|
| `id` | `uuid` PK | gerado pela aplicação |
| `name` | `varchar(120) NOT NULL` | 2–120 |
| `email` | `varchar(254) NOT NULL` | **único**, minúsculo (`CHECK email = lower(email)`), sem espaços nas pontas; unicidade é **por papel** (pode coincidir com e-mail de professor) |
| `active` | `boolean NOT NULL` | |
| `password_hash` | `varchar(255)` | Argon2id codificado (parâmetros dentro); nulo até a ativação e após inativar |
| `password_provisional` | `boolean NOT NULL` | verdadeiro só para o primeiro administrador até a troca |
| `activation_code_hash` | `varchar(64)` | HMAC; no máximo **um** pendente por administrador (é uma coluna); índice único parcial onde não nulo |
| `activation_code_expires_at` | `timestamptz` | criação + 7 dias |
| `activated_at` | `timestamptz` | preenchido quando a senha é definida pelo código |
| `created_at`, `updated_at` | `timestamptz NOT NULL` | |

Estados derivados (lista): `PENDING` (ativo, sem `password_hash` e com código pendente), `ACTIVATED`
(`password_hash` presente), inativo (`active = false`).

### 3.2 `administrator_session` (mesma migração)

| Coluna | Tipo | Regra |
|---|---|---|
| `id` | `uuid` PK | |
| `administrator_id` | `uuid NOT NULL` FK → `administrator(id) ON DELETE CASCADE` | a remoção dos administradores (FR-034b) leva as sessões |
| `token_hash` | `varchar(64) NOT NULL` **único** | SHA-256 do token de 256 bits |
| `created_at` | `timestamptz NOT NULL` | |
| `expires_at` | `timestamptz NOT NULL` | `created_at` + `reports.administrator.session-hours` (2 h); **nunca atualizado** |
| `revoked_at`, `revocation_reason` | `timestamptz`, `varchar(24)` | `ACCOUNT_INACTIVATED`; "Sair" apaga a linha |

Índices: `(administrator_id)`, `(expires_at)` (varredura diária de vencidas, a `AccessSweepTask` existente passa a cobrir
as duas tabelas). **Validação por requisição**: `sessão válida ⇔ now < expires_at ∧ revoked_at IS NULL ∧ administrator.active`.

### 3.3 `web_session` do professor (migração `V14__web_session_revocation.sql`)

Acrescenta `revoked_at timestamptz` e `revocation_reason varchar(24)` (`ACCOUNT_INACTIVATED`, `CODE_REISSUED`).
`ValidateSessionWebUseCase`: revogada por inativação → `ACCESS_ENDED`; revogada por reemissão ou vencida → `SESSION_EXPIRED`.
A expiração deslizante de 12 h do professor **não muda**.

### 3.4 `authorship_record` (migração `V10__authorship_record.sql`)

| Coluna | Tipo | Regra |
|---|---|---|
| `id` | `uuid` PK | |
| `occurred_at` | `timestamptz NOT NULL` | |
| `author_id` | `uuid` | id do administrador ou do professor; nulo para "sistema" e para e-mail desconhecido; **sem FK** |
| `author_role` | `varchar(16)` | `ADMINISTRATOR` \| `TEACHER` \| nulo (sistema ou desconhecido) — o papel do autor |
| `origin` | `varchar(24) NOT NULL` | `WEB`, `TELEGRAM`, `SYSTEM_STARTUP`, `INITIAL_LOAD` |
| `operation` | `varchar(48) NOT NULL` | nome de `Operation` ou de evento de autenticação (seção 8) |
| `target_type`, `target_id` | `varchar(24)`, `uuid` | `TEACHER`, `ADMINISTRATOR`, `STUDENT`, `ASSIGNMENT`; sem FK |
| `result` | `varchar(10) NOT NULL` | `SUCCESS`, `FAILURE`, `DENIED`, `BLOCKED` |
| `error_code` | `varchar(40)` | ex.: `CPF_UNAVAILABLE`, `UNKNOWN_EMAIL` |
| `origin_hash` | `varchar(64)` | HMAC do endereço de rede; **só** em eventos de autenticação/bloqueio |
| `correlation_id` | `varchar(64) NOT NULL` | id da requisição (Web) ou `update_id` (Telegram) |

`CHECK`s: valores das enumerações; `author_role IS NOT NULL OR author_id IS NULL`; `origin_hash` nulo fora de
autenticação. Índices: `(occurred_at)`, `(author_id, occurred_at)`, `(target_type, target_id)`. **Somente acréscimo**:
gatilho `BEFORE UPDATE OR DELETE` e `BEFORE TRUNCATE` levantam exceção. **Proibido gravar**: CPF, nome/data/contato do
aluno ou do responsável, conteúdo de relatório, e-mail digitado de conta inexistente, senha, códigos.

### 3.5 `student_registration_draft` (migração `V12__student_contact_and_registration_draft.sql`)

Cadastro em andamento no Telegram (FR-036). Uma linha por vínculo.

| Coluna | Tipo | Regra |
|---|---|---|
| `link_id` | `uuid` PK, FK → `telegram_link(id) ON DELETE CASCADE` | some quando o vínculo termina, por qualquer causa |
| `kind` | `varchar(12) NOT NULL` | `CREATE` \| `UPDATE` \| `INACTIVATE` \| `REACTIVATE` (os dois últimos só guardam o token de confirmação, sem campos) |
| `student_id` | `uuid` FK → `student(id)` | obrigatório em `UPDATE`, `INACTIVATE` e `REACTIVATE`; conferido pelo escopo do professor ao iniciar e de novo ao confirmar |
| `provided_fields` | `varchar(120) NOT NULL` | conjunto dos campos já informados (`NAME,BIRTH_DATE,CPF,GUARDIAN,INSTRUMENT`); vazio em `INACTIVATE` e `REACTIVATE` |
| `name`, `instrument` | `varchar(120)`, `varchar(60)` | |
| `birth_date` | `date` | |
| `cpf_encrypted`, `cpf_hash` | `bytea`, `varchar(64)` | **só cifrado** e resumo com chave; nunca em claro |
| `guardian_name`, `guardian_phone`, `guardian_email` | `varchar(120)`, `varchar(11)`, `varchar(254)` | |
| `confirmation_token` | `varchar(16)` | gerado a cada resumo mostrado; uso único |
| `created_at`, `updated_at`, `expires_at` | `timestamptz NOT NULL` | `expires_at` = última atividade + 30 min |

Varredura: `StudentDraftSweepTask` apaga linhas vencidas (e a próxima atualização do vínculo também descarta, com aviso).

### 3.6 `student` (alterada em `V12`)

Acrescenta `guardian_phone varchar(11)` (`CHECK guardian_phone ~ '^[0-9]{10,11}$'`) e `guardian_email varchar(254)`.
`guardian_name` já existe. **Sem `NOT NULL` no banco**: a obrigatoriedade para menor é regra do núcleo na data do
cadastro/edição; alunos já existentes sem contato continuam válidos até serem editados (então, se ainda menores, os três
dados passam a ser exigidos). `UNIQUE(cpf_hash)` já existe e passa a ser a garantia sob concorrência.

### 3.7 `access_attempt` (alterada em `V11__access_attempt_scope.sql`)

Acrescenta `scope varchar(24) NOT NULL` (preenchida com `TEACHER_CODE` nas linhas existentes) e `window_started_at timestamptz`;
troca a unicidade `(channel, source_hash)` por `(scope, channel, source_hash)`. Para `ADMIN_*` o `channel` é `WEB`; para
`TEACHER_CPF_PROBE` a chave é o hash do id do professor (canal do ator).

### 3.8 `channel_action` (alterada em `V13__channel_action_delete_message.sql`)

Estende a `CHECK` do tipo de ação com `DELETE_MESSAGE` (o nome real da constraint é descoberto nas migrações `V6`/`V7`/`V8`
na primeira tarefa do fluxo). Nenhuma coluna nova.

### 3.9 `teacher`, `teacher_student_assignment`

Sem coluna nova. `teacher.access_code_hash/expires_at/renewed_at` passam a ser **nulos** ao inativar. A associação
ganha apenas operações de repositório (`delete`, `listTeachersOfStudent`, `countActiveTeachers`).

## 4. Estados e transições

**Administrador**: `criado → PENDENTE_DE_ATIVAÇÃO` (ativo, sem senha, com código de ativação 7 dias) `→ ATIVADO` (definiu a
senha) `→ INATIVO` (inativar: sessões revogadas, senha e código apagados) `→ PENDENTE_DE_ATIVAÇÃO` (reativar: código novo).
Primeiro administrador: `ATIVADO` com `password_provisional = true` `→` troca obrigatória `→ ATIVADO` normal. Invariante:
**nunca zero administradores ativos** (R-15).

**Sessão do administrador**: `criada (expires_at = +2 h) → expirada | revogada | encerrada ("Sair" apaga)`; sem renovação.

**Professor**: `ativo → inativo` (sessões revogadas, vínculo/rascunho/ações pendentes encerrados, código invalidado) `→ ativo`
(reativar: código novo; sessões e vínculo **não** voltam).

**Aluno**: `ativo ⇄ inativo`; nada é apagado. Inativo: fora das listas padrão e de novas aulas; relatório em aberto continua
com o professor associado. Associação: `inexistente ⇄ existente` (retirar corta o acesso na hora; reassociar devolve).

**Cadastro em andamento**: `iniciado → (campos validados) → resumo mostrado (token novo) → confirmado (consumido, grava) | cancelado |
vencido | descartado por fim do vínculo`.

**Tentativa de acesso**: `0 falhas → n falhas → bloqueada até T` (5 em 15 min por origem) `→` ao fim do bloqueio zera;
sucesso zera apenas sem bloqueio ativo; falha durante bloqueio é ignorada.

## 5. Validações e constantes (FR-018) — núcleo, classe `RegistryLimits`

| Campo | Regra |
|---|---|
| Texto em geral | trim; NFC; caracteres de controle (`Cc`) recusados; vazio após trim = ausente |
| Nome (aluno, professor, administrador, responsável) | 2 a 120 caracteres |
| Instrumento | opcional; até 60 |
| E-mail | até 254; `^[^@\s]+@[^@\s]+\.[^@\s]+$`; guardado em minúsculas |
| Telefone do responsável | máscara aceita; guarda só dígitos; DDD `11–99`; 10 (fixo) ou 11 (celular, começa com 9) dígitos |
| Data de nascimento | existe no calendário; `≤ hoje` (fuso da escola, `America/Sao_Paulo` por padrão); `≥ hoje − 120 anos` |
| Idade | em anos completos na data de referência no fuso da escola; **18 anos completos = adulto** |
| CPF | aceita `.`, `-` e espaço na digitação; 11 dígitos; dígitos verificadores; sequências repetidas recusadas |
| Senha | 12 a 128 pontos de código; sem composição; ≠ e-mail e ≠ nome (sem diferenciar caixa); ≠ provisória na troca |
| Código de acesso | 8 caracteres de `ABCDEFGHJKLMNPQRSTUVWXYZ23456789`, 7 dias, renova com o uso (001) |
| Código de ativação | 12 caracteres do mesmo alfabeto, 7 dias, uso único |
| Responsável | nome, telefone e e-mail **todos** obrigatórios se menor de 18 na data do cadastro/edição; senão opcionais e mantidos |
| Página | tamanho 1–100 (padrão 20); Telegram 6 por página |

## 6. Registro de autoria — operações que geram registro (base do SC-014)

Gravações (uma por linha da matriz que grava): `TEACHER_REGISTER`, `TEACHER_UPDATE`, `TEACHER_INACTIVATE`, `TEACHER_REACTIVATE`,
`TEACHER_REISSUE_ACCESS_CODE`, `TEACHER_UNLINK_TELEGRAM`, `ADMINISTRATOR_CREATE`, `ADMINISTRATOR_INACTIVATE`,
`ADMINISTRATOR_REACTIVATE`, `ADMIN_ACTIVATE` (definição de senha e ativação), `ADMIN_CHANGE_PASSWORD`, `ASSIGNMENT_ASSIGN`,
`ASSIGNMENT_REMOVE`, `STUDENT_REGISTER`, `STUDENT_UPDATE`, `STUDENT_INACTIVATE`, `STUDENT_REACTIVATE`,
`FIRST_ADMINISTRATOR_BOOTSTRAP`. Eventos: `ADMIN_SIGN_IN`, `ADMIN_SIGN_OUT`, `TEACHER_SIGN_IN` (código na Web),
`TEACHER_LINK_TELEGRAM`, `ATTEMPT_BLOCKED`, `AUTHORIZATION_DENIED` (credencial de um papel em rota do outro; aluno alheio por
id manipulado), `CPF_REFUSED_OTHER_STUDENT` (cadastro e edição). **Não geram**: expiração de sessão, leituras autorizadas
(`*_LIST`, `*_GET`, `*_PREVIEW_*`).

## 7. Portas do núcleo (assinaturas congeladas na fundação)

Novas: `AdministratorRepository` (`searchById`, `searchByEmail` — a ativação é por e-mail, não há busca por código —,
`count`, `insertIfNoneExists`, `insertIfAbsent`, `save`, `list(filter, page)`, `lockAllActiveForUpdate`),
`AdministratorSessionRepository` (`searchByTokenHash` já com a conta, `save`, `remove`, `revokeAllOf`, `removeExpired`),
`PasswordHashPort`, `AuthorshipRecorderPort` (`record`, `recordIndependent`), `StudentRegistrationDraftRepository`,
`ActivationCodeGeneratorPort`. Alteradas: `AccessAttemptRepository` (`registerFailure(scope, …, policy)`, `confirmSuccess`, `search`),
`DataProtectionPort` (`hashActivationCode`, `originHash(scope, key)`), `StudentRepository` (`findVisible(scope, id)`,
`list(scope, status)`, `insertIfAbsent`, `searchByCpfHash`), `TeacherRepository` (`list`, `insertIfAbsent`), `AssignmentRepository`
(`delete`, `listTeachersOfStudent`, `countActiveTeachers`, `countOpenReports…` via `OpenReportsQueryPort`), `WebSessionRepository`
(`revokeAllOf(teacherId, reason, now)`), `TelegramLinkRepository` (cascata do rascunho), `ChannelActionRepository`
(`discardPendingByChat`), `TransactionPort` (`runAndRollBack`), `OpenReportsQueryPort` (filtro por associação/professor ativo).

## 8. `Operation` (matriz)

`ADMIN_SIGN_IN`, `ADMIN_ACTIVATE`, `ADMIN_GET_SESSION`, `ADMIN_SIGN_OUT`, `ADMIN_CHANGE_PASSWORD`, `TEACHER_SIGN_IN`,
`TEACHER_LIST`, `TEACHER_GET`, `TEACHER_REGISTER`, `TEACHER_UPDATE`, `TEACHER_INACTIVATE`, `TEACHER_REACTIVATE`,
`TEACHER_REISSUE_ACCESS_CODE`, `TEACHER_UNLINK_TELEGRAM`, `ADMINISTRATOR_LIST`, `ADMINISTRATOR_CREATE`,
`ADMINISTRATOR_INACTIVATE`, `ADMINISTRATOR_REACTIVATE`, `FIRST_ADMINISTRATOR_BOOTSTRAP`, `ASSIGNMENT_ASSIGN`,
`ASSIGNMENT_REMOVE`, `ASSIGNMENT_PREVIEW_REMOVAL`, `STUDENT_LIST`, `STUDENT_GET`, `STUDENT_REGISTER`, `STUDENT_UPDATE`,
`STUDENT_INACTIVATE`, `STUDENT_REACTIVATE`, `LESSON_AND_SEMESTER_REPORTS`. Detalhes por papel/canal: `contracts/permission-matrix.json`.

Casos de uso (um por operação; pacotes `usecase/administrator`, `usecase/teacher`, `usecase/registry`, `usecase/seed`):
`SignInAdministratorUseCase`, `ValidateAdministratorSessionUseCase`, `GetAdministratorSessionUseCase`,
`SignOutAdministratorUseCase`, `ActivateAdministratorUseCase`, `ChangeProvisionalPasswordUseCase`,
`BootstrapFirstAdministratorUseCase`, `ListAdministratorsUseCase`, `CreateAdministratorUseCase`,
`InactivateAdministratorUseCase`, `ReactivateAdministratorUseCase`; `ListTeachersUseCase`, `GetTeacherUseCase`,
`RegisterTeacherUseCase`, `UpdateTeacherUseCase`, `InactivateTeacherUseCase`, `ReactivateTeacherUseCase`,
`ReissueAccessCodeUseCase`, `UnlinkTeacherTelegramUseCase`; `ListStudentsUseCase`, `GetStudentUseCase`,
`RegisterStudentUseCase`, `UpdateStudentUseCase`, `InactivateStudentUseCase`, `ReactivateStudentUseCase`,
`AssignTeacherToStudentUseCase`, `RemoveTeacherFromStudentUseCase`, `PreviewAssignmentRemovalUseCase`;
Telegram (rascunho): `StartStudentRegistrationUseCase`, `CollectStudentRegistrationFieldUseCase`, `ConfirmStudentRegistrationUseCase`,
`CancelStudentRegistrationUseCase`; carga: `LoadInitialRegistryUseCase` (orquestra os acima), `RotateCpfKeysUseCase`.

## 9. `ErrorCode`

Acrescentar: `INVALID_CREDENTIALS`, `INVALID_ACTIVATION`, `ACCESS_ENDED`, `PASSWORD_CHANGE_REQUIRED`,
`PASSWORD_CHANGE_NOT_REQUIRED`, `PASSWORD_LENGTH`, `PASSWORD_EQUALS_IDENTITY`, `PASSWORD_SAME_AS_PROVISIONAL`,
`LAST_ADMINISTRATOR`, `VALIDATION_FAILED`, `INVALID_NAME`, `INVALID_PHONE`, `INVALID_INSTRUMENT`, `INVALID_TEXT`,
`DUPLICATE_EMAIL`, `DUPLICATE_CPF`, `CPF_UNAVAILABLE`, `STUDENT_ALREADY_YOURS`, `SHARED_STUDENT_ACTION_DENIED`,
`OPERATION_TEMPORARILY_BLOCKED`, `TEACHER_INACTIVE`, `STUDENT_INACTIVE`, `CONFIRMATION_EXPIRED`,
`CONFIRMATION_ALREADY_DONE`, `CALLBACK_EXPIRED`, `OPERATION_NOT_SAVED`. **Remover** `EXPIRED_CODE`. Mantêm-se os códigos de campo
da carga (`INVALID_EMAIL`, `NAME_REQUIRED`, `INVALID_CPF`, `INVALID_BIRTH_DATE`, `GUARDIAN_REQUIRED`, `TEACHER_NOT_FOUND`,
`DUPLICATE_*_IN_FILE`). Textos e HTTP: `contracts/messages.md`.

## 10. Glossário — termos a registrar em `.specify/memory/glossary.json` **antes** do uso

| Português (palavra proibida em identificador) | Inglês canônico |
|---|---|
| ativacao | activation |
| autoria | authorship |
| autor | author |
| papel | role |
| telefone | phone |
| revogacao | revocation |
| sondagem | probing |
| correlacao | correlation |
| desvincular / desvinculo | unlink |
| congelado | frozen |
| rascunho | draft (já existe) |
| responsavel | guardian (já existe) |

Cada termo entra com um único equivalente; o que já existe não é duplicado. Antes de usar qualquer outra palavra
portuguesa nova em identificador, o fluxo roda `GUARD_REPORT=1` (ver `.specify/CLAUDE.md`).

## 11. Invariantes e concorrência (testes obrigatórios)

1. Dois cadastros simultâneos com o mesmo CPF/e-mail → um insere, o outro recebe duplicidade (sem aborto de transação).
2. Duas inativações simultâneas de administradores → sempre sobra ≥ 1 ativo (SC-009).
3. Seis tentativas simultâneas erradas → bloqueio exato em 5; a sexta não conta nem prorroga; a senha correta chegando
   depois da quinta é recusada.
4. Revogação: nenhuma requisição iniciada após o commit é aceita (SC-010); requisição em curso pode terminar.
5. Confirmação duplicada do bot: uma gravação, a segunda responde `CONFIRMATION_ALREADY_DONE`.
6. `authorship_record` não admite `UPDATE`/`DELETE`/`TRUNCATE` (teste de integração com Postgres real).
7. Nenhuma coluna, log, resposta ou ação de saída contém o CPF canário (R-25).
