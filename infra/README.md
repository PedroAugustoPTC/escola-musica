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
| `scripts/local.sh` | ambiente local: up, seed, issue-code (alias obsoleto: code), consent-load, rotate-cpf-key, database, status, logs, tests, stop, clean — passo a passo em [`scripts/README.md`](scripts/README.md) |
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
`HMAC_SECRET`; gere outro valor com o mesmo comando do terceiro para o `AUTH_HMAC_SECRET` (precisa ser **diferente** do
`HMAC_SECRET`). No passo a passo manual, preencha também as três `ADMIN_BOOTSTRAP_*` (seção 2) para ter o primeiro
administrador; o `local.sh up` faz as duas coisas sozinho. Sem chaves de IA, use
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

Formato e regras vigentes em [`initial-load.md`](../specs/005-admin-registrations-access/contracts/initial-load.md)
(feature 005; a [versão da 001](../specs/001-fluxo-relatorios/contracts/carga-inicial.md) é histórico). Depois da
feature 005 a carga não tem regra própria: cada linha chama os mesmos casos de uso do administrador, e o cadastro normal
de professores e alunos é feito na área administrativa (`/admin`); a carga e o `issue-code` ficam para a primeira
carga e para emergência.

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

### Variáveis da administração (feature 005)

Todas estão em `infra/.env.example` (copie para `infra/.env`, que nunca vai para o Git) e são repassadas ao backend por
`compose.yaml`. Sem valor, vale o **padrão**; as quatro primeiras não têm padrão e ficam **vazias** no repositório (não existe
credencial padrão).

| Variável | Padrão | Função |
|---|---|---|
| `AUTH_HMAC_SECRET` | nenhum (obrigatória) | Segredo (≥ 32 bytes, **diferente** do `HMAC_SECRET`; o backend recusa subir de outro modo) de onde saem as chaves do código de ativação do administrador e da origem das tentativas de entrada. Trocá-lo invalida as ativações pendentes e zera o bloqueio por tentativas do administrador |
| `ADMIN_BOOTSTRAP_NAME` | vazio | Nome do primeiro administrador (só vale em banco sem administrador) |
| `ADMIN_BOOTSTRAP_EMAIL` | vazio | E-mail do primeiro administrador |
| `ADMIN_BOOTSTRAP_PASSWORD` | vazio | Senha **provisória** do primeiro administrador (12 a 128 caracteres, diferente do nome e do e-mail); troca obrigatória no primeiro acesso. Nunca vai a log. Esvazie depois do primeiro acesso |
| `ADMIN_SESSION_HOURS` | `2` | Duração **fixa** da sessão do administrador, em horas (não renova por uso) |
| `ADMIN_PROVISIONAL_PASSWORD_DAYS` | `7` | Dias de validade da senha provisória |
| `ADMIN_PASSWORD_ARGON2_MEMORY_KIB` | `19456` | Memória (KiB) do hash Argon2id da senha |
| `ADMIN_PASSWORD_ARGON2_ITERATIONS` | `2` | Iterações do Argon2id. Mudar o custo não invalida senhas antigas (são recalculadas na próxima entrada) |
| `ACCESS_CPF_PROBE_MAX_REFUSALS` | `5` | Recusas por CPF de aluno alheio, pelo professor, antes do bloqueio da operação |
| `ACCESS_CPF_PROBE_WINDOW_MINUTES` | `15` | Janela dessa contagem |
| `ACCESS_CPF_PROBE_LOCK_MINUTES` | `15` | Duração do bloqueio |
| `ACCESS_CPF_LOOKUP_MAX` | `30` | Consultas de CPF digitado pelo professor (livre ou ocupado) antes do bloqueio |
| `ACCESS_CPF_LOOKUP_WINDOW_MINUTES` | `60` | Janela dessa contagem |
| `ACCESS_CPF_LOOKUP_LOCK_MINUTES` | `60` | Duração do bloqueio |
| `TELEGRAM_STUDENT_DRAFT_MINUTES` | `30` | Minutos de inatividade que descartam o cadastro de aluno em andamento no Telegram |

### Variáveis de privacidade, retenção e operação (feature 007)

Todas estão em `infra/.env.example`. Valor ausente, zero, negativo ou inválido **não impede a subida**: vale o padrão e o
backend avisa no log o **nome da variável** (nunca o valor). Contrato: `specs/007-privacy-retention-operations/contracts/configuration.md`.

| Variável | Padrão | Função |
|---|---|---|
| `DB_APP_PASSWORD` | nenhum (obrigatória) | Senha do papel de banco `reports_app`, usado pelo pool da aplicação (sem `UPDATE`/`DELETE` na trilha). Gere um valor novo e longo; `infra/scripts/lib/db-roles.sh` a aplica ao papel. O Flyway segue com `POSTGRES_PASSWORD` |
| `DB_APP_USER` | `reports_app` | Nome do papel da aplicação |
| `RETENTION_TRANSCRIPTION_DAYS` | `30` | Dias após aprovar ou cancelar até apagar a transcrição e o texto das interações |
| `RETENTION_UNAPPROVED_PDF_DAYS` | `30` | Dias após aprovar até apagar os PDFs das versões não aprovadas |
| `RETENTION_CANCELLED_REPORT_DAYS` | `90` | Dias após cancelar até anonimizar o relatório |
| `RETENTION_AUDIT_TRAIL_DAYS` | `1826` | Dias de guarda da trilha de auditoria (o banco impõe piso de 365) |
| `RETENTION_RUN_HISTORY_DAYS` | igual à trilha | Dias de guarda do histórico das execuções de retenção |
| `RETENTION_CRON` | `0 41 3 * * *` | Horário da rotina diária (cron do Spring, fuso da escola) |
| `PRIVACY_OFFICER_NAME` / `PRIVACY_OFFICER_CONTACT` | vazio | Responsável pela LGPD mostrado nas telas; vazio mostra "a definir pela escola" e gera aviso na subida |
| `ALERT_PROVIDER_CONSECUTIVE_FAILURES` | `3` | Falhas seguidas de um provedor para abrir alerta |
| `ALERT_PROVIDER_FAILURE_RATE_PERCENT` | `20` | Taxa de falha na janela para abrir alerta |
| `ALERT_PROVIDER_WINDOW_MINUTES` | `15` | Janela dessa taxa |
| `ALERT_QUEUE_MAX_AGE_MINUTES` | `5` | Idade máxima de item na fila do canal |
| `ALERT_QUEUE_MAX_ITEMS` | `50` | Itens pendentes na fila do canal |
| `ALERT_STORAGE_QUIET_MINUTES` | `15` | Minutos sem falha de armazenamento para normalizar o alerta |
| `ALERT_EVALUATION_SECONDS` | `60` | Intervalo da apuração dos alertas |
| `COSTS_INFRASTRUCTURE_MONTHLY_USD` / `COSTS_INFRASTRUCTURE_EFFECTIVE_FROM` | `0` / vazio | Custo fixo mensal de infraestrutura (USD) e a data de vigência |

Os preços por provedor ficam em `reports.costs.prices` (lista com `provider`, `unit`, `value-usd`, `effective-from`); lista vazia
reaproveita as propriedades de preço atuais.

**Rotação de logs**: todo serviço do `compose.yaml` usa `json-file` com `max-size: 20m` e `max-file: 5`.

`ROTATE_NEW_*` e `ROTATE_OLD_*` (rotação das chaves, abaixo) **não** vão para o `.env`: são variáveis do terminal.

### Subida com o primeiro administrador

O backend cria o primeiro administrador na subida, a partir das três `ADMIN_BOOTSTRAP_*`, **somente se o banco ainda não tem
nenhum administrador** (em qualquer situação); se já existe algum, as variáveis são ignoradas, até as inválidas. Sem as três
variáveis e sem administrador, o sistema sobe e avisa no log; com valor inválido (por exemplo, senha curta), **recusa subir** e a
mensagem cita a variável, nunca o valor.

- **Local**: o `local.sh up` gera o `AUTH_HMAC_SECRET` (48 caracteres, distinto do `HMAC_SECRET`) e, se as três variáveis
  `ADMIN_BOOTSTRAP_*` estão vazias, preenche o administrador fictício `admin@example.test` com uma senha provisória impressa
  **uma única vez**. Entre em `https://localhost/admin/sign-in`, troque a senha (obrigatória no primeiro acesso) e **esvazie
  `ADMIN_BOOTSTRAP_PASSWORD` no `infra/.env`**. Antes de zerar os dados (`up --demo-data=true|false`) esvazie também
  `ADMIN_BOOTSTRAP_NAME` e `ADMIN_BOOTSTRAP_EMAIL`: com o banco vazio e as variáveis incompletas, o backend recusa subir.
- **Produção**: no `infra/.env` da VPS (permissão `600`) preencha `AUTH_HMAC_SECRET` (`openssl rand -base64 48`) e as três
  `ADMIN_BOOTSTRAP_*` (senha com `openssl rand -base64 24`), rode `sh infra/scripts/deploy.sh main`, entre em
  `https://<domínio>/admin/sign-in`, troque a senha e **esvazie `ADMIN_BOOTSTRAP_PASSWORD`** no `.env` (a senha provisória não
  deve ficar em disco). Daí em diante o administrador cria os demais administradores pela própria área administrativa (cada um
  recebe um código de ativação exibido uma única vez).

### Recuperação: nenhum administrador consegue entrar

Não existe variável, rota nem comando que redefina a senha de um administrador existente. Se nenhum administrador ativo consegue
entrar (senha perdida, contas inativadas), o mantenedor da VPS refaz o primeiro administrador:

1. **Pare o backend** (o portal fica fora do ar durante o procedimento):

   ```bash
   docker compose -f infra/compose.yaml -f infra/compose.prod.yaml stop backend
   ```

2. **Apague os administradores** (as sessões saem em cascata; o registro de autoria **é preservado**, porque não tem chave
   estrangeira):

   ```bash
   docker compose -f infra/compose.yaml -f infra/compose.prod.yaml exec -T postgres psql -U reports -d reports -c "DELETE FROM administrator;"
   ```

3. **Preencha** `ADMIN_BOOTSTRAP_NAME`, `ADMIN_BOOTSTRAP_EMAIL` e `ADMIN_BOOTSTRAP_PASSWORD` (uma senha nova, 12 a 128 caracteres)
   no `infra/.env`.
4. **Reinicie** (recria o contêiner com o `.env` novo; o backend cria o administrador porque não há nenhum):

   ```bash
   docker compose -f infra/compose.yaml -f infra/compose.prod.yaml up -d --force-recreate backend
   ```

5. **Entre, troque a senha e apague a senha do `.env`**: acesse `/admin/sign-in` com a senha provisória, defina a nova senha
   (obrigatória; a provisória vale `ADMIN_PROVISIONAL_PASSWORD_DAYS` dias) e **esvazie `ADMIN_BOOTSTRAP_PASSWORD`** no `infra/.env`.
   Recrie o backend de novo para que a variável saia do contêiner.

Professores, alunos, relatórios e códigos de acesso dos professores não são tocados. Os códigos de ativação pendentes de outros
administradores somem junto com as contas; crie-os de novo pela área administrativa.

### Comandos operacionais (perfil `seed`)

- **`seed [arquivo] [--simulate]`**: carga inicial idempotente (professor por e-mail, aluno pelo resumo do CPF; nunca apaga) e
  tudo ou nada; `--simulate` roda os mesmos casos de uso e **desfaz** a transação. Imprime os códigos de acesso uma única vez.
- **`issue-code <email>`**: **emergência**; o caminho normal é o administrador reemitir o código na área administrativa. O código
  anterior morre na hora e as **sessões Web do professor são encerradas**; o vínculo do Telegram é mantido (`code` é alias obsoleto).
- **`rotate-cpf-key [--simulate] [--confirm-invalidate-codes]`**: troca a chave de cifra do CPF e/ou o segredo do resumo (abaixo).

### Rotação das chaves do CPF

Use numa **janela de manutenção** e numa **máquina confiável**, com uma cópia de segurança recente do banco (que não inclui as
chaves). As chaves **novas** entram por variáveis de ambiente do terminal, nunca por argumento (não ficam no histórico do shell):

- `ROTATE_NEW_CPF_ENCRYPTION_KEY` (Base64 de 32 bytes) troca a chave de cifra: reescreve o CPF cifrado, sem efeito colateral;
- `ROTATE_NEW_HMAC_SECRET` (≥ 32 bytes) troca o segredo do resumo: recalcula o resumo do CPF **e invalida os códigos de acesso
  vigentes dos professores** (o administrador os reemite); exige `--confirm-invalidate-codes` (a simulação dispensa). Os códigos
  de ativação e as origens de tentativas usam o `AUTH_HMAC_SECRET` e não mudam;
- `ROTATE_OLD_CPF_ENCRYPTION_KEY` e `ROTATE_OLD_HMAC_SECRET` só são necessárias se as chaves antigas forem diferentes das do `.env`.

Cada chave é opcional e independente. O comando decifra e cifra **em memória**, confere que não surgiram colisões, nunca imprime
o CPF (só contagens) e descarta os rascunhos de cadastro do Telegram em andamento (duram 30 minutos e estão cifrados com a chave
antiga). No ambiente local o `local.sh rotate-cpf-key` valida tudo **antes** de parar o portal e a API e, se a rotação falhar,
informa como religar (`up`). Roteiro local em [`scripts/README.md`](scripts/README.md); na VPS, com os mesmos argumentos:

```bash
export ROTATE_NEW_CPF_ENCRYPTION_KEY="$(openssl rand -base64 32)"
docker compose -f infra/compose.yaml -f infra/compose.prod.yaml run --rm -e ROTATE_NEW_CPF_ENCRYPTION_KEY backend rotate-cpf-key --simulate
```

```bash
docker compose -f infra/compose.yaml -f infra/compose.prod.yaml stop web backend
docker compose -f infra/compose.yaml -f infra/compose.prod.yaml run --rm -e ROTATE_NEW_CPF_ENCRYPTION_KEY backend rotate-cpf-key
```

Depois de conferir as contagens, troque `CPF_ENCRYPTION_KEY` (e/ou `HMAC_SECRET`) no `infra/.env` pelo valor novo, religue com
`docker compose -f infra/compose.yaml -f infra/compose.prod.yaml up -d` e rode `unset ROTATE_NEW_CPF_ENCRYPTION_KEY ROTATE_NEW_HMAC_SECRET ROTATE_OLD_CPF_ENCRYPTION_KEY ROTATE_OLD_HMAC_SECRET`.
Guarde as chaves novas no cofre junto do backup e **não** apague as antigas antes de confirmar que o sistema subiu e lê os CPFs.

### Privacidade do CPF na infraestrutura

- **O n8n não guarda execuções.** O update em que o professor digita o CPF no cadastro pelo Telegram passa pelo n8n, e o backend
  manda apagar essa mensagem no chat (ação `DELETE_MESSAGE`). Por isso o banco do n8n e o `binaryData` ficam em memória (`tmpfs`)
  e nenhuma execução é persistida: **não ligue** persistência de execuções, exportação de logs de execução nem editor em produção.
- **O proxy não deve registrar a query string.** A busca de professores e alunos usa `?search=` (nome ou instrumento) e a
  interface recusa termo com forma de CPF, mas, por defesa em profundidade, o `Caddyfile` não habilita registro de acesso. Se algum
  dia habilitar, descarte a query do URI no registro.

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
   - `AUTH_HMAC_SECRET` novo (`openssl rand -base64 48`, diferente do `HMAC_SECRET`; o backend não sobe sem ele) e as três
     `ADMIN_BOOTSTRAP_*` do primeiro administrador (seção 2; esvazie a senha depois do primeiro acesso);
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

## Carga dos consentimentos já coletados (feature 007)

Antes de liberar o uso com dados reais, registre o consentimento dos alunos menores já cadastrados (a escola já o coletou).
O arquivo CSV **fica fora do repositório** (o comando recusa caminho dentro dele) e **não leva CPF nem nome do aluno**:

```bash
sh infra/scripts/local.sh consent-load /caminho/fora/do/repositorio/consentimentos.csv
```

Formato (UTF-8, separador `;`, cabeçalho obrigatório): `student_id;consenting_name;relationship;consented_on;term_version;collection_method`.
O resumo mostra quantas linhas foram aceitas e, para as recusadas, só o número da linha e o código do erro. Ao final, imprime
quantos alunos menores ainda estão sem consentimento válido (deve ser 0 antes de liberar o uso). Na VPS, copie o arquivo por SSH,
rode o mesmo comando e **apague o arquivo da VPS** depois. Contrato: `specs/007-privacy-retention-operations/contracts/consent-load.md`.
