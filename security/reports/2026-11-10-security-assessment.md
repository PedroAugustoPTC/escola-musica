# Avaliação de segurança — 2026-11-10

Relatório técnico da bateria de testes de vulnerabilidades básicas (OWASP Top 10 / API Top 10), feita **100% em
contêineres** contra a pilha isolada de testes. Este trabalho **não corrige nada**: só mede e recomenda.

## 1. Sumário executivo

| Severidade | Achados |
|---|---|
| Alta | 1 (SEC-001) |
| Média | 5 (SEC-002 a SEC-006) |
| Baixa | 4 (SEC-007 a SEC-010) |
| Informativa | 3 (SEC-011 a SEC-013) |

O núcleo de autorização está sólido: isolamento entre professores (BOLA/IDOR), separação de papéis, CSRF, sessão,
bloqueio de força bruta e cabeçalhos passaram em todas as sondas. Nenhum SQL injetável e nenhum segredo real no
repositório rastreado. Os riscos concentram-se em **cadeia de suprimentos e hardening de imagens**.

Cinco riscos principais:

1. **SEC-001** — imagem do n8n com 19 CVEs críticas e 72 altas (base Alpine com `openssh` e `simple-git`/`handlebars`).
2. **SEC-002** — `/actuator/prometheus` sem autenticação para qualquer contêiner da rede interna.
3. **SEC-003** — arquivo aceito pelo tipo declarado, sem verificação do conteúdo (HTML/binário como `audio/mpeg`).
4. **SEC-004** — `frontend/Dockerfile` roda o Caddy como `root`.
5. **SEC-005** — GitHub Actions por tag mutável (`@v4`/`@v5`), sem fixar por SHA.

## 2. Escopo, ambiente e limitações

- Commit avaliado: `cc9d18c` (branch `chore/security-assessment`, árvore de trabalho com o ferramental novo).
- Ferramentas (imagens de varredura fixadas por tag e digest; `node` e `alpine` só por tag em `infra/compose.security.yaml`): gitleaks v8.28.0, semgrep 1.130.0,
  trivy 0.65.0, testssl.sh 3.2, ZAP 2.16.1, alpine 3.22 (sondas com `curl` e `jq`).
- Alvo dinâmico: `postgres-e2e` + `backend-e2e` + `web-e2e` (IA simulada, banco descartável). Nunca o ambiente local nem a VPS.
- Saídas brutas: `security/out/` (fora do Git).

**Não testado / limitações**

- VPS, n8n real, provedores de IA reais e Telegram real.
- **SCA do Java incompleto**: o Trivy não resolve as versões do BOM do Spring Boot (`Dependency version cannot be
  determined`), então `trivy-fs.json` não traz CVEs de `backend/**/pom.xml`. O resultado "zero CVEs" do backend **não é
  evidência de ausência**. Ver SEC-012.
- **Imagem `reports-backend`**: a varredura de imagem só havia terminado para `reports-web` e `reports-n8n` na data da
  escrita; reexecute `sh infra/scripts/local.sh tests security` para completar.
- O DAST do ZAP rodou com o spider e o OpenAPI das specs 001 e 005 e scan ativo autenticado, uma vez por papel; rotas
  destrutivas ficaram fora (cobertas pelas sondas). Cobertura de rotas das specs 006 e 007 não foi garantida.
- A cadeia de certificados do TLS (CA interna do Caddy, intermediário que expira em 17/11) é artefato do ambiente de
  teste e foi descartada (seção 7).

## 3. Tabela-resumo

| ID | Título | Severidade (CVSS 3.1) | Categoria OWASP / CWE | Componente | Status |
|---|---|---|---|---|---|
| SEC-001 | Imagem do n8n com CVEs críticas e altas | Alta (8.1) | A06 / CWE-1104, CWE-1395 | `infra/n8n/Dockerfile` | Aberto |
| SEC-002 | `/actuator/prometheus` sem autenticação na rede interna | Média (5.3) | A05 / CWE-306 | `SecurityConfig.java:57-59` | Aberto |
| SEC-003 | Upload aceito pelo tipo declarado, sem verificar o conteúdo | Média (5.4) | A04 / CWE-434, CWE-345 | `WebAudioReception.java:74` | Aberto |
| SEC-004 | Contêiner do `web` roda como `root` | Média (5.9) | A05 / CWE-250 | `frontend/Dockerfile` | Aberto |
| SEC-005 | GitHub Actions por tag mutável | Média (5.0) | A08 / CWE-829 | `.github/workflows/ci.yml` | Aberto |
| SEC-006 | Dependências do frontend com CVEs (`undici`, `vite`, `zod`) | Média (5.3) | A06 / CWE-1104 | `frontend/package-lock.json` | Aberto |
| SEC-007 | CSP com `style-src 'unsafe-inline'` | Baixa (3.7) | A05 / CWE-693 | `infra/caddy/Caddyfile:19` | Aberto |
| SEC-008 | Imagem do `web` (Caddy) com 2 CVEs altas em bibliotecas Go | Baixa (3.7) | A06 / CWE-1104 | `reports-web` | Aberto |
| SEC-009 | Nome de aluno aceita HTML e é armazenado sem validação | Baixa (3.1) | A03 / CWE-79 | `POST /api/v1/admin/students` | Aberto (defesa em profundidade) |
| SEC-010 | Dockerfiles sem `HEALTHCHECK`, `apt-get` sem `--no-install-recommends` | Baixa (2.0) | A05 / CWE-1188 | Dockerfiles | Aberto |
| SEC-011 | SQL dinâmico com concatenação de enum | Informativa | A03 / CWE-89 | `OperationalAlertRepositoryAdapter.java:97` | Aberto (hardening) |
| SEC-012 | SCA do Java não conclui offline | Informativa | A06 | `compose.security.yaml` | Aberto |
| SEC-013 | Entrada de CPF/segredo detectada pelo gitleaks em fixtures | Informativa | A02 | testes | Aceito |

## 4. Achados

### SEC-001 — Imagem do n8n com CVEs críticas e altas

- **Descrição**: `trivy image reports-n8n` encontrou 19 críticas, 72 altas, 128 médias e 56 baixas. As críticas vêm do
  Alpine 3.24 (`openssh*` 10.3_p1-r0, `brotli-libs` 1.2.0-r1) e de pacotes Node (`simple-git` 3.36.0,
  `@simple-git/argv-parser` 1.1.1, `handlebars` 4.7.9). Altas incluem `libexpat`, `libcrypto3` e `libcurl`.
- **Evidência**: `docker compose -f infra/compose.test.yaml -f infra/compose.security.yaml run --rm security-images`;
  ver `security/out/images/reports-n8n.json`.
- **Afetado**: `infra/n8n/Dockerfile:7` (`FROM docker.n8n.io/n8nio/n8n:2.43.6`).
- **Impacto**: o n8n processa o update do Telegram (inclui o CPF digitado no cadastro). Sem porta pública e sem rota no
  Caddy, mas a superfície interna continua exposta a qualquer contêiner comprometido.
- **Correção**: atualizar a tag do n8n para uma versão com base Alpine corrigida e, se preciso, `apk upgrade` na imagem
  fina; remover `openssh` se o n8n não o usa. Reavaliar a cada atualização.
- **Teste de regressão**: etapa de CI com `trivy image --severity CRITICAL --exit-code 1 reports-n8n`.
- **Esforço**: baixo a médio (validar workflows após o upgrade).

### SEC-002 — `/actuator/prometheus` sem autenticação na rede interna

- **Descrição**: `GET http://backend:8080/actuator/prometheus` devolve 200 com métricas, sem credencial. Pelo proxy
  público o caminho cai no fallback do SPA (não vaza), então a exposição é só interna.
- **Evidência**: sonda `INTERNAL-prometheus` (`security/out/probes/results.jsonl`).
- **Afetado**: `SecurityConfig.java:57-59` (rota liberada) e `application.yaml:49-53` (exposição de `prometheus`).
- **Impacto**: qualquer contêiner da rede lê métricas (nomes de rotas, contadores, uso de JVM).
- **Correção**: servir o actuator em porta de gerenciamento separada (`management.server.port`) sem publicação, ou exigir
  autenticação; manter só `health` liberado.
- **Teste de regressão**: `IT` que chama `/actuator/prometheus` sem credencial e espera 401/403.
- **Esforço**: baixo.

### SEC-003 — Upload aceito pelo tipo declarado, sem verificar o conteúdo

- **Descrição**: `WebAudioReception.type()` confia no `Content-Type` do multipart. Um HTML e 2 KB aleatórios declarados
  `audio/mpeg` foram aceitos (HTTP 202, `jobId` retornado). O nome do arquivo no disco é gerado pelo servidor
  (`web-<uuid>`), então não há travessia de diretório; o limite de tamanho funciona (56 MB → 413).
- **Evidência**: sondas `UPLOAD-evil.html` e `UPLOAD-garbage.bin`.
- **Afetado**: `backend/application/src/main/java/br/com/felipeblaft/reports/inbound/web/WebAudioReception.java:74`.
- **Impacto**: o ffmpeg e o provedor de transcrição recebem entrada arbitrária (consumo de recurso e custo; superfície de
  parsing). Não há execução de código observada.
- **Correção**: validar a assinatura (magic bytes) dos formatos aceitos antes de gravar/enfileirar e rejeitar com
  `UNSUPPORTED_FORMAT`; manter timeout e limite de recursos no ffmpeg.
- **Teste de regressão**: `IT` com HTML e binário declarados como áudio esperando 4xx.
- **Esforço**: baixo a médio.

### SEC-004 — Contêiner do `web` roda como `root`

- **Descrição**: o estágio final de `frontend/Dockerfile` (`FROM caddy:2`) não define `USER`. O Trivy aponta DS002 (alta).
- **Afetado**: `frontend/Dockerfile:16-18`. O mesmo vale para `infra/tests/n8n/Dockerfile` (só em testes).
- **Impacto**: comprometimento do proxy dá `root` no contêiner. O Caddy escuta em 443/80; abrir portas baixas sem root
  exige `cap_net_bind_service`.
- **Correção**: `USER` não privilegiado com a capacidade de bind, ou portas altas atrás do mapeamento do compose;
  `read_only: true` e `cap_drop: [ALL]` no serviço.
- **Teste de regressão**: `ComposeGuardTest` verificando `cap_drop`/`user`; Trivy `config` sem DS002 em CI.
- **Esforço**: médio (volumes `caddy-data` e `caddy-config` precisam de dono compatível).

### SEC-005 — GitHub Actions por tag mutável

- **Descrição**: 7 usos (`actions/checkout@v5`, `upload-artifact@v4`, `cache@v4`) por tag; o semgrep reporta
  `github-actions-mutable-action-tag`.
- **Afetado**: `.github/workflows/ci.yml:20,35,49,59,64,84,86`.
- **Impacto**: uma tag movida injeta código no CI, que tem acesso ao repositório.
- **Correção**: fixar por SHA de commit com comentário da versão; manter o Dependabot para atualizar.
- **Teste de regressão**: regra semgrep/`zizmor` no CI.
- **Esforço**: baixo.

### SEC-006 — Dependências do frontend com CVEs

- **Descrição**: `npm audit` (lockfile): 3 altas (`undici` 8.0.0–8.12.1, `vite` 8.3.0–8.3.2, `zod` <4.6.6) e 10 moderadas
  (cadeia `eslint`/`typescript-eslint`/`minimatch`, só de desenvolvimento). Todas as altas têm correção sem quebra.
- **Afetado**: `frontend/package-lock.json`.
- **Impacto**: `vite` e `undici` são de build/desenvolvimento; `zod` roda no navegador. Risco real baixo a médio.
- **Correção**: `npm audit fix` para as três altas; avaliar `eslint` 9.39.5 para as moderadas (mudança de major).
- **Teste de regressão**: `npm audit --audit-level=high` no CI.
- **Esforço**: baixo.

### SEC-007 — CSP com `style-src 'unsafe-inline'`

- **Descrição**: confirmado por sonda, ZAP (4 instâncias, "Medium (High)") e pela configuração. `script-src 'self'` está
  correto, o que limita o impacto a injeção de estilo.
- **Afetado**: `infra/caddy/Caddyfile:19`.
- **Correção**: remover `'unsafe-inline'` movendo estilos em linha para classes ou usar nonce/hash; validar com o
  Playwright e o axe.
- **Esforço**: médio (depende de quantos `style=` o SPA usa).

### SEC-008 — Imagem do `web` (Caddy) com CVEs em bibliotecas Go

- **Descrição**: `usr/bin/caddy` embute `github.com/go-jose/go-jose/v3` v3.0.5 (CVE-2026-116587, sem correção publicada)
  e `golang.org/x/net` v0.60.0 (CVE-2026-78661, corrigida em 0.62.0).
- **Correção**: subir a tag do `caddy` quando a versão com `x/net` ≥ 0.62.0 sair; acompanhar o `go-jose`.
- **Esforço**: baixo.

### SEC-009 — Nome de aluno aceita HTML

- **Descrição**: `POST /api/v1/admin/students` com `name="<img src=x onerror=alert(1)><script>…"` retorna 201 e devolve o
  nome tal qual. **Sem exploração hoje**: o SPA renderiza como texto (React) e o PDF usa `th:text` (escapado); o
  `th:utext` do template só recebe CSS do servidor (`lesson-report.html:6-7`).
- **Impacto**: nenhum atual; qualquer novo consumidor que use `innerHTML`/`utext` herdaria o problema.
- **Correção**: restringir caracteres do nome na validação (letras, espaços, apóstrofo, hífen, ponto) e manter o escape
  na saída.
- **Teste de regressão**: `IT` com `<` no nome esperando `VALIDATION_FAILED`.
- **Esforço**: baixo.

### SEC-010 — Higiene de Dockerfiles

- Sem `HEALTHCHECK` em `backend/application/Dockerfile`, `frontend/Dockerfile`, `infra/n8n/Dockerfile` e
  `infra/telegram-simulator/Dockerfile` (DS026); `infra/tests/n8n/Dockerfile` usa `apt-get` sem
  `--no-install-recommends` (DS029). O compose já define healthchecks nos serviços, então o impacto é baixo.

### SEC-011 — SQL dinâmico com concatenação de enum

- `OperationalAlertRepositoryAdapter.java:97` monta `" WHERE status = '" + status.name() + "'"`. Como `status` é um enum
  fechado, **não é injetável**; é só um padrão frágil. Trocar por parâmetro (`?`) para blindar contra mudança futura.

### SEC-012 — SCA do Java não conclui offline

- O Trivy não resolve as versões do BOM do Spring Boot. Opções: gerar `mvn dependency:tree`/SBOM CycloneDX em contêiner
  Maven e passar ao Trivy (`trivy sbom`), ou liberar a resolução pelo Maven Central dentro do contêiner. Até lá, o SCA do
  backend está sem cobertura.

### SEC-013 — Achados do gitleaks em fixtures

- 14 ocorrências de `generic-api-key` no histórico, todas em testes (`SensitiveDataMaskingConverterTest`,
  `CpfKeyProbe`, `*ContractDriftIT`, `application-test.yaml`, …) e uma no alfabeto do token
  (`StudentRegistrationDraft.java:48-49`, falso positivo). `infra/.env`, `infra/.env.pre-003.bak` e
  `compose.security.yaml:118-120` aparecem só na árvore de trabalho; os dois primeiros estão no `.gitignore` e não são
  rastreados. O valor de `compose.security.yaml:118-120` é a chave fixa de teste do e2e. Nenhum segredo real versionado.

## 5. Controles verificados e aprovados

| Controle | Resultado |
|---|---|
| BOLA/IDOR (`students`, `history`, `lesson-reports`, `semesters`, `open-report`, `my-students`, `jobs`) | Professor B recebe 404 idêntico ao de inexistente |
| Escalada de papel (professor em `/admin/**`; admin em rota de professor) | Negado em todas as rotas |
| Cookies de sessão | `HttpOnly`, `Secure`, `SameSite` presentes |
| Sessão | Token inválido após logout; identificador novo a cada login |
| CSRF (professor e administrador, JSON) | 403 sem token e com token forjado |
| Força bruta (código do professor, login do admin) | 429 a partir da 6ª tentativa; `X-Forwarded-For` forjado não contorna |
| Enumeração de e-mail do administrador | Tempos iguais (8,7 ms × 8,8 ms) |
| SQLi em `search` do administrador | 5 payloads sem erro e sem atraso |
| Limite de upload | 56 MB → 413 |
| Exposição pelo proxy (`/actuator/*`, `/internal`, Swagger, `.env`, `.git`, source maps) | Fallback do SPA, sem conteúdo próprio |
| CORS com `Origin` arbitrário | Sem `Access-Control-*` |
| Vazamento em erro | JSON malformado sem stack trace |
| Canal interno (`/internal/**`) | 401 sem token e com token errado |
| Cabeçalhos | HSTS, CSP, `nosniff`, `Referrer-Policy`, `Permissions-Policy` presentes; `Cache-Control: no-store` nas respostas com dado pessoal |
| TLS | Só TLS 1.2 e 1.3; sem SSLv2/3, TLS 1.0/1.1, NULL, EXPORT, LOW, 3DES |
| PDF | Nome e textos via `th:text` (escapados) |
| Logs | `SensitiveDataMaskingConverter` cobre CPF, telefone, e-mail e senha, com teste |
| Revisão de `window.open` (`frontend/src/services/pdf.ts:48`) | Abre `about:blank` e depois um blob de mesma origem; `noopener` impediria controlar a aba. Risco baixo |
| Prompt injection | A transcrição segue como mensagem de usuário em JSON, separada do prompt de sistema; risco residual comum a LLMs |

## 6. Dependências e imagens vulneráveis

| Componente | CVE | Versão atual | Versão corrigida |
|---|---|---|---|
| `reports-web` / `golang.org/x/net` | CVE-2026-78661 | v0.60.0 | v0.62.0 |
| `reports-web` / `go-jose/v3` | CVE-2026-116587 | v3.0.5 | sem correção |
| `reports-n8n` / `brotli-libs` | CVE-2026-111497 (crítica) | 1.2.0-r1 | 1.2.1-r0 |
| `reports-n8n` / `libcrypto3` | CVE-2026-14456 | 3.5.7-r1 | 3.5.8-r0 |
| `reports-n8n` / `libcurl` | CVE-2026-92392 | 8.21.0-r0 | 8.23.0-r0 |
| `reports-n8n` / `libexpat` | 7 CVEs altas | 2.8.3-r1 | 2.8.4-r0 a 2.10.0-r0 |
| `reports-n8n` / `openssh*` | críticas | 10.3_p1-r0 | ver `reports-n8n.json` |
| `reports-n8n` / `simple-git`, `handlebars` | críticas | 3.36.0, 4.7.9 | ver `reports-n8n.json` |
| `frontend` / `undici`, `vite`, `zod` | altas (npm audit) | 8.x, 8.3.x, <4.6.6 | `npm audit fix` |

## 7. Falsos positivos e descartes

- **ZAP "Format String Error"** (1 instância em `GET /api/v1/students?search=…%s%n…`): provável falso positivo; a stack é
  JVM, que não interpreta `%n`/`%s` vindos da query como formato nativo. A resposta não mostrou erro de aplicação.
- **ZAP "Informational"** (Modern Web Application, User Agent Fuzzer, Authentication Request Identified, Suspicious
  Comments, Re-examine Cache-control): sem risco; o `Cache-Control` revisado já é `no-store` para dados pessoais.
- **testssl `cert_chain_of_trust`, `cert_revocation`, `intermediate_cert_*`**: o certificado vem da CA interna do Caddy no
  ambiente de teste; não vale para a VPS, que usa certificado público automático.
- **testssl `BREACH`**: potencial, só em `/` (estático, sem segredo refletido); sem ação.
- **gitleaks em `.claude/worktrees/**` e `graph.db-wal`**: cópias de trabalho e banco do grafo, fora do Git.
- **semgrep**: os 7 achados são todos SEC-005; nenhum de código de aplicação.

## 8. Plano de correção e como reexecutar

Ordem sugerida e agrupamento por PR:

1. **PR "imagens e suprimentos"**: SEC-001, SEC-008, SEC-006 e SEC-005 (atualizações de versão e SHAs; sem mudança de comportamento).
2. **PR "hardening do proxy"**: SEC-004 e SEC-007 (usuário não-root, `cap_drop`, CSP sem `unsafe-inline`; validar com
   Playwright e axe).
3. **PR "backend"**: SEC-002, SEC-003, SEC-009 e SEC-011 (porta de gerenciamento, assinatura do áudio, validação do nome,
   parâmetro no SQL), cada um com seu teste de regressão.
4. **PR "ferramental"**: SEC-012 (SBOM do Maven) e SEC-010 (`HEALTHCHECK`), mais a etapa de Trivy `--exit-code 1` no CI.

Reexecução (≈ 40 min; baixa as imagens das ferramentas na primeira vez):

```bash
sh infra/scripts/local.sh tests security
```

Reprodução isolada de uma sonda: suba a pilha (`docker compose -f infra/compose.test.yaml -f infra/compose.security.yaml
up -d --wait postgres-e2e backend-e2e web-e2e`), semeie (`run --rm security-seed`) e rode `run --rm --no-deps
security-probes`. Limpe com `down -v`.
