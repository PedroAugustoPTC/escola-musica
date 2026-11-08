# US1 — Trilha de auditoria (T052)

Suítes em contêiner, banco novo (`infra/compose.test.yaml`):

| Suíte | Resultado |
|---|---|
| `tests backend` — `core` (unidade, ArchUnit, JaCoCo ≥ 90%) | verde |
| `tests backend` — `application` unidade (guardiões de idioma, compose e configuração órfã) | 382 testes, 0 falhas |
| `tests backend` — `application` integração (Postgres real) | 930 testes; 2 falhas de `LessonReportConcurrencyIT` (id de correlação de teste fora do formato) corrigidas e reexecutadas: 3/3 verdes |
| `tests frontend` | 50 arquivos, 446 testes, 0 falhas |
| `tests e2e` | 103 verdes; os 5 da trilha falharam por seletor (opção do filtro e rótulo "De" ambíguos), corrigidos; `admin-audit*` reexecutado: 6/6 verdes |

Provas pedidas: T031 `AuditTrailTamperIT`, T033 `AuditEventCoverageIT`, T034 `AuditReadsParityScriptIT` e T035
`AuditTrailCanaryIT` verdes.

Achados e decisões:

- **Evento duplicado no Telegram (corrigido).** O PDF era lido ao montar a resposta e de novo ao entregar o token, e cada
  leitura gravava `REPORT_DOCUMENT_SENT`. Agora a montagem usa `prepareOfficial`/`prepareReview` (sem evento) e só a entrega
  grava, uma vez (a tabela de `audit-events.md` exige exatamente um).
- **Paridade da leitura do PDF:** a rota do professor na Web só abre o PDF (`REPORT_DOCUMENT_OPEN`); o contrato menciona
  `REPORT_DOCUMENT_DOWNLOAD` na Web. O teste de paridade compara `OPEN` × `SENT`. Pendente de decisão: ajustar o contrato ou
  criar a rota de download do professor.
- **T033** executa os cenários da US1; as operações das US2–US5 ficam em lista de adiados, e o teste reprova operação da tabela
  sem cenário nem adiamento.
- **Limitação:** o relatório semestral oficial não grava `document_version` (o de aula grava).
- `LanguageGuardTest` varre literais: nos testes do Telegram usei "audit", "audit trail" e "/audit".
