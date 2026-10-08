# infra/

Contêineres, ambiente local e implantação. **Todo serviço roda em contêiner**, local e em produção; a
máquina só precisa de Docker Engine 27+ com Compose v2 (constituição, "Containerização e implantação").

- `compose.yaml` é a base (`web` = Caddy + SPA, `backend`, `postgres`, `telegram-bot-api`; o n8n ainda será incluído);
  `compose.dev.yaml`, `compose.prod.yaml`, `compose.test.yaml` e `compose.banco.yaml` são camadas por ambiente.
- `caddy/Caddyfile`: HTTPS, cabeçalhos de segurança e proxy de `/api/*`. Só a entrada HTTPS é pública.
- `scripts/local.sh` sobe e opera o ambiente local (`subir`, `carga`, `testes`, `logs`, `parar`, `limpar`);
  `scripts/implantar.sh` atualiza a pilha na VPS Hostinger. Passo a passo em `README.md` e `scripts/README.md`.
- `carga/`: cargas de cadastro. Só `exemplo.json` e `demonstracao.json` (fictícios) são versionados.

Regras:

- Imagens com **versão fixada** (nunca `latest`); local e produção usam as mesmas definições, variando só a configuração.
- Segredos nunca em imagem nem no Git: `infra/.env` não é versionado; atualize `.env.example` ao criar variável.
- Quando o n8n entrar, a imagem terá versão fixada e os workflows serão versionados como código, sem segredo nem ID de credencial nos JSON (constituição v1.4.0).
- Dado pessoal real nunca em `carga/` versionada.
- Mudou serviço, variável ou comando? Atualize `README.md` e `scripts/README.md`.
