# privacy/

Documentação de privacidade mantida junto do código (feature 007, LGPD).

| Arquivo | O que é |
|---|---|
| `processing-inventory.md` | Inventário de tratamento: por provedor (transcrição, LLM, mensageria, armazenamento), o dado enviado, a finalidade, a base legal proposta, local, prazo, uso para treinamento e os prazos de retenção do sistema |

Regras:

- **FR-071**: mudou o que vai a um provedor (adaptadores em `outbound/ai/**`, prompts, portas de IA ou de mensageria)? Revise
  `processing-inventory.md` no mesmo PR. O modelo de PR traz o item e o CI avisa (`infra/scripts/check-inventory-review.sh`, modo aviso).
- `ProcessingInventoryConsistencyTest` lê os marcadores `<!-- ... -->` (em inglês) do inventário e os compara ao código: portas,
  prazos padrão de `RetentionPolicy`, entradas aceitas pela porta do LLM e a lista do que nunca é enviado. Mexa nos marcadores e no
  código juntos.
- O inventário **não é parecer jurídico**; bases legais são propostas a validar.
- Sem dado pessoal real aqui, nunca.
