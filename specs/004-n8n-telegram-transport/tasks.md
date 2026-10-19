---

description: "Lista de tarefas da feature 004 — n8n como único transporte do canal Telegram"
---

# Tasks: n8n como único transporte do canal Telegram

**Input**: documentos em `specs/004-n8n-telegram-transport/` — `plan.md`, `spec.md`, `research.md`, `data-model.md`,
`contracts/` (`n8n-workflows.md`, `n8n-endpoints.openapi.yaml`, `transport-scripts.md`) e `quickstart.md`.

**Pré-requisitos**: `plan.md` e `spec.md` aprovados; API interna da feature 002 mesclada (já está); constituição v2.1.0.

**Testes**: **pedidos explicitamente** (testes antes da implementação, TDD). Em cada fase, as tarefas de teste vêm
primeiro e **devem ser vistas falhando** antes da implementação correspondente. Os testes novos de que a
Constituição exige (Princípio V) estão incluídos; a suíte existente de conversa e de paridade é **reaproveitada
sem alterar nenhuma expectativa** (SC-001).

**Organização**: por user story da spec. As oito fases pedidas mapeiam assim:

| Fase | Assunto | User story da spec | Fatia do plano |
|---|---|---|---|
| 0 | Spikes do n8n 2.x (bloqueante) | — | S0 |
| 1 | Contêiner e importação de workflows | US1 (P1) | S1 |
| 2 | Fluxo de entrada e execução de ações | US2 (P1) | S2 |
| 3 | Confirmação e tratamento de falha por ação | US4 (P2) | S2 |
| 4 | Entrega proativa, reentrega e reinício seguro | US3 (P1) e US6 (P2) | S2 |
| 5 | Segurança: tokens, segredo do webhook, rotação, privacidade | US5 (P2) e US2 | S3 |
| 6 | Troca controlada do webhook com reversão | US5 (P2) | S3 |
| 7 | Remoção do adaptador antigo (**só depois da prova de paridade**) | US7 (P3) | S5 |
| 8 | Documentação da 001 e `CLAUDE.md`/READMEs de `infra/` | transversal (FR-032, FR-033) | S4 |
| 9 | Acabamento e validação final | — | — |

A ordem de **dependência** real é 0 → 1 → 2 → 3 → 4 → 5 → 6 → (operação em produção por 7 dias) → 7. A Fase 8
só depende da Fase 1 e pode correr em paralelo às Fases 2 a 6 (item da S4 do plano).

## Formato: `[ID] [P?] [Story] Descrição com caminho`

- **[P]**: pode rodar em paralelo (arquivos diferentes, sem dependência de tarefa incompleta)
- **[USn]**: user story da spec; Fases 0 e 9 não levam rótulo
- Caminhos relativos à raiz do repositório. `T/` = `backend/application/src/test/java/br/com/felipeblaft/reports`
- Identificadores em inglês (Princípio VI); mensagens ao usuário, logs e mensagens de teste/guardião em português
  acentuado; comentários e documentação em português
- Comandos de teste (só Docker, nada instalado no host):
  `sh infra/scripts/local.sh tests backend` · `… tests n8n` · `… tests rehearsal`

---

## Fase 0: Spikes do n8n 2.x (bloqueante)

**Propósito**: fechar as seis perguntas de `research.md` ("Spikes") em ambiente descartável (`docker run`, pasta de
rascunho fora do repositório). Cada resposta pode mudar uma decisão; nada de produção é escrito aqui.

- [X] T001 Confirmar S-1: puxar `docker.n8n.io/n8nio/n8n:2.43.6`, registrar *digest*, usuário, versão do Node, presença de `wget` busybox e `node` com `fetch` em `specs/004-n8n-telegram-transport/research.md` (coluna "Resultado" da tabela de spikes)
- [X] T002 [P] Confirmar S-2: em banco SQLite novo e sem dono configurado, `n8n import:workflow --separate --input=<dir>` e `n8n publish:workflow --id=<id>` funcionam **antes** de `n8n start`, com `id` fixo em JSON; registrar o resultado e o plano B (`--userId`/`--projectId`) em `specs/004-n8n-telegram-transport/research.md`
- [X] T003 [P] Confirmar S-3: `{{ $env.X }}` em campos de `HTTP Request` e `If` com `N8N_BLOCK_ENV_ACCESS_IN_NODE=false`; confirmar que mensagem de erro de nó nunca contém o valor; registrar em `specs/004-n8n-telegram-transport/research.md`
- [X] T004 [P] Confirmar S-4: `Webhook` com `rawBody` + `HTTP Request` com corpo cru repassam bytes idênticos (inclusive id de chat negativo grande); `Respond to Webhook` devolve `200` e `503` sem corpo; nomes de cabeçalho chegam em minúsculas; registrar em `specs/004-n8n-telegram-transport/research.md`
- [X] T005 [P] Confirmar S-5: `neverError` + resposta completa + `continueOnFail` em conexão recusada; `multipart/form-data` com binário vindo de outro `HTTP Request`; binário removido ao fim da execução com `tmpfs` e sem execução salva; registrar em `specs/004-n8n-telegram-transport/research.md`
- [X] T006 [P] Confirmar S-6: tempo do `entrypoint` ao `healthy` (meta ≤ 40 s), efeito de `NODES_EXCLUDE`/`NODES_INCLUDE` no carregamento, webhooks com `N8N_DISABLE_UI=true`, REST disponível para o teste de privacidade com a UI ligada; registrar em `specs/004-n8n-telegram-transport/research.md`
- [X] T007 Consolidar os spikes: preencher a tabela de `specs/004-n8n-telegram-transport/research.md`, ajustar R-03/R-04/R-06/R-10/R-13 e, se algo mudar, `plan.md` e os contratos; commitar. **Gate**: nenhuma tarefa da Fase 1 em diante começa antes deste commit

**Checkpoint**: decisões do n8n 2.x confirmadas ou corrigidas por escrito.

---

## Fase 1: User Story 1 — O n8n sobe com o ambiente e seus workflows vêm do repositório (P1) 🎯

**Goal**: o mesmo comando único sobe o n8n em contêiner com imagem fixada, sem porta pública, com os workflows do
repositório importados e ativos, sem segredo nem ID de credencial em nenhum arquivo versionado.

**Independent Test**: de um ambiente sem volumes, `sh infra/scripts/local.sh up` deixa o `n8n` `healthy` com os
workflows do repositório ativos; destruir e recriar dá o mesmo resultado; a edição manual na instância é
detectada por `n8n.sh check` e descartada no próximo reinício; a varredura de JSON não acha segredo nem ID de credencial.

### Testes da US1 (escreva primeiro; devem FALHAR antes da implementação) ⚠️

- [X] T008 [P] [US1] `WorkflowGuardTest` (offline, roda no `tests-backend`) em `T/architecture/WorkflowGuardTest.java`: regras de `research.md` R-05 sobre `infra/n8n/workflows/*.json` — inventário ⊆ {`telegram-inbound`,`channel-delivery`,`execute-actions`,`transport-health`}, `id` igual ao nome do arquivo, no máximo um receptor de update (`Webhook` com `path=telegram-update`) e nenhum `Telegram Trigger`, lista de nós permitidos, destinos só `$env.BACKEND_INTERNAL_URL`/`$env.TELEGRAM_API_URL`, sem `getFile`, `$env` só nas 5 variáveis permitidas, `If`/`Switch` só sobre `type`/`state`/status/`outcome`/token de entrega, `Code` sem `$env`/`process`/`require`/`fetch`/`eval`, sem texto livre; falha com mensagem em português citando arquivo e caminho JSON, nunca o valor
- [X] T009 [P] [US1] `WorkflowGuardSelfTest` em `T/architecture/WorkflowGuardSelfTest.java` com um JSON-defeito por regra em `backend/application/src/test/resources/workflow-guard/` (um segundo receptor de update, nó de banco, URL externa, `credentials`, `$env` não permitido, `Code` com `require`, `getFile`, `stickyNote`), provando que cada regra reprova
- [X] T010 [P] [US1] **Varredura de segredos e IDs de credencial** em `T/architecture/WorkflowSecretScanTest.java`: varre todo `infra/n8n/workflows/*.json` e os demais arquivos versionados de `infra/n8n/` (Dockerfile, `entrypoint.sh`, `ops/`) e reprova se achar chave `credentials` ou `credentialId`, `id` de credencial, token de bot (`\d{6,}:[\w-]{30,}`), `gsk_`, `sk-`, `Bearer `, base64/hex ≥ 32 caracteres, URL com `user:senha@` ou segredo no caminho, CPF; a mensagem cita arquivo e caminho, nunca o valor. Inclui autoteste com fixtures positivas em `backend/application/src/test/resources/workflow-guard/secret-scan/` (FR-009, SC-005, US1 cenário 4)
- [X] T011 [P] [US1] `ComposeGuardTest` + autoteste em `T/architecture/ComposeGuardTest.java` lendo `infra/compose*.yaml`: imagem sem tag/`latest`/`stable`/`next`/tag major reprova (exceto serviços com `build`), `n8n` com `ports` reprova (só `127.0.0.1:5678` em `compose.dev.yaml`), `n8n` fora da rede `internal`, `n8n` sem `restart` em `compose.prod.yaml`, `N8N_DISABLE_UI` diferente de `true` na base/prod, variável secreta com valor literal, `FROM` do `infra/n8n/Dockerfile` sem versão exata
- [X] T012 [P] [US1] Teste `node:test` do normalizador em `infra/n8n/ops/normalize-workflow.test.mjs`: remove campos voláteis (`createdAt`, `updatedAt`, `versionId`, `activeVersionId`, `shared`, `tags`, `meta.instanceId`, `pinData`, `staticData`), ordena chaves de forma estável, é idempotente, **falha** sem gravar se houver `credentials`, token ou URL com segredo
- [X] T013 [P] [US1] Teste `node:test` do cliente HTTP em `infra/n8n/ops/probe.test.mjs`: imprime `status` e corpo, repassa cabeçalhos, lê segredo de variável de ambiente (nunca de argumento), não imprime token nem URL com token, sai com código ≠ 0 em falha de conexão
- [X] T014 [US1] Suporte dos testes com n8n em `T/support/N8nTestSupport.java`: `@Tag("n8n")`, URLs (`N8N_URL` padrão `http://n8n-test:5678`, backend simulado `:18080`, API de bots simulada `:18081`), helper HTTP, helper de `docker exec`/`docker kill`/`docker start` pelo `docker` CLI, espera por `healthy`
- [X] T015 [US1] `N8nImportIT` em `T/transport/n8n/N8nImportIT.java`: a partir de volume vazio o n8n fica `healthy` e `GET /webhook/transport-health/live` = `200`; o conjunto de workflows publicados é exatamente o do repositório; `check` (exportar + normalizar + comparar, mesma lógica de `n8n.sh check`) sem divergência; reimportar um JSON alterado simula edição manual → `check` reprova → reinício restaura (US1 cenários 1 a 3; FR-008)
- [X] T016 [P] [US1] `N8nHealthIT` (parte `live`) em `T/transport/n8n/N8nHealthIT.java`: `live` responde `200 {"status":"UP"}` sem chamar o backend com o backend parado (a saúde do n8n não depende dele, R-10)
- [X] T017 [P] [US1] `N8nExposureIT` em `T/transport/n8n/N8nExposureIT.java`: pela entrada pública do `web`, `POST /webhook/telegram-update`, `GET /healthz` e `GET /rest/settings` respondem com SPA/404 do Caddy, nunca com resposta do n8n (R-02, FR-002)

### Implementação da US1

- [X] T018 [US1] Criar `infra/n8n/CLAUDE.md` (o que é, regras locais, procedimento de exportação do R-18, "não edite em produção") e acrescentar as linhas `infra/n8n` ao mapa do `CLAUDE.md` raiz e ao `infra/CLAUDE.md` (regra "toda estrutura nova nasce com `CLAUDE.md`", FR-007)
- [X] T019 [P] [US1] Implementar `infra/n8n/ops/normalize-workflow.mjs` (faz T012 passar)
- [X] T020 [P] [US1] Implementar `infra/n8n/ops/probe.mjs` (faz T013 passar)
- [X] T021 [P] [US1] Criar o workflow `infra/n8n/workflows/transport-health.json` (`id` = `transport-health`; `live` sem chamadas; `deep` conforme `contracts/n8n-workflows.md` §5; sem texto livre, sem `credentials`)
- [X] T022 [US1] Criar `infra/n8n/Dockerfile` (`FROM docker.n8n.io/n8nio/n8n:2.43.6` com o *digest* em comentário, `COPY workflows /opt/workflows`, `COPY ops /opt/ops`, `COPY entrypoint.sh`, `USER node`, `ENTRYPOINT ["tini","--","/opt/entrypoint.sh"]` mantendo o `tini` da imagem, F-3)
- [X] T023 [US1] Criar `infra/n8n/entrypoint.sh` (POSIX): `n8n import:workflow --separate`, `n8n publish:workflow --id=` para cada `id` lido dos JSON, `n8n unpublish:workflow` + aviso em português para workflow extra da instância, `exec n8n start`; falha de importação encerra o processo; nada de segredo lido nem impresso
- [X] T024 [US1] Acrescentar o serviço `n8n` em `infra/compose.yaml`: `build: ./n8n`, `image: reports-n8n`, rede `internal`, `expose: ["5678"]`, volume `n8n-data:/home/node/.n8n`, `tmpfs` em `/home/node/.n8n/binaryData`, variáveis de `data-model.md` §2.1, `healthcheck` (R-10), `depends_on` (`backend` saudável, `telegram-bot-api` iniciado), `stop_grace_period: 30s`; no `backend`: `TELEGRAM_WEBHOOK_URL: ${TELEGRAM_WEBHOOK_URL:-http://backend:8080/telegram/webhook}`, e `TELEGRAM_CHANNEL_RESPONSE_TIMEOUT_MS: 30000` (**`TELEGRAM_CHANNEL_ORCHESTRATOR_URL` NÃO entra no compose base nesta fase**: com a URL preenchida o backend ativa o despachante de entrega proativa mesmo em modo `DIRECT`, o que mudaria o caminho atual (FR-027); quem a define é o `switch` da Fase 6); declarar o volume `n8n-data`
- [X] T025 [P] [US1] `infra/compose.dev.yaml`: `N8N_DISABLE_UI: "false"` e `ports: ["127.0.0.1:5678:5678"]` só para o `n8n`
- [X] T026 [P] [US1] `infra/compose.prod.yaml`: `restart: unless-stopped` e `N8N_DISABLE_UI: "true"` para o `n8n`
- [X] T027 [P] [US1] `infra/caddy/Caddyfile`: só comentário explicando que o n8n **não** é roteado (R-02); nenhuma diretiva nova
- [X] T028 [P] [US1] `infra/.env.example`: documentar `N8N_ENCRYPTION_KEY`, `TELEGRAM_TRANSPORT`, `TELEGRAM_VERIFY_CHAT_ID` e a exigência dos três tokens do canal; marcar `TELEGRAM_CHANNEL_ORCHESTRATOR_URL` como "definida pelo compose"; sem valores secretos
- [X] T029 [US1] Criar `infra/scripts/lib/common.sh` (POSIX, *sourced*): escolha de camadas do compose por `--env local|prod`, leitura de chave do `infra/.env`, **escrita atômica** de chave (arquivo temporário + `mv`, permissões preservadas, cópia em `infra/.env.previous`), saída `✔`/`✘` com códigos de motivo, confirmação digitada; nunca imprime valores secretos
- [X] T030 [US1] Criar `infra/scripts/n8n.sh` (`health [--deep]`, `check`, `export`, `import`, `reset`, `logs`) conforme `contracts/transport-scripts.md` §3, usando `docker compose exec -T n8n node /opt/ops/…`
- [X] T031 [US1] `infra/scripts/local.sh`: incluir `n8n` em `services()` com Telegram habilitado, esperar `n8n healthy` em `wait_until_healthy`, exigir `N8N_ENCRYPTION_KEY` em `require_env`, novos subcomandos `tests n8n` (e `all` passa a incluí-lo), e atualizar o cabeçalho de uso
- [X] T032 [US1] `infra/compose.test.yaml`: serviços `n8n-test` (build `../infra/n8n`, UI ligada, `BACKEND_INTERNAL_URL=http://tests-n8n:18080`, `TELEGRAM_API_URL=http://tests-n8n:18081`, tokens e chave **de teste**, `tmpfs`, healthcheck), `tests-n8n` (imagem Maven, `docker` CLI instalado, `/var/run/docker.sock` montado **somente aqui**, portas fixas 18080/18081, `depends_on: postgres-test, n8n-test`) e `tests-n8n-ops` (`reports-n8n` com `node --test /opt/ops`)
- [X] T033 [US1] `backend/application/pom.xml`: perfil `n8n` com execuções do failsafe `n8n-contract` (`**/transport/n8n/*IT.java`), `n8n-transport` (mesma lista de `internal-transport` + `OldButtonFormatIT`, `ReplyWithinFiveSecondsIT`, `ChannelActionsDeliveryIT`, `CompensationConfirmationIT`, `TelegramNotificationOrchestratorIT`, com `telegram.transport=n8n`) (**desvio da Fase 4:** `ChannelActionsDeliveryIT` e `CompensationConfirmationIT` ficam só na execução padrão: testam a semântica do backend contra o orquestrador programável e assumem ids `p:…`, que no transporte n8n viram `u…` por causa das ações de update; a entrega proativa pelo n8n real é coberta por `TelegramNotificationOrchestratorIT`, `ReplyWithinFiveSecondsIT`, `N8nDeliveryWorkflowIT`, `N8nIdempotencyIT` e `N8nResilienceIT`) e `n8n-duplicates` (idem + `telegram.n8n.duplicates=true`); excluir `**/transport/n8n/**` da execução padrão (**na Fase 1 o perfil tem só a execução padrão `n8n-contract`**; `n8n-transport` e `n8n-duplicates` entram na Fase 2/4, junto do modo `n8n` das bases de teste)
- [X] T034 [US1] `.github/workflows/ci.yml`: job `n8n` (depois de `backend` e `frontend`): `docker compose -f infra/compose.test.yaml run --rm tests-n8n-ops` e `… run --rm tests-n8n`, com `down -v` no fim; `.github/CLAUDE.md` descreve o job
- [X] T035 [US1] Rodar o guardião de idioma em modo relatório (`GUARD_REPORT=1`, comando de `.specify/CLAUDE.md`) com os arquivos novos de `infra/` e corrigir nomes; se algum termo novo for necessário, entrar **antes** em `.specify/memory/glossary.json`
- [X] T036 [US1] Rodar `tests backend` e `tests n8n`: T008 a T017 verdes; fechar o checkpoint abaixo e commitar

**Checkpoint (US1)**: `local.sh up` sobe o n8n saudável com `transport-health`; guardiões e varredura de segredo verdes; o
webhook **continua no backend** (FR-027) — nada muda para o professor.

---

## Fase 2: User Story 2 — A conversa completa passa pelo n8n sem diferença visível (P1)

**Goal**: o update entra pelo n8n, vai cru ao backend e as ações voltam executadas em ordem (mensagem, edição,
documento, confirmação de toque, etapa); toda mensagem é respondida e todo toque é confirmado.

**Independent Test**: a suíte existente de conversa e de paridade, com `telegram.transport=n8n`, passa sem alterar
nenhuma expectativa de texto, botão ou ordem.

### Testes da US2 (devem FALHAR antes da implementação) ⚠️

- [X] T037 [P] [US2] `N8nBackendStub` em `T/support/N8nBackendStub.java`: WireMock na porta `18080` que faz o papel do backend nos testes de contrato dos workflows, **valida cada requisição do n8n** contra o esquema v1 / cabeçalhos esperados, grava o que recebeu e permite programar respostas (`UpdateResponse`, `ActionStateResponse`, `404`, `5xx`, PDF)
- [X] T038 [P] [US2] `N8nBotApiStub` em `T/support/N8nBotApiStub.java`: WireMock na porta `18081` que faz o papel do servidor local da API de bots, registra chamadas na ordem (método, corpo, multipart) com horário e permite programar erros do Telegram
- [X] T039 [US2] `N8nInboundWorkflowIT` em `T/transport/n8n/N8nInboundWorkflowIT.java` (cenários N1, N2, N3 sem confirmação, N15, N17 de `contracts/n8n-workflows.md`): corpo repassado byte a byte com `X-N8N-Service-Token` e o cabeçalho de segredo copiado; backend `401`/`500`/fora do ar ⇒ `503` sem corpo e **nenhuma** chamada à API de bots; lista `[CALLBACK_REPLY, MESSAGE, DOCUMENT]` executada nessa ordem com **uma** busca de PDF; áudio ⇒ nenhuma chamada a `getFile`; update de grupo, de tipo não suportado ou ilegível ⇒ `200` e nenhuma chamada à API de bots (N17)
- [X] T040 [P] [US2] Estender `N8nHealthIT` em `T/transport/n8n/N8nHealthIT.java` com `deep` (N14): backend `404` ⇒ `200 backend:ok`; `401` ⇒ `503 backend:unauthorized`; `getMe` falho ⇒ `503 botApi:down`; corpo nunca com token nem URL
- [X] T041 [US2] Modo `n8n` na infraestrutura de conversa: `T/support/IntegrationBase.java` passa a subir o servidor HTTP embutido (porta aleatória) e, no modo `n8n`, a base de conversa mantém um **proxy de porta fixa `18080`** (WireMock) que encaminha ao contexto Spring do teste em execução (cada contexto em cache sobe na sua porta; uma porta fixa por contexto não funciona); `T/support/TelegramConversationBase.java` ganha `TRANSPORT=n8n` (`sendUpdate` faz `POST` ao `${N8N_URL}/webhook/telegram-update` com `X-Telegram-Bot-Api-Secret-Token`, espera `200`; `API_BOTS` do WireMock em porta fixa `18081`; `TOKEN_SERVICE` igual ao do `n8n-test`) sem alterar nenhuma asserção existente; garantir que o modo `webhook` e o `internal` seguem iguais
- [X] T042 [US2] Criar a anotação `T/support/ConversationState.java` (`@ConversationState({n, …})`, valores 1 a 10 dos estados de `specs/001-fluxo-relatorios/contracts/telegram-conversa.md` §4) e o meta-teste `T/architecture/ConversationStateCoverageTest.java`: varre as classes incluídas na execução `n8n-transport` (lista do `pom.xml`, T033) e reprova o build se algum dos 10 estados não tiver ao menos um método anotado, listando os estados sem teste (rastreio de `data-model.md` §6.1)
- [X] T043 [US2] Rodar `sh infra/scripts/local.sh tests n8n` e **ver falhar** `N8nInboundWorkflowIT`, `N8nHealthIT`, `ConversationStateCoverageTest` e as execuções `n8n-transport` (ainda não há `telegram-inbound` nem `execute-actions`)

### Implementação da US2

- [X] T044 [US2] Criar `infra/n8n/workflows/execute-actions.json` conforme `contracts/n8n-workflows.md` §4 (etapas E1, E2, E3 e E6): `GET /actions/{id}`: executar **só** `PENDING`/`DELIVERED`, pular `EXECUTED`/`FAILED`/`PERMANENTLY_FAILED`/`DISCARDED`/`404` sem chamada e sem confirmação (W9), abortar em `5xx`/conexão/`401`; `Switch` por `type`; `Code` puro monta o corpo (teclado `rows`→`inline_keyboard`, `parse_mode=HTML`, `link_preview_options.is_disabled`); chamada à API de bots com `neverError`; `DOCUMENT` com **uma** busca e `multipart`; sem a classificação completa (Fase 3). **Desvio registrado na Fase 2:** a suíte de conversa existente depende da confirmação do `messageId` (o backend grava `stepMessage` a partir dela), então a confirmação **por ação** entra já aqui, com resultado mínimo (`EXECUTED` se a API de bots responder `2xx`, senão `FAILED`/`TELEGRAM_ERROR`); a Fase 3 acrescenta a tabela completa, as tentativas, `ACTION_NOT_SUPPORTED` e `DOCUMENT_UNAVAILABLE`
- [X] T045 [US2] Criar `infra/n8n/workflows/telegram-inbound.json` conforme §2: `Webhook` `POST telegram-update` com `rawBody`, repasse cru ao backend com os dois cabeçalhos, `If status == 200` → `Execute Workflow execute-actions` → `Respond to Webhook 200`; qualquer outro caminho → `503` sem corpo
- [X] T046 [US2] `T/architecture/WorkflowGuardTest.java`: passar a exigir os arquivos `telegram-inbound` e `execute-actions` presentes e `telegram-inbound` com exatamente um receptor de update
- [X] T047 [US2] Anotar com `@ConversationState` os testes existentes de `T/inbound/telegram/*ConversationIT.java` e `T/parity/*ParityScriptIT.java` (somente a anotação, sem tocar em asserção), confirmar a coluna "Teste candidato" de `data-model.md` §6.1 e, **se algum estado não tiver teste**, criar o teste ausente no `*ConversationIT` correspondente (lacuna herdada da 001; nenhuma expectativa existente muda)
- [X] T048 [US2] Rodar `tests backend` (guardiões) e `tests n8n`: `N8nInboundWorkflowIT`, `N8nHealthIT`, `ConversationStateCoverageTest` e a execução `n8n-transport` (toda a suíte de conversa e de paridade) verdes; **nenhuma expectativa existente alterada** (SC-001, SC-002); corrigir os workflows, nunca as expectativas

**Checkpoint (US2)**: a conversa inteira passa pelo n8n com a transcrição do chat idêntica à do caminho antigo.

---

## Fase 3: User Story 4 — Falha ao executar uma ação é informada e a lista continua (P2)

**Goal**: cada ação é confirmada ao backend com o resultado (ou código de motivo), a lista nunca para por falha
de execução, e nenhum texto de aluno acompanha a falha.

**Independent Test**: simular cada falha do Telegram numa lista com várias ações e conferir ordem, confirmação por
ação e códigos corretos.

### Testes da US4 (devem FALHAR antes da implementação) ⚠️

- [X] T049 [P] [US4] Extrair a tabela de casos de classificação para `T/support/ClassificationCases.java` (resposta do Telegram → `outcome`/`reasonCode`, inclusive "message is not modified", `403`, "chat not found", `429`, edição antiga, erro desconhecido) e fazer `T/support/SimulatedActionsExecutorTest.java` e o teste unitário correspondente usá-la; nenhum comportamento muda
- [X] T050 [US4] `N8nFailureHandlingIT` em `T/transport/n8n/N8nFailureHandlingIT.java` (N3 com confirmações, N4 a N10c, N16): edição recusada ⇒ `MESSAGE_NOT_EDITABLE` e 3ª ação executa; "not modified" ⇒ `EXECUTED`; `403`/`429`/desconhecido/conexão recusada ⇒ `CHAT_UNAVAILABLE`/`RATE_LIMITED`/`TELEGRAM_ERROR`/`TELEGRAM_ERROR` sem texto do erro; `DOCUMENT` com `404`/`5xx` ⇒ **uma** `GET`, `DOCUMENT_UNAVAILABLE`, nenhum `sendDocument`; `type` novo ⇒ `ACTION_NOT_SUPPORTED` e as demais executam, campo extra ignorado; `STEP_MESSAGE` sem/com `editMessageId` ⇒ `messageId` correto; ação `EXECUTED`/`FAILED`/`PERMANENTLY_FAILED`/`DISCARDED`/`404` pulada sem chamada à API de bots e sem confirmar, e ação `PENDING`/`DELIVERED` executada (N10, N10b, N10c); confirmação recusada 3 vezes ⇒ lista segue e a resposta é `200`; `CALLBACK_REPLY` executa mesmo após falha anterior; confirmação **por ação**, logo após executar
- [X] T051 [US4] Rodar `tests n8n` e **ver falhar** T050 (sem classificação nem confirmação)

### Implementação da US4

- [X] T052 [US4] `infra/n8n/workflows/execute-actions.json`: `Code` puro de classificação (mesma tabela de `ClassificationCases`), `ACTION_NOT_SUPPORTED` no *fallback* do `Switch`, `DOCUMENT_UNAVAILABLE` sem chamar a API de bots, `messageId` de `STEP_MESSAGE`, `continueOnFail` em conexão sem reproduzir a mensagem da exceção
- [X] T053 [US4] `infra/n8n/workflows/execute-actions.json`: `POST …/confirmations` **por ação** (`version: "v1"`, `messageId` obrigatório em etapa executada), até 3 tentativas com 1 s, e falha persistente não derruba a lista nem a resposta (R-06 E5)
- [X] T054 [US4] Rodar `tests backend` e `tests n8n`: T050 verde e a suíte de conversa/paridade pelo n8n **ainda** verde; sem expectativa alterada

**Checkpoint (US4)**: nenhuma falha pontual deixa o professor sem resposta ou sem PDF; cada resultado chega ao backend com código.

---

## Fase 4: User Stories 3 e 6 — Entrega proativa, reentrega e reinício seguro (P1/P2)

**Goal**: o backend entrega lotes proativos ao n8n, que os executa uma única vez e informa o `messageId` da etapa;
com o n8n caindo ou reiniciando, nada se perde nem duplica e o estado da conversa continua no backend.

**Independent Test**: concluir processamento de áudio (Telegram) e trabalho da Web com professor vinculado e ver
o chat; repetir a entrega e ver que nada chega a mais; derrubar o n8n no meio da lista e ver as ações restantes
chegarem uma vez.

### Testes das US3 e US6 (devem FALHAR antes da implementação) ⚠️

- [X] T055 [P] [US3] `N8nDeliveryWorkflowIT` em `T/transport/n8n/N8nDeliveryWorkflowIT.java` (N11, N12): sem token e com token errado ⇒ `401` sem corpo e **nenhuma** chamada a ninguém; lote válido ⇒ `200` **só depois** das confirmações, ordem preservada, `Idempotency-Key` não guardado; lote repetido com os mesmos `actionId` ⇒ nada reexecutado
- [X] T056 [US3] Modo `n8n` na entrega proativa em `T/support/OrchestratorDeliveryBase.java`: com `telegram.transport=n8n`, `orchestrator-url` aponta para `${N8N_URL}/webhook/channel-delivery` e `drain()` espera as confirmações no banco (em vez de rodar o executor em memória); no modo `internal` continua como hoje
- [X] T057 [US3] Modo de duplicidade em `T/support/TelegramConversationBase.java` e `T/support/OrchestratorDeliveryBase.java`: `-Dtelegram.n8n.duplicates=true` entrega cada update duas vezes e reentrega cada lote; as asserções existentes não mudam
- [X] T058 [US3] `N8nIdempotencyIT` em `T/transport/n8n/N8nIdempotencyIT.java`: roda os mesmos cenários com e sem duplicidade e exige **transcrição idêntica** do chat, sem mensagem a mais (FR-028a, SC-011), para update e para lote proativo; registrar a transcrição por cenário
- [X] T059 [US6] Suporte de queda em `T/support/N8nTestSupport.java`: `killN8n()`, `startN8n()`, `awaitHealthy(timeout)` pelo `docker` CLI e medição do tempo de volta a receber updates
- [X] T060 [US6] `N8nResilienceIT` em `T/transport/n8n/N8nResilienceIT.java` (N13, N13b, N13c e FR-028b): (a) N13b com update: queda **entre** ações (a 1ª confirmada, a 2ª ainda não iniciada) ⇒ o Telegram simulado reenvia ⇒ a 1ª **não** repete e as restantes chegam uma vez; (b) N13b com lote proativo: o backend reentrega os mesmos `actionId` ⇒ idem; (b2) N13c: queda na **janela em voo** (depois do `sendMessage`, antes da confirmação) ⇒ a ação em voo repete **no máximo uma vez**, as demais seguem N13b, e o teste registra essa repetição como o comportamento documentado (Clarifications); (c) reinício planejado volta a receber updates em ≤ 60 s e as mensagens do intervalo são respondidas uma vez, com atraso; (d) conversa no meio do registro de aula continua da mesma etapa após reinício; (e) backend fora do ar ⇒ `503` ao Telegram e resposta quando volta; (f) lote lento **abaixo de 30 s** ⇒ nenhuma duplicidade; acima de 30 s a sobreposição é o risco residual de R-07 e o teste apenas documenta o comportamento
- [X] T061 [P] [US2] `N8nLatencyIT` em `T/transport/n8n/N8nLatencyIT.java`: 100 updates, p95 do intervalo entre a chegada ao n8n e a primeira chamada à API de bots ≤ 2 s (SC-003), usando o horário do `N8nBotApiStub`
- [X] T062 [US3] Rodar `tests n8n` e **ver falhar** T055, T058, T060 (ainda sem `channel-delivery`)

### Implementação das US3 e US6

- [X] T063 [US3] Criar `infra/n8n/workflows/channel-delivery.json` conforme `contracts/n8n-workflows.md` §3: confere `X-Channel-Delivery-Token` contra `$env.CHANNEL_DELIVERY_TOKEN`, `401` sem corpo, `Execute Workflow execute-actions`, `200` só depois da execução e confirmação, `503` em falha de transporte
- [X] T064 [US3] `T/architecture/WorkflowGuardTest.java`: ativar a asserção de **inventário completo** (os quatro workflows presentes) e a de "um único receptor de update em todo o conjunto"
- [X] T065 [US6] Ajustes que os testes pedirem em `infra/compose.yaml` e `infra/n8n/entrypoint.sh` para o orçamento de 60 s (ordem de importação, `healthcheck` `start_period` de 60 s, `stop_grace_period`; a alavanca de não publicar o sub-workflow `execute-actions` **não funciona**; entrou a publicação em paralelo no `entrypoint.sh`, R-10); registrar a medição em `specs/004-n8n-telegram-transport/research.md` (R-10)
- [X] T066 [US3] Rodar `tests backend` e `tests n8n` completos: T055, T058, T060, T061 verdes e **toda** a suíte de conversa/paridade pelo n8n, nas execuções `n8n-transport` e `n8n-duplicates`; expectativas existentes intactas

**Checkpoint (US3, US6)**: o transporte pelo n8n está completo no CI; o caminho antigo continua intacto e o webhook ainda não foi trocado.

---

## Fase 5: User Story 5 (parte 1) e privacidade — Segurança: tokens, segredo do webhook, rotação (P2)

**Goal**: segredos só do ambiente e sem vazamento; segredo do webhook repassado e validado pelo backend; rotação do
token de serviço com os dois tokens simultâneos, sem janela de recusa; nenhum dado de aluno no n8n.

**Independent Test**: suíte de segurança e de privacidade pelo n8n verde; ensaio da rotação com tráfego sem
recusas e sem respostas duplicadas.

### Testes (devem FALHAR antes da implementação) ⚠️

- [X] T067 [P] [US5] `N8nSecurityIT` em `T/transport/n8n/N8nSecurityIT.java`: segredo do webhook ausente/errado ⇒ backend `401` ⇒ n8n `503` sem enviar nada; token de serviço errado no n8n ⇒ `deep` mostra `backend:unauthorized` e update ⇒ `503`; dois tokens aceitos pelo backend ao mesmo tempo (n8n com `CURRENT`, depois com `NEXT`) sem recusa; o n8n não valida o segredo do Telegram por conta própria; nenhuma chamada a `getFile`
- [X] T068 [P] [US2] `N8nPrivacyIT` em `T/transport/n8n/N8nPrivacyIT.java` (FR-031, SC-005): depois da suíte, **aguarda a poda de ≈ 2 min (consulta até ≈ 3 min)** e varre `docker logs` do `n8n-test`, a exportação dos workflows e as execuções (leitura do SQLite pelo `node:sqlite` do contêiner, porque a REST não lista execução apagada logicamente; F-1) atrás de tokens, nomes de aluno, textos de relatório e dos *sentinelas* dos testes e dos bytes do PDF; exige zero ocorrências, zero execuções salvas e `binaryData` vazio ao fim
- [X] T069 [P] [US5] Teste `node:test` de `infra/n8n/ops/generate-token.test.mjs`: 32 bytes aleatórios em base64url, nunca repete, sem escrever em disco
- [X] T070 [P] [US5] Teste `node:test` do simulador em `infra/telegram-simulator/server.test.mjs`: `setWebhook`/`getWebhookInfo`/`deleteWebhook`, entrega do update com o cabeçalho de segredo, **retenção e reenvio** enquanto a resposta não for `2xx`, transcrição por chat, falha programada, `sendDocument` multipart
- [X] T071 [US5] Roteiro do ensaio de rotação em `infra/tests/rehearsal/rotation.sh` (e harness `infra/tests/rehearsal/lib.sh`): `status`→`IDLE`; `start` sob tráfego ⇒ `DUAL` e zero recusas; `finish` ⇒ `IDLE`, antigo recusado; `abort`; recusa de `start` fora de `IDLE`; cada update com **exatamente uma** resposta na transcrição

### Implementação

- [X] T072 [US5] Implementar `infra/n8n/ops/generate-token.mjs` (faz T069 passar)
- [X] T073 [US5] Criar `infra/telegram-simulator/` (`server.mjs`, `Dockerfile` sobre `node:24-alpine` fixado, `CLAUDE.md`, linha no `CLAUDE.md` raiz): API de bots mínima + API de controle `/_control/*` conforme `research.md` R-12 (faz T070 passar)
- [X] T074 [US5] `infra/compose.test.yaml`: serviços do ensaio — `postgres-rehearsal`, `backend-rehearsal` (imagem real, IA simulada, `TELEGRAM_API_URL` apontando ao simulador), `n8n-rehearsal`, `telegram-simulator` e `tests-rehearsal` (`docker:27-cli` fixado, socket montado, repositório em `/work`)
- [X] T075 [US5] Criar `infra/scripts/rotate-service-token.sh` conforme `contracts/transport-scripts.md` §2 (`status`, `start`, `finish`, `abort`; verificação por `GET /actions/u0-0`; recria `backend` e depois `n8n`; nunca imprime token), usando `lib/common.sh`
- [X] T076 [US5] `infra/scripts/local.sh`: subcomando `tests rehearsal`; `.gitignore`: `infra/.env.previous` e `infra/.transport-state`; `.github/workflows/ci.yml`: passo do ensaio no job `n8n`
- [X] T077 [US5] Rodar `tests backend`, `tests n8n` e `tests rehearsal`: T067 a T071 verdes; corrigir causas, não expectativas

**Checkpoint (parte 1 da US5)**: segurança e privacidade provadas; a rotação roda sem janela de recusa.

---

## Fase 6: User Story 5 (parte 2) — Troca controlada do webhook com reversão (P2)

**Goal**: um procedimento documentado e automatizado liga o n8n ao professor real (webhook e entrega proativa
juntos), com recusa segura, verificação pós-troca e reversão igualmente simples, sem perda nem duplicidade.

**Independent Test**: `tests rehearsal` — troca e reversão sob tráfego, cada update com uma resposta; recusa com n8n
parado; `verify` reprova com segredo errado.

### Testes (devem FALHAR antes da implementação) ⚠️

- [X] T078 [US5] Roteiro `infra/tests/rehearsal/switch.sh`: `status`=`DIRECT`; `switch` sob tráfego contínuo ⇒ `N8N` (webhook, entrega proativa e `verify`); `rollback` ⇒ `DIRECT`; `switch` de novo; **exatamente uma** resposta por update em todas as fases (US5 cenários 1 a 3)
- [X] T079 [US5] Roteiro `infra/tests/rehearsal/switch-refusals.sh`: `switch` com o `n8n` parado ⇒ saída `1` `[N8N_NOT_HEALTHY]` e webhook intacto; segredo com caractere inválido ⇒ `[INVALID_WEBHOOK_SECRET]`; `verify` com segredo registrado diferente ⇒ saída `3` `[SECRET_MISMATCH]`; `switch` cuja verificação reprova ⇒ restaura o estado anterior e sai `3`; repetir `switch`/`rollback` é idempotente (US5 cenários 4 e 5)
- [X] T080 [US5] Rodar `tests rehearsal` e **ver falhar** T078 e T079 (o script ainda não existe) *(a falha esperada não foi observada: os roteiros e o script nasceram juntos)*

### Implementação

- [X] T081 [US5] Criar `infra/scripts/telegram-webhook.sh` conforme `contracts/transport-scripts.md` §1: `status`, `switch`, `rollback`, `verify`, `apply`; pré-condições com códigos de motivo; validação `^[A-Za-z0-9_-]{1,256}$`; `setWebhook` com `secret_token`, `allowed_updates` e `max_connections=10`; `getWebhookInfo`; sondas de segredo (negativa e positiva sem efeito); verificação do modo de entrega no ambiente do `backend`; `--chat-id` para a ida e volta real; restauração automática em reprovação; confirmação digitada em `--env prod`
- [X] T082 [US5] `infra/scripts/local.sh` e `infra/scripts/deploy.sh`: chamar `telegram-webhook.sh apply` depois de a pilha ficar saudável (`deploy.sh` **não** executa `switch`); `require_env` exige os três tokens do canal quando `TELEGRAM_TRANSPORT=N8N`
- [X] T083 [P] [US5] `infra/scripts/README.md` e `infra/README.md`: seções "Trocar o webhook para o n8n", "Reverter", "Verificar", "Rotacionar o token de serviço", "Versão do n8n", "Exportar workflows de volta"; todas com os comandos de `quickstart.md`
- [X] T084 [US5] Rodar `tests rehearsal` (T078, T079 e o roteiro de rotação) e `tests all`; tudo verde
- [X] T085 [US5] Ensaio **manual, uma vez**, com um bot de testes real (`quickstart.md` V13): `switch --chat-id`, conversa, áudio curto, PDF, `rollback`; registrar o resultado (sem dado pessoal) na descrição do PR *(declarado validado pelo mantenedor no chat em 2026-10-19; sem evidência anexada)*

**Checkpoint (US5)**: troca e reversão ensaiadas e repetíveis; o transporte pelo n8n está pronto para a produção. O PR das Fases 0 a 6 (e 8) pode ser aberto; o merge é do usuário.

---

## ⏸ Operação em produção — 7 dias de observação (fora do código)

> ⛔ **PARE AQUI.** O `/speckit-implement` termina em T085 (Fase 6). As tarefas desta seção são **humanas**, levam 7 dias e dependem do merge das Fases 0 a 6; um agente **não** as executa na mesma sessão nem avança para a Fase 7.

Não há tarefa de código. Depois do merge das Fases 0 a 6:

- [X] T086 O mantenedor executa `telegram-webhook.sh switch --env prod --chat-id <id>`, anota `switchedAt` e abre um **PR de documentação** para a `main` criando `specs/004-n8n-telegram-transport/evidence/parity.md` (campos de `data-model.md` §5); o merge é do usuário *(declarado validado pelo mantenedor no chat em 2026-10-19; sem evidência anexada)*
- [X] T087 Durante **7 dias corridos**, coletar diariamente os quatro sinais (updates sem resposta, respostas duplicadas, ações proativas com falha definitiva por transporte, confirmações de toque perdidas — todos exigidos em zero) e registrar relatos de professores (`quickstart.md` V16) *(declarado validado pelo mantenedor no chat em 2026-10-19; sem evidência anexada)*
- [X] T088 Dono do produto registra a aprovação (`approvedBy`/`approvedAt`) em `evidence/parity.md` (atualização por PR de documentação, merge pelo usuário); qualquer sinal diferente de zero **reinicia a contagem** ou volta a `rollback` *(declarado validado pelo mantenedor no chat em 2026-10-19; sem evidência anexada)*

---

## Fase 7: User Story 7 — Remoção do adaptador antigo (P3) — **só depois da prova de paridade**

**Goal**: o backend deixa de interpretar o webhook direto e de enviar mensagens direto ao Telegram; as chaves que só
serviam ao caminho antigo somem; o download de áudio pelo servidor local de bots permanece.

**Independent Test**: com a evidência anexada, a suíte inteira (backend, frontend, conversa e paridade pelo n8n,
guardiões) passa sem alterar expectativa e nenhuma rota, classe, propriedade ou variável órfã resta.

> ⛔ **FASE BLOQUEADA.** Só começa depois de T086 a T088 concluídos, do `evidence/parity.md` completo **na `main`** e em sessão nova, em branch nova (T090). Não pertence ao mesmo `/speckit-implement` das Fases 0 a 6.

**⚠️ GATE (FR-029)**: **não iniciar** enquanto T086 a T088 não estiverem concluídos e `evidence/parity.md` completo **ou** com a dispensa formal do mantenedor (`mode: waiver`, ver spec, sessão 2026-10-19), na `main`.
Se a evidência estiver incompleta, esta fase **não é aberta** e o que falta é listado no PR/issue (US7 cenário 1).

- [X] T089 [US7] *(gate satisfeito pela dispensa formal `mode: waiver` do mantenedor, PR #22 mesclado na `main`)* Verificar o gate: `evidence/parity.md` com `observationDays ≥ 7`, quatro sinais em zero, nenhum relato pendente, `suiteRun` verde na `main` e aprovação registrada **ou** `mode: waiver` com `waivedBy`/`waivedAt`/`reason`, **tudo já na `main`** (PR de documentação mesclado); se faltar algo, registrar o que falta e **parar**
- [X] T090 [US7] Criar a branch `spec/004-remove-direct-telegram-path` a partir da `main` atualizada (`git checkout main && git pull --ff-only origin main`); nunca reaproveitar a branch das Fases 0 a 6 (Princípio VIII)
- [X] T091 [US7] Levantar o inventário de remoção (`research.md` R-15) com `get_impact_radius_tool`, `get_affected_flows_tool` e `Grep` em `backend/application/src/main/java/br/com/felipeblaft/reports/` (`TelegramWebhookController`, `TelegramSender`, `TelegramDirectPublicationAdapter`, `TelegramActionPublicationPort`, `TelegramChannelConfig`, `TelegramConfig`, `TelegramOutput`, `TelegramUpdateResult`, `SecurityConfig`, `application.yaml`) e atualizar R-15 com o que realmente tem uso

### Testes da US7 (escreva primeiro; devem FALHAR antes da remoção) ⚠️

- [X] T092 [P] [US7] `ParityEvidenceGuardTest` em `T/architecture/ParityEvidenceGuardTest.java` (e autoteste com fixtures de evidência incompleta em `backend/application/src/test/resources/parity-evidence/`): lê `specs/004-n8n-telegram-transport/evidence/parity.md` e reprova o build se o arquivo faltar; com `mode: waiver` exige `waivedBy`, `waivedAt` e `reason` não vazios; com `mode: evidence` reprova o build se `observationDays` < 7, qualquer sinal ≠ 0 (exceto a repetição isolada documentada e aceita de `duplicateReplies`), `teacherReports` ≠ nenhum, `suiteRun` vazio ou `approvedBy`/`approvedAt` ausentes (FR-029); é a trava do CI que impede a remoção sem a prova
- [X] T093 [P] [US7] `OrphanConfigurationGuardTest` em `T/architecture/OrphanConfigurationGuardTest.java`: reprova chave de `application.yaml`/`.env.example`/`compose*.yaml` que nenhuma classe lê, rota `/telegram/webhook` e variáveis `TELEGRAM_CHANNEL_DELIVERY`, `TELEGRAM_WEBHOOK_URL`; garante que `getFile`/`setMyCommands` e `TELEGRAM_API_URL`, `TELEGRAM_FILES_DIR`, `PENDING_AUDIO_MINUTES`, `TELEGRAM_WEBHOOK_SECRET` **continuam** (FR-030, SC-010)
- [X] T094 [P] [US7] Teste de áudio preservado em `T/inbound/telegram/AudioDownloadAfterRemovalIT.java`: com o caminho antigo removido, chega um áudio e o backend baixa pelo servidor local de bots, com o mesmo limite e as mesmas regras de descarte (US7 cenário 4)
- [X] T095 [US7] Rodar `tests backend` e **ver falhar** T093 (as chaves e a rota antigas ainda existem)

### Remoção

- [X] T096 [US7] Testes: remover o modo `webhook` de `T/support/TelegramConversationBase.java`; tornar o canal interno com o executor em memória o padrão do `tests-backend`; remover a execução `internal-transport` redundante em `backend/application/pom.xml` se ficar duplicada; apagar testes que só existiam para o caminho antigo — **sem alterar nenhuma expectativa de texto, botão ou ordem**
- [X] T097 [US7] Remover `TelegramWebhookController`, `TelegramSender`, a exceção de `/telegram/webhook` em `inbound/web/security/SecurityConfig.java` e o que `get_impact_radius_tool` mostrar como sem uso (`TelegramOutput`, `TelegramUpdateResult.outputs()`) em `backend/application/src/main/java/br/com/felipeblaft/reports/inbound/telegram/`
- [X] T098 [US7] Remover `TelegramDirectPublicationAdapter`, a alternativa "direta" de `TelegramActionPublicationPort`, a verificação "exatamente um caminho ativo" e a propriedade de escolha em `outbound/telegram/` e `config/TelegramChannelConfig.java`; manter `TelegramOutboxPublicationAdapter`, `N8nOrchestratorAdapter` e `ChannelActionMapper`
- [X] T099 [US7] `config/TelegramConfig.java` e `config/ApplicationProperties.java`: retirar o `setWebhook` e `webhookUrl`, `proactiveDelivery`; **manter** `setMyCommands`, `getFile` (`TelegramMessagingAdapter` reduzido) e `webhookSecret`; `backend/application/src/main/resources/application.yaml`: retirar `TELEGRAM_CHANNEL_DELIVERY` e `TELEGRAM_WEBHOOK_URL`
- [X] T100 [US7] Infra: `infra/compose.yaml` (retirar `TELEGRAM_WEBHOOK_URL` e `TELEGRAM_CHANNEL_ORCHESTRATOR_URL` do `backend` se virarem constantes do n8n), `infra/.env.example` (retirar `TELEGRAM_TRANSPORT`, `TELEGRAM_CHANNEL_DELIVERY`, chaves órfãs), `infra/scripts/telegram-webhook.sh` (remover `rollback` e `switch`; ficam `status`, `verify`, `apply` como **único dono** do registro do webhook), `infra/scripts/local.sh`/`deploy.sh` (já chamam `apply`), `infra/tests/rehearsal/` (ajustar roteiros) — e o `ComposeGuardTest`/`WorkflowGuardTest` se citarem o caminho antigo
- [X] T101 [US7] Documentação da remoção: `infra/CLAUDE.md`, `infra/README.md`, `infra/scripts/README.md`, `backend/application/CLAUDE.md` e `specs/001-fluxo-relatorios/contracts/telegram-conversa.md` deixam de falar de dois caminhos; registrar a consequência aceita ("depois do merge, reverter é reimplantar a versão anterior")
- [X] T102 [US7] Rodar `tests all` e `tests n8n` e `tests rehearsal`: suíte inteira verde, T093 e T094 verdes, nenhuma expectativa alterada; `mvn spotless:apply`; abrir o PR com a evidência anexada (sem mesclar), e `code-review-graph build`

**Checkpoint (US7)**: o n8n é o único transporte; nenhuma sobra do caminho antigo; áudio intacto.

---

## Fase 8: Documentação da 001 e `CLAUDE.md`/READMEs de `infra/` (FR-032, FR-033)

**Propósito**: nenhum documento afirma, como situação atual, que não há n8n; o registro histórico das decisões
originais é preservado (notas de histórico, sem reescrever o que já foi decidido). Pode correr em paralelo às Fases 2 a 6.

- [X] T103 [P] `specs/001-fluxo-relatorios/research.md`: R-04 e "Alternativas consideradas" passam a apontar o n8n (R-01 da 002 e `specs/004-…/plan.md`), preservando o texto original como histórico
- [X] T104 [P] `specs/001-fluxo-relatorios/plan.md`: Summary, Technical Context, estrutura e Complexity Tracking refletem o transporte pelo n8n (linhas II e VII do Constitution Check, "sem orquestrador")
- [X] T105 [P] `specs/001-fluxo-relatorios/quickstart.md`: cenários do Telegram passam pelo n8n (comandos de `infra/scripts/`)
- [X] T106 [P] `specs/001-fluxo-relatorios/tasks.md`: anotar, sem reescrever tarefas concluídas, que o transporte do Telegram é o n8n (feature 004)
- [X] T107 [P] `specs/001-fluxo-relatorios/contracts/telegram-conversa.md`: §1 (transporte) aponta o n8n e os contratos da 002/004
- [X] T108 [P] `infra/CLAUDE.md` e `infra/README.md`: retirar "o n8n ainda será incluído" e "Quando o n8n entrar…"; descrever o serviço, o volume `n8n-data`, a imagem fina e a regra de workflows como código
- [X] T109 [P] `infra/scripts/README.md` e `infra/.env.example`: retirar o trecho "sem n8n" / `N8N_*` não existe aqui / "o compose é da feature 004" e descrever as variáveis reais
- [X] T110 [P] `specs/CLAUDE.md`, `.github/CLAUDE.md`, `backend/application/CLAUDE.md` e `CLAUDE.md` raiz: tabela da 004, descrição do job `n8n`, menção ao simulador e ao `infra/n8n` no mapa
- [X] T111 `specs/004-n8n-telegram-transport/quickstart.md`: conferir cada comando (V1 a V16) contra o que foi realmente entregue e corrigir divergências (FR-033)

---

## Fase 9: Acabamento e validação final

- [X] T112 Rodar `quickstart.md` V1 a V15 de ponta a ponta num ambiente limpo (V16 só após a observação) e anotar divergências *(V4 a V12 pelas suítes `tests backend`, `tests n8n` e `tests rehearsal`; V1 a V3, V13 e V14 exigem um ambiente local com bot de testes e ficam com o mantenedor)*
- [X] T113 [P] Rodar o guardião de idioma completo (`tests backend` e `tests frontend`) sem lista de exclusão e sem palavra em português em identificador, arquivo ou YAML novo
- [X] T114 [P] Revisar o PR contra a constituição (Princípios II, IV, VIII, restrições de contêiner e de orquestrador): refazer a tabela "Pós" de `plan.md` se algo mudou
- [X] T115 Confirmar que o `CLAUDE.md` de `infra/n8n/` e de `infra/telegram-simulator/` existem e que a tabela da raiz tem as duas linhas
- [X] T116 Depois de **cada** PR aberto: `code-review-graph build` (CLAUDE.md raiz) e `ccd_pr` `get_status`; **nunca** mesclar nem habilitar merge automático

---

## Dependências e ordem de execução

### Entre fases

- **Fase 0** → bloqueia tudo. **Fase 1** → bloqueia 2 a 6 (compose, imagem, guardiões, `tests-n8n`).
- **Fase 2** → **3** → **4** (cada uma estende `execute-actions.json`; as três mexem no mesmo arquivo, então não correm em paralelo).
- **Fase 5** depende da 4 (`N8nPrivacyIT` precisa de tráfego completo) e **Fase 6** depende da 5 (simulador, harness, `lib/common.sh`).
- **Fase 8** depende só da Fase 1; pode ser entregue junto com qualquer PR das Fases 1 a 6.
- **Operação (T086–T088)** depende do merge das Fases 0 a 6; **Fase 7** depende **da operação concluída** e do gate T089.

### Dentro de cada fase

Testes (devem falhar) → suporte de teste → implementação → rodar suíte → checkpoint. Os rótulos [P] marcam arquivos distintos.

### Oportunidades de paralelismo

- Fase 0: T002 a T006 em paralelo após T001.
- Fase 1: T008 a T013 (testes offline em arquivos distintos); T019 a T021 e T025 a T028 (arquivos distintos).
- Fase 2: T037, T038 e T040 em paralelo; **T044/T045 não** (T045 usa `execute-actions`).
- Fase 5: T067, T068, T069, T070 em paralelo.
- Fase 8: T103 a T110 todas em paralelo.

### Exemplo de execução paralela (Fase 1)

```text
Task: "WorkflowGuardTest em T/architecture/WorkflowGuardTest.java"
Task: "WorkflowSecretScanTest em T/architecture/WorkflowSecretScanTest.java"
Task: "ComposeGuardTest em T/architecture/ComposeGuardTest.java"
Task: "normalize-workflow.test.mjs em infra/n8n/ops/"
Task: "probe.test.mjs em infra/n8n/ops/"
```

---

## Estratégia de implementação

### Primeiro entregável (MVP): Fases 0 a 4

1. Fase 0 (spikes) → Fase 1 (serviço e workflows, **webhook ainda no backend**) → Fases 2 a 4 (transporte completo provado no CI).
2. **Parar e validar**: `tests n8n` verde (conversa, paridade, duplicidade, queda, privacidade parcial). O professor ainda não vê nada diferente.
3. Abrir o PR das Fases 0 a 4 (e 8 se pronta); merge pelo usuário.

### Entrega incremental

1. + Fases 5 e 6: scripts de rotação e de troca, simulador e ensaio → PR; ensaio manual com bot de testes.
2. Troca em produção pelo mantenedor → 7 dias de observação → evidência.
3. Fase 7 em **branch e PR próprios**, só com a evidência; é a última fase.

### Regras que valem para todas as fases

- Commitar por tarefa ou grupo lógico; PR ao concluir cada entrega; **nunca** mesclar o próprio PR.
- Nunca alterar uma expectativa de texto, botão ou ordem dos testes existentes para fazer o n8n passar: corrigir o workflow.
- Nenhum segredo, token ou ID de credencial em arquivo versionado, em log ou em saída de script.
- Nenhum texto ao professor dentro do n8n; nenhuma rota nova no backend.
