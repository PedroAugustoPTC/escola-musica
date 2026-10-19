# infra/n8n/

O **n8n** é o transporte do canal Telegram (feature 004, constituição v2.1.0, Princípio II): recebe o update, repassa ao
backend, executa as ações que o backend devolve e confirma o resultado. **Ele só transporta**: não decide texto, botão,
ordem nem compensação, não grava no banco, não chama IA e não vê áudio. Esta pasta é a imagem fina `reports-n8n` e os
workflows versionados como código.

| Caminho | O que é |
|---|---|
| `Dockerfile` | `FROM docker.n8n.io/n8nio/n8n:<versão exata>` + `workflows/`, `ops/` e `entrypoint.sh`. Versão **fixada**; nunca `latest`, `stable` nem `next` |
| `entrypoint.sh` | importa e publica os workflows do repositório, despublica o que só existe na instância e entrega a `ops/gate.mjs` |
| `workflows/*.json` | os workflows (inventário fechado: `telegram-inbound`, `channel-delivery`, `execute-actions`, `transport-health`) |
| `ops/` | utilitários sem dependências, executados **dentro** da imagem: `gate.mjs` (porta de entrada: abre a 5678 só com os receptores registrados), `probe.mjs` (HTTP), `normalize-workflow.mjs` (exportação e comparação), `check-workflows.sh`, e os `*.test.mjs` |

## Regras

- **Workflows são código**: toda mudança entra por PR e nova imagem. Em produção o editor é desligado e nada se edita à
  mão; a instância é só uma cópia do repositório, importada a cada subida (o repositório sempre prevalece).
- **Nenhum segredo nem ID de credencial nos JSON.** Nenhum nó usa `credentials`; segredos e endereços chegam por
  `{{ $env.NOME }}` (lista fechada: `BACKEND_INTERNAL_URL`, `TELEGRAM_API_URL`, `TELEGRAM_BOT_TOKEN`,
  `BACKEND_SERVICE_TOKEN`, `CHANNEL_DELIVERY_TOKEN`).
- **Nada de texto livre nos JSON**: sem nota, sem `stickyNote`, sem comentário em nó `Code`. A explicação de cada passo
  mora em `specs/004-n8n-telegram-transport/contracts/n8n-workflows.md`.
- **Só transporte**: `If` e `Switch` leem apenas tipo e estado da ação, status HTTP, resultado e o token de entrega; nós
  `Code` são funções puras (sem `$env`, rede ou módulo); destinos só o backend e o servidor local da API de bots; nunca `getFile`.
- Os guardiões `WorkflowGuardTest`, `WorkflowSecretScanTest` e `ComposeGuardTest` (em `tests-backend`) reprovam o build
  se alguma regra for quebrada; leia a mensagem (arquivo, nó e caminho JSON; nunca o valor).
- Um único receptor de updates do Telegram em todo o conjunto: o `Webhook` `telegram-update` do `telegram-inbound`.
- **Porta de entrada (`ops/gate.mjs`)**: o n8n escuta em `127.0.0.1:5680` e a 5678 só abre depois que os três `*/live` respondem
  `{"status":"UP"}`. Sem isso, nos primeiros ms o n8n responde `200 "n8n is starting up"` a qualquer POST e o Telegram dá o
  update por entregue (perda). Na parada (SIGTERM) a porta deixa de aceitar, espera as requisições em curso (até 20 s) e só então manda o n8n parar: na parada
  limpa do próprio n8n o executor de `Code` encerra primeiro e os updates aceitos nesse intervalo recebem 200 sem terem sido
  repassados (`research.md` R-13a).
- O n8n **nunca** é publicado: sem `ports` (só o editor em `127.0.0.1:5678` no ambiente local) e sem rota no Caddy.
- Privacidade: nenhuma execução é guardada; o instantâneo inicial e o PDF em trânsito existem só em memória: o SQLite
  do n8n (`DB_SQLITE_DATABASE`) e `binaryData` ficam em `tmpfs` e a poda os apaga em ≈ 2 min (`research.md` R-13, F-1).
  Consequência: reiniciar o contêiner zera o banco, e **o usuário do editor local é recriado a cada reinício**. Nunca ponha dado de aluno em log, nó `Set` ou nome de nó.

## Mudar um workflow (editar e exportar de volta)

1. `sh infra/scripts/local.sh up` (o editor abre em `http://127.0.0.1:5678`; crie o usuário local na primeira vez).
2. Edite no navegador. Prefira nomes de nó em inglês e nenhum texto livre.
3. `sh infra/scripts/n8n.sh export`: exporta, normaliza (remove campos voláteis, ordena as chaves) e grava em
   `infra/n8n/workflows/`. Recusa e não grava se achar credencial, token ou URL com senha.
4. `git diff` para revisar; `sh infra/scripts/local.sh tests backend` (guardiões) e `tests n8n`.
5. Abra o PR. `sh infra/scripts/n8n.sh check` mostra se a instância divergiu do repositório (edição manual).

## Atualizar a versão do n8n

Leia as notas de versão, troque a tag **só** no `Dockerfile` (e o *digest* do comentário), suba no ambiente local, rode
`tests n8n` e abra o PR. Passo a passo em `infra/README.md` ("Versão do n8n").
