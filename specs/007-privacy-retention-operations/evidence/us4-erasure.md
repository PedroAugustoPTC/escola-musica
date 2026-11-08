# Evidência da US4 — eliminação a pedido

Data: 2026-11-05. Sem dado pessoal.

| Verificação | Resultado |
|---|---|
| Núcleo: `ErasureRequestTest`, casos de uso de eliminação, `ErasedStudentVisibilityTest`, `JobProcessorErasureTest` | verdes |
| Contrato `AdminErasureContractIT` (registro, lista, detalhe, prévia, confirmação, comprovante; 201/400/404/409; `no-store`) | 12 testes verdes |
| `ErasureIT` (SC-004): varredura de todas as colunas de texto e `jsonb` do esquema, logs, trilha e `ai_call`; aluno some das consultas; CPF liberado | verde |
| `ErasureFailureIT`: falha na remoção de arquivo deixa `IN_PROGRESS`; nova prévia e confirmação concluem | verde |
| `AdminRequestsNeutralDenialIT`: "apagar aluno", "eliminar", "excluir dados" no Telegram → negativa neutra | verde |
| Backend completo (core + application: unidade e ITs, 417 arquivos de relatório, 3162 testes, nenhum com falha ou erro) | verde |
| Frontend (Vitest) | 464 testes, 53 arquivos, verdes |
| E2E (Playwright, inclui `admin-erasure.spec.ts` e `admin-erasure.a11y.spec.ts`) | 117 testes, verdes |

## Lacunas conhecidas

- Falha forçada **no meio da transação do banco** (parte de T101) não tem teste de integração; a atomicidade
  vem da transação do caso de uso e está coberta só em unidade.
- A lista de pedidos (`/admin/erasure-requests`) não tem atalho na navegação.
