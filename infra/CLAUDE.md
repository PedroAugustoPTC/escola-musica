# infra/

Contêineres, ambiente local e implantação. **Todo serviço roda em contêiner**, local e em produção; a
máquina só precisa de Docker Engine 27+ com Compose v2 (constituição, "Containerização e implantação").

- `compose.yaml` é a base (`web` = Caddy + SPA, `backend`, `postgres`, `telegram-bot-api`; o n8n ainda será incluído);
  `compose.dev.yaml`, `compose.prod.yaml`, `compose.test.yaml` e `compose.database.yaml` são camadas por ambiente.
  O projeto do compose, o banco e o usuário do PostgreSQL chamam-se `reports`; os volumes são `pgdata`,
  `documents`, `telegram-bot-api-data`, `caddy-data` e `caddy-config`, e a rede é `internal`.
- `caddy/Caddyfile`: HTTPS, cabeçalhos de segurança e proxy de `/api/*`. Só a entrada HTTPS é pública.
- `scripts/local.sh` sobe e opera o ambiente local (`up`, `seed`, `code`, `database`, `database-port`,
  `database-close`, `status`, `logs`, `tests`, `stop`, `clean`; `up --demo-data=true|false`);
  `scripts/deploy.sh` atualiza a pilha na VPS. Passo a passo em `README.md` e `scripts/README.md`.
- `seed/`: cargas de cadastro. Só `example.json` e `demo.json` (fictícios) são versionados; `infra/seed/*.json`
  é ignorado pelo Git e pelo contexto de build do Docker (dado pessoal real nunca entra).

Regras:

- Imagens com **versão fixada** (nunca `latest`); local e produção usam as mesmas definições, variando só a configuração.
- Segredos nunca em imagem nem no Git: `infra/.env` não é versionado; atualize `.env.example` ao criar variável.
  Chaves de ambiente em inglês (`DATABASE_*`, `CPF_ENCRYPTION_KEY`, `HMAC_SECRET`, `TELEGRAM_*`, `AI_PROVIDERS`…).
- Quem tem `infra/.env` ou volumes anteriores à feature 003 precisa regerar o `.env` e migrar os dados: o `local.sh up`
  recusa o arquivo antigo e a seção "Se você tem um ambiente anterior à feature 003" de `README.md` explica como.
- Quando o n8n entrar, a imagem terá versão fixada e os workflows serão versionados como código, sem segredo nem ID de credencial nos JSON (constituição v1.4.0).
- Dado pessoal real nunca em `seed/` versionada.
- Mudou serviço, variável ou comando? Atualize `README.md` e `scripts/README.md`.
