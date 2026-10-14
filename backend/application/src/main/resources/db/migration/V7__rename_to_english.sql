-- V7 (feature 003): renomeia tabelas, colunas, sequência, restrições, índices e chaves JSON do esquema para inglês.
-- Só nomes: tipos, tamanhos, nulabilidade, padrões, restrições e índices continuam idênticos (FR-014). Os valores de
-- enumeração (status, tipos, etapas…) continuam em português até a V8 (são também o fio). Nenhuma migração anterior
-- é editada (FR-013). Gerada por specs/003-migrate-code-to-english/tools/gen-v7.mjs a partir de db-catalog-v6.json.

-- 1. Tabelas (colunas, chaves, índices e dados acompanham pelo OID)
ALTER TABLE acao_canal RENAME TO channel_action;
ALTER TABLE aluno RENAME TO student;
ALTER TABLE aula RENAME TO lesson;
ALTER TABLE chamada_ia RENAME TO ai_call;
ALTER TABLE documento_canal RENAME TO channel_document;
ALTER TABLE documento_pdf RENAME TO pdf_document;
ALTER TABLE interacao_relatorio RENAME TO report_interaction;
ALTER TABLE professor RENAME TO teacher;
ALTER TABLE professor_aluno RENAME TO teacher_student_assignment;
ALTER TABLE relatorio_aula RENAME TO lesson_report;
ALTER TABLE relatorio_semestral RENAME TO semester_report;
ALTER TABLE sessao_web RENAME TO web_session;
ALTER TABLE tentativa_acesso RENAME TO access_attempt;
ALTER TABLE trabalho_processamento RENAME TO processing_job;
ALTER TABLE update_telegram_processado RENAME TO processed_telegram_update;
ALTER TABLE versao_relatorio RENAME TO report_version;
ALTER TABLE vinculo_telegram RENAME TO telegram_link;

-- 2. Colunas
ALTER TABLE channel_action RENAME COLUMN lote_id TO batch_id;
ALTER TABLE channel_action RENAME COLUMN canal TO channel;
ALTER TABLE channel_action RENAME COLUMN origem TO origin;
ALTER TABLE channel_action RENAME COLUMN professor_id TO teacher_id;
ALTER TABLE channel_action RENAME COLUMN trabalho_id TO job_id;
ALTER TABLE channel_action RENAME COLUMN tipo TO type;
ALTER TABLE channel_action RENAME COLUMN compensacao TO compensation;
ALTER TABLE channel_action RENAME COLUMN estado TO state;
ALTER TABLE channel_action RENAME COLUMN tentativas TO attempts;
ALTER TABLE channel_action RENAME COLUMN proxima_tentativa_em TO next_attempt_at;
ALTER TABLE channel_action RENAME COLUMN prazo_em TO deadline_at;
ALTER TABLE channel_action RENAME COLUMN versao TO version;
ALTER TABLE channel_action RENAME COLUMN criado_em TO created_at;
ALTER TABLE channel_action RENAME COLUMN entregue_em TO delivered_at;
ALTER TABLE channel_action RENAME COLUMN finalizado_em TO finalized_at;
ALTER TABLE channel_action RENAME COLUMN codigo_motivo TO reason_code;
ALTER TABLE student RENAME COLUMN nome TO name;
ALTER TABLE student RENAME COLUMN data_nascimento TO birth_date;
ALTER TABLE student RENAME COLUMN cpf_cifrado TO cpf_encrypted;
ALTER TABLE student RENAME COLUMN nome_responsavel TO guardian_name;
ALTER TABLE student RENAME COLUMN instrumento TO instrument;
ALTER TABLE student RENAME COLUMN ativo TO active;
ALTER TABLE student RENAME COLUMN criado_em TO created_at;
ALTER TABLE student RENAME COLUMN atualizado_em TO updated_at;
ALTER TABLE lesson RENAME COLUMN professor_id TO teacher_id;
ALTER TABLE lesson RENAME COLUMN aluno_id TO student_id;
ALTER TABLE lesson RENAME COLUMN data_aula TO lesson_date;
ALTER TABLE lesson RENAME COLUMN criado_em TO created_at;
ALTER TABLE ai_call RENAME COLUMN trabalho_id TO job_id;
ALTER TABLE ai_call RENAME COLUMN relatorio_tipo TO report_type;
ALTER TABLE ai_call RENAME COLUMN relatorio_id TO report_id;
ALTER TABLE ai_call RENAME COLUMN tarefa TO task;
ALTER TABLE ai_call RENAME COLUMN provedor_modelo TO provider_model;
ALTER TABLE ai_call RENAME COLUMN segundos_audio TO audio_seconds;
ALTER TABLE ai_call RENAME COLUMN tokens_entrada TO input_tokens;
ALTER TABLE ai_call RENAME COLUMN tokens_saida TO output_tokens;
ALTER TABLE ai_call RENAME COLUMN duracao_ms TO duration_ms;
ALTER TABLE ai_call RENAME COLUMN custo_estimado_usd TO estimated_cost_usd;
ALTER TABLE ai_call RENAME COLUMN sucesso TO success;
ALTER TABLE ai_call RENAME COLUMN codigo_erro TO error_code;
ALTER TABLE ai_call RENAME COLUMN criado_em TO created_at;
ALTER TABLE channel_document RENAME COLUMN professor_id TO teacher_id;
ALTER TABLE channel_document RENAME COLUMN relatorio_tipo TO report_type;
ALTER TABLE channel_document RENAME COLUMN relatorio_id TO report_id;
ALTER TABLE channel_document RENAME COLUMN versao TO version;
ALTER TABLE channel_document RENAME COLUMN nome_arquivo TO file_name;
ALTER TABLE channel_document RENAME COLUMN criado_em TO created_at;
ALTER TABLE channel_document RENAME COLUMN expira_em TO expires_at;
ALTER TABLE pdf_document RENAME COLUMN versao_id TO version_id;
ALTER TABLE pdf_document RENAME COLUMN tipo TO type;
ALTER TABLE pdf_document RENAME COLUMN chave_armazenamento TO storage_key;
ALTER TABLE pdf_document RENAME COLUMN tamanho_bytes TO size_bytes;
ALTER TABLE pdf_document RENAME COLUMN gerado_em TO generated_at;
ALTER TABLE report_interaction RENAME COLUMN relatorio_tipo TO report_type;
ALTER TABLE report_interaction RENAME COLUMN relatorio_id TO report_id;
ALTER TABLE report_interaction RENAME COLUMN tipo TO type;
ALTER TABLE report_interaction RENAME COLUMN canal TO channel;
ALTER TABLE report_interaction RENAME COLUMN meio TO medium;
ALTER TABLE report_interaction RENAME COLUMN texto TO text;
ALTER TABLE report_interaction RENAME COLUMN versao_base TO base_version;
ALTER TABLE report_interaction RENAME COLUMN rodada TO round;
ALTER TABLE report_interaction RENAME COLUMN resultado TO result;
ALTER TABLE report_interaction RENAME COLUMN versao_resultante TO resulting_version;
ALTER TABLE report_interaction RENAME COLUMN criado_em TO created_at;
ALTER TABLE teacher RENAME COLUMN nome TO name;
ALTER TABLE teacher RENAME COLUMN ativo TO active;
ALTER TABLE teacher RENAME COLUMN codigo_acesso_hash TO access_code_hash;
ALTER TABLE teacher RENAME COLUMN codigo_acesso_expira_em TO access_code_expires_at;
ALTER TABLE teacher RENAME COLUMN codigo_acesso_renovado_em TO access_code_renewed_at;
ALTER TABLE teacher RENAME COLUMN criado_em TO created_at;
ALTER TABLE teacher RENAME COLUMN atualizado_em TO updated_at;
ALTER TABLE teacher_student_assignment RENAME COLUMN professor_id TO teacher_id;
ALTER TABLE teacher_student_assignment RENAME COLUMN aluno_id TO student_id;
ALTER TABLE teacher_student_assignment RENAME COLUMN criado_em TO created_at;
ALTER TABLE lesson_report RENAME COLUMN aula_id TO lesson_id;
ALTER TABLE lesson_report RENAME COLUMN professor_id TO teacher_id;
ALTER TABLE lesson_report RENAME COLUMN aluno_id TO student_id;
ALTER TABLE lesson_report RENAME COLUMN transcricao TO transcription;
ALTER TABLE lesson_report RENAME COLUMN conteudo TO content;
ALTER TABLE lesson_report RENAME COLUMN perguntas_pendentes TO pending_questions;
ALTER TABLE lesson_report RENAME COLUMN rodadas_perguntas TO question_rounds;
ALTER TABLE lesson_report RENAME COLUMN etapa_processamento TO processing_step;
ALTER TABLE lesson_report RENAME COLUMN versao_vigente TO current_version;
ALTER TABLE lesson_report RENAME COLUMN canal_origem TO origin_channel;
ALTER TABLE lesson_report RENAME COLUMN motivo_falha TO failure_reason;
ALTER TABLE lesson_report RENAME COLUMN criado_em TO created_at;
ALTER TABLE lesson_report RENAME COLUMN atualizado_em TO updated_at;
ALTER TABLE lesson_report RENAME COLUMN aprovado_em TO approved_at;
ALTER TABLE lesson_report RENAME COLUMN versao_aprovada TO approved_version;
ALTER TABLE lesson_report RENAME COLUMN canal_aprovacao TO approval_channel;
ALTER TABLE lesson_report RENAME COLUMN cancelado_em TO cancelled_at;
ALTER TABLE lesson_report RENAME COLUMN tempo_processamento_ms TO processing_time_ms;
ALTER TABLE lesson_report RENAME COLUMN bloqueio_otimista TO lock_version;
ALTER TABLE semester_report RENAME COLUMN professor_id TO teacher_id;
ALTER TABLE semester_report RENAME COLUMN aluno_id TO student_id;
ALTER TABLE semester_report RENAME COLUMN periodo_chave TO period_key;
ALTER TABLE semester_report RENAME COLUMN periodo_inicio TO period_start;
ALTER TABLE semester_report RENAME COLUMN periodo_fim TO period_end;
ALTER TABLE semester_report RENAME COLUMN quantidade_aulas_consideradas TO considered_lesson_count;
ALTER TABLE semester_report RENAME COLUMN quantidade_relatorios_em_aberto TO open_report_count;
ALTER TABLE semester_report RENAME COLUMN informacoes_gerais TO general_information;
ALTER TABLE semester_report RENAME COLUMN conteudo TO content;
ALTER TABLE semester_report RENAME COLUMN etapa_processamento TO processing_step;
ALTER TABLE semester_report RENAME COLUMN versao_vigente TO current_version;
ALTER TABLE semester_report RENAME COLUMN canal_origem TO origin_channel;
ALTER TABLE semester_report RENAME COLUMN criado_em TO created_at;
ALTER TABLE semester_report RENAME COLUMN atualizado_em TO updated_at;
ALTER TABLE semester_report RENAME COLUMN aprovado_em TO approved_at;
ALTER TABLE semester_report RENAME COLUMN versao_aprovada TO approved_version;
ALTER TABLE semester_report RENAME COLUMN canal_aprovacao TO approval_channel;
ALTER TABLE semester_report RENAME COLUMN cancelado_em TO cancelled_at;
ALTER TABLE semester_report RENAME COLUMN tempo_processamento_ms TO processing_time_ms;
ALTER TABLE semester_report RENAME COLUMN bloqueio_otimista TO lock_version;
ALTER TABLE web_session RENAME COLUMN professor_id TO teacher_id;
ALTER TABLE web_session RENAME COLUMN criada_em TO created_at;
ALTER TABLE web_session RENAME COLUMN ultimo_uso_em TO last_used_at;
ALTER TABLE web_session RENAME COLUMN expira_em TO expires_at;
ALTER TABLE access_attempt RENAME COLUMN canal TO channel;
ALTER TABLE access_attempt RENAME COLUMN origem_hash TO source_hash;
ALTER TABLE access_attempt RENAME COLUMN tentativas_incorretas TO failed_attempts;
ALTER TABLE access_attempt RENAME COLUMN bloqueado_ate TO blocked_until;
ALTER TABLE access_attempt RENAME COLUMN atualizado_em TO updated_at;
ALTER TABLE processing_job RENAME COLUMN tipo TO type;
ALTER TABLE processing_job RENAME COLUMN professor_id TO teacher_id;
ALTER TABLE processing_job RENAME COLUMN aluno_id TO student_id;
ALTER TABLE processing_job RENAME COLUMN relatorio_tipo TO report_type;
ALTER TABLE processing_job RENAME COLUMN relatorio_id TO report_id;
ALTER TABLE processing_job RENAME COLUMN canal_solicitacao TO request_channel;
ALTER TABLE processing_job RENAME COLUMN meio TO medium;
ALTER TABLE processing_job RENAME COLUMN parametros TO parameters;
ALTER TABLE processing_job RENAME COLUMN arquivo_temporario TO temporary_file;
ALTER TABLE processing_job RENAME COLUMN etapa TO step;
ALTER TABLE processing_job RENAME COLUMN resultado_codigo TO result_code;
ALTER TABLE processing_job RENAME COLUMN relatorio_resultante_id TO resulting_report_id;
ALTER TABLE processing_job RENAME COLUMN tentativas TO attempts;
ALTER TABLE processing_job RENAME COLUMN aviso_demora_enviado TO delay_notice_sent;
ALTER TABLE processing_job RENAME COLUMN criado_em TO created_at;
ALTER TABLE processing_job RENAME COLUMN iniciado_em TO started_at;
ALTER TABLE processing_job RENAME COLUMN concluido_em TO completed_at;
ALTER TABLE processed_telegram_update RENAME COLUMN recebido_em TO received_at;
ALTER TABLE processed_telegram_update RENAME COLUMN total_acoes TO total_actions;
ALTER TABLE processed_telegram_update RENAME COLUMN origem TO origin;
ALTER TABLE report_version RENAME COLUMN relatorio_tipo TO report_type;
ALTER TABLE report_version RENAME COLUMN relatorio_id TO report_id;
ALTER TABLE report_version RENAME COLUMN numero TO number;
ALTER TABLE report_version RENAME COLUMN conteudo TO content;
ALTER TABLE report_version RENAME COLUMN origem TO origin;
ALTER TABLE report_version RENAME COLUMN interacao_id TO interaction_id;
ALTER TABLE report_version RENAME COLUMN canal TO channel;
ALTER TABLE report_version RENAME COLUMN prompt_versao TO prompt_version;
ALTER TABLE report_version RENAME COLUMN criada_em TO created_at;
ALTER TABLE telegram_link RENAME COLUMN professor_id TO teacher_id;
ALTER TABLE telegram_link RENAME COLUMN telegram_usuario_id TO telegram_user_id;
ALTER TABLE telegram_link RENAME COLUMN vinculado_em TO linked_at;
ALTER TABLE telegram_link RENAME COLUMN aluno_selecionado_id TO selected_student_id;
ALTER TABLE telegram_link RENAME COLUMN fluxo TO flow;
ALTER TABLE telegram_link RENAME COLUMN etapa_fluxo TO flow_step;
ALTER TABLE telegram_link RENAME COLUMN dados_fluxo TO flow_data;
ALTER TABLE telegram_link RENAME COLUMN audio_pendente_file_id TO pending_audio_file_id;
ALTER TABLE telegram_link RENAME COLUMN audio_pendente_file_unique_id TO pending_audio_file_unique_id;
ALTER TABLE telegram_link RENAME COLUMN audio_pendente_mime TO pending_audio_mime;
ALTER TABLE telegram_link RENAME COLUMN audio_pendente_tamanho TO pending_audio_size;
ALTER TABLE telegram_link RENAME COLUMN audio_pendente_recebido_em TO pending_audio_received_at;
ALTER TABLE telegram_link RENAME COLUMN relatorio_foco_tipo TO focused_report_type;
ALTER TABLE telegram_link RENAME COLUMN relatorio_foco_id TO focused_report_id;
ALTER TABLE telegram_link RENAME COLUMN versao_apresentada TO presented_version;
ALTER TABLE telegram_link RENAME COLUMN atualizado_em TO updated_at;
ALTER TABLE telegram_link RENAME COLUMN bloqueio_otimista TO lock_version;

-- 3. Sequência da identidade de channel_action.seq
ALTER SEQUENCE acao_canal_seq_seq RENAME TO channel_action_seq_seq;

-- 4. Restrições (NOT NULL, chave primária, chave estrangeira, unicidade, CHECK); o índice que sustenta a restrição acompanha
ALTER TABLE channel_action RENAME CONSTRAINT acao_canal_canal_not_null TO channel_action_channel_not_null;
ALTER TABLE channel_action RENAME CONSTRAINT acao_canal_chat_id_not_null TO channel_action_chat_id_not_null;
ALTER TABLE channel_action RENAME CONSTRAINT acao_canal_criado_em_not_null TO channel_action_created_at_not_null;
ALTER TABLE channel_action RENAME CONSTRAINT acao_canal_estado_not_null TO channel_action_state_not_null;
ALTER TABLE channel_action RENAME CONSTRAINT acao_canal_id_not_null TO channel_action_id_not_null;
ALTER TABLE channel_action RENAME CONSTRAINT acao_canal_lote_id_not_null TO channel_action_batch_id_not_null;
ALTER TABLE channel_action RENAME CONSTRAINT acao_canal_origem_not_null TO channel_action_origin_not_null;
ALTER TABLE channel_action RENAME CONSTRAINT acao_canal_pkey TO channel_action_pkey;
ALTER TABLE channel_action RENAME CONSTRAINT acao_canal_prazo_em_not_null TO channel_action_deadline_at_not_null;
ALTER TABLE channel_action RENAME CONSTRAINT acao_canal_seq_not_null TO channel_action_seq_not_null;
ALTER TABLE channel_action RENAME CONSTRAINT acao_canal_tentativas_not_null TO channel_action_attempts_not_null;
ALTER TABLE channel_action RENAME CONSTRAINT acao_canal_tipo_not_null TO channel_action_type_not_null;
ALTER TABLE channel_action RENAME CONSTRAINT acao_canal_versao_not_null TO channel_action_version_not_null;
ALTER TABLE channel_action RENAME CONSTRAINT ck_acao_canal_estado TO ck_channel_action_state;
ALTER TABLE channel_action RENAME CONSTRAINT ck_acao_canal_origem TO ck_channel_action_origin;
ALTER TABLE channel_action RENAME CONSTRAINT ck_acao_canal_resposta_update TO ck_channel_action_update_reply;
ALTER TABLE channel_action RENAME CONSTRAINT ck_acao_canal_tentativas TO ck_channel_action_attempts;
ALTER TABLE channel_action RENAME CONSTRAINT ck_acao_canal_tipo TO ck_channel_action_type;
ALTER TABLE channel_action RENAME CONSTRAINT uk_acao_canal_seq TO uk_channel_action_seq;
ALTER TABLE student RENAME CONSTRAINT aluno_ativo_not_null TO student_active_not_null;
ALTER TABLE student RENAME CONSTRAINT aluno_atualizado_em_not_null TO student_updated_at_not_null;
ALTER TABLE student RENAME CONSTRAINT aluno_cpf_cifrado_not_null TO student_cpf_encrypted_not_null;
ALTER TABLE student RENAME CONSTRAINT aluno_cpf_hash_not_null TO student_cpf_hash_not_null;
ALTER TABLE student RENAME CONSTRAINT aluno_criado_em_not_null TO student_created_at_not_null;
ALTER TABLE student RENAME CONSTRAINT aluno_data_nascimento_not_null TO student_birth_date_not_null;
ALTER TABLE student RENAME CONSTRAINT aluno_id_not_null TO student_id_not_null;
ALTER TABLE student RENAME CONSTRAINT aluno_nome_not_null TO student_name_not_null;
ALTER TABLE student RENAME CONSTRAINT aluno_pkey TO student_pkey;
ALTER TABLE student RENAME CONSTRAINT uk_aluno_cpf_hash TO uk_student_cpf_hash;
ALTER TABLE lesson RENAME CONSTRAINT aula_aluno_id_fkey TO lesson_student_id_fkey;
ALTER TABLE lesson RENAME CONSTRAINT aula_aluno_id_not_null TO lesson_student_id_not_null;
ALTER TABLE lesson RENAME CONSTRAINT aula_criado_em_not_null TO lesson_created_at_not_null;
ALTER TABLE lesson RENAME CONSTRAINT aula_data_aula_not_null TO lesson_lesson_date_not_null;
ALTER TABLE lesson RENAME CONSTRAINT aula_id_not_null TO lesson_id_not_null;
ALTER TABLE lesson RENAME CONSTRAINT aula_pkey TO lesson_pkey;
ALTER TABLE lesson RENAME CONSTRAINT aula_professor_id_fkey TO lesson_teacher_id_fkey;
ALTER TABLE lesson RENAME CONSTRAINT aula_professor_id_not_null TO lesson_teacher_id_not_null;
ALTER TABLE ai_call RENAME CONSTRAINT chamada_ia_criado_em_not_null TO ai_call_created_at_not_null;
ALTER TABLE ai_call RENAME CONSTRAINT chamada_ia_duracao_ms_not_null TO ai_call_duration_ms_not_null;
ALTER TABLE ai_call RENAME CONSTRAINT chamada_ia_id_not_null TO ai_call_id_not_null;
ALTER TABLE ai_call RENAME CONSTRAINT chamada_ia_pkey TO ai_call_pkey;
ALTER TABLE ai_call RENAME CONSTRAINT chamada_ia_sucesso_not_null TO ai_call_success_not_null;
ALTER TABLE ai_call RENAME CONSTRAINT chamada_ia_tarefa_not_null TO ai_call_task_not_null;
ALTER TABLE ai_call RENAME CONSTRAINT chamada_ia_trabalho_id_fkey TO ai_call_job_id_fkey;
ALTER TABLE ai_call RENAME CONSTRAINT chamada_ia_trabalho_id_not_null TO ai_call_job_id_not_null;
ALTER TABLE channel_document RENAME CONSTRAINT ck_documento_canal_tipo TO ck_channel_document_type;
ALTER TABLE channel_document RENAME CONSTRAINT ck_documento_canal_versao TO ck_channel_document_version;
ALTER TABLE channel_document RENAME CONSTRAINT documento_canal_criado_em_not_null TO channel_document_created_at_not_null;
ALTER TABLE channel_document RENAME CONSTRAINT documento_canal_expira_em_not_null TO channel_document_expires_at_not_null;
ALTER TABLE channel_document RENAME CONSTRAINT documento_canal_nome_arquivo_not_null TO channel_document_file_name_not_null;
ALTER TABLE channel_document RENAME CONSTRAINT documento_canal_pkey TO channel_document_pkey;
ALTER TABLE channel_document RENAME CONSTRAINT documento_canal_professor_id_not_null TO channel_document_teacher_id_not_null;
ALTER TABLE channel_document RENAME CONSTRAINT documento_canal_relatorio_id_not_null TO channel_document_report_id_not_null;
ALTER TABLE channel_document RENAME CONSTRAINT documento_canal_relatorio_tipo_not_null TO channel_document_report_type_not_null;
ALTER TABLE channel_document RENAME CONSTRAINT documento_canal_token_hash_not_null TO channel_document_token_hash_not_null;
ALTER TABLE pdf_document RENAME CONSTRAINT documento_pdf_chave_armazenamento_not_null TO pdf_document_storage_key_not_null;
ALTER TABLE pdf_document RENAME CONSTRAINT documento_pdf_gerado_em_not_null TO pdf_document_generated_at_not_null;
ALTER TABLE pdf_document RENAME CONSTRAINT documento_pdf_id_not_null TO pdf_document_id_not_null;
ALTER TABLE pdf_document RENAME CONSTRAINT documento_pdf_pkey TO pdf_document_pkey;
ALTER TABLE pdf_document RENAME CONSTRAINT documento_pdf_sha256_not_null TO pdf_document_sha256_not_null;
ALTER TABLE pdf_document RENAME CONSTRAINT documento_pdf_tamanho_bytes_not_null TO pdf_document_size_bytes_not_null;
ALTER TABLE pdf_document RENAME CONSTRAINT documento_pdf_tipo_not_null TO pdf_document_type_not_null;
ALTER TABLE pdf_document RENAME CONSTRAINT documento_pdf_versao_id_fkey TO pdf_document_version_id_fkey;
ALTER TABLE pdf_document RENAME CONSTRAINT documento_pdf_versao_id_not_null TO pdf_document_version_id_not_null;
ALTER TABLE pdf_document RENAME CONSTRAINT uk_documento_pdf TO uk_pdf_document;
ALTER TABLE report_interaction RENAME CONSTRAINT interacao_relatorio_canal_not_null TO report_interaction_channel_not_null;
ALTER TABLE report_interaction RENAME CONSTRAINT interacao_relatorio_criado_em_not_null TO report_interaction_created_at_not_null;
ALTER TABLE report_interaction RENAME CONSTRAINT interacao_relatorio_id_not_null TO report_interaction_id_not_null;
ALTER TABLE report_interaction RENAME CONSTRAINT interacao_relatorio_meio_not_null TO report_interaction_medium_not_null;
ALTER TABLE report_interaction RENAME CONSTRAINT interacao_relatorio_pkey TO report_interaction_pkey;
ALTER TABLE report_interaction RENAME CONSTRAINT interacao_relatorio_relatorio_id_not_null TO report_interaction_report_id_not_null;
ALTER TABLE report_interaction RENAME CONSTRAINT interacao_relatorio_relatorio_tipo_not_null TO report_interaction_report_type_not_null;
ALTER TABLE report_interaction RENAME CONSTRAINT interacao_relatorio_resultado_not_null TO report_interaction_result_not_null;
ALTER TABLE report_interaction RENAME CONSTRAINT interacao_relatorio_texto_not_null TO report_interaction_text_not_null;
ALTER TABLE report_interaction RENAME CONSTRAINT interacao_relatorio_tipo_not_null TO report_interaction_type_not_null;
ALTER TABLE report_interaction RENAME CONSTRAINT interacao_relatorio_versao_base_not_null TO report_interaction_base_version_not_null;
ALTER TABLE teacher RENAME CONSTRAINT ck_professor_email_minusculo TO ck_teacher_email_lowercase;
ALTER TABLE teacher RENAME CONSTRAINT professor_ativo_not_null TO teacher_active_not_null;
ALTER TABLE teacher RENAME CONSTRAINT professor_atualizado_em_not_null TO teacher_updated_at_not_null;
ALTER TABLE teacher RENAME CONSTRAINT professor_criado_em_not_null TO teacher_created_at_not_null;
ALTER TABLE teacher RENAME CONSTRAINT professor_email_not_null TO teacher_email_not_null;
ALTER TABLE teacher RENAME CONSTRAINT professor_id_not_null TO teacher_id_not_null;
ALTER TABLE teacher RENAME CONSTRAINT professor_nome_not_null TO teacher_name_not_null;
ALTER TABLE teacher RENAME CONSTRAINT professor_pkey TO teacher_pkey;
ALTER TABLE teacher RENAME CONSTRAINT uk_professor_email TO uk_teacher_email;
ALTER TABLE teacher_student_assignment RENAME CONSTRAINT professor_aluno_aluno_id_fkey TO teacher_student_assignment_student_id_fkey;
ALTER TABLE teacher_student_assignment RENAME CONSTRAINT professor_aluno_aluno_id_not_null TO teacher_student_assignment_student_id_not_null;
ALTER TABLE teacher_student_assignment RENAME CONSTRAINT professor_aluno_criado_em_not_null TO teacher_student_assignment_created_at_not_null;
ALTER TABLE teacher_student_assignment RENAME CONSTRAINT professor_aluno_id_not_null TO teacher_student_assignment_id_not_null;
ALTER TABLE teacher_student_assignment RENAME CONSTRAINT professor_aluno_pkey TO teacher_student_assignment_pkey;
ALTER TABLE teacher_student_assignment RENAME CONSTRAINT professor_aluno_professor_id_fkey TO teacher_student_assignment_teacher_id_fkey;
ALTER TABLE teacher_student_assignment RENAME CONSTRAINT professor_aluno_professor_id_not_null TO teacher_student_assignment_teacher_id_not_null;
ALTER TABLE teacher_student_assignment RENAME CONSTRAINT uk_professor_aluno TO uk_teacher_student_assignment;
ALTER TABLE lesson_report RENAME CONSTRAINT ck_relatorio_aula_rodadas TO ck_lesson_report_question_rounds;
ALTER TABLE lesson_report RENAME CONSTRAINT relatorio_aula_aluno_id_fkey TO lesson_report_student_id_fkey;
ALTER TABLE lesson_report RENAME CONSTRAINT relatorio_aula_aluno_id_not_null TO lesson_report_student_id_not_null;
ALTER TABLE lesson_report RENAME CONSTRAINT relatorio_aula_atualizado_em_not_null TO lesson_report_updated_at_not_null;
ALTER TABLE lesson_report RENAME CONSTRAINT relatorio_aula_aula_id_fkey TO lesson_report_lesson_id_fkey;
ALTER TABLE lesson_report RENAME CONSTRAINT relatorio_aula_aula_id_not_null TO lesson_report_lesson_id_not_null;
ALTER TABLE lesson_report RENAME CONSTRAINT relatorio_aula_bloqueio_otimista_not_null TO lesson_report_lock_version_not_null;
ALTER TABLE lesson_report RENAME CONSTRAINT relatorio_aula_canal_origem_not_null TO lesson_report_origin_channel_not_null;
ALTER TABLE lesson_report RENAME CONSTRAINT relatorio_aula_conteudo_not_null TO lesson_report_content_not_null;
ALTER TABLE lesson_report RENAME CONSTRAINT relatorio_aula_criado_em_not_null TO lesson_report_created_at_not_null;
ALTER TABLE lesson_report RENAME CONSTRAINT relatorio_aula_id_not_null TO lesson_report_id_not_null;
ALTER TABLE lesson_report RENAME CONSTRAINT relatorio_aula_perguntas_pendentes_not_null TO lesson_report_pending_questions_not_null;
ALTER TABLE lesson_report RENAME CONSTRAINT relatorio_aula_pkey TO lesson_report_pkey;
ALTER TABLE lesson_report RENAME CONSTRAINT relatorio_aula_professor_id_fkey TO lesson_report_teacher_id_fkey;
ALTER TABLE lesson_report RENAME CONSTRAINT relatorio_aula_professor_id_not_null TO lesson_report_teacher_id_not_null;
ALTER TABLE lesson_report RENAME CONSTRAINT relatorio_aula_rodadas_perguntas_not_null TO lesson_report_question_rounds_not_null;
ALTER TABLE lesson_report RENAME CONSTRAINT relatorio_aula_status_not_null TO lesson_report_status_not_null;
ALTER TABLE lesson_report RENAME CONSTRAINT relatorio_aula_tempo_processamento_ms_not_null TO lesson_report_processing_time_ms_not_null;
ALTER TABLE lesson_report RENAME CONSTRAINT relatorio_aula_transcricao_not_null TO lesson_report_transcription_not_null;
ALTER TABLE lesson_report RENAME CONSTRAINT relatorio_aula_versao_vigente_not_null TO lesson_report_current_version_not_null;
ALTER TABLE lesson_report RENAME CONSTRAINT uk_relatorio_aula_aula TO uk_lesson_report_lesson;
ALTER TABLE semester_report RENAME CONSTRAINT ck_relatorio_semestral_aulas TO ck_semester_report_lessons;
ALTER TABLE semester_report RENAME CONSTRAINT ck_relatorio_semestral_em_aberto TO ck_semester_report_open;
ALTER TABLE semester_report RENAME CONSTRAINT ck_relatorio_semestral_periodo TO ck_semester_report_period;
ALTER TABLE semester_report RENAME CONSTRAINT relatorio_semestral_aluno_id_fkey TO semester_report_student_id_fkey;
ALTER TABLE semester_report RENAME CONSTRAINT relatorio_semestral_aluno_id_not_null TO semester_report_student_id_not_null;
ALTER TABLE semester_report RENAME CONSTRAINT relatorio_semestral_atualizado_em_not_null TO semester_report_updated_at_not_null;
ALTER TABLE semester_report RENAME CONSTRAINT relatorio_semestral_bloqueio_otimista_not_null TO semester_report_lock_version_not_null;
ALTER TABLE semester_report RENAME CONSTRAINT relatorio_semestral_canal_origem_not_null TO semester_report_origin_channel_not_null;
ALTER TABLE semester_report RENAME CONSTRAINT relatorio_semestral_conteudo_not_null TO semester_report_content_not_null;
ALTER TABLE semester_report RENAME CONSTRAINT relatorio_semestral_criado_em_not_null TO semester_report_created_at_not_null;
ALTER TABLE semester_report RENAME CONSTRAINT relatorio_semestral_id_not_null TO semester_report_id_not_null;
ALTER TABLE semester_report RENAME CONSTRAINT relatorio_semestral_informacoes_gerais_not_null TO semester_report_general_information_not_null;
ALTER TABLE semester_report RENAME CONSTRAINT relatorio_semestral_periodo_chave_not_null TO semester_report_period_key_not_null;
ALTER TABLE semester_report RENAME CONSTRAINT relatorio_semestral_periodo_fim_not_null TO semester_report_period_end_not_null;
ALTER TABLE semester_report RENAME CONSTRAINT relatorio_semestral_periodo_inicio_not_null TO semester_report_period_start_not_null;
ALTER TABLE semester_report RENAME CONSTRAINT relatorio_semestral_pkey TO semester_report_pkey;
ALTER TABLE semester_report RENAME CONSTRAINT relatorio_semestral_professor_id_fkey TO semester_report_teacher_id_fkey;
ALTER TABLE semester_report RENAME CONSTRAINT relatorio_semestral_professor_id_not_null TO semester_report_teacher_id_not_null;
ALTER TABLE semester_report RENAME CONSTRAINT relatorio_semestral_quantidade_aulas_consideradas_not_null TO semester_report_considered_lesson_count_not_null;
ALTER TABLE semester_report RENAME CONSTRAINT relatorio_semestral_quantidade_relatorios_em_aberto_not_null TO semester_report_open_report_count_not_null;
ALTER TABLE semester_report RENAME CONSTRAINT relatorio_semestral_status_not_null TO semester_report_status_not_null;
ALTER TABLE semester_report RENAME CONSTRAINT relatorio_semestral_tempo_processamento_ms_not_null TO semester_report_processing_time_ms_not_null;
ALTER TABLE semester_report RENAME CONSTRAINT relatorio_semestral_versao_vigente_not_null TO semester_report_current_version_not_null;
ALTER TABLE web_session RENAME CONSTRAINT sessao_web_criada_em_not_null TO web_session_created_at_not_null;
ALTER TABLE web_session RENAME CONSTRAINT sessao_web_expira_em_not_null TO web_session_expires_at_not_null;
ALTER TABLE web_session RENAME CONSTRAINT sessao_web_id_not_null TO web_session_id_not_null;
ALTER TABLE web_session RENAME CONSTRAINT sessao_web_pkey TO web_session_pkey;
ALTER TABLE web_session RENAME CONSTRAINT sessao_web_professor_id_fkey TO web_session_teacher_id_fkey;
ALTER TABLE web_session RENAME CONSTRAINT sessao_web_professor_id_not_null TO web_session_teacher_id_not_null;
ALTER TABLE web_session RENAME CONSTRAINT sessao_web_token_hash_not_null TO web_session_token_hash_not_null;
ALTER TABLE web_session RENAME CONSTRAINT sessao_web_ultimo_uso_em_not_null TO web_session_last_used_at_not_null;
ALTER TABLE web_session RENAME CONSTRAINT uk_sessao_web_token TO uk_web_session_token;
ALTER TABLE access_attempt RENAME CONSTRAINT ck_tentativa_acesso_positiva TO ck_access_attempt_positive;
ALTER TABLE access_attempt RENAME CONSTRAINT tentativa_acesso_atualizado_em_not_null TO access_attempt_updated_at_not_null;
ALTER TABLE access_attempt RENAME CONSTRAINT tentativa_acesso_canal_not_null TO access_attempt_channel_not_null;
ALTER TABLE access_attempt RENAME CONSTRAINT tentativa_acesso_id_not_null TO access_attempt_id_not_null;
ALTER TABLE access_attempt RENAME CONSTRAINT tentativa_acesso_origem_hash_not_null TO access_attempt_source_hash_not_null;
ALTER TABLE access_attempt RENAME CONSTRAINT tentativa_acesso_pkey TO access_attempt_pkey;
ALTER TABLE access_attempt RENAME CONSTRAINT tentativa_acesso_tentativas_incorretas_not_null TO access_attempt_failed_attempts_not_null;
ALTER TABLE access_attempt RENAME CONSTRAINT uk_tentativa_acesso_origem TO uk_access_attempt_source;
ALTER TABLE processing_job RENAME CONSTRAINT trabalho_processamento_aluno_id_fkey TO processing_job_student_id_fkey;
ALTER TABLE processing_job RENAME CONSTRAINT trabalho_processamento_aluno_id_not_null TO processing_job_student_id_not_null;
ALTER TABLE processing_job RENAME CONSTRAINT trabalho_processamento_aviso_demora_enviado_not_null TO processing_job_delay_notice_sent_not_null;
ALTER TABLE processing_job RENAME CONSTRAINT trabalho_processamento_canal_solicitacao_not_null TO processing_job_request_channel_not_null;
ALTER TABLE processing_job RENAME CONSTRAINT trabalho_processamento_criado_em_not_null TO processing_job_created_at_not_null;
ALTER TABLE processing_job RENAME CONSTRAINT trabalho_processamento_etapa_not_null TO processing_job_step_not_null;
ALTER TABLE processing_job RENAME CONSTRAINT trabalho_processamento_id_not_null TO processing_job_id_not_null;
ALTER TABLE processing_job RENAME CONSTRAINT trabalho_processamento_meio_not_null TO processing_job_medium_not_null;
ALTER TABLE processing_job RENAME CONSTRAINT trabalho_processamento_parametros_not_null TO processing_job_parameters_not_null;
ALTER TABLE processing_job RENAME CONSTRAINT trabalho_processamento_pkey TO processing_job_pkey;
ALTER TABLE processing_job RENAME CONSTRAINT trabalho_processamento_professor_id_fkey TO processing_job_teacher_id_fkey;
ALTER TABLE processing_job RENAME CONSTRAINT trabalho_processamento_professor_id_not_null TO processing_job_teacher_id_not_null;
ALTER TABLE processing_job RENAME CONSTRAINT trabalho_processamento_status_not_null TO processing_job_status_not_null;
ALTER TABLE processing_job RENAME CONSTRAINT trabalho_processamento_tentativas_not_null TO processing_job_attempts_not_null;
ALTER TABLE processing_job RENAME CONSTRAINT trabalho_processamento_tipo_not_null TO processing_job_type_not_null;
ALTER TABLE processed_telegram_update RENAME CONSTRAINT ck_update_telegram_origem TO ck_processed_telegram_update_origin;
ALTER TABLE processed_telegram_update RENAME CONSTRAINT ck_update_telegram_total_acoes TO ck_processed_telegram_update_total_actions;
ALTER TABLE processed_telegram_update RENAME CONSTRAINT update_telegram_processado_pkey TO processed_telegram_update_pkey;
ALTER TABLE processed_telegram_update RENAME CONSTRAINT update_telegram_processado_recebido_em_not_null TO processed_telegram_update_received_at_not_null;
ALTER TABLE processed_telegram_update RENAME CONSTRAINT update_telegram_processado_update_id_not_null TO processed_telegram_update_update_id_not_null;
ALTER TABLE report_version RENAME CONSTRAINT ck_versao_relatorio_numero TO ck_report_version_number;
ALTER TABLE report_version RENAME CONSTRAINT uk_versao_relatorio TO uk_report_version;
ALTER TABLE report_version RENAME CONSTRAINT versao_relatorio_canal_not_null TO report_version_channel_not_null;
ALTER TABLE report_version RENAME CONSTRAINT versao_relatorio_conteudo_not_null TO report_version_content_not_null;
ALTER TABLE report_version RENAME CONSTRAINT versao_relatorio_criada_em_not_null TO report_version_created_at_not_null;
ALTER TABLE report_version RENAME CONSTRAINT versao_relatorio_id_not_null TO report_version_id_not_null;
ALTER TABLE report_version RENAME CONSTRAINT versao_relatorio_interacao_id_fkey TO report_version_interaction_id_fkey;
ALTER TABLE report_version RENAME CONSTRAINT versao_relatorio_numero_not_null TO report_version_number_not_null;
ALTER TABLE report_version RENAME CONSTRAINT versao_relatorio_origem_not_null TO report_version_origin_not_null;
ALTER TABLE report_version RENAME CONSTRAINT versao_relatorio_pkey TO report_version_pkey;
ALTER TABLE report_version RENAME CONSTRAINT versao_relatorio_relatorio_id_not_null TO report_version_report_id_not_null;
ALTER TABLE report_version RENAME CONSTRAINT versao_relatorio_relatorio_tipo_not_null TO report_version_report_type_not_null;
ALTER TABLE telegram_link RENAME CONSTRAINT uk_vinculo_telegram_professor TO uk_telegram_link_teacher;
ALTER TABLE telegram_link RENAME CONSTRAINT uk_vinculo_telegram_usuario TO uk_telegram_link_user;
ALTER TABLE telegram_link RENAME CONSTRAINT vinculo_telegram_aluno_selecionado_id_fkey TO telegram_link_selected_student_id_fkey;
ALTER TABLE telegram_link RENAME CONSTRAINT vinculo_telegram_atualizado_em_not_null TO telegram_link_updated_at_not_null;
ALTER TABLE telegram_link RENAME CONSTRAINT vinculo_telegram_bloqueio_otimista_not_null TO telegram_link_lock_version_not_null;
ALTER TABLE telegram_link RENAME CONSTRAINT vinculo_telegram_chat_id_not_null TO telegram_link_chat_id_not_null;
ALTER TABLE telegram_link RENAME CONSTRAINT vinculo_telegram_dados_fluxo_not_null TO telegram_link_flow_data_not_null;
ALTER TABLE telegram_link RENAME CONSTRAINT vinculo_telegram_etapa_fluxo_not_null TO telegram_link_flow_step_not_null;
ALTER TABLE telegram_link RENAME CONSTRAINT vinculo_telegram_fluxo_not_null TO telegram_link_flow_not_null;
ALTER TABLE telegram_link RENAME CONSTRAINT vinculo_telegram_id_not_null TO telegram_link_id_not_null;
ALTER TABLE telegram_link RENAME CONSTRAINT vinculo_telegram_pkey TO telegram_link_pkey;
ALTER TABLE telegram_link RENAME CONSTRAINT vinculo_telegram_professor_id_fkey TO telegram_link_teacher_id_fkey;
ALTER TABLE telegram_link RENAME CONSTRAINT vinculo_telegram_professor_id_not_null TO telegram_link_teacher_id_not_null;
ALTER TABLE telegram_link RENAME CONSTRAINT vinculo_telegram_telegram_usuario_id_not_null TO telegram_link_telegram_user_id_not_null;
ALTER TABLE telegram_link RENAME CONSTRAINT vinculo_telegram_vinculado_em_not_null TO telegram_link_linked_at_not_null;

-- 5. Índices avulsos (parciais e por expressão)
ALTER INDEX ix_acao_canal_chat_vivas RENAME TO ix_channel_action_live_chat;
ALTER INDEX ix_acao_canal_entrega RENAME TO ix_channel_action_delivery;
ALTER INDEX ix_acao_canal_expurgo RENAME TO ix_channel_action_purge;
ALTER INDEX ix_acao_canal_lote RENAME TO ix_channel_action_batch;
ALTER INDEX ix_acao_canal_update RENAME TO ix_channel_action_update;
ALTER INDEX ix_aula_professor_aluno RENAME TO ix_lesson_teacher_student;
ALTER INDEX ix_aula_professor_aluno_data RENAME TO ix_lesson_teacher_student_date;
ALTER INDEX ix_chamada_ia_relatorio RENAME TO ix_ai_call_report;
ALTER INDEX ix_documento_canal_expira RENAME TO ix_channel_document_expires;
ALTER INDEX ix_interacao_relatorio RENAME TO ix_report_interaction;
ALTER INDEX uk_professor_codigo_acesso_hash RENAME TO uk_teacher_access_code_hash;
ALTER INDEX ix_professor_aluno_aluno RENAME TO ix_teacher_student_assignment_student;
ALTER INDEX ix_relatorio_aula_aprovados RENAME TO ix_lesson_report_approved;
ALTER INDEX uk_relatorio_aula_em_aberto RENAME TO uk_lesson_report_open;
ALTER INDEX uk_relatorio_semestral_aprovado RENAME TO uk_semester_report_approved;
ALTER INDEX uk_relatorio_semestral_em_aberto RENAME TO uk_semester_report_open;
ALTER INDEX ix_sessao_web_expira RENAME TO ix_web_session_expires;
ALTER INDEX ix_trabalho_pendente RENAME TO ix_processing_job_pending;
ALTER INDEX ix_trabalho_relatorio RENAME TO ix_processing_job_report;
ALTER INDEX uk_trabalho_consolidacao_em_curso RENAME TO uk_processing_job_semester_consolidation_running;
ALTER INDEX uk_trabalho_registro_em_curso RENAME TO uk_processing_job_registration_running;
ALTER INDEX ix_update_telegram_recebido RENAME TO ix_processed_telegram_update_received;

-- 6. Chaves JSON só de banco: função auxiliar temporária (existe só nesta sessão; não é objeto do esquema).

--    Percorre o jsonb recursivamente e renomeia APENAS chaves; valores (textos do professor ou da IA) nunca são alterados.
CREATE FUNCTION pg_temp.rename_json_keys(document jsonb, names jsonb) RETURNS jsonb
LANGUAGE sql IMMUTABLE AS $$
  SELECT CASE jsonb_typeof(document)
    WHEN 'object' THEN COALESCE((
      SELECT jsonb_object_agg(COALESCE(names ->> entry.key, entry.key), pg_temp.rename_json_keys(entry.value, names))
        FROM jsonb_each(document) AS entry), '{}'::jsonb)
    WHEN 'array' THEN COALESCE((
      SELECT jsonb_agg(pg_temp.rename_json_keys(element.value, names) ORDER BY element.position)
        FROM jsonb_array_elements(document) WITH ORDINALITY AS element(value, position)), '[]'::jsonb)
    ELSE document
  END
$$;

-- conteúdo do relatório de aula (5 chaves)
UPDATE lesson_report
   SET content = pg_temp.rename_json_keys(content, '{"conteudosTrabalhados":"workedTopics","evolucao":"progress","dificuldades":"difficulties","atividadesPropostas":"proposedActivities","observacoes":"observations"}'::jsonb)
 WHERE content IS NOT NULL;

-- conteúdo do relatório semestral (seções e subchaves)
UPDATE semester_report
   SET content = pg_temp.rename_json_keys(content, '{"frequenciaEEstudo":"attendanceAndStudy","frequenciaAulas":"lessonAttendance","regularidadeEstudoEmCasa":"homeStudyRegularity","engajamento":"engagement","comentarioGeral":"generalComment","tecnica":"technique","conteudosTrabalhados":"workedTopics","evolucaoTecnica":"technicalProgress","pontosADesenvolver":"pointsToDevelop","estudosRealizados":"completedStudies","musicalidade":"musicality","repertorioEstudado":"studiedRepertoire","som":"sound","ritmo":"rhythm","fraseado":"phrasing","articulacao":"articulation","estilo":"style","comentarioMusical":"musicalComment","leituraEMemorizacao":"readingAndMemorization","avaliacao":"assessment","comentario":"comment","pontosDeAtencao":"attentionPoints","estrategiasPedagogicas":"pedagogicalStrategies","itens":"items","acompanhamentoFamiliar":"familySupport","nivelDePresenca":"presenceLevel","observacaoDoProfessor":"teacherObservation","planejamentoProximoSemestre":"nextSemesterPlan","metasTecnicas":"technicalGoals","metasMusicais":"musicalGoals","habitosDeEstudo":"studyHabits","repertorioProposto":"proposedRepertoire","estudosTecnicos":"technicalStudies","leituraETeoria":"readingAndTheory","projetoDoSemestre":"semesterProject","parecerFinal":"finalOpinion"}'::jsonb)
 WHERE content IS NOT NULL;

-- informações gerais do semestral
UPDATE semester_report
   SET general_information = pg_temp.rename_json_keys(general_information, '{"nome":"name","idade":"age","instrumento":"instrument","professor":"teacher","periodoChave":"periodKey","periodoInicio":"periodStart","periodoFim":"periodEnd","quantidadeAulasConsideradas":"consideredLessonCount"}'::jsonb)
 WHERE general_information IS NOT NULL;

-- versão de relatório de aula
UPDATE report_version
   SET content = pg_temp.rename_json_keys(content, '{"conteudosTrabalhados":"workedTopics","evolucao":"progress","dificuldades":"difficulties","atividadesPropostas":"proposedActivities","observacoes":"observations"}'::jsonb)
 WHERE content IS NOT NULL
   AND report_type = 'AULA';

-- versão de relatório semestral
UPDATE report_version
   SET content = pg_temp.rename_json_keys(content, '{"frequenciaEEstudo":"attendanceAndStudy","frequenciaAulas":"lessonAttendance","regularidadeEstudoEmCasa":"homeStudyRegularity","engajamento":"engagement","comentarioGeral":"generalComment","tecnica":"technique","conteudosTrabalhados":"workedTopics","evolucaoTecnica":"technicalProgress","pontosADesenvolver":"pointsToDevelop","estudosRealizados":"completedStudies","musicalidade":"musicality","repertorioEstudado":"studiedRepertoire","som":"sound","ritmo":"rhythm","fraseado":"phrasing","articulacao":"articulation","estilo":"style","comentarioMusical":"musicalComment","leituraEMemorizacao":"readingAndMemorization","avaliacao":"assessment","comentario":"comment","pontosDeAtencao":"attentionPoints","estrategiasPedagogicas":"pedagogicalStrategies","itens":"items","acompanhamentoFamiliar":"familySupport","nivelDePresenca":"presenceLevel","observacaoDoProfessor":"teacherObservation","planejamentoProximoSemestre":"nextSemesterPlan","metasTecnicas":"technicalGoals","metasMusicais":"musicalGoals","habitosDeEstudo":"studyHabits","repertorioProposto":"proposedRepertoire","estudosTecnicos":"technicalStudies","leituraETeoria":"readingAndTheory","projetoDoSemestre":"semesterProject","parecerFinal":"finalOpinion"}'::jsonb)
 WHERE content IS NOT NULL
   AND report_type = 'SEMESTRAL';

-- parâmetros do trabalho
UPDATE processing_job
   SET parameters = pg_temp.rename_json_keys(parameters, '{"texto":"text","versaoBase":"baseVersion","textoEntendido":"understoodText","arquivoCanal":"channelFile","arquivoCanalUnico":"uniqueChannelFile","arquivoMime":"fileMime","arquivoTamanho":"fileSize","mensagemEtapa":"stepMessage","periodoChave":"periodKey","confirmarMesmoComEmAberto":"confirmEvenWithOpen"}'::jsonb)
 WHERE parameters IS NOT NULL;

-- dados do fluxo da conversa do Telegram
UPDATE telegram_link
   SET flow_data = pg_temp.rename_json_keys(flow_data, '{"confirmacao":"confirmation","opcoes":"options","histAluno":"historyStudent","histItens":"historyItems","alunoEmAberto":"studentWithOpenReport","semAluno":"semesterStudent","semOpcoes":"semesterOptions","semPeriodo":"semesterPeriod"}'::jsonb)
 WHERE flow_data IS NOT NULL;

-- 7. Índice por expressão JSON: recriado com a chave nova, DEPOIS da etapa 6 (sem isso o índice único deixaria de proteger)
DROP INDEX uk_processing_job_semester_consolidation_running;
CREATE UNIQUE INDEX uk_processing_job_semester_consolidation_running ON processing_job USING btree (teacher_id, student_id, ((parameters ->> 'periodKey'::text))) WHERE (((type)::text = 'CONSOLIDACAO_SEMESTRAL'::text) AND (status IN ('PENDENTE', 'EM_EXECUCAO')));

