# Research: n8n como único transporte do canal Telegram

**Feature**: `004-n8n-telegram-transport` | Referências: [spec.md](./spec.md), [plan.md](./plan.md), contrato da 002
([canal-telegram-interno.md](../002-api-interna-canal-telegram/contracts/canal-telegram-interno.md), nome em
inglês no OpenAPI e no esquema), constituição v2.1.0.

Nenhum item ficou como "NEEDS CLARIFICATION". Os pontos que dependem de comportamento do n8n 2.x que **não pude
confirmar só lendo a documentação** estão como *spikes* (S-1 a S-6): são as primeiras tarefas da fatia S0 e cada
um diz o que muda no plano se der diferente. As fontes consultadas em 2026-10-15 foram a página de releases do
n8n no GitHub, "n8n 2.0 breaking changes" e "CLI commands" de docs.n8n.io.

## R-01. Versão e origem da imagem

- **Decisão**: `docker.n8n.io/n8nio/n8n:2.43.6` (linha 2.x, última estável em 2026-10-15), fixada no `FROM` de
  `infra/n8n/Dockerfile` e repetida só nesse arquivo. Nunca `latest`, `stable`, `next` nem tag major (`2`).
  Atualização por PR, seguindo uma seção nova "Versão do n8n" de `infra/README.md` (espelho da seção da API de
  bots): ler as notas de versão, subir em ambiente local, rodar `tests n8n`, só então trocar a tag.
- **Por quê**: restrição de contêiner da constituição. A linha 2.x traz o `publish:workflow`, mantém SQLite com
  driver em pool e vem com *task runners* internos para o nó `Code`. A 1.x ainda recebe correções
  (`1.123.x`), mas a 2.0 mudou padrões de segurança relevantes (R-03) e começar na 1.x só adiaria a migração.
- **Verificação**: S-1 confirma que a tag existe em `docker.n8n.io`, registra o *digest* no `research.md` e anota
  usuário, versão do Node e utilitários disponíveis (busybox `wget`).
- **Alternativas**: `n8nio/n8n` do Docker Hub (mesma imagem; o registro `docker.n8n.io` é o oficial citado na
  documentação); fixar por *digest* sozinho (ilegível em revisão — o digest vai como comentário ao lado da tag);
  `latest` (proibido).

## R-02. Rede, exposição e o papel do Caddy

- **Decisão**: serviço `n8n` na rede `internal`, com `expose: ["5678"]` e **sem `ports`** em qualquer camada do
  compose, exceto o editor em `127.0.0.1:5678` no `compose.dev.yaml` (espelho de como o `web` e o banco local são
  publicados: só em loopback). Nenhuma rota no Caddyfile para o n8n; entra apenas um comentário que o diz.
- **Por quê**: o `telegram-bot-api` roda em `--local` (R-05 da 001): é ele, de dentro da rede, que chama o
  webhook. O endereço registrado passa a ser `http://n8n:5678/webhook/telegram-update`. O Telegram na nuvem nunca
  fala com o nosso domínio, então uma rota pública não teria chamador legítimo, e o corpo do webhook do n8n (que
  repassa o segredo do Telegram e executa ações no chat) não deve ficar na internet (Princípio IV).
- **Discordância com o pedido**: o texto original diz que o Caddy "encaminha o webhook do Telegram ao n8n".
  Isso só faria sentido com a API de bots na nuvem, o que contradiz FR-005 e o limite de 50 MB. Está sinalizado
  no fim do `plan.md`; se for mantida a intenção do pedido, muda este item e o Caddyfile.
- **Teste**: `ComposeGuardTest` (nenhum `ports` no `n8n`, exceto loopback no `dev`); `N8nExposureIT` bate no host
  público do `web` com `/webhook/telegram-update` e `/healthz` e espera a resposta do SPA/404, nunca a do n8n.
- **Alternativas**: rota `/telegram-webhook/*` no Caddy com segredo no caminho (rejeitada, acima); publicar a
  porta do n8n (proibido).

## R-03. Segredos e credenciais: `$env`, sem credenciais do n8n

- **Decisão**: os workflows leem segredos e endereços por expressões `{{ $env.NAME }}` nos campos dos nós
  `HTTP Request` e `If`, com `N8N_BLOCK_ENV_ACCESS_IN_NODE=false`. **Nenhum nó usa `credentials`**; logo, nenhum
  JSON tem ID de credencial. Lista fechada de variáveis permitidas (o guardião reprova qualquer outra e reprova
  `$env`, `process.env` e `require` dentro de nó `Code`):

  | Variável | Uso | Segredo |
  |---|---|---|
  | `BACKEND_INTERNAL_URL` | base das chamadas ao backend (`http://backend:8080`) | não |
  | `TELEGRAM_API_URL` | base do servidor local da API de bots (`http://telegram-bot-api:8081`) | não |
  | `TELEGRAM_BOT_TOKEN` | segmento `/bot<token>/` das chamadas à API de bots | **sim** |
  | `BACKEND_SERVICE_TOKEN` | cabeçalho `X-N8N-Service-Token` | **sim** |
  | `CHANNEL_DELIVERY_TOKEN` | confere o cabeçalho `X-Channel-Delivery-Token` das entregas | **sim** |

  `N8N_ENCRYPTION_KEY` é lida pelo próprio n8n (FR-004), não pelos workflows.
- **Por quê**: o n8n 2.0 passou `N8N_BLOCK_ENV_ACCESS_IN_NODE` para `true`, o que impede `$env`; reabrir é o
  caminho documentado. Credenciais do n8n exigiriam importá-las do ambiente na subida e referenciá-las nos
  nós por **ID** (ou por nome, comportamento que não está documentado e depende do spike S-3): isso é estado
  extra, contradiz "sem ID de credencial nos JSON" e dificulta "recriar do zero". Com `$env` não existe
  credencial para provisionar, exportar ou vazar.
- **Risco aceito**: com o bloqueio aberto, qualquer nó `Code` poderia ler o ambiente do contêiner. Mitigações:
  o contêiner só recebe as 5 variáveis acima mais as de operação do n8n; o guardião proíbe `$env` em `Code`;
  edição em produção é impossível (editor desligado, importação do Git a cada subida); `NODES_EXCLUDE` retira
  `executeCommand`, `readWriteFile`, `ssh`, `ftp` e os nós de banco (defesa em profundidade, S-6 confirma que não
  quebra o carregamento).
- **Variáveis do compose para o n8n**: `BACKEND_SERVICE_TOKEN: ${TELEGRAM_CHANNEL_NEXT_TOKEN:-${TELEGRAM_CHANNEL_CURRENT_TOKEN}}`
  (R-09), `CHANNEL_DELIVERY_TOKEN: ${TELEGRAM_CHANNEL_ORCHESTRATOR_TOKEN}`, `TELEGRAM_BOT_TOKEN` e
  `N8N_ENCRYPTION_KEY: ${N8N_ENCRYPTION_KEY}` (nova, obrigatória, gerada como as demais).
- **Alternativas**: credenciais do tipo *Header Auth* provisionadas por `n8n import:credentials` a partir de um
  JSON gerado no boot (estado e ID de credencial); *external secrets* do n8n (recurso de licença paga);
  segredo no caminho do webhook (a URL pode vazar em log do cliente).

## R-04. Importação dos workflows na subida e prevalência do repositório

- **Decisão**: `infra/n8n/entrypoint.sh` (POSIX `sh`, executado como `node`) faz, antes de iniciar o n8n:
  1. `n8n import:workflow --separate --input=/opt/workflows` (substitui por ID os que já existem);
  2. para cada arquivo, `n8n publish:workflow --id=<id do JSON>` (o `update:workflow --active` está obsoleto e o
     `publish:workflow` não aceita `--all`, então o *shell* itera sobre os `id` lidos pelos JSON com
     `node -e`);
  3. `exec n8n start`.
  Os workflows têm **`id` fixo e legível** no JSON (ex.: `telegram-inbound`) — é ID de workflow, não de
  credencial. O n8n 2.x só aplica mudanças feitas pela CLI após a reinicialização, e é por isso que tudo isso
  acontece **antes** do `start`.
- **Por quê**: FR-008 e a clarificação de 2026-10-15 ("sempre substituem"). Edição manual na instância some na
  subida seguinte e `n8n.sh check` a flagra entre duas subidas.
- **Workflow removido do repositório** continuaria na instância (não há `delete:workflow` documentado):
  o `entrypoint.sh` o despublica (`unpublish:workflow`) e registra aviso em português; o `check` reprova enquanto
  existir, e `n8n.sh reset` apaga o volume `n8n-data` (procedimento documentado). Nos testes o volume é efêmero.
- **Volume**: `n8n-data` em `/home/node/.n8n` nos três ambientes (mesma definição); recriar do zero =
  `local.sh clean`/remover o volume, e o resultado é o mesmo conjunto ativo (US1, cenário 2).
- **Spikes**: S-2 confirma que a importação funciona em banco recém-criado sem passar pelo assistente de
  *setup* do dono (se exigir, usar `--userId`/`--projectId` obtidos de `n8n` ou provisionar o dono por
  variáveis de ambiente da 2.x) e que o `publish:workflow` por ID funciona offline.
- **Alternativas**: montar a pasta do repositório (R-01 do Complexity Tracking); API REST pública do n8n para
  publicar (exige chave de API e a API pública desligada, R-13); `--activeState=fromJson` (só funciona em
  *multi-main*).

## R-05. Inventário dos workflows e guardião

- **Decisão**: quatro workflows (descritos em [contracts/n8n-workflows.md](./contracts/n8n-workflows.md)):

  | `id` / nome | Gatilho | Papel |
  |---|---|---|
  | `telegram-inbound` | `POST /webhook/telegram-update` | **único** receptor de updates do Telegram; repassa ao backend e executa as ações |
  | `channel-delivery` | `POST /webhook/channel-delivery` | confere o token de entrega, executa o lote proativo |
  | `execute-actions` | sub-workflow (*Execute Workflow Trigger*) | percorre as ações em ordem, executa, classifica e confirma |
  | `transport-health` | `GET /webhook/transport-health/live` e `/deep` | saúde local e saúde ponta a ponta |

- **Guardião (`WorkflowGuardTest`, offline, roda em `tests-backend`)** reprova, lendo `infra/n8n/workflows/*.json`:
  - mais de um nó `Webhook` com o caminho `telegram-update` ou algum receptor de update do Telegram em outro
    workflow (inclui o nó `Telegram Trigger`);
  - nó fora da **lista permitida**: `webhook`, `respondToWebhook`, `httpRequest`, `code`, `if`, `switch`,
    `splitInBatches`, `set`, `noOp`, `executeWorkflow`, `executeWorkflowTrigger` (e `stickyNote` proibido por
    carregar texto livre);
  - qualquer chave `credentials`, ID de credencial, token, chave de API, CPF, URL com segredo no caminho ou
    literal parecido com segredo (entropia/padrões `\d{6,}:[\w-]{30,}`, `gsk_`, `sk-`…);
  - `httpRequest` cujo `url` não comece por `{{ $env.BACKEND_INTERNAL_URL }}` ou `{{ $env.TELEGRAM_API_URL }}`
    (destino diferente do backend e do servidor de bots);
  - chamada a `/getFile` ou a `/file/bot` (áudio nunca passa pelo n8n);
  - `If`/`Switch` cuja expressão leia algo além de `type` da ação, `outcome`, códigos HTTP, `state` da ação e o
    cabeçalho do token de entrega (sem ramificação por conteúdo da conversa);
  - `Code` com `$env`, `process`, `require`, `import`, `fetch`, `eval`, `Function(` ou literal em português;
  - texto ao professor: o JSON não pode ter literal com espaço e letra acentuada ou palavra do glossário (o
    guardião de idioma já cobre; este teste confere o que o n8n **não** deve ter).
- **Por quê**: FR-009 a FR-011 e SC-009 pedem verificação a cada build; um teste Java offline não precisa de
  contêiner nem de instância e roda no `tests-backend` do CI.
- **Alternativa**: validar só pelo teste de contrato do n8n em contêiner (rejeitada: não roda no `tests-backend`,
  que é o job obrigatório do merge).

## R-06. Como o workflow executa as ações (fidelidade ao contrato 002 §4)

- **Fluxo de `telegram-inbound`**: `Webhook (rawBody, responseMode=responseNode)` → `HTTP Request` ao backend
  (`POST {BACKEND_INTERNAL_URL}/internal/v1/channel/telegram/update`, corpo **cru** do webhook, cabeçalhos
  `X-N8N-Service-Token` e `X-Telegram-Bot-Api-Secret-Token` repassado do cabeçalho recebido, `neverError`,
  resposta completa) → `If status == 200` → `Execute Workflow (execute-actions)` com `actions` →
  `Respond to Webhook 200`. Qualquer outro status (inclui `401`, `5xx`, conexão recusada) → `Respond to Webhook
  503` **sem corpo**: o Telegram reenvia (FR-015), o n8n não escreve texto algum.
- **Fluxo de `execute-actions`** (por ação, em ordem, `splitInBatches` de 1):
  1. `GET /actions/{actionId}`; só `PENDING` e `DELIVERED` executam; `EXECUTED`, `FAILED`, `PERMANENTLY_FAILED`, `DISCARDED` e `404` → pula (decisão do backend, Princípios II e IV); erro de conexão ou `5xx` → aborta a lista
     e devolve falha de transporte (o chamador responde `503`, e o reenvio retoma);
  2. `Switch` por `type`: `MESSAGE`, `EDIT`, `DOCUMENT`, `CALLBACK_REPLY`, `STEP_MESSAGE`, *fallback* →
     `ACTION_NOT_SUPPORTED` sem chamada externa;
  3. `Code` puro monta o corpo (teclado `rows` → `inline_keyboard`, `parse_mode=HTML`,
     `link_preview_options.is_disabled=true`) conforme a tabela "ação v1 → API de bots" do contrato;
  4. `HTTP Request` à API de bots com `neverError`, resposta completa, sem seguir redirecionamento;
  5. `Code` puro **classifica** (a mesma tabela do `SimulatedActionsExecutorTest.classify`, que é a
     especificação executável: "message is not modified" → `EXECUTED`; edição inexistente/antiga →
     `MESSAGE_NOT_EDITABLE`; `403`/"chat not found" → `CHAT_UNAVAILABLE`; `429` → `RATE_LIMITED`; resto →
     `TELEGRAM_ERROR`; falha de conexão com a API de bots → `TELEGRAM_ERROR`);
  6. `POST /confirmations` com **uma** confirmação (`messageId` obrigatório em `STEP_MESSAGE` executada), com até
     3 tentativas de 1 s: se mesmo assim falhar, **não** derruba a resposta (a mensagem já foi entregue ao
     professor; deixar o Telegram reenviar causaria duplicidade) e a ação expira como `NO_CONFIRMATION` na regra
     da 002.
  `DOCUMENT`: um único `GET /documents/{documentId}` (formato de resposta arquivo), `404`/`5xx`/erro de conexão →
  `DOCUMENT_UNAVAILABLE` sem nova tentativa (item 7 do contrato); com `200`, `sendDocument` multipart com
  `caption`/`parse_mode`. O endereço do documento é sempre `{BACKEND_INTERNAL_URL}/…/documents/{documentId}`:
  nunca vem de outro campo.
- **`CALLBACK_REPLY` sempre executa** mesmo que a ação anterior tenha falhado, porque a lista nunca é
  interrompida por falha de execução (só por falha de **transporte com o backend**).
- **Repasse do update**: o corpo vai como veio (`rawBody`), então chat ids grandes e a ordem das chaves não são
  reescritos por um *parse/stringify* (FR-013). S-4 confirma.
- **Sem estado**: nada é lido ou gravado no n8n entre execuções; o único dado entre nós é o item em memória.
- **Spikes**: S-4 (corpo cru e `Respond to Webhook`), S-5 (`neverError`, `multipart`, binário).
- **Alternativas**: confirmar o lote no fim (Complexity Tracking); `Telegram` node do n8n (não aceita endereço
  de servidor local nem expõe o erro bruto que a classificação precisa); `Wait`/retry de reenvio no Telegram
  (proibido: o n8n não decide reenvios, FR-018).

## R-07. Aceite da entrega proativa: só depois de executar e confirmar

- **Decisão**: `channel-delivery` responde `2xx` ao backend **depois** de executar e confirmar o lote, com `401`
  sem corpo se o token não conferir (nada é executado) e `503` se o backend estiver inacessível durante a
  execução. O compose configura `TELEGRAM_CHANNEL_RESPONSE_TIMEOUT_MS=30000` no backend (a trava
  `lease-seconds=60 > conexão 3 s + resposta 30 s` do `TelegramChannelConfig` continua verdadeira).
- **Por quê**: a spec deixou isto para o plano (Assumptions). O backend da 002 só reentrega ação **recusada**
  (`PENDING`); ação aceita (`DELIVERED`) e nunca confirmada fica esperando e expira como `NO_CONFIRMATION`.
  Logo, aceitar de imediato perderia o resto da lista numa queda do n8n. Aceitando só ao final, a queda corta a
  conexão, o backend vê recusa, reentrega os **mesmos** `actionId`, e a consulta `GET /actions/{id}` antes de cada
  ação pula as já confirmadas — exatamente o cenário do FR-028(b). Nenhuma mudança no backend nem no contrato
  (item 9 do contrato: "responder 2xx só depois de aceitar" — executar antes é um aceite mais forte).
- **Risco residual**: se a execução de um lote passar de 30 s, o backend considera recusa, reenvia 30 s depois e
  as duas execuções podem se sobrepor; a consulta por ação reduz a janela a uma ação em voo. Em condições
  normais um lote leva segundos (SC-003). `N8nResilienceIT` inclui o caso de lote lento; o limite de 100 ações
  por lote da 002 e o PDF único por lista mantêm o pior caso abaixo de 30 s.
- **Alternativa rejeitada**: aceitar de imediato e fazer o backend reentregar `DELIVERED` sem confirmação — é
  mudança aditiva na máquina de estados da 002, que a spec só admite "se o plano descobrir uma lacuna", e aqui
  há solução sem ela.

## R-08. Troca, reversão e verificação do webhook (`telegram-webhook.sh`)

- **Decisão** (contrato de linha de comando em [contracts/transport-scripts.md](./contracts/transport-scripts.md)):
  - **Estado da troca = três chaves de `infra/.env`**, nenhuma é segredo: `TELEGRAM_CHANNEL_DELIVERY`
    (`DIRECT`|`ORCHESTRATOR`), `TELEGRAM_WEBHOOK_URL` e `TELEGRAM_TRANSPORT` (`DIRECT`|`N8N`, só para o
    `status` e o `apply` saberem o alvo sem deduzir). O compose passa `TELEGRAM_WEBHOOK_URL` com
    `${TELEGRAM_WEBHOOK_URL:-http://backend:8080/telegram/webhook}` (hoje está fixo) e fixa
    `TELEGRAM_CHANNEL_ORCHESTRATOR_URL=http://n8n:5678/webhook/channel-delivery` no backend.
  - **`switch`**: pré-condições (n8n `healthy`, `transport-health/deep` com `backend` e `botApi` em `ok`,
    segredo do webhook válido, tokens preenchidos e distintos) → grava o estado anterior em
    `infra/.transport-state` → ajusta as três chaves → recria **só** o `backend` (mesma imagem; reinicia o
    registro automático do webhook para a URL do n8n e liga a entrega por orquestrador) → `setWebhook` explícito
    (idempotente, mesmo `secret_token`, `allowed_updates=["message","callback_query"]`, `max_connections=10`) →
    `verify`. Se o `verify` reprovar, **restaura sozinho** o estado anterior e sai com código `3`.
  - **`rollback`**: restaura as três chaves, recria o `backend`, `setWebhook` para `http://backend:8080/telegram/webhook`,
    `verify`. É idempotente.
  - **`verify`** (somente leitura, exceto a sonda): (1) `getWebhookInfo`: `url` esperada, sem `last_error_message`
    recente; (2) `GET transport-health/deep` → `backend: ok` (token de serviço aceito) e `botApi: ok` (`getMe` com o
    token do bot); (3) **sonda de segredo**: `POST` ao webhook com segredo **errado** deve dar status `>= 400`
    e com o segredo **do `.env`** + update de tipo não suportado deve dar `200` (prova que o segredo registrado
    é o que o backend conhece, sem efeito colateral); (4) entrega proativa: `docker compose exec backend` não é
    necessário — basta o `status` do backend ler `TELEGRAM_CHANNEL_DELIVERY` no ambiente do contêiner
    (`docker compose exec -T backend printenv`, sem imprimir segredos); (5) opcional `--chat-id <id>`: **ida e
    volta real**: injeta um update de `ajuda` daquele chat e confere que a resposta do backend chegou (texto vindo
    do catálogo do backend, nunca do n8n).
  - **Validação do segredo do webhook**: `^[A-Za-z0-9_-]{1,256}$` (regra do Telegram); o script recusa antes de
    qualquer chamada e avisa se tiver menos de 32 caracteres.
  - **Nunca dois caminhos para a mesma mensagem**: as duas rotas do backend (`/telegram/webhook` antigo e
    `/internal/…/update`) só respondem a quem chamar; o único registro de webhook ativo é o do Telegram, e o
    estado de entrega proativa é exclusivo (`TelegramChannelConfig` exige exatamente uma publicação). Update em
    voo durante a troca é deduplicado por `update_id` no backend (spec, Edge Cases).
- **Execução HTTP**: todas as chamadas saem de **dentro do contêiner do n8n** com `ops/probe.mjs`
  (`docker compose exec -T n8n node /opt/ops/probe.mjs …`), com segredos passados por `-e`, nunca na linha de
  comando; assim o host só precisa de Docker (constituição). O token do bot vai no caminho `/bot<token>/` apenas
  dentro do contêiner e nunca é impresso.
- **Backend não é reimplantado** (FR-027): a imagem é a mesma; muda a configuração e o contêiner é recriado.
- **Alternativas**: o backend registrar o webhook sozinho em modo "externo" (exigiria código novo no backend);
  `deleteWebhook` + polling (muda a arquitetura); runbook manual (não ensaiável).

## R-09. Rotação do token de serviço com os dois tokens simultâneos

- **Decisão**: o n8n usa `BACKEND_SERVICE_TOKEN = NEXT se existir, senão CURRENT` (interpolação aninhada do
  compose). `rotate-service-token.sh`:
  - `start`: gera token novo (`ops/generate-token.mjs`, 32 bytes, base64url), grava em
    `TELEGRAM_CHANNEL_NEXT_TOKEN`, recria o `backend` (aceita CURRENT e NEXT), espera `healthy`, recria o `n8n`
    (passa a enviar NEXT), espera `healthy` e confere: NEXT aceito, CURRENT ainda aceito, `transport-health/deep`
    ok.
  - `finish`: `CURRENT ← NEXT`, `NEXT` vazio, recria o `backend` e confere: novo aceito, **antigo recusado**
    (`401` sem corpo). O n8n **não** reinicia (o valor que ele envia não mudou).
  - `abort`: esvazia `NEXT` e recria `backend` e `n8n` (volta ao CURRENT). `status` mostra em qual passo está,
    sem imprimir token.
- **Por quê**: segue o procedimento do contrato 002 §1 sem janela de recusa e com a ordem que impede o n8n de
  usar um token que o backend ainda não conhece. O reinício do n8n respeita o limite de 60 s (FR-003).
- **Verificação** sem rota nova: `GET /actions/u0-0` com o token sob teste — `404` = autenticado,
  `401` = recusado (o contrato diz que ambos vêm sem corpo).
- **Token de entrega** (`TELEGRAM_CHANNEL_ORCHESTRATOR_TOKEN`, sentido backend → n8n) é um segredo só; sua
  troca é um reinício coordenado dos dois contêineres, seguro porque o lote recusado é reenviado. Documentado no
  `quickstart`, fora do escopo dos dois tokens simultâneos (que é da 002 e só cobre o sentido n8n → backend).

## R-10. Healthcheck e política de reinício

- **Decisão** (ajustada na Fase 4: o `healthcheck` exige os **três** receptores registrados, por `ops/healthcheck.sh`,
  que consulta `transport-health/live`, `telegram-inbound/live` e `channel-delivery/live`; antes, o contêiner podia ficar
  `healthy` com o webhook do Telegram ainda sem registro e responder `404` a um update logo após o reinício):
  - `healthcheck` do contêiner = **vivacidade local**:
    `wget -q -O - http://127.0.0.1:5678/webhook/transport-health/live` (responde `200` só se o `transport-health`
    estiver publicado, o que prova que o `publish:workflow` rodou e o n8n subiu), `interval: 10s`, `timeout: 5s`,
    `retries: 6`, `start_period: 60s` (spike S-6: ≈ 46 s com 4 workflows, F-2). Sem chamar backend nem API de bots, para que a queda do backend não derrube
    a saúde do n8n em cascata.
  - **Saúde profunda** (`/deep`): backend aceita o token e a API de bots aceita o token do bot. Consultada por
    `n8n.sh health --deep` e pelo `verify`, não pelo Docker.
  - `depends_on`: `n8n` espera `backend` `service_healthy` e `telegram-bot-api` `service_started`; o `backend`
    **não** depende do `n8n` (a outbox reentrega). `web` não muda.
  - **Reinício**: `restart: unless-stopped` em `compose.prod.yaml` (como os demais). `stop_grace_period: 30s` e `N8N_GRACEFUL_SHUTDOWN_TIMEOUT=5`: a porta de entrada drena as requisições em curso antes de parar o n8n (R-13a). Uma única réplica.
  - **Orçamento de 60 s** (FR-003): `entrypoint.sh` (importar 4 + publicar 4) + partida do n8n. **Medido no S-6**:
    importação ≈ 6 s, ≈ 5 s por `publish:workflow` e servidor ≈ 20 s ⇒ ≈ 46 s com 4 workflows (a frio, 39 s); passa
    da meta de 40 s e cabe nos 60 s. **Medido na Fase 4 (T065):** não publicar o sub-workflow `execute-actions` **não funciona** (a chamada do
    `Execute Workflow` falha com `500`); a alavanca que funciona é rodar as quatro `publish:workflow` **em paralelo**
    no `entrypoint.sh`: partida a frio 26 s (era 33 a 44 s) e reinício 20 a 21 s (era 28 a 41 s, mais 9 s depois
    de queda com recuperação de execuções); sem erro de bloqueio no SQLite. `NODES_INCLUDE` não ajuda (o custo é a CLI, não os nós).
  - `local.sh` passa a esperar também o `n8n` `healthy` em `wait_until_healthy`; `deploy.sh` já espera todos.
- **Por quê**: SC-006 e FR-006. Docker não reinicia contêiner `unhealthy` por conta própria; o reinício é por
  saída do processo, e o n8n sai se o `entrypoint.sh` falhar (importação inválida derruba a subida em vez de
  ficar de pé com fluxo antigo).
- **Alternativa**: saúde profunda no `healthcheck` (rejeitada: acopla o n8n ao backend e causa falso
  `unhealthy` durante a implantação do backend).

## R-11. Estratégia de testes (três camadas, reaproveitando a suíte da 001/002)

- **Camada 1 — guardiões estáticos** (`tests-backend`, offline): `WorkflowGuardTest` (R-05) e
  `ComposeGuardTest`, que lê `infra/compose*.yaml` e reprova: imagem sem tag, `latest`/`stable`/`next`/tag major
  (exceto serviços construídos localmente), `n8n` com `ports` (exceto loopback no `dev`), `n8n` fora de `internal`,
  `n8n` sem `restart` no `prod`, `N8N_DISABLE_UI` diferente de `true` no `prod`, variável secreta com valor
  literal. Ambos entram no guardião de idioma (arquivos novos YAML/JSON/shell).
- **Camada 2 — contrato dos workflows pelo n8n real** (`tests-n8n`, `-Pn8n`): o JVM do teste faz de **backend**
  (WireMock com os esquemas v1 validando cada requisição do n8n) e de **API de bots** (WireMock). Verifica, uma
  vez por obrigação do contrato 002 §4: autenticação por cabeçalho; repasse do corpo e do segredo sem alteração;
  ordem; continuidade após falha; classificação (as 6 situações + "not modified"); `ACTION_NOT_SUPPORTED` e campo
  desconhecido; `DOCUMENT_UNAVAILABLE` com **um** `GET`; `CALLBACK_REPLY` mesmo com falha anterior; `503` ao
  Telegram com backend fora; `401` sem execução na entrega com token errado; confirmação de `messageId` em
  etapa. Classes: `N8nInboundWorkflowIT`, `N8nDeliveryWorkflowIT`, `N8nFailureHandlingIT`.
- **Camada 3 — paridade pelo n8n**: o JVM do teste sobe o **backend de verdade** (Spring + Postgres de teste) com
  `server.port` fixo; a API de bots simulada é o WireMock já existente (`API_BOTS`) em porta fixa; o `n8n-test`
  recebe os updates por HTTP. `TelegramConversationBase.sendUpdate` ganha o modo `TRANSPORT=n8n`: `POST` ao
  webhook do n8n com o segredo do Telegram; a resposta só volta quando o n8n terminou de executar todas as ações
  (R-06), então as asserções existentes (`calls`, `sentTexts`, `buttons`…) **não mudam**. Em
  `OrchestratorDeliveryBase`, `orchestrator-url` aponta para o n8n real e `drain()` passa a esperar as
  confirmações no banco em vez de rodar o executor em memória. Roda **toda** a suíte de conversa e os roteiros de
  paridade.
- **Idempotência ponta a ponta** (FR-028, SC-011): (a) `-Dtelegram.n8n.duplicates=true` faz `sendUpdate` entregar
  cada update duas vezes e `drain()` reentregar cada lote, e compara a transcrição final com a de uma execução
  sem duplicidade (`N8nIdempotencyIT` guarda as duas e as compara); (b) `N8nResilienceIT` derruba o n8n no meio da
  lista (WireMock atrasa a 2ª chamada à API de bots; `docker kill`; `docker start`; espera `healthy`), reentrega
  e confere que as ações restantes chegam uma vez e as feitas não repetem — para update (Telegram reenvia) e
  para lote proativo (backend reentrega).
- **Privacidade** (FR-031, SC-005): `N8nPrivacyIT`, depois da suíte, varre `docker logs n8n-test`, a exportação
  dos workflows da instância e as execuções salvas (REST do n8n, com o dono do editor criado no teste) atrás de
  tokens, nomes de aluno, textos de relatório e dos *sentinelas* usados nos testes; espera zero ocorrências e zero
  execuções salvas.
- **Latência** (SC-003): `N8nLatencyIT` envia 100 updates e mede, pelo registro de horário do WireMock, a diferença
  entre a chegada ao n8n e a primeira chamada à API de bots; exige p95 ≤ 2 s (medida em máquina de CI, não é
  garantia de produção).
- **Camada 4 — ensaio da troca** (`tests-rehearsal`, S3): pilha reduzida (`postgres`, `backend` real com IA
  simulada, `n8n`, `telegram-simulator`) e o próprio `telegram-webhook.sh`; ver R-12.
- **Execução**: `sh infra/scripts/local.sh tests n8n` (e `all` passa a incluir); job `n8n` no CI depois de
  `backend` e `frontend`. Os testes das camadas 2 e 3 ficam marcados `@Tag("n8n")` e **fora** do `verify`
  padrão (`-Pn8n`), porque exigem os contêineres.
- **Por quê**: SC-001/SC-002 pedem exatamente as mesmas expectativas pelo n8n; reaproveitar a base evita um
  segundo conjunto de cenários que divergiria do primeiro.

## R-12. Ensaio de troca/reversão e simulador de Telegram

- **Decisão**: `infra/telegram-simulator/server.mjs` (Node, sem dependências, `node:24-alpine`) implementa o
  mínimo da API de bots **e** do comportamento de entrega do Telegram: `setWebhook` (guarda `url`,
  `secret_token`, `allowed_updates`), `getWebhookInfo` (inclui `pending_update_count` e `last_error_message`),
  `deleteWebhook`, `getMe`, `setMyCommands`, `sendMessage`, `editMessageText`, `sendDocument`,
  `answerCallbackQuery`, e uma API de controle `/_control/…`: injetar update de um chat, ler a transcrição por chat,
  falhar a próxima chamada, travar a entrega. Entrega o update ao webhook registrado com o cabeçalho de segredo
  e **retém e reenvia** enquanto a resposta não for `2xx` (a semântica do Telegram que a spec cita).
- **Ensaio** (`infra/tests/rehearsal.sh`, em `tests-rehearsal` com `docker:27-cli` e o socket): sobe a pilha,
  injeta tráfego contínuo de usuários **não vinculados** (resposta `CODE_REQUIRED` previsível, sem massa de dados),
  executa `switch` com tráfego, `rollback`, `switch` de novo, e confere por update: **exatamente uma** resposta na
  transcrição, e `getWebhookInfo`/ambiente do backend nos três estados; cenários extras: `switch` com n8n parado
  (recusa sem alterar o webhook), `verify` com segredo errado (reprova), rotação de token com tráfego. A exatidão
  da entrega proativa na troca é provada na camada 3 (duplicidade) porque exige massa de dados e fila.
- **Por quê**: US5 e SC-007 pedem ensaio em ambiente de teste; sem estado de webhook e reenvio o teste não prova
  nada sobre "durante a passagem".
- **Alternativas**: WireMock standalone com cenários (sem retenção/reenvio, sem controle de entrega); testar
  contra um bot real de testes (não roda em CI e depende de segredos); marcar o ensaio como manual (FR-025 exige
  que seja ensaiado antes da produção, e manual deixa de ser prova repetível).

## R-13. Privacidade do n8n: execuções, binário, logs e API pública

- **Decisão (variáveis do n8n)**: `EXECUTIONS_DATA_SAVE_ON_SUCCESS=none`, `EXECUTIONS_DATA_SAVE_ON_ERROR=none`,
  `EXECUTIONS_DATA_SAVE_ON_PROGRESS=false`, `EXECUTIONS_DATA_SAVE_MANUAL_EXECUTIONS=false`;
  **poda acelerada (spike S-5/F-1)**: `EXECUTIONS_DATA_HARD_DELETE_BUFFER=0`,
  `EXECUTIONS_DATA_PRUNE_HARD_DELETE_INTERVAL=1` e `EXECUTIONS_DATA_PRUNE_SOFT_DELETE_INTERVAL=1`, porque `none`
  **não** impede o instantâneo inicial da execução (cabeçalhos com o segredo do webhook e o `body` do update) nem
  o binário do PDF de ficarem no SQLite e no `tmpfs` até a poda (padrão: até ≈ 75 min);
  `N8N_DEFAULT_BINARY_DATA_MODE=filesystem` com `/home/node/.n8n/binaryData` em **`tmpfs`** (no 2.x o modo em
  memória foi removido); `N8N_PUBLIC_API_DISABLED=true`; `N8N_DIAGNOSTICS_ENABLED=false`,
  `N8N_VERSION_NOTIFICATIONS_ENABLED=false`, `N8N_TEMPLATES_ENABLED=false`, `N8N_PERSONALIZATION_ENABLED=false`,
  `N8N_HIRING_BANNER_ENABLED=false`; `N8N_LOG_LEVEL=warn`, `N8N_LOG_OUTPUT=console`; `GENERIC_TIMEZONE` e `TZ`
  `America/Sao_Paulo`.
- **Erro sem vazamento**: os nós `HTTP Request` usam `neverError` e resposta completa, então um `4xx/5xx` vira
  dado e não exceção; a classificação decide. Falha de **conexão** (que levanta exceção e cujo texto pode trazer a
  URL com o token do bot) é capturada com `continueOnFail` e vira `TELEGRAM_ERROR` ou `DOCUMENT_UNAVAILABLE`
  sem reproduzir a mensagem; nenhum nó escreve o corpo em log.
- **Janela residual declarada (medida no spike, F-1)**: com a poda acelerada, o texto do update e o PDF podem
  existir por **até ≈ 2 min** depois da execução: o texto no SQLite do volume `n8n-data` (disco) e o PDF no
  `tmpfs`. Nenhum dos três tokens aparece nesse registro; o segredo do webhook do Telegram aparece nos
  cabeçalhos. Passado esse tempo restam zero linhas e zero arquivos. **Teste:** `N8nPrivacyIT` espera a poda
  (consulta até ≈ 3 min) e lê o SQLite pelo `node:sqlite` do contêiner (a REST não lista execução apagada).
- **ATUALIZAÇÃO (Fase 5, medição): o endurecimento abaixo foi ADOTADO.** A medição mostrou que a poda apaga as
  *linhas*, mas o texto do update, o texto das ações do backend e o segredo do webhook **continuam no arquivo do
  SQLite e no `-wal`** (páginas livres não são zeradas): a "janela de ≈ 2 min" valia só para as linhas, não para o
  disco. Por isso o banco do n8n (`DB_SQLITE_DATABASE=/var/lib/n8n-db/database.sqlite`) fica num `tmpfs`; o volume
  `n8n-data` guarda só a configuração (sem texto de aluno, sem token, conferido por varredura). Custo: o
  banco é refeito a cada reinício (os workflows já são reimportados em toda subida) e o dono do editor local é recriado.
- **Resíduo aceito**: o arquivo `config` do volume `n8n-data` guarda a chave de cifra do próprio n8n (o valor de
  `N8N_ENCRYPTION_KEY`), gravada pelo n8n por conta própria. Ela não protege nada de domínio (não há credencial nem
  dado no n8n) e é o único segredo que o volume contém; nenhum token do bot, de serviço ou de entrega, nem o segredo do
  webhook, vai para o disco (varredura do `N8nPrivacyIT`).
- **Endurecimento (adotado na Fase 5)**: pôr o banco em `tmpfs`, o que tira o texto do disco. Cabe porque os workflows são reimportados em toda subida e a cifra vem
  de `N8N_ENCRYPTION_KEY`; o custo é perder, a cada reinício, o dono do editor do ambiente local e refazer a migração
  do banco em toda partida (a frio, com 4 workflows, a partida mediu 39 s, dentro do orçamento). Mudança de uma
  linha no compose se for pedida.
- **Por quê**: Princípio IV e FR-031: o histórico de execuções do n8n guarda por padrão o conteúdo das
  mensagens; desligá-lo, acelerar a poda e provar com varredura é a forma de garantir "sem texto de aluno" para
  além da janela acima.
- **Spikes**: S-5 e S-6 confirmaram a lista de variáveis do 2.43, **refutaram** que o binário seja apagado ao
  fim da execução (F-1) e mostraram o que `N8N_DISABLE_UI=true` mantém (webhooks e `/healthz` sim; `/rest/*` não,
  só com a UI ligada).

## R-13a. Janelas em que o n8n responde 200 sem repassar o update

- **Achado (ensaio de reinício sob tráfego, 2 a 17 updates sem resposta em ≈ 600 a 1250)**: o Telegram só reenvia o
  update que não recebeu 2xx; qualquer 200 dado pelo n8n sem ter repassado o update ao backend é perda definitiva.
  Duas janelas produziam esse 200:
  1. **Partida**: logo depois de escutar a 5678 (e antes de registrar os webhooks), o n8n responde
     `200 "n8n is starting up. Please wait"` a qualquer requisição, inclusive POST em `/webhook/*` (≈ 20 ms; medido com um
     laço de POST a cada 5 ms durante a partida). O `Webhook` não chegou a rodar e o backend não tem registro do update.
  2. **Parada limpa**: o executor de `Code` encerra antes do servidor; as execuções ficam penduradas até o limite de
     encerramento e as requisições aceitas nesse intervalo recebem 200 ao fim, também sem registro no backend.
- **Decisão**: (a) `ops/gate.mjs` é o processo principal do contêiner: sobe o n8n em `127.0.0.1:5680` e só abre a 5678
  (repasse de bytes TCP, sem ler o conteúdo) depois que os três `*/live` respondem `{"status":"UP"}`; antes disso a
  conexão é recusada e o Telegram reenvia. (b) a mesma porta, na parada (SIGTERM), deixa de aceitar (conexão recusada ou 503), espera as
  requisições em curso terminarem (até 20 s, com o n8n ainda inteiro) e só então manda o n8n parar
  (`stop_grace_period: 30s`, `N8N_GRACEFUL_SHUTDOWN_TIMEOUT=5`). Parar com SIGKILL foi tentado e descartado: derruba
  execuções entre o envio da mensagem e a confirmação ao backend e duplica a resposta (2 em 923 no ensaio de rotação).
- **Alternativas descartadas**: confiar na parada limpa (o executor de `Code` cai primeiro, a janela continua); um proxy
  (Caddy/nginx) na frente (mais um serviço só para isso); resposta 503 nos primeiros ms (não configurável no n8n).
- **Validação**: três reinícios do n8n sob tráfego contínuo (≈ 500 a 600 updates cada) com exatamente uma resposta por
  update; `ops/gate.test.mjs`; `rotation.sh` e `switch.sh` do ensaio.

## R-13b. Update repetido ainda sem conclusão: 503 em vez de 200 vazio (muda a 002)

- **Achado (ensaio de rotação, que reinicia o backend sob tráfego; 1 update sem resposta em 288)**: o backend gravou a linha
  do update, morreu antes de concluir e o Telegram reenviou em menos de 30 s. A resposta era `200` com lista vazia
  ("o primeiro processamento ainda está em curso"); o n8n a executou (nada) e respondeu 200, e o Telegram deu o update por
  entregue. O update some, sem aviso ao professor.
- **Decisão**: o backend responde `503` nesse caso (o n8n já traduz qualquer status diferente de 200 em 503 ao Telegram, que
  reenvia). Passados 30 s sem conclusão, vale o `INTERNAL_ERROR` estável que a 002 já definia. Mudam o
  `TelegramChannelController`, o teste `UpdateIdempotencyIT` e a nota do contrato da 002 (`canal-telegram-interno.md` e o
  `openapi`). Nenhuma lógica nova no n8n.
- **Validação**: `UpdateIdempotencyIT` e o roteiro de rotação do ensaio (zero updates sem resposta).

## R-14. Ordem, concorrência e `max_connections`

- **Decisão**: o n8n não impõe ordem entre updates; segue o comportamento de hoje (até 10 conexões simultâneas
  do servidor de bots) e a ordem **de execução por chat** continua garantida pelo portão de ordem da 002 (para
  lotes proativos) e pela sequência `actionId` dentro de cada resposta. Sem `N8N_CONCURRENCY_PRODUCTION_LIMIT`
  (padrão ilimitado em modo regular); sem modo `queue`.
- **Por quê**: manter o comportamento atual (SC-002); limitar concorrência aumentaria a latência (SC-003) sem
  requisito que a justifique. Se a medição de S-6 mostrar pressão de memória, o limite é a primeira alavanca.

## R-15. Remoção do caminho antigo (última fase, S5)

- **Pré-condições (gate, FR-029)**: (1) suíte completa verde pelo n8n na `main`; (2) **7 dias corridos** de
  produção com os 4 sinais em zero e nenhum relato de diferença; (3) aprovação registrada do dono do produto;
  (4) `evidence/parity.md` já registrada **na `main`** por PR de documentação, conforme data-model.md §5, e verificada pelo `ParityEvidenceGuardTest` que a entrega da S5 traz; (5) rollback do webhook para o backend ainda possível
  até o instante do merge — depois dele, a reversão é reimplantar a versão anterior (spec, Edge Cases).
- **Inventário a confirmar com `get_impact_radius_tool` e `get_affected_flows_tool` no início de S5** (o grafo atual
  só indexa YAML/SQL, então o levantamento de Java usa `Grep` e `ArchUnit`):
  - **Remover**: `TelegramWebhookController` (`/telegram/webhook`), `TelegramSender`, a escolha entre caminhos
    (`TelegramDirectPublicationAdapter`, `TelegramActionPublicationPort` na parte "direta" e a verificação "exatamente
    um caminho ativo" de `TelegramChannelConfig`), o registro de `setWebhook` em `TelegramConfig`, a propriedade
    `reports.telegram.webhook-url` e `TELEGRAM_CHANNEL_DELIVERY`, `TELEGRAM_WEBHOOK_URL` (a variável
    `TELEGRAM_WEBHOOK_SECRET` **fica**: o backend ainda valida o segredo do webhook em `/update`), as exceções de
    `SecurityConfig` para `/telegram/webhook`, `TelegramOutput`/`TelegramUpdateResult.outputs()` se ficarem sem
    uso depois do mapeador de ações, e os testes que só existiam para o caminho antigo.
  - **Manter**: o cliente do servidor local de bots para **baixar áudio** (`getFile`, `TelegramMessagingAdapter`
    reduzido) e `setMyCommands` (rótulos em português vêm do catálogo do backend; o n8n não pode tê-los);
    `ChannelActionMapper` e todo o canal interno; `TELEGRAM_API_URL`, `TELEGRAM_FILES_DIR`, `PENDING_AUDIO_MINUTES`.
  - **Testes**: o modo `webhook` some de `TelegramConversationBase`; o padrão passa a ser o canal interno com o
    executor em memória (`SimulatedActionsExecutorTest`) para os testes rápidos do `tests-backend`, e o `tests-n8n`
    continua exercitando o n8n de verdade. Nenhuma expectativa de texto, botão ou ordem muda.
  - **Infra**: `telegram-webhook.sh` perde `rollback` para o caminho antigo (vira só `status|apply|verify`),
    `TELEGRAM_TRANSPORT` e `TELEGRAM_CHANNEL_DELIVERY` saem do `.env.example`, `local.sh`/`deploy.sh` chamam
    `apply` depois de saudáveis (agora o script é o **único** dono do registro do webhook), `.env.example`
    perde chaves órfãs; `README`s e `CLAUDE.md` deixam de falar de dois caminhos.
- **Guardião de órfãos**: um teste varre `application.yaml`, `.env.example` e `compose*.yaml` atrás de chaves que
  nenhuma classe lê (e classes sem uso) para fechar SC-010.
- **Consequência aceita**: depois do merge, desfazer exige reimplantar a versão anterior (registrado na spec).

## R-16. Idioma, glossário e guardiões

- Identificadores novos em inglês: `telegram-inbound`, `channel-delivery`, `execute-actions`, `transport-health`,
  `telegram-webhook.sh`, `rotate-service-token.sh`, `n8n.sh`, `entrypoint.sh`, `probe.mjs`, `generate-token.mjs`,
  `normalize-workflow.mjs`, `telegram-simulator`, variáveis `BACKEND_INTERNAL_URL`, `BACKEND_SERVICE_TOKEN`,
  `CHANNEL_DELIVERY_TOKEN`, `N8N_ENCRYPTION_KEY`, `TELEGRAM_TRANSPORT`. Termos como `webhook`, `workflow`,
  `probe`, `rehearsal`, `simulator` e `rollback` são ingleses; não entram no glossário pt→en.
- Mensagens dos scripts e logs do `entrypoint.sh` em português com acento; comentários e `CLAUDE.md` em
  português. **Os JSON de workflow não levam texto livre** (sem nota, sem `stickyNote`, sem comentário em `Code`):
  a documentação de cada passo fica em `contracts/n8n-workflows.md` e em `infra/n8n/CLAUDE.md`, de modo que o
  guardião de idioma não encontre prosa portuguesa nos JSON.
- Rodar uma vez o guardião em modo relatório (`GUARD_REPORT=1`) com os arquivos novos antes de fechar S1.

## R-17. Tempo de resposta ao Telegram e retenção de update

- **Decisão**: `telegram-inbound` responde ao Telegram **depois** de executar a lista (R-06). O tempo típico é
  1 a 3 chamadas internas mais 1 a 4 chamadas à API de bots (centenas de milissegundos). Processamento pesado de
  áudio é assíncrono (fila do backend → entrega proativa), então não segura a resposta ao webhook.
- **Por quê**: é o que dá a garantia "queda do n8n = o Telegram reenvia" (FR-015, US6.3), sem estado no n8n.
- **Risco**: se a API de bots ficar lenta, a resposta ao webhook demora e o servidor de bots pode abrir outra
  conexão para o mesmo update; o `GET /actions/{id}` por ação evita repetir o que já foi confirmado, e o
  backend devolve só as ações não confirmadas. Documentado no `quickstart`, com o sinal de "respostas
  duplicadas" da observação de 7 dias (FR-029).

## R-18. Exportação de volta e verificação de divergência

- **Decisão**: `n8n.sh export` executa dentro do contêiner `n8n export:workflow --backup --output=/tmp/export`
  (um arquivo por workflow, formatado), passa cada arquivo pelo `normalize-workflow.mjs` e grava em
  `infra/n8n/workflows/`. O normalizador: ordena chaves, **remove** campos voláteis (`createdAt`, `updatedAt`,
  `versionId`, `activeVersionId`, `shared`, `tags`, `meta.instanceId`, `pinData`, `staticData`), força `id` e
  `name` esperados, e **falha** (sem gravar) se encontrar `credentials`, token, URL com segredo ou literal
  suspeito. `n8n.sh check` exporta a instância, normaliza e compara com a pasta do repositório; reprova a
  diferença, mostrando só nomes de workflow e caminhos de campo (nunca valores).
- **Procedimento** (documentado em `infra/n8n/CLAUDE.md` e `infra/README.md`): (1) `local.sh up` (editor em
  `https://…` de loopback); (2) editar no navegador; (3) `n8n.sh export`; (4) `git diff`; (5) `tests n8n`;
  (6) PR. Em produção nada é editado.
- **Por quê**: FR-008, FR-009 e a constituição ("reproduzível a partir deles").
- **Alternativa**: guardar o JSON como o n8n exporta (cheio de campos voláteis: diffs ruidosos e risco de
  `credentials` escapar).

## Spikes (S0) — fechar antes das tarefas de implementação

Resultados medidos em 2026-10-15 com a imagem real `docker.n8n.io/n8nio/n8n:2.43.6`, em contêineres
descartáveis (pasta de rascunho fora do repositório, removidos ao fim). Os achados que **mudam** uma decisão estão
em "Achados dos spikes" logo abaixo.

| # | Pergunta | Se a resposta for "não" | Resultado |
|---|---|---|---|
| S-1 | A tag `2.43.6` existe em `docker.n8n.io`? Qual o *digest*, o usuário, o Node e há `wget` busybox? | usar a patch mais próxima publicada; sem `wget`, o `healthcheck` passa a `node -e fetch` (já há Node) | **Sim.** *Digest* `sha256:dd580fe1ab6e99e85bc66f2acf8bc8c5e9409fbe61b2e2803c5024403ef52833` (linux/amd64); `n8n --version` = `2.43.6`. Usuário `node` (uid 1000, `HOME=/home/node`). Node **v26.7.0**. `wget` do busybox 1.37.0 em `/usr/bin/wget`; `fetch` existe no `node`. O `ENTRYPOINT` da imagem é `tini -- /docker-entrypoint.sh` (ver F-3) |
| S-2 | `import:workflow --separate` e `publish:workflow --id` funcionam em banco novo, sem dono configurado, antes do `start`? | passar `--userId`/`--projectId`, ou provisionar o dono do editor por variáveis de ambiente; se nenhum servir, publicar pelo REST interno do n8n no `entrypoint` com a UI/REST habilitados só localmente | **Sim, sem plano B.** Em volume vazio e sem dono, `import:workflow --separate --input=<dir>` (≈ 6 s) e `publish:workflow --id=<id>` (≈ 5 s **por chamada**) saem com código 0; o `id` fixo do JSON é preservado (`n8n list:workflow` imprime `id\|nome`); depois do `exec n8n start` os webhooks publicados respondem. `--userId`/`--projectId` não foram necessários |
| S-3 | `$env.X` funciona em campos de `HTTP Request` e `If` com `N8N_BLOCK_ENV_ACCESS_IN_NODE=false`? A mensagem de erro do nó nunca mostra o valor? | cair para credenciais *Header Auth* provisionadas por `import:credentials` (R-03, alternativa), refazendo a regra do guardião | **Sim.** `{{ $env.X }}` funcionou na URL e em cabeçalho do `HTTP Request` e na comparação do `If` (token de entrega certo ⇒ `200`; errado, vazio ou ausente ⇒ `401`). Com conexão recusada o item de erro (inclusive `stack`) traz `connect ECONNREFUSED 127.0.0.1:1`, **sem** o caminho `/bot<token>/`; o log do contêiner teve zero ocorrências dos valores secretos. Não testado: erro de expressão com variável ausente |
| S-4 | O `Webhook` com `rawBody` e o `HTTP Request` com corpo cru repassam **bytes idênticos** ao backend? `Respond to Webhook` devolve `503` sem corpo e `200` vazio? Cabeçalhos chegam em minúsculas? | reconstruir o corpo por `JSON.stringify` do objeto original (aceitável porque o backend lê o JSON), mantendo o teste de equivalência semântica | **Sim.** Corpo de 98 bytes (id de chat `-1001234567890123456789`, espaços, quebra de linha, UTF-8 e `1.0`) chegou **idêntico** ao mock (comparação byte a byte) com `Content-Type: application/json`, `X-N8N-Service-Token` e o segredo do Telegram copiado. Cabeçalhos chegam em minúsculas (`x-telegram-bot-api-secret-token`). `Respond to Webhook` com `noData` devolve `200` e `503` com corpo vazio; backend `401` ⇒ n8n `503`. Ressalvas em F-4 |
| S-5 | `neverError` + resposta completa + `continueOnFail` bastam para nunca haver exceção com URL? `multipart` com o binário do `GET` funciona? O binário some ao fim da execução com `tmpfs`? | encapsular a chamada de documento em sub-workflow próprio; se o binário persistir, limpar no `entrypoint` e na parada | **Parcial.** `neverError` + resposta completa: `400` vira dado (`statusCode` e `body`). Conexão recusada com `onError: continueRegularOutput` vira o item `{error:{message,name,stack}}`, sem token nem URL. `multipart/form-data` com o binário de outro `HTTP Request` funciona (PDF íntegro; `chat_id` negativo grande literal; o nome de arquivo saiu `pdf` porque o mock não enviou `Content-Disposition`: conferir na T044). **O binário NÃO some ao fim da execução**: o PDF ficou no `tmpfs`, por execução, até a poda (F-1) |
| S-6 | Tempo de partida do `entrypoint.sh` ao `healthy` ≤ 40 s? `NODES_EXCLUDE`/`NODES_INCLUDE` não quebram o carregamento? `N8N_DISABLE_UI=true` mantém os webhooks? Há REST utilizável para o teste de privacidade com a UI ligada? | reduzir carga (R-10); abrir mão do `NODES_*` (o guardião é o controle principal); para a varredura de execuções, ler o SQLite pelo `node` da imagem | **Parcial.** Partida: a frio (banco novo, 4 workflows) **39 s**; a quente com 7 workflows 59 a 68 s (importação 6 s, ≈ 5 s por publicação, servidor ≈ 20 s) ⇒ com 4 workflows ≈ 46 s: a meta de 40 s **não é garantida**, o orçamento de 60 s (FR-003) fecha (F-2). `NODES_EXCLUDE` com 4 nós não quebrou o carregamento. `N8N_DISABLE_UI=true`: webhooks e `/healthz` respondem (`200`), `/` e `/rest/*` dão `404`. Com a UI ligada: `/rest/settings` `200`, `POST /rest/owner/setup` `200` e `/rest/executions` `200`, **mas a listagem não mostra execuções apagadas logicamente**; a varredura de privacidade lê o SQLite pelo `node:sqlite` do contêiner (F-1) |

### Achados dos spikes (mudam decisões)

- **F-1 — O histórico "desligado" ainda grava o instantâneo inicial (muda R-13).** Com
  `EXECUTIONS_DATA_SAVE_ON_SUCCESS/_ON_ERROR=none`, cada execução cria no SQLite uma linha em `execution_entity`
  (`status` fica `running`, nunca finaliza) e outra em `execution_data` com o **primeiro item do gatilho**:
  cabeçalhos (**inclui o segredo do webhook do Telegram**) e o `body` já interpretado do update (**inclui o texto
  da mensagem**). A linha é apagada logicamente na hora (`deletedAt`) e só sai de vez, com o binário, na poda
  (`EXECUTIONS_DATA_PRUNE_HARD_DELETE_INTERVAL` = 15 min e `EXECUTIONS_DATA_HARD_DELETE_BUFFER` = 1 h por padrão).
  O PDF do `HTTP Request` fica em `binaryData/workflows/<id>/executions/<n>/` no `tmpfs` até a mesma poda. Os
  tokens do bot e de serviço **não** apareceram no `execution_data`. **Medido** com
  `EXECUTIONS_DATA_HARD_DELETE_BUFFER=0`, `EXECUTIONS_DATA_PRUNE_HARD_DELETE_INTERVAL=1` e
  `EXECUTIONS_DATA_PRUNE_SOFT_DELETE_INTERVAL=1`: aos 45 s ainda havia linhas e arquivos; aos 90 s, zero linhas e
  zero arquivos. **Decisão:** adotar as três variáveis (R-13), de modo que nada persista por mais de ≈ 2 min; a
  janela residual existe e está declarada em R-13 e no `plan.md`.
- **F-2 — Orçamento de partida (muda R-10).** O custo dominante é a CLI: cada `publish:workflow` inicia o
  aplicativo inteiro (≈ 5 s). Com 4 workflows a partida fica ≈ 46 s. `start_period` do `healthcheck` sobe de 40 s
  para **60 s**. Alavanca a medir na T065: não publicar o sub-workflow `execute-actions` (só os três que têm
  gatilho próprio), se o n8n aceitar chamá-lo despublicado.
- **F-3 — `ENTRYPOINT` da imagem.** O original é `tini -- /docker-entrypoint.sh`. O `Dockerfile` da T022 deve
  manter o `tini` como PID 1: `ENTRYPOINT ["tini","--","/opt/entrypoint.sh"]`.
- **F-4 — Repasse do update: ressalvas.** (a) O `Webhook` só entrega o binário `data` quando o `Content-Type` é
  JSON (o Telegram sempre envia assim; com `application/x-www-form-urlencoded` o fluxo cai no `503`).
  (b) Corpo que **não é JSON** é recusado pelo próprio n8n com `422`, antes do workflow: o "corpo ilegível" do
  cenário N17 e da spec passa a significar JSON bem formado de formato inesperado (decidido pelo backend);
  corpo não-JSON nunca vem do Telegram.
- **F-5 — Ruído de log aceito.** A cada partida o n8n registra, em nível `warn`, a lista de avisos de depreciação
  (`N8N_RUNNERS_MODE` interno, etc.) e `Failed to start Python task runner in internal mode` (a imagem não tem
  Python; o nó `Code` em JavaScript funciona). Nenhum desses avisos contém segredo; a varredura de privacidade
  (T068) não deve tratá-los como vazamento.
