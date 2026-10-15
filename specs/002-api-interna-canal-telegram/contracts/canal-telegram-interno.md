# Contrato: API interna de canal do Telegram (v1)

**Feature**: `002-api-interna-canal-telegram` | Referências: [spec.md](../spec.md),
[research.md](../research.md) R-03 a R-09, [data-model.md](../data-model.md),
[acoes-v1.schema.json](./acoes-v1.schema.json) (formato normativo dos corpos),
[canal-telegram-interno.openapi.yaml](./canal-telegram-interno.openapi.yaml) (rotas, cabeçalhos e
códigos HTTP). O comportamento da conversa (estados, textos, botões `v1:<acao>:<arg>`) **não** está
aqui: continua em `specs/001-fluxo-relatorios/contracts/telegram-conversa.md` e `mensagens.md`.

Dois lados falam este contrato:

- **Backend** (esta feature): recebe updates, devolve ações, entrega ações proativas, recebe
  confirmações e serve PDFs. É a única parte que decide o que responder.
- **Orquestrador (n8n)** (feature `004`): só transporta (Princípio II). Esta seção "Obrigações do
  orquestrador" é a especificação do que o workflow deve fazer.

## 1. Autenticação

| Item | Regra |
|---|---|
| Cabeçalho | `X-N8N-Service-Token: <token>` em **toda** rota `/interno/**` (FR-024) |
| Comparação | tempo constante, sobre digests SHA-256; até **dois** tokens válidos (`atual`, `proximo`), ambos sempre comparados (FR-026a) |
| Falha | `401` **sem corpo**, para ausente, errado ou nenhum token configurado (fail-closed, FR-026) |
| Cookie de portal | **ignorado** na área interna (FR-025); só o cookie, sem token = `401` |
| Token em rota do portal | `/api/v1/**` continua exigindo sessão; o token de serviço não autentica nada ali (FR-025) |
| CSRF | desligado **somente** na cadeia `/interno/**` (sem cookie, sem navegador); inalterado no portal (FR-027) |
| Segredo do webhook | em `/atualizacao`, além do token: `X-Telegram-Bot-Api-Secret-Token`, tempo constante; ausente/errado = `401` sem corpo (FR-006) |
| Exposição | a área interna não é publicada pelo proxy (o Caddy só encaminha `/api/*`); a decisão de rede é da `004`, mas a API recusa tudo sem token mesmo se alcançada |

Rotação do token: (1) pôr o novo em `TELEGRAM_CHANNEL_NEXT_TOKEN`; (2) trocar o valor no n8n;
(3) promover (`CURRENT ← NEXT`, esvaziar `NEXT`). Nenhuma janela de recusa.

## 2. Rotas

Todas sob `/interno/v1/canal/telegram`. Corpos em `application/json; charset=utf-8`, exceto o PDF.

| Método e caminho | Para quê | Sucesso | Falhas |
|---|---|---|---|
| `POST /update` | entrega o **update bruto** do Telegram; devolve as ações a executar | `200` `UpdateResponse` | `401` (token ou segredo); `200` com `actions: []` para corpo ilegível, tipo não suportado ou update já totalmente confirmado |
| `POST /confirmations` | o orquestrador informa o resultado de cada ação executada (mesma rota para respostas a update e proativas) | `200` `ConfirmationResponse` | `401`; `400` `Error` se o corpo for inválido |
| `GET /documents/{documentId}` | bytes do PDF referenciado por uma ação `DOCUMENT` | `200` `application/pdf` | `401`; `404` sem corpo (desconhecido, vencido, malformado, URL/caminho) |
| `GET /actions/{actionId}` | estado de uma ação (idempotência do receptor sem estado) | `200` `ActionStateResponse` | `401`; `404` sem corpo |

`POST /update` **não** faz o backend enviar nada ao Telegram (FR-010): o estado da conversa já
está gravado quando a resposta sai, e o envio é do orquestrador.

### 2.1 `POST /update`

- Cabeçalhos: `X-N8N-Service-Token`, `X-Telegram-Bot-Api-Secret-Token`, `Content-Type: application/json`.
- Corpo: o objeto `Update` do Telegram, **sem alterações** (FR-001). Só `message` (chat privado) e
  `callback_query` são interpretados; o resto vira `actions: []`. Grupos recebem a ação
  `PRIVATE_CONVERSATION_ONLY`, como hoje.
- Resposta: `UpdateResponse`. Toda mensagem recebida traz ao menos uma ação, inclusive
  `INTERNAL_ERROR` em falha inesperada; todo `callback_query` traz `CALLBACK_REPLY` primeiro
  (FR-008).
- **Update repetido** (mesmo `update_id`): o estado não é reprocessado; a resposta traz só as ações
  **ainda não confirmadas**, com os mesmos `actionId`; lista vazia se todas foram confirmadas (FR-007).
- Latência alvo: 95% em até 2 s (SC-003).

### 2.2 `POST /confirmations`

- Corpo: `ConfirmationRequest`: lista de `{actionId, outcome, reasonCode?, messageId?}`.
- `messageId` é **obrigatório** para `STEP_MESSAGE` com `EXECUTED`: o backend o grava no trabalho
  para as próximas etapas editarem a mesma mensagem (FR-017).
- `FAILED` exige `reasonCode` (código em caixa alta, sem texto livre; valor desconhecido é aceito e tratado como `TELEGRAM_ERROR`). Com `MESSAGE_NOT_EDITABLE`, o
  backend decide a compensação e a entrega como **nova ação** (`<actionId>-c`) pelo mesmo caminho das
  proativas (FR-007b).
- Cada resultado é validado isoladamente: um item inválido vai para `ignored` e não impede os demais.
- Concorrência: a confirmação nunca é perdida nem sobrescrita pelo despachante; se chegar enquanto o
  backend ainda grava o aceite HTTP, o estado final (`EXECUTED`/`FAILED`) prevalece (data-model §1.2.2, invariante 6).
- Idempotente: repetir a mesma confirmação não tem efeito. `actionId` desconhecido, descartado, vencido
  ou com resultado divergente do já registrado volta em `ignored` (`200`, não é erro).
- Pode ser enviada em lote (várias ações) ou uma a uma; a ordem entre elas não importa.

### 2.3 `GET /documents/{documentId}`

- `documentId` é o valor opaco de 43 caracteres Base64url da ação `DOCUMENT`. Qualquer outro valor
  (URL, caminho, tamanho ou alfabeto errado) → `404`. O backend **nunca** acessa endereço vindo do
  payload (FR-023).
- Sucesso: `Content-Type: application/pdf`, `Content-Disposition: inline; filename="…"` (nome sem
  identificadores internos), `Cache-Control: no-store`. Bytes idênticos aos do portal (SC-007).
- Validade: cobre todas as tentativas de entrega (prazo + 1 h; padrão 25 h). O orquestrador pode
  repetir o `GET` em retentativas.
- Falha real de leitura → `5xx`, nunca conteúdo parcial; o orquestrador retenta.

### 2.4 `GET /actions/{actionId}`

Existe para que o n8n, **sem guardar estado** (Princípio II), saiba se uma ação repetida já foi
tratada. Estados: `PENDING`, `DELIVERED`, `EXECUTED`, `FAILED`, `PERMANENTLY_FAILED`, `DISCARDED`.

## 3. Entrega proativa (backend → orquestrador)

Para ações que nascem sem update do professor (resultado do processamento, etapas, demora, avisos entre
canais, compensações), com `TELEGRAM_CHANNEL_DELIVERY=ORCHESTRATOR`.

```text
POST {CANAL_TELEGRAM_ORQUESTRADOR_URL}
X-Canal-Entrega-Token: <segredo do destino>
Idempotency-Key: <batchId>
Content-Type: application/json

{ "version": "v1", "batchId": "p:result:7f3c…", "origin": "PROACTIVE",
  "createdAt": "2026-10-02T15:04:05Z", "attempt": 1, "actions": [ … ] }      # LoteEntrega
```

| Aspecto | Regra |
|---|---|
| Aceite | qualquer `2xx` = **aceita**; a ação passa a `DELIVERED` e aguarda a confirmação (`POST /confirmations`) |
| Recusa | timeout (conexão 3 s, resposta 10 s), erro de conexão, `5xx` ou `4xx` = **recusa**: continua `PENDING` e é reenviada |
| Retentativa | 30 s inicial, dobrando até 15 min, prazo 24 h desde a criação, jitter ±10%; tudo configurável (FR-018) |
| Prazo vencido | `PERMANENTLY_FAILED`, registrada sem dado de aluno e visível por métrica (US3, cenário 6) |
| Identidade | `actionId` e `batchId` idênticos em **todas** as tentativas (FR-013) |
| Ordem | as ações de um lote vêm na ordem de execução; um lote do mesmo chat só é entregue depois que o anterior foi **confirmado** (ou passou `confirmacao-max-segundos`, 60 s) (FR-016); chats diferentes são independentes |
| Atomicidade | o lote é entregue inteiro |
| Vínculo desfeito | ação `PENDING` de professor sem vínculo é `DISCARDED` com registro, sem entrega |
| Etapa | em `STEP_MESSAGE` com `jobId`, `editMessageId` é preenchido no momento do despacho com o id da mensagem de etapa conhecida do trabalho |

## 4. Obrigações do orquestrador (especificação para a feature `004`)

1. **Autenticar-se** com `X-N8N-Service-Token` em toda chamada ao backend e **validar**
   `X-Canal-Entrega-Token` nas entregas recebidas. Segredos só do ambiente/cofre; nenhum segredo nem id
   de credencial nos JSON dos workflows (constituição, "Orquestrador de canal").
2. **Repassar o update bruto**, sem alterar nem interpretar (FR-001), incluindo o cabeçalho de segredo
   do webhook do Telegram.
3. **Executar as ações em ordem, sem parar na primeira falha** (FR-007b). A confirmação do toque em
   botão (`CALLBACK_REPLY`) é executada independentemente do resultado das demais.
4. **Informar cada resultado** por `POST /confirmations`: `EXECUTED` (com `messageId` nas mensagens
   de etapa) ou `FAILED` com `reasonCode`. **Não** decidir compensações, **não** reenviar por conta
   própria.
5. **Idempotência (FR-013)**: ao receber uma ação com `actionId` já tratado, não executá-la de novo. Sem
   guardar estado, o workflow consulta `GET /actions/{actionId}` e pula se o estado for `EXECUTED` ou
   `FAILED`.
6. **Tolerar evolução (FR-003a)**: ignorar campos desconhecidos; para `type` desconhecido, pular a
   ação, confirmar `FAILED` com `ACTION_NOT_SUPPORTED` e seguir com as demais.
7. **Documento**: para `DOCUMENT`, obter os bytes em `GET /documents/{documentId}` com o mesmo
   token, anexar ao envio e **nunca** montar URL a partir de outro campo. Faz **uma única** tentativa de
   `GET`; `404`/`5xx` → `FAILED` com `DOCUMENT_UNAVAILABLE`, sem nova tentativa por conta própria
   (quem decide o que fazer depois é o backend, item 4).
8. **Áudio nunca passa pelo n8n** (FR-028): o update traz só o `file_id`; o backend baixa o arquivo
   pelo servidor local da API de bots. O workflow não chama `getFile` nem baixa o áudio.
9. **Responder `2xx` às entregas só depois de aceitar a ação** (não é necessário ter executado; a
   confirmação é a prova de execução).
10. **Sem persistência de domínio** e sem logs com texto de aluno (Princípio II e IV).

### Tradução ação → API de bots (referência para a `004` e para o executor simulado dos testes)

| Ação v1 | Chamada ao Telegram | Observações |
|---|---|---|
| `MESSAGE` | `sendMessage` | `parse_mode=HTML`, `link_preview_options.is_disabled=true`, `reply_markup.inline_keyboard` a partir de `keyboard.rows` (`label`→`text`, `data`→`callback_data`) |
| `EDIT` | `editMessageText` | mesmo `parse_mode`; teclado vazio remove os botões |
| `DOCUMENT` | `sendDocument` (multipart) | bytes do `GET /documents/…`; `caption` e `parse_mode=HTML` se houver `caption` |
| `CALLBACK_REPLY` | `answerCallbackQuery` | `callback_query_id` = `callbackId` |
| `STEP_MESSAGE` | `editMessageText` se há `editMessageId`, senão `sendMessage` | confirmar com o `message_id` resultante |

### Erro do Telegram → resultado informado ao backend

O orquestrador só **classifica** a resposta da API de bots; nenhuma outra decisão é dele (item 4).

| Resposta do Telegram | `outcome` | `reasonCode` | Efeito no backend |
|---|---|---|---|
| sucesso | `EXECUTED` | — | segue |
| `editMessageText` com "message is not modified" | `EXECUTED` | — | segue; **não** gera compensação (é o que o envio direto faz hoje: ignora, sem mensagem nova) |
| `editMessageText` com mensagem inexistente, apagada ou antiga demais para editar | `FAILED` | `MESSAGE_NOT_EDITABLE` | compensação `<actionId>-c` se a ação tiver uma |
| chat bloqueado, bot removido, usuário inativo (`403`, "chat not found") | `FAILED` | `CHAT_UNAVAILABLE` | só registra |
| `429` | `FAILED` | `RATE_LIMITED` | só registra |
| `GET /documents/{id}` `404`/`5xx` | `FAILED` | `DOCUMENT_UNAVAILABLE` | compensação `<actionId>-c`: aviso curto ao professor com o botão para pedir o PDF de novo (FR-007c; decidido em 2026-10-08) |
| qualquer outro erro da API de bots | `FAILED` | `TELEGRAM_ERROR` | só registra |

`reasonCode` desconhecido recebido pelo backend é tratado como `TELEGRAM_ERROR` (nunca `400`), para que
um código novo do orquestrador não derrube as demais confirmações do lote (§5, evolução aditiva). A lista
acima está fechada na v1 (decidido em 2026-10-08); o código `NO_CONFIRMATION` é só do backend (ação
entregue e nunca confirmada até o prazo) e o orquestrador nunca o envia.

## 5. Versionamento

- **Aditivo (permanece na v1)**: campo opcional novo; tipo de ação novo; valor novo em enum de
  `reasonCode`. O receptor tolera (item 6).
- **Incompatível (nova versão)**: remover ou mudar o significado de um campo; tornar obrigatório um
  campo existente. Cria `/interno/v2/…`; a v1 continua servida enquanto o orquestrador não migrar
  (FR-003).
- A versão está em **todo** corpo (`version`). Um corpo recebido com versão desconhecida → `400`
  `UNSUPPORTED_VERSION`.

## 6. Privacidade e logs (FR-029)

Logs, métricas e corpos de erro desta área trazem só identificadores técnicos (`updateId`, `actionId`,
`batchId`), estados e códigos. Nunca CPF, nome de aluno, texto de relatório, transcrição, áudio, token, o
payload recebido, mensagens de exceção de parsing, a URL do orquestrador (pode conter segredo no caminho)
nem o `documentId` (credencial ao portador, válido por cerca de 25 h; vale também para logs de `404`). O texto das ações existe no banco só enquanto necessário e é zerado após a retenção
(R-11); ação entregue e nunca confirmada expira no prazo de entrega e também é zerada (decidido em
2026-10-08).

## 7. Cenários do contrato (base dos testes, `FR-031`)

| # | Cenário | Resultado esperado |
|---|---|---|
| C1 | texto de professor não vinculado | `MESSAGE` com o texto de `CODE_REQUIRED`, `actionId=u<N>-0` |
| C2 | toque em "Aprovar relatório" | `CALLBACK_REPLY` e depois `MESSAGE` de `CONFIRM_APPROVAL` com botões |
| C3 | relatório pronto (texto longo + PDF) | `MESSAGE`s divididas, `DOCUMENT`, botões na ordem de hoje |
| C4 | mesmo `update_id` duas vezes, nada confirmado | mesmas ações, mesmos ids |
| C5 | mesmo `update_id` após confirmar tudo | `actions: []` |
| C6 | mesmo `update_id` após confirmar parte | só as não confirmadas |
| C7 | token ausente / errado / só cookie / `proximo` | `401` / `401` / `401` / aceito |
| C8 | segredo do webhook ausente ou errado | `401` sem corpo |
| C9 | orquestrador fora do ar, depois volta | reentrega com `actionId` iguais; uma só execução; entrega em ordem |
| C10 | confirmação `FAILED` + `MESSAGE_NOT_EDITABLE` em edição | nova ação `<id>-c` entregue; não repete a edição |
| C11 | prazo vencido sem aceite | `PERMANENTLY_FAILED`, sem dado de aluno no log |
| C12 | `GET /documents` com id válido / vencido / URL | `200` PDF idêntico ao do portal / `404` / `404` |
| C13 | áudio acima do limite | `MESSAGE` de `AUDIO_TOO_LARGE`, nada baixado |
| C14 | `GET /actions/{id}` | estado correto; `404` para desconhecido |
