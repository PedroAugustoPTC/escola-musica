# US3 — Retenção automática (T094)

Suítes em contêiner (`infra/compose.test.yaml`), banco novo (a V17 ganhou `pdf_document.file_removed_at`):

| Suíte | Resultado |
|---|---|
| `tests backend` — `core` | 1726 testes, 0 falhas (inclui `RetentionDecisionTest`, `RunRetentionUseCaseTest`, regra ArchUnit do T082) |
| `tests backend` — `application` | 1352 testes; 1 erro na primeira rodada (`MigrationV8ReadableDataIT`, colunas da V17 ausentes no esquema parado na V8), corrigido e verde isolado; demais verdes |
| ITs da US3 | `RetentionIT` (4), `RetentionApprovalRaceIT` (1), `RetentionCanaryIT` (1), `DocumentStorageDeleteIT`, `RetentionTaskTest` — verdes |

Decisões e achados:

- Os blocos `retention`, `alerts`, `costs` e `privacy` do `application.yaml` estavam dentro do documento do perfil `seed`
  (nunca eram lidos); movidos para `reports:`.
- Prazos contados em dias de 24h a partir da aprovação ou do cancelamento (sem horário de verão no fuso da escola).
- Arquivo do PDF removido depois do commit que o marcou (`content_removed_at`); `file_removed_at` marca a remoção do arquivo.
  Remoção que falha deixa o item pendente e a execução seguinte repete.
- Aprovado vigente, seu PDF e `general_information` do semestral aprovado não mudam (hash do PDF, resposta `GET` e JSON iguais).
- Alertas normalizados e execuções antigas são expurgados com o mesmo prazo da trilha.

Pendências: contagem por tipo numa massa de demonstração (rodar `demo-data` e a retenção); hash da cabeça da cadeia no log da
`RetentionTask`; falha de `delete` com nova execução só coberta no teste unitário.
