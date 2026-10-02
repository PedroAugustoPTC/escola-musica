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
- **Orquestrador (n8n)** (feature `003`): só transporta (Princípio II). Esta seção "Obrigações do
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
| Exposição | a área interna não é publicada pelo proxy (o Caddy só encaminha `/api/*`); a decisão de rede é da `003`, mas a API recusa tudo sem token mesmo se alcançada |

Rotação do token: (1) pôr o novo em `CANAL_TELEGRAM_TOKEN_PROXIMO`; (2) trocar o valor no n8n;
(3) promover (`ATUAL ← PROXIMO`, esvaziar `PROXIMO`). Nenhuma janela de recusa.

## 2. Rotas

Todas sob `/interno/v1/canal/telegram`. Corpos em `application/json; charset=utf-8`, exceto o PDF.

| Método e caminho | Para quê | Sucesso | Falhas |
|---|---|---|---|
| `POST /atualizacao` | entrega o **update bruto** do Telegram; devolve as ações a executar | `200` `RespostaAtualizacao` | `401` (token ou segredo); `200` com `acoes: []` para corpo ilegível, tipo não suportado ou update já totalmente confirmado |
| `POST /confirmacoes` | o orquestrador informa o resultado de cada ação executada (mesma rota para respostas a update e proativas) | `200` `RespostaConfirmacao` | `401`; `400` `Erro` se o corpo for inválido |
| `GET /documentos/{documentoId}` | bytes do PDF referenciado por uma ação `DOCUMENTO` | `200` `application/pdf` | `401`; `404` sem corpo (desconhecido, vencido, malformado, URL/caminho) |
| `GET /acoes/{acaoId}` | estado de uma ação (idempotência do receptor sem estado) | `200` `RespostaEstadoAcao` | `401`; `404` sem corpo |

`POST /atualizacao` **não** faz o backend enviar nada ao Telegram (FR-010): o estado da conversa já
está gravado quando a resposta sai, e o envio é do orquestrador.

### 2.1 `POST /atualizacao`

- Cabeçalhos: `X-N8N-Service-Token`, `X-Telegram-Bot-Api-Secret-Token`, `Content-Type: application/json`.
- Corpo: o objeto `Update` do Telegram, **sem alterações** (FR-001). Só `message` (chat privado) e
  `callback_query` são interpretados; o resto vira `acoes: []`. Grupos recebem a ação
  `SOMENTE_CONVERSA_PRIVADA`, como hoje.
- Resposta: `RespostaAtualizacao`. Toda mensagem recebida traz ao menos uma ação, inclusive
  `ERRO_INTERNO` em falha inesperada; todo `callback_query` traz `RESPOSTA_CALLBACK` primeiro
  (FR-008).
- **Update repetido** (mesmo `update_id`): o estado não é reprocessado; a resposta traz só as ações
  **ainda não confirmadas**, com os mesmos `acaoId`; lista vazia se todas foram confirmadas (FR-007).
- Latência alvo: 95% em até 2 s (SC-003).

### 2.2 `POST /confirmacoes`

- Corpo: `RequisicaoConfirmacao`: lista de `{acaoId, situacao, codigoMotivo?, mensagemId?}`.
- `mensagemId` é **obrigatório** para `MENSAGEM_ETAPA` com `EXECUTADA`: o backend o grava no trabalho
  para as próximas etapas editarem a mesma mensagem (FR-017).
- `FALHOU` exige `codigoMotivo` (enum `CodigoMotivo`, sem texto livre). Com `MENSAGEM_NAO_EDITAVEL`, o
  backend decide a compensação e a entrega como **nova ação** (`<acaoId>-c`) pelo mesmo caminho das
  proativas (FR-007b).
- Idempotente: repetir a mesma confirmação não tem efeito. `acaoId` desconhecido, descartado, vencido
  ou com resultado divergente do já registrado volta em `ignoradas` (`200`, não é erro).
- Pode ser enviada em lote (várias ações) ou uma a uma; a ordem entre elas não importa.

### 2.3 `GET /documentos/{documentoId}`

- `documentoId` é o valor opaco de 43 caracteres Base64url da ação `DOCUMENTO`. Qualquer outro valor
  (URL, caminho, tamanho ou alfabeto errado) → `404`. O backend **nunca** acessa endereço vindo do
  payload (FR-023).
- Sucesso: `Content-Type: application/pdf`, `Content-Disposition: inline; filename="…"` (nome sem
  identificadores internos), `Cache-Control: no-store`. Bytes idênticos aos do portal (SC-007).
- Validade: cobre todas as tentativas de entrega (prazo + 1 h; padrão 25 h). O orquestrador pode
  repetir o `GET` em retentativas.
- Falha real de leitura → `5xx`, nunca conteúdo parcial; o orquestrador retenta.

### 2.4 `GET /acoes/{acaoId}`

Existe para que o n8n, **sem guardar estado** (Princípio II), saiba se uma ação repetida já foi
tratada. Estados: `PENDENTE`, `ENTREGUE`, `EXECUTADA`, `FALHOU`, `FALHA_DEFINITIVA`, `DESCARTADA`.

## 3. Entrega proativa (backend → orquestrador)

Para ações que nascem sem update do professor (resultado do processamento, etapas, demora, avisos entre
canais, compensações), com `CANAL_TELEGRAM_ENTREGA=ORQUESTRADOR`.

```text
POST {CANAL_TELEGRAM_ORQUESTRADOR_URL}
X-Canal-Entrega-Token: <segredo do destino>
Idempotency-Key: <loteId>
Content-Type: application/json

{ "versao": "v1", "loteId": "p:resultado:7f3c…", "origem": "PROATIVA",
  "criadoEm": "2026-10-02T15:04:05Z", "tentativa": 1, "acoes": [ … ] }      # LoteEntrega
```

| Aspecto | Regra |
|---|---|
| Aceite | qualquer `2xx` = **aceita**; a ação passa a `ENTREGUE` e aguarda a confirmação (`POST /confirmacoes`) |
| Recusa | timeout (conexão 3 s, resposta 10 s), erro de conexão, `5xx` ou `4xx` = **recusa**: continua `PENDENTE` e é reenviada |
| Retentativa | 30 s inicial, dobrando até 15 min, prazo 24 h desde a criação, jitter ±10%; tudo configurável (FR-018) |
| Prazo vencido | `FALHA_DEFINITIVA`, registrada sem dado de aluno e visível por métrica (US3, cenário 6) |
| Identidade | `acaoId` e `loteId` idênticos em **todas** as tentativas (FR-013) |
| Ordem | as ações de um lote vêm na ordem de execução; um lote do mesmo chat só é entregue depois que o anterior foi **confirmado** (ou passou `confirmacao-max-segundos`, 60 s) (FR-016); chats diferentes são independentes |
| Atomicidade | o lote é entregue inteiro |
| Vínculo desfeito | ação `PENDENTE` de professor sem vínculo é `DESCARTADA` com registro, sem entrega |
| Etapa | em `MENSAGEM_ETAPA` com `trabalhoId`, `editarMensagemId` é preenchido no momento do despacho com o id da mensagem de etapa conhecida do trabalho |

## 4. Obrigações do orquestrador (especificação para a feature `003`)

1. **Autenticar-se** com `X-N8N-Service-Token` em toda chamada ao backend e **validar**
   `X-Canal-Entrega-Token` nas entregas recebidas. Segredos só do ambiente/cofre; nenhum segredo nem id
   de credencial nos JSON dos workflows (constituição, "Orquestrador de canal").
2. **Repassar o update bruto**, sem alterar nem interpretar (FR-001), incluindo o cabeçalho de segredo
   do webhook do Telegram.
3. **Executar as ações em ordem, sem parar na primeira falha** (FR-007b). A confirmação do toque em
   botão (`RESPOSTA_CALLBACK`) é executada independentemente do resultado das demais.
4. **Informar cada resultado** por `POST /confirmacoes`: `EXECUTADA` (com `mensagemId` nas mensagens
   de etapa) ou `FALHOU` com `codigoMotivo`. **Não** decidir compensações, **não** reenviar por conta
   própria.
5. **Idempotência (FR-013)**: ao receber uma ação com `acaoId` já tratado, não executá-la de novo. Sem
   guardar estado, o workflow consulta `GET /acoes/{acaoId}` e pula se o estado for `EXECUTADA` ou
   `FALHOU`.
6. **Tolerar evolução (FR-003a)**: ignorar campos desconhecidos; para `tipo` desconhecido, pular a
   ação, confirmar `FALHOU` com `ACAO_NAO_SUPORTADA` e seguir com as demais.
7. **Documento**: para `DOCUMENTO`, obter os bytes em `GET /documentos/{documentoId}` com o mesmo
   token, anexar ao envio e **nunca** montar URL a partir de outro campo. `404`/`5xx` → `FALHOU`
   (`DOCUMENTO_INDISPONIVEL`) ou nova tentativa dentro do prazo da ação.
8. **Áudio nunca passa pelo n8n** (FR-028): o update traz só o `file_id`; o backend baixa o arquivo
   pelo servidor local da API de bots. O workflow não chama `getFile` nem baixa o áudio.
9. **Responder `2xx` às entregas só depois de aceitar a ação** (não é necessário ter executado; a
   confirmação é a prova de execução).
10. **Sem persistência de domínio** e sem logs com texto de aluno (Princípio II e IV).

### Tradução ação → API de bots (referência para a `003` e para o executor simulado dos testes)

| Ação v1 | Chamada ao Telegram | Observações |
|---|---|---|
| `MENSAGEM` | `sendMessage` | `parse_mode=HTML`, `link_preview_options.is_disabled=true`, `reply_markup.inline_keyboard` a partir de `teclado.linhas` (`rotulo`→`text`, `dados`→`callback_data`) |
| `EDICAO` | `editMessageText` | mesmo `parse_mode`; teclado vazio remove os botões |
| `DOCUMENTO` | `sendDocument` (multipart) | bytes do `GET /documentos/…`; `caption` e `parse_mode=HTML` se houver `legenda` |
| `RESPOSTA_CALLBACK` | `answerCallbackQuery` | `callback_query_id` = `callbackId` |
| `MENSAGEM_ETAPA` | `editMessageText` se há `editarMensagemId`, senão `sendMessage` | confirmar com o `message_id` resultante |

## 5. Versionamento

- **Aditivo (permanece na v1)**: campo opcional novo; tipo de ação novo; valor novo em enum de
  `codigoMotivo`. O receptor tolera (item 6).
- **Incompatível (nova versão)**: remover ou mudar o significado de um campo; tornar obrigatório um
  campo existente. Cria `/interno/v2/…`; a v1 continua servida enquanto o orquestrador não migrar
  (FR-003).
- A versão está em **todo** corpo (`versao`). Um corpo recebido com versão desconhecida → `400`
  `VERSAO_NAO_SUPORTADA`.

## 6. Privacidade e logs (FR-029)

Logs, métricas e corpos de erro desta área trazem só identificadores técnicos (`updateId`, `acaoId`,
`loteId`), estados e códigos. Nunca CPF, nome de aluno, texto de relatório, transcrição, áudio, token ou
o payload recebido. O texto das ações existe no banco só enquanto necessário e é zerado após a retenção
(R-11).

## 7. Cenários do contrato (base dos testes, `FR-031`)

| # | Cenário | Resultado esperado |
|---|---|---|
| C1 | texto de professor não vinculado | `MENSAGEM` com o texto de `CODIGO_NECESSARIO`, `acaoId=u<N>-0` |
| C2 | toque em "Aprovar relatório" | `RESPOSTA_CALLBACK` e depois `MENSAGEM` de `CONFIRMAR_APROVACAO` com botões |
| C3 | relatório pronto (texto longo + PDF) | `MENSAGEM`s divididas, `DOCUMENTO`, botões na ordem de hoje |
| C4 | mesmo `update_id` duas vezes, nada confirmado | mesmas ações, mesmos ids |
| C5 | mesmo `update_id` após confirmar tudo | `acoes: []` |
| C6 | mesmo `update_id` após confirmar parte | só as não confirmadas |
| C7 | token ausente / errado / só cookie / `proximo` | `401` / `401` / `401` / aceito |
| C8 | segredo do webhook ausente ou errado | `401` sem corpo |
| C9 | orquestrador fora do ar, depois volta | reentrega com `acaoId` iguais; uma só execução; entrega em ordem |
| C10 | confirmação `FALHOU` + `MENSAGEM_NAO_EDITAVEL` em edição | nova ação `<id>-c` entregue; não repete a edição |
| C11 | prazo vencido sem aceite | `FALHA_DEFINITIVA`, sem dado de aluno no log |
| C12 | `GET /documentos` com id válido / vencido / URL | `200` PDF idêntico ao do portal / `404` / `404` |
| C13 | áudio acima do limite | `MENSAGEM` de `AUDIO_MUITO_GRANDE`, nada baixado |
| C14 | `GET /acoes/{id}` | estado correto; `404` para desconhecido |
