# Contrato: scripts operacionais do transporte

**Feature**: `004-n8n-telegram-transport` | Referências: [spec.md](../spec.md) FR-006, FR-008, FR-024 a FR-026,
[research.md](../research.md) R-08, R-09, R-10, R-18, [data-model.md](../data-model.md) §2 e §4

Três scripts POSIX `sh` em `infra/scripts/`, no estilo de `local.sh` e `deploy.sh` (`set -eu`, rodam da raiz do
repositório, cabeçalho de uso no próprio arquivo, mensagens em **português com acentuação**). O host só precisa
de Docker: toda chamada HTTP sai de dentro do contêiner do n8n (`docker compose exec -T n8n node /opt/ops/probe.mjs …`),
com segredos por `-e`, nunca na linha de comando, e **nenhum script imprime token, segredo, URL com segredo ou
o `.env`**.

## 0. Convenções comuns

| Item | Regra |
|---|---|
| Ambiente | `--env local` (padrão; `compose.yaml` + `compose.dev.yaml`) ou `--env prod` (`compose.yaml` + `compose.prod.yaml`) |
| Entrada | `infra/.env` (mesmas chaves de `local.sh`); ausente ⇒ erro e saída `1` |
| Confirmação | alterações em `--env prod` pedem confirmação digitada (`trocar`, `reverter`, `rotacionar`); `--yes` pula (CI e ensaio). Em `local` não pedem |
| `--dry-run` | mostra o que mudaria (chaves e comandos, sem valores secretos) e não altera nada |
| Códigos de saída | `0` sucesso · `1` pré-condição não atendida (nada foi alterado) · `2` uso incorreto · `3` alteração feita mas a **verificação reprovou** (o script já restaurou o estado anterior quando aplicável; diz isso) · `4` falha inesperada |
| Saída | uma linha por passo (`✔`/`✘` + frase curta), resumo final; códigos de motivo estáveis em inglês entre colchetes ao fim da linha de erro (`[N8N_NOT_HEALTHY]`) para o ensaio conferir |
| Idempotência | repetir um comando que já está no estado desejado não altera nada e sai `0` |

## 1. `telegram-webhook.sh`

```text
sh infra/scripts/telegram-webhook.sh <command> [--env local|prod] [--yes] [--dry-run] [--chat-id <id>]
```

| Comando | O que faz | Altera |
|---|---|---|
| `status` | mostra o estado do transporte (`DIRECT`/`N8N`/`INCONSISTENT`), o webhook registrado (`url`, `pending_update_count`, `last_error_message` truncado), a saúde do n8n e do backend, e quem entrega as proativas | nada |
| `switch` | leva o transporte para o n8n (data-model §4.1). Sequência: pré-condições → guarda o estado anterior → ajusta `.env` → recria **somente** o `backend` → `setWebhook` → `verify`. Em reprovação do `verify`, **restaura** e sai `3` | `.env`, contêiner `backend`, webhook |
| `rollback` | leva o transporte de volta ao caminho antigo; mesma verificação. Existe enquanto o caminho antigo existir (some na fatia S5) | idem |
| `verify` | verificação pós-troca, somente leitura exceto a sonda; reprova (`3`) se o destino do webhook, a entrega proativa ou a ida e volta divergirem do estado esperado | nada |
| `apply` | registra o webhook do **estado atual** (`setWebhook` idempotente) e roda o `verify`; usado por `local.sh up` e `deploy.sh` depois de tudo saudável (e, depois da S5, é o único dono do registro) | webhook |

### 1.1 Pré-condições do `switch` (códigos de motivo)

| Verificação | Código | Efeito se falhar |
|---|---|---|
| contêiner `n8n` existe e está `healthy` | `N8N_NOT_HEALTHY` | recusa, explica o que falta, **não altera o webhook** (US5.4) |
| `GET transport-health/live` = `200` (workflows publicados) | `WORKFLOWS_NOT_ACTIVE` | recusa |
| `GET transport-health/deep` = `200` (`backend:ok`, `botApi:ok`) | `DEEP_HEALTH_FAILED` | recusa |
| `TELEGRAM_WEBHOOK_SECRET` casa `^[A-Za-z0-9_-]{1,256}$` | `INVALID_WEBHOOK_SECRET` | recusa; aviso (sem recusa) se tiver menos de 32 caracteres |
| `TELEGRAM_CHANNEL_CURRENT_TOKEN` e `TELEGRAM_CHANNEL_ORCHESTRATOR_TOKEN` preenchidos e diferentes entre si | `TOKENS_MISSING` | recusa |
| `TELEGRAM_ENABLED=true` e `TELEGRAM_BOT_TOKEN` preenchido | `TELEGRAM_DISABLED` | recusa |
| estado atual é `DIRECT` | — | já está em `N8N`: sai `0` sem alterar (idempotente) |

### 1.2 `verify` (todas as verificações; falha de qualquer uma ⇒ saída `3`)

| # | Verificação | Código de motivo |
|---|---|---|
| 1 | `getWebhookInfo`: `url` igual à esperada para o estado (`http://n8n:5678/webhook/telegram-update` ou `http://backend:8080/telegram/webhook`); `allowed_updates` = `message`, `callback_query`; sem `last_error_message` posterior à troca | `WEBHOOK_MISMATCH` |
| 2 | **sonda de segredo, negativa**: `POST` ao webhook com segredo diferente responde `>= 400` | `SECRET_NOT_ENFORCED` |
| 3 | **sonda de segredo, positiva e sem efeito**: `POST` com o segredo do `.env` e um update de tipo não suportado (`update_id` fora da faixa real) responde `2xx` | `SECRET_MISMATCH` |
| 4 | em `N8N`: `transport-health/deep` = `200`; em `DIRECT`: n/a | `DEEP_HEALTH_FAILED` |
| 5 | quem entrega as proativas, lido do ambiente do contêiner do `backend` (apenas o nome do modo, nunca valores secretos), bate com o estado (`ORCHESTRATOR`/`DIRECT`) | `DELIVERY_MODE_MISMATCH` |
| 6 | só com `--chat-id` (ou `TELEGRAM_VERIFY_CHAT_ID`): **ida e volta real** — injeta no webhook um update `ajuda` desse chat e confere que o backend o aceitou (`2xx`); a resposta ao chat é a do catálogo do backend, o script não escreve texto | `ROUND_TRIP_FAILED` |

A sonda 3 usa um `update_id` alto e fixo reservado (`2147000000 + n`), de um tipo que o backend ignora
(`actions: []`), então não cria resposta, não altera conversa e não entra em nenhuma contagem de professor.

### 1.3 Como o `setWebhook` é feito

`setWebhook` com `url`, `secret_token` (o do `.env`, já validado), `allowed_updates=["message","callback_query"]` e
`max_connections=10`, os mesmos parâmetros de `TelegramConfig.register`. Resposta `ok:true` obrigatória. O
backend, ao ser recriado, registra a **mesma** URL pelo caminho já existente (a variável `TELEGRAM_WEBHOOK_URL`
agora vem do `.env`), então os dois registros convergem e não brigam.

### 1.4 Garantias de "um caminho por vez"

- as três chaves de estado mudam juntas ou o script restaura todas (data-model §4.1);
- o `backend` é recriado **uma vez** por troca; durante a recriação, updates pendentes ficam retidos pelo
  servidor de bots e são reenviados (sem perda); a deduplicação por `update_id` evita reprocessar;
- o script nunca roda `deleteWebhook` nem polling; `rollback` é o espelho de `switch`.

## 2. `rotate-service-token.sh`

```text
sh infra/scripts/rotate-service-token.sh <command> [--env local|prod] [--yes] [--dry-run]
```

| Comando | Estado exigido | Sequência | Estado final |
|---|---|---|---|
| `status` | qualquer | mostra `IDLE`/`DUAL` e se os tokens estão preenchidos (sem imprimi-los) | — |
| `start` | `IDLE` | gera o token novo (`ops/generate-token.mjs`) → grava `TELEGRAM_CHANNEL_NEXT_TOKEN` → recria `backend`, espera `healthy` → recria `n8n`, espera `healthy` → verifica: **novo aceito**, **atual ainda aceito**, `transport-health/deep` ok | `DUAL` |
| `finish` | `DUAL` | `CURRENT ← NEXT`, `NEXT` vazio → recria `backend`, espera `healthy` → verifica: **novo aceito**, **antigo recusado** (`401`) | `IDLE` |
| `abort` | `DUAL` | esvazia `NEXT` → recria `backend` e `n8n` → verifica que o atual é aceito e o novo (descartado) é recusado | `IDLE` |

Verificação de aceite sem rota nova: `GET /internal/v1/channel/telegram/actions/u0-0` com o token sob teste —
`404` = autenticado, `401` = recusado. O token sob teste é passado por `-e` ao `probe.mjs`.

Códigos de motivo: `NOT_IN_IDLE`, `NOT_IN_DUAL`, `N8N_NOT_HEALTHY`, `BACKEND_NOT_HEALTHY`, `NEW_TOKEN_REJECTED`,
`OLD_TOKEN_STILL_ACCEPTED`, `CURRENT_TOKEN_REJECTED`. Falha no meio de `start` deixa o estado em `DUAL` (seguro:
os dois tokens são aceitos) e o script orienta `finish` ou `abort`. Nunca há janela de recusa para o n8n.

`.env` é reescrito de forma atômica (arquivo temporário + `mv`, permissões preservadas), guardando a cópia
anterior em `infra/.env.previous` (ignorada pelo Git, como o `.env`). O script avisa que a cópia contém segredos.

## 3. `n8n.sh`

```text
sh infra/scripts/n8n.sh <command> [--env local|prod] [--deep]
```

| Comando | O que faz | Altera |
|---|---|---|
| `health [--deep]` | `live` (padrão) ou `deep`; imprime o corpo (sem segredos) e sai `0`/`1` | nada |
| `check` | exporta os workflows da **instância** (`n8n export:workflow --backup`), normaliza e compara com `infra/n8n/workflows/`; lista só nomes de workflow e caminhos de campo divergentes, extras na instância e ausentes; reprova (`1`) se houver | nada |
| `export` | exporta da instância, normaliza (`ops/normalize-workflow.mjs`) e **grava** em `infra/n8n/workflows/`; recusa e não grava se achar `credentials`, token ou URL com segredo | arquivos do repositório |
| `import` | reimporta e republica os workflows da imagem **sem** reiniciar a pilha (equivale ao início do `entrypoint.sh`); útil depois de `up --build` no local | instância |
| `reset` | apaga o volume `n8n-data` com confirmação digitada (`apagar`) e sobe de novo o n8n (para remover workflows que saíram do repositório) | volume |
| `logs [N]` | últimas `N` linhas (padrão 200) do contêiner, já filtradas pelo `ops` para esconder qualquer coisa parecida com token | nada |

`check` e `export` usam o mesmo normalizador (R-18), por isso uma exportação recém-gravada passa no `check`.

## 4. Integração com `local.sh` e `deploy.sh`

| Mudança | Onde |
|---|---|
| `services()` inclui `n8n` quando `TELEGRAM_ENABLED=true`; `wait_until_healthy` espera também `n8n healthy` | `local.sh` |
| depois de saudável: `telegram-webhook.sh apply` (a partir da S3; antes disso o backend registra sozinho) | `local.sh up`, `deploy.sh` |
| nova suíte `tests n8n` (e `all` passa a incluí-la); novo `tests rehearsal` | `local.sh` |
| `require_env` exige `N8N_ENCRYPTION_KEY` com Telegram habilitado, e os tokens do canal quando `TELEGRAM_TRANSPORT=N8N` | `local.sh` |
| `deploy.sh` não troca o transporte: troca é sempre uma ação deliberada do mantenedor | `deploy.sh` |

## 5. O que os testes cobrem destes scripts

`tests-rehearsal` (R-12) roda `status`, `switch`, `verify`, `rollback`, `switch` com tráfego contínuo, `switch`
com o n8n parado (espera `1` e webhook intacto), `verify` com segredo errado (espera `3`), `rotate-service-token.sh
start/finish/abort` com tráfego, e `n8n.sh check` (com e sem edição manual na instância). Em todas as fases
confere: cada update tem **exatamente uma** resposta na transcrição do simulador.
