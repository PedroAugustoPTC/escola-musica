# Contrato: "Meus alunos" no Telegram (professor)

**Feature**: `005-admin-registrations-access` | Referências: FR-008, FR-009, FR-024 a FR-027, FR-035 a FR-037, FR-042;
`specs/001-fluxo-relatorios/contracts/telegram-conversa.md` (fluxos, callback `v1:<ação>:<argumento>`, ordem de decisão);
`specs/002-api-interna-canal-telegram/contracts/actions-v1.schema.json` (ações v1).

O Telegram é canal **só do Professor**. Este contrato define o fluxo que o bot oferece ao professor vinculado; não
existe contraparte para o administrador (a única "contraparte" é a negativa neutra, seção 6). O fluxo é um
adaptador: todo texto vem do catálogo (`contracts/messages.md`) e toda validação, autorização e gravação é dos casos
de uso do núcleo (Princípios I e II). O n8n continua só transportando.

## 1. Entradas do menu e palavras-chave

| Palavra-chave (normalizada: sem acento, maiúscula, barra) | Comando interno | Observação |
|---|---|---|
| `meus alunos`, `alunos` | `MY_STUDENTS` | abre a lista paginada |
| `cadastrar aluno`, `novo aluno` | `REGISTER_STUDENT` | inicia o cadastro |
| `pular` | `SKIP` | só nas etapas opcionais (instrumento) |
| `voltar` | `BACK` | só na lista e no cartão; no cadastro vale `cancelar` |

`cancelar`, `sair`, `ajuda`, `início` e `sim` mantêm o significado da 001. O menu do professor ganha os botões
"Meus alunos" e "Cadastrar aluno". Nenhuma palavra-chave, botão ou comando administrativo existe.

## 2. Estado da conversa (persistido no backend, Princípio II)

Novo fluxo `STUDENT_REGISTRY` (cabe nos 20 caracteres da coluna `flow`). Etapas novas de `ConversationFlowStep`
(≤ 40 caracteres):

| Etapa | O bot espera |
|---|---|
| `REGISTRY_LIST` | toque em aluno, página, filtro ou texto de busca (nome ou instrumento) |
| `REGISTRY_CARD` | toque em Editar, Inativar/Reativar ou Voltar |
| `REGISTRY_ASK_NAME` | nome do aluno |
| `REGISTRY_ASK_BIRTH_DATE` | data de nascimento (`dd/mm/aaaa`) |
| `REGISTRY_ASK_CPF` | CPF (11 dígitos; aceita pontos e traço) |
| `REGISTRY_ASK_GUARDIAN_NAME` | nome do responsável |
| `REGISTRY_ASK_GUARDIAN_PHONE` | telefone do responsável (DDD + número) |
| `REGISTRY_ASK_GUARDIAN_EMAIL` | e-mail do responsável |
| `REGISTRY_ASK_INSTRUMENT` | instrumento ou "pular" |
| `REGISTRY_EDIT_FIELD` | escolha do campo a editar (botões) |
| `REGISTRY_CONFIRM` | "sim"/botão Confirmar, ou Cancelar |

Os valores já aceitos ficam no **cadastro em andamento** (`student_registration_draft`, `data-model.md` §3.5), preso ao
vínculo por `ON DELETE CASCADE`: **desaparece na hora em que o vínculo termina, por qualquer causa** (FR-036). O CPF
fica só cifrado. Prazo de inatividade: 30 minutos (`reports.telegram.student-draft-minutes`); vencido, o
rascunho é descartado no próximo update e por uma varredura, e o bot avisa.

## 3. Roteiro

### 3.1 Cadastrar

1. `REGISTER_STUDENT` (ou botão) → o adaptador chama `StartStudentRegistrationUseCase` (kind `CREATE`) → mensagem `REGISTRY_ASK_NAME`. Fora de conversa privada: negativa neutra (já feita
   para todo o bot, `PRIVATE_CONVERSATION_ONLY`; os callbacks também são checados).
2. A cada resposta o adaptador chama `CollectStudentRegistrationFieldUseCase` (campo + texto bruto). O núcleo valida
   (nome 2–120, data real e não futura, CPF com dígitos verificadores, telefone, e-mail) e guarda o valor
   normalizado; erro de validação devolve a mesma pergunta com o motivo (`VALIDATION_FAILED` do campo), sem
   repetir o dado.
3. Depois do CPF, o núcleo informa se o aluno é menor de 18 anos **na data de hoje do fuso da escola**
   (`guardianRequired`) e o bot pergunta nome, telefone e e-mail do responsável; senão pula para o instrumento.
4. **Duplicidade de CPF** é decidida no momento do CPF (e de novo na confirmação, sob a mesma transação): CPF de
   aluno que não é do professor → `CPF_UNAVAILABLE` (neutra, sem nome, sem professor), registro de autoria e conta
   do limite de sondagem (FR-024); cada CPF digitado conta ainda uma consulta no teto `TEACHER_CPF_LOOKUP` (FR-024); CPF de aluno do próprio professor → `STUDENT_ALREADY_YOURS`. O rascunho é mantido
   para o professor corrigir o CPF ou cancelar.
5. `REGISTRY_CONFIRM`: resumo **sem CPF** (nome, data, instrumento, nome do responsável, sem telefone nem e-mail) + botões **Confirmar** / **Cancelar**
   (`v1:reg-confirm:<token>` e `v1:reg-cancel:`). O `<token>` é um valor aleatório de 8 caracteres gerado a cada vez
   que o resumo é mostrado e guardado no rascunho.
6. Confirmar → `ConfirmStudentRegistrationUseCase(actor, linkId, token)`: numa única transação, confere o dono (o
   vínculo do chamador), o token e o prazo, executa o `RegisterStudentUseCase` e **consome** o rascunho. Segundo
   toque, ou token de resumo anterior → `CONFIRMATION_ALREADY_DONE` ("Essa operação já foi concluída"), sem gravar
   de novo. Atualização duplicada do Telegram é ignorada pela idempotência da 002 (`update_id`).
7. Cancelar, "cancelar" por texto, "sair", ou prazo vencido: rascunho apagado, nada gravado.

### 3.2 Listar, buscar, abrir

- `MY_STUDENTS` → `ListStudentsUseCase` (o mesmo caso de uso da Web, com o ator professor), página de 6 itens, ordem alfabética, botões
  `‹ Anterior` / `Próxima ›` (`v1:reg-page:<n>`), filtro `Ativos` / `Inativos` (`v1:reg-filter:<ACTIVE|INACTIVE>`).
  Texto digitado em `REGISTRY_LIST` é busca por nome ou instrumento (mesma normalização da Web). Texto com forma de CPF (11 dígitos, com ou sem pontos e traço) nunca vira busca: é recusado com `VALIDATION_FAILED` sem repeti-lo, não é guardado em `data["options"]` e a mensagem é apagada (§4).
- Toque num aluno → `GetStudentUseCase`; o cartão mostra nome, idade, instrumento, situação e nome do responsável (sem telefone nem e-mail)
  (nunca o CPF). Aluno de outro professor, ou identificador adulterado no `callback_data`, é tratado como inexistente:
  mesma resposta de "Não encontramos o que você procurou" (FR-025).
- Lista vazia e sem alunos: "Você ainda não tem alunos. Que tal cadastrar o primeiro?" com o botão
  "Cadastrar aluno" (FR-042).

### 3.3 Editar

`Editar` → o adaptador chama `StartStudentRegistrationUseCase` (kind `UPDATE`), que confere o escopo do professor com
`findVisible` (aluno alheio ≡ inexistente, nada é criado) → `REGISTRY_EDIT_FIELD` (botões: Nome, Nascimento, CPF, Responsável, Instrumento). O professor informa o novo
valor pelo mesmo caminho do cadastro; ao terminar, resumo **só com o que muda** (CPF aparece como "CPF será
substituído", nunca o valor) + Confirmar/Cancelar. A confirmação executa `UpdateStudentUseCase`. Editar o CPF para o de
outro aluno segue o item 4 do cadastro. Mudar a data para menor de 18 anos sem responsável cadastrado exige os três
dados do responsável antes de confirmar.

### 3.4 Inativar e reativar

`Inativar`/`Reativar` seguem o mesmo mecanismo do cadastro: o adaptador chama `StartStudentRegistrationUseCase` (kind
`INACTIVATE` ou `REACTIVATE`, que confere o escopo do professor) e o bot mostra um resumo que nomeia a
ação e o aluno, com **Confirmar** / **Cancelar** (`v1:reg-confirm:<token>`, token de uso único, preso ao vínculo e ao
prazo de 30 minutos). A confirmação passa por `ConfirmStudentRegistrationUseCase`, que executa
`InactivateStudentUseCase`/`ReactivateStudentUseCase` (dono, token e prazo conferidos; segundo toque, token anterior ou
botão de outro vínculo → `CONFIRMATION_ALREADY_DONE` ou `CONFIRMATION_EXPIRED`, sem gravar de novo, FR-009). Aluno com mais de um professor
ativo: `SHARED_STUDENT_ACTION_DENIED` com a orientação de procurar o administrador (FR-026). Nenhuma inativação é
bloqueada por relatório em aberto (FR-022).

## 4. Apagar a mensagem com o CPF (FR-036)

Ao receber o texto da etapa `REGISTRY_ASK_CPF` (e do novo CPF, na edição) ou de qualquer texto com forma de CPF em outra etapa, como a busca de `REGISTRY_LIST`, o backend devolve, **antes** da próxima
pergunta, uma ação `DELETE_MESSAGE` para a mensagem do professor. É uma ação nova v1, só de transporte:

```json
{ "actionId": "u123-0", "type": "DELETE_MESSAGE", "chatId": 4567, "messageId": 89 }
```

- Schema: `DeleteMessageAction` em `actions-v1.schema.json` (acréscimo compatível: o receptor ignora campo
  desconhecido e responde `ACTION_NOT_SUPPORTED` a tipo desconhecido, FR-003a da 002).
- O n8n executa `deleteMessage` e confirma como as demais. Falha → código `MESSAGE_NOT_DELETABLE`; o backend
  gera a **compensação** (mensagem `CPF_MESSAGE_NOT_DELETED`: "Não consegui apagar a mensagem com o CPF. Apague-a
  você mesmo(a) para não deixá-lo no chat."). Tipo e código entram em `ChannelActionType` / `ChannelAction` e na
  constraint do banco (migração própria).
- O CPF nunca aparece em mensagem do bot, no resumo, em ação de saída, em log, no registro de autoria nem no
  n8n (histórico de execuções desligado, feature 004). Risco aceito: o Telegram é serviço externo.

## 5. Callback data

`v1:<ação>:<argumento>` (≤ 64 bytes). Ações novas: `reg-page`, `reg-filter`, `reg-open` (argumento: posição na lista
persistida em `data["options"]`, nunca um id), `reg-new`, `reg-edit` (campo), `reg-confirm` (token), `reg-cancel`,
`reg-inactivate` e `reg-reactivate` (só iniciam o rascunho de confirmação; a gravação sai por `reg-confirm`), `reg-skip`, `reg-back`. O argumento nunca identifica o professor e é revalidado
contra o estado persistido; botão de mensagem antiga ou de outro estado responde `CALLBACK_EXPIRED`.

## 6. Negativa neutra e conta sem vínculo

- Qualquer texto que o bot não reconhece (inclusive tentativa de operação administrativa: "criar professor",
  "reemitir código", e-mail e senha digitados) recebe a mesma resposta de texto não reconhecido, **seguida do menu do
  professor**; nunca cita Web, área administrativa ou administrador.
- Conta sem vínculo: apenas `CODE_REQUIRED` (orientação de entrada). Um código de ativação de administrador ou uma
  senha digitada não vinculam nada: o código de acesso do professor é o único aceito (`INVALID_CODE`).
- Não existe tela, botão, comando nem estado para administrador (SC-003, SC-007); uma regra ArchUnit reprova qualquer
  referência de `inbound.telegram` a casos de uso ou repositórios do administrador.

## 7. Paridade Web ≡ Telegram

| Operação do professor | Web (`/my-students`) | Telegram | Mesmo caso de uso |
|---|---|---|---|
| Listar / buscar (nome, instrumento, situação) | tabela com filtros e paginação | lista por botões, 6 por página | `ListStudentsUseCase` |
| Abrir | tela do aluno | cartão | `GetStudentUseCase` |
| Cadastrar | formulário único | conversa guiada + resumo + confirmar | `RegisterStudentUseCase` |
| Editar | formulário | escolha do campo + resumo + confirmar | `UpdateStudentUseCase` |
| Inativar / reativar | diálogo de confirmação (inativar) | resumo + Confirmar (token de uso único) | `InactivateStudentUseCase` / `ReactivateStudentUseCase` |

Diferenças **intencionais** (declaradas na spec, Assumptions): confirmação em toda gravação do Telegram (sem
formulário de revisão); vínculo sem expiração por inatividade; bloqueio por conta (Telegram) × por origem (Web).
O resultado de negócio é o mesmo. Nenhum desvio de paridade do Professor foi encontrado (ver `plan.md`, Constitution Check I).
