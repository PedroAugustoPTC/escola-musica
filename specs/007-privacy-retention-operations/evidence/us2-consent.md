# US2 — Consentimento do responsável e bloqueio (T079)

Suítes em contêiner (`infra/compose.test.yaml`), banco novo:

| Suíte | Resultado |
|---|---|
| `tests backend` — `core` (unidade, ArchUnit, JaCoCo ≥ 90%) | verde; testes novos do consentimento, da barreira (`ConsentBlocksAiTest`) e do cadastro com consentimento |
| `tests backend` — `application` (unidade e integração, Postgres real) | ver a rodada final no PR; ITs novos: `AdminConsentContractIT`, `TelegramConsentFlowIT`, `ConsentLoadCommandIT`, `ConsentNoAiCallIT` |
| `tests frontend` | 51 arquivos, 454 testes, 0 falhas |
| `tests e2e` | 114 verdes (inclui `consent.spec.ts` e `admin-consent.a11y.spec.ts`) |

Decisões e achados:

- **Barreira em duas camadas.** `ConsentGuardService.requireForAi` recusa com `CONSENT_REQUIRED` na entrada dos seis casos de uso que
  alcançam a IA (áudio descartado, evento `CONSENT_BLOCKED_AI`); `requireForProcessing` (via `JobProcessorService`) encerra o
  trabalho enfileirado antes da revogação com o resultado `CONSENT_REQUIRED`, sem chamar provedor. O semestral usa o mesmo
  caminho pelo processador complementar. Regra ArchUnit: todo caso de uso que enfileira trabalho depende da barreira.
- **Cadastro e edição.** Menor sem `ConsentInput` é recusado com `VALIDATION_FAILED` em `consent` (`CONSENT_REQUIRED`); erros de
  campo em `consent.*`; evento `CONSENT_RECORD` na mesma transação. A carga inicial (`INITIAL_LOAD`) não exige: o consentimento
  vem depois, por `consent-load`. Na edição só exige quem **passa a ser** menor; o menor antigo sem consentimento aparece em
  "Pendentes" e fica bloqueado para IA.
- **Telegram.** Passos C1–C5 no rascunho (`student_registration_draft.consent`, jsonb da V17); termo publicado no meio
  reapresenta o C1; "não" descarta o rascunho; o resumo mostra só versão e data.
- **Contrato × código.** O texto de orientação não cita "secretaria" (teste do catálogo); fala em "administrador".

Pendências desta história (não feitas):

- Botão "Registrar consentimento" no aviso de bloqueio (Telegram e Web) e `Kind` de rascunho só para consentimento.
- Situação ("Registrado"/"Pendente") no detalhe do aluno do professor.
- `ConsentParityScriptIT` completo (roteiro Web × Telegram do bloqueio por revogação); hoje a paridade do cadastro é coberta por
  `StudentRegistryParityScriptIT` (ajustado) e a do bloqueio, pelos ITs de cada canal.
- Confirmação do termo informando quantos alunos ficarão pendentes (hoje a confirmação explica o efeito, sem o número).
