# Research: Migração do código para inglês

**Feature**: `003-migrate-code-to-english` | **Data**: 2026-10-10 | **Spec**: [spec.md](./spec.md)

Nenhum `NEEDS CLARIFICATION` ficou aberto: a spec já foi esclarecida (M1 a M10, JSON, LLM, glossário e PR).
Este documento resolve **como** executar. Os achados abaixo vêm de leitura do repositório em 2026-10-10
(620 arquivos rastreados: 217 em `backend/nucleo`, 269 em `backend/aplicacao`, 77 em `frontend`).
Mapas de nomes: [data-model.md](./data-model.md) (tabelas e colunas), [contracts/](./contracts/) (APIs,
esquemas, glossário e [identifier-map.md](./contracts/identifier-map.md)).

## R-01 Fatiamento, acoplamentos e o que cada fatia contém

**Problema encontrado.** Há nomes que cruzam camadas: o **nome de uma constante de enumeração** é, ao mesmo
tempo, o valor gravado no banco (`varchar`, `CHECK`s e índices parciais com literais), o valor no JSON da API
e a chave do catálogo/código de erro lido pelo frontend. O **nome de um componente de `record`** é ao mesmo
tempo o campo do JSON da API (DTOs), a chave dos `jsonb` gravados (`conteudo`, `informacoes_gerais`, payloads
da outbox) e o campo que a IA devolve (esquemas, prompts, respostas simuladas). Renomear no código sem
tratar isso quebra, em tempo de execução, o que o compilador não vê: `ddl-auto: validate`, `CHECK`s,
índices parciais, templates de PDF, contratos e o frontend.

**Decisão.** Manter as sete fatias na ordem pedida e acrescentar uma regra de fronteira que mantém
**compilação e todas as suítes verdes em cada commit**: o que é *nome de fio* (cruza a rede, o banco e o
catálogo ao mesmo tempo) é cortado **uma única vez**, na fatia 4, junto do frontend que o consome.

| Fatia (commit) | Conteúdo | O que NÃO entra (fica temporariamente em português) |
|---|---|---|
| **1** Glossário e guardião | `.specify/memory/glossary.json` (de `contracts/glossary.json`), os três guardiões, passo de CI, `.specify/CLAUDE.md` | tudo é exclusão temporária (lista por fatia, FR-035) |
| **2** `nucleo` → `core` | diretório e `artifactId`, pacotes, tipos, portas, casos de uso, serviços, métodos, campos, parâmetros, testes, dublês, regras ArchUnit; **referências de fora do módulo** a esses nomes (imports em `aplicacao`, `pom.xml`, `Dockerfile`, `compose*.yaml` e `ci.yml` só no caminho) | **B1** constantes de enumeração · **B2** componentes dos `record` que viram JSON (`ConteudoRelatorioAula`, `ConteudoRelatorioSemestral`, `InformacoesGerais` e os que a revisão da fatia achar) · **B3** literais de uma palavra que são chave de JSON gravado, chave de modelo de template ou chave do catálogo |
| **3** `aplicacao` → `application` + `V7` | diretório, `artifactId`, `application.jar`, pacotes, adaptadores, `*Controller/*Adapter/*Mapper`, entidades e `@Table/@Column`, `*Repository` e consultas, **chaves de configuração e variáveis de ambiente** (`application.yaml`, `.env.example`, `environment:` dos compose, `local.sh` leitura de chaves), comandos do CLI e perfil `seed`, **B2** (componentes de `record` + esquemas, prompts e respostas simuladas da IA + modelos de PDF) e **B3 persistidos**, `V7` (estrutura e chaves JSON só de banco) | **fio**: rotas, campos dos DTOs, cookie, códigos de erro, chaves do catálogo, ações de botão, JSON do canal interno, **B1** |
| **4** Contratos + corte do fio | os 5 contratos novos no lugar dos antigos, `CONTRACT_API`, DTOs/rotas/cookie/códigos de erro do backend, **B1**, catálogo e botões, `V8` (valores), regeneração dos tipos e **toda a parte do frontend que toca o fio** (clientes de API, acesso aos campos, chaves de mensagens, `localStorage`/cookie), `*.md` de contrato | nomes de pastas, componentes, páginas, classes CSS e testes do frontend (não tocam o fio) |
| **5** Frontend | pastas, componentes, páginas, rotas da SPA, CSS, ativos de marca, `scripts/`, testes Vitest e Playwright | — |
| **6** Infra, scripts, CI | nomes de arquivos/serviços/volumes/projeto do compose, subcomandos do `local.sh`, `implantar` → `deploy`, workflows, `infra/seed`, `README`s de `infra/` | — |
| **7** Documentação | `CLAUDE.md` de cada estrutura, READMEs, constituição (emenda 2.1.0), notas nas specs 001/002, "feature 003 → 004" | — |

Resultado: ao fim de **cada** commit, `testes backend`, `testes frontend` e `testes e2e` passam; só o que
cruza o fio muda em um único commit (o 4), que contém os dois lados. O guardião (fatia 1) vai ganhando
cobertura: a lista de exclusões temporárias lista, por fatia, os caminhos e o conjunto B1/B2/B3 ainda
pendentes, e **está vazia no commit 7** (e o CI recusa o PR se o arquivo de exclusões existir).

**Regra de referência.** Quem move ou renomeia uma coisa atualiza, **no mesmo commit**, toda referência a ela
(caminho em `Dockerfile`, `compose`, `ci.yml`, `pom.xml`, script, import, template, XML de log), ainda que o
arquivo referenciador "pertença" a uma fatia posterior. O arquivo referenciador só leva a edição de
caminho; a migração do *resto* dele fica na sua fatia.

**Matriz de suítes por commit** (todas verdes; "n/a" = não toca):

| Commit | Guardião | Backend (unit, arquitetura, integração, contrato, paridade, privacidade, cobertura ≥ 90%) | Frontend (lint, tipos, Vitest) | E2E + axe |
|---|---|---|---|---|
| 1 | verde com exclusões | verde | verde | verde |
| 2 | verde | verde | n/a (sem mudança de fio) | verde |
| 3 | verde | verde | n/a (fio congelado) | verde |
| 4 | verde | verde | verde | verde |
| 5–7 | verde | verde | verde | verde |

**Desvios do pedido (a confirmar, ver plan.md, *Pontos de decisão*)**:
(a) a migração do banco é **V7 + V8**, não só V7, pelo motivo acima; (b) a "regeneração dos tipos do
frontend" e parte do frontend entram na fatia 4, não na 5, porque tipos novos não compilam sem o código que
os usa; (c) os 7 commits e a ordem 1→7 são mantidos.

**Alternativas consideradas**:
- *Uma única `V7` e o corte do fio já na fatia 3*: o `e2e` fica vermelho nas fatias 3 e 4 (backend novo,
  frontend antigo) e `testes frontend` não prova nada nesse intervalo. Rejeitada por quebrar "cada fatia
  com a suíte verde".
- *Camada de compatibilidade (`@JsonProperty` com os nomes antigos, `@Enumerated` com conversor)*: mantém o
  fio, mas é abstração nova e temporária, proibida por FR-024 e pelo pedido ("não introduza nova abstração").
- *Tudo em um único commit*: não é revisável nem bisseccionável.

## R-02 Como renomear por ferramenta e como validar que nada além do nome mudou

### Ferramenta (FR-032)

**Estado do grafo (`code-review-graph`), verificado em 2026-10-10**: indexa só 8 arquivos (YAML e SQL, 101
nós `ConfigProperty`); **não há nós de Java nem de TypeScript**, e `refactor_tool` (modo `rename`) devolve
"No node found" para `RelatorioAulaMapeador`. Além disso, sua aplicação é substituição textual exata. Logo
o `refactor_tool` **não é adotado** para Java/TS: não oferece análise semântica (FR-032). O grafo continua
útil só para `detect_changes_tool` na revisão.

| Linguagem | Ferramenta | Como |
|---|---|---|
| Java (core, application, testes) | **OpenRewrite** (`rewrite-maven-plugin`) rodando no contêiner `maven:3.9-eclipse-temurin-25` já usado nos testes; **fallback: renomeação de refatoração da IDE** | receita YAML **gerada** do mapa de renomeação (`ChangePackage`, `ChangeType`, `ChangeMethodName`, renomeação de campo/variável/componente de `record`), executada por fatia, com o plugin passado por linha de comando (nada novo no `pom.xml` versionado) |
| TypeScript/TSX | **ts-morph** (serviço de linguagem do TypeScript), instalado com `npm install --no-save` dentro do contêiner `node:24-alpine`; não entra no `package.json` | script de renomeação de símbolos, de arquivos (`sourceFile.move`, que reescreve os imports) e de componentes JSX |
| CSS, HTML de PDF, `.properties`, YAML, JSON, SQL, shell, XML | **mapa fechado, token exato** | o verificador substitui apenas tokens do vocabulário fechado (extraído do próprio arquivo), e **cada ocorrência vira linha de relatório** para revisão; nada de `sed` livre. Contratos já foram escritos à mão neste plano |

**Spike obrigatório no início da fatia 2** (meio dia): provar o OpenRewrite com Java 25 sobre um pacote
pequeno (`dominio.cadastro`) e conferir `record`, `enum` e `sealed`. Se falhar, a fatia usa o fallback da IDE
**pacote a pacote**, em ordem de dependência (domínio → portas → serviços → casos de uso), com
`mvn -q compile` entre pacotes. Em ambos os casos o resultado passa pelo mesmo verificador abaixo.

**Pontos cegos** (o que a ferramenta semântica não renomeia e o compilador não acusa). A lista abaixo é o
roteiro de varredura de cada fatia; o verificador lista cada ocorrência por categoria:

1. **`valueOf(...)`/`.name()` ↔ `String`** (135 ocorrências em `aplicacao`): enum ↔ texto por nome.
2. **Jackson**: nome de componente de `record` e de getter vira campo JSON (DTOs, `jsonb`, IA).
3. **JPA**: `@Table(name)`, `@Column(name)`, `@Query` (JPQL e nativas), **métodos derivados `findBy…OrderBy…`**
   (embutem nome de propriedade; falham na subida do contexto), `ddl-auto: validate`.
4. **Chaves de modelo de template**: o HTML do PDF usa `${aluno}`, `${professor}`, `${imagens['logo']}`; as
   chaves nascem como literais em Java (`Map.of("aluno", …)`).
5. **Catálogo de mensagens**: chaves em literais Java, `msg('…')` no TS, `ResourceBundle` por nome-base
   (`mensagens/mensagens`).
6. **Configuração**: `@Value("${…}")`, `@ConfigurationProperties(prefix)`, chaves do `application*.yaml`,
   `logging.level.<pacote>`, FQCN em `logback-spring.xml` (`MascaramentoDadosSensiveisConversor`).
7. **Build**: `groupId/artifactId`, `finalName`, `mainClass`, id de regra do enforcer
   (`nucleo-sem-dependencias`), `COPY` do `Dockerfile`, `dockerfile:` do compose, caminho do artefato
   JaCoCo no `ci.yml`, `-pl aplicacao -am`.
8. **Strings de pacote** em ArchUnit (`BASE + ".casodeuso.."`) e sufixos (`"CasoDeUso"`, `"Controlador"`).
9. **Reflexão e `classpath:`** em testes (`@Sql`, caminhos de fixture), nomes de arquivo de recurso.
10. **Rotas e cookies**: `@RequestMapping/@GetMapping`, `Set-Cookie` `sessao`, cabeçalhos, `localStorage`.
11. **Shell/Compose**: `case`/`valor "CHAVE"` em `local.sh`, nomes de serviço e de volume.

### Validação de que só o nome mudou — `rename-check` (verificador lexical)

Ferramenta pontual em `specs/003-migrate-code-to-english/tools/rename-check.mjs` (Node, sem dependências),
executada no contêiner `node:24-alpine` comparando a árvore-base (`git archive origin/main`) com a árvore
de trabalho. Não é código de produção e não entra no build.

1. **Pareia arquivos** pelo mapa de caminhos (`git diff -M` + mapa de pastas da fatia).
2. **Tokeniza** cada par com o léxico da linguagem (Java e TS por léxicos próprios; CSS, SQL, YAML, JSON,
   `.properties`, shell por tokenizadores simples). Comentários e documentação **não** são comparados
   (Princípio VI: ficam em português e podem citar nomes novos); suas diferenças saem em uma lista para
   conferência rápida.
3. **Alinha token a token** (ordenando blocos de `import`, que o Spotless reordena). Exige o mesmo número de
   tokens. Cada diferença é classificada:
   - identificador ↔ identificador: o par `(antigo → novo)` DEVE existir no **mapa completo de
     identificadores** da fatia (`tools/maps/slice-<N>.json`). O português põe o adjetivo depois do
     substantivo (`RelatorioAula`→`LessonReport`, `codigo_acesso_hash`→`access_code_hash`), então a
     tradução NÃO é palavra a palavra: `tools/gen-identifier-map.mjs` extrai todos os identificadores da
     fatia, sugere o par (palavras do `glossary.json`, reordenação substantivo-adjetivo, conectivos
     descartados, valores com hífen em camelCase, entradas `contextual`) e marca `needsReview`; **todo
     item marcado é revisado à mão** antes da receita. `explicit-pairs.json` guarda só as exceções;
   - **código embutido em literal** (SQL/JPQL em `@Query`, JSON em *text blocks* de teste, URLs, rotas):
     tem espaço mas NÃO é texto para pessoas; é verificado **token a token** pelo mapa (cada identificador
     dentro do literal segue a regra acima) e o resto do literal fica idêntico. O verificador decide pelo
     contexto sintático (anotação `@Query`/`@Sql`, argumento de `jsonPath`, *text block* que parseia como
     JSON ou SQL), e o que não parsear vai para revisão manual, nunca para "texto";
   - literal de texto (tem espaço, acento ou maiúscula inicial isolada) que não é código embutido: DEVE
     ser **idêntico byte a byte**; o nome do PDF mostrado ao usuário (`relatorio-aula-…pdf`) é exceção
     documentada `download-file-name` e também deve permanecer idêntico;
   - literal de uma palavra: DEVE estar no mapa, como identificador;
   - número, operador, palavra-chave, anotação: DEVEM ser idênticos.
4. **Injetividade**: dois nomes antigos diferentes não podem virar o mesmo nome novo no mesmo escopo
   (detecta colisão que mudaria o comportamento), e o mesmo nome antigo não pode virar dois novos.
5. **Saída**: lista de pares fora do mapa (reprova), de tokens de texto alterados (reprova, e é o gatilho de
   FR-003/US1-4 "se uma expectativa de texto precisar mudar, o trabalho para"), de arquivos sem par, e as
   mudanças de comentário.

Verificações complementares por camada, todas automatizadas:

- **Comportamento**: as suítes completas (R-01) sem alterar expectativa de texto; o item 3 prova que nenhum
  literal de texto de teste mudou.
- **Cobertura (FR-004, SC-004)**: o relatório JaCoCo do `core` antes (base) e depois, por commit; a cobertura
  de linha e de branch não pode cair; o limite de 90% do `pom.xml` continua barrando o build.
- **Contratos**: comparação estrutural lado a lado (percorre o YAML/JSON antigo e o novo em paralelo; exige
  mesma forma e só aceita diferença de nome). Já executada neste plano: **0 diferenças de forma** nos 5
  contratos (316, 30, 114, 18 e 55 nomes/valores renomeados, todos conferidos).
- **Catálogo de mensagens**: R-04. **Esquema do banco**: R-03.
- **Ferramenta semântica**: a compilação (`mvn compile`, `tsc -b`) após cada lote.

## R-03 Migração do banco: `V7` (estrutura) e `V8` (valores)

Fontes: `V1` a `V6` (imutáveis, checksum do Flyway). Mapa de tabelas/colunas em [data-model.md](./data-model.md);
valores e chaves em [identifier-map.md](./contracts/identifier-map.md) §2 e §3.

### Por que duas migrações

`V7` (fatia 3) muda **nomes de estrutura** e chaves JSON que só o banco usa; o fio continua em português,
então o `e2e` segue verde. `V8` (fatia 4) muda **valores** que também são o fio (R-01). Em ambas nenhuma
migração anterior é editada (FR-013) e cada uma roda em uma transação (DDL transacional do PostgreSQL):
falhou, o banco fica na versão anterior.

### `V7__rename_to_english.sql` — ordem

1. **Tabelas**: `ALTER TABLE x RENAME TO y` (17 tabelas). Colunas, FKs, índices e dados acompanham pelo OID.
2. **Colunas**: `ALTER TABLE … RENAME COLUMN` (data-model.md). Um `CHECK` ou índice parcial que referencia a
   coluna segue a renomeação; o que NÃO segue são **literais** dentro dos predicados (ficam para a `V8`).
3. **Sequência da identidade** de `channel_action.seq` (`acao_canal_seq_seq`): `ALTER SEQUENCE … RENAME`.
   *Risco*: se o PostgreSQL recusar renomear a sequência de uma coluna `GENERATED ALWAYS AS IDENTITY`, a
   alternativa é recriar a identidade preservando o valor corrente; o teste de catálogo (abaixo) acusa o
   nome em português se sobrar.
4. **Restrições e índices**, um por um e **explícitos** (auditáveis, sem laço genérico): `PRIMARY KEY`
   (`professor_pkey` → `teacher_pkey`, …), `FOREIGN KEY` de nome automático (`aula_professor_id_fkey` →
   `lesson_teacher_id_fkey`, …), `UNIQUE`, `CHECK` com `ALTER TABLE … RENAME CONSTRAINT` (que também renomeia o
   índice subjacente) e índices avulsos com `ALTER INDEX … RENAME TO`. Padrão: `uk_professor_email` →
   `uk_teacher_email`; `ck_relatorio_aula_rodadas` → `ck_lesson_report_question_rounds`;
   `uk_relatorio_aula_em_aberto` → `uk_lesson_report_open`; `ix_acao_canal_chat_vivas` →
   `ix_channel_action_live_chat`; `uk_trabalho_consolidacao_em_curso` →
   `uk_processing_job_semester_consolidation_running`. Lista completa no corpo da migração e conferida pelo
   teste de catálogo.
5. **Chaves JSON só de banco**, antes de recriar o índice por expressão: função auxiliar **temporária**
   (`pg_temp`, existe só na sessão da migração; não é objeto do esquema) que percorre o `jsonb`
   recursivamente (`jsonb_each`, `jsonb_array_elements … WITH ORDINALITY` para manter a ordem) e renomeia
   **apenas chaves** por um mapa. Valores (textos do professor ou da IA) nunca são lidos nem alterados.
   Colunas e mapas: `lesson_report.content`/`report_version.content` (aula: 5 chaves), `semester_report.content`
   e `report_version.content` (semestral: seções e subchaves), `semester_report.general_information`,
   `processing_job.parameters` (`periodoChave` → `periodKey` e demais), `telegram_link.flow_data`.
6. **Índice por expressão JSON**: `uk_processing_job_semester_consolidation_running` usa
   `parametros ->> 'periodoChave'`; é descartado e recriado com `parameters ->> 'periodKey'` **depois** do
   passo 5. Sem isso o índice único deixaria de proteger contra consolidação em duplicidade, sem erro algum.
7. `flyway_schema_history` não muda.

### `V8__rename_enum_values.sql` — ordem

1. **Descarta** os objetos cujo predicado tem literal de enumeração: os `CHECK`s de `channel_action`
   (estado, tipo, origem, resposta a update), de `processed_telegram_update` (origem) e de
   `channel_document` (tipo), e os índices parciais de `lesson_report` (em aberto, aprovados),
   `processing_job` (registro em curso, consolidação em curso, pendente), `semester_report` (aprovado, em
   aberto) e `channel_action` (entrega, chats vivos).
2. **Valores de coluna** com `UPDATE … SET col = novo.valor FROM (VALUES …) novo WHERE col = novo.antigo`,
   por coluna (identifier-map.md §2): status, tipo, etapa, canal-meio, fluxo, etapa de fluxo, estado/tipo/origem
   da ação, códigos de resultado/erro/motivo, `report_type`, tipo de documento, tarefa de IA, origem de
   versão, resultado de interação e `prompt_version`.
3. **JSON do fio** (`channel_action.payload`/`compensation`): chaves pelo mapa de
   [actions-v1.schema.json](./contracts/actions-v1.schema.json); valor de `type`; e o texto de `data` dos
   botões (`v1:aprovar:7` → `v1:approve:7`) por troca do trecho da ação. Os textos `text`/`caption` não são
   tocados. `telegram_link.flow_data`: apenas valores que sejam ação de botão ou enumeração conhecida.
4. **Recria** os objetos do passo 1, mesmos nomes (já em inglês pela V7), predicados com os literais novos.
5. **Rede de segurança**: bloco `DO $$ … RAISE EXCEPTION` que aborta a migração se sobrar, em qualquer
   coluna enumerada, valor fora do conjunto novo. **Não há `ELSE` de passagem**: um valor desconhecido
   interrompe a migração em vez de ficar em português e só estourar depois em `valueOf`.
6. **Comprimento**: o valor novo precisa caber no `varchar(n)` da coluna (o limite apertado é
   `PERMANENTLY_FAILED`, 18 em `varchar(18)`); o `UPDATE` falha e a transação aborta se não couber, e o
   teste confere todos os valores contra o tamanho da coluna.

### Dados existentes em bancos locais (FR-015, US4-2)

- **Banco novo**: executa `V1…V8`; mesmo estado final por construção.
- **Banco no esquema antigo, com dados** (relatórios, versões, conversas do Telegram, trabalhos, outbox):
  `V7` e `V8` convertem no lugar; é *renomeação*, não recriação. Pare a aplicação antes (não há troca a
  quente; não há produção). Faça `pg_dump` antes (não há migração "down" no Flyway; o retorno é restaurar).
- **Trabalhos em andamento** (`PENDENTE`/`EM_EXECUCAO`): mantêm `parametros` e passam a `PENDING`/`RUNNING`.
- **PDFs já gravados**: `chave_armazenamento` é *dado* (caminho opaco que o serviço lê do próprio registro,
  `documento.chaveArmazenamento()`); documentos antigos continuam legíveis com a chave antiga. Só os novos
  usam nomes internos em inglês (`official.pdf`, `v<n>.pdf`, pasta por `LESSON`/`SEMESTER`); nenhum arquivo
  é movido. O nome mostrado ao usuário continua em português (FR-026). A troca do literal `"oficial.pdf"`
  (`GeracaoDocumentoServico`, `core`) é feita na **fatia 3** como renomeação de dado persistido novo (B3), com
  teste próprio que prova que o nome mostrado não mudou e que um documento novo usa a chave nova; a pasta
  por tipo de relatório vem da enumeração e muda com B1, na fatia 4.
- **Conteúdo de texto** (relatórios, perguntas, transcrição, `text` das ações): intocado.

### Verificação automatizada (testes de integração em `application`, contra o `postgres-test`)

1. **Equivalência estrutural (FR-014)**: aplica `Flyway.target("6")` em um esquema dedicado, captura a
   assinatura do catálogo (tabelas, colunas com tipo/nulabilidade/padrão, definição de `CHECK`, definição de
   índice, unicidades, FKs), migra para `7` (e depois `8`), captura de novo, aplica o mapa de
   data-model.md à primeira e exige igualdade, com as exceções declaradas dos literais da `V8`. O mapa é um
   recurso de teste (`db/rename-map.yaml`) cuja cobertura é verificada: toda tabela/coluna da `V6` precisa
   estar nele.
2. **Dados legíveis (FR-015)**: no esquema na `V6`, grava um relatório de aula com versões, uma conversa do
   Telegram com áudio pendente, um trabalho de consolidação, uma ação da outbox com teclado e um documento
   de canal; migra; lê pelos **adaptadores reais** e exige objetos de domínio iguais aos esperados.
3. **Zero chaves antigas**: depois da `V8`, percorre todo `jsonb` e exige que nenhuma chave do vocabulário
   antigo exista.
4. **Valor desconhecido aborta**: insere um status inexistente antes da `V8` e exige a falha da migração.
5. **Catálogo em inglês**: o guardião de SQL (R-05) varre os nomes do esquema final.
6. **Proteções ativas**: tenta violar `uk_lesson_report_open`, o índice de consolidação e os `CHECK`s
   recriados, para provar que as proteções continuam ativas depois da recriação.

**Alternativas**: uma migração só (R-01, rejeitada); recriar tabelas e copiar (descarta `OID`s, perde
sequência e é maior); `DROP/CREATE` do esquema (apaga dados, proibido por FR-013); `ELSE` de passagem na
`V8` (esconde valor desconhecido).

## R-04 Catálogo de mensagens: trocar só as chaves

**Estado.** `backend/aplicacao/…/mensagens/mensagens_pt_BR.properties`: 249 linhas, ~230 chaves, linhas
`CHAVE=texto` com marcadores `{nome}`. É a fonte única dos dois canais: o backend lê por `ResourceBundle`
(`CatalogoMensagens`), o frontend gera `src/mensagens/mensagens.json` por `scripts/gerar-mensagens.mjs`. A
chave de um erro é o próprio `CodigoErro` (`chaveMensagem()` devolve `name()`, exceto
`RELATORIO_CANCELADO` → `RELATORIO_CANCELADO_ERRO`).

**Decisão.**
1. **Mapa de chaves único**, gerado palavra a palavra pelo glossário e revisado: ~40 chaves de erro estão em
   [identifier-map.md](./contracts/identifier-map.md) §1; o resto sai em `tools/message-key-map.json` (evidência
   versionada no PR). O mesmo mapa serve a `.properties`, Java e TS.
2. **Marcadores são identificadores**: `{minutos}` → `{minutes}`, `{nome}` → `{name}`, `{alunoAtual}` →
   `{currentStudent}`, `{limiteMb}` → `{limitMb}`, etc., por um mapa de marcadores, aplicado nos valores
   *apenas dentro de chaves `{}`* e nos pontos de chamada (`Map.of("minutos", …)`, `msg(c, {minutos})`).
3. **Marcadores guardados**: como `{minutos}` é identificador (item 2) mas está dentro de texto para
   pessoas, o guardião de artefatos varre, nos **valores** do `.properties`, apenas o padrão
   `{[A-Za-z]+}` (nada do resto do texto), de modo que um marcador novo em português reprova.
4. **Troca nos pontos de uso** por correspondência exata com o conjunto **fechado** das chaves antigas
   (um literal igual a uma chave do catálogo é, por construção, um uso; colisões com valores de enumeração
   são listadas e decididas uma a uma, não trocadas em massa).
4. **Fatiamento**: nas fatias 2 e 3 as chaves continuam em português (B3), com `chaveMensagem()` intocado;
   na fatia 4 trocam juntas no `.properties`, nos literais Java, em `CodigoErro` e no frontend. Assim
   `CatalogoMensagensTeste` (todo `CodigoErro` tem texto) e o frontend nunca divergem.
5. **Nome do arquivo e do pacote de recursos**: `mensagens/mensagens_pt_BR.properties` →
   `messages/messages_pt_BR.properties` na fatia 3 (com a nome-base do `ResourceBundle` e o `COPY` do
   `frontend/Dockerfile`). O sufixo `pt_BR` é localidade.

**Prova de que os valores não mudaram (SC-003)**, executada no fim das fatias 3 e 4 e antes do merge:
carrega o catálogo da árvore-base e o novo; para cada chave antiga `k` exige `novo[mapa[k]] ==
trocarMarcadores(antigo[k])` **byte a byte**; exige bijeção das chaves (mesmo total, sem sobra nem falta) e
repete a comparação no `mensagens.json` gerado. Os comentários do cabeçalho (documentação) ficam em
português; só os nomes de tipos citados neles acompanham.

**Alternativas**: renomear no `.properties` e deixar o código "se virar" (quebra em execução, sem erro de
compilação); tradução do texto (proibida, FR-002); alias das chaves antigas (proibido, FR-024).

## R-05 Teste guardião (FR-019 a FR-023, FR-035)

### Fonte de dados e exceções

[`glossary.json`](./contracts/glossary.json) (na fatia 1 vai para `.specify/memory/glossary.json`): `words`
(316 palavras em português → equivalente em inglês: glossário da constituição + tabela aprovada + o que o
inventário achar) e `exceptions` (cada uma com `id`, `scope`, `terms`, **`reason` obrigatório**). Casamento:
divide por camelCase/PascalCase/`snake_case`/`kebab-case`/ponto/barra, compara a palavra, ela sem `s` e sem
`es` finais; palavras idênticas ou ambíguas entre os idiomas (`data`, `no`, `do`, `total`, `base`, `status`…)
ficam **fora** da lista, e quem as acompanha (`aula` em `dataAula`) é que aciona a falha.
**Inventário de completude (primeira tarefa da fatia 1)**: extrai todas as palavras de identificadores do
repositório (hoje ≈ 4,1 mil palavras distintas, a maioria de bibliotecas), classifica as de origem
portuguesa e completa o `words` **antes** de ligar o guardião. Ao fim da fatia 7, repete o inventário e
revê as palavras restantes (rede contra o que a lista de palavras não conhece).

**Exceções permanentes** (detalhe e motivo em `glossary.json`): nome da organização
`br.com.felipeblaft`; migrações `V1`–`V8` (imutáveis, e quem as renomeia contém os nomes antigos; o esquema
*resultante* é varrido pelo teste de catálogo); as palavras que o professor digita no bot
(`aprovar`, `cancelar`, `inicio`, `registrar aula`, `relatorio semestral`, `historico`, `semestral`,
`continuar`, `descartar`, `tentar`, `tentar novamente`, `sair`, `ajuda`, `help`, `sim`, `start`, `menu`) só
em `TelegramKeywords` e seu teste (FR-011); nomes de arquivo de fontes/licenças de terceiros; o prefixo `fb-`
dos ativos da marca; o domínio público do `$id`; textos para pessoas (literal com espaço/acento, comentários,
logs, documentação, FR-023); pastas fora do escopo (`portal-do-professor-design-system/`, `assets/`, `specs/`,
`.claude/`, documentos de planejamento na raiz); nomes impostos por Telegram/n8n (`callback_data`,
`chat_id`, `X-N8N-Service-Token`). O guardião **falha se uma exceção não casar com nada** (exceção morta) e
se alguma não tiver motivo.

**Exclusões temporárias (FR-035)**: arquivo à parte, `.specify/memory/glossary-temporary-exclusions.json`,
só na branch. Ele lista **arquivos existentes na fatia 1** (por caminho) e, dentro de arquivos já migrados,
**termos retidos** (B1/B2/B3); arquivos novos ou renomeados nunca estão na lista, então já nascem varridos
(e é assim que a prova de que o guardião barra funciona em arquivo novo). Cada fatia regenera a lista a
partir do modo relatório dos guardiões (`GUARD_REPORT=1` + `tools/gen-exclusions.mjs`), que só encolhe. Cada
commit remove o que migrou; o commit 7 o apaga. O guardião lê o arquivo se existir; um
passo do `ci.yml` (`test ! -e …`) reprova o PR se ele existir, de modo que a `main` nunca o recebe. O CI só
roda em PR e em `push` na `main` (não em cada commit da branch), então o estado conferido é o do último commit.

### Três implementações, em contêiner (FR-022)

Todas leem o mesmo `glossary.json` pelo volume `..:/work` que os dois contêineres de teste já montam; o CI
já exige `testes-backend` e `testes-frontend`, então nenhum workflow novo é necessário.

1. **Java — `testes-backend`** (`application/src/test/…/architecture/LanguageGuardTest`; enxerga o
   `core` por dependência). *ArchUnit* sobre os `.class`: nome de pacote, de tipo (inclusive aninhado), de
   método, de campo, constante de enumeração e componente de `record`, e valores de anotação de uma palavra
   (`@Table`, `@Column`, `@RequestMapping`, `@JsonProperty`, `@Value`). *Varredura de fonte* complementar para
   o que o bytecode não guarda: parâmetros, variáveis locais e literais de uma palavra, usando um léxico
   mínimo que descarta comentários, blocos de texto e literais com espaço/acento/maiúscula inicial isolada
   (texto para pessoas). Também varre nomes de arquivos e pastas dos módulos.
2. **TypeScript — `testes-frontend`** (Vitest, `frontend/tests/language-guard.test.ts`): usa a API do
   compilador `typescript` (já é dependência de desenvolvimento) para percorrer `src`, `tests`, `scripts` e
   os arquivos de configuração: identificadores, nomes de propriedade e de atributo JSX, especificadores de
   `import`, valores de `className` (cada classe, separadas por espaço), literais de uma palavra e partes
   estáticas de *template literals* sem espaço (rotas); mais seletores, classes e propriedades
   customizadas dos `.css` e nomes de arquivos e pastas.
3. **SQL, YAML, JSON e configuração — `testes-backend`** (`…/architecture/ArtifactsLanguageGuardTest`):
   - *catálogo do banco*: depois de todas as migrações, consulta `information_schema` e `pg_catalog`
     (tabelas, colunas, índices, constraints, sequências) e falha por nome em português, exceto
     `flyway_schema_history`;
   - *arquivos*: SQL `V9` em diante; YAML (`application*.yaml`, compose, workflows, OpenAPI inclusive os de `specs/**/contracts/`) com SnakeYAML:
     chaves e valores de uma palavra; JSON (esquemas, cargas, `package.json` scripts) com Jackson; `.env.example`
     (nomes), `.properties` (só as **chaves**), `.sh`, `Dockerfile`, `Caddyfile`, `pom.xml`, `logback-spring.xml`,
     HTML e CSS de PDF (atributos, expressões `${…}`, classes); nomes de arquivos e pastas de `infra/`,
     `.github/` e recursos. Raízes explícitas de varredura; pula `target`, `node_modules`, `.git`,
     relatórios do Playwright.

**Recorte dos contratos em `specs/`**: a pasta `specs/` é isenta, **exceto** `specs/**/contracts/*.yaml` e
`*.json` (os contratos de fio e de IA), que SÃO varridos (FR-019, "contratos"). Os `.md` de contrato e a
prosa das specs não são varridos. O guardião de artefatos também varre os arquivos de ignorar
(`.gitignore`, `.dockerignore`, `backend/.dockerignore`) por caminhos com palavras do glossário.

**Separadores**: além de camelCase, `snake_case`, `kebab-case`, ponto e barra, a divisão usa `: = ? & ;`, de
modo que ações de botão (`v1:aprovar:7`), *query strings* e rotas sejam examinadas palavra a palavra.

**Categoria das exceções**: cada exceção do `glossary.json` traz `constitutionCategory`. As que não cabem nas
quatro categorias fechadas do Princípio VI (nomes próprios e material de referência) são ratificadas pela
emenda 2.1.0 e listadas na descrição do PR (plan.md, *Complexity Tracking*).

**Mensagem de falha (FR-021)**: `caminho:linha: "periodoChave" contém "periodo" → use "period"`, com a
lista de todas as ocorrências antes de reprovar (não para na primeira).

**Autoteste (US2 cenário 2)**: cada guardião tem testes que o alimentam com uma fonte sintética contendo
um termo do glossário em cada tipo de artefato (classe, método, variável, arquivo, pasta, tabela, coluna,
rota, campo de contrato, código de erro, chave de catálogo, variável de ambiente, classe CSS, script,
workflow) e exigem a falha com arquivo, termo e equivalente; e fontes com exceção ou texto para pessoas que
exigem o contrário. Não há ramo de produção para isso: é código de teste.

**Riscos**: falso positivo de texto de uma palavra em tela ("Carregando"): a regra da maiúscula inicial e do
acento cobre, e a primeira execução (fatia 1) lista os restantes para decisão; falso negativo por palavra
fora do `words`: coberto pelo inventário e pela revisão do fim.

**Alternativas**: dicionário de português (dependência grande, erra nomes próprios e siglas); lista de
permissão de inglês (inviável sem dicionário); ESLint/Checkstyle (não cobrem SQL/YAML/CSS, e seriam três
configurações a mais); um só guardião em um idioma (o pedido pede Java/ArchUnit, TypeScript e SQL/YAML).

## R-06 Contratos como fonte de verdade (FR-012)

- Os cinco contratos novos já estão em [contracts/](./contracts/) e foram verificados contra os atuais
  (R-02: 0 diferenças de forma). Na **fatia 4** eles substituem os atuais **no lugar** (`git mv`):
  `specs/001-fluxo-relatorios/contracts/api-portal.openapi.yaml` (mesmo caminho, então `CONTRACT_API` e
  `generate:api` não mudam de caminho), `specs/002-api-interna-canal-telegram/contracts/` com
  `acoes-v1.schema.json` → `actions-v1.schema.json` e `canal-telegram-interno.openapi.yaml` →
  `telegram-channel-internal.openapi.yaml`; os esquemas de IA vão para `application/src/main/resources/ai/schemas/`
  na fatia 3 (não são fio de rede, são o que o LLM devolve).
- Os contratos em Markdown dessas pastas (`mensagens.md`, `telegram-conversa.md`, `rotas-portal.md`,
  `carga-inicial.md`, `portas-provedores.md`, `canal-telegram-interno.md`) recebem **só troca de
  identificadores** na fatia que renomeia o que descrevem (portas: 2; carga: 3; mensagens, conversa, rotas do
  portal, canal interno: 4 e 5). A prosa em português e o histórico das specs 001/002 não são reescritos
  (FR-029); recebem a nota de histórico na fatia 7.
- Contratos *de forma* (`CONTRACT_API`, validação por `swagger-request-validator`/equivalente em
  `application/src/test`) passam a validar contra o contrato novo no commit 4, o mesmo em que o backend
  passa a falar o fio novo; é o mesmo teste, só com o arquivo atualizado.
- A regeneração de `api-tipos.ts` (`generate:api`) acontece no commit 4, e `typecheck` aponta no frontend
  todo ponto que ainda usa um nome antigo; é a rede que o TypeScript dá para a parte do frontend que toca o
  fio.

## R-07 Provedores de IA (FR-033, FR-034)

- Os nomes dos campos que o LLM devolve **são** os componentes de `record` do núcleo e as chaves do `jsonb`
  gravado; por isso esquemas (`ai/schemas`), respostas simuladas (`ai/simulated`), modelos de PDF e a leitura
  do JSON mudam **na mesma fatia** que os componentes (fatia 3, junto da `V7`, que converte o que já foi
  gravado).
- **Prompts**: o texto de instrução fica em português; só mudam os tokens que são nomes de campo
  (`conteudosTrabalhados:` → `workedTopics:`, `"transcricao"` → `"transcription"`). O verificador trata
  `prompts/*.txt` por palavras: só aceita diferença onde o token antigo está no conjunto fechado de campos
  dos dois esquemas; o resto é idêntico byte a byte. Arquivos: `estruturacao-aula.v1.txt` →
  `lesson-structuring.v1.txt` etc. (identifier-map.md §2 e §8). O identificador de versão do prompt
  (`prompt_version`) é dado gravado: a `V8` o converte; a fatia 3 confere o formato real.
- **Verificação manual com o LLM real (FR-034, SC-008)**: depois da fatia 3 e de novo no commit final, com
  `PROVEDORES_IA=real` (já `AI_PROVIDERS=real`): `sh infra/scripts/local.sh testes ia-real` (suíte `iareal`
  que já existe) e o fluxo de aula e o semestral completos pelos dois canais; resultado (campos
  preenchidos, perguntas, qualidade percebida, custo) registrado na descrição do PR. Queda de qualidade
  atribuível aos nomes é **reportada**, não corrigida mudando instrução.

## R-08 Telegram: ações de botão e palavras do professor

- Formato `v1:<ação>:<argumento>` preservado (limite de 64 bytes; ações em inglês são tão curtas quanto as
  antigas). Mudam as ações ([identifier-map.md](./contracts/identifier-map.md) §4), na fatia 4, com a `V8`
  convertendo botões já gravados na outbox (a `data` dos botões).
- **FR-025**: botão no formato antigo (`v1:aprovar:3`) chega a `DadosCallbackCodificador.decodificar` (→
  `CallbackDataCodec.decode`) com ação desconhecida; o plano manda **conferir o ramo "ação desconhecida"**
  dos fluxos e **acrescentar um teste de regressão** (entrada antiga → resposta segura, sem exceção); é
  teste novo de comportamento já existente, sem mudar código de produção.
- As palavras do professor ficam em português, **só no mapa de `TelegramKeywords`**; o `enum Comando`
  (identificador) renomeia (`INICIO` → `HOME`). O guardião as isenta pela exceção `teacher-keywords`.

## R-09 Infra, scripts, CI e ambientes locais existentes

- **Atomicidade de configuração (fatia 3)**: o backend passa a ler variáveis novas; portanto o `environment:`
  de `compose.yaml`, `compose.test.yaml` (`backend-e2e`, `testes-ia-real`), `.env.example`, `application-test.yaml`
  e as chaves lidas pelo `local.sh` mudam no **mesmo** commit. O resto (nomes de serviço, projeto, volumes,
  arquivos, subcomandos, workflows) é a fatia 6.
- **Projeto do compose, volumes e banco (fatia 6)**: `name: relatorios` → `reports` renomeia os volumes
  (`relatorios_pgdata` → `reports_pgdata`, `relatorios_documentos` → `reports_documents`) e, com banco e usuário
  `reports`, quem tem um volume antigo **não o reaproveita sozinho**. Como não há produção e dados locais
  são descartáveis, o caminho padrão é regerar (`up --demo-data=true`); quem quer manter dados usa o caminho
  do quickstart (`pg_dump` da pilha antiga → restaurar no banco novo → subir: a `V7`/`V8` rodam sobre os dados
  restaurados, que é o ensaio manual de US4-2). A decisão está em *Pontos de decisão*.
- **Subcomandos do `local.sh`** (`subir`, `carga`, `testes`, …) são vocabulário do script: passam a inglês na
  fatia 6, sem alias (FR-024). Até a fatia 5 o comando de testes continua `sh infra/scripts/local.sh testes
  backend|frontend|e2e`, e é o que cada fatia usa para provar a suíte verde.
- **`.env` antigo (US4-4)**: `exigir_env` já aborta listando as chaves obrigatórias que faltam; passa a
  acrescentar "se o seu `infra/.env` é anterior à feature 003, regere-o a partir de `infra/.env.example`".
  Não lista nomes antigos (não cria identificador em português dentro do script).
- **CI**: `ci.yml` mantém os jobs `backend`, `frontend`, `e2e`; muda o caminho do artefato JaCoCo
  (`backend/nucleo/target/site/jacoco/` → `backend/core/target/site/jacoco/`, no commit 2, **com
  `if-no-files-found: ignore` isso falharia calado**: o plano exige conferir o upload na execução), e
  `implantar.yml` → `deploy.yml`.
- **Dockerfile do frontend** copia o catálogo por caminho (`COPY backend/aplicacao/…/mensagens/`): muda com o
  diretório (commit 3) e com a pasta de recursos.
- **Arquivos de ignorar**: `.gitignore` (linhas que ignoram `infra/carga/*.json` e liberam `exemplo.json` e
  `demonstracao.json`) e `.dockerignore` (`infra/carga`) citam a pasta que vira `infra/seed`. Eles protegem
  cargas com dados reais de aluno (Princípio IV): mudam **no mesmo commit** do `git mv` (fatia 6), com prova
  por `git check-ignore -v` de que uma carga real em `infra/seed/` continua ignorada e os dois exemplos não.
- **Semente do e2e**: `frontend/tests/e2e/suporte.ts` semeia o banco com SQL próprio (`insert into professor …`,
  `insert into aluno …`, `insert into professor_aluno …`) e lista `pg_tables` para limpar. Os nomes de tabela e
  coluna dessa semente são referência fora do backend: mudam no **commit 3** (V7) e os valores de
  enumeração que ela insira, no **commit 4** (V8). O compilador e o `typecheck` não acusam: só o `e2e`.

## R-10 Qualidade e o que não muda

- **Sem nova abstração**: o único código novo é de teste (3 guardiões, autotestes, testes de migração,
  um teste de botão antigo) e a ferramenta pontual de verificação. Nenhuma classe, camada ou porta de
  produção é criada; a função `pg_temp` é descartada com a sessão da migração.
- **Princípio I**: as regras ArchUnit existentes continuam (domínio ↛ casos de uso/serviços/portas, portas ↛
  casos de uso/serviços, serviços ↛ casos de uso, sufixos, sem fornecedor no núcleo, controladores só em
  `inbound.web`); a fatia 2 migra os pacotes e os sufixos dentro das regras (`CasoDeUso` → `UseCase`, etc.) e
  mantém o teste vermelho para qualquer violação (provado por uma violação provocada em branch descartável).
- **Princípio V**: cobertura ≥ 90% do `core`, medida por linha e branch, comparada antes/depois por commit;
  os testes de contrato, de paridade entre canais (`parity`) e de privacidade continuam e migram só de nome.
- **Princípio VI (2.0.0)**: o guardião é a prova automática; a emenda 2.1.0 (fatia 7) registra a quitação.
- **Spotless/google-java-format (AOSP)**: roda após cada lote; reordena `import`s e quebra linhas (o
  verificador ignora espaço e ordena `import`).

## R-11 Entrega

- Branch `spec/003-migrate-code-to-english`, a partir da `main` atualizada (já criada); **um PR**, sete
  commits (1 por fatia, mensagens em português, `Co-Authored-By` conforme a política do projeto), aberto ao
  concluir, sem merge e sem auto-merge (Princípio VIII). `code-review-graph build` após criar o PR.
- A descrição do PR leva: matriz de suítes por commit, saída do `rename-check`, comparação do catálogo,
  comparação de contratos, comparação de cobertura, resultado do teste de migração, e a verificação
  manual com o LLM real (SC-008).
- "Feature 003" do n8n → "feature 004" nas specs 001/002 e na constituição (FR-028) na fatia 7, junto da
  nota de histórico (FR-029).
