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


Fechamento da US2 (pendências resolvidas):

- **Botão "Registrar consentimento" no aviso de bloqueio.** Web: o `ErrorAlert` mostra o atalho para `/my-students/{id}` quando o erro é
  `CONSENT_REQUIRED` (gravação de aula, perguntas, conferência e semestral). Telegram: o botão `consent-record` abre um rascunho do tipo
  `CONSENT` (só os passos C1–C5, em `student_registration_draft`; a V17 amplia o CHECK do tipo) e a confirmação chama
  `RecordConsentUseCase`. O `CONSENT_REQUIRED` agora leva o `studentId` nos detalhes.
- **Situação do consentimento no detalhe do aluno do professor.** `GET /my-students/{id}/consent-status` (contrato 007) e a seção
  "Consentimento do responsável" em `MyStudentDetailScreen` (Registrado / Pendente, com o painel de registro).
- **Contagem na confirmação do termo.** `GET /admin/consent-terms/impact` informa quantos menores com consentimento válido ficam
  pendentes; a tela A16d mostra o número antes de confirmar.
- **`ConsentParityScriptIT`** (Web ≡ Telegram): menor sem consentimento recusado, com consentimento aceito, adulto sem bloqueio, bloqueio
  com o mesmo código e o mesmo evento, registro pelo botão, nova liberação e bloqueio depois da revogação.
- **`AuditEventCoverageIT`:** `CONSENT_RECORD/REVOKE/TERM_PUBLISH/BLOCKED_AI` (e `RETENTION_RUN/AUDIT_TRAIL_PURGED`, da US3) saíram da lista
  de adiadas e têm cenário real.
- **Contrato de `/my-students`:** `MyStudentConsentContractIT` valida o termo vigente, a situação e o registro avulso contra o contrato 007.

Limite conhecido: a tela de processamento (resultado `CONSENT_REQUIRED` de trabalho já enfileirado) segue só com o texto, sem o atalho.
