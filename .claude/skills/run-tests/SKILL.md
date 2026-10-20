---
name: run-tests
description: Roda as suítes de teste do projeto em contêiner (backend, frontend, e2e, n8n, rehearsal) escolhendo a suíte pelos arquivos alterados e resumindo só o que falhou. Use ao validar uma mudança antes do PR.
argument-hint: "[backend|frontend|e2e|n8n|rehearsal|all]"
---

# Rodar testes

Todos os testes rodam em contêiner (constituição, "Containerização"): `sh infra/scripts/local.sh tests <suíte>`.

## Passos

1. Confirme que o Docker está de pé. Sem `infra/.env`, avise e pare (`cp infra/.env.example infra/.env`).
2. Sem argumento, escolha a suíte pelo `git diff --name-only main...HEAD`:
   - `backend/**` ou `specs/**/contracts/**` -> `backend`
   - `frontend/**` -> `frontend`, e `e2e` se mudou tela, rota ou acessibilidade
   - `infra/n8n/**`, `infra/telegram-simulator/**`, `infra/scripts/**` -> `n8n`; `rehearsal` se mexeu em
     webhook, rotação ou `infra/tests/rehearsal/`
   - `.specify/memory/glossary.json` ou renomeações -> `backend` e `frontend` (guardião de idioma)
   - Na dúvida ou antes do PR final: `all`.
3. Suítes longas rodam em segundo plano; acompanhe o fim sem ficar consultando.
4. Resuma: suíte, passou/falhou, e para cada falha o teste, a mensagem e o arquivo. Não despeje o log inteiro.
5. Se a cobertura do `core` ficou abaixo de 90%, destaque as classes que puxaram para baixo.

## Não faça

- Rodar `real-ai` sem pedido explícito: chama provedor de IA real e gera custo.
- Concluir "está tudo certo" se uma suíte foi pulada; diga quais não rodaram.
