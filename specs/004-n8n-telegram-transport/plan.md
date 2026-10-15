# Implementation Plan: n8n como único transporte do canal Telegram

**Branch**: `spec/004-n8n-telegram-transport` | **Date**: 2026-10-15 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `specs/004-n8n-telegram-transport/spec.md`

## Summary

O n8n passa a ser o único transporte do Telegram (constituição v2.1.0, Princípio II) sem que o professor veja
qualquer diferença. A API interna da feature 002 está pronta e **não muda**: esta feature entrega o **lado do
orquestrador** (serviço em contêiner, workflows versionados, scripts operacionais, testes pelo n8n) e, numa
entrega separada e só depois da prova de paridade, **remove o caminho antigo** do backend.

**Abordagem técnica** (decisões em [research.md](./research.md)):

1. **Serviço**: imagem fina `reports-n8n` (`infra/n8n/Dockerfile`) sobre `docker.n8n.io/n8nio/n8n:2.43.6`
   (versão fixada; nunca `latest`, `stable` ou `next`), com os workflows e o `entrypoint.sh` copiados para dentro.
   Mesma imagem no local, nos testes e na produção; só a configuração varia (R-01, R-04).
2. **Rede**: o `n8n` fica só na rede `internal` (`expose: 5678`, nenhum `ports`). O Caddy continua sendo a única
   porta pública e **não ganha rota para o n8n**: o servidor local da API de bots (`--local`) entrega o webhook
   pela rede interna. Divergência consciente do pedido de "Caddy encaminha o webhook" — ver R-02 e o aviso no
   fim deste plano.
3. **Quatro workflows** em `infra/n8n/workflows/*.json`, sem segredo e sem ID de credencial: `telegram-inbound`
   (o **único** receptor de updates), `channel-delivery` (entrega proativa), `execute-actions` (executor
   compartilhado, sub-workflow) e `transport-health` (verificação de saúde). Segredos chegam por `$env` (R-03).
4. **Importação na subida**: o `entrypoint.sh` roda `n8n import:workflow --separate` e `n8n publish:workflow`
   para cada workflow e só então `exec n8n start`; o repositório sempre prevalece (R-04, FR-008).
5. **Execução fiel ao contrato 002 §4**: ordem preservada, sem parar na primeira falha, consulta
   `GET /actions/{id}` antes de **cada** ação, confirmação **por ação** logo após executar, documento em uma única
   tentativa, `ACTION_NOT_SUPPORTED` para tipo desconhecido (R-06).
6. **Aceite do lote proativo só depois de executar e confirmar** (R-07): cumpre FR-023 sem mudar o backend, porque
   o contrato da 002 só reentrega ação **recusada** (queda do n8n no meio = conexão cortada = recusa = reentrega).
7. **Troca e volta do webhook** por `infra/scripts/telegram-webhook.sh` (`switch`, `rollback`, `verify`, `status`,
   `apply`), com `setWebhook` e segredo validado; **rotação do token** por `infra/scripts/rotate-service-token.sh`
   usando os dois tokens simultâneos da 002 (R-08, R-09).
8. **Testes pelo n8n em contêiner** com o Telegram simulado: guardiões estáticos (workflows e compose), contrato
   dos workflows, a suíte de conversa/paridade **existente** reexecutada com um terceiro transporte `n8n`, as duas
   execuções de idempotência (duplicidade e queda no meio da lista) e um ensaio de troca/reversão (R-11 a R-13).
9. **Nenhuma rota nova no backend** e nenhuma mudança de código de produção do backend nas fatias S1 a S4; a
   única mudança de código de produção é a **remoção** da fatia S5 (R-15).

## Technical Context

**Language/Version**: scripts POSIX `sh` (como `local.sh` e `deploy.sh`); workflows em JSON do n8n com nós `Code`
em JavaScript (Node empacotado na imagem do n8n); utilitários `.mjs` executados **dentro** da imagem do n8n;
testes em Java 25 / JUnit 5 (módulo `backend/application`, já existente)

**Primary Dependencies**: `docker.n8n.io/n8nio/n8n:2.43.6` (única dependência nova de runtime); simulador de
Telegram próprio em Node, sem dependências (`infra/telegram-simulator/`, só para testes); WireMock, ArchUnit e
`networknt json-schema-validator` já presentes. **Nenhuma dependência nova de produção no backend.**

**Storage**: o n8n usa SQLite em volume `n8n-data` **somente** para configuração própria (chave de cifra gerada
a partir de `N8N_ENCRYPTION_KEY`, ajustes e, no ambiente local, o usuário do editor). Nenhum dado de domínio,
conversa, texto, execução ou PDF persiste (R-13). Binários em trânsito ficam em `tmpfs`.

**Testing**: `tests-backend` (guardiões estáticos, sem n8n) e um novo `tests-n8n` em `infra/compose.test.yaml`
(contrato dos workflows, conversa e paridade pelo n8n, duplicidade, resiliência), mais `tests-rehearsal` (troca,
reversão e rotação, com o simulador). CI ganha o job `n8n`.

**Target Platform**: contêineres Linux (Docker Engine 27+, Compose v2), local e VPS genérica

**Project Type**: infraestrutura + automação de transporte (workflows), consumindo a API interna do web-service

**Performance Goals**: 95% dos updates com a primeira ação executada em ≤ 2 s a partir da chegada ao n8n
(SC-003); reinício planejado do n8n voltando a receber updates em ≤ 60 s (FR-003, SC-006)

**Constraints**: n8n nunca publicado; nenhum segredo/ID de credencial/dado de aluno em JSON, logs ou histórico;
editor desligado em produção; uma única instância do n8n; o n8n não decide, não valida, não grava e não chama IA;
áudio nunca passa pelo n8n; textos ao usuário só vêm do backend; identificadores em inglês e logs em português

**Scale/Scope**: uma escola, dezenas de professores; dezenas a poucas centenas de ações por dia; 4 workflows;
3 scripts operacionais; 1 contêiner novo em produção

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

Constituição **v2.1.0**. Avaliação **antes** da pesquisa (Pré) e **refeita depois** do design (Pós). Os itens
pedidos explicitamente (II, IV, VIII, contêiner e orquestrador) vêm primeiro e por extenso.

| Princípio / Restrição | Como o plano atende | Pré | Pós |
|---|---|---|---|
| **II. Fonte única da verdade** | (a) **n8n só transporta**: os 4 workflows só recebem, repassam, executam ação e confirmam; o guardião de workflows (R-05) reprova nós de banco, de IA, de código arbitrário de domínio, destinos que não sejam backend e servidor de bots, `Switch`/`If` sobre conteúdo da conversa (só `type` da ação e resultado técnico). (b) **Nada é decidido no n8n**: textos, botões, ordem e compensação vêm do backend; falha é só classificada (tabela do contrato 002 §4) e informada. (c) **Sem estado**: a idempotência é a consulta `GET /actions/{id}`; não há deduplicação própria; histórico de execuções desligado (R-13). (d) **Reiniciar não perde nada**: update é respondido só depois de executar, então a queda faz o Telegram reenviar; lote proativo só é aceito depois de executado, então a queda vira recusa e reentrega (R-07); o estado da conversa vive só no backend. (e) **Autorização é do backend**: o n8n repassa o cabeçalho de segredo e o token; não compara nada além do token de entrega, que é do canal proativo. (f) Só o backend escreve no banco: nenhuma migração, nenhum acesso do n8n ao Postgres. | ✅ | ✅ |
| **IV. Privacidade desde o design** | (a) **Sem porta pública**: `n8n` só em `internal`, `expose` sem `ports`; teste estático do compose reprova qualquer `ports` no serviço (R-11). (b) **HTTPS externo**: o único ponto público segue sendo o Caddy; o salto Telegram → n8n é interno à rede de serviços, igual ao salto atual Telegram → backend. (c) **Segredos fora do Git**: token do bot, token de serviço, token de entrega e chave de cifra vêm do `infra/.env` por `environment:` do compose; guardião reprova valor literal, `credentials` e ID de credencial nos JSON (R-03, FR-009). (d) **Sem dado de aluno no n8n**: execuções não são salvas (`EXECUTIONS_DATA_SAVE_*=none`), binário do PDF em `tmpfs`, logs só técnicos, erros do nó HTTP sem URL (a URL do bot carrega o token) por `neverError` e classificação própria; varredura automatizada de logs e histórico no `tests-n8n` (R-13, FR-031, SC-005). (e) **Áudio nunca no n8n**: o workflow não conhece `getFile` e o guardião reprova a chamada. (f) **Mínimo ao provedor de IA**: n8n não chama IA. (g) Editor desligado em produção (`N8N_DISABLE_UI=true`); API pública do n8n desligada. | ✅ | ✅ |
| **VIII. Fluxo de trabalho** | (a) Branch própria `spec/004-n8n-telegram-transport` criada de `main` atualizada (já feita); a remoção do caminho antigo sai em **outra branch/PR** a partir da `main` atualizada depois da prova (S5). (b) Workflows, `Dockerfile`, scripts de troca/rotação, compose e simulador são **artefatos de automação versionados** (Princípio VIII, último item). (c) Ao concluir cada fatia: commit e PR para a `main`, sem merge pelo autor nem auto-merge; `code-review-graph build` depois de cada PR (CLAUDE.md raiz). (d) A fatia S5 só é aberta com a evidência de paridade anexada (FR-029). (e) Toda mudança de workflow entra por PR e nova imagem — a interface de edição de produção é desligada, então não existe caminho de edição fora do Git (revisão de separação de responsabilidades do Quality Gate). | ✅ | ✅ |
| **Restrição: Containerização e implantação** | O `n8n` é serviço do `compose.yaml` base, sobe pelo mesmo comando único (`local.sh up`, `deploy.sh`) e roda em contêiner nos testes. **Versão fixada** em `FROM` e no `compose.test`; o guardião do compose reprova `latest`, tag ausente e tag flutuante. **Mesma imagem local e produção** (imagem fina com workflows embutidos); variam só `N8N_DISABLE_UI`, porta em loopback do editor e política de reinício (`restart: unless-stopped` em `compose.prod.yaml`). Segredos não entram na imagem (nem o `entrypoint.sh` os conhece). Dependências do servidor seguem só Docker + Compose; os scripts novos rodam HTTP **dentro** do contêiner do n8n, então não exigem `curl`/`jq`/`openssl` no host. Testes de CI em contêiner; o `tests-n8n` monta o socket do Docker **só** em `compose.test.yaml` (R-12, justificado em Complexity Tracking). | ✅ | ✅ |
| **Restrição: Orquestrador de canal (n8n)** | Papel restrito a transporte (acima). **Workflows como código**: `infra/n8n/workflows/*.json`, importados em toda subida e prevalecendo sobre a instância (FR-008); procedimento de exportação de volta documentado e normalizado (`n8n.sh export`); `n8n.sh check` compara instância × repositório. **Sem segredos nem IDs de credencial nos JSON**: nenhum nó usa `credentials`; segredos por `$env` com lista fechada de nomes permitidos (R-03). **Um receptor de updates**: o guardião exige exatamente um nó `Webhook` com o caminho `telegram-update`. | ✅ | ✅ |
| **I. Domínio independente de canal e fornecedor** | Nenhuma linha do núcleo muda. O n8n é adaptador de transporte fora do processo; nomes de fornecedor continuam proibidos no núcleo (regra ArchUnit da 002 mantida). Paridade Web × Telegram inalterada: mesmos casos de uso, mesmos testes. | ✅ | ✅ |
| **III. Humano no controle da IA** | Sem mudança no ciclo de vida do relatório; a aprovação continua amarrada à versão vigente pelo backend; o n8n não vê nem altera esse dado. | ✅ | ✅ |
| **V. Qualidade obrigatória** | Contrato testado nos dois lados (a 002 testa o backend; esta feature testa o consumidor real); suíte de conversa/paridade existente reexecutada **sem alterar expectativa**; cobertura do núcleo intocada (≥ 90%); CI bloqueia o merge (job `n8n` novo). | ✅ | ✅ |
| **VI. Nomenclatura e idioma** | Workflows `telegram-inbound`, `channel-delivery`, `execute-actions`, `transport-health`; scripts `telegram-webhook.sh`, `rotate-service-token.sh`, `n8n.sh`; variáveis em inglês; mensagens dos scripts e logs em português; **nenhum texto ao professor no n8n**. Guardião de idioma varre os novos YAML, JSON, shell e `.env.example`; termos novos entram antes no `glossary.json` (R-16). | ✅ | ✅ |
| **VII. Simplicidade e foco** | Cada acréscimo está justificado em Complexity Tracking; sem fila, sem banco externo para o n8n, sem modo `queue`/workers, sem Redis, sem nova rota. O ensaio e o simulador são o mínimo para provar US5 sem depender do Telegram real. | ✅ | ✅ (acréscimos justificados) |
| **IX. Identidade visual** | Sem alteração de UI, PDF ou texto do bot: o n8n repassa texto e botões como o backend os definiu. | ✅ | ✅ |
| **Quality gate: paridade Web × Telegram** | Nenhum desvio; a suíte de paridade passa a rodar também pelo n8n. | ✅ | ✅ |
| **Quality gate: separação de responsabilidades em orquestração** | Contrato dos workflows ([contracts/n8n-workflows.md](./contracts/n8n-workflows.md)) lista, por passo, o que é transporte; o guardião é a revisão mecânica. | ✅ | ✅ |

**Resultado**: aprovado antes e depois do desenho, **sem violação da constituição e sem emenda**. Pendências
registradas, nenhuma bloqueante: (1) a divergência R-02 sobre o Caddy (decisão do dono do produto, abaixo);
(2) as verificações de comportamento do n8n 2.x marcadas como *spike* em research.md (R-04, R-06, R-13), a serem
fechadas nas primeiras tarefas antes de qualquer outra.

## Project Structure

### Documentation (this feature)

```text
specs/004-n8n-telegram-transport/
├── plan.md                                  # Este arquivo
├── research.md                              # Fase 0: decisões R-01 a R-18 e spikes S-1 a S-6
├── data-model.md                            # Fase 1: inventário de workflows, configuração, estados da troca, matriz de testes
├── quickstart.md                            # Fase 1: validação V1 a V16
├── contracts/
│   ├── n8n-workflows.md                     # comportamento normativo dos 4 workflows e do guardião
│   ├── n8n-endpoints.openapi.yaml           # endpoints internos expostos pelo n8n (webhooks e saúde)
│   └── transport-scripts.md                 # contrato de linha de comando dos 3 scripts
└── tasks.md                                 # Fase 2 (/speckit-tasks; não criado por este comando)
```

### Source Code (repository root)

Marcação: **(N)** novo, **(M)** modificado, **(R)** removido. Fatia entre colchetes (S1 a S5, ver "Fatiamento").

```text
infra/
├── n8n/                                       # (N)[S1] estrutura nova — nasce com CLAUDE.md (CLAUDE.md raiz)
│   ├── CLAUDE.md                              # (N)[S1] o que é, regras, procedimento de exportação
│   ├── Dockerfile                             # (N)[S1] FROM docker.n8n.io/n8nio/n8n:2.43.6 + COPY
│   ├── entrypoint.sh                          # (N)[S1] import + publish + exec n8n start
│   ├── ops/
│   │   ├── probe.mjs                          # (N)[S1] cliente HTTP sem dependências (status + corpo), usado pelos scripts
│   │   ├── normalize-workflow.mjs             # (N)[S1] normaliza exportação (remove campos voláteis; reprova credencial)
│   │   └── generate-token.mjs                 # (N)[S3] gera token aleatório (base64url) sem openssl no host
│   └── workflows/
│       ├── telegram-inbound.json              # (N)[S1] único receptor de updates do Telegram
│       ├── channel-delivery.json              # (N)[S1] entrega proativa (backend → n8n)
│       ├── execute-actions.json               # (N)[S1] executor compartilhado (sub-workflow)
│       └── transport-health.json              # (N)[S1] saúde: live (local) e deep (backend + API de bots)
├── telegram-simulator/                        # (N)[S2] Telegram + API de bots simulados (só testes) — nasce com CLAUDE.md
│   ├── CLAUDE.md, Dockerfile, server.mjs
├── scripts/
│   ├── n8n.sh                                 # (N)[S1] health | check | export | import | reset | logs
│   ├── telegram-webhook.sh                    # (N)[S3] status | switch | rollback | verify | apply
│   ├── rotate-service-token.sh                # (N)[S3] start | finish | abort | status
│   ├── local.sh                               # (M)[S1,S3] sobe o n8n; `tests n8n`; `apply` após saudável
│   ├── deploy.sh                              # (M)[S1,S3] mesmo
│   └── README.md                              # (M)[S1,S3] seções do n8n, da troca, da rotação
├── compose.yaml                               # (M)[S1] serviço `n8n`; backend recebe URL do orquestrador e timeouts
├── compose.dev.yaml                           # (M)[S1] editor ligado e publicado só em 127.0.0.1
├── compose.prod.yaml                          # (M)[S1] `restart: unless-stopped`, `N8N_DISABLE_UI=true`
├── compose.test.yaml                          # (M)[S2,S3] n8n-test, tests-n8n, tests-rehearsal, simulador
├── caddy/Caddyfile                            # (M)[S1] só comentário: o n8n não é roteado (R-02)
├── .env.example                               # (M)[S1,S3] variáveis do n8n; limpa "o compose é da feature 004"
├── CLAUDE.md, README.md                       # (M)[S1,S3,S5]
backend/application/src/test/java/…/           # (M/N)[S1,S2,S3] só testes
├── architecture/WorkflowGuardTest.java        # (N)[S1] guardião dos JSON de workflow (offline)
├── architecture/ComposeGuardTest.java         # (N)[S1] guardião do compose (imagens, portas, rede, restart)
├── support/IntegrationBase.java               # (M)[S2] porta HTTP fixa opcional no modo n8n
├── support/TelegramConversationBase.java      # (M)[S2] terceiro transporte `n8n` e modo de duplicidade
├── support/OrchestratorDeliveryBase.java      # (M)[S2] entrega proativa real ao n8n no modo n8n
└── transport/n8n/                             # (N)[S2] N8nInboundWorkflowIT, N8nDeliveryWorkflowIT, N8nFailureHandlingIT,
                                               #       N8nIdempotencyIT, N8nResilienceIT, N8nPrivacyIT, N8nLatencyIT
.github/workflows/ci.yml                       # (M)[S2] job `n8n`
.github/CLAUDE.md                              # (M)[S2]
CLAUDE.md (raiz)                               # (M)[S1] linha `infra/n8n` e `infra/telegram-simulator` no mapa
specs/CLAUDE.md                                # (M)[S1] linha da 004 já existe; ajuste de texto se preciso
specs/001-fluxo-relatorios/{research,plan,quickstart,tasks}.md, contracts/telegram-conversa.md   # (M)[S4] FR-032
.specify/memory/glossary.json                  # (M)[S1] termos novos antes do uso
backend/application/src/main/…                 # (R)[S5] remoção do caminho antigo (inventário em R-15)
```

**Structure Decision**: o n8n entra como **serviço de infraestrutura** (`infra/n8n/`) e não como módulo do
`backend/`: ele é um adaptador de transporte fora do processo, sem Java. Os testes ficam no módulo
`backend/application`, que já tem WireMock, o esquema v1, as bases de conversa e o executor simulado; assim a
suíte existente é **reaproveitada**, não reescrita. O simulador de Telegram é uma pasta própria de teste porque
o ensaio de troca precisa de comportamento com estado (registro do webhook, retenção e reenvio de update) que o
WireMock sozinho não dá.

## Fatiamento e ordem de entrega

Cada fatia é um PR para a `main` (merge só pelo usuário). S1 a S4 saem na branch desta feature; **S5 sai em
branch nova** (`spec/004-remove-direct-telegram-path`) criada da `main` atualizada **depois** da prova de paridade.

| Fatia | Conteúdo | Pré-requisito | Saída verificável |
|---|---|---|---|
| **S0 — Spikes** | fechar S-1 a S-6 de research.md em ambiente descartável | — | decisões R-04, R-06, R-13 confirmadas ou ajustadas no próprio research.md |
| **S1 — Serviço e workflows** | imagem fina, workflows, `entrypoint.sh`, compose, `n8n.sh`, guardiões (workflow e compose), `.env.example`, docs de `infra/` | S0 | `local.sh up` sobe o n8n saudável; `n8n.sh check` sem divergência; guardiões verdes; **o webhook ainda não foi trocado** (caminho antigo intacto, FR-027) |
| **S2 — Testes pelo n8n** | `n8n-test`, `tests-n8n`, terceiro transporte, duplicidade, resiliência, privacidade, latência, job de CI | S1 | suíte de conversa/paridade verde pelo n8n sem mudar expectativa (SC-001/SC-002/SC-011) |
| **S3 — Troca, reversão e rotação** | `telegram-webhook.sh`, `rotate-service-token.sh`, simulador, `tests-rehearsal`, integração com `local.sh`/`deploy.sh` | S2 | ensaio verde: troca, reversão, troca de novo, rotação, tudo sem perda nem duplicidade (SC-007) |
| **S4 — Documentação da 001 e de `infra/`** | FR-032 (pode ir junto com S1–S3 se preferir PRs menores) | S1 | nenhum documento da 001 afirma, como situação atual, que não há n8n |
| **Operação** | mantenedor troca o webhook em produção e **observa 7 dias** (FR-029) | S1–S4 mesclados | evidência de paridade (modelo em data-model.md §5) |
| **S5 — Remoção do caminho antigo** | inventário R-15, **última fase** | evidência de paridade completa **e** aprovação registrada do dono do produto | suíte inteira verde sem alterar expectativa; sem rota, classe, propriedade ou variável órfã (SC-010) |

## Complexity Tracking

> Não há violação da constituição. A tabela registra os acréscimos que o Princípio VII manda justificar.

| Acréscimo | Por que é necessário | Alternativa mais simples rejeitada porque |
|---|---|---|
| Imagem fina `reports-n8n` (Dockerfile + `entrypoint.sh`) em vez da imagem oficial pura | "mesmas definições de imagem" local e produção (constituição) com workflows embutidos e importação na subida sem *bind mount* do host | montar a pasta do repositório na VPS depende do diretório do host e deixa a imagem diferente da testada; baixar workflows na subida adiciona dependência de rede |
| 4 workflows (um deles sub-workflow compartilhado) | FR-012 e FR-021 exigem dois pontos de entrada com as **mesmas** regras de execução; duplicar o executor dobraria a superfície que o guardião e os testes cobrem | um workflow só com dois gatilhos mistura as respostas HTTP (Telegram × backend) e dificulta o guardião de "um receptor de updates" |
| Nós `Code` (JavaScript) para montar corpo de chamada e classificar erro | a tabela "erro do Telegram → resultado" (contrato 002 §4) e o teclado `rows → inline_keyboard` não se expressam bem só com nós visuais | só nós visuais multiplicam `If`/`Switch` sobre texto de erro, que o guardião teria de liberar; o `Code` é limitado a funções puras sem `$env`, sem rede e sem `require` |
| Confirmação **por ação**, logo após executar | minimiza duplicidade após queda (no máximo a ação em voo repete) e libera o portão de ordem da 002 mais cedo | confirmar o lote no fim perde todas as confirmações numa queda e repete a lista inteira |
| Aceite do lote proativo após execução (R-07), com timeout de resposta de 30 s no backend por compose | cumpre FR-023 sem mudar o contrato da 002 | aceitar de imediato exigiria reentrega de ação `DELIVERED` não confirmada (mudança aditiva no backend e na máquina de estados da 002) |
| `telegram-webhook.sh` com `switch`, `rollback`, `verify`, `status`, `apply` | FR-024 a FR-026 pedem procedimento único, reversível e verificável | passos manuais em runbook não são ensaiáveis nem testáveis em CI |
| `rotate-service-token.sh` | FR-004/US do token na spec (edge case) e pedido explícito; usa os dois tokens da 002 | rotação manual de `.env` tem janela de recusa por erro de ordem |
| Simulador de Telegram (`infra/telegram-simulator/`) e `tests-rehearsal` | provar US5 (troca/reversão sem perda nem duplicidade) exige registro de webhook, retenção e reenvio de update com estado | WireMock não guarda estado do webhook nem reenvia update; testar contra o Telegram real não roda em CI |
| Socket do Docker montado no `tests-n8n` (somente `compose.test.yaml`) | FR-028(b): derrubar e subir o n8n no meio de uma lista de ações | derrubar o processo por dentro (fault injection no WireMock) não reproduz a queda do contêiner; Testcontainers também exige o socket e acrescenta dependência |
| Guardiões estáticos novos (`WorkflowGuardTest`, `ComposeGuardTest`) | FR-009, FR-010, FR-011 e a restrição de imagem fixada exigem verificação automática a cada build | revisão manual de JSON de workflow não escala e é onde segredo escapa |
| `N8N_BLOCK_ENV_ACCESS_IN_NODE=false` | única forma de usar `$env` no n8n 2.x (R-03); o contêiner só recebe as 4 variáveis secretas que ele precisa | credenciais do n8n exigem importar credenciais a partir do ambiente e referenciá-las por ID/nome — o que a constituição proíbe nos JSON — ou estado extra a recriar |

## Aviso ao dono do produto (decisão a confirmar)

O pedido diz que "só o Caddy expõe HTTPS e encaminha o webhook do Telegram ao n8n". Com o **servidor local da API
de bots** (`--local`, já em uso, necessário para o limite de 50 MB), é esse servidor — dentro da rede interna —
quem chama o webhook; o Telegram na nuvem nunca chama o nosso domínio. Por isso o plano **mantém o Caddy como
única porta pública, mas não cria rota dele para o n8n**: uma rota pública `/…/telegram-update` não teria quem a
usasse e aumentaria a superfície (Princípio IV). Se a intenção for outra (por exemplo, voltar à API de bots na
nuvem), isso muda R-02, o Caddyfile e a restrição de 50 MB, e deve ser decidido **antes** de `/speckit-tasks`.
