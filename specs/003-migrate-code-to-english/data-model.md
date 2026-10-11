# Data Model: Mapa de renomeação de tabelas e colunas

**Feature**: `003-migrate-code-to-english` | **Data**: 2026-10-10

Esta feature **não cria nem altera entidades**: tipos, tamanhos, nulabilidade, chaves, unicidades,
`CHECK`s e índices continuam idênticos (FR-014). Este documento é apenas o **mapa de renomeação** que a
migração `V7__rename_to_english.sql` aplica. A fonte de cada coluna é `V1` a `V6` (em
`backend/aplicacao/src/main/resources/db/migration/`, imutáveis).

Fora deste documento (ver [research.md](./research.md), R-03): nomes de índices, constraints e sequência,
valores de enumeração persistidos e chaves dos documentos JSON. Estas ficam lá porque dependem da ordem de
execução da migração.

Convenções: colunas não listadas **não mudam de nome** (já são neutras ou iguais em inglês: `id`, `email`,
`status`, `payload`, `seq`, `chat_id`, `update_id`, `sha256`, `token_hash`, `cpf_hash`). Termos usados:
glossário da constituição + tabela complementar aprovada (spec, *Assumptions*), em
[contracts/glossary.json](./contracts/glossary.json).

## Tabelas

| Tabela atual | Tabela nova | Conceito |
|---|---|---|
| `professor` | `teacher` | Professor |
| `aluno` | `student` | Aluno |
| `professor_aluno` | `teacher_student_assignment` | Associação professor-aluno |
| `sessao_web` | `web_session` | Sessão do Portal |
| `vinculo_telegram` | `telegram_link` | Vínculo do Telegram (e estado da conversa) |
| `tentativa_acesso` | `access_attempt` | Tentativas de acesso |
| `update_telegram_processado` | `processed_telegram_update` | Deduplicação do webhook |
| `aula` | `lesson` | Aula |
| `relatorio_aula` | `lesson_report` | Relatório de aula |
| `interacao_relatorio` | `report_interaction` | Interações (respostas e alterações) |
| `versao_relatorio` | `report_version` | Versões imutáveis |
| `documento_pdf` | `pdf_document` | PDFs gerados |
| `trabalho_processamento` | `processing_job` | Fila de trabalhos |
| `chamada_ia` | `ai_call` | Custo das chamadas de IA |
| `relatorio_semestral` | `semester_report` | Relatório semestral |
| `acao_canal` | `channel_action` | Outbox de ações do canal |
| `documento_canal` | `channel_document` | Identificador opaco de PDF para o orquestrador |

## Colunas

### `teacher` (era `professor`)

| Atual | Nova |
|---|---|
| `nome` | `name` |
| `ativo` | `active` |
| `codigo_acesso_hash` | `access_code_hash` |
| `codigo_acesso_expira_em` | `access_code_expires_at` |
| `codigo_acesso_renovado_em` | `access_code_renewed_at` |
| `criado_em` | `created_at` |
| `atualizado_em` | `updated_at` |

### `student` (era `aluno`)

| Atual | Nova |
|---|---|
| `nome` | `name` |
| `data_nascimento` | `birth_date` |
| `cpf_cifrado` | `cpf_encrypted` |
| `nome_responsavel` | `guardian_name` |
| `instrumento` | `instrument` |
| `ativo` | `active` |
| `criado_em` / `atualizado_em` | `created_at` / `updated_at` |

### `teacher_student_assignment` (era `professor_aluno`)

| Atual | Nova |
|---|---|
| `professor_id` | `teacher_id` |
| `aluno_id` | `student_id` |
| `criado_em` | `created_at` |

### `web_session` (era `sessao_web`)

| Atual | Nova |
|---|---|
| `professor_id` | `teacher_id` |
| `criada_em` | `created_at` |
| `ultimo_uso_em` | `last_used_at` |
| `expira_em` | `expires_at` |

### `telegram_link` (era `vinculo_telegram`)

| Atual | Nova |
|---|---|
| `professor_id` | `teacher_id` |
| `telegram_usuario_id` | `telegram_user_id` |
| `vinculado_em` | `linked_at` |
| `aluno_selecionado_id` | `selected_student_id` |
| `fluxo` | `flow` |
| `etapa_fluxo` | `flow_step` |
| `dados_fluxo` | `flow_data` |
| `audio_pendente_file_id` | `pending_audio_file_id` |
| `audio_pendente_file_unique_id` | `pending_audio_file_unique_id` |
| `audio_pendente_mime` | `pending_audio_mime` |
| `audio_pendente_tamanho` | `pending_audio_size` |
| `audio_pendente_recebido_em` | `pending_audio_received_at` |
| `relatorio_foco_tipo` | `focused_report_type` |
| `relatorio_foco_id` | `focused_report_id` |
| `versao_apresentada` | `presented_version` |
| `atualizado_em` | `updated_at` |
| `bloqueio_otimista` | `lock_version` |

### `access_attempt` (era `tentativa_acesso`)

| Atual | Nova |
|---|---|
| `canal` | `channel` |
| `origem_hash` | `source_hash` |
| `tentativas_incorretas` | `failed_attempts` |
| `bloqueado_ate` | `blocked_until` |
| `atualizado_em` | `updated_at` |

### `processed_telegram_update` (era `update_telegram_processado`)

| Atual | Nova |
|---|---|
| `recebido_em` | `received_at` |
| `total_acoes` | `total_actions` |
| `origem` | `origin` |

### `lesson` (era `aula`)

| Atual | Nova |
|---|---|
| `professor_id` | `teacher_id` |
| `aluno_id` | `student_id` |
| `data_aula` | `lesson_date` |
| `criado_em` | `created_at` |

### `lesson_report` (era `relatorio_aula`)

| Atual | Nova |
|---|---|
| `aula_id` | `lesson_id` |
| `professor_id` | `teacher_id` |
| `aluno_id` | `student_id` |
| `transcricao` | `transcription` |
| `conteudo` | `content` |
| `perguntas_pendentes` | `pending_questions` |
| `rodadas_perguntas` | `question_rounds` |
| `etapa_processamento` | `processing_step` |
| `versao_vigente` | `current_version` |
| `canal_origem` | `origin_channel` |
| `motivo_falha` | `failure_reason` |
| `criado_em` / `atualizado_em` | `created_at` / `updated_at` |
| `aprovado_em` | `approved_at` |
| `versao_aprovada` | `approved_version` |
| `canal_aprovacao` | `approval_channel` |
| `cancelado_em` | `cancelled_at` |
| `tempo_processamento_ms` | `processing_time_ms` |
| `bloqueio_otimista` | `lock_version` |

### `report_interaction` (era `interacao_relatorio`)

| Atual | Nova |
|---|---|
| `relatorio_tipo` | `report_type` |
| `relatorio_id` | `report_id` |
| `tipo` | `type` |
| `canal` | `channel` |
| `meio` | `medium` |
| `texto` | `text` |
| `versao_base` | `base_version` |
| `rodada` | `round` |
| `resultado` | `result` |
| `versao_resultante` | `resulting_version` |
| `criado_em` | `created_at` |

### `report_version` (era `versao_relatorio`)

| Atual | Nova |
|---|---|
| `relatorio_tipo` | `report_type` |
| `relatorio_id` | `report_id` |
| `numero` | `number` |
| `conteudo` | `content` |
| `origem` | `origin` |
| `interacao_id` | `interaction_id` |
| `canal` | `channel` |
| `prompt_versao` | `prompt_version` |
| `criada_em` | `created_at` |

### `pdf_document` (era `documento_pdf`)

| Atual | Nova |
|---|---|
| `versao_id` | `version_id` |
| `tipo` | `type` |
| `chave_armazenamento` | `storage_key` |
| `tamanho_bytes` | `size_bytes` |
| `gerado_em` | `generated_at` |

### `processing_job` (era `trabalho_processamento`)

| Atual | Nova |
|---|---|
| `tipo` | `type` |
| `professor_id` | `teacher_id` |
| `aluno_id` | `student_id` |
| `relatorio_tipo` | `report_type` |
| `relatorio_id` | `report_id` |
| `canal_solicitacao` | `request_channel` |
| `meio` | `medium` |
| `parametros` | `parameters` |
| `arquivo_temporario` | `temporary_file` |
| `etapa` | `step` |
| `resultado_codigo` | `result_code` |
| `relatorio_resultante_id` | `resulting_report_id` |
| `tentativas` | `attempts` |
| `aviso_demora_enviado` | `delay_notice_sent` |
| `criado_em` | `created_at` |
| `iniciado_em` | `started_at` |
| `concluido_em` | `completed_at` |

### `ai_call` (era `chamada_ia`)

| Atual | Nova |
|---|---|
| `trabalho_id` | `job_id` |
| `relatorio_tipo` | `report_type` |
| `relatorio_id` | `report_id` |
| `tarefa` | `task` |
| `provedor_modelo` | `provider_model` |
| `segundos_audio` | `audio_seconds` |
| `tokens_entrada` | `input_tokens` |
| `tokens_saida` | `output_tokens` |
| `duracao_ms` | `duration_ms` |
| `custo_estimado_usd` | `estimated_cost_usd` |
| `sucesso` | `success` |
| `codigo_erro` | `error_code` |
| `criado_em` | `created_at` |

### `semester_report` (era `relatorio_semestral`)

| Atual | Nova |
|---|---|
| `professor_id` | `teacher_id` |
| `aluno_id` | `student_id` |
| `periodo_chave` | `period_key` |
| `periodo_inicio` / `periodo_fim` | `period_start` / `period_end` |
| `quantidade_aulas_consideradas` | `considered_lesson_count` |
| `quantidade_relatorios_em_aberto` | `open_report_count` |
| `informacoes_gerais` | `general_information` |
| `conteudo` | `content` |
| `etapa_processamento` | `processing_step` |
| `versao_vigente` | `current_version` |
| `canal_origem` | `origin_channel` |
| `criado_em` / `atualizado_em` | `created_at` / `updated_at` |
| `aprovado_em` | `approved_at` |
| `versao_aprovada` | `approved_version` |
| `canal_aprovacao` | `approval_channel` |
| `cancelado_em` | `cancelled_at` |
| `tempo_processamento_ms` | `processing_time_ms` |
| `bloqueio_otimista` | `lock_version` |

### `channel_action` (era `acao_canal`)

| Atual | Nova |
|---|---|
| `lote_id` | `batch_id` |
| `canal` | `channel` |
| `origem` | `origin` |
| `professor_id` | `teacher_id` |
| `trabalho_id` | `job_id` |
| `tipo` | `type` |
| `compensacao` | `compensation` |
| `estado` | `state` |
| `tentativas` | `attempts` |
| `proxima_tentativa_em` | `next_attempt_at` |
| `prazo_em` | `deadline_at` |
| `versao` | `version` |
| `criado_em` | `created_at` |
| `entregue_em` | `delivered_at` |
| `finalizado_em` | `finalized_at` |
| `codigo_motivo` | `reason_code` |

### `channel_document` (era `documento_canal`)

| Atual | Nova |
|---|---|
| `professor_id` | `teacher_id` |
| `relatorio_tipo` | `report_type` |
| `relatorio_id` | `report_id` |
| `versao` | `version` |
| `nome_arquivo` | `file_name` |
| `criado_em` | `created_at` |
| `expira_em` | `expires_at` |

## Verificação

- **Cobertura do mapa**: o teste de migração (R-03) falha se alguma tabela ou coluna do catálogo após a
  `V6` não aparecer neste mapa, ou se aparecer uma coluna fora dele após a `V7`.
- **Mesma estrutura**: o teste compara o catálogo após a `V6`, com o mapa aplicado, ao catálogo após a
  `V7` (tipos, nulabilidade, padrões, definição dos `CHECK`s e dos índices). Qualquer diferença além do
  nome reprova.
- **Sem português no esquema**: o teste guardião (R-05) varre os nomes do catálogo final.
