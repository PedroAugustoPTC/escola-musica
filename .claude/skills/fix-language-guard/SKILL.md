---
name: fix-language-guard
description: Resolve falhas do guardião de idioma (Princípio VI) - lista todas as violações de uma vez, separa renomeação de termo novo no glossário e de exceção documentada, aplica a correção e reexecuta. Use quando LanguageGuardTest, ArtifactsLanguageGuardIT ou language-guard.test.ts reprovarem.
---

# Corrigir o guardião de idioma

Leia `.specify/CLAUDE.md` antes de começar. O guardião varre o repositório inteiro e **não aceita lista de exclusão**.

## Passos

1. Gere o relatório completo, sem reprovar:
   - Backend: `docker compose -f infra/compose.test.yaml run --rm -e GUARD_REPORT=1 tests-backend`
   - Frontend: `docker compose -f infra/compose.test.yaml run --rm -e GUARD_REPORT=1 tests-frontend`
2. Leia `backend/application/target/guard-violations-{java,artifacts}.json` e
   `frontend/test-results/guard-violations.json`.
3. Classifique cada violação:
   - **Palavra em português num identificador, arquivo ou pasta**: renomeie para inglês usando o glossário
     (`.specify/memory/glossary.json` e o Princípio VI).
   - **Termo do domínio ainda sem tradução**: acrescente em `words` do `glossary.json` **antes** de usar,
     com um único equivalente em inglês (palavra minúscula, sem acento, singular).
   - **Nome de terceiro, nome próprio ou texto do usuário**: crie uma exceção documentada com `reason`,
     `constitutionCategory`, `mode` e `appliesTo`. Exceção sem casamento é "morta" e reprova.
4. Mostre ao usuário a lista de renomeações e de entradas novas do glossário e **espere confirmação**
   antes de aplicar. Renomear em Java exige rodar o Spotless (`backend/CLAUDE.md`).
5. Reexecute **sem** `GUARD_REPORT` e confirme que passa.

## Não faça

- Criar `glossary-temporary-exclusions.json` (o CI reprova o PR).
- Traduzir texto mostrado ao usuário, log ou palavra que o professor digita: ficam em português.
- Editar migrações Flyway V1 a V8.
