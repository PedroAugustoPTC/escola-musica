# Ambiente local com Docker — `local.sh`

Roteiro para subir o sistema inteiro na sua máquina, usar o portal, consultar o banco como
administrador e rodar os testes. Tudo roda em contêineres: a máquina só precisa do Docker.

Para a implantação na VPS, use `deploy.sh` e o roteiro de [`infra/README.md`](../README.md).

## O que sobe

| Serviço | Para que serve | Acesso pela sua máquina |
|---|---|---|
| `web` (Caddy) | portal do professor (SPA) e entrada HTTPS; repassa `/api/*` ao backend | **https://localhost** (portas 443 e 80, só em `127.0.0.1`) |
| `backend` (Spring Boot) | API, processamento dos áudios, PDFs e bot | não exposto (só pela rede interna) |
| `postgres` (PostgreSQL 18) | banco `reports` | não exposto; veja [Acessar o banco](#acessar-o-banco-como-administrador) |
| `telegram-bot-api` | servidor local da API de bots | não exposto; só sobe com `TELEGRAM_ENABLED=true` |
| `n8n` | transporte do canal Telegram: recebe o update, repassa ao backend e executa as ações; os workflows vêm do repositório | não exposto; o editor abre em `http://127.0.0.1:5678` (só local); veja `infra/n8n/CLAUDE.md` |

Os dados (banco, PDFs gerados, certificados do Caddy) ficam em volumes do Docker e sobrevivem a
`stop` e a reinícios do computador. Só `clean` os apaga.

## Passo a passo (primeira vez)

Todos os comandos rodam **na raiz do repositório** (`E:\Projetos\escola-musica`). No Windows, use o
**Git Bash** (ou o terminal do VS Code configurado com Git Bash).

### 1. Instale e abra o Docker

Instale o **Docker Desktop** (Docker Engine 27+ com Compose v2) e deixe-o aberto. Para conferir:

```bash
docker info
```

Se aparecer `failed to connect to the docker API`, o Docker Desktop está fechado: abra-o e espere o
ícone ficar verde.

### 2. Crie o `infra/.env`

```bash
cp infra/.env.example infra/.env
```

Preencha os segredos locais. Para gerar cada valor:

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
`HMAC_SECRET`. Para começar sem o bot e sem custos de IA, deixe:

```text
TELEGRAM_ENABLED=false
AI_PROVIDERS=simulated
```

O `infra/.env` nunca vai para o Git (está no `.gitignore`). Cada variável está explicada no
[`infra/.env.example`](../.env.example).

### 3. Suba a pilha — com ou sem massa

Escolha como quer começar:

```bash
sh infra/scripts/local.sh up --demo-data=true
```

```bash
sh infra/scripts/local.sh up --demo-data=false
```

| Opção | O que acontece |
|---|---|
| `--demo-data=true` | **zera** os dados locais (banco e PDFs) e sobe com a [massa de demonstração](#massa-de-demonstração): professores, alunos, aulas aprovadas com PDFs, semestrais e relatórios em aberto. Ideal para testar logo. |
| `--demo-data=false` | **zera** os dados locais e sobe só o sistema, sem nenhum dado. Para começar do zero. |
| sem `--demo-data` | sobe mantendo os dados que já existem (o uso do dia a dia, depois da primeira vez). |

A primeira vez demora alguns minutos (build das imagens do backend e do portal); a massa leva cerca
de mais um minuto. O comando confere o `infra/.env`, sobe os serviços e espera todos ficarem
saudáveis. No fim aparece:

```text
Portal: https://localhost  (certificado local do Caddy: aceite no navegador)
```

`--demo-data=true` e `--demo-data=false` apagam o banco e os PDFs locais (os certificados do Caddy e o volume
do bot são mantidos). Não afetam a pilha de testes nem a produção.

### 4. Pegue o código de acesso

Com `--demo-data=true`, a saída termina com uma linha por professor:

```text
clara@example.test → XXXX-XXXX (válido até …)
bruno@example.test → XXXX-XXXX (válido até …)
```

Esse é o código de acesso. Ele só aparece nessa saída (no banco fica apenas o HMAC). Se perder,
gere outro (o anterior deixa de valer):

```bash
sh infra/scripts/local.sh code clara@example.test
```

Com `--demo-data=false`, o sistema começa sem professores. Para cadastrar os de exemplo (só cadastros,
sem relatórios):

```bash
sh infra/scripts/local.sh seed
```

Para carregar outro arquivo, coloque-o em `infra/seed/` (cargas reais nunca vão para o Git) e
passe o caminho: `sh infra/scripts/local.sh seed infra/seed/my-data.json`. O formato está em
[`contracts/carga-inicial.md`](../../specs/001-fluxo-relatorios/contracts/carga-inicial.md).

### 5. Abra o portal

1. Acesse **https://localhost** (use `https://`: o cookie de sessão só funciona com HTTPS).
2. Na primeira vez o navegador avisa que o certificado não é confiável, porque é emitido pela CA
   interna do Caddy. Clique em **Avançado → Continuar para localhost**.
3. Entre com o código da Clara.
4. Escolha um aluno (com a massa, Lucas ou Sofia não têm aulas ainda) → **Registrar aula** → envie um arquivo de áudio (MP3, M4A, OGG, WAV, WebM ou
   vídeo MP4, até 50 MB) ou grave pelo microfone.

Com `AI_PROVIDERS=simulated`, o sistema não chama a IA: qualquer áudio com som gera o mesmo
relatório de exemplo, com duas perguntas pendentes. Serve para testar o fluxo completo de graça.

## Uso no dia a dia

| Comando | O que faz |
|---|---|
| `sh infra/scripts/local.sh up` | sobe (ou atualiza, após mudanças no código) a pilha mantendo os dados |
| `sh infra/scripts/local.sh up --demo-data=true` | zera os dados locais e sobe com a massa de demonstração |
| `sh infra/scripts/local.sh up --demo-data=false` | zera os dados locais e sobe o sistema vazio |
| `sh infra/scripts/local.sh status` | estado de cada serviço |
| `sh infra/scripts/local.sh logs` | acompanha os logs do backend (`Ctrl+C` sai); `logs web` ou `logs postgres` para os outros |
| `sh infra/scripts/local.sh seed [file]` | carga inicial de professores e alunos |
| `sh infra/scripts/local.sh code <email>` | novo código de acesso para um professor |
| `sh infra/scripts/local.sh database` | abre o `psql` no banco local |
| `sh infra/scripts/local.sh database-port` / `database-close` | publica / fecha o banco em `127.0.0.1` para DBeaver ou pgAdmin |
| `sh infra/scripts/local.sh tests [suite]` | roda os testes em contêiner: `backend`, `frontend`, `e2e`, `real-ai` ou `all` |
| `sh infra/scripts/local.sh stop` | para tudo, mantendo os dados |
| `sh infra/scripts/local.sh clean` | para e **apaga** banco, PDFs e certificados locais (pede confirmação) |
| `sh infra/scripts/local.sh help` | lista os comandos |

Depois de mudar código do backend ou do portal, rode `up` de novo: ele reconstrói as imagens e
recria só o que mudou.

## Massa de demonstração

Criada por `up --demo-data=true` a partir de [`infra/seed/demo.json`](../seed/demo.json).
**Todos os dados são fictícios** (nomes, CPFs e responsáveis inventados). Os relatórios são criados
pelas mesmas regras de domínio e pelo mesmo gerador de PDF do sistema, com datas relativas ao dia em
que você sobe a pilha (semestre atual e anterior).

| Professor | Aluno | O que já existe |
|---|---|---|
| Clara Souza | Matteo Mafra (Piano) | ~12 aulas aprovadas no semestre anterior e ~10 no atual; **semestral do semestre anterior aprovado** |
| Clara Souza | Olavo Torrente (Violão) | ~8 + ~8 aulas aprovadas; **semestral do semestre anterior em conferência**; **aula de ontem aguardando conferência** |
| Clara Souza | Miguel de Castro Torrente (Violino) | ~6 aulas aprovadas no semestre atual; **aula de hoje aguardando respostas** (2 perguntas) |
| Clara Souza | Beatriz Arruda (Canto) | 4 aulas aprovadas no semestre atual |
| Clara Souza | Lucas Pimentel (Piano), Sofia Rangel (Violão) | nada — para testar histórico vazio e o primeiro registro |
| Bruno Lima | Helena Prado (Flauta), Davi Coutinho (Bateria) | algumas aulas aprovadas — para testar o acesso cruzado (Clara não pode ver) |

Cada aula aprovada tem o PDF de conferência e o PDF oficial; o semestral aprovado também. Os
relatórios aprovados alternam o canal (portal e Telegram), como aconteceria no uso real. No total
são cerca de 57 aulas aprovadas, 2 aulas em aberto, 2 semestrais e 118 PDFs.

O que dá para testar logo de cara (entrando como Clara):

- **Painel**: 3 relatórios em aberto (Olavo — aula e semestral; Miguel — perguntas).
- **Histórico** de Matteo: aulas dos dois semestres e o semestral, com "Abrir PDF".
- **Retomada**: "Continuar" no relatório de Olavo (conferência) ou de Miguel (perguntas).
- **Semestral**: gerar o do semestre atual de Matteo ou de Beatriz; ver o de Olavo em conferência;
  tentar gerar de novo o de Matteo (já aprovado → recusado).
- **Telas vazias**: histórico de Lucas ou Sofia.
- **Acesso cruzado**: entrar como Bruno (código dele na mesma saída) e conferir que os alunos da
  Clara não aparecem.

O comando que gera a massa (`demo-data`, dentro do backend) só funciona com o banco vazio — por isso
nunca mistura dados fictícios com dados reais.

## Acessar o banco como administrador

Credenciais do banco local:

| Campo | Valor |
|---|---|
| Banco | `reports` |
| Usuário | `reports` |
| Senha | o valor de `POSTGRES_PASSWORD` no `infra/.env` |

### Opção 1 — `psql` no terminal (recomendada)

```bash
sh infra/scripts/local.sh database
```

Abre o `psql` dentro do contêiner, sem senha e sem abrir porta nenhuma. Comandos úteis:

| Comando | Para quê |
|---|---|
| `\dt` | lista as tabelas |
| `\d lesson_report` | estrutura de uma tabela |
| `select id, name, email from teacher;` | uma consulta qualquer (termine com `;`) |
| `\x` | alterna a exibição vertical (bom para linhas largas) |
| `\q` | sai |

Também aceita SQL pela entrada padrão:

```bash
echo "select status, count(*) from lesson_report group by status;" | sh infra/scripts/local.sh database
```

Equivalente sem o script:

```bash
docker compose -f infra/compose.yaml -f infra/compose.dev.yaml exec postgres psql -U reports -d reports
```

### Opção 2 — ferramenta gráfica (DBeaver, pgAdmin, DataGrip)

O banco não fica exposto por padrão (Princípio IV). Para usar uma ferramenta gráfica, publique a
porta **só na sua máquina** enquanto precisar:

```bash
sh infra/scripts/local.sh database-port
```

Na ferramenta, crie uma conexão PostgreSQL com host `localhost`, porta `5432` e as credenciais da
tabela acima. Ao terminar:

```bash
sh infra/scripts/local.sh database-close
```

Se você já tem um PostgreSQL instalado no Windows usando a porta 5432, defina outra no `infra/.env`
(por exemplo `LOCAL_DATABASE_PORT=5433`) e use essa porta na ferramenta. A porta é publicada por
[`infra/compose.database.yaml`](../compose.database.yaml), que nunca deve ser usado em produção.

### Opção 3 — produção (VPS)

Em produção o banco **nunca** é publicado. Entre na VPS por SSH e use o `psql` dentro do contêiner:

```bash
docker compose -f infra/compose.yaml -f infra/compose.prod.yaml exec postgres psql -U reports -d reports
```

Para uma ferramenta gráfica, use um túnel SSH até o IP interno do contêiner, sem abrir porta no
firewall.

### O que esperar nos dados

- **CPF**: fica cifrado (`student.cpf_encrypted`, AES-256-GCM); nem o administrador vê o CPF em claro.
- **Códigos de acesso e sessões**: só os hashes (`teacher.access_code_hash`, `web_session.token_hash`).
- **Áudio**: não existe em nenhuma tabela; é apagado logo após a transcrição (FR-018).
- **Evite alterar dados à mão.** Relatórios e versões têm regras (versão vigente, aprovação,
  cancelamento) que só a aplicação garante. Para cadastros, use o `seed`; para códigos, o `code`.

## Ligar a IA real e o bot do Telegram

**IA real** (Groq por padrão; chave em https://console.groq.com/keys):

```text
AI_PROVIDERS=real
TRANSCRIPTION_API_KEY=gsk_...
LLM_API_KEY=gsk_...
```

**Bot do Telegram** (use um bot de testes):

```text
TELEGRAM_ENABLED=true
TELEGRAM_BOT_TOKEN=...        # @BotFather
TELEGRAM_API_ID=...           # https://my.telegram.org → API development tools
TELEGRAM_API_HASH=...
TELEGRAM_WEBHOOK_SECRET=...  # openssl rand -hex 32
N8N_ENCRYPTION_KEY=...        # openssl rand -hex 32 (o n8n sobe junto com o bot)
```

Se o bot já foi usado na API da nuvem (por exemplo, no MVP com n8n), faça uma vez o `logOut` e não
rode o MVP ao mesmo tempo:

```bash
curl -s "https://api.telegram.org/bot<TOKEN>/logOut"
```

Depois de editar o `infra/.env`, rode `up` de novo. O comando avisa se faltar alguma credencial.
No Telegram, envie o código de acesso ao bot para vincular a conta.

## Operar o n8n e o transporte

```bash
sh infra/scripts/n8n.sh health [--deep]       # saúde do n8n (e, com --deep, do backend e da API de bots)
sh infra/scripts/n8n.sh check                 # a instância diverge dos workflows do repositório?
sh infra/scripts/n8n.sh export                # exporta da instância, normaliza e grava em infra/n8n/workflows/
sh infra/scripts/telegram-webhook.sh status   # webhook registrado e saúde do n8n
sh infra/scripts/telegram-webhook.sh verify   # verifica o webhook e as sondas (somente leitura, exceto as sondas)
sh infra/scripts/telegram-webhook.sh apply    # registra o webhook do n8n e verifica
sh infra/scripts/rotate-service-token.sh start|finish|abort
```

`local.sh up` e `deploy.sh` já registram o webhook do n8n (`telegram-webhook.sh apply`). O n8n é o único transporte: não
há troca nem `rollback` (reverter é reimplantar a versão anterior). O passo a passo do registro e da rotação está em
[`infra/README.md`](../README.md).

## Repetir só no limite de taxa do registro (CI)

`sh infra/scripts/retry-rate-limit.sh <comando>` repete o comando (até 5 vezes, esperando 30 s a mais a cada tentativa) quando a saída traz `429 Too Many Requests`; qualquer outra falha sai na hora com o mesmo código. É o que o job `n8n` do CI usa nos passos que constroem a imagem do n8n.

## Testes

```bash
sh infra/scripts/local.sh tests
```

| Suíte | O que roda |
|---|---|
| `backend` | unitários, arquitetura, integração com PostgreSQL, contrato da API, conversa do bot, paridade; JaCoCo ≥ 90% no `core` |
| `frontend` | ESLint, TypeScript e Vitest |
| `e2e` | Playwright + axe contra uma pilha própria de testes (IA simulada); relatório em `frontend/playwright-report/` |
| `real-ai` | gravações de `assets/` com a IA real (usa as chaves do `infra/.env`); gera `backend/application/target/real-ai/report.md` |
| `n8n` | o n8n real em contêiner: utilitários, contrato dos workflows e a suíte de conversa e de paridade pelo n8n (com duplicidade, queda, privacidade e latência) |
| `rehearsal` | ensaio do registro do webhook e da rotação do token de serviço, com o Telegram simulado |
| `all` | `backend`, `frontend`, `e2e`, `n8n` e `rehearsal` |

Os testes usam bancos e contêineres próprios (`infra/compose.test.yaml`): não tocam nos dados da
pilha local.

## Solução de problemas

| Sintoma | Causa provável e solução |
|---|---|
| `failed to connect to the docker API` | Docker Desktop fechado: abra-o e espere ficar pronto. |
| `Preencha em infra/.env: ...` | Falta algum valor obrigatório; o próprio aviso diz quais. Se o seu `infra/.env` é anterior à feature 003, as chaves mudaram de nome: regere-o a partir do `infra/.env.example` (veja [`infra/README.md`](../README.md#se-você-tem-um-ambiente-anterior-à-feature-003)). Sem bot: `TELEGRAM_ENABLED=false`; sem chaves: `AI_PROVIDERS=simulated`. |
| `Algum serviço ficou unhealthy` | Veja `sh infra/scripts/local.sh logs` (ou `logs web`). O erro mais comum é segredo inválido (ex.: `CPF_ENCRYPTION_KEY` que não é Base64 de 32 bytes). |
| `port is already allocated` (80 ou 443) | Outro programa usa a porta (IIS, Skype, outro Docker). Feche-o ou rode `docker ps` para achar o contêiner. |
| Navegador recusa o certificado | Normal no ambiente local: **Avançado → Continuar para localhost**. |
| Entro com o código e volto para a tela de entrada | Acesse por `https://` (o cookie de sessão exige HTTPS). |
| "Acesso bloqueado temporariamente" | 5 códigos errados seguidos: espere 15 minutos ou gere um código novo. |
| A carga diz que não leu o arquivo | O arquivo precisa estar em `infra/seed/` e ser JSON válido. |
| Os serviços sumiram depois de reiniciar o PC | Na pilha local eles não reiniciam sozinhos: rode `up` (os dados continuam lá). |
| Quero começar do zero | `sh infra/scripts/local.sh up --demo-data=false` (ou `--demo-data=true` para já ter dados). |
| `A massa de demonstração só é criada em banco vazio` | Rode pelo script (`up --demo-data=true`), que zera os dados antes. |
