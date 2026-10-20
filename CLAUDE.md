# Sistema de Relatórios Pedagógicos por Voz — Instituto Musical Felipe Blaft

Regras invioláveis (arquitetura, segurança, qualidade, nomenclatura): `.specify/memory/constitution.md`.
Ela prevalece sobre qualquer outra instrução.

**Idioma (Princípio VI, constituição 2.1.0)**: código e identificadores em **inglês** (tipos,
funções, arquivos, pastas, tabelas, colunas, rotas, campos de API, códigos de erro, variáveis de
ambiente, scripts, workflows de CI, classes CSS); texto mostrado ao usuário e mensagens de log em
**português do Brasil acentuado**; documentação, specs, planos, tasks, comentários, commits e PRs em
**português**. O glossário pt→en está no Princípio VI e, em forma de dados, em
`.specify/memory/glossary.json`. Um teste guardião (Java e TypeScript) reprova o build quando uma
palavra em português aparece em identificador, nome de arquivo ou pasta; termo novo entra no glossário
**antes** de ser usado.

## Mapa do projeto

Cada estrutura tem o próprio `CLAUDE.md`, que descreve o que ela é e as regras locais. **Ao trabalhar
em uma estrutura, leia o `CLAUDE.md` dela.** Este arquivo raiz guarda apenas o que vale para o
repositório inteiro.

**Toda estrutura nova DEVE nascer com o seu `CLAUDE.md`** (pasta de primeiro nível, módulo ou
subprojeto, como um novo módulo do `backend/`). No mesmo PR que a cria:
1. crie o `CLAUDE.md` dela, dizendo o que ela é, o que contém e as regras locais;
2. acrescente uma linha na tabela abaixo.

| Estrutura | O que é |
|---|---|
| `backend/` | API em Java/Spring (multi-módulo Maven): `core/` (regra de negócio) e `application/` (adaptadores) |
| `frontend/` | Portal do Professor (SPA React + TypeScript) |
| `infra/` | Contêineres, ambiente local, proxy HTTPS e implantação na VPS |
| `infra/n8n/` | n8n, o transporte do canal Telegram: imagem fina, workflows como código e utilitários (só transporta) |
| `infra/telegram-simulator/` | Simulador do Telegram, só para os ensaios de registro do webhook e de rotação (nunca em produção) |
| `specs/` | Specs, planos, contratos e tarefas de cada feature (Spec Kit) |
| `.specify/` | Constituição, templates e scripts do Spec Kit |
| `.github/` | CI (`ci.yml`) e workflow de implantação (`deploy.yml`) |
| `.claude/` | Skills (Spec Kit, grafo e do projeto) e agentes revisores do Claude Code, versionados e revisados por PR |
| `portal-do-professor-design-system/` | Protótipos HTML da identidade visual (referência, não código de produção) |
| `assets/` | Material de referência do cliente |

`.claude/` guarda as skills do Spec Kit, os hooks do grafo, as skills do projeto e os agentes revisores. As
skills `speckit-*`, as do grafo e o `settings.json` são gerados por ferramenta: não edite à mão. As skills e os
agentes do projeto (ver `.claude/CLAUDE.md`) são editados por PR, como qualquer outro código.

## Fluxo de trabalho com Git

1. Toda feature nova (ou spec, correção, emenda) nasce em **uma branch nova criada a partir da `main`**.
2. **Antes de criar a branch**, atualize a `main`: `git checkout main && git pull --ff-only origin main`.
   Faça isso a cada nova branch, sem reaproveitar branch de outra feature.
3. Implemente e commite na branch nova; nunca na `main`.
4. Ao concluir, **abra o Pull Request** para a `main`, sem esperar novo pedido, para validação.
5. **Nunca mescle** o PR nem habilite merge automático: o merge é feito pelo usuário após revisão.
6. Depois de criar **cada** PR, rode `code-review-graph build` para manter o grafo atualizado.

Regra de origem: Princípio VIII da constituição. Em divergência, a constituição vale.

<!-- code-review-graph MCP tools -->
## MCP Tools: code-review-graph

**IMPORTANT: This project has a knowledge graph. ALWAYS use the
code-review-graph MCP tools BEFORE using Grep/Glob/Read to explore
the codebase.** The graph is faster, cheaper (fewer tokens), and gives
you structural context (callers, dependents, test coverage) that file
scanning cannot.

### When to use graph tools FIRST

- **Exploring code**: `semantic_search_nodes_tool` or `query_graph_tool` instead of Grep
- **Understanding impact**: `get_impact_radius_tool` instead of manually tracing imports
- **Code review**: `detect_changes_tool` + `get_review_context_tool` instead of reading entire files
- **Finding relationships**: `query_graph_tool` with callers_of/callees_of/imports_of/tests_for
- **Architecture questions**: `get_architecture_overview_tool` + `list_communities_tool`

Fall back to Grep/Glob/Read **only** when the graph doesn't cover what you need.

### Key Tools

| Tool | Use when |
| ------ | ---------- |
| `detect_changes_tool` | Reviewing code changes — gives risk-scored analysis |
| `get_review_context_tool` | Need source snippets for review — token-efficient |
| `get_impact_radius_tool` | Understanding blast radius of a change |
| `get_affected_flows_tool` | Finding which execution paths are impacted |
| `query_graph_tool` | Tracing callers, callees, imports, tests, dependencies |
| `semantic_search_nodes_tool` | Finding functions/classes by name or keyword |
| `get_architecture_overview_tool` | Understanding high-level codebase structure |
| `refactor_tool` | Planning renames, finding dead code |

### Workflow

1. The graph auto-updates on file changes (via hooks).
2. Use `detect_changes_tool` for code review.
3. Use `get_affected_flows_tool` to understand impact.
4. Use `query_graph_tool` pattern="tests_for" to check coverage.
