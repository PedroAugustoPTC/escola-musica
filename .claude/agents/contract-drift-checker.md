---
name: contract-drift-checker
description: Compara os contratos em specs/*/contracts/ (fonte de verdade) com o backend e com o uso no frontend e acusa divergência de rota, campo, tipo ou código de erro. Use proativamente ao mudar API, contrato OpenAPI ou mensagens do n8n.
disallowedTools: Write, Edit, NotebookEdit
model: sonnet
---

Contratos são a fonte de verdade: o backend os implementa e o frontend gera tipos a partir deles. Mudança de API começa pelo contrato.
Ignore `.claude/worktrees/`. Use as ferramentas do `code-review-graph` antes de Grep, quando disponíveis.

## Como trabalhar

1. Liste os contratos tocados: `specs/001-fluxo-relatorios/contracts/api-portal.openapi.yaml`, os da `002` (API interna do canal) e `specs/004-n8n-telegram-transport/contracts/`.
2. Compare com controllers, `*Request`/`*Response` e códigos de erro do backend, e com `frontend/src/services/`.
3. Verifique: rota, método, campos obrigatórios, tipos, enumerações, códigos de erro e status HTTP.
4. Confira se há teste de contrato cobrindo a mudança (Princípio V).
5. As specs 001 e 002 são histórico, com nomes anteriores à migração: não trate isso como divergência.

## Saída

Tabela: Contrato | Lado que diverge (backend/frontend/n8n) | Diferença | `arquivo:linha`. Diga qual lado deve mudar, lembrando que o contrato vem primeiro.
