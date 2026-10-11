# Evidência — Fatia 1: glossário e teste guardião

Registro das tasks T001 a T023 (`tasks.md`). Nenhum valor de texto, CPF, nome de aluno, chave ou token é
registrado aqui; só contagens, nomes de arquivos e resultados.

## Linha de base (T001, T002)

- **Árvore-base**: `origin/main` = `edf2d0a6f3cb080715e4f064af67e7c42ca1c4a4` (T002). A branch da feature só
  difere da `main` por documentos de `specs/003-migrate-code-to-english/`; o código é o mesmo.
- **Ambiente**: `infra/.env` já existia (não foi recriado). Comando: `sh infra/scripts/local.sh testes <suíte>`,
  em contêiner, uma suíte por vez.

| Suíte | Resultado | Testes executados | Ignorados |
|---|---|---|---|
| `backend` — `core` (Surefire, módulo `nucleo`) | verde | 324 | 0 |
| `backend` — `aplicacao` unitários (Surefire) | verde | 53 | 0 |
| `backend` — `aplicacao` integração (Failsafe, execução padrão) | verde | 252 | 1 |
| `backend` — `aplicacao` integração (Failsafe, `transporte-interno`) | verde | 71 | 0 |
| `backend` — **total** (`BUILD SUCCESS`, 14:47 min) | verde | **700** | **1** |
| `frontend` (lint + `tsc` + Vitest) | verde | 67 (11 arquivos) | 0 |
| `e2e` (Playwright + axe) | verde | 19 | 0 |

- O teste ignorado do backend é preexistente: `RegistroAulaConversaIT` (1 de 12 ignorado). Não foi investigado
  nem alterado (regra do pedido: bug ou código morto é anotado, não corrigido).
- **Cobertura do `core`** (JaCoCo, `backend/nucleo/target/site/jacoco/jacoco.csv`, cópia em
  `evidence/baseline-main-jacoco.csv`; leitura por `tools/coverage-summary.mjs`; o CSV traz o nome do módulo
  entre aspas e com vírgula, por isso a ferramenta conta as colunas do fim):
  - linhas: 3244/3304 = **98,18 %**
  - branches: 807/875 = **92,23 %**
- Guardiões: ainda não existiam (nascem nesta fatia).

## O que a fatia entrega

| Task | Entrega |
|---|---|
| T003 | `.specify/memory/glossary.json` (cópia byte a byte do `contracts/glossary.json`, depois ampliada em T008) |
| T004, T008 | `tools/word-inventory.mjs`; inventários em `evidence/word-inventory-initial.txt` (glossário v2 original) e `word-inventory-final.txt` (glossário completado) |
| T005 | `tools/gen-exclusions.mjs` e `tools/slice-owners.json` |
| T006, T007 | `tools/gen-identifier-map.mjs` e `tools/test/gen-identifier-map.test.mjs` (12 casos, verdes em `node:24-alpine`) |
| T009 | `GlossaryFile` (carregador, casamento, exceções, exclusões, relatório) + `RepositoryWalker` |
| T010 | `backend/pom.xml`: Surefire aceita `*Test.java` e continua aceitando `*Teste.java` |
| T011 | `LanguageGuardTest` + `JavaSourceScanner` (léxico de fonte) + `BytecodeNameScanner` (ArchUnit) |
| T012 | `ArtifactsLanguageGuardIT` + `ArtifactScanner` (YAML com SnakeYAML, JSON, `.properties`, `.env.example`, shell, XML/HTML, CSS, SQL; catálogo do banco) |
| T013 | `frontend/tests/language-guard.test.ts` + `tests/support/language-guard-engine.ts` (compilador `typescript`) |
| T014–T016 | `LanguageGuardSelfTest` (12), `ArtifactsLanguageGuardSelfTest` (13), `language-guard.selftest.test.ts` (9) |
| T017 | verificado, sem mudança: os dois serviços montam `..:/work`, então enxergam o glossário e a lista de exclusões |
| T019 | passo do `ci.yml` (job `backend`): `test ! -e .specify/memory/glossary-temporary-exclusions.json` |
| T020 | `.specify/CLAUDE.md` com o glossário, as exclusões, os consumidores e o modo relatório |

## Decisões de projeto e desvios (para o revisor)

1. **Glossário** (T008): 322 → **1.580 palavras**. O inventário (3.727 palavras distintas em identificadores)
   mostrou que muito português está em nomes de métodos de teste (frases); foram acrescentadas as palavras de
   identificador de origem portuguesa, uma equivalência por palavra. **Ficaram de fora, como ambíguas com o
   inglês** (lista `matching.ambiguousWords`, 33 itens): `classe` (`classes` em inglês casaria por remoção do `s`),
   `principal` (`java.security.Principal`), `motor`, `cor` (`CORS`), `grade`, `face`, `serve`, `agenda`, `alto`,
   `real`, `media`, `medio`, `bom` (BOM), `usa` (USA), `sao` (`America/Sao_Paulo`), `mas`, `todo` (TODO), `ate`, além
   das já previstas. Também ficaram fora, de propósito, nomes próprios e conteúdo de domínio que não são
   vocabulário de identificador (instrumentos, meses, nomes de pessoas de dados de teste).
2. **Abreviações de ação de botão** (`aprov`, `canc`, `tent`, `aprovok`, `cancok`, `descok`, `gerarsim`, `trocal`,
   `confal`, `hitem`) entraram no glossário para o guardião acusá-las; os equivalentes são só sugestão: a
   fatia 4 decide os códigos novos (`identifier-map.md` §4).
3. **Exceções** (12): `temporary-per-slice` saiu (substituída pelo arquivo de exclusões, FR-035); entraram
   `glossary-data` (o `contracts/glossary.json` de planejamento é, por definição, lista de português) e
   `generated-files` (`api-tipos.ts` e `mensagens.json`, gerados e ignorados pelo Git, com a fonte varrida na
   origem); cada exceção ganhou `mode` (`term`, `skip-file`, `builtin`), `paths` e `appliesTo`. Uma exceção
   `historical-spec-folders` foi criada e removida: os nomes das pastas das specs nunca chegam a ser examinados
   (`specs/` só entra pelos contratos, e cada item é examinado só pelo próprio nome), então ela seria morta.
4. **Nome examinado uma vez, no próprio item.** Nome de pasta é reportado na pasta e nome de arquivo no arquivo,
   e `package`/`import` são ignorados no fonte (cada nome é examinado onde é declarado). Sem isso, qualquer
   arquivo **novo** dentro de um pacote ainda em português nasceria violando (o caminho e o `package` carregam
   `relatorios`/`arquitetura`), contradizendo "arquivos novos nascem varridos e limpos" (FR-035). Pelo mesmo
   motivo, os autotestes não têm literal em português: tiram as palavras do próprio glossário.
5. **`infra/.env` nunca é lido** (segredos): o guardião de artefatos lê `.env.example`, não `.env*`. Na
   primeira rodada o `.env` local foi varrido por engano (o dispatch aceitava qualquer `.env*`); o relatório
   daquela rodada ficou só em `target/` (ignorado pelo Git), foi regerado logo depois da correção e nenhum
   valor foi impresso nem registrado (só nomes de variáveis apareceram na saída).
6. **Texto para pessoas**: além de espaço, acento e palavra isolada com maiúscula, vale a mesma palavra com
   pontuação final (`Aprovado.`), que apareceu em exemplos do contrato novo.
7. **Código embutido**: o JPQL de `@Query` e o JSON/SQL de blocos de texto são examinados token a token; bloco de
   texto comum é texto para pessoas. O nome do PDF mostrado ao usuário (`download-file-name`) só vale como
   literal Java.
8. **Banco**: o catálogo é lido de um banco **descartável** criado pelo próprio teste no `postgres-teste`
   (`create database guard_catalog_…`, migrações pelo Flyway, `drop database … with (force)` ao final), sem
   depender do contexto do Spring nem de classes do projeto.
9. **Relatório** (`GUARD_REPORT=1`): agrupa por (arquivo, termo, texto), com contagem, para ficar em ~6 MB em vez
   de ~15 MB.
10. **Bug corrigido durante a fatia (código novo, não do produto)**: os termos das exceções eram comparados sem
    caixa baixa no guardião Java; o autoteste de "exceção morta" (vendor-font-files) acusou na primeira rodada.

## Exclusões temporárias (T018)

`.specify/memory/glossary-temporary-exclusions.json`, gerado por `gen-exclusions.mjs --init` a partir dos três
relatórios, **somente para itens existentes na `main`** (lista de caminhos em `evidence/main-paths.txt`; arquivo
novo com violação seria rejeitado pela ferramenta):

| Dono (fatia) | Entradas | Observação |
|---|---|---|
| 2 (`core`) | 260 | arquivos e pastas de `backend/nucleo` |
| 3 (`application`) | 722 | 286 arquivos e pastas de `backend/aplicacao` + 436 objetos do catálogo (tabelas, colunas, índices, constraints) |
| 4 (contratos) | 7 | contratos das specs 001 e 002 |
| 5 (frontend) | 96 | |
| 6 (infra e CI) | 15 | |
| **Total** | **1.100** | termos retidos B1/B2/B3: 0 (nascem na fatia 2) |

Os três guardiões ficaram **verdes** com a lista; entrada que não casa com nada reprova (provado: o
`backend/pom.xml` foi acusado como "exclusão morta" até a posse dele ser corrigida).

## Prova de que os guardiões barram (T021)

Branch descartável `scratch/guard-proof` (criada do commit da fatia, apagada depois com os arquivos). Em vez de
uma violação por rodada, todas foram introduzidas **de uma vez**, cada uma em arquivo novo (arquivo novo não
está na lista de exclusões), e as três suítes de guardião foram rodadas uma vez cada. Cada violação saiu como
uma falha própria, com `arquivo:linha`, o termo e o equivalente (`aluno → student`):

| Guardião | Violação provocada (todas acusadas) |
|---|---|
| Java (`LanguageGuardTest`) | classe, método, parâmetro, variável local, campo, constante, componente de `record`, constante de `enum`, rota e palavra do professor em literal |
| Artefatos (`ArtifactsLanguageGuardIT`) | tabela e coluna (`V9` descartável, e também no **catálogo vivo** do banco), campo de contrato em `specs/**/contracts`, código de erro, chave de catálogo `.properties`, marcador `{minutos}`, variável de ambiente (`ALUNO_URL`), script (`alunos.sh`), workflow (`implantar-*.yml`), chave YAML, ação de botão `v1:aprovar:7`, caminho em arquivo de ignorar |
| TypeScript (`language-guard.test.ts`) | componente React, prop, rota (template literal), chave de cache (`queryKey`), classe em `className`, classe e propriedade customizada de CSS, nome de arquivo |

Ao voltar para a branch da feature e apagar a branch descartável, as três suítes rodam verdes (comparação,
abaixo). *Desvio assumido*: a task pede "um por vez"; rodar 14 vezes não acrescentaria prova, porque cada
violação já saiu isolada na lista, e a volta ao verde é demonstrada pela rodada completa sobre o commit limpo.

## Comparação (T023)

Rodada sobre o commit da fatia 1 (`8f77253`, mais o ajuste de prazo do teste do guardião de TypeScript, nota 2).

| Suíte | Linha de base (`main`, 2026-10-10) | Fatia 1 | Veredito |
|---|---|---|---|
| `backend` — `core` (Surefire) | 324 | 324 | igual |
| `backend` — `aplicacao` unitários | 53 | **79** (+26: `LanguageGuardTest` 1, `LanguageGuardSelfTest` 12, `ArtifactsLanguageGuardSelfTest` 13) | maior, só pelos guardiões |
| `backend` — Failsafe padrão | 252 (1 ignorado) | **253** (1 ignorado; +1 `ArtifactsLanguageGuardIT`) | maior, só pelo guardião |
| `backend` — Failsafe `transporte-interno` | 71 | 71 | igual |
| `backend` — total | 700 | **727** | verde (`BUILD SUCCESS`) |
| `frontend` | 67 (11 arquivos) | **77** (13 arquivos: +1 guardião, +9 autotestes) | verde |
| `e2e` | 19 | 19 | verde (primeira rodada; nota 2) |

Nenhum teste removido e nenhuma expectativa de texto alterada (nenhum arquivo de teste existente foi tocado
nesta fatia). Guardiões verdes, com 1.100 exclusões temporárias (0 termos retidos).

**Cobertura do `core`**: fatia 1 = linhas 3243/3304 = **98,15 %**, branches 806/875 = **92,11 %**
(`evidence/slice-1-jacoco.csv`). Fica **1 linha e 1 branch abaixo** da linha de base de 2026-10-10 (3244 e 807).
**Não é efeito da fatia**: o módulo `core` não foi tocado, e a árvore **pura da `main`**, medida em 2026-10-11 com
o mesmo comando, dá exatamente 3243/806 (`evidence/baseline-main-jacoco-2026-10-11.csv`), estável em 4 rodadas.
A diferença está numa única classe, `ObterDocumentoRelatorioCasoDeUso` (1 linha e 1 branch), e sugere um caminho
que depende do relógio ou da data (a linha de base foi medida um dia antes). **Anotado, não corrigido**: fragilidade
preexistente nos testes dessa classe. Daqui em diante a referência de cobertura é a da `main` **medida no mesmo
dia**; o piso de 90 % do JaCoCo segue folgado.

Notas da rodada:

1. A primeira tentativa do backend falhou por **resíduo de build** da branch descartável da prova
   (`V9__proof.sql` e `prova.properties` ficaram em `target/classes`; o guardião do catálogo e o `MigracaoV6IT`
   os enxergaram). Não era defeito do código: o `target` foi apagado e o backend rerodou verde. Quem repetir a prova
   deve rodar `mvn clean`.
2. A primeira tentativa do frontend falhou por **timeout de 5 s** do Vitest no guardião (a varredura do repositório
   levou 5,4 s no disco montado). O teste ganhou prazo explícito de 120 s. Backend e frontend foram rerodados
   depois; o `e2e` não foi repetido porque o arquivo alterado é um teste do Vitest, fora do e2e.
3. O backend levou ~30 min por rodada completa em contêiner (~18 min na linha de base): o guardião Java varre o
   repositório e o IT cria um banco descartável, sobretudo por causa do disco montado no Windows.

## Anotações (bug ou código morto: anotado, não corrigido)

- `RegistroAulaConversaIT`: 1 teste ignorado na `main` (preexistente).
- `infra/scripts/local.sh testes …` termina com `docker compose down -v`, que apaga o cache do Maven
  (`m2`) e o `node_modules` a cada execução; por isso cada rodada refaz os downloads (~1 min no frontend). Não
  é bug; custa tempo.
- O hook do `code-review-graph` imprime um `UnicodeEncodeError` (cp1252) ao final do commit no Windows. É da
  ferramenta do grafo, não do repositório; o grafo é atualizado mesmo assim.
- Efeito colateral local: `npm ci` rodado em contêiner sobre `frontend/` (para o Prettier) reinstalou o
  `frontend/node_modules` do host com binários Linux (a pasta é ignorada pelo Git). Quem desenvolve fora de
  contêiner deve rodar `npm ci` no host.
- `specs/003-migrate-code-to-english/tasks.md.tmp`: resto do `/speckit-tasks`, ignorado pelo Git (`*.tmp`).
