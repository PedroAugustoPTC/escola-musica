# Evidência de paridade — dispensa formal (FR-029)

Este arquivo é a trava do `ParityEvidenceGuardTest` (data-model §5). Ele registra uma **dispensa formal e
explícita** da prova de paridade, e **não** uma prova de paridade: os números da observação de 7 dias **não
foram registrados** e nenhum valor foi estimado ou inventado aqui.

## Registro

| Campo | Valor |
|---|---|
| `mode` | `waiver` |
| `waivedBy` | PedroAugustoPTC (mantenedor) |
| `waivedAt` | 2026-10-19 |
| `reason` | O mantenedor declarou no chat, em 2026-10-19, que o ensaio manual com bot real (T085) e a observação em produção (T086 a T088) estão validados, e decidiu não registrar a evidência numérica por escrito. |
| `switchedAt` | não registrado |
| `observationDays` | não registrado |
| `suiteRun` | não registrado |
| `updatesWithoutReply` | não registrado |
| `duplicateReplies` | não registrado |
| `proactiveDefinitiveFailuresFromTransport` | não registrado |
| `lostCallbackConfirmations` | não registrado |
| `teacherReports` | não registrado |

## O que a dispensa significa

- A condição de FR-029 (7 dias corridos com os quatro sinais em zero, nenhum relato de professor, suíte verde
  na `main` e aprovação) **não foi comprovada por escrito**; o mantenedor assume a declaração de que foi
  atendida.
- A remoção do caminho antigo passa a ser permitida pela dispensa, e não pela prova.
- Depois do merge da remoção, o `rollback` do webhook deixa de existir: reverter é reimplantar a versão
  anterior (spec, Edge Cases). O risco foi aceito pelo mantenedor.
- Se os números reais existirem, substitua os campos `não registrado` e troque `mode` para `evidence`; o
  `ParityEvidenceGuardTest` então passa a exigir a prova completa.
