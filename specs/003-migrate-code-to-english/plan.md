# Implementation Plan: Migração do código para inglês

**Branch**: `spec/003-migrate-code-to-english` | **Data**: 2026-10-10 | **Spec**: [spec.md](./spec.md)

**Entrada**: especificação da feature em `specs/003-migrate-code-to-english/spec.md`.

## Summary

Refatoração pura: todos os nomes de código (pacotes, tipos, métodos, arquivos e pastas, tabelas e colunas,
valores de enumeração, rotas e campos das APIs, códigos de erro, ações de botão, chaves de catálogo,
variáveis de ambiente, scripts, workflows e classes CSS) passam de português para inglês, conforme o
Princípio VI da constituição 2.0.0. Textos mostrados ao usuário, logs, documentação, comentários e as
palavras que o professor digita no bot **não mudam**. Nenhuma regra, layout, comportamento ou camada muda.

Abordagem técnica (decisões em [research.md](./research.md)):

- **Sete fatias, um PR, um commit verde por fatia** (FR-031): glossário e guardião → `core` → `application`
  + `V7` → contratos e corte do fio + `V8` → frontend → infra/CI → documentação. Os nomes que cruzam banco,
  rede e catálogo ao mesmo tempo ("nomes de fio") são cortados uma única vez, na fatia 4, para que **as três
  suítes (`testes backend`, `testes frontend`, `testes e2e`) fiquem verdes em todos os commits** (R-01).
- **Renomeação por ferramenta semântica** (OpenRewrite em contêiner para Java, ts-morph para TypeScript) e
  **prova de que só o nome mudou** por um verificador lexical que alinha as árvores antes e depois e exige
  texto idêntico byte a byte (R-02). O `refactor_tool` do grafo não é usado: o grafo não indexa Java nem
  TypeScript neste repositório.
- **Banco**: `V7` (estrutura, chaves JSON só de banco) e `V8` (valores de enumeração e JSON do fio), sem
  editar `V1`–`V6`, com dados preservados e testes de equivalência estrutural e de dados legíveis (R-03).
- **Catálogo**: só as chaves e os marcadores `{…}` mudam; os textos são provados idênticos byte a byte (R-04).
- **Guardião** em três implementações (ArchUnit + varredura de fonte, TypeScript, SQL/YAML/JSON/catálogo do
  banco) a partir de um `glossary.json` com exceções documentadas, rodando nos contêineres de teste (R-05).
- Contratos já escritos com os nomes novos e verificados contra os atuais: 0 diferenças de forma (R-06).

## Technical Context

**Language/Version**: Java 25 (Spring Boot 4, Maven multi-módulo `core` + `application`), TypeScript ~5.9
(React 19, Vite 8), SQL (PostgreSQL 18), shell POSIX, YAML

**Primary Dependencies**: Spring Boot 4, Spring Data JPA/Hibernate (`ddl-auto: validate`), Flyway, Jackson 3
(`tools.jackson`), Thymeleaf + gerador de PDF, ArchUnit, JUnit 5, JaCoCo, Spotless (google-java-format AOSP);
React Router 7, TanStack Query 5, Vitest 5, Playwright 1.63 + axe, `openapi-typescript` 7. Para a migração:
OpenRewrite (`rewrite-maven-plugin`, só em linha de comando) e ts-morph (`--no-save`), ambos fora do build.

**Storage**: PostgreSQL 18 (Flyway). Esta feature só **renomeia**; nenhum tipo, tamanho, constraint ou
regra muda (FR-014).

**Testing**: tudo em contêiner (Princípio V e "Containerização"): `sh infra/scripts/local.sh testes
backend|frontend|e2e` (até a fatia 5; ver *Pontos de decisão* sobre o nome do subcomando depois da 6).
Backend: unitários, ArchUnit, integração com `postgres-teste`, contrato (`CONTRATO_API`), paridade,
privacidade, cobertura ≥ 90% (linha e branch) no `core`. Frontend: lint, `tsc`, Vitest; E2E: Playwright + axe.
Novos testes: três guardiões e seus autotestes; testes de migração (equivalência estrutural, dados legíveis,
zero chaves antigas, valor desconhecido aborta, proteções ativas); regressão do botão no formato antigo.

**Target Platform**: Linux (contêineres Docker; Docker Engine 27+ e Compose v2 como únicas dependências);
desenvolvimento em Windows/macOS/Linux com Git Bash.

**Project Type**: aplicação web (API Spring + SPA React + infra de contêineres) — monorepo.

**Performance Goals**: nenhuma mudança (FR-005). A `V7`/`V8` rodam em segundos nos volumes de dados de
desenvolvimento.

**Constraints**: comportamento observável idêntico; zero expectativa de texto alterada; cobertura não
decresce; esquema final idêntico em estrutura; sem compatibilidade com nomes antigos (FR-024); sem nova
abstração de produção; commits e PR em português (Princípio VIII); merge só pelo usuário.

**Scale/Scope**: 620 arquivos rastreados (217 em `nucleo`, 269 em `aplicacao`, 77 em `frontend`, ~20 em
`infra`/CI); 17 tabelas, 6 migrações existentes; ~230 chaves de catálogo; 5 contratos formais; ≈ 4,1 mil
palavras distintas em identificadores (inventário, R-05).

## Constitution Check

*Porta: DEVE passar antes da Fase 0 e ser reavaliada depois da Fase 1.* Fonte:
`.specify/memory/constitution.md` v2.0.0.

| Princípio | Avaliação | Como o plano garante |
|---|---|---|
| **I. Domínio independente de canal e de fornecedor** (camadas intactas) | **PASS** | A migração só troca nomes; a regra de dependência `domain → usecase → adapters` não muda. Os testes ArchUnit existentes (domínio não depende de casos de uso, serviços ou portas; portas não dependem de casos de uso/serviços; serviços não dependem de casos de uso; sufixos; controladores só na entrada web; sem fornecedor no núcleo) são **migrados, não afrouxados**: pacotes e sufixos novos (`domain`, `usecase`, `port`, `service`, `inbound`, `outbound`; `*UseCase`, `*Port`, `*Repository`, `*Adapter`, `*Controller`, `*Mapper`, `*Dto/*Request/*Response`), mesmas asserções. Prova de que seguem barrando: uma violação provocada em branch descartável deve reprovar cada regra. A lista de fornecedores proibidos no núcleo (`groq`, `openai`, `whisper`, `llama`, `ffmpeg`, `openhtmltopdf`, `thymeleaf`, `postgres`, `spring`, `n8n`) é mantida. Nenhuma camada nova, nenhuma regra movida. O módulo continua sem Spring/JPA. |
| **II. Fonte única da verdade** | **PASS** | Sem mudança de escrita no banco nem papel do n8n. As ações do canal e a outbox só mudam de nome (e as ações dos botões, de vocabulário). |
| **III. Humano no controle da IA** | **PASS** | Aprovação por versão, versionamento e "IA não inventa" intactos; só os nomes dos campos da IA mudam (R-07), com verificação manual no LLM real antes do merge (FR-034). |
| **IV. Privacidade desde o design** | **PASS** | Nenhum dado novo circula. O guardião, o verificador e a `V8` imprimem **nomes**, nunca valores de texto/CPF/aluno. O `pg_dump` do caminho com dados existentes é local e descartável. Segredos continuam só no ambiente; a regeração do `.env` é orientada, sem registrar valores. |
| **V. Qualidade obrigatória** (cobertura mantida) | **PASS** | Cobertura de linha e branch do `core` **medida antes e depois, por commit**, e nunca abaixo de 90% nem da base (R-02, R-10). Nenhum teste é removido; só os identificadores deles migram. Nenhuma expectativa de texto muda (provado por literal idêntico byte a byte). Testes de contrato continuam (contrato novo adotado no mesmo commit do fio novo). Chamadas reais a provedores continuam fora da cobertura unitária (`iareal`). O CI continua bloqueando o merge por teste vermelho ou cobertura baixa; o guardião entra como verificação obrigatória sem workflow novo (roda nos contêineres que o CI já exige). |
| **VI. Nomenclatura, linguagem ubíqua e idioma (v2.0.0)** | **PASS — é o objeto da feature** | Código/identificadores/dados/rotas/variáveis/scripts/CSS em **inglês** pelo glossário (constituição + tabela complementar aprovada, FR-016/FR-017, sem sinônimos); textos para usuário e logs em português acentuado, **intocados**; documentação, specs, planos, tasks, comentários, commits e PR em português. Sufixos padronizados aplicados a tudo (FR-018). Exceções só as permitidas pela constituição (siglas, terceiros, nomes impostos por ferramenta), cada uma com motivo no `glossary.json`. A regra de transição é cumprida por esta feature (a única permitida); a emenda 2.1.0 (fatia 7) registra a quitação e suspende o bloqueio. |
| **VII. Simplicidade e foco** | **PASS** | **Nenhuma abstração, camada, porta ou integração de produção é criada.** Código novo é só de teste (guardiões, migração) e uma ferramenta pontual de verificação fora do build. A alternativa de camada de compatibilidade (`@JsonProperty` com nomes antigos, conversor de enumeração) foi rejeitada por ser abstração nova (R-01). A função auxiliar da migração é `pg_temp` (some com a sessão). |
| **VIII. Fluxo de trabalho** | **PASS** | Branch `spec/003-migrate-code-to-english` criada da `main` atualizada; um PR com sete commits (um por fatia) aberto ao concluir; **sem merge e sem auto-merge**; `code-review-graph build` depois de criar o PR; automações (CI, scripts, compose) versionadas. |
| **IX. Identidade visual consistente e acessível** | **PASS** | Nenhuma mudança visual. Classes e variáveis CSS mudam de nome com o mesmo estilo; o e2e com axe (WCAG 2.1 AA) segue na suíte e verde em todos os commits; ativos de marca renomeados sem alterar o conteúdo. |

**Restrições adicionais.** *Containerização*: testes e guardiões em contêiner; nada além do runtime no
host (exceto Git para a árvore-base do verificador, que a máquina já tem). *Provedores plugáveis*: o
núcleo continua sem citar fornecedor. *n8n*: workflows ainda não existem (feature 004). *VPS genérica*:
inalterado.

**Quality gates.** O PR comprova, na descrição: paridade Web ≡ Telegram (suíte `parity` verde), cobertura,
revisão de privacidade (nenhum dado novo), ciclo de relatório (suíte verde), identidade visual (e2e axe).

**Resultado do portão**: **PASS**, sem violações; *Complexity Tracking* vazio.

**Reavaliação pós-design (Fase 1)**: **PASS**. O design (contratos, mapa de tabelas, V7/V8, guardiões) não
introduz camada, porta nem dependência de produção; as dependências novas (OpenRewrite, ts-morph) são de
uso pontual na linha de comando, fora de `pom.xml`/`package.json`.

## Project Structure

### Documentation (this feature)

```text
specs/003-migrate-code-to-english/
├── plan.md                      # este arquivo
├── research.md                  # Fase 0: R-01 a R-11 (fatiamento, ferramenta, V7/V8, catálogo, guardião…)
├── data-model.md                # Fase 1: SÓ o mapa de renomeação de tabelas e colunas
├── quickstart.md                # Fase 1: como provar cada fatia e o conjunto
├── contracts/                   # Fase 1: contratos já com os nomes novos
│   ├── api-portal.openapi.yaml            # substitui specs/001/.../api-portal.openapi.yaml (fatia 4)
│   ├── telegram-channel-internal.openapi.yaml   # era canal-telegram-interno.openapi.yaml (fatia 4)
│   ├── actions-v1.schema.json             # era acoes-v1.schema.json (fatia 4)
│   ├── ai-lesson-report.schema.json       # era ia/schemas/relatorio-aula.schema.json (fatia 3)
│   ├── ai-semester-report.schema.json     # era ia/schemas/relatorio-semestral.schema.json (fatia 3)
│   ├── glossary.json                      # fonte do guardião → .specify/memory/glossary.json (fatia 1)
│   └── identifier-map.md                  # erros/catálogo, enums, JSON, botões, rotas SPA, env, infra
├── tools/                       # criado na execução: rename-check.mjs, message-key-map.json (evidência)
├── checklists/requirements.md
└── tasks.md                     # Fase 2 (/speckit-tasks) — NÃO criado por /speckit-plan
```

### Source Code (raiz do repositório) — antes → depois

```text
backend/
├── pom.xml                      # groupId br.com.felipeblaft.reports, artifactId reports
├── nucleo/            → core/            # Java puro: sem Spring, JPA, HTTP nem fornecedor
│   └── src/{main,test}/java/br/com/felipeblaft/relatorios/
│        {dominio,casodeuso,porta,servico}            →  .../reports/{domain,usecase,port,service}
│        (dominio/{acesso,cadastro,canal,comum,relatorio,semestral,telegram,trabalho})
│                                                      →  domain/{access,registry,channel,common,report,semester,telegram,job}
│        (test) arquitetura, dubles                    →  architecture, doubles
└── aplicacao/         → application/     # liga o core ao mundo
    ├── Dockerfile, docker-entrypoint.sh              # aplicacao.jar → application.jar; comandos seed/demo-data/issue-code
    └── src/
        ├── main/java/br/com/felipeblaft/relatorios/
        │    {entrada,saida,configuracao}             →  .../reports/{inbound,outbound,config}
        │    entrada/{web,telegram,telegram/canal,interno,carga}  →  inbound/{web,telegram,telegram/channel,internal,seed}
        │    saida/{persistencia,ia,audio,pdf,armazenamento,fila,telegram,seguranca}
        │                                              →  outbound/{persistence,ai,audio,pdf,storage,queue,telegram,security}
        ├── main/resources/{db/migration (V1–V6 intactas + V7 + V8), ia→ai, mensagens→messages, prompts, pdf (fontes→fonts, imagens→images), application.yaml}
        └── test/java/.../{arquitetura,paridade,privacidade,iareal,suporte,…} → {architecture,parity,privacy,realai,support,…}
frontend/
├── src/{app,paginas,componentes,servicos,estilos,mensagens}   →  {app,pages,components,services,styles,messages}
├── tests/{componentes,paginas,e2e}                             →  {components,pages,e2e}
├── public/marca                                                →  public/brand
└── scripts/gerar-mensagens.mjs                                 →  scripts/generate-messages.mjs
infra/
├── compose*.yaml (banco→database), scripts/{local.sh,implantar.sh→deploy.sh}, carga/→seed/, caddy/, .env.example
.github/workflows/{ci.yml, implantar.yml→deploy.yml}
.specify/memory/{constitution.md (emenda 2.1.0), glossary.json (novo)}
```

**Structure Decision**: nenhuma estrutura nova nasce (FR-006: só renomear); por isso **nenhuma nova estrutura
exige `CLAUDE.md` novo**, mas os `CLAUDE.md` existentes de cada estrutura mudam na fatia 7 (e o de `.specify/`
já na fatia 1, por causa do glossário). O mapa completo de nomes de pacotes, pastas e arquivos de recurso
está em [identifier-map.md](./contracts/identifier-map.md) §7–§8 e o de tabelas/colunas em
[data-model.md](./data-model.md).

## Fatias, conteúdo e prova (para `/speckit-tasks`)

| # | Fatia | Prova de "verde" ao fim | Prova de "só o nome mudou" |
|---|---|---|---|
| 1 | Glossário e guardião | as 3 suítes + guardiões com exclusões temporárias cobrindo tudo ainda em português; autotestes de cada guardião; passo do CI | n/a (só adiciona) |
| 2 | `core` | backend (unidade, ArchUnit, integração, contrato, paridade, privacidade, cobertura) + frontend + e2e | `rename-check` core+testes; JaCoCo antes/depois |
| 3 | `application` + `V7` | idem + testes de migração (V7) | `rename-check`; catálogo do esquema; contratos de IA; prompts por palavra |
| 4 | Contratos + corte do fio + `V8` | idem + testes de migração (V8) + contratos formais novos + `typecheck` | `rename-check` (backend e frontend); catálogo de mensagens byte a byte; contratos 0 forma |
| 5 | Frontend | idem | `rename-check` do frontend e CSS |
| 6 | Infra, scripts, CI | idem + subida do zero (`quickstart.md`) | `rename-check` de YAML/shell |
| 7 | Documentação e emenda | idem; guardião sem exclusões; `ci.yml` sem arquivo de exclusões | links e nomes citados existem (SC-007) |

A ordem interna de cada fatia: **mapa/contrato → ferramenta → compilação → suíte → verificador → commit**.
Dependências entre tarefas: 1 → 2 → 3 → 4 → 5 → 6 → 7 (cada uma parte do estado verde da anterior).

## Pontos de decisão (para confirmação do dono do produto)

Resolvidos no plano com a opção recomendada; mudam o tamanho do trabalho se forem revertidos.

1. **`V7` + `V8` em vez de uma só migração, e frontend dividido entre as fatias 4 e 5.** *Recomendado:*
   mantém as três suítes verdes em todos os commits (R-01). *Alternativa:* uma `V7` e o corte do fio já na
   fatia 3, com o `e2e` declarado vermelho nas fatias 3 e 4 por construção (backend novo, frontend
   antigo). O pedido ("cada fatia compilando e com a suíte verde") favorece a recomendada.
2. **Subcomandos do `local.sh` em inglês na fatia 6** (`testes` → `tests`, `subir` → `up`, `ia-real` →
   `real-ai`). O pedido cita `sh infra/scripts/local.sh testes backend|frontend|e2e`; **até a fatia 5 esse é o
   comando usado**. FR-010 manda migrar nomes de scripts e FR-024 proíbe alias. *Recomendado:* migrar.
   *Alternativa:* manter `testes` e os demais subcomandos como vocabulário de interface do operador e
   registrá-los como exceção no `glossary.json` (menos trabalho, mas contraria o espírito de FR-010).
3. **Projeto do compose, volumes e banco/usuário `relatorios` → `reports`** (fatia 6). *Recomendado:* migrar
   (SC-001, sem exceção). Custo: volumes locais antigos não são reaproveitados automaticamente; caminho
   com dados no quickstart. *Alternativa:* manter o nome do banco/usuário/volumes como exceção ("persistido
   em volume").
4. **Fora de escopo, nomes mantidos**: `portal-do-professor-design-system/`, `assets/`, e os documentos
   `HANDOFF_MVP_Relatorios_Escola_Musica.md`/`PLANO_EXECUCAO_SPECKIT.md` (material de referência do cliente e
   documentação, FR-023). *Alternativa:* renomear as pastas (quebra links nas specs).
5. **Domínio público no `$id` do esquema** (`relatorios.felipeblaft.com.br`): mantido como exceção, só o
   caminho muda. Se o domínio real for outro, ajustar antes da fatia 4.
6. **Ferramenta Java**: OpenRewrite se o spike da fatia 2 passar com Java 25; senão renomeação da IDE por
   pacote. O resultado passa pelo mesmo verificador; só o esforço muda.

## Complexity Tracking

Sem violações da constituição a justificar. As três decisões que acrescentam trabalho (V8, divisão do
frontend, ferramenta de verificação) estão em *Pontos de decisão* e em R-01/R-02; nenhuma adiciona abstração
de produção.
