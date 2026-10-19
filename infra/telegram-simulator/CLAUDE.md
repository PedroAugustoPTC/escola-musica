# infra/telegram-simulator/

Simulador do Telegram **só para testes** (feature 004, R-12): faz o papel do servidor local da API de bots e o do
Telegram na nuvem, para os ensaios de troca do webhook e de rotação do token (`tests rehearsal`). Nunca vai para
produção e não tem dependências.

| Arquivo | O que é |
|---|---|
| `server.mjs` | a API de bots mínima (`setWebhook`, `getWebhookInfo`, `deleteWebhook`, `getMe`, `setMyCommands`, `sendMessage`, `editMessageText`, `sendDocument`, `answerCallbackQuery`) e a API de controle `/_control/*` |
| `server.test.mjs` | testes `node:test` do próprio simulador (`tests-simulator` do `compose.test.yaml`) |
| `Dockerfile` | `node:24.15.0-alpine` fixado |

## Regras

- Entrega cada update ao webhook registrado com o cabeçalho `X-Telegram-Bot-Api-Secret-Token` e **retém e reenvia**
  enquanto a resposta não for `2xx` (a semântica do Telegram em que a spec se apoia).
- A transcrição por chat (`GET /_control/transcript?chat=<id>`) é o que os ensaios conferem: **exatamente uma** resposta
  por update, em todas as fases da troca e da rotação.
- Controle: `POST /_control/updates` (injeta update de um chat), `POST /_control/fail-next` (falha a próxima chamada de um
  método), `POST /_control/hold` (retém a entrega), `POST /_control/reset`, `GET /_control/status`.
- Valores de teste apenas; nenhum segredo real passa por aqui.
