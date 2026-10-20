---
name: open-pr
description: Conclui o trabalho da branch atual seguindo o Princípio VIII - confere a branch, commita em português, faz push, abre o Pull Request para a main (sem mesclar) e atualiza o grafo. Use ao terminar uma feature, spec, correção ou emenda.
disable-model-invocation: true
---

# Abrir Pull Request

Fluxo do Princípio VIII da constituição. Nunca mescle e nunca habilite merge automático.

## Passos

1. `git status` e `git branch --show-current`.
   - Se estiver na `main`: **pare** e pergunte o nome da branch. Antes de criá-la, rode
     `git checkout main && git pull --ff-only origin main`. Se houver alterações não commitadas,
     avise o usuário em vez de levá-las para a branch nova sem perguntar.
2. Mostre o resumo das alterações (`git diff --stat`) e confirme que nada sensível entrou:
   `infra/.env`, `infra/seed/*.json` reais, chaves, tokens.
3. Commit em **português**, uma linha de assunto no estilo do histórico (`test:`, `ci:`, `fix:`, `docs:`),
   com o trailer de coautoria indicado pelo ambiente. Não use `--no-verify`.
4. `git push -u origin <branch>`.
5. Abra o PR com `gh pr create --base main`. Título e descrição em **português**, com: o que mudou,
   por que mudou, como foi testado e, se tocar a constituição ou o contrato, o impacto. Termine a
   descrição com a linha de atribuição indicada pelo ambiente.
6. Rode `code-review-graph build` (regra da raiz: depois de **cada** PR).
7. Devolva o link do PR e lembre que o merge é do usuário.

## Não faça

- `gh pr merge`, `--auto` ou qualquer habilitação de merge automático.
- Commit direto na `main` ou na branch de outra feature.
- Abrir PR com testes falhando sem avisar o usuário; se o CI local não foi rodado, diga isso na descrição.
