# Data Model: n8n como único transporte do canal Telegram

**Feature**: `004-n8n-telegram-transport` | Referências: [spec.md](./spec.md), [plan.md](./plan.md),
[research.md](./research.md)

**Esta feature não cria tabela, coluna, migração nem entidade de domínio.** O backend continua sendo o único a
gravar no banco (Princípio II); o estado das ações vive em `channel_action` (feature 002) e o n8n só o consulta
por `GET /actions/{id}`. O "modelo de dados" aqui é o do que o n8n **configura, carrega e mostra**: inventário de
workflows, configuração, estados da troca, forma da evidência de paridade e matriz de testes.

## 1. Inventário de workflows (`infra/n8n/workflows/`)

| Arquivo / `id` | Nome | Gatilho | Entradas | Saídas | Chama |
|---|---|---|---|---|---|
| `telegram-inbound.json` | `telegram-inbound` | `POST /webhook/telegram-update` (corpo cru) | update bruto; cabeçalho `X-Telegram-Bot-Api-Secret-Token` | `200` vazio (lista executada); `503` sem corpo (falha de transporte com o backend) | backend `POST /update`; `execute-actions` |
| `channel-delivery.json` | `channel-delivery` | `POST /webhook/channel-delivery` | `DeliveryBatch` v1; `X-Channel-Delivery-Token`; `Idempotency-Key` | `200` (lote executado e confirmado); `401` sem corpo (token); `503` (backend inacessível) | `execute-actions` |
| `execute-actions.json` | `execute-actions` | *Execute Workflow Trigger* | lista `actions` v1 | lista de resultados por ação | backend `GET /actions/{id}`, `GET /documents/{id}`, `POST /confirmations`; API de bots |
| `transport-health.json` | `transport-health` | `GET /webhook/transport-health/live` e `/deep` | — | `live`: `200 {"status":"UP"}`; `deep`: `200 {"status":"UP","backend":"ok","botApi":"ok"}` ou `503` com o componente em falha | `deep`: backend `GET /actions/u0-0` (espera `404`, `401` = token inválido) e API de bots `getMe` |

Regras do inventário (verificadas por `WorkflowGuardTest`): exatamente estes quatro arquivos; `id` igual ao nome
do arquivo sem extensão; nenhum `credentials`; um único `Webhook` com `path = telegram-update`; destinos só
`{{ $env.BACKEND_INTERNAL_URL }}` e `{{ $env.TELEGRAM_API_URL }}`; nenhum texto livre.

## 2. Configuração (variáveis de ambiente)

### 2.1 Do contêiner `n8n` (definidas no `compose`; o valor vem de `infra/.env`)

| Variável | Valor / origem | Segredo | Observação |
|---|---|---|---|
| `BACKEND_INTERNAL_URL` | `http://backend:8080` | não | fixo no compose |
| `TELEGRAM_API_URL` | `http://telegram-bot-api:8081` | não | o **mesmo** servidor do backend (FR-005) |
| `TELEGRAM_BOT_TOKEN` | `${TELEGRAM_BOT_TOKEN}` | **sim** | só no caminho `/bot<token>/` |
| `BACKEND_SERVICE_TOKEN` | `${TELEGRAM_CHANNEL_NEXT_TOKEN:-${TELEGRAM_CHANNEL_CURRENT_TOKEN}}` | **sim** | usa o próximo se existir (rotação) |
| `CHANNEL_DELIVERY_TOKEN` | `${TELEGRAM_CHANNEL_ORCHESTRATOR_TOKEN}` | **sim** | confere as entregas do backend |
| `N8N_ENCRYPTION_KEY` | `${N8N_ENCRYPTION_KEY}` | **sim** | **nova**; `openssl rand -hex 32`; obrigatória |
| `N8N_BLOCK_ENV_ACCESS_IN_NODE` | `false` | não | R-03 |
| `N8N_DISABLE_UI` | `true` (base e prod); `false` (dev e testes) | não | FR-002 |
| `N8N_PUBLIC_API_DISABLED` | `true` | não | |
| `EXECUTIONS_DATA_SAVE_ON_SUCCESS` / `_ON_ERROR` | `none` | não | R-13 |
| `EXECUTIONS_DATA_SAVE_ON_PROGRESS` / `_MANUAL_EXECUTIONS` | `false` | não | R-13 |
| `EXECUTIONS_DATA_HARD_DELETE_BUFFER` | `0` | não | R-13 (spike S-5/F-1): apaga de vez sem esperar 1 h |
| `EXECUTIONS_DATA_PRUNE_HARD_DELETE_INTERVAL` / `_SOFT_DELETE_INTERVAL` | `1` / `1` (minutos) | não | R-13: instantâneo e binário somem em ≈ 2 min (padrão: 15 / 60) |
| `N8N_DEFAULT_BINARY_DATA_MODE` | `filesystem` (em `tmpfs`) | não | R-13 |
| `N8N_GRACEFUL_SHUTDOWN_TIMEOUT` | `25` | não | R-10 |
| `N8N_DIAGNOSTICS_ENABLED`, `N8N_VERSION_NOTIFICATIONS_ENABLED`, `N8N_TEMPLATES_ENABLED`, `N8N_PERSONALIZATION_ENABLED`, `N8N_HIRING_BANNER_ENABLED` | `false` | não | sem telemetria nem rede externa |
| `N8N_LOG_LEVEL` / `N8N_LOG_OUTPUT` | `warn` / `console` | não | R-13 |
| `NODES_EXCLUDE` | lista JSON de nós perigosos | não | defesa em profundidade (S-6) |
| `GENERIC_TIMEZONE`, `TZ` | `${SCHOOL_TIMEZONE:-America/Sao_Paulo}` | não | |

### 2.2 Do contêiner `backend` (acréscimos no `compose.yaml`; o código não muda)

| Variável | Valor | Por quê |
|---|---|---|
| `TELEGRAM_WEBHOOK_URL` | `${TELEGRAM_WEBHOOK_URL:-http://backend:8080/telegram/webhook}` (hoje fixo) | o estado da troca vive no `.env` (R-08) |
| `TELEGRAM_CHANNEL_ORCHESTRATOR_URL` | `http://n8n:5678/webhook/channel-delivery` | usada só com `TELEGRAM_CHANNEL_DELIVERY=ORCHESTRATOR` |
| `TELEGRAM_CHANNEL_RESPONSE_TIMEOUT_MS` | `30000` | R-07 (a reserva `LEASE_S=60` continua maior que 3000+30000) |

### 2.3 Chaves novas em `infra/.env` / `.env.example`

| Chave | Obrigatória | Descrição |
|---|---|---|
| `N8N_ENCRYPTION_KEY` | sim (com Telegram habilitado) | cifra da instância do n8n; trocar invalida o que o n8n cifrou (nada de domínio) |
| `TELEGRAM_TRANSPORT` | não (padrão `DIRECT`) | `DIRECT` ou `N8N`; escrita pelo `telegram-webhook.sh`; **não editar à mão** |
| `TELEGRAM_CHANNEL_CURRENT_TOKEN` | sim para o n8n | já existia (002); passa a ser exigida quando `TELEGRAM_TRANSPORT=N8N` |
| `TELEGRAM_CHANNEL_NEXT_TOKEN` | não | já existia (002); usada só durante a rotação |
| `TELEGRAM_CHANNEL_ORCHESTRATOR_TOKEN` | sim para o n8n | já existia (002); segredo da entrega backend → n8n |
| `TELEGRAM_VERIFY_CHAT_ID` | não | chat do mantenedor para a ida e volta real do `verify` |

`local.sh require_env` passa a exigir `N8N_ENCRYPTION_KEY` quando `TELEGRAM_ENABLED=true`, e os três tokens do
canal quando `TELEGRAM_TRANSPORT=N8N`.

## 3. Volumes e processos do contêiner `n8n`

| Caminho / recurso | Tipo | Conteúdo | Retenção |
|---|---|---|---|
| `/home/node/.n8n` | volume `n8n-data` | SQLite do n8n (workflows importados, ajustes, usuário do editor local) e arquivo de configuração | sobrevive a reinício; **recriável do zero** (R-04); nada de domínio |
| `/home/node/.n8n/binaryData` | `tmpfs` | bytes do PDF em trânsito | some na poda (≈ 2 min depois da execução, spike F-1) e ao reiniciar |
| `/opt/workflows` | camada da imagem (somente leitura) | os 4 JSON do repositório | trocam a cada nova imagem |
| `/opt/ops` | camada da imagem | `probe.mjs`, `normalize-workflow.mjs`, `generate-token.mjs` | idem |

## 4. Máquinas de estado

### 4.1 Estado do transporte (por ambiente) — `telegram-webhook.sh`

```text
            switch (pré-condições ok, verify ok)
  DIRECT ───────────────────────────────────────▶ N8N
    ▲  ◀───────────────────────────────────────────  │
    │            rollback (verify ok)                │
    │                                                │
    └─ switch com verify reprovado: restaura o estado anterior (saída 3) ◀──┘
```

| Estado | `TELEGRAM_TRANSPORT` | `TELEGRAM_CHANNEL_DELIVERY` | Webhook registrado | Quem responde ao professor |
|---|---|---|---|---|
| `DIRECT` | `DIRECT` | `DIRECT` | `http://backend:8080/telegram/webhook` | o backend, direto à API de bots |
| `N8N` | `N8N` | `ORCHESTRATOR` | `http://n8n:5678/webhook/telegram-update` | o n8n, executando as ações do backend |

Invariante: os três valores mudam **juntos**; qualquer combinação diferente das duas linhas é "estado
inconsistente", que o `status` mostra e o `verify` reprova (sai com `3`).

Dentro do `switch`, a ordem é: pré-condições → gravar `infra/.transport-state` (valores anteriores, não
segredos) → ajustar `.env` → recriar `backend` → `setWebhook` → `verify`. O `rollback` percorre o espelho.

### 4.2 Rotação do token de serviço — `rotate-service-token.sh`

```text
 IDLE (NEXT vazio) ──start──▶ DUAL (CURRENT e NEXT aceitos; n8n envia NEXT) ──finish──▶ IDLE (CURRENT = antigo NEXT)
                                    │
                                    └──abort──▶ IDLE (NEXT vazio; n8n volta a enviar CURRENT)
```

### 4.3 Execução de uma ação no n8n (sem estado persistido; reflete o contrato 002 §4)

```text
 recebida ─▶ GET /actions/{id}
              ├─ EXECUTED | FAILED | PERMANENTLY_FAILED | DISCARDED | 404 ─▶ pulada (sem chamada, sem confirmação)
              ├─ erro de conexão | 5xx | 401 ───────────────────────────────▶ aborta a lista: falha de transporte (503 ao chamador)
              └─ PENDING | DELIVERED ─▶ executa ─▶ classifica ─▶ POST /confirmations (EXECUTED | FAILED + reasonCode)
```

O n8n só executa o que o backend ainda espera ver executado: o destino de uma ação já decidida (resultado
registrado, vínculo desfeito, prazo vencido) é do backend e o n8n não o desfaz.

**Janela em voo**: entre o sucesso da chamada ao Telegram e a confirmação ao backend existe um intervalo em que
uma queda do n8n deixa a ação `DELIVERED`; no reenvio ela volta a ser executada. É a única repetição possível
(no máximo uma ação por queda); as ações já confirmadas não repetem e as não iniciadas chegam uma vez.

## 5. Evidência de paridade (condição para a fatia S5, FR-029)

Registro único em arquivo versionado **na `main`**: `specs/004-n8n-telegram-transport/evidence/parity.md`. É
criado quando a observação começar e atualizado por PR de documentação (Princípio VIII); a entrega de remoção só
nasce depois de ele estar completo na `main` e **traz um teste que reprova o build** se o arquivo faltar ou
violar as colunas "exigido" abaixo (`ParityEvidenceGuardTest`). **Não** é entidade de banco.

| Campo | Conteúdo |
|---|---|
| `switchedAt` | data/hora (UTC) em que `telegram-webhook.sh switch` terminou em produção |
| `observationDays` | ≥ 7 dias corridos contados de `switchedAt` |
| `suiteRun` | identificador da execução de CI verde de `tests n8n` na `main` (inclui duplicidade e resiliência) |
| `updatesWithoutReply` | contagem no período; exigido `0` |
| `duplicateReplies` | contagem; exigido `0`. Única exceção: uma repetição isolada comprovadamente coincidente com queda **não planejada** do n8n (janela em voo, §4.3), listada com data e `actionId` e aceita por escrito pelo dono do produto |
| `proactiveDefinitiveFailuresFromTransport` | ações `PERMANENTLY_FAILED` por transporte; exigido `0` |
| `lostCallbackConfirmations` | toques sem `CALLBACK_REPLY` executado; exigido `0` |
| `teacherReports` | relatos de diferença visível registrados pelo dono do produto; exigido `nenhum` |
| `approvedBy` / `approvedAt` | dono do produto e data da aprovação registrada |

Fonte dos quatro sinais: logs técnicos do backend e do n8n (só identificadores: `updateId`, `actionId`,
`batchId`, estado e código), consultas à tabela `channel_action` e à métrica de ações `PERMANENTLY_FAILED` já
existente na 002. A coleta passo a passo está em `quickstart.md` V16. Nenhum dado de aluno entra na evidência.

## 6. Matriz de testes (requisito → verificação)

| Requisito | Verificação | Camada |
|---|---|---|
| FR-001, FR-002 | `ComposeGuardTest` (imagem fixada, sem `ports`, rede, `N8N_DISABLE_UI`); `N8nExposureIT` | 1, 3 |
| FR-003, SC-006 | `N8nResilienceIT` (reinício ≤ 60 s, update e lote); `tests-rehearsal` | 3, 4 |
| FR-004, FR-009, SC-005 | `WorkflowGuardTest` (segredos/credenciais); `N8nPrivacyIT` | 1, 3 |
| FR-005 | `WorkflowGuardTest` (destino); contrato dos workflows (URL da API de bots) | 1, 2 |
| FR-006 | `N8nHealthIT` (`live` e `deep`); `healthcheck` do compose | 2 |
| FR-007, FR-008 | `n8n.sh check` no `tests-n8n` (sem divergência; com edição manual reprova); recriação do zero | 3 |
| FR-010, FR-011, SC-009 | `WorkflowGuardTest`; guardião de idioma | 1 |
| FR-012 a FR-016, SC-008 | `N8nInboundWorkflowIT`, `N8nFailureHandlingIT`, paridade pelo n8n | 2, 3 |
| FR-017 a FR-020 | `N8nFailureHandlingIT` (6 códigos, tipo desconhecido, documento, `CALLBACK_REPLY`) | 2 |
| FR-021 a FR-023, SC-006 | `N8nDeliveryWorkflowIT`, `N8nIdempotencyIT`, `N8nResilienceIT` | 2, 3 |
| FR-024 a FR-027, SC-007 | `tests-rehearsal` (`switch`, `rollback`, recusa, segredo errado, tráfego) | 4 |
| FR-028, SC-001, SC-002, SC-011 | suíte inteira com `TRANSPORT=n8n`, mais `duplicates=true` e queda no meio; `ConversationStateCoverageTest` (10 estados, §6.1) | 3 |
| SC-003 | `N8nLatencyIT` (p95 ≤ 2 s) | 3 |
| FR-029, FR-030, SC-010 | PR da S5 com evidência; guardião de órfãos; suíte inteira | S5 |
| FR-031 | `N8nPrivacyIT` | 3 |
| FR-032, FR-033, SC-010 | revisão de PR (lista de arquivos em plan.md) e `quickstart.md` | S4 |
| Rotação do token | `tests-rehearsal` (`start`, `finish`, `abort`, com tráfego) | 4 |

### 6.1 Matriz dos 10 estados da conversa (rastreio da paridade pelo n8n)

Fonte dos estados: `specs/001-fluxo-relatorios/contracts/telegram-conversa.md` §4. A coluna "Teste candidato"
sai da leitura dos nomes das classes da suíte existente e **deve ser confirmada** pela tarefa que anota os
testes (`@ConversationState`); o meta-teste `ConversationStateCoverageTest` reprova o build se algum estado
não tiver pelo menos um teste anotado dentro da execução `n8n-transport`.

| # | Estado derivado | Teste candidato (a confirmar) |
|---|---|---|
| 1 | Não vinculado | `LinkingConversationIT` |
| 2 | Aguardando confirmação de ação (aprovar, cancelar, descartar, sair) | `ReviewConversationIT` |
| 3 | Relatório em foco `AWAITING_ANSWERS` | `LessonRegistrationConversationIT`, `ResumptionConversationIT` |
| 4 | Relatório em foco `AWAITING_REVIEW` | `ReviewConversationIT` |
| 5 | Relatório em processamento (`PROCESSING_IN_PROGRESS`) | `LessonRegistrationConversationIT` |
| 6 | Relatório `PROCESSING_FAILED` (`PROCESSING_FAILURE_OPTIONS`) | `LessonRegistrationConversationIT` |
| 7 | Áudio pendente de confirmação do aluno (inclui `PENDING_AUDIO_EXPIRED`) | `LessonRegistrationConversationIT` |
| 8 | Fluxo `SEMESTER` | `SemesterConversationIT` |
| 9 | Fluxo `HISTORY` | `HistoryConversationIT` |
| 10 | Vinculado, sem foco (inclui `LESSON_REGISTRATION` e início/busca) | `HomeSearchConversationIT` |

Fora da tabela, mas exigidos na mesma execução: botão antigo (`EXPIRED_ACTION`, `OldButtonFormatIT`),
resposta em até 5 s (`ReplyWithinFiveSecondsIT`), update de grupo ou não suportado (cenário N17) e os roteiros
`*ParityScriptIT`. Se a confirmação achar um estado **sem** teste na suíte existente, o teste é criado nessa
mesma tarefa (é lacuna herdada da 001, não da 004), sem alterar nenhuma expectativa existente.

### 6.2 Tipos de ação (cobertos pelos cenários de contrato dos workflows)

| Tipo | Cenários |
|---|---|
| `MESSAGE` | N3, N6, N17 |
| `EDIT` | N4, N5 |
| `DOCUMENT` | N3, N7 |
| `CALLBACK_REPLY` | N3, N4 |
| `STEP_MESSAGE` | N9 |
| tipo desconhecido | N8 |
