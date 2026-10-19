# Contrato: Conversa do bot do Telegram

**Feature**: `001-fluxo-relatorios` | Referências: spec (FR-036 a FR-040, FR-027b, tabela "Paridade
entre canais"), [research.md](../research.md) R-04, R-05, R-18 e [mensagens.md](./mensagens.md).

O adaptador `adaptadores.entrada.telegram` é **só um tradutor**: recebe o update, carrega o estado
persistido (`VinculoTelegram` + `ConversaTelegram`), decide qual caso de uso chamar e apresenta o
resultado. Não há regra de negócio no adaptador (Princípio I) e nenhum estado fica em memória
(FR-036). Todo update recebe resposta (FR-039, SC-007: confirmação em até 5 s).

## 1. Transporte

> **Nota (feature 002, sem reescrever histórico):** o transporte do Telegram agora é feito via n8n
> (constituição v1.4.0); ver `specs/002-api-interna-canal-telegram/research.md` R-01. A rota
> `/interno/v1/canal/telegram/**` (contrato da 002) passa a ser o caminho usado pelo n8n; o webhook
> `/telegram/webhook` descrito abaixo segue disponível.
>
> **Nota (feature 004):** o n8n é o transporte; ele recebe o update em `/webhook/telegram-update`, repassa o corpo e o cabeçalho de segredo ao backend e executa as ações devolvidas. A conversa (estados, mensagens, botões) não muda. Contratos: `specs/002-api-interna-canal-telegram/contracts/` e `specs/004-n8n-telegram-transport/contracts/`.

| Item | Contrato |
|---|---|
| Entrada | `POST /telegram/webhook` (rede interna), chamado pelo servidor local `telegram-bot-api`. Cabeçalho `X-Telegram-Bot-Api-Secret-Token` obrigatório, comparado em tempo constante; se não bater, 401 sem corpo. |
| Updates aceitos | `message` (chat privado) e `callback_query`. Outros tipos: 200 e ignorados. Grupos: o bot responde que só atende em conversa privada. |
| Idempotência | `update_id` gravado em `update_telegram_processado`; um update repetido devolve 200 sem reprocessar. |
| Resposta HTTP | sempre 200 depois de persistir o estado; o trabalho pesado vai para a fila (R-06). |
| Saída | chamadas à API de bots (`sendMessage`, `sendDocument`, `editMessageText`, `answerCallbackQuery`, `getFile`) por `MensageriaTelegramAdaptador`, com `parse_mode=HTML` e texto escapado. |
| Identidade | `message.from.id` / `callback_query.from.id` resolve o `VinculoTelegram`. O conteúdo de `callback_data` nunca identifica o professor. |
| Áudio aceito | `voice`, `audio`, `video`, `video_note` e `document` com MIME `audio/*` ou `video/*`. O tamanho (`file_size`) é conferido na chegada contra o limite (50 MB); acima dele, a mensagem é `AUDIO_TOO_LARGE`, sem baixar. |

## 2. Comandos e palavras-chave (FR-037)

A comparação ignora maiúsculas, minúsculas, acentos e a barra inicial (`/inicio` = `início` = `menu`).

| Entrada | Ação | Caso de uso |
|---|---|---|
| `/start`, `início`, `start`, `menu` | mostra o início (se não vinculado, pede o código) | `ObterInicioProfessorCasoDeUso` |
| `registrar aula` | começa o fluxo de registro; limpa o foco, sem cancelar relatório em aberto (FR-027b) | — (estado da conversa) |
| `relatório semestral`, `semestral` | começa o fluxo do semestral | `ListarSemestresDisponiveisCasoDeUso` |
| `histórico`, `historico` | começa o fluxo do histórico | `ConsultarHistoricoAlunoCasoDeUso` |
| `aprovar` | pede confirmação da aprovação da versão apresentada | — (confirmação) → `AprovarRelatorio*CasoDeUso` |
| `cancelar` | pede confirmação do cancelamento do relatório em foco, ou descarta o áudio pendente | — → `CancelarRelatorio*CasoDeUso` |
| `continuar` / `descartar` | responde ao aviso de relatório em aberto | `RetomarRelatorio…` / `Cancelar…` |
| `sair` | pede confirmação; confirmado, desfaz o vínculo (FR-008) | `EncerrarVinculoTelegramCasoDeUso` |
| `ajuda`, `/help` | lista o que o bot faz na etapa atual | — |

## 3. Botões (teclado inline) e `callback_data`

Formato: `v1:<acao>:<argumento>`, no máximo 64 bytes. O argumento é um **identificador curto** que o
backend guardou em `dados_fluxo` (índice de opção) ou um UUID de relatório já presente no estado. O
backend sempre revalida o argumento contra o estado persistido e contra a autorização. Um botão de
mensagem antiga, que não corresponde mais ao estado, recebe `EXPIRED_ACTION` e o bot reapresenta a
etapa atual.

| Ação | Argumento | Rótulo do botão |
|---|---|---|
| `start` | — | "Início" |
| `reg` | — | "Registrar aula" |
| `sem` | — | "Relatório semestral" |
| `hist` | — | "Histórico" |
| `student` | índice da opção | nome do aluno |
| `confal` | — | "Confirmar {aluno}" |
| `trocal` | — | "Trocar aluno" |
| `cont` / `desc` | — | "Continuar de onde parei" / "Descartar e começar um novo" |
| `descok` | — | "Sim, descartar" (confirma o descarte pedido por `desc`) |
| `aprov` | versão | "Aprovar relatório" |
| `aprovok` | versão | "Sim, aprovar a versão N" |
| `alt` | — | "Solicitar alteração" |
| `canc` / `cancok` | — | "Cancelar relatório" / "Sim, cancelar" |
| `pdf` | — | "Ver PDF" |
| `tent` | — | "Tentar novamente" |
| `per` | índice | rótulo do semestre |
| `gerar` / `gerarsim` | — | "Gerar relatório semestral" / "Gerar mesmo assim" |
| `voltar` | — | "Voltar" |
| `hitem` | índice | rótulo do item do histórico |
| `sairok` | — | "Sim, sair" |
| `foco` | `a<UUID>` (relatório de aula), `s<UUID>` (semestral) ou `t<UUID>` (registro ainda em transcrição) | "Continuar no chat" / "Continuar: {aluno} ({data})" |

O botão `foco` revalida o dono e o estado do relatório, põe o relatório em foco e apresenta a etapa
atual (processamento, perguntas ou conferência); um relatório já aprovado ou cancelado não entra em
foco. As mensagens de etapa do processamento são editadas no lugar: o id da mensagem fica no
parâmetro `mensagemEtapa` do trabalho.

## 4. Estados da conversa e interpretação da próxima mensagem

O estado é **derivado** a cada update: vínculo + `fluxo`/`etapa_fluxo` + relatório em foco (status
lido do banco) + áudio pendente. A primeira linha que se aplica decide.

| # | Estado derivado | Texto livre | Áudio | Outros tipos |
|---|---|---|---|---|
| 1 | Não vinculado | tentativa de código (`VincularTelegramCasoDeUso`), com bloqueio por tentativas (FR-007) | `CODE_REQUIRED` | `CODE_REQUIRED` |
| 2 | Aguardando confirmação de ação (aprovar, cancelar, descartar, sair) | "sim" confirma; qualquer outro texto desiste e reinterpreta a mensagem como no estado anterior | desiste da confirmação; reinterpreta | `UNSUPPORTED_TYPE` |
| 3 | Relatório em foco `AWAITING_ANSWERS` | resposta às perguntas (`ResponderPerguntasCasoDeUso`, meio TEXTO), exceto palavras-chave | resposta por áudio **sem confirmação** (FR-027b, FR-024a) | `UNSUPPORTED_TYPE` + lembrete |
| 4 | Relatório em foco `AWAITING_REVIEW` | se corresponder **exatamente** a outro aluno associado, pergunta se quer trocar de aluno (mantém o relatório em aberto); senão, pedido de alteração (`SolicitarAlteracao*CasoDeUso` com `baseVersion = versao_apresentada`) | pedido de alteração por áudio **sem confirmação** (FR-027b, FR-027a) | `UNSUPPORTED_TYPE` + lembrete |
| 5 | Relatório em foco em processamento (`step ≠ nulo` ou trabalho em curso) | informa a etapa atual (`PROCESSING_IN_PROGRESS`) | `PROCESSING_IN_PROGRESS` | idem |
| 6 | Relatório em foco `PROCESSING_FAILED` | "tentar" → nova tentativa; "cancelar" → confirmação; outro texto → lembra as opções | `PROCESSING_FAILURE_OPTIONS` | idem |
| 7 | Áudio pendente de confirmação do aluno | nome de aluno → busca; confirmado, inicia o processamento | novo áudio **substitui** o anterior e pede confirmação de novo | `UNSUPPORTED_TYPE` |
| 8 | Fluxo `SEMESTER` (escolhendo aluno ou período) | nome de aluno → busca; texto de período → seleção | inicia o registro de aula (vai para o #10) | `UNSUPPORTED_TYPE` |
| 9 | Fluxo `HISTORY` (escolhendo aluno ou item) | nome de aluno → busca | inicia o registro (vai para o #10) | idem |
| 10 | Vinculado, sem foco (inclui fluxo `LESSON_REGISTRATION`) | nome de aluno → `BuscarAlunosCasoDeUso`: 1 resultado seleciona; vários → lista; nenhum → `NO_STUDENT_FOUND` + lista | recebe o áudio (guarda só o `file_id`) → pede **confirmação do aluno** (FR-040): com aluno selecionado, "Confirmar {aluno}" / "Trocar aluno"; sem aluno, lista de alunos | `UNSUPPORTED_TYPE` |

Regras transversais:

- **Palavras-chave primeiro**: nos estados 3 e 4, as palavras da seção 2 são interpretadas como
  comando, não como resposta ou alteração.
- **Relatório em aberto ao confirmar o aluno** (FR-034): se o aluno já tem relatório em aberto, o
  bot descarta o áudio pendente, mostra o aviso "Você tem um relatório em aberto" (canal de origem,
  data e hora da última alteração) e oferece continuar ou descartar. O áudio só é reaproveitado se o
  professor escolher descartar e o áudio ainda estiver válido; senão, o bot pede o áudio de novo.
- **Expiração do áudio pendente**: passados 30 minutos, na próxima interação (ou pela varredura
  periódica) o `file_id` é descartado e o bot avisa `PENDING_AUDIO_EXPIRED`.
- **Lembrete de foco** (FR-027b): as mensagens de perguntas e de conferência terminam com "Texto ou
  áudio enviado aqui será aplicado a este relatório." e o botão "Registrar aula".
- **Seleção limpa** após aprovar ou cancelar (FR-038). Depois da aprovação, o foco vai para nulo.
- **Início**: com relatórios em aberto, cada um vira um botão "Continuar: {aluno} ({data})"; os
  alunos com relatório em aberto aparecem marcados "· Em aberto" (FR-035).

## 5. Sequências de mensagens por história

### 5.1 Vinculação e início (US2)

1. Não vinculado envia qualquer texto → `CODE_REQUIRED`.
2. Envia o código válido → `LINK_CREATED` ("Conta vinculada com sucesso, {nome}!") seguido do
   bloco de início (saudação, relatórios em aberto, total de alunos, até 5 alunos, botões
   "Registrar aula", "Relatório semestral", "Histórico").
3. Código errado → `INVALID_CODE`; após 5 erros → `TEMPORARILY_BLOCKED` com o tempo
   restante. Conflitos → `TELEGRAM_ALREADY_LINKED` / `TEACHER_ALREADY_LINKED`. Vencido →
   `EXPIRED_CODE`.

### 5.2 Registro de aula (US1)

1. `reg` ou texto → "Para qual aluno?" + lista.
2. Aluno escolhido → `SELECTED_STUDENT` ("Aula de hoje, dd/mm/aaaa, com {aluno}. Envie o áudio da
   aula (mensagem de voz ou arquivo, até 50 MB).").
3. Áudio → `RECEIVED_AUDIO_CONFIRM_STUDENT` com os botões `confal` / `trocal`.
4. `confal` → a mensagem é editada para a etapa "Enviando o áudio"; a cada mudança de etapa
   ("Transcrevendo", "Estruturando o relatório") a mesma mensagem é editada, então não se acumulam
   mensagens. Aos 3 minutos → `PROCESSING_DELAYED`.
5. Resultado:
   - Perguntas → `PENDING_QUESTIONS` com a lista numerada + lembrete de foco.
   - Conferência → mensagem com cabeçalho (aluno, professor, data da aula, "Versão N"), o texto das
     5 seções (dividido em várias mensagens se passar de 4096 caracteres), o **PDF como documento**
     e os botões `aprov`, `alt`, `canc` + lembrete de foco.
   - Falha na transcrição → `AUDIO_NOT_PROCESSABLE` (nada criado).
   - Falha na estruturação → `PROCESSING_FAILURE_OPTIONS` com `tent` / `canc`.
6. Alteração (texto ou áudio) → etapas "Transcrevendo o pedido" (só para áudio) e "Aplicando a
   alteração" → `CHANGE_APPLIED` ("Alteração aplicada. Confira a versão N+1.") + "Entendi:
   “{texto}”" + nova conferência com PDF.
7. `aprov` → `CONFIRM_APPROVAL` (cita a versão N e o efeito) com `aprovok`. `aprovok` →
   `REPORT_APPROVED` (data e hora) + botões "Registrar outra aula" e "Histórico".
8. `canc` → `CONFIRM_CANCELLATION` → `cancok` → `REPORT_CANCELLED` + início.

### 5.3 Retomada entre canais (US3)

Aviso `REPORT_ALREADY_OPEN` com canal de origem e data e hora; `cont` → reapresenta a etapa
(processamento, perguntas ou conferência na versão vigente); `desc` → `CONFIRM_DISCARD` →
cancelado → pede o novo áudio. Aprovar com `versao_apresentada < versao_vigente` →
`STALE_VERSION` + nova conferência na versão vigente (FR-033).

### 5.4 Semestral (US4)

`sem` → aluno (lista ou nome) → períodos como botões (o mais recente primeiro, com "★" e "em
andamento" no corrente, e o intervalo de datas no texto) → prévia:
- 0 aprovadas → `NO_APPROVED_LESSONS_IN_PERIOD`;
- já aprovado → `SEMESTER_REPORT_ALREADY_APPROVED`;
- semestral em aberto → `REPORT_ALREADY_OPEN` (`cont`/`desc`);
- relatórios de aula em aberto → `OPEN_REPORTS_EXIST` ("Há N relatórios de aula em aberto
  neste período; eles não serão considerados.") com `gerarsim` / `voltar`;
- senão → `gerar`.

A consolidação mostra etapas; a conferência traz "N relatórios de aula foram considerados nesta
consolidação", o aviso de relatórios em aberto (se houver), as 10 seções em texto e o PDF. O ciclo
continua como em 5.2 (passos 6 a 8).

### 5.5 Histórico (US5)

`hist` → aluno → lista numerada ("Aula de dd/mm/aaaa · Aprovado em dd/mm/aaaa às hh:mm", do mais
recente ao mais antigo, até 10 por página com "Mais") como botões `hitem` → envia o **PDF oficial**
como documento. Vazio → `EMPTY_HISTORY` + botão "Registrar aula".

## 6. Avisos entre canais (FR-021)

Quando um trabalho pedido **pela Web** termina e o professor tem vínculo, o bot envia uma mensagem
curta: `AVISO_RESULTADO_WEB` ("O relatório de {aluno} está pronto para conferência.",
"...tem perguntas pendentes.", "...tem uma nova versão (N).", "...não pôde ser processado.") com o
botão "Continuar no chat". O botão põe o relatório em foco e apresenta a etapa. O aviso não muda o
foco atual da conversa até o professor tocar no botão.

## 7. Testes de contrato do canal

Cada linha da tabela da seção 4 e cada sequência da seção 5 tem um teste de conversa: os updates
JSON de entrada são enviados ao `POST /telegram/webhook`, e o teste verifica as chamadas feitas ao
WireMock da API de bots (método, `chat_id`, código de mensagem e botões) e o estado persistido
resultante. O roteiro de paridade (R-16) usa as mesmas entradas que o roteiro da Web.
