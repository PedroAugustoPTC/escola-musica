# infra/tests/security/

Ferramental da avaliação de segurança em contêiner (`sh infra/scripts/local.sh tests security`, fora do `all`).

| Arquivo | Papel |
|---|---|
| `run.sh` | Orquestra: varreduras estáticas, sobe a pilha e2e, imagens, TLS, semeadura, DAST e sondas; termina com `down -v` |
| `probes.sh` | Sondas dirigidas (BOLA/IDOR, papéis, sessão, CSRF, força bruta, upload, injeção, exposição, cabeçalhos, canal interno); grava JSON Lines |
| `zap-auth.sh`, `zap/automation.yaml` | Login por `curl`, injeção de cookie e CSRF e plano do ZAP (um por papel) |
| `seed.mjs` | Semeia admin, 2 professores e 4 alunos fictícios reaproveitando `frontend/tests/e2e/support.ts` |
| `gitleaks.toml` | Regras do gitleaks e exceção das credenciais fixas de teste |

Regras:

- Atacar **só** `web-e2e`/`backend-e2e` (pilha descartável com IA simulada). Nunca apontar para o ambiente local nem para a VPS.
- A senha do administrador de teste é gerada a cada execução e passada por ambiente; nunca em arquivo.
- Imagens das ferramentas de varredura fixadas por tag e digest (`infra/compose.security.yaml`; `node` e `alpine` só por tag, como no `compose.test.yaml`); atualizar exige reexecutar e conferir o `ComposeGuardTest`.
- Saídas em `security/out/` (ignorado pelo Git).
