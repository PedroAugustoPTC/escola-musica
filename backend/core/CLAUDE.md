# backend/core

Núcleo da aplicação. Aqui mora **toda** a regra de negócio. Não declara dependência de Spring,
JPA, HTTP nem SDK de fornecedor; nenhum artefato cita fornecedor ou modelo de IA específico.

Pacotes em `src/main/java/br/com/felipeblaft/reports/`:

| Pacote | Conteúdo |
|---|---|
| `domain/` | Entidades e regras: `access`, `channel`, `common`, `job`, `registry`, `report`, `semester`, `telegram` |
| `usecase/` | Um caso de uso por ação de negócio (`*UseCase`), agrupados por contexto (`lesson`, `semester`, `history`, `channel`, `access`, `administrator`, `teacher`, `registry`, `seed`, `document`, `home`, `job`) |
| `port/` | Portas de saída: `*Port` (provedores externos) e `*Repository` (persistência) |
| `service/` | `*Service`: apoiam casos de uso sem substituí-los |

## Acesso e cadastros (feature 005)

Administrador (somente Web), professor (Web e Telegram) e o ator de sistema usam os **mesmos** casos de uso; o que muda é o
`Actor` e a permissão que a matriz lhe dá. Fonte das regras: `specs/005-admin-registrations-access/`.

| Peça | O que é |
|---|---|
| `domain/access/Actor` | Quem executa: papel (`Role`), id, origem (`ActionOrigin`: `WEB`, `TELEGRAM`, `SYSTEM_STARTUP`, `INITIAL_LOAD`) e `passwordChangeRequired`. Os adaptadores montam o ator da sessão ou do vínculo, **nunca** do corpo da requisição. O ator de sistema (`Actor.system`) não tem papel nem id e só nasce na inicialização e na carga inicial |
| `domain/access/Operation`, `Permission`, `PermissionMatrix` | Uma `Operation` por ação; `PermissionMatrix.standard()` decide `ALLOW`, `ALLOW_OWN_STUDENTS`, `ALLOW_SOLE_ACTIVE_TEACHER`, `PUBLIC` ou `DENY`, negando por padrão. É o espelho de `specs/005-admin-registrations-access/contracts/permission-matrix.json` (`PermissionMatrixDriftTest` reprova divergência) |
| `domain/access` (demais) | `Administrator`, `AdministratorSession` (validade fixa; `expires_at` nunca é renovado), `PasswordPolicy`, `ActivationCode`, `AttemptScope`/`AttemptPolicy`, `AuthorshipRecord`, `SessionRevocationReason` |
| `domain/registry` | `Student`, `Teacher`, `TeacherStudent` (associação), `StudentData`, `Guardian`, `Cpf` e `ProtectedCpf`, `StudentScope`, `StudentSearch`; os objetos de valor (`PersonName`, `EmailAddress`, `BrazilianPhone`, `BirthDate`, `RegistryText`) validam a si mesmos |
| `usecase/administrator` | Entrada, ativação, sessão e troca de senha do administrador; criar, listar, inativar e reativar administradores; `BootstrapFirstAdministratorUseCase` |
| `usecase/teacher` | Cadastrar, editar, listar, inativar e reativar professor; reemitir o código de acesso; desvincular o Telegram |
| `usecase/registry` | Alunos (`RegisterStudent`, `UpdateStudent`, `InactivateStudent`, `ReactivateStudent`, `GetStudent`, `ListStudents`), associações (`AssignTeacherToStudent`, `RemoveTeacherFromStudent`, `PreviewAssignmentRemoval`) e o cadastro guiado do Telegram (`StartStudentRegistration`, `CollectStudentRegistrationField`, `ConfirmStudentRegistration`, `CancelStudentRegistration`) |
| `usecase/seed` | `LoadInitialRegistryUseCase` (a carga **não tem regra própria**: chama os mesmos casos de uso com o ator `INITIAL_LOAD`) e `RotateCpfKeysUseCase` |

Regras de autorização, escopo e autoria (todas no núcleo; nenhuma na Web nem no Telegram):

- **Autorização é a primeira instrução** de todo caso de uso que exige credencial: `ActorAuthorizationService.require(actor, operation)`.
  Papel sem a operação recebe `RESOURCE_NOT_FOUND`, igual ao recurso inexistente. O administrador com senha provisória só troca a
  senha, lê a sessão e sai (`ActorAuthorizationService.ALLOWED_WHILE_RESTRICTED`, fonte única que o filtro da Web também usa).
  Operações `PUBLIC` (entrada) e de sistema ficam fora da regra.
- **Escopo de alunos** (`StudentScope`, `StudentAccessService`): o administrador enxerga a escola inteira; o professor, só os alunos
  associados a ele. O escopo vai **dentro da consulta** (`findVisible`), nunca por filtragem depois. Aluno alheio, sem professor ou
  inexistente dão o mesmo `RESOURCE_NOT_FOUND`, sem distinguir. O ArchUnit reprova leitura de aluno sem escopo nos casos de uso do
  cadastro e leitura com escopo fora do `StudentAccessService`.
- **Autoria** (`AuthorshipService`): toda operação que muda estado grava um `AuthorshipRecord` com papel, canal e resultado
  (sucesso, falha, negado, bloqueado). O sucesso entra na **mesma transação** do caso de uso; falha, negativa e bloqueio, em
  transação independente. O registro aceita só códigos e identificadores (nunca CPF, e-mail digitado, senha, código de acesso nem
  dado do responsável). Leituras e expiração de sessão não geram registro. `AuthorshipRecorderPort` só acrescenta.
- **CPF** (`Cpf`, `ProtectedCpf`): existe em claro só dentro do caso de uso que o recebe; vai ao banco cifrado e como resumo com
  chave. Nenhum `toString` o revela (`PersonalDataToStringTest`) e só cadastro e edição de aluno consultam por resumo
  (`StudentCpfRulesTest`). Ao professor, CPF repetido vira o código neutro `CPF_UNAVAILABLE` (ou `STUDENT_ALREADY_YOURS`), contado
  nos limites contra sondagem (`AccessAttemptsControlService`, um escopo de contagem por finalidade).
- **Encerrar acesso** tem um caminho só: `SessionRevocationService` revoga as sessões na hora e `TelegramLinkTerminationService` é
  quem remove o vínculo do Telegram (junto com o cadastro em andamento e as ações pendentes do chat).
- Inativar nunca apaga; retirar associação nunca bloqueia nem altera relatório (ficam congelados com o professor); a escola nunca
  fica sem administrador ativo (`LAST_ADMINISTRATOR`, com bloqueio em ordem fixa sob concorrência).
- O núcleo devolve **código de erro** e dados estruturados, nunca texto; o texto vem do catálogo da aplicação.

## Custo e alertas de operação (feature 007, US5)

- `domain/privacy` (feature 007): consentimento (`StudentConsent`, versão do termo), retenção (`RetentionPolicy` com prazos padrão e valor inválido que cai no padrão com aviso) e eliminação (`ErasureRequest`, `ErasedStudent`, prévia de alcance e confirmação por `ELIMINAR`). O **guarda de consentimento** impede enviar à IA aula de aluno sem consentimento vigente; a eliminação reaproveita as remoções da retenção.
- `domain/audit` (feature 007): trilha encadeada por hash (`AuditEvent`, âncora). A **porta de guarda da trilha** (`AuditTrailRetentionPort`) é a única que pode apagar eventos vencidos e só com a cadeia íntegra; `VerifyAuditChainUseCase` confere a cadeia e abre `AUDIT_CHAIN_BROKEN`. Todo caso de uso que lê ou altera dado de aluno grava o evento pelo mesmo ator que autorizou.
- `domain/operations`: `OperationalAlert` (só tipo, componente e código do motivo; nunca aluno nem texto livre), `AlertType`, `AlertStatus`, `AlertThresholds`, `PriceTable` (preço com vigência) e `MonthlyCostReport`/`CostAggregate`.
- `OperationalAlertService` abre (`report`) e normaliza (`resolve`) alertas; a implementação `PersistentOperationalAlertService` mantém um ativo por tipo e componente, soma a ocorrência e grava `ALERT_OPENED`/`ALERT_NORMALIZED` com o ator de apuração. Quem reporta: `RunRetentionUseCase`, `ConfirmErasureUseCase`, `DocumentGenerationService` (`STORAGE_FAILED`), `VerifyAuditChainUseCase`.
- `EvaluateOperationalAlertsUseCase` (só a tarefa agendada chama; sem autorização): provedor de IA (falhas seguidas **ou** taxa na janela), fila do canal (item antigo **ou** itens demais) e normalização do armazenamento após o silêncio. `AUDIT_CHAIN_BROKEN` só normaliza por `AcknowledgeOperationalAlertUseCase` (`OPERATIONS_ALERT_ACK`); `RETENTION_FAILED` normaliza na próxima execução boa.
- `GetMonthlyCostReportUseCase` (`OPERATIONS_COST_READ`): mês no fuso da escola; agrega as `CostLine` por áudio, aula, semestral, aluno, canal e provedor. O nome do aluno só vem de `CostReportQueryPort.studentNames`, para a tela do administrador.
- `TranscriptionPort.providerName()` e `LlmPort.providerName()` dão o provedor das chamadas que falham, para o alerta por provedor.

## Regras

- `domain` não depende de `usecase`, `port` nem de nada externo; `usecase` não depende de adaptadores.
- Caso de uso não conhece HTTP, Telegram nem persistência; recebe tudo por portas.
- Autorização por papel e por professor (só os próprios alunos) é decidida aqui, em todo caso de uso que lê ou altera aluno
  (ver "Acesso e cadastros"). Caso de uso novo de administração precisa de uma `Operation` na matriz (e no contrato) ou entrar na
  lista explícita de exceções do ArchUnit.
- Relatório gerado por IA só vira oficial com aprovação explícita da versão vigente; toda alteração cria nova versão.
- Testes unitários cobrem sucesso, erro e borda, com dublês (`src/test/.../doubles`) no lugar de provedores;
  mantenha ≥ 90% de cobertura. As regras de dependência entre camadas são testadas com ArchUnit em
  `src/test/.../architecture` (`ArchitectureRulesTest`: camadas, autorização em todo caso de uso, escopo de alunos, autoria só de
  acréscimo e remoção de vínculo; `PersonalDataToStringTest`; `StudentCpfRulesTest`).
- Nomes seguem os sufixos padronizados da constituição (Princípio VI), em inglês: `*UseCase`, `*Port`,
  `*Repository`, `*Service`. O guardião de idioma reprova nome em português (ver `.specify/CLAUDE.md`).
