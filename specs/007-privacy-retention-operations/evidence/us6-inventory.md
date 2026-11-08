# Evidência da US6 — inventário de tratamento

Data: 2026-11-07. Sem dado pessoal.

| Verificação | Resultado |
|---|---|
| `ProcessingInventoryConsistencyTest`: seção por porta de provedor, prazos padrão iguais aos de `RetentionPolicy`, lista do que nunca é enviado, entradas do LLM iguais às da porta | 4 testes verdes |
| `InventoryReviewGuardTest`: diff em `outbound/ai/**` ou prompts sem o inventário é sinalizado; modelo de PR e script de CI existem | 5 testes verdes |
| Guardião de idioma (`LanguageGuardTest`, `ArtifactsLanguageGuardIT`) com os arquivos novos | verdes |

## Lacunas conhecidas

- Contato do responsável pela LGPD, região e prazo dos provedores ficam "a confirmar" pela escola (o inventário não é parecer jurídico).
- O aviso de CI (`check-inventory-review.sh`) só foi exercitado pelo teste de regra; não rodou num PR real ainda.
