# Quickstart: validar o n8n como único transporte do canal Telegram

**Feature**: `004-n8n-telegram-transport` | Referências: [spec.md](./spec.md), [plan.md](./plan.md),
[contracts/n8n-workflows.md](./contracts/n8n-workflows.md), [contracts/transport-scripts.md](./contracts/transport-scripts.md),
[data-model.md](./data-model.md)

Guia de **validação e operação**, não de implementação. Os comandos existem depois das fatias indicadas entre
colchetes. Todos rodam da raiz do repositório, só com Docker (Princípio "Containerização"). Detalhes de
cada comando estão nos contratos; aqui só se diz o que rodar e o que esperar.

## Pré-requisitos

- Docker Engine 27+ com Compose v2; `infra/.env` criado de `infra/.env.example` (ver `infra/README.md`).
- Para o ambiente local com bot real: `TELEGRAM_ENABLED=true`, `TELEGRAM_BOT_TOKEN`, `TELEGRAM_API_ID`,
  `TELEGRAM_API_HASH`, `TELEGRAM_WEBHOOK_SECRET` e, **novas**, `N8N_ENCRYPTION_KEY` (`openssl rand -hex 32`),
  `TELEGRAM_CHANNEL_CURRENT_TOKEN` e `TELEGRAM_CHANNEL_ORCHESTRATOR_TOKEN` (`openssl rand -base64 32`, valores
  diferentes). Use um **bot de testes** fora da produção.
- Os cenários V4 a V12 e V14 não precisam de bot real: o Telegram é simulado.

## V1. O n8n sobe com o ambiente e com os workflows do repositório [S1]

```bash
sh infra/scripts/local.sh up
sh infra/scripts/n8n.sh health
sh infra/scripts/n8n.sh check
```

Esperado: o `n8n` aparece `healthy` em `local.sh status`; `health` imprime `{"status":"UP"}`; `check` termina sem
divergência. A imagem é `reports-n8n`, construída sobre `docker.n8n.io/n8nio/n8n:2.43.6`. **Ninguém abre a
interface do n8n.** Nenhuma porta do `n8n` aparece em `docker compose … ps` fora de `127.0.0.1:5678` (só no local).

## V2. Recriar do zero produz o mesmo [S1]

```bash
sh infra/scripts/n8n.sh reset      # confirmação digitada: apagar
sh infra/scripts/n8n.sh check
```

Esperado: o conjunto ativo é o mesmo (4 workflows) e `check` sem divergência (US1, cenário 2; SC-004).

## V3. Edição manual na instância é descoberta e descartada [S1]

1. Abra `http://localhost:5678` no navegador do host (publicado só em 127.0.0.1) (editor, só no local; crie o usuário local na primeira vez),
   mude qualquer nó de `execute-actions` e salve.
2. `sh infra/scripts/n8n.sh check` — **reprova**, apontando o workflow e o caminho do campo.
3. `docker compose -f infra/compose.yaml -f infra/compose.dev.yaml restart n8n` e `n8n.sh check` — **volta a passar**.

## V4. Guardiões estáticos [S1]

```bash
sh infra/scripts/local.sh tests backend
```

Esperado (entre os testes do módulo): `WorkflowGuardTest` e `ComposeGuardTest` verdes. Para ver a reprovação,
acrescente de propósito a um JSON: um nó `credentials`, um segundo `Webhook` com `path = telegram-update`, um
nó de banco, uma URL fora de `BACKEND_INTERNAL_URL`/`TELEGRAM_API_URL`, um token literal; ou `latest` no
`FROM`, `ports` no `n8n` do `compose.prod.yaml`. Cada um reprova com mensagem em português que cita o arquivo e
o caminho, nunca o valor.

## V5. Contrato dos workflows pelo n8n real [S2]

```bash
sh infra/scripts/local.sh tests n8n
```

Esperado: `N8nInboundWorkflowIT`, `N8nDeliveryWorkflowIT`, `N8nFailureHandlingIT` e `N8nHealthIT` verdes,
cobrindo os cenários N1 a N16 de `contracts/n8n-workflows.md`.

## V6. Conversa e paridade completas pelo n8n [S2]

É a mesma execução de V5 (a suíte `n8n` roda, em ordem: contrato, conversa e roteiros de paridade com
`-Dtelegram.transport=n8n`, duplicidade, resiliência, privacidade, latência). Para só ver a conversa:

```bash
docker compose -f infra/compose.test.yaml run --rm tests-n8n \
  sh -c "mvn -B -ntp -Pn8n verify -Dtelegram.transport=n8n -Dit.test='*ConversationIT,*ParityScriptIT'"
```

Esperado: **todos** os cenários de conferência, registro de aula, retomada, semestral, histórico, vinculação,
início/busca e os roteiros de paridade passam **sem alterar nenhuma expectativa** (SC-001, SC-002).

## V7. Idempotência: updates e lotes em duplicidade [S2]

Já incluída em V5/V6 como segunda passada da suíte (`-Dtelegram.n8n.duplicates=true`). Esperado: a transcrição do
chat de cada cenário é idêntica à da passada sem duplicidade (SC-011).

## V8. Queda do n8n no meio de uma lista [S2]

`N8nResilienceIT`, dentro de V5. Esperado: com o n8n derrubado e religado no meio de uma lista de ações —
tanto de update quanto de lote proativo — as ações restantes chegam **uma vez** e as já executadas não se
repetem; o reinício planejado volta a receber updates em até 60 s (SC-006). Vale também: backend fora do ar ao
chegar um update ⇒ o n8n devolve `503` e o professor é respondido quando o backend volta.

## V9. Privacidade: nada de aluno no n8n [S2]

`N8nPrivacyIT`, dentro de V5. Esperado: zero ocorrência de token, segredo, nome de aluno, texto de relatório ou
bytes de PDF nos logs do contêiner, na exportação dos workflows e nas execuções (zero execuções salvas)
(SC-005). Manualmente, em qualquer momento: `sh infra/scripts/n8n.sh logs` não mostra conteúdo de mensagem.

## V10. O n8n não é público [S1/S2]

```bash
sh infra/scripts/local.sh up
curl -sk -X POST https://localhost/webhook/telegram-update -o /dev/null -w '%{http_code}\n'
curl -sk https://localhost/healthz -o /dev/null -w '%{http_code}\n'
```

Esperado: respostas do SPA/Caddy (não as do n8n). `N8nExposureIT` automatiza isto na pilha de testes, e
`ComposeGuardTest` garante que o `n8n` não tem `ports` em produção.

## V11. Ensaio da troca e da reversão (Telegram simulado) [S3]

```bash
sh infra/scripts/local.sh tests rehearsal
```

Esperado: `status` → `DIRECT`; `switch` sob tráfego → `N8N`; `rollback` → `DIRECT`; `switch` de novo; cada update
enviado ao longo do ensaio tem **exatamente uma** resposta na transcrição do simulador; `switch` com o n8n parado
é recusado (`[N8N_NOT_HEALTHY]`) sem mexer no webhook; `verify` com segredo errado reprova com `3`.

## V12. Rotação do token de serviço (Telegram simulado) [S3]

Dentro de V11, e manualmente:

```bash
sh infra/scripts/rotate-service-token.sh status
sh infra/scripts/rotate-service-token.sh start
sh infra/scripts/rotate-service-token.sh finish
```

Esperado: `start` ⇒ estado `DUAL` (token novo e atual aceitos, n8n usando o novo, `deep` ok); `finish` ⇒ `IDLE`
(novo aceito, antigo recusado com `401`). Nenhuma recusa de mensagem durante a troca (sem janela). `abort`
devolve ao estado anterior.

## V13. Trocar o webhook do bot de testes de verdade [S3, manual, uma vez]

```bash
sh infra/scripts/telegram-webhook.sh status
sh infra/scripts/telegram-webhook.sh switch --chat-id <seu chat id>
```

Esperado: `✔` em todas as verificações, e no Telegram a resposta do `/ajuda` com o mesmo texto de hoje. Mande
mensagens, toque nos botões, grave um áudio curto, peça o PDF: o professor não percebe diferença. Para
voltar: `sh infra/scripts/telegram-webhook.sh rollback`.

## V14. Exportar um workflow de volta para o repositório [S1]

1. `sh infra/scripts/local.sh up` e edite no editor local (V3, passo 1).
2. `sh infra/scripts/n8n.sh export`
3. `git diff infra/n8n/workflows/` — só mudanças reais, sem campos voláteis nem `credentials`.
4. `sh infra/scripts/n8n.sh check` passa; `sh infra/scripts/local.sh tests backend` e `tests n8n` passam; abra o PR.

## V15. Documentação [S4]

Conferir que `research.md` R-04, `plan.md`, `quickstart.md`, `tasks.md` e `contracts/telegram-conversa.md` da 001
descrevem o transporte pelo n8n, preservando o registro histórico da decisão original, e que `infra/README.md`,
`infra/scripts/README.md`, `infra/CLAUDE.md`, `.env.example`, `specs/CLAUDE.md` e o `CLAUDE.md` da raiz não dizem
mais "o n8n ainda será incluído" nem "o compose é da feature 004" (FR-032, SC-010).

## V16. Evidência de paridade e remoção do caminho antigo [Operação + S5]

1. Troque em produção (`switch`) e anote `switchedAt`; o bot continua com `rollback` disponível.
2. Durante **7 dias corridos**, colete diariamente os quatro sinais, sem dado de aluno:
   - updates sem resposta: updates recebidos pelo n8n menos respostas executadas (log técnico do backend por
     `updateId`);
   - respostas duplicadas: mais de uma ação `EXECUTED` para o mesmo `actionId`/`updateId`;
   - ações proativas `PERMANENTLY_FAILED` por causa do transporte (métrica já existente da 002 e tabela
     `channel_action`);
   - toques sem `CALLBACK_REPLY` executado.
3. Registre relatos de professores. Preencha `specs/004-n8n-telegram-transport/evidence/parity.md` (campos em
   data-model §5) e peça a aprovação ao dono do produto.
4. Só com tudo zerado e aprovado: crie a branch da S5 a partir da `main` atualizada, remova o caminho antigo
   (R-15), rode `sh infra/scripts/local.sh tests all` e abra o PR com a evidência anexada. Se qualquer item falhar:
   **a S5 não é aberta**; `rollback` segue disponível.

## Troubleshooting

| Sintoma | Causa provável | O que fazer |
|---|---|---|
| `n8n` fica `unhealthy` logo após `up` | importação de workflow inválido derrubou o `entrypoint` | `sh infra/scripts/n8n.sh logs`; corrija o JSON; `up --build` |
| `switch` recusa com `[DEEP_HEALTH_FAILED]` | token de serviço do n8n não confere com o do backend, ou token do bot errado | confira `TELEGRAM_CHANNEL_CURRENT_TOKEN` e `TELEGRAM_BOT_TOKEN`; `n8n.sh health --deep` mostra qual componente falhou |
| `verify` reprova com `[SECRET_MISMATCH]` | `TELEGRAM_WEBHOOK_SECRET` foi trocado depois do último `setWebhook` | `telegram-webhook.sh apply` |
| professor sem resposta e o `getWebhookInfo` mostra `last_error_message` | n8n fora do ar ou backend recusando | `local.sh status`, `n8n.sh logs`; o Telegram reenvia sozinho quando voltar |
| mensagem duplicada no chat | execução de lote lenta (> 30 s) sobreposta pela reentrega (R-07) | `n8n.sh logs`; conferir lentidão da API de bots; conta como sinal de divergência na observação |
| `check` aponta workflow "extra" na instância | workflow removido do repositório | `n8n.sh reset` |
