# .claude/

Configuração do Claude Code para o repositório: skills, agentes e hooks. Tudo aqui é versionado.

| Caminho | O que é | Edição |
|---|---|---|
| `settings.json` | Hooks do grafo (`code-review-graph`) | Gerado por ferramenta; não edite à mão |
| `skills/speckit-*` | Fluxo do Spec Kit (specify, clarify, plan, tasks, implement...) | Gerado pelo Spec Kit |
| `skills/{debug-issue,explore-codebase,refactor-safely,review-changes}` | Navegação e revisão com o grafo | Gerado pelo `code-review-graph` |
| `skills/open-pr` | Fecha o trabalho: commit, push, PR para a `main` (nunca mescla) e `code-review-graph build` | Por PR |
| `skills/fix-language-guard` | Resolve falhas do guardião de idioma (relatório, glossário, exceções) | Por PR |
| `skills/run-tests` | Escolhe e roda a suíte em contêiner pelos arquivos alterados | Por PR |
| `skills/pre-pr-review` | Roteia os agentes revisores pelo diff e consolida um relatório único | Por PR |
| `agents/*.md` | Agentes revisores, somente leitura | Por PR |
| `worktrees/` | Cópias de trabalho temporárias; fora do Git (`.git/info/exclude`) | Não edite; ignore em buscas |

## Quem chama quem

`pre-pr-review` lê `git diff main...HEAD` e decide, pelos caminhos alterados:

| Sinal | Agente |
|---|---|
| `backend/**` | `backend-reviewer` |
| `frontend/**` | `frontend-reviewer` |
| `infra/**`, `.github/**` | `infra-security-auditor` |
| contratos, controllers, `frontend/src/services/**` | `contract-drift-checker` |
| aluno, CPF, áudio, log, autenticação, envio à IA | `privacy-auditor` |
| prompts, portas e adaptadores de IA | `ai-pipeline-reviewer` |
| qualquer mudança, por último | `constitution-auditor` |
| CI ou ensaio vermelho | `ci-failure-triage` |

Depois do relatório, o usuário chama `/open-pr`. Essa skill só roda por pedido explícito.

## Regras

- Os agentes **só leem e relatam** (`disallowedTools: Write, Edit, NotebookEdit`); quem corrige é a conversa principal.
  Exceção: `ci-failure-triage` tem `memory: project` e grava só a própria memória em `.claude/agent-memory/`.
- Agentes herdam as ferramentas da sessão (inclusive as do MCP `code-review-graph`); por isso restringimos por
  `disallowedTools` em vez de uma lista fixa `tools`.
- Nome de arquivo de skill e de agente em **inglês** (Princípio VI); descrição e corpo em **português**.
- Mantenha as `description` curtas: a soma delas entra no contexto de toda sessão. Detalhe vai no corpo.
- Agente novo: informe `name`, `description` (quando usar, com "use proativamente" se couber), `disallowedTools`
  e `model`; acrescente a linha em "Quem chama quem" e o roteamento em `skills/pre-pr-review/SKILL.md`.
- Nenhuma skill ou agente mescla PR nem habilita merge automático (Princípio VIII).
