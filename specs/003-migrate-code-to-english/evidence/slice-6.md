# Evidência — Fatia 6: infraestrutura, scripts e CI

Registro das tasks T169 a T191 (`tasks.md`). Nenhum valor de texto, CPF, nome de aluno, chave ou token é registrado
aqui; só contagens, nomes de arquivos e resultados.

## Linha de base (T169)

Árvore: commit da fatia 5 (`807070b`). A fatia 5 terminou com as três suítes verdes sobre exatamente essa árvore
(o commit só acrescentou o que já havia sido rodado), então a linha de base da fatia 6 **é o resultado final da
fatia 5** (`evidence/slice-5.md`), sem rodar de novo suítes sobre uma árvore idêntica. Comando antigo
(`sh infra/scripts/local.sh testes …`).

| Suíte | Testes | Resultado |
|---|---|---|
| `backend` — total (`core` 324, `application` unitários 81, Failsafe padrão 274 com 1 ignorado, Failsafe `internal-transport` 71) | 750 | verde |
| `frontend` | 77 (13 arquivos) | verde |
| `e2e` | 19 | verde |

Cobertura do `core` (`evidence/slice-4-jacoco.csv`, a mesma da fatia 5): linhas 3.178/3.238, branches 806/875,
métodos 745/766.

## Decisões tomadas durante a fatia (registro corrente)

1. **Fonte única das trocas (T170)**: a tabela `tools/lib/slice6-rules.mjs` (arquivos movidos, arquivos no escopo e ~110 regras de
   troca, cada uma com o par antigo → novo que o verificador aceita). `tools/rewrite/rename-infra.mjs` aplica (`--phase moves` com
   `git mv`, `--phase content`, `--phase map`) e reprova se alguma regra não casar em nenhum arquivo. `tools/maps/slice-6.json`
   (78 entradas) é derivado dessa mesma tabela, não do `gen-identifier-map.mjs`: nesta área quase todo identificador é prosa
   (comentário e mensagem), e o gerador de mapa propôs 194 palavras que não são nome (`abaixo`, `pilha`, `sobe`…), das quais só ~40 eram nome
   de verdade. Por isso a tabela é escrita à mão, com contexto: `massa`, `arquivo`, `porta`, `interna`, `valor`, `chave` só trocam em posição
   de código (regex com contexto), nunca em frase.
2. **Escopo (T171 a T178)**: 5 arquivos movidos (`compose.banco.yaml→compose.database.yaml`, `infra/carga/→infra/seed/` com
   `exemplo.json→example.json` e `demonstracao.json→demo.json`, `implantar.sh→deploy.sh`, `implantar.yml→deploy.yml`) e 27 arquivos com
   conteúdo trocado: 12 de `infra/`, `.github/`, `.gitignore` e `.dockerignore`; 14 de fora, que carregam os mesmos nomes
   (`backend/application/Dockerfile`, `application.yaml`, `application-test.yaml`, 8 testes Java, a mensagem de uso do `DemoDataCommand`,
   `frontend/tests/e2e/support.ts` e `frontend/playwright.config.ts`); e o `SeedCommandsExecutor` (decisão 6). `infra/compose.prod.yaml` não
   precisou de troca (só cita serviços que já estavam em inglês). Os dois READMEs de `infra/` foram atualizados por edição revisada (`manualFiles`),
   incluindo os nomes de variáveis, comandos do backend e tabelas que a fatia 3 já havia mudado e a documentação ainda citava.
3. **O que mudou de nome e pode afetar quem opera**: projeto do compose `relatorios→reports`; banco, usuário e papel do PostgreSQL
   `relatorios→reports` (e `relatorios_teste→reports_test` nos testes); usuário do contêiner do backend `relatorios→reports`; volumes
   `documentos→documents`, `telegram-bot-api-dados→telegram-bot-api-data`, `caddy-dados→caddy-data` (e, pelo prefixo do projeto,
   `reports_pgdata` e `reports_documents`); rede `interna→internal`; pasta de documentos do contêiner `/dados/documentos→/data/documents`;
   pasta de carga montada `/carga→/seed`; subcomandos do `local.sh` e `--massa→--demo-data`; serviços de teste `testes-*→tests-*`
   (`testes-ia-real→tests-real-ai`); artefato do CI `jacoco-nucleo→jacoco-core`; segredos do deploy `VPS_CHAVE_SSH→VPS_SSH_KEY`,
   `VPS_HOST_CONHECIDO→VPS_KNOWN_HOST`, `VPS_USUARIO→VPS_USER`, `VPS_DIRETORIO→VPS_DIRECTORY` e ambiente `producao→production`.
4. **Segredos e ambiente do GitHub (T178)**: o repositório não tem nenhum segredo nem ambiente cadastrado (`gh secret list` vazio e
   `environments` com `total_count: 0`), então os nomes novos não deixam nada órfão. Quem for ativar o workflow `deploy.yml` cadastra
   direto com os nomes novos (`infra/README.md`, seção 4.2). Não há exceção "nome persistido fora do repositório".
5. **Palavra que o operador digita**: o `local.sh clean` pede a palavra `apagar` para confirmar. Traduzi-la mudaria a interação do script
   (a regra é comportamento idêntico); mantê-la exige uma exceção do glossário, `typed-confirmation-word` (`appliesTo: artifacts`, só
   `infra/scripts/local.sh`, categoria "texto do usuário"). Entra na lista para a emenda 2.1.0. A exceção `spec-folder-references`
   ganhou `infra/compose.test.yaml` (a variável `CONTRACT_API` aponta para o contrato em `specs/001-fluxo-relatorios/`).
6. **Achado: regressão da fatia 3 no executor dos comandos operacionais (corrigida, com teste)**. O `docker-entrypoint.sh` ativa o perfil
   `seed` e os três comandos (`SeedCommand`, `IssueCodeCommand`, `DemoDataCommand`) já estavam em `@Profile("seed")`, mas o
   `SeedCommandsExecutor`, que é quem de fato os executa e encerra o processo, ficou com `@Profile("carga & !teste")`. A expressão tem
   espaços, então tanto a ferramenta de renomeação quanto o guardião a tratam como texto e a deixaram passar. Efeito no contêiner: o
   perfil `seed` não casava com `carga`, o executor não existia e `seed`, `issue-code` e `demo-data` subiam o Spring e terminavam sem
   fazer nada (`local.sh up --demo-data=true` ia dizer "sistema no ar, mas vazio"; `local.sh seed` e `local.sh code` não imprimiriam
   nada). Os testes não pegaram porque chamam os comandos direto (`SeedCommandIT` ativa `seed` + `test` e injeta os beans). Correção:
   `@Profile("seed & !test")` (o `!test` é o que mantém o executor fora dos testes de integração, como o `!teste` fazia na `main`), a
   Javadoc e a mensagem de uso do executor (`massa --arquivo` → `demo-data --file`). Teste novo
   `SeedCommandsExecutorProfileTest` (3 casos, sem Spring): casa em `seed`, não casa em `seed`+`test`, `test` nem sem perfil. É o único
   código de produção e o único teste acrescentados à fatia; o total sobe de 750 para 753.
7. **Achado: relatório da suíte de IA real**: o `local.sh tests real-ai` imprimia `backend/application/target/ia-real/relatorio.md`, mas a
   suíte (`RealAiSuiteIT`) já grava `target/real-ai/report.md` desde a fatia 3. Mensagem corrigida para o caminho real; o perfil Maven
   e o serviço já eram `real-ai`.
8. **Proteção da carga real preservada (T173, Princípio IV)**: criado `infra/seed/real-data.json` só para a prova e removido em seguida.
   `git check-ignore -v` aponta `.gitignore:15 infra/seed/*.json` para ele (ignorado) e **não** aponta `example.json` nem `demo.json`
   (saída 1, nenhum ignorado). No contexto de `docker build` (`COPY . /ctx` com o `.dockerignore` da raiz), `ls /ctx/infra` lista
   `CLAUDE.md`, `README.md`, `caddy`, `compose*.yaml` e `scripts`: **sem** `seed` (e sem `.env`).
9. **`.env` antigo (T181, T186, US4-4)**: com o `infra/.env` anterior à feature (o do próprio ambiente local, de 1º/10), `sh infra/scripts/local.sh up`
   aborta com código 1 e a saída `Preencha em infra/.env: CPF_ENCRYPTION_KEY HMAC_SECRET`, seguida de `(sem o bot: …)` e da orientação
   nova `Se o seu infra/.env é anterior à feature 003, regere-o a partir de infra/.env.example.` (nenhum valor impresso; só os
   nomes das chaves novas). Antes de qualquer teste de subida o arquivo foi copiado para `infra/.env.pre-003.bak` (ignorado pelo Git).
10. **Sintaxe (T182)**: `docker compose config -q` sem erro para `compose.yaml`+`compose.dev.yaml`, `compose.yaml`+`compose.prod.yaml`,
    `compose.yaml`+`compose.dev.yaml`+`compose.database.yaml` e `compose.test.yaml`; `sh -n` sem erro em `local.sh` e `deploy.sh`.
11. **Exclusões temporárias (T183)**: guardiões em modo relatório (`GUARD_REPORT=1`) sobre a árvore da fatia 6: **0 violações** nos três
    relatórios (Java, artefatos e TypeScript). `gen-exclusions.mjs` regerou `.specify/memory/glossary-temporary-exclusions.json` com
    **0 arquivos e 0 termos** (eram 15 arquivos e 49 termos, todos da fatia 6). O arquivo ainda existe (a fatia 7 o apaga) e o
    passo do `ci.yml` que reprova o PR enquanto ele existir continua valendo.
12. **Subida do zero (T185, SC-005)**: `infra/.env` regerado a partir do `infra/.env.example` (segredos novos gerados localmente, `TELEGRAM_ENABLED=false`,
    `AI_PROVIDERS=simulated`) e `sh infra/scripts/local.sh up --demo-data=true`: build, serviços saudáveis (`backend`, `postgres`, `web`), volumes e rede
    com o prefixo novo (`reports_pgdata`, `reports_documents`, `reports_caddy-data`, `reports_telegram-bot-api-data`, rede `reports_internal`) e a carga de
    demonstração aplicada: 2 professores, 8 alunos, 57 relatórios de aula aprovados e 2 em aberto, 1 semestral aprovado e 1 em conferência, 118 PDFs
    (os mesmos números do `infra/scripts/README.md`). Sem a correção da decisão 6, esta etapa terminaria sem criar nada.
    Verificado pela API (`/api/v1`) com o código da Clara: entrada 200, painel com os 6 alunos, histórico de Matteo com 23 itens, PDF oficial da aula e
    PDF oficial do semestral baixados (`%PDF-`, 56.498 e 85.605 bytes) com o nome em português (`relatorio-aula-2026-09-09.pdf`,
    `relatorio-semestral-2026-1.pdf`); `/brand/*` responde com `Cache-Control: public, max-age=31536000, immutable` e as rotas novas da SPA
    (`/sign-in`, `/students`) respondem 200 pelo Caddy. **Não exercitado**: o fluxo manual registrar aula → perguntas → conferência → alterar → aprovar
    pelo navegador (o navegador embutido recusou o certificado local; esse fluxo é o que o e2e do Playwright percorre, 19 de 19 verdes, sobre a mesma imagem
    do backend) e o Telegram ("Telegram não exercitado: sem credenciais"; `TELEGRAM_ENABLED=false`).
13. **Ensaio com dados existentes (T187, quickstart §4)**: na `main` (árvore `git archive` em `/tmp/base`, com o `infra/.env` anterior à feature),
    `subir --massa=true` e `pg_dump -U relatorios -Fc relatorios` (74.780 bytes; contagens: 59 relatórios de aula, 2 semestrais, 60 versões, 118 PDFs, 8 alunos,
    2 professores; `flyway_schema_history` até a V6); `parar`. Na branch: `infra/.env` novo com os três segredos levados do arquivo antigo para as chaves novas
    (`CPF_ENCRYPTION_KEY`, `HMAC_SECRET`, `POSTGRES_PASSWORD`; nenhum valor impresso), só o banco no ar (`compose up -d postgres`), `pg_restore --no-owner
    --no-privileges` em `reports` (saída 0), cópia do volume `relatorios_documentos` para `reports_documents` (118 arquivos) e `sh infra/scripts/local.sh up`:
    aplicação iniciou sem erro (nenhum `ERROR` nem exceção no log), `flyway_schema_history` com **1,2,3,4,5,6,7,8**, contagens **idênticas** nas tabelas com nome novo
    (`lesson_report` 59, `semester_report` 2, `report_version` 60, `pdf_document` 118, `student` 8, `teacher` 2). Pela API: o código emitido pela pilha antiga entra (200),
    a Clara vê os 6 alunos, o histórico de Matteo tem 23 itens, os PDFs oficiais da aula e do semestral abrem (`%PDF-`) com o nome em português, o relatório em
    conferência de Olavo vem com status `AWAITING_REVIEW`, canal `TELEGRAM` e o conteúdo com as chaves novas (`workedTopics`, `progress`, `difficulties`,
    `proposedActivities`, `observations`). O procedimento é o que está em `infra/README.md` ("Se você tem um ambiente anterior à feature 003"). O dump ficou só na
    máquina local e foi apagado ao fim. A cópia dos PDFs mostrou que a chave de armazenamento (`relatorios/<tipo>/<id>/v<n>.pdf`) continua igual: é formato persistido.
14. **Efeito colateral do ensaio no ambiente local**: a etapa pede `subir --massa=true` na `main`, e esse comando **zera os volumes locais `relatorios_pgdata` e
    `relatorios_documentos`** antes de subir. Se havia dados de desenvolvimento neles, foram substituídos pela massa de demonstração. Os volumes `relatorios_*` seguem no Docker
    (agora com a massa do ensaio); os `reports_*` têm a massa restaurada; a pilha nova está parada. O `infra/.env` anterior foi guardado em
    `infra/.env.pre-003.bak` (ignorado pelo Git) e o `infra/.env` atual é o novo (chaves em inglês), sem as credenciais reais de bot e de IA que o antigo possa ter.

## Resultados (T184, T188, T189 e T191)

Comando novo, sobre a árvore da fatia 6: `sh infra/scripts/local.sh tests all` (backend, frontend e e2e na ordem), saída 0.

| Suíte | Linha de base (T169) | Fatia 6 | Resultado |
|---|---|---|---|
| `backend` — `core` (Surefire) | 324 | 324 | verde |
| `backend` — `application` unitários | 81 | **84** | verde (+3 de `SeedCommandsExecutorProfileTest`, decisão 6) |
| `backend` — Failsafe padrão | 274 (1 ignorado) | 274 (1 ignorado) | verde |
| `backend` — Failsafe `internal-transport` | 71 | 71 | verde |
| `backend` — total | 750 | **753** | verde |
| `frontend` (lint, `tsc -b`, guardião TypeScript incluídos) | 77 (13 arquivos) | 77 (13 arquivos) | verde |
| `e2e` (Playwright + axe) | 19 | 19 | verde, 0 violações de acessibilidade |

- **Número de testes**: 753 contra 750. Os 3 a mais são o teste de regressão do perfil do executor, acrescentado de propósito por causa do achado da decisão 6; a regra
  de comparação só admite aumento nas fases 1, 3 e 4, então este desvio fica registrado e é o único. Nenhum teste foi removido ou alterado.
- **Cobertura do `core` (T189)**: linhas 3.178/3.238 (98,15%), branches 806/875 (92,11%): o `jacoco.csv` é **byte a byte igual** ao da fatia 4
  (`evidence/slice-6-jacoco.csv` × `evidence/slice-4-jacoco.csv`); esta fatia não toca o `core`.
- **Guardiões**: verdes em modo normal (`LanguageGuardTest`, `LanguageGuardSelfTest`, `ArtifactsLanguageGuardIT` e seu autoteste, guardião TypeScript) com **0 exclusões
  temporárias** (decisão 11), inclusive YAML, shell, workflows, `Dockerfile`, `Caddyfile`, `.gitignore`, `.dockerignore` e os nomes de pasta e arquivo de `infra/` e `.github/`.
- **`rename-check --slice 6` (T188)**: `tools/rename-check-infra6.mjs` (`evidence/slice-6-rename-check.txt`): 27 arquivos pareados com o commit da fatia 5, 329 trechos
  trocados, **todos** explicados pela tabela; **0** trechos fora da tabela, **0** arquivos sem par, **0** escopo morto, 2 arquivos movidos (JSON de carga) com bytes idênticos.
  Mais 141 trechos em comentário ou em frase, listados no arquivo, em que só um nome (comando, arquivo, serviço, usuário) mudou; conferidos um a um.
- **Texto**: nenhuma expectativa de texto para pessoas mudou. A única mensagem de código de produção que mudou é a de uso do executor (`massa --arquivo` →
  `demo-data --file`, que já era o nome do comando) e a do `DemoDataCommand` (cita `local.sh up --demo-data=true`); nenhum teste confere essas frases.
- **Formatação**: `mvn spotless:check` ficou vermelho depois da troca (nomes mais curtos mudaram a quebra de linha de 5 arquivos); `spotless:apply` ajustou só quebras de
  linha e a compilação foi repetida sem erro.

## Achados e avisos para o checkpoint

- **Regressão da fatia 3 achada e corrigida** (decisão 6): o executor dos comandos `seed`, `issue-code` e `demo-data` não existia no contêiner. Vale registrar que a ferramenta
  e o guardião são cegos para expressões com espaço dentro de anotações (`@Profile("carga & !teste")`); varri as demais anotações de configuração (`@Profile`,
  `@ConditionalOn*`, `@Value`, `@Qualifier`) e não achei outra.
- **Divergência de documento (`.env.example`)**: `DATABASE_POOL` e `MULTIPART_LIMIT_MB` existem no `application.yaml` e não estão no `.env.example`; já era assim na `main`.
  Não é renomeação; só anotado.
- **`infra/CLAUDE.md`, `.github/CLAUDE.md`, `backend/CLAUDE.md`, `.specify/CLAUDE.md` e a constituição** ainda citam `implantar.sh`, `implantar.yml`, `testes backend`, `testes-frontend`
  e `infra/carga`: documentação da fatia 7 (a constituição e o `CLAUDE.md` raiz têm de ser emendados com aprovação).
- **`/fontes/*` no `Caddyfile`**: virou `/fonts/*`; continua sendo caminho morto (o Vite emite as fontes em `/assets`). Não removi.
- **Segredos e ambiente do GitHub**: sem nada cadastrado (decisão 4); quem ativar o `deploy.yml` usa os nomes novos.
- **Teste instável que já existia** (`SignInScreen`, "bloqueio (01c)"): não apareceu nesta rodada.
- **Termo do e2e** que a fatia 5 deixou com a fatia 6 (`relatorios` na URL padrão do banco em `support.ts`): quitado (`reports`).
