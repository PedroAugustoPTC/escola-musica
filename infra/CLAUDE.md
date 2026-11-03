# infra/

Contêineres, ambiente local e implantação. **Todo serviço roda em contêiner**, local e em produção; a
máquina só precisa de Docker Engine 27+ com Compose v2 (constituição, "Containerização e implantação").

- `compose.yaml` é a base (`web` = Caddy + SPA, `backend`, `postgres`, `telegram-bot-api` e `n8n`);
  `compose.dev.yaml`, `compose.prod.yaml`, `compose.test.yaml` e `compose.database.yaml` são camadas por ambiente.
  O projeto do compose, o banco e o usuário do PostgreSQL chamam-se `reports`; os volumes são `pgdata`,
  `documents`, `telegram-bot-api-data`, `n8n-data`, `caddy-data` e `caddy-config`, e a rede é `internal`.
- `n8n/`: imagem fina do n8n, workflows (`workflows/*.json`), utilitários (`ops/`) e `entrypoint.sh`. Leia `n8n/CLAUDE.md` antes de mexer.
- `tests/n8n/`: `Dockerfile` do `tests-n8n` (Maven + `docker` CLI; o socket do Docker só é montado em `compose.test.yaml`).
- `caddy/Caddyfile`: HTTPS, cabeçalhos de segurança e proxy de `/api/*`. Só a entrada HTTPS é pública.
- `scripts/telegram-webhook.sh` (`status`, `verify`, `apply`) é o único dono do registro do webhook, que aponta para o n8n (o
  único transporte do Telegram; não há `switch` nem `rollback`: reverter é reimplantar a versão anterior).
- `scripts/local.sh` sobe e opera o ambiente local (`up`, `seed`, `issue-code` (alias obsoleto: `code`), `rotate-cpf-key`,
  `database`, `database-port`, `database-close`, `status`, `logs`, `tests`, `stop`, `clean`; `up --demo-data=true|false`). O `up`
  gera o `AUTH_HMAC_SECRET` local e, se as três `ADMIN_BOOTSTRAP_*` estão vazias, preenche o administrador fictício
  `admin@example.test` com uma senha provisória impressa **uma única vez**;
  `scripts/deploy.sh` atualiza a pilha na VPS. Passo a passo em `README.md` e `scripts/README.md`.
- `seed/`: cargas de cadastro. Só `example.json` e `demo.json` (fictícios) são versionados; `infra/seed/*.json`
  é ignorado pelo Git e pelo contexto de build do Docker (dado pessoal real nunca entra).

Regras:

- Imagens com **versão fixada** (nunca `latest`); local e produção usam as mesmas definições, variando só a configuração.
- Segredos nunca em imagem nem no Git: `infra/.env` não é versionado; atualize `.env.example` ao criar variável.
  Chaves de ambiente em inglês (`DATABASE_*`, `CPF_ENCRYPTION_KEY`, `HMAC_SECRET`, `TELEGRAM_*`, `AI_PROVIDERS`…).
- **Feature 005 (administração)**: o `.env` ganha `AUTH_HMAC_SECRET` (obrigatório, ≥ 32 bytes e **diferente** do `HMAC_SECRET`; o
  backend recusa subir de outro modo), `ADMIN_BOOTSTRAP_NAME|EMAIL|PASSWORD` (**vazias** no repositório, sem credencial padrão;
  só valem em banco sem administrador; esvazie a senha depois do primeiro acesso) e variáveis opcionais com padrão (sessão do
  administrador, custo do Argon2id, limites contra sondagem de CPF, validade do rascunho do Telegram). A tabela completa, o
  procedimento de recuperação (`DELETE FROM administrator;`) e a rotação das chaves do CPF estão em `README.md`. O compose repassa
  as variáveis só em `compose.yaml`; `compose.dev.yaml` e `compose.prod.yaml` herdam o `environment` (não repita ali).
- O proxy **não deve registrar a query string**: a busca de alunos e professores usa `?search=` (nome ou instrumento) e o CPF nunca
  deve chegar à URL. O `Caddyfile` não habilita `log`; se algum dia habilitar registro de acesso, descarte a query do URI.
- O n8n não guarda execuções: o update com o CPF que o professor digita no cadastro pelo Telegram passa por ele (e a ação
  `DELETE_MESSAGE` apaga essa mensagem no chat). Não ligue persistência de execuções, `binaryData` em disco nem exportação de logs
  de execução do n8n.
- Quem tem `infra/.env` ou volumes anteriores à feature 003 precisa regerar o `.env` e migrar os dados: o `local.sh up`
  recusa o arquivo antigo e a seção "Se você tem um ambiente anterior à feature 003" de `README.md` explica como.
- O n8n (`n8n/`, tem `CLAUDE.md` próprio) usa imagem de versão fixada e workflows versionados como código, sem segredo nem ID de credencial nos JSON; nunca publica porta (só o editor em `127.0.0.1`, no ambiente local) e não tem rota no Caddy.
- Dado pessoal real nunca em `seed/` versionada.
- Mudou serviço, variável ou comando? Atualize `README.md` e `scripts/README.md`.
