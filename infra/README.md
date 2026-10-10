# Infraestrutura — Sistema de Relatórios Pedagógicos

Roteiro de operação: subir o ambiente local, carregar cadastros, rodar os testes e implantar numa VPS
Linux genérica, de qualquer provedor. Tudo roda em contêineres; a máquina só precisa de **Docker Engine 27+ com Compose v2**.
A validação funcional completa está em
[`specs/001-fluxo-relatorios/quickstart.md`](../specs/001-fluxo-relatorios/quickstart.md).

## Arquivos

| Arquivo | Papel |
|---|---|
| `compose.yaml` | serviços base: `web` (Caddy + SPA), `backend`, `postgres`, `telegram-bot-api` |
| `compose.dev.yaml` | local: `web` só em `127.0.0.1:80/443`; monta `infra/carga` em `/carga` |
| `compose.prod.yaml` | produção: `web` publica 80 e 443; `restart: unless-stopped` |
| `compose.test.yaml` | testes em contêiner (backend, frontend, ponta a ponta, IA real) |
| `caddy/Caddyfile` | HTTPS, cabeçalhos de segurança e proxy de `/api/*` |
| `.env.example` | modelo das variáveis; copie para `infra/.env` (**nunca versionado**) |
| `carga/exemplo.json` | carga fictícia; cargas reais ficam fora do Git (`infra/carga/*.json`) |
| `carga/demonstracao.json` | cadastros fictícios da massa de demonstração (`local.sh subir --massa=true`) |
| `scripts/local.sh` | ambiente local: subir, carga, código, banco, status, logs, testes, parar, limpar — passo a passo em [`scripts/README.md`](scripts/README.md) |
| `compose.banco.yaml` | só local: publica o banco em `127.0.0.1` para ferramentas gráficas (`local.sh banco-porta`) |
| `scripts/implantar.sh` | atualização e subida da pilha de produção na VPS |

## 1. Ambiente local

Atalho para tudo desta seção e das seções 2 e 3 (roda da raiz do repositório, inclusive no Git Bash
do Windows). **Passo a passo completo, massa de demonstração e acesso ao banco em
[`scripts/README.md`](scripts/README.md).**

```bash
sh infra/scripts/local.sh subir --massa=true
```

| Opção do `subir` | Resultado |
|---|---|
| `--massa=true` | zera banco e PDFs locais e sobe com a massa de demonstração (cadastros, aulas aprovadas com PDFs, semestrais, relatórios em aberto) |
| `--massa=false` | zera banco e PDFs locais e sobe só o sistema, vazio |
| sem `--massa` | sobe mantendo os dados existentes |

Os demais comandos: `carga [infra/carga/arquivo.json]`, `codigo <email>`, `banco` (psql),
`banco-porta`/`banco-fechar` (banco em `127.0.0.1` para DBeaver/pgAdmin), `status`,
`logs [serviço]`, `testes [backend|frontend|e2e|ia-real|todos]`, `parar` e `limpar` (apaga os dados
locais, pede confirmação). O `subir` confere o `infra/.env` antes (segredos, credenciais do bot se
`TELEGRAM_HABILITADO=true`, chaves de IA se `PROVEDORES_IA=real`) e só sobe o `telegram-bot-api`
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

O primeiro vai em `POSTGRES_PASSWORD`, o segundo em `CPF_CHAVE_CIFRAGEM` e o terceiro em
`SEGREDO_HMAC`. Sem chaves de IA, use
`PROVEDORES_IA=simulado`.

```bash
docker compose -f infra/compose.yaml -f infra/compose.dev.yaml up -d --build
```

O portal responde em `https://localhost` (CA interna do Caddy). O backend registra o webhook no
servidor local da API de bots ao iniciar (log `webhook-registrado`).

### Massa de demonstração

`local.sh subir --massa=true` carrega `carga/demonstracao.json` e cria relatórios e PDFs de exemplo
pelo comando `massa` do backend. É uma ferramenta **só do ambiente local**, mantida para a revisão
visual e as validações manuais (justificativa em "Complexity Tracking" de
`specs/001-fluxo-relatorios/plan.md`). Os dados são todos fictícios (nomes, CPFs e responsáveis
inventados, e-mails `@exemplo.test`); o comando só roda no perfil `carga` e com o banco vazio, para
nunca misturar dados fictícios com reais, e a implantação na VPS não o chama.

### Versão do servidor local da API de bots

O `telegram-bot-api` usa a imagem `aiogram/telegram-bot-api:10.3` (verificada em 08/10/2026, quando
era também a `latest`). A versão é fixa porque o limite de 50 MB de arquivo nos dois canais (FR-015)
depende do modo `--local` deste servidor, e uma atualização silenciosa poderia mudar esse
comportamento. Para atualizar: troque a tag em `infra/compose.yaml`, suba em ambiente local com o
bot de teste e envie o MP4 de 31 MB pelo Telegram (checklist `validacao-quickstart.md`, V5) antes de
implantar.

### Bot de testes já usado na nuvem

O servidor local da API de bots só assume um bot que não esteja logado na API da nuvem. Faça
`logOut` uma vez (troque `<TOKEN>`):

```bash
curl -s "https://api.telegram.org/bot<TOKEN>/logOut"
```

## 2. Carga inicial e códigos de acesso

No ambiente local, `sh infra/scripts/local.sh subir --massa=true` já faz a carga (de
`carga/demonstracao.json`) e cria relatórios e PDFs fictícios; os comandos abaixo servem para cargas
avulsas e para a produção.

Formato e regras em [`contracts/carga-inicial.md`](../specs/001-fluxo-relatorios/contracts/carga-inicial.md).

```bash
docker compose -f infra/compose.yaml -f infra/compose.dev.yaml run --rm backend carga --arquivo /carga/exemplo.json
```

Use `--simular` para validar sem gravar. O código em claro só aparece nessa saída
(`email → CÓDIGO (válido até …)`); repasse-o ao professor fora do sistema. Para reemitir:

```bash
docker compose -f infra/compose.yaml -f infra/compose.dev.yaml run --rm backend emitir-codigo --email clara@exemplo.test
```

Em produção não há `compose.dev.yaml`: monte o arquivo de carga só durante o comando e apague-o em
seguida (ele contém CPF):

```bash
docker compose -f infra/compose.yaml -f infra/compose.prod.yaml run --rm -v "$PWD/infra/carga:/carga:ro" backend carga --arquivo /carga/carga.json
```

## 3. Testes

```bash
docker compose -f infra/compose.test.yaml run --rm testes-backend
```

```bash
docker compose -f infra/compose.test.yaml run --rm testes-frontend
```

```bash
docker compose -f infra/compose.test.yaml run --rm --build testes-e2e
```

- `testes-backend`: unitários, ArchUnit, integração com PostgreSQL, contrato OpenAPI, conversa do
  Telegram e paridade; o JaCoCo exige ≥ 90% de linhas e branches no `nucleo`.
- `testes-frontend`: ESLint, TypeScript e Vitest.
- `testes-e2e`: Playwright + axe contra a pilha completa com IA simulada. A semeadura grava dados
  fictícios direto no banco de testes. Relatório em `frontend/playwright-report/`.
- `testes-ia-real` (opcional, fora da cobertura): precisa de `TRANSCRICAO_CHAVE` e `LLM_CHAVE` no
  ambiente e das gravações em `assets/`; escreve `backend/aplicacao/target/ia-real/relatorio.md`.

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
3. **Código**: `git clone` do repositório no diretório de implantação (ex.: `/opt/relatorios`).
4. **`infra/.env` de produção** (permissão `600`, dono o usuário de implantação):
   - `DOMINIO` com o domínio real e `ACME_EMAIL` com um e-mail válido;
   - `POSTGRES_PASSWORD`, `CPF_CHAVE_CIFRAGEM`, `SEGREDO_HMAC` novos, gerados na VPS e guardados
     num cofre (perder `CPF_CHAVE_CIFRAGEM` torna os CPFs ilegíveis; trocar `SEGREDO_HMAC` invalida
     códigos e vínculos);
   - `TELEGRAM_HABILITADO=true`, `TELEGRAM_BOT_TOKEN` do bot de produção, `TELEGRAM_API_ID`,
     `TELEGRAM_API_HASH` e um `TELEGRAM_SEGREDO_WEBHOOK` aleatório;
   - `PROVEDORES_IA=real` e as chaves `TRANSCRICAO_CHAVE` e `LLM_CHAVE`.

   Os segredos ficam só nesse arquivo: nenhuma imagem os contém (o `.env` não entra no contexto de
   build e é lido em tempo de execução).
5. **Bot de produção**: `logOut` na nuvem (seção 1) antes da primeira subida.

### 4.2 Implantar

Na VPS, na raiz do repositório:

```bash
sh infra/scripts/implantar.sh main
```

O script faz `git pull --ff-only`, `build` e `up -d` com `compose.yaml` + `compose.prod.yaml` e
espera os serviços ficarem saudáveis.

Pelo GitHub: workflow **Implantar** (`.github/workflows/implantar.yml`, disparo manual). Configure
o ambiente `producao` com aprovação obrigatória (Settings → Environments → Required reviewers) e os
segredos `VPS_HOST`, `VPS_USUARIO`, `VPS_DIRETORIO`, `VPS_CHAVE_SSH` (chave privada dedicada) e
`VPS_HOST_CONHECIDO` (linha do `known_hosts`, obtida com `ssh-keyscan` e conferida).

### 4.3 Conferências depois da implantação

- Só 80 e 443 publicadas pelos contêineres (além do SSH do sistema):

  ```bash
  sudo ss -tlnp
  ```

  Esperado: `docker-proxy` apenas em `:80` e `:443`; PostgreSQL, backend e `telegram-bot-api` não
  aparecem (ficam na rede interna `interna`).
- `https://<domínio>` abre o portal com certificado válido e os cabeçalhos de segurança
  (`Strict-Transport-Security`, `Content-Security-Policy`, `X-Content-Type-Options`).
- Log `webhook-registrado` no backend:

  ```bash
  docker compose -f infra/compose.yaml -f infra/compose.prod.yaml logs backend | grep webhook
  ```

### 4.4 Perfil do bot no @BotFather

A API de bots não permite definir a foto do bot; faça pelo @BotFather:

1. `/setuserpic` → enviar o monograma FB (`frontend/public/marca/fb-monograma.png`).
2. `/setdescription` e `/setabouttext` com o texto institucional do Instituto Musical Felipe Blaft.
3. Os comandos (`/inicio`, `/registrar`, `/semestral`, `/historico`, `/ajuda`, `/sair`) são
   registrados pelo backend a cada subida (`setMyCommands`); não é preciso usar `/setcommands`.

## 5. Backup e restauração

Os dados persistentes estão nos volumes `pgdata` (banco) e `documentos` (PDFs). Backup do banco:

```bash
docker compose -f infra/compose.yaml -f infra/compose.prod.yaml exec -T postgres pg_dump -U relatorios -Fc relatorios > backup.dump
```

Guarde os backups fora da VPS, cifrados, junto com `CPF_CHAVE_CIFRAGEM` e `SEGREDO_HMAC` (sem eles o
backup não serve). Áudios nunca são persistidos (FR-018): não há o que salvar de `/tmp/audios` nem
do volume do `telegram-bot-api`.
