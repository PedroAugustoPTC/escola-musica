---
name: pre-pr-review
description: Roteia a verificação antes do PR - lê o diff contra a main, decide quais agentes revisores acionar (em paralelo quando independentes), deixa o constitution-auditor por último e consolida um relatório único. Também encaminha falha de CI ao ci-failure-triage. Use ao terminar uma mudança, ao pedir "revise antes do PR" ou quando o CI falhar.
argument-hint: "[foco opcional, ex.: backend | só privacidade | CI]"
---

# Verificação antes do PR

Você coordena a revisão; os agentes só leem e relatam. **Você** corrige, depois de consolidar.
Chame apenas estes agentes: `backend-reviewer`, `frontend-reviewer`, `infra-security-auditor`,
`privacy-auditor`, `ai-pipeline-reviewer`, `contract-drift-checker`, `constitution-auditor`,
`ci-failure-triage`. Ignore `.claude/worktrees/` em qualquer busca.

## Passo 1 - entender o escopo

1. `git branch --show-current` e `git diff --name-only main...HEAD`. Se o argumento trouxer um foco, respeite-o.
2. Se o diff estiver vazio, avise e pare. Se houver alterações não commitadas, inclua-as (`git status`).

## Passo 2 - rotear

| Sinal no diff ou no pedido | Acionar |
|---|---|
| `backend/**` | `backend-reviewer` |
| `frontend/**` | `frontend-reviewer` |
| `infra/**`, `.github/**`, `infra/n8n/**` | `infra-security-auditor` |
| `specs/**/contracts/**`, controllers, `frontend/src/services/**`, mensagens do n8n | `contract-drift-checker` |
| aluno, professor, CPF, áudio, log, autenticação, envio a provedor de IA | `privacy-auditor` |
| prompts, portas ou adaptadores de IA, transcrição, geração de relatório | `ai-pipeline-reviewer` |
| qualquer mudança, **sempre por último** | `constitution-auditor` |
| só documentação ou comentário | apenas `constitution-auditor` |
| pedido menciona CI ou ensaio vermelho | `ci-failure-triage` primeiro; se o veredito for "real", roteie pela área afetada |

Não acione agente sem sinal. Anote, para o relatório, quais foram dispensados e por quê.

## Passo 3 - executar

1. **Revisores em paralelo**: chame todos os selecionados (menos o `constitution-auditor`) numa única mensagem.
2. **Briefing**: cada agente começa sem contexto. Passe a branch, o intervalo `main...HEAD`, os caminhos de spec
   relevantes e peça resposta de no máximo 40 linhas, por severidade, com `arquivo:linha`.
3. **Depois**: chame o `constitution-auditor` com o resumo dos demais.
4. **Verificação**: se a mudança tem código, sugira a skill `run-tests` para a suíte certa; não rode `real-ai`.

## Passo 4 - consolidar e corrigir

1. Junte os achados sem duplicar o que vários agentes disseram, ordene por severidade e mostre ao usuário.
2. Corrija os bloqueantes que forem claros. Reexecute **só** o agente que os apontou, no máximo uma vez;
   se persistir, pergunte ao usuário.
3. Guardião de idioma reprovou? Use a skill `fix-language-guard`.

## Relatório final

1. Veredito: pronto para PR / bloqueado.
2. Bloqueantes, ajustes e sugestões, cada um com `arquivo:linha` e o agente de origem.
3. Agentes acionados e dispensados (com o motivo).
4. O que não foi verificado (suíte não rodada, tela não aberta).
5. Se estiver pronto, peça ao usuário que chame `/open-pr`. Você não abre o PR nem mescla.

Não cole o relatório bruto dos agentes.
