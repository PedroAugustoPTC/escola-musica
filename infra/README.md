# Infraestrutura — Sistema de Relatórios Pedagógicos

Roteiro de operação: subir o ambiente local, carregar cadastros, rodar os testes e implantar numa VPS
Linux genérica, de qualquer provedor. Tudo roda em contêineres; a máquina só precisa de **Docker Engine 27+ com Compose v2**.
A validação funcional completa está em
[`specs/001-fluxo-relatorios/quickstart.md`](../specs/001-fluxo-relatorios/quickstart.md).

## Arquivos

| Arquivo | Papel |
|---|---|
| `compose.yaml` | serviços base: `web` (Caddy + SPA), `backend`, `postgres`, `telegram-bot-api`, `n8n` |
| `compose.dev.yaml` | local: `web` só em `127.0.0.1:80/443`; monta `infra/seed` em `/seed` |
| `compose.prod.yaml` | produção: `web` publica 80 e 443; `restart: unless-stopped` |
| `compose.test.yaml` | testes em contêiner (backend, frontend, ponta a ponta, IA real, n8n e o ensaio do webhook) |
| `caddy/Caddyfile` | HTTPS, cabeçalhos de segurança e proxy de `/api/*` |
| `.env.example` | modelo das variáveis; copie para `infra/.env` (**nunca versionado**) |
| `seed/example.json` | carga fictícia; cargas reais ficam fora do Git (`infra/seed/*.json`) |
| `seed/demo.json` | cadastros fictícios da massa de demonstração (`local.sh up --demo-data=true`) |
| `scripts/local.sh` | ambiente local: up, seed, issue-code (alias obsoleto: code), rotate-cpf-key, database, status, logs, tests, stop, clean — passo a passo em [`scripts/README.md`](scripts/README.md) |
| `compose.database.yaml` | só local: publica o banco em `127.0.0.1` para ferramentas gráficas (`local.sh database-port`) |
| `scripts/deploy.sh` | atualização e subida da pilha de produção na VPS |
| `n8n/` | n8n, o transporte do Telegram: imagem fina, workflows como código e utilitários — leia [`n8n/CLAUDE.md`](n8n/CLAUDE.md) |
| `telegram-simulator/` | simulador do Telegram, só para os ensaios (nunca em produção) |
| `scripts/n8n.sh`, `scripts/telegram-webhook.sh`, `scripts/rotate-service-token.sh` | operação do n8n, registro e verificação do webhook e rotação do token de serviço (seções abaixo) |

## Se você tem um ambiente anterior à feature 003

A feature 003 passou os nomes do código para inglês, e isso alcança o que roda em contêiner. Não há alias
nem período de compatibilidade: o ambiente antigo **não é reaproveitado sozinho**.

- **`infra/.env`**: as chaves mudaram de nome. O `local.sh up` recusa o arquivo antigo e lista as chaves
  que faltam. Regere-o a partir do modelo (`cp infra/.env.example infra/.env`) e **leve os valores secretos
  do arquivo antigo** para as chaves novas, senão os CPFs gravados ficam ilegíveis e os códigos de acesso
  deixam de valer: `CPF_CHAVE_CIFRAGEM` → `CPF_ENCRYPTION_KEY` e `SEGREDO_HMAC` → `HMAC_SECRET`
  (`POSTGRES_PASSWORD` não muda). A correspondência das demais está em
  [`identifier-map.md`](../specs/003-migrate-code-to-english/contracts/identifier-map.md), seção 6.
- **Projeto, banco, usuário e volumes**: o projeto do compose passou de `relatorios` para `reports`, e o banco
  e o usuário do PostgreSQL também. Os volumes antigos (`relatorios_pgdata`, `relatorios_documentos`) ficam
  intactos, mas **não são usados**: a pilha nova sobe com volumes `reports_*` vazios.
- **Para levar os dados**, antes de atualizar o código, faça o `pg_dump` da pilha antiga (no local, com o
  `compose.dev.yaml`; na VPS, com o `compose.prod.yaml`):

  ```bash
  docker compose -f infra/compose.yaml -f infra/compose.dev.yaml exec -T postgres pg_dump -U relatorios -Fc relatorios > antigo.dump
  ```

  Depois de atualizar o código e regerar o `infra/.env`, suba **só o banco**, restaure o dump e copie os PDFs:

  ```bash
  docker compose -f infra/compose.yaml -f infra/compose.dev.yaml up -d postgres
  ```

  ```bash
  docker compose -f infra/compose.yaml -f infra/compose.dev.yaml exec -T postgres pg_restore -U reports -d reports --no-owner --no-privileges < antigo.dump
  ```

  ```bash
  docker run --rm -v relatorios_documentos:/origem:ro -v reports_documents:/destino alpine cp -a /origem/. /destino/
  ```

  Em seguida `sh infra/scripts/local.sh up`: o Flyway aplica a `V7` e a `V8` sobre os dados restaurados.
  O `antigo.dump` contém dados reais: guarde-o fora do repositório e apague-o ao terminar.

## 1. Ambiente local

Atalho para tudo desta seção e das seções 2 e 3 (roda da raiz do repositório, inclusive no Git Bash
do Windows). **Passo a passo completo, massa de demonstração e acesso ao banco em
[`scripts/README.md`](scripts/README.md).**

```bash
sh infra/scripts/local.sh up --demo-data=true
```

| Opção do `up` | Resultado |
|---|---|
| `--demo-data=true` | zera banco e PDFs locais e sobe com a massa de demonstração (cadastros, aulas aprovadas com PDFs, semestrais, relatórios em aberto) |
| `--demo-data=false` | zera banco e PDFs locais e sobe só o sistema, vazio |
| sem `--demo-data` | sobe mantendo os dados existentes |

Os demais comandos: `seed [infra/seed/arquivo.json] [--simulate]`, `issue-code <email>` (o alias `code` é obsoleto), `rotate-cpf-key` (veja a seção 2), `database` (psql),
`database-port`/`database-close` (banco em `127.0.0.1` para DBeaver/pgAdmin), `status`,
`logs [serviço]`, `tests [backend|frontend|e2e|real-ai|all]`, `stop` e `clean` (apaga os dados
locais, pede confirmação). O `up` confere o `infra/.env` antes (segredos, credenciais do bot se
`TELEGRAM_ENABLED=true`, chaves de IA se `AI_PROVIDERS=real`) e só sobe o `telegram-bot-api`
com o bot habilitado.

Passo a passo manual:

```bash
cp infra/.env.example infra/.env
```

Preencha `infra/.env` (ver comentários do arquivo). Para gerar os segredos:

```bash
openssl rand -base64 24
```

```bash
openssl rand -base64 32
```

```bash
openssl rand -base64 48
```

O primeiro vai em `POSTGRES_PASSWORD`, o segundo em `CPF_ENCRYPTION_KEY` e o terceiro em
`HMAC_SECRET`. Sem chaves de IA, use
`AI_PROVIDERS=simulated`.

```bash
docker compose -f infra/compose.yaml -f infra/compose.dev.yaml up -d --build
```

O portal responde em `https://localhost` (CA interna do Caddy). Com o Telegram habilitado, o `local.sh up`
registra o webhook do n8n (`telegram-webhook.sh apply`) e o backend registra os comandos do bot ao iniciar (log
`commands-registered`).

### Massa de demonstração

`local.sh up --demo-data=true` carrega `seed/demo.json` e cria relatórios e PDFs de exemplo
pelo comando `demo-data` do backend. É uma ferramenta **só do ambiente local**, mantida para a revisão
visual e as validações manuais (justificativa em "Complexity Tracking" de
`specs/001-fluxo-relatorios/plan.md`). Os dados são todos fictícios (nomes, CPFs e responsáveis
inventados, e-mails `@example.test`); o comando só roda no perfil `seed` e com o banco vazio, para
nunca misturar dados fictícios com reais, e a implantação na VPS não o chama.

### Versão do servidor local da API de bots

O `telegram-bot-api` usa a imagem `aiogram/telegram-bot-api:10.3` (verificada em 08/10/2026, quando
era também a `latest`). A versão é fixa porque o limite de 50 MB de arquivo nos dois canais (FR-015)
depende do modo `--local` deste servidor, e uma atualização silenciosa poderia mudar esse
comportamento. Para atualizar: troque a tag em `infra/compose.yaml`, suba em ambiente local com o
bot de teste e envie o MP4 de 31 MB pelo Telegram (checklist `validacao-quickstart.md`, V5) antes de
implantar.

### Versão do n8n

O n8n usa a imagem `docker.n8n.io/n8nio/n8n:2.43.6`, fixada **só** em `infra/n8n/Dockerfile` (com o *digest* em
comentário). Nunca use `latest`, `stable` nem `next`. Para atualizar: leia as notas de versão do n8n, troque a tag no
`Dockerfile`, suba no ambiente local, rode `sh infra/scripts/local.sh tests n8n` e `tests rehearsal` e só então
abra o PR. A imagem já traz os workflows do repositório; nada é baixado em tempo de execução.

### Exportar workflows de volta

Os workflows são código (`infra/n8n/workflows/*.json`) e o repositório sempre prevalece: a cada subida o n8n os
importa de novo. Para mudar um: `local.sh up` (o editor abre em `http://127.0.0.1:5678`), edite no navegador,
`sh infra/scripts/n8n.sh export` (normaliza e recusa credencial, token ou URL com senha), `git diff`, `tests n8n` e
abra o PR. `sh infra/scripts/n8n.sh check` mostra se a instância divergiu do repositório. Em produção o editor é
desligado e nada se edita à mão. O banco do n8n fica só em memória (`tmpfs`): reiniciar o contêiner o zera, e o
usuário do editor local é recriado.

### Registrar o webhook do n8n

O n8n é o **único** transporte do Telegram: o webhook registrado no bot aponta sempre para ele, e o
`telegram-webhook.sh` é o único dono desse registro (o backend não registra webhook). O `local.sh up` e o `deploy.sh`
já rodam `apply`. Para registrar ou conferir à mão, com o ensaio em `sh infra/scripts/local.sh tests rehearsal` (backend
real, n8n real e Telegram simulado; cada update precisa ter exatamente uma resposta durante o registro e a rotação):

```bash
sh infra/scripts/telegram-webhook.sh status --env prod
sh infra/scripts/telegram-webhook.sh apply --env prod --chat-id <seu-chat-de-teste>
```

O `apply` confere as pré-condições (n8n saudável, workflows publicados, saúde profunda, segredo do webhook válido,
tokens preenchidos e diferentes), registra o webhook e **verifica** (webhook certo, segredo errado recusado, segredo
certo aceito, saúde profunda, entrega proativa apontando para o n8n e, com `--chat-id`, a ida e volta real). Os
códigos de motivo (`[N8N_NOT_HEALTHY]`, `[SECRET_MISMATCH]` etc.) estão em
`specs/004-n8n-telegram-transport/contracts/transport-scripts.md`.

### Reverter

Não existe mais reversão por script: o caminho antigo (o backend falando direto com o Telegram) foi removido depois
da paridade (feature 004, FR-029). Reverter é **reimplantar a versão anterior** da aplicação; nesse caso, registre o
webhook do backend dessa versão antes de ligar o tráfego.

### Verificar

```bash
sh infra/scripts/telegram-webhook.sh verify --env prod   # somente leitura, exceto as sondas
sh infra/scripts/n8n.sh health --deep --env prod         # backend e API de bots, vistos pelo n8n
```

### Rotacionar o token de serviço

O token que o n8n envia ao backend (`TELEGRAM_CHANNEL_CURRENT_TOKEN`) troca-se **sem janela de recusa**, usando os
dois tokens simultâneos do backend:

```bash
sh infra/scripts/rotate-service-token.sh start --env prod    # gera o novo; o backend aceita os dois; o n8n envia o novo
sh infra/scripts/rotate-service-token.sh finish --env prod   # o novo vira o atual; o antigo passa a ser recusado
# ou: sh infra/scripts/rotate-service-token.sh abort --env prod
```

O `infra/.env.previous` guarda a cópia anterior do `.env` (contém segredos; ignorado pelo Git). O token de entrega
(`TELEGRAM_CHANNEL_ORCHESTRATOR_TOKEN`, do backend ao n8n) é outro segredo: sua troca é um reinício coordenado dos
dois contêineres.

### Bot de testes já usado na nuvem

O servidor local da API de bots só assume um bot que não esteja logado na API da nuvem. Faça
`logOut` uma vez (troque `<TOKEN>`):

```bash
curl -s "https://api.telegram.org/bot<TOKEN>/logOut"
```

## 2. Carga inicial e códigos de acesso

No ambiente local, `sh infra/scripts/local.sh up --demo-data=true` já faz a carga (de
`seed/demo.json`) e cria relatórios e PDFs fictícios; os comandos abaixo servem para cargas
avulsas e para a produção.

Formato e regras em [`contracts/carga-inicial.md`](../specs/001-fluxo-relatorios/contracts/carga-inicial.md).

```bash
docker compose -f infra/compose.yaml -f infra/compose.dev.yaml run --rm backend seed --file /seed/example.json
```

Use `--simulate` para validar sem gravar. O código em claro só aparece nessa saída
(`email → CÓDIGO (válido até …)`); repasse-o ao professor fora do sistema. Para reemitir:

```bash
docker compose -f infra/compose.yaml -f infra/compose.dev.yaml run --rm backend issue-code --email clara@example.test
```

Em produção não há `compose.dev.yaml`: monte o arquivo de carga só durante o comando e apague-o em
seguida (ele contém CPF):

```bash
docker compose -f infra/compose.yaml -f infra/compose.prod.yaml run --rm -v "$PWD/infra/seed:/seed:ro" backend seed --file /seed/real-data.json
```

### Primeiro administrador, `AUTH_HMAC_SECRET` e rotação das chaves

- **Local**: o `local.sh up` gera o `AUTH_HMAC_SECRET` (48 caracteres, distinto do `HMAC_SECRET`) e, se
  as três variáveis `ADMIN_BOOTSTRAP_*` estão vazias, preenche o administrador fictício
  `admin@example.test` com uma senha provisória impressa **uma única vez**. A senha só vale se o
  banco ainda não tiver administrador. Depois do primeiro acesso (quando a troca da senha é
  obrigatória), **esvazie `ADMIN_BOOTSTRAP_PASSWORD` no `infra/.env`**.
- **Produção**: preencha `AUTH_HMAC_SECRET` e `ADMIN_BOOTSTRAP_*` à mão no `infra/.env` da VPS e
  esvazie `ADMIN_BOOTSTRAP_PASSWORD` depois do primeiro acesso.
- **`seed [arquivo] [--simulate]`**: carga idempotente; `--simulate` valida e desfaz.
- **`issue-code <email>`**: emergência; reemite o código de um professor (`code` é alias obsoleto).
- **`rotate-cpf-key [--simulate] [--confirm-invalidate-codes]`**: troca a chave de cifra do CPF e/ou
  o segredo do resumo. As chaves novas vêm em `ROTATE_NEW_CPF_ENCRYPTION_KEY` e/ou
  `ROTATE_NEW_HMAC_SECRET` (e as antigas, se diferirem do `.env`, em `ROTATE_OLD_*`), exportadas no
  terminal de uma **máquina confiável**, e devem receber `unset` ao terminar. O comando valida tudo
  antes de parar o portal e a API, descarta os rascunhos de cadastro do Telegram e, se falhar,
  informa como religar (`up`). Detalhes em [`scripts/README.md`](scripts/README.md).

## 3. Testes

```bash
docker compose -f infra/compose.test.yaml run --rm tests-backend
```

```bash
docker compose -f infra/compose.test.yaml run --rm tests-frontend
```

```bash
docker compose -f infra/compose.test.yaml run --rm --build tests-e2e
```

- `tests-backend`: unitários, ArchUnit, integração com PostgreSQL, contrato OpenAPI, conversa do
  Telegram e paridade; o JaCoCo exige ≥ 90% de linhas e branches no `core`.
- `tests-frontend`: ESLint, TypeScript e Vitest.
- `tests-n8n`: o n8n real em contêiner (contrato dos workflows, a suíte de conversa e de paridade pelo n8n,
  duplicidade, resiliência, privacidade e latência); `tests-n8n-ops` e `tests-simulator` testam os utilitários.
  Rode com `sh infra/scripts/local.sh tests n8n`.
- `tests-rehearsal`: o ensaio do registro do webhook e da rotação do token, com o Telegram simulado
  (`sh infra/scripts/local.sh tests rehearsal`).
- `tests-e2e`: Playwright + axe contra a pilha completa com IA simulada. A semeadura grava dados
  fictícios direto no banco de testes. Relatório em `frontend/playwright-report/`.
- `tests-real-ai` (opcional, fora da cobertura): precisa de `TRANSCRIPTION_API_KEY` e `LLM_API_KEY` no
  ambiente e das gravações em `assets/`; escreve `backend/application/target/real-ai/report.md`.

Para desmontar a pilha de testes: `docker compose -f infra/compose.test.yaml down -v`.

## 4. Implantação na VPS

Qualquer VPS Linux serve, de qualquer provedor: as únicas dependências são Docker Engine 27+ e
Compose v2, e nenhum recurso exclusivo do provedor (painel, snapshot, DNS ou rede específicos) é
requisito.

### 4.1 Preparação (uma vez)

1. **VPS**: Linux (ex.: Ubuntu LTS) com Docker Engine 27+ e Compose v2; usuário sem senha de root para a
   implantação, com acesso por chave SSH; firewall liberando só 22 (SSH), 80 e 443.
2. **DNS**: registro `A` (e `AAAA`, se houver IPv6) do domínio do portal apontando para o IP da
   VPS. O Caddy obtém o certificado automaticamente na primeira subida (portas 80 e 443 abertas).
3. **Código**: `git clone` do repositório no diretório de implantação (ex.: `/opt/reports`).
4. **`infra/.env` de produção** (permissão `600`, dono o usuário de implantação):
   - `DOMAIN` com o domínio real e `ACME_EMAIL` com um e-mail válido;
   - `POSTGRES_PASSWORD`, `CPF_ENCRYPTION_KEY`, `HMAC_SECRET` novos, gerados na VPS e guardados
     num cofre (perder `CPF_ENCRYPTION_KEY` torna os CPFs ilegíveis; trocar `HMAC_SECRET` invalida
     códigos e vínculos);
   - `TELEGRAM_ENABLED=true`, `TELEGRAM_BOT_TOKEN` do bot de produção, `TELEGRAM_API_ID`,
     `TELEGRAM_API_HASH` e um `TELEGRAM_WEBHOOK_SECRET` aleatório;
   - `AI_PROVIDERS=real` e as chaves `TRANSCRIPTION_API_KEY` e `LLM_API_KEY`.

   Os segredos ficam só nesse arquivo: nenhuma imagem os contém (o `.env` não entra no contexto de
   build e é lido em tempo de execução).
5. **Bot de produção**: `logOut` na nuvem (seção 1) antes da primeira subida.

### 4.2 Implantar

Na VPS, na raiz do repositório:

```bash
sh infra/scripts/deploy.sh main
```

O script faz `git pull --ff-only`, `build` e `up -d` com `compose.yaml` + `compose.prod.yaml` e
espera os serviços ficarem saudáveis.

Pelo GitHub: workflow **Implantar** (`.github/workflows/deploy.yml`, disparo manual). Configure
o ambiente `production` com aprovação obrigatória (Settings → Environments → Required reviewers) e os
segredos `VPS_HOST`, `VPS_USER`, `VPS_DIRECTORY`, `VPS_SSH_KEY` (chave privada dedicada) e
`VPS_KNOWN_HOST` (linha do `known_hosts`, obtida com `ssh-keyscan` e conferida).

### 4.3 Conferências depois da implantação

- Só 80 e 443 publicadas pelos contêineres (além do SSH do sistema):

  ```bash
  sudo ss -tlnp
  ```

  Esperado: `docker-proxy` apenas em `:80` e `:443`; PostgreSQL, backend e `telegram-bot-api` não
  aparecem (ficam na rede interna `internal`).
- `https://<domínio>` abre o portal com certificado válido e os cabeçalhos de segurança
  (`Strict-Transport-Security`, `Content-Security-Policy`, `X-Content-Type-Options`).
- Log `commands-registered` no backend e webhook do n8n registrado:

  ```bash
  docker compose -f infra/compose.yaml -f infra/compose.prod.yaml logs backend | grep commands-registered
  sh infra/scripts/telegram-webhook.sh status --env prod
  ```

### 4.4 Perfil do bot no @BotFather

A API de bots não permite definir a foto do bot; faça pelo @BotFather:

1. `/setuserpic` → enviar o monograma FB (`frontend/public/brand/fb-monogram.png`).
2. `/setdescription` e `/setabouttext` com o texto institucional do Instituto Musical Felipe Blaft.
3. Os comandos (`/inicio`, `/registrar`, `/semestral`, `/historico`, `/ajuda`, `/sair`) são
   registrados pelo backend a cada subida (`setMyCommands`); não é preciso usar `/setcommands`.

## 5. Backup e restauração

Os dados persistentes estão nos volumes `pgdata` (banco) e `documents` (PDFs), com o prefixo do projeto (`reports_pgdata`, `reports_documents`). Backup do banco:

```bash
docker compose -f infra/compose.yaml -f infra/compose.prod.yaml exec -T postgres pg_dump -U reports -Fc reports > backup.dump
```

Guarde os backups fora da VPS, cifrados, junto com `CPF_ENCRYPTION_KEY` e `HMAC_SECRET` (sem eles o
backup não serve). Áudios nunca são persistidos (FR-018): não há o que salvar de `/tmp/audios` nem
do volume do `telegram-bot-api`.
