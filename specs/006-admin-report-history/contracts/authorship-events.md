# Contrato: registros de autoria das leituras do administrador

Estende o registro de autoria da 005 (FR-040, tabela `authorship_record`, somente acréscimo) para a trilha da feature
007. Detalhe do esquema em [data-model.md](../data-model.md) §4.

## Campos comuns (iguais à 005)

`id`, `occurred_at`, `author_id` (administrador), `author_role = ADMINISTRATOR`, `origin = WEB`, `operation`,
`target_type`, `target_id`, `result`, `error_code`, `correlation_id` (id da requisição). `origin_hash` fica nulo
(só autenticações o levam).

## Campos novos (V16), só nas três operações desta feature

`filter_teacher_id`, `filter_report_type`, `filter_period_start`, `filter_period_end`, `page_number`,
`document_version`. Tipados (uuid, enumeração, data, inteiro); nenhum texto livre.

## Eventos

| # | Quando | `operation` | Alvo | Detalhe | `result` | `error_code` | Gravação |
|---|---|---|---|---|---|---|---|
| E1 | Página da lista entregue (abrir, filtrar, trocar de página, recarregar) | `SCHOOL_HISTORY_QUERY` | `STUDENT` + `studentId`, ou nulo | `filter_*`, `page_number` | `SUCCESS` | — | mesma transação; falhou → 500 e nada entregue |
| E2 | Filtro recusado na validação | `SCHOOL_HISTORY_QUERY` | idem, se `studentId` válido | os filtros válidos | `FAILURE` | `VALIDATION_FAILED` | independente |
| E3 | PDF aberto | `SCHOOL_REPORT_DOCUMENT_OPEN` | `LESSON_REPORT`/`SEMESTER_REPORT` + `reportId` | `document_version` | `SUCCESS` | — | mesma transação; falhou → 500 e nada entregue |
| E4 | PDF baixado | `SCHOOL_REPORT_DOCUMENT_DOWNLOAD` | idem | `document_version` | `SUCCESS` | — | idem |
| E5 | PDF de relatório inexistente ou não aprovado | `_OPEN` ou `_DOWNLOAD` | idem, com o id pedido | — | `FAILURE` | `RESOURCE_NOT_FOUND` | independente |
| E6 | PDF aprovado indisponível no armazenamento | `_OPEN` ou `_DOWNLOAD` | idem | — | `FAILURE` | `INTERNAL_ERROR` | independente |
| E7 | Credencial de professor nas rotas | `AUTHORIZATION_DENIED` (005) | — | — | `DENIED` | — | independente (já existe) |

Não geram registro: expiração de sessão (regra da 005), busca de aluno pelo nome (`STUDENT_LIST` da 005, leituras sem
registro até a 007), carregamento da lista de professores do filtro (`TEACHER_LIST` da 005).

## Proibido em qualquer evento, log ou mensagem (FR-022, FR-024, SC-005)

Nome do aluno ou do professor, CPF, data de nascimento, dados do responsável, transcrição, conteúdo de relatório, texto
de busca digitado. Verificação: teste de privacidade com valores canário (nome, CPF e trecho de conteúdo conhecidos)
que varre `authorship_record`, os logs capturados e os corpos de erro de todos os testes da feature.
