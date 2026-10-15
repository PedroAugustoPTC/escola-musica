# Migração do código para inglês (feature 003)

Quita a dívida do Princípio VI: todo identificador do repositório (pacotes, tipos, métodos, arquivos e pastas, tabelas e colunas,
rotas e campos das APIs, códigos de erro, chaves de catálogo, variáveis de ambiente, scripts, workflows, classes CSS) passa de
português sem acento para **inglês**. Texto mostrado ao usuário, mensagens de log, documentação, comentários, commits e PRs seguem em
português. **Refatoração pura: comportamento idêntico.** Nenhuma tela, mensagem, botão, rótulo, PDF ou nome de arquivo baixado mudou, e
nenhuma expectativa de texto de teste foi alterada.

Um teste guardião (Java e TypeScript) passa a reprovar o build quando uma palavra em português aparece em identificador, e a constituição
ganha a emenda 2.1.0 (ver "Pedido de aprovação").

## Commits (sete fatias, cada uma verde)

| # | Commit | Conteúdo |
|---|---|---|
| 1 | `2243d8b` | glossário (`.specify/memory/glossary.json`) e teste guardião de idioma, com autotestes |
| 2 | `7781be2` | `nucleo` → `core` |
| 3 | `96e4020` | `aplicacao` → `application` e migração `V7` (esquema do banco) |
| 4 | `7879ab4` | contratos, rotas, erros, catálogo, botões do Telegram e migração `V8` (valores de enumeração) |
| 5 | `807070b` | frontend |
| 6 | `52f0b7f` | infraestrutura, scripts e CI |
| 7 | _(este)_ | documentação, constituição 2.1.0, notas de histórico e quitação |

Mais cinco commits de documento da própria feature (spec, clarificações, plano, tarefas e convergência).

## Suítes por commit (resultado de cada `evidence/slice-N.md`)

| Commit | `backend` | `frontend` | `e2e` | Cobertura do `core` (linhas / branches) |
|---|---|---|---|---|
| `main` (linha de base) | 700 | 67 | 19 | 3.243/3.304 · 806/875 |
| 1 | 727 (+27 dos guardiões) | 77 | 19 | igual |
| 2 | 727 | 77 | 19 | 3.243/3.304 · 806/875 |
| 3 | 738 (+11: testes da `V7`) | 77 | 19 | 3.176/3.237 · 806/875 |
| 4 | 750 (+12: `V8` e formato antigo de botão) | 77 | 19 | 3.178/3.238 · 806/875 |
| 5 | 750 | 77 | 19 | igual à fatia 4 |
| 6 | 753 (+3: perfil do executor, ver abaixo) | 77 | 19 | igual à fatia 4 (CSV idêntico) |
| 7 | 753 | 78 (+1: chaves do semestral) | 19 | igual à fatia 4 |

A cobertura de **linhas** do `core` varia pouco entre os commits porque os arquivos renomeados e os testes foram reorganizados; o que a regra
exige (≥ 90% por linha e por branch) está em 98,15% e 92,11%. Os números de cada fatia, com a divisão Surefire/Failsafe, estão nos arquivos
`evidence/slice-1.md` a `slice-7.md`. Nenhum teste foi removido; os acréscimos são só de guardião, migração, regressão e as duas correções abaixo.

## Provas de que só o nome mudou

- **`rename-check` por fatia** (`evidence/slice-N-rename-check.txt`): fatias 2 a 4, 449 a 454 arquivos Java pareados e ~250 mil tokens alinhados, **0** pares fora
  do mapa, **0** literais de texto alterados, **0** arquivos sem par; fatia 5 (frontend), 96 arquivos comparados e 6.903 trocas pelo mapa, 0 divergências, binários com SHA-256
  idêntico; fatia 6 (infraestrutura), 329 trechos trocados, todos explicados pela tabela de regras, 0 fora dela.
- **Catálogo de mensagens** (`slice-4-catalog.txt`, `slice-5-catalog.txt`): 217 chaves na base e 217 no novo, 0 divergências de texto; só as chaves e 23 marcadores trocaram.
  Na fatia 7 o marcador `{hora}` passou a `{time}` (5 mensagens) junto com o código que o preenche.
- **Contratos** (`slice-4-contracts.txt`): OpenAPI do Portal e esquema das ações do Telegram com **0 diferenças de forma** (216 e 63 nomes trocados).
- **Migrações** `V7` e `V8` (testes `MigrationV7*IT` e `MigrationV8*IT`): estrutura equivalente ao mapa do `data-model.md`, dados gravados antes continuam legíveis,
  zero chaves JSON antigas, valor desconhecido aborta a `V8`, índices únicos e `CHECK`s continuam ativos. `V1` a `V8` são imutáveis.
- **Ensaio manual com dados existentes** (`evidence/slice-6.md`, decisão 13): pilha da `main` com dados de demonstração → `pg_dump` → restauração em `reports` → subida da branch:
  `flyway_schema_history` com 1 a 8, contagens idênticas (59 relatórios de aula, 2 semestrais, 60 versões, 118 PDFs, 8 alunos, 2 professores), o código de acesso emitido pela
  pilha antiga entra, os PDFs antigos abrem com o nome em português e o conteúdo usa as chaves novas.
- **Subida do zero** (`evidence/slice-6.md`, decisão 12): `up --demo-data=true` com `infra/.env` novo, serviços saudáveis, 57 aulas aprovadas, 118 PDFs; `.env` antigo é recusado com orientação.
- **Prova de que o guardião barra** (`evidence/slice-1.md`, T021): violações de classe, método, variável, constante, `record`, `enum`, rota, tabela, coluna, chave de YAML,
  código de erro, componente React, classe CSS, nome de arquivo e outras saíram cada uma com `arquivo:linha`, o termo e o equivalente; a volta ao verde foi demonstrada.
- **Verificadores da fatia 7**: `doc-links-check` (17 documentos, 243 referências, 0 quebradas), `synonym-check` (0 achados), inventário final de palavras.

## Regressões que a própria migração criou e que esta PR corrige

O guardião e o compilador não enxergam acesso por **texto**; três erros desse tipo apareceram na verificação e foram corrigidos (cada um registrado na evidência da fatia):

1. **Fatia 3 → corrigido na 6**: `SeedCommandsExecutor` ficou com `@Profile("carga & !teste")` enquanto o entrypoint ativa `seed`; no contêiner, `seed`, `issue-code` e `demo-data` não executavam nada.
   Teste novo `SeedCommandsExecutorProfileTest`.
2. **Fatia 4 → corrigido na 5**: o relatório semestral lia as seções por chaves em português; os 10 campos apareceriam vazios.
3. **Fatia 5 → corrigido na 7**: duas chaves do semestral foram traduzidas pela metade (`commentMusical`, `goalsMusicais`, quando o fio traz `musicalComment` e `musicalGoals`);
   "Comentário musical" e "Metas musicais" apareceriam vazios. Teste novo `semesterSections.test.ts`, que gera um conteúdo com **todas** as chaves do esquema do backend e exige
   que cada uma apareça em alguma seção (vermelho antes da correção, verde depois).

## O que muda para quem opera (sem alias nem período de compatibilidade)

- **`infra/.env`**: as chaves mudaram de nome (`CPF_CHAVE_CIFRAGEM` → `CPF_ENCRYPTION_KEY`, `SEGREDO_HMAC` → `HMAC_SECRET`, `PROVEDORES_IA` → `AI_PROVIDERS`…). O `local.sh up` recusa o arquivo antigo e orienta
  regerá-lo; **os segredos têm de ser levados para as chaves novas** (senão os CPFs ficam ilegíveis e os códigos de acesso deixam de valer).
- **Docker**: projeto, banco e usuário do PostgreSQL passam de `relatorios` para `reports`; volumes `documentos`/`telegram-bot-api-dados`/`caddy-dados` → `documents`/`telegram-bot-api-data`/`caddy-data`; rede `interna` → `internal`.
  Os volumes antigos não são reaproveitados: o procedimento de `pg_dump`/`pg_restore` e de cópia dos PDFs está em `infra/README.md` ("Se você tem um ambiente anterior à feature 003").
- **`local.sh`**: `subir`/`carga`/`codigo`/`banco`/`testes`/`parar`/`limpar` → `up`/`seed`/`code`/`database`/`tests`/`stop`/`clean`; `--massa` → `--demo-data`. `implantar.sh`/`.yml` → `deploy.sh`/`.yml`; `infra/carga/` → `infra/seed/`.
- **Deploy no GitHub**: segredos `VPS_SSH_KEY`, `VPS_KNOWN_HOST`, `VPS_USER`, `VPS_DIRECTORY` (e `VPS_HOST`) e ambiente `production`. O repositório ainda não tem nenhum cadastrado.
- **API, rotas e fio**: campos, códigos de erro, rotas da SPA (`/sign-in`, `/students…`, `/lesson-reports/…`, `/semester-reports/…`), parâmetros de consulta e `callback_data` dos botões (o formato antigo continua sendo aceito, `OldButtonFormatIT`).
  O nome do PDF **baixado** continua em português (`relatorio-aula-…`, `relatorio-semestral-…`).
- **Persistido que não muda**: prefixos de domínio do HMAC (`codigo:`, `origem:`) e a chave de armazenamento dos PDFs (`relatorios/<tipo>/<id>/v<n>.pdf`).

## Exceções ao Princípio VI (`.specify/memory/glossary.json`, cada uma com motivo e categoria)

| Exceção | O que cobre | Categoria |
|---|---|---|
| `org-name` | `br.com.felipeblaft` (pacote Java, `groupId`) | nomes próprios e material de referência |
| `brand-prefix` | prefixo `fb-` dos arquivos de identidade visual | nomes próprios e material de referência |
| `public-domain` | `$id` de JSON Schema e URLs públicas | nomes próprios e material de referência |
| `hmac-domain-prefixes` | `codigo:` e `origem:` (entram no HMAC já gravado) | nomes próprios e material de referência |
| `spec-folder-references` | caminho `specs/001-fluxo-relatorios` em scripts, `Dockerfile` e compose de testes | nomes próprios e material de referência |
| `feature-folder-names` | pastas de feature citadas pelos testes de contrato | nomes próprios e material de referência |
| `out-of-scope-folders` | `portal-do-professor-design-system/`, `assets/`, `.claude/`, `specs/` (exceto contratos) | nomes próprios e material de referência |
| `immutable-migrations` | `V1` a `V8` (Flyway valida o checksum) | nomes de terceiros ou exigidos por ferramenta |
| `legacy-schema-names` | testes das migrações e do botão antigo (nomes e valores do esquema anterior) | nomes de terceiros ou exigidos por ferramenta |
| `vendor-font-files` | arquivos de fontes de terceiros | nomes de terceiros ou exigidos por ferramenta |
| `glossary-data` | cópia de planejamento do glossário em `specs/**/contracts` | nomes de terceiros ou exigidos por ferramenta |
| `third-party-api-names` | campos da API do Telegram, do n8n e de outras bibliotecas | nomes de terceiros ou exigidos por ferramenta |
| `generated-files` | `api-types.ts` e `messages.json` (gerados) | nomes de terceiros ou exigidos por ferramenta |
| `teacher-keywords` | palavras que o professor digita no bot | texto do usuário |
| `user-visible-text` | texto com espaço ou acento, comentários, documentação e logs | texto do usuário |
| `download-file-name` | nome do PDF mostrado ao baixar | texto do usuário |
| `user-text-samples` | textos de exemplo dos testes | texto do usuário |
| `typed-confirmation-word` | `apagar`, palavra que o operador digita no `local.sh clean` | texto do usuário |

## Pedido de aprovação do dono do produto

A **emenda 2.1.0** da constituição (MINOR, `.specify/memory/constitution.md`) precisa da sua aprovação (Governança). Ela: (a) registra a quitação da dívida e remove a **regra de transição**
e o bloqueio de feature nova; (b) torna o `glossary.json` e o guardião de idioma parte do Princípio VI; (c) ratifica a categoria de exceção "nomes próprios e material de referência"
(as sete primeiras linhas da tabela acima); (d) acrescenta os sufixos `*Entity`, `*Command`, `*Filter`, `*Converter`, `*Codec` e `*Config`. O texto das versões anteriores foi preservado.
A feature que traz o n8n passou de "003" a **004** (documentos da spec 002 e três comentários corrigidos).

## Privacidade, identidade visual e acessibilidade

- **Privacidade**: nenhum dado novo é coletado ou armazenado; a proteção da carga real em `.gitignore` e no `.dockerignore` foi preservada e provada (`infra/seed/*.json` ignorado, `example.json` e `demo.json` versionados, fora do contexto de build).
  Nenhum segredo entrou no repositório; o teste de log sem dado sensível continua verde.
- **Identidade visual e acessibilidade**: nenhuma tela mudou; o e2e (Playwright + axe, 390 px e 1440 px, claro e escuro) está verde com 0 violações.

## Pendente do dono (precisa das chaves reais)

- **Verificação com o LLM real** (FR-034, SC-008; tasks T101 e T229): rodar `sh infra/scripts/local.sh tests real-ai` e percorrer um fluxo de aula e um semestral com `AI_PROVIDERS=real` (roteiro em `quickstart.md` §8),
  registrando campos preenchidos, perguntas, qualidade percebida e custo. Não foi feito porque o ambiente de desenvolvimento só tem IA simulada.
  Vale conferir especialmente a tela de conferência do semestral (as 10 seções preenchidas).
- Fluxo manual no navegador e pelo Telegram da pilha local (o e2e cobre o Portal; o Telegram não foi exercitado sem credenciais).

## Limitações conhecidas (não bloqueiam)

- Alguns nomes seguem a ordem do português dentro do inglês (`ValidateSessionWebUseCase`, `UpdateTelegram…`): são palavras em inglês, o guardião não as acusa; ficam como melhoria de legibilidade para uma PR própria.
- `data` (calendário × dado) é homônimo aceito; o marcador `{data}` do catálogo fica.
- `/fonts/*` no `Caddyfile` é um caminho sem uso (as fontes saem em `/assets`); mantido para não alterar comportamento.

🤖 Generated with [Claude Code](https://claude.com/claude-code)
