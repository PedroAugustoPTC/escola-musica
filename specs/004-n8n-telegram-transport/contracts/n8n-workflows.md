# Contrato: workflows do n8n (transporte do Telegram)

**Feature**: `004-n8n-telegram-transport` | Referências: [spec.md](../spec.md), [research.md](../research.md) R-03 a
R-07 e R-13, [data-model.md](../data-model.md) §1, contrato da 002
([canal-telegram-interno.md](../../002-api-interna-canal-telegram/contracts/canal-telegram-interno.md) §2 a §4,
[esquema v1](../../002-api-interna-canal-telegram/contracts/actions-v1.schema.json),
[OpenAPI](../../002-api-interna-canal-telegram/contracts/telegram-channel-internal.openapi.yaml)).

Este documento é a **especificação normativa dos 4 workflows**. Nenhuma regra da 002 muda: aqui só se diz como o
n8n cumpre a seção "Obrigações do orquestrador" (§4 da 002). Os JSON não levam texto livre (R-16), então a
explicação de cada passo mora aqui. Endpoints do n8n: [n8n-endpoints.openapi.yaml](./n8n-endpoints.openapi.yaml).

> Os nomes de campo, rota, cabeçalho e código de motivo são os do **esquema v1 em inglês** (feature 003). O texto
> da 002 ainda cita `X-Canal-Entrega-Token`; vale o OpenAPI: **`X-Channel-Delivery-Token`**.

## 1. Princípios que valem para os quatro

| # | Regra | Verificada por |
|---|---|---|
| W1 | O n8n **não escreve texto ao professor**: todo `text`, `caption` e rótulo de botão vem das ações do backend | `WorkflowGuardTest` |
| W2 | Segredos e endereços só por `$env` das 5 variáveis de [research.md R-03](../research.md); nunca `credentials` | `WorkflowGuardTest` |
| W3 | Destinos HTTP só `BACKEND_INTERNAL_URL` e `TELEGRAM_API_URL`; nunca `getFile` | `WorkflowGuardTest` |
| W4 | Nenhuma ramificação por conteúdo da conversa: só por `type` da ação, `state` da ação, status HTTP, `outcome` e presença do token de entrega | `WorkflowGuardTest` |
| W5 | Nenhum estado entre execuções; nenhum nó de banco; nenhum nó de IA | `WorkflowGuardTest` |
| W6 | Nenhuma execução é salva; nenhum log traz corpo, URL ou token | `N8nPrivacyIT` |
| W7 | Falha de execução **nunca** interrompe a lista; só falha de **transporte com o backend** a interrompe | `N8nFailureHandlingIT` |
| W8 | O n8n não decide reenvio nem compensação; classifica e informa | `N8nFailureHandlingIT` |

## 2. `telegram-inbound` — único receptor de updates

| Item | Valor |
|---|---|
| Gatilho | `POST /webhook/telegram-update`, corpo bruto preservado (`rawBody`), resposta controlada pelo nó `Respond to Webhook` |
| Quem chama | o servidor local da API de bots, pela rede interna (R-02) |

Passos:

1. **Repassar** — `POST {BACKEND_INTERNAL_URL}/internal/v1/channel/telegram/update` com o **corpo exatamente como
   recebido** (`Content-Type: application/json`), `X-N8N-Service-Token: {BACKEND_SERVICE_TOKEN}` e
   `X-Telegram-Bot-Api-Secret-Token` copiado do cabeçalho recebido (ausente continua ausente; o n8n não o valida).
   Sem seguir redirecionamento; `neverError`; resposta completa. (Contrato 002 §2.1, obrigação 2.)
2. **Decidir o transporte** — status `200` com corpo `UpdateResponse` válido → passo 3. Qualquer outra coisa
   (`401`, `5xx`, conexão recusada, corpo ilegível) → `Respond to Webhook 503` **sem corpo** e fim: o n8n não
   responde texto, não registra o update como tratado (FR-015); o Telegram reenvia.
3. **Executar** — chama `execute-actions` com `actions` (lista possivelmente vazia: não faz nada, nenhum erro).
4. **Responder** — `Respond to Webhook 200` sem corpo, depois de **toda** a lista. Falha de transporte
   devolvida por `execute-actions` → `503` sem corpo (o reenvio do Telegram retoma só o que falta, porque o
   backend devolve as ações não confirmadas, contrato §2.1).

Garantias: update de tipo não suportado ou ilegível recebe `actions: []` do backend → `200` e nada é enviado
(US2.6); todo `callback_query` traz `CALLBACK_REPLY` primeiro, executado antes das demais (FR-014).

## 3. `channel-delivery` — entrega proativa

| Item | Valor |
|---|---|
| Gatilho | `POST /webhook/channel-delivery` |
| Quem chama | o backend (`N8nOrchestratorAdapter`) com `X-Channel-Delivery-Token` e `Idempotency-Key = batchId` |

Passos:

1. **Autenticar a entrega** — compara `X-Channel-Delivery-Token` com `CHANNEL_DELIVERY_TOKEN`; ausente ou
   diferente → `Respond to Webhook 401` **sem corpo** e fim; **nada é executado** (US3.5; obrigação 1).
2. **Executar** — chama `execute-actions` com `actions` do `DeliveryBatch`.
3. **Aceitar** — `200` vazio **depois** de executar e confirmar o lote (R-07). Falha de transporte com o
   backend durante a execução → `503` sem corpo (o backend reentrega os mesmos `actionId`).
4. **Idempotência** — `Idempotency-Key` não é guardado: a repetição do mesmo `actionId` é tratada pela consulta
   de estado dentro de `execute-actions` (FR-022). Sem estado próprio.

## 4. `execute-actions` — executor compartilhado

Entrada: `actions` (lista v1). Percorre **em ordem**, uma ação por vez.

| Etapa | Regra |
|---|---|
| E1. Estado | `GET {BACKEND_INTERNAL_URL}/internal/v1/channel/telegram/actions/{actionId}`. `state` `EXECUTED` ou `FAILED` → **pula** (nem executa nem confirma). Erro de conexão ou `5xx` → **aborta** a lista com falha de transporte. `404`, `PENDING`, `DELIVERED` → executa. Qualquer outro `4xx` (`401`) → aborta com falha de transporte (configuração errada deve aparecer, não ser engolida) |
| E2. Despacho por `type` | `MESSAGE`, `EDIT`, `DOCUMENT`, `CALLBACK_REPLY`, `STEP_MESSAGE`; qualquer outro valor → resultado `FAILED` `ACTION_NOT_SUPPORTED`, **sem chamada externa**, e segue (FR-019). Campos desconhecidos de ação conhecida são ignorados |
| E3. Chamada à API de bots | `POST {TELEGRAM_API_URL}/bot{TELEGRAM_BOT_TOKEN}/<método>` conforme a tabela abaixo; sem retentativa própria; `neverError` |
| E4. Classificação | tabela de erros abaixo; função pura |
| E5. Confirmação | `POST …/confirmations` com **um** item `{actionId, outcome, reasonCode?, messageId?}` e `version: "v1"`, imediatamente após a execução; até 3 tentativas com 1 s de espera; se falhar, **não** aborta nem derruba a resposta (R-06) |
| E6. Continuação | próxima ação, **independentemente** do resultado de E4 (W7) |

### 4.1 Tradução ação → API de bots (igual à tabela da 002 §4)

| Ação v1 | Método | Corpo |
|---|---|---|
| `MESSAGE` | `sendMessage` | `chat_id`, `text`, `parse_mode=HTML`, `link_preview_options.is_disabled=true`, `reply_markup.inline_keyboard` a partir de `keyboard.rows` (`label`→`text`, `data`→`callback_data`; sem `keyboard` ou `rows` vazio → sem `reply_markup`) |
| `EDIT` | `editMessageText` | `chat_id`, `message_id`, `text`, `parse_mode=HTML`, `reply_markup` (teclado vazio **remove** os botões) |
| `DOCUMENT` | `sendDocument` (`multipart/form-data`) | `chat_id`; `document` = bytes de **uma única** `GET …/documents/{documentId}`; `caption` + `parse_mode=HTML` se houver `caption`; nome do arquivo = `fileName`. `404`, `5xx` ou erro de conexão na busca → `FAILED` `DOCUMENT_UNAVAILABLE` **sem** chamar a API de bots e sem repetir. Os bytes não são guardados além da ação |
| `CALLBACK_REPLY` | `answerCallbackQuery` | `callback_query_id = callbackId` |
| `STEP_MESSAGE` | `editMessageText` se há `editMessageId`, senão `sendMessage` | como `EDIT`/`MESSAGE`; o `messageId` informado é o de `result.message_id` (envio) ou o `editMessageId` (edição) |

### 4.2 Classificação da resposta da API de bots (única decisão do n8n, só rótulo)

| Resposta | `outcome` | `reasonCode` | Observação |
|---|---|---|---|
| `2xx` | `EXECUTED` | — | `messageId` conforme 4.1 |
| `editMessageText` com "message is not modified" | `EXECUTED` | — | sem compensação |
| edição com mensagem inexistente, apagada ou antiga ("message to edit not found", "message can't be edited", `MESSAGE_ID_INVALID`) | `FAILED` | `MESSAGE_NOT_EDITABLE` | o backend decide a compensação |
| `403` ou "chat not found" | `FAILED` | `CHAT_UNAVAILABLE` | |
| `429` | `FAILED` | `RATE_LIMITED` | o n8n **não** espera nem reenvia |
| busca de PDF sem sucesso | `FAILED` | `DOCUMENT_UNAVAILABLE` | |
| tipo de ação desconhecido | `FAILED` | `ACTION_NOT_SUPPORTED` | |
| qualquer outro erro, inclusive falha de conexão com a API de bots | `FAILED` | `TELEGRAM_ERROR` | |

O `reasonCode` nunca leva texto livre nem trecho da resposta do Telegram. O código `NO_CONFIRMATION` é só do
backend; o n8n nunca o envia. A fonte executável da tabela é `SimulatedActionsExecutorTest.classify`; a mesma
tabela de casos alimenta `N8nFailureHandlingIT`, de modo que o simulador de testes e o workflow não divergem.

## 5. `transport-health` — saúde

| Rota | Resposta | Quem usa |
|---|---|---|
| `GET /webhook/transport-health/live` | `200 {"status":"UP"}` sem chamar ninguém | `healthcheck` do Docker; `n8n.sh health` |
| `GET /webhook/transport-health/deep` | `200 {"status":"UP","backend":"ok","botApi":"ok"}` se: `GET …/actions/u0-0` retorna `404` (token de serviço aceito; `401` ⇒ `"backend":"unauthorized"`; falha ⇒ `"backend":"down"`) **e** `getMe` retorna `ok:true` (`"botApi":"ok"`; senão `"botApi":"down"`). Qualquer componente fora ⇒ `503` com o mesmo corpo | `n8n.sh health --deep`; `telegram-webhook.sh verify` e pré-condição do `switch` |

O corpo nunca contém token, URL, nome do bot nem versão.

## 6. Guardião de workflows (`WorkflowGuardTest`)

Regras e pontos de falha em [research.md R-05](../research.md). Mensagem de falha em português, citando o arquivo
e o caminho JSON do nó, **nunca** o valor encontrado.

## 7. Cenários de contrato dos workflows (base de `N8nInboundWorkflowIT` e afins)

| # | Cenário | Resultado esperado |
|---|---|---|
| N1 | update com segredo e corpo bruto | o backend recebe corpo idêntico, `X-N8N-Service-Token` e o cabeçalho de segredo copiado; o n8n responde `200` depois da lista |
| N2 | backend responde `401`, `500` ou fora do ar | o n8n responde `503` sem corpo; **nenhuma** chamada à API de bots; **nenhuma** confirmação |
| N3 | lista `[CALLBACK_REPLY, MESSAGE, DOCUMENT]` | chamadas à API de bots **nessa ordem**; três confirmações `EXECUTED`; PDF buscado uma vez |
| N4 | lista `[MESSAGE, EDIT(recusado), MESSAGE]` | a 3ª é executada; a 2ª é confirmada `FAILED MESSAGE_NOT_EDITABLE`; nenhuma nova ação criada pelo n8n |
| N5 | `EDIT` com "message is not modified" | confirmada `EXECUTED` |
| N6 | `403`, `429`, erro desconhecido, conexão recusada na API de bots | `CHAT_UNAVAILABLE`, `RATE_LIMITED`, `TELEGRAM_ERROR`, `TELEGRAM_ERROR`; o texto do erro não aparece na confirmação |
| N7 | `DOCUMENT` com `404` ou `5xx` do backend | exatamente **uma** `GET`; `DOCUMENT_UNAVAILABLE`; nenhuma chamada a `sendDocument` |
| N8 | ação de `type` novo + campo desconhecido em ação conhecida | a nova é `ACTION_NOT_SUPPORTED`; as demais executam; o campo extra é ignorado |
| N9 | `STEP_MESSAGE` sem e com `editMessageId` | `sendMessage` com `messageId` de `result.message_id`; `editMessageText` com o `editMessageId` |
| N10 | ação com estado `EXECUTED` ou `FAILED` | pulada, sem chamada à API de bots e sem confirmação |
| N11 | entrega proativa sem token, com token errado | `401` sem corpo; nenhuma chamada a ninguém |
| N12 | entrega proativa válida | `200` somente depois das confirmações; ordem preservada |
| N13 | backend cai no meio da lista (a `GET` da 2ª ação falha) | a 1ª já executada e confirmada; resposta `503`; ao reenviar, só as restantes executam |
| N14 | `GET /deep` com backend `401`, com `getMe` falhando, tudo ok | `503 backend:unauthorized`, `503 botApi:down`, `200` |
| N15 | update com `message` de áudio (voice/audio/document) | o corpo segue ao backend com `file_id`; **nenhuma** chamada a `getFile` nem download |
| N16 | confirmação recusada (`5xx`) 3 vezes | a lista continua e a resposta ao Telegram é `200`; nada é reexecutado |
