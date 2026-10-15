-- Fatia 4 da feature 003: valores de enumeração em inglês (os nomes de tabela, coluna e chave já são da V7).
-- Os valores são os de `identifier-map.md` §2; chaves e textos do JSON do fio acompanham os contratos novos.
-- A migração roda em uma transação e ABORTA se sobrar valor antigo em coluna de conjunto fechado (parte 5).

-- 1. Objetos cujo predicado tem literal de enumeração: descartados aqui e recriados na parte 4, com os mesmos nomes.
DROP INDEX ix_channel_action_delivery;
DROP INDEX ix_channel_action_live_chat;
DROP INDEX ix_lesson_report_approved;
DROP INDEX uk_lesson_report_open;
DROP INDEX ix_processing_job_pending;
DROP INDEX uk_processing_job_registration_running;
DROP INDEX uk_processing_job_semester_consolidation_running;
DROP INDEX uk_semester_report_approved;
DROP INDEX uk_semester_report_open;
ALTER TABLE channel_action DROP CONSTRAINT ck_channel_action_origin;
ALTER TABLE channel_action DROP CONSTRAINT ck_channel_action_state;
ALTER TABLE channel_action DROP CONSTRAINT ck_channel_action_type;
ALTER TABLE channel_action DROP CONSTRAINT ck_channel_action_update_reply;
ALTER TABLE channel_document DROP CONSTRAINT ck_channel_document_type;
ALTER TABLE processed_telegram_update DROP CONSTRAINT ck_processed_telegram_update_origin;

-- 2. Valores das colunas, uma coluna por vez (os conjuntos de cada coluna estão em `identifier-map.md` §2).
UPDATE ai_call AS target SET report_type = changed.novo FROM (VALUES ('AULA', 'LESSON'), ('SEMESTRAL', 'SEMESTER')) AS changed(antigo, novo) WHERE target.report_type = changed.antigo;
UPDATE channel_document AS target SET report_type = changed.novo FROM (VALUES ('AULA', 'LESSON'), ('SEMESTRAL', 'SEMESTER')) AS changed(antigo, novo) WHERE target.report_type = changed.antigo;
UPDATE report_interaction AS target SET report_type = changed.novo FROM (VALUES ('AULA', 'LESSON'), ('SEMESTRAL', 'SEMESTER')) AS changed(antigo, novo) WHERE target.report_type = changed.antigo;
UPDATE processing_job AS target SET report_type = changed.novo FROM (VALUES ('AULA', 'LESSON'), ('SEMESTRAL', 'SEMESTER')) AS changed(antigo, novo) WHERE target.report_type = changed.antigo;
UPDATE report_version AS target SET report_type = changed.novo FROM (VALUES ('AULA', 'LESSON'), ('SEMESTRAL', 'SEMESTER')) AS changed(antigo, novo) WHERE target.report_type = changed.antigo;
UPDATE telegram_link AS target SET focused_report_type = changed.novo FROM (VALUES ('AULA', 'LESSON'), ('SEMESTRAL', 'SEMESTER')) AS changed(antigo, novo) WHERE target.focused_report_type = changed.antigo;
UPDATE pdf_document AS target SET type = changed.novo FROM (VALUES ('CONFERENCIA', 'REVIEW'), ('OFICIAL', 'OFFICIAL')) AS changed(antigo, novo) WHERE target.type = changed.antigo;
UPDATE lesson_report AS target SET status = changed.novo FROM (VALUES ('EM_PROCESSAMENTO', 'PROCESSING'), ('FALHA_PROCESSAMENTO', 'PROCESSING_FAILED'), ('AGUARDANDO_RESPOSTAS', 'AWAITING_ANSWERS'), ('AGUARDANDO_CONFERENCIA', 'AWAITING_REVIEW'), ('APROVADO', 'APPROVED'), ('CANCELADO', 'CANCELLED')) AS changed(antigo, novo) WHERE target.status = changed.antigo;
UPDATE semester_report AS target SET status = changed.novo FROM (VALUES ('EM_PROCESSAMENTO', 'PROCESSING'), ('FALHA_PROCESSAMENTO', 'PROCESSING_FAILED'), ('AGUARDANDO_RESPOSTAS', 'AWAITING_ANSWERS'), ('AGUARDANDO_CONFERENCIA', 'AWAITING_REVIEW'), ('APROVADO', 'APPROVED'), ('CANCELADO', 'CANCELLED')) AS changed(antigo, novo) WHERE target.status = changed.antigo;
UPDATE processing_job AS target SET status = changed.novo FROM (VALUES ('PENDENTE', 'PENDING'), ('EM_EXECUCAO', 'RUNNING'), ('CONCLUIDO', 'COMPLETED'), ('FALHOU', 'FAILED'), ('DESCARTADO', 'DISCARDED')) AS changed(antigo, novo) WHERE target.status = changed.antigo;
UPDATE processing_job AS target SET type = changed.novo FROM (VALUES ('REGISTRO_AULA', 'LESSON_REGISTRATION'), ('NOVA_TENTATIVA', 'RETRY'), ('RESPOSTA_PERGUNTAS', 'QUESTION_ANSWER'), ('ALTERACAO', 'CHANGE'), ('CONSOLIDACAO_SEMESTRAL', 'SEMESTER_CONSOLIDATION')) AS changed(antigo, novo) WHERE target.type = changed.antigo;
UPDATE processing_job AS target SET step = changed.novo FROM (VALUES ('ENVIANDO', 'UPLOADING'), ('TRANSCREVENDO', 'TRANSCRIBING'), ('ESTRUTURANDO', 'STRUCTURING'), ('INCORPORANDO_RESPOSTAS', 'INCORPORATING_ANSWERS'), ('TRANSCREVENDO_PEDIDO', 'TRANSCRIBING_REQUEST'), ('APLICANDO_ALTERACAO', 'APPLYING_CHANGE'), ('CONSOLIDANDO', 'CONSOLIDATING'), ('GERANDO_PDF', 'GENERATING_PDF'), ('PRONTO', 'READY')) AS changed(antigo, novo) WHERE target.step = changed.antigo;
UPDATE processing_job AS target SET result_code = changed.novo FROM (VALUES ('PRONTO_PARA_CONFERENCIA', 'READY_FOR_REVIEW'), ('PERGUNTAS_PENDENTES', 'PENDING_QUESTIONS'), ('NOVA_VERSAO', 'NEW_VERSION'), ('SEM_ALTERACAO_DE_CONTEUDO', 'NO_CONTENT_CHANGE'), ('PEDIDO_DE_ACAO_IGNORADO', 'ACTION_REQUEST_IGNORED'), ('VERSAO_DESATUALIZADA', 'STALE_VERSION'), ('AUDIO_NAO_PROCESSAVEL', 'AUDIO_NOT_PROCESSABLE'), ('FALHA_ESTRUTURACAO', 'STRUCTURING_FAILED'), ('FALHA_ALTERACAO', 'CHANGE_FAILED'), ('FALHA_RESPOSTA', 'ANSWER_FAILED'), ('FALHA_CONSOLIDACAO', 'CONSOLIDATION_FAILED'), ('DESCARTADO', 'DISCARDED')) AS changed(antigo, novo) WHERE target.result_code = changed.antigo;
UPDATE lesson_report AS target SET processing_step = changed.novo FROM (VALUES ('ENVIANDO', 'UPLOADING'), ('TRANSCREVENDO', 'TRANSCRIBING'), ('ESTRUTURANDO', 'STRUCTURING'), ('INCORPORANDO_RESPOSTAS', 'INCORPORATING_ANSWERS'), ('TRANSCREVENDO_PEDIDO', 'TRANSCRIBING_REQUEST'), ('APLICANDO_ALTERACAO', 'APPLYING_CHANGE'), ('CONSOLIDANDO', 'CONSOLIDATING'), ('GERANDO_PDF', 'GENERATING_PDF'), ('PRONTO', 'READY')) AS changed(antigo, novo) WHERE target.processing_step = changed.antigo;
UPDATE semester_report AS target SET processing_step = changed.novo FROM (VALUES ('ENVIANDO', 'UPLOADING'), ('TRANSCREVENDO', 'TRANSCRIBING'), ('ESTRUTURANDO', 'STRUCTURING'), ('INCORPORANDO_RESPOSTAS', 'INCORPORATING_ANSWERS'), ('TRANSCREVENDO_PEDIDO', 'TRANSCRIBING_REQUEST'), ('APLICANDO_ALTERACAO', 'APPLYING_CHANGE'), ('CONSOLIDANDO', 'CONSOLIDATING'), ('GERANDO_PDF', 'GENERATING_PDF'), ('PRONTO', 'READY')) AS changed(antigo, novo) WHERE target.processing_step = changed.antigo;
UPDATE processing_job AS target SET medium = changed.novo FROM (VALUES ('TEXTO', 'TEXT')) AS changed(antigo, novo) WHERE target.medium = changed.antigo;
UPDATE report_interaction AS target SET medium = changed.novo FROM (VALUES ('TEXTO', 'TEXT')) AS changed(antigo, novo) WHERE target.medium = changed.antigo;
UPDATE telegram_link AS target SET flow = changed.novo FROM (VALUES ('NENHUM', 'NONE'), ('REGISTRO_AULA', 'LESSON_REGISTRATION'), ('SEMESTRAL', 'SEMESTER'), ('HISTORICO', 'HISTORY')) AS changed(antigo, novo) WHERE target.flow = changed.antigo;
UPDATE telegram_link AS target SET flow_step = changed.novo FROM (VALUES ('NENHUMA', 'NONE'), ('ESCOLHENDO_ALUNO', 'CHOOSING_STUDENT'), ('AGUARDANDO_AUDIO', 'AWAITING_AUDIO'), ('CONFIRMANDO_ALUNO_DO_AUDIO', 'CONFIRMING_AUDIO_STUDENT'), ('AVISO_RELATORIO_EM_ABERTO', 'OPEN_REPORT_NOTICE'), ('ESCOLHENDO_SEMESTRE', 'CHOOSING_SEMESTER'), ('CONFIRMANDO_GERACAO_SEMESTRAL', 'CONFIRMING_SEMESTER_GENERATION'), ('ESCOLHENDO_ITEM_HISTORICO', 'CHOOSING_HISTORY_ITEM')) AS changed(antigo, novo) WHERE target.flow_step = changed.antigo;
UPDATE channel_action AS target SET state = changed.novo FROM (VALUES ('PENDENTE', 'PENDING'), ('ENTREGUE', 'DELIVERED'), ('EXECUTADA', 'EXECUTED'), ('FALHOU', 'FAILED'), ('FALHA_DEFINITIVA', 'PERMANENTLY_FAILED'), ('DESCARTADA', 'DISCARDED')) AS changed(antigo, novo) WHERE target.state = changed.antigo;
UPDATE channel_action AS target SET type = changed.novo FROM (VALUES ('MENSAGEM', 'MESSAGE'), ('EDICAO', 'EDIT'), ('DOCUMENTO', 'DOCUMENT'), ('RESPOSTA_CALLBACK', 'CALLBACK_REPLY'), ('MENSAGEM_ETAPA', 'STEP_MESSAGE')) AS changed(antigo, novo) WHERE target.type = changed.antigo;
UPDATE channel_action AS target SET origin = changed.novo FROM (VALUES ('RESPOSTA_UPDATE', 'UPDATE_REPLY'), ('PROATIVA', 'PROACTIVE'), ('COMPENSACAO', 'COMPENSATION')) AS changed(antigo, novo) WHERE target.origin = changed.antigo;
UPDATE channel_action AS target SET reason_code = changed.novo FROM (VALUES ('MENSAGEM', 'MESSAGE'), ('EDICAO', 'EDIT'), ('DOCUMENTO', 'DOCUMENT'), ('RESPOSTA_CALLBACK', 'CALLBACK_REPLY'), ('MENSAGEM_ETAPA', 'STEP_MESSAGE'), ('PROATIVA', 'PROACTIVE'), ('COMPENSACAO', 'COMPENSATION'), ('EXECUTADA', 'EXECUTED'), ('FALHOU', 'FAILED'), ('PENDENTE', 'PENDING'), ('ENTREGUE', 'DELIVERED'), ('FALHA_DEFINITIVA', 'PERMANENTLY_FAILED'), ('DESCARTADA', 'DISCARDED'), ('REQUISICAO_INVALIDA', 'INVALID_REQUEST'), ('VERSAO_NAO_SUPORTADA', 'UNSUPPORTED_VERSION'), ('CODIGO_INVALIDO', 'INVALID_CODE'), ('CODIGO_EXPIRADO', 'EXPIRED_CODE'), ('BLOQUEADO_TEMPORARIAMENTE', 'TEMPORARILY_BLOCKED'), ('TELEGRAM_JA_VINCULADO', 'TELEGRAM_ALREADY_LINKED'), ('PROFESSOR_JA_VINCULADO', 'TEACHER_ALREADY_LINKED'), ('SESSAO_EXPIRADA', 'SESSION_EXPIRED'), ('NAO_AUTENTICADO', 'NOT_AUTHENTICATED'), ('ALUNO_NAO_ASSOCIADO', 'STUDENT_NOT_ASSIGNED'), ('RECURSO_NAO_ENCONTRADO', 'RESOURCE_NOT_FOUND'), ('PROCESSAMENTO_EM_ANDAMENTO', 'PROCESSING_IN_PROGRESS'), ('AUDIO_MUITO_GRANDE', 'AUDIO_TOO_LARGE'), ('FORMATO_NAO_SUPORTADO', 'UNSUPPORTED_FORMAT'), ('AUDIO_NAO_PROCESSAVEL', 'AUDIO_NOT_PROCESSABLE'), ('RESPOSTA_VAZIA', 'EMPTY_ANSWER'), ('ALTERACAO_VAZIA', 'EMPTY_CHANGE'), ('VERSAO_DESATUALIZADA', 'STALE_VERSION'), ('RELATORIO_JA_APROVADO', 'REPORT_ALREADY_APPROVED'), ('RELATORIO_CANCELADO', 'REPORT_CANCELLED'), ('ACAO_NAO_PERMITIDA_NA_ETAPA', 'ACTION_NOT_ALLOWED_AT_STEP'), ('RELATORIO_EM_ABERTO', 'REPORT_ALREADY_OPEN'), ('SEM_AULAS_APROVADAS_NO_PERIODO', 'NO_APPROVED_LESSONS_IN_PERIOD'), ('HA_RELATORIOS_EM_ABERTO', 'OPEN_REPORTS_EXIST'), ('SEMESTRAL_JA_APROVADO', 'SEMESTER_REPORT_ALREADY_APPROVED'), ('EMAIL_INVALIDO', 'INVALID_EMAIL'), ('EMAIL_DUPLICADO_NO_ARQUIVO', 'DUPLICATE_EMAIL_IN_FILE'), ('NOME_OBRIGATORIO', 'NAME_REQUIRED'), ('CPF_INVALIDO', 'INVALID_CPF'), ('CPF_DUPLICADO_NO_ARQUIVO', 'DUPLICATE_CPF_IN_FILE'), ('DATA_NASCIMENTO_INVALIDA', 'INVALID_BIRTH_DATE'), ('RESPONSAVEL_OBRIGATORIO', 'GUARDIAN_REQUIRED'), ('PROFESSOR_INEXISTENTE', 'TEACHER_NOT_FOUND'), ('DADOS_INVALIDOS', 'INVALID_DATA'), ('ERRO_INTERNO', 'INTERNAL_ERROR'), ('SERVICO_INDISPONIVEL', 'SERVICE_UNAVAILABLE'), ('VINCULO_DESFEITO', 'LINK_UNDONE'), ('PRAZO_VENCIDO', 'DEADLINE_EXPIRED'), ('SEM_CONFIRMACAO', 'NO_CONFIRMATION'), ('MENSAGEM_NAO_EDITAVEL', 'MESSAGE_NOT_EDITABLE'), ('DOCUMENTO_INDISPONIVEL', 'DOCUMENT_UNAVAILABLE'), ('CHAT_INDISPONIVEL', 'CHAT_UNAVAILABLE'), ('ERRO_TELEGRAM', 'TELEGRAM_ERROR'), ('ACAO_NAO_SUPORTADA', 'ACTION_NOT_SUPPORTED'), ('LIMITE_DE_TAXA', 'RATE_LIMITED')) AS changed(antigo, novo) WHERE target.reason_code = changed.antigo;
UPDATE processed_telegram_update AS target SET origin = changed.novo FROM (VALUES ('INTERNO', 'INTERNAL')) AS changed(antigo, novo) WHERE target.origin = changed.antigo;
UPDATE report_interaction AS target SET type = changed.novo FROM (VALUES ('RESPOSTA_PERGUNTAS', 'QUESTION_ANSWER'), ('ALTERACAO', 'CHANGE')) AS changed(antigo, novo) WHERE target.type = changed.antigo;
UPDATE report_interaction AS target SET result = changed.novo FROM (VALUES ('APLICADA', 'APPLIED'), ('SEM_ALTERACAO_DE_CONTEUDO', 'NO_CONTENT_CHANGE'), ('PEDIDO_DE_ACAO_IGNORADO', 'ACTION_REQUEST_IGNORED'), ('FALHOU', 'FAILED'), ('RECUSADA_VERSAO_DESATUALIZADA', 'REJECTED_STALE_VERSION')) AS changed(antigo, novo) WHERE target.result = changed.antigo;
UPDATE report_version AS target SET origin = changed.novo FROM (VALUES ('ESTRUTURACAO_INICIAL', 'INITIAL_STRUCTURING'), ('RESPOSTA_PERGUNTAS', 'QUESTION_ANSWER'), ('ALTERACAO', 'CHANGE'), ('CONSOLIDACAO', 'CONSOLIDATION')) AS changed(antigo, novo) WHERE target.origin = changed.antigo;
UPDATE report_version AS target SET prompt_version = changed.novo FROM (VALUES ('estruturacao-aula.v1', 'lesson-structuring.v1'), ('incorporacao-respostas.v1', 'answer-incorporation.v1'), ('revisao-aula.v1', 'lesson-revision.v1'), ('consolidacao-semestral.v1', 'semester-consolidation.v1'), ('revisao-semestral.v1', 'semester-revision.v1'), ('correcao-json.v1', 'json-correction.v1')) AS changed(antigo, novo) WHERE target.prompt_version = changed.antigo;
UPDATE ai_call AS target SET task = changed.novo FROM (VALUES ('TRANSCRICAO', 'TRANSCRIPTION'), ('ESTRUTURACAO', 'STRUCTURING'), ('INCORPORACAO_RESPOSTAS', 'ANSWER_INCORPORATION'), ('REVISAO', 'REVISION'), ('CONSOLIDACAO', 'CONSOLIDATION')) AS changed(antigo, novo) WHERE target.task = changed.antigo;
UPDATE lesson_report AS target SET failure_reason = changed.novo FROM (VALUES ('CODIGO_INVALIDO', 'INVALID_CODE'), ('CODIGO_EXPIRADO', 'EXPIRED_CODE'), ('BLOQUEADO_TEMPORARIAMENTE', 'TEMPORARILY_BLOCKED'), ('TELEGRAM_JA_VINCULADO', 'TELEGRAM_ALREADY_LINKED'), ('PROFESSOR_JA_VINCULADO', 'TEACHER_ALREADY_LINKED'), ('SESSAO_EXPIRADA', 'SESSION_EXPIRED'), ('NAO_AUTENTICADO', 'NOT_AUTHENTICATED'), ('ALUNO_NAO_ASSOCIADO', 'STUDENT_NOT_ASSIGNED'), ('RECURSO_NAO_ENCONTRADO', 'RESOURCE_NOT_FOUND'), ('PROCESSAMENTO_EM_ANDAMENTO', 'PROCESSING_IN_PROGRESS'), ('AUDIO_MUITO_GRANDE', 'AUDIO_TOO_LARGE'), ('FORMATO_NAO_SUPORTADO', 'UNSUPPORTED_FORMAT'), ('AUDIO_NAO_PROCESSAVEL', 'AUDIO_NOT_PROCESSABLE'), ('RESPOSTA_VAZIA', 'EMPTY_ANSWER'), ('ALTERACAO_VAZIA', 'EMPTY_CHANGE'), ('VERSAO_DESATUALIZADA', 'STALE_VERSION'), ('RELATORIO_JA_APROVADO', 'REPORT_ALREADY_APPROVED'), ('RELATORIO_CANCELADO', 'REPORT_CANCELLED'), ('ACAO_NAO_PERMITIDA_NA_ETAPA', 'ACTION_NOT_ALLOWED_AT_STEP'), ('RELATORIO_EM_ABERTO', 'REPORT_ALREADY_OPEN'), ('SEM_AULAS_APROVADAS_NO_PERIODO', 'NO_APPROVED_LESSONS_IN_PERIOD'), ('HA_RELATORIOS_EM_ABERTO', 'OPEN_REPORTS_EXIST'), ('SEMESTRAL_JA_APROVADO', 'SEMESTER_REPORT_ALREADY_APPROVED'), ('EMAIL_INVALIDO', 'INVALID_EMAIL'), ('EMAIL_DUPLICADO_NO_ARQUIVO', 'DUPLICATE_EMAIL_IN_FILE'), ('NOME_OBRIGATORIO', 'NAME_REQUIRED'), ('CPF_INVALIDO', 'INVALID_CPF'), ('CPF_DUPLICADO_NO_ARQUIVO', 'DUPLICATE_CPF_IN_FILE'), ('DATA_NASCIMENTO_INVALIDA', 'INVALID_BIRTH_DATE'), ('RESPONSAVEL_OBRIGATORIO', 'GUARDIAN_REQUIRED'), ('PROFESSOR_INEXISTENTE', 'TEACHER_NOT_FOUND'), ('DADOS_INVALIDOS', 'INVALID_DATA'), ('ERRO_INTERNO', 'INTERNAL_ERROR'), ('SERVICO_INDISPONIVEL', 'SERVICE_UNAVAILABLE'), ('SAIDA_INVALIDA', 'INVALID_OUTPUT'), ('PROVEDOR_INDISPONIVEL', 'PROVIDER_UNAVAILABLE'), ('AUDIO_SEM_FALA', 'AUDIO_NO_SPEECH'), ('AUDIO_FORMATO', 'AUDIO_FORMAT'), ('AUDIO_VAZIO', 'AUDIO_EMPTY')) AS changed(antigo, novo) WHERE target.failure_reason = changed.antigo;
UPDATE ai_call AS target SET error_code = changed.novo FROM (VALUES ('CODIGO_INVALIDO', 'INVALID_CODE'), ('CODIGO_EXPIRADO', 'EXPIRED_CODE'), ('BLOQUEADO_TEMPORARIAMENTE', 'TEMPORARILY_BLOCKED'), ('TELEGRAM_JA_VINCULADO', 'TELEGRAM_ALREADY_LINKED'), ('PROFESSOR_JA_VINCULADO', 'TEACHER_ALREADY_LINKED'), ('SESSAO_EXPIRADA', 'SESSION_EXPIRED'), ('NAO_AUTENTICADO', 'NOT_AUTHENTICATED'), ('ALUNO_NAO_ASSOCIADO', 'STUDENT_NOT_ASSIGNED'), ('RECURSO_NAO_ENCONTRADO', 'RESOURCE_NOT_FOUND'), ('PROCESSAMENTO_EM_ANDAMENTO', 'PROCESSING_IN_PROGRESS'), ('AUDIO_MUITO_GRANDE', 'AUDIO_TOO_LARGE'), ('FORMATO_NAO_SUPORTADO', 'UNSUPPORTED_FORMAT'), ('AUDIO_NAO_PROCESSAVEL', 'AUDIO_NOT_PROCESSABLE'), ('RESPOSTA_VAZIA', 'EMPTY_ANSWER'), ('ALTERACAO_VAZIA', 'EMPTY_CHANGE'), ('VERSAO_DESATUALIZADA', 'STALE_VERSION'), ('RELATORIO_JA_APROVADO', 'REPORT_ALREADY_APPROVED'), ('RELATORIO_CANCELADO', 'REPORT_CANCELLED'), ('ACAO_NAO_PERMITIDA_NA_ETAPA', 'ACTION_NOT_ALLOWED_AT_STEP'), ('RELATORIO_EM_ABERTO', 'REPORT_ALREADY_OPEN'), ('SEM_AULAS_APROVADAS_NO_PERIODO', 'NO_APPROVED_LESSONS_IN_PERIOD'), ('HA_RELATORIOS_EM_ABERTO', 'OPEN_REPORTS_EXIST'), ('SEMESTRAL_JA_APROVADO', 'SEMESTER_REPORT_ALREADY_APPROVED'), ('EMAIL_INVALIDO', 'INVALID_EMAIL'), ('EMAIL_DUPLICADO_NO_ARQUIVO', 'DUPLICATE_EMAIL_IN_FILE'), ('NOME_OBRIGATORIO', 'NAME_REQUIRED'), ('CPF_INVALIDO', 'INVALID_CPF'), ('CPF_DUPLICADO_NO_ARQUIVO', 'DUPLICATE_CPF_IN_FILE'), ('DATA_NASCIMENTO_INVALIDA', 'INVALID_BIRTH_DATE'), ('RESPONSAVEL_OBRIGATORIO', 'GUARDIAN_REQUIRED'), ('PROFESSOR_INEXISTENTE', 'TEACHER_NOT_FOUND'), ('DADOS_INVALIDOS', 'INVALID_DATA'), ('ERRO_INTERNO', 'INTERNAL_ERROR'), ('SERVICO_INDISPONIVEL', 'SERVICE_UNAVAILABLE'), ('SAIDA_INVALIDA', 'INVALID_OUTPUT'), ('PROVEDOR_INDISPONIVEL', 'PROVIDER_UNAVAILABLE'), ('AUDIO_SEM_FALA', 'AUDIO_NO_SPEECH'), ('AUDIO_FORMATO', 'AUDIO_FORMAT'), ('AUDIO_VAZIO', 'AUDIO_EMPTY')) AS changed(antigo, novo) WHERE target.error_code = changed.antigo;

-- 3. JSON do fio: chaves de `payload` e `compensation` pelo esquema novo das ações; valor de `type` e ação do botão (`data`)
--    pelos vocabulários novos. `text` e `caption` (texto do professor) nunca são lidos nem alterados.
CREATE FUNCTION pg_temp.rename_wire_json(document jsonb, names jsonb, types jsonb, actions jsonb) RETURNS jsonb
LANGUAGE sql IMMUTABLE AS $$
  SELECT CASE jsonb_typeof(document)
    WHEN 'object' THEN COALESCE((
      SELECT jsonb_object_agg(
        COALESCE(names ->> entry.key, entry.key),
        CASE
          WHEN entry.key = 'tipo' AND jsonb_typeof(entry.value) = 'string'
            THEN COALESCE(types -> (entry.value #>> '{}'), entry.value)
          WHEN entry.key = 'dados' AND jsonb_typeof(entry.value) = 'string' AND (entry.value #>> '{}') ~ '^v1:[a-z]+:'
            THEN to_jsonb('v1:' || COALESCE(actions ->> split_part(entry.value #>> '{}', ':', 2), split_part(entry.value #>> '{}', ':', 2))
              || substr(entry.value #>> '{}', length('v1:' || split_part(entry.value #>> '{}', ':', 2)) + 1))
          ELSE pg_temp.rename_wire_json(entry.value, names, types, actions)
        END)
        FROM jsonb_each(document) AS entry), '{}'::jsonb)
    WHEN 'array' THEN COALESCE((
      SELECT jsonb_agg(pg_temp.rename_wire_json(element.value, names, types, actions) ORDER BY element.position)
        FROM jsonb_array_elements(document) WITH ORDINALITY AS element(value, position)), '[]'::jsonb)
    ELSE document
  END
$$;

UPDATE channel_action
   SET payload = pg_temp.rename_wire_json(payload, '{"rotulo":"label","dados":"data","linhas":"rows","acaoId":"actionId","tipo":"type","texto":"text","teclado":"keyboard","mensagemId":"messageId","documentoId":"documentId","nomeArquivo":"fileName","tipoConteudo":"contentType","legenda":"caption","trabalhoId":"jobId","editarMensagemId":"editMessageId","versao":"version","loteId":"batchId","acoes":"actions","origem":"origin","criadoEm":"createdAt","tentativa":"attempt","situacao":"outcome","codigoMotivo":"reasonCode","resultados":"results","aceitas":"accepted","ignoradas":"ignored","estado":"state","codigo":"code"}'::jsonb, '{"MENSAGEM":"MESSAGE","EDICAO":"EDIT","DOCUMENTO":"DOCUMENT","RESPOSTA_CALLBACK":"CALLBACK_REPLY","MENSAGEM_ETAPA":"STEP_MESSAGE"}'::jsonb, '{"alt":"change","aluno":"student","aprov":"approve","aprovok":"approve-ok","canc":"cancel","cancok":"cancel-ok","confal":"confirm-student","cont":"continue","desc":"discard","descok":"discard-ok","foco":"focus","gerar":"generate","gerarsim":"generate-yes","hist":"history","hitem":"history-item","hmais":"history-more","inicio":"home","per":"period","reg":"register","sair":"sign-out","sairok":"sign-out-ok","sem":"semester","tent":"retry","trocal":"switch-student","voltar":"back","sim":"yes","opcoes":"options"}'::jsonb)
 WHERE payload IS NOT NULL;
UPDATE channel_action
   SET compensation = pg_temp.rename_wire_json(compensation, '{"rotulo":"label","dados":"data","linhas":"rows","acaoId":"actionId","tipo":"type","texto":"text","teclado":"keyboard","mensagemId":"messageId","documentoId":"documentId","nomeArquivo":"fileName","tipoConteudo":"contentType","legenda":"caption","trabalhoId":"jobId","editarMensagemId":"editMessageId","versao":"version","loteId":"batchId","acoes":"actions","origem":"origin","criadoEm":"createdAt","tentativa":"attempt","situacao":"outcome","codigoMotivo":"reasonCode","resultados":"results","aceitas":"accepted","ignoradas":"ignored","estado":"state","codigo":"code"}'::jsonb, '{"MENSAGEM":"MESSAGE","EDICAO":"EDIT","DOCUMENTO":"DOCUMENT","RESPOSTA_CALLBACK":"CALLBACK_REPLY","MENSAGEM_ETAPA":"STEP_MESSAGE"}'::jsonb, '{"alt":"change","aluno":"student","aprov":"approve","aprovok":"approve-ok","canc":"cancel","cancok":"cancel-ok","confal":"confirm-student","cont":"continue","desc":"discard","descok":"discard-ok","foco":"focus","gerar":"generate","gerarsim":"generate-yes","hist":"history","hitem":"history-item","hmais":"history-more","inicio":"home","per":"period","reg":"register","sair":"sign-out","sairok":"sign-out-ok","sem":"semester","tent":"retry","trocal":"switch-student","voltar":"back","sim":"yes","opcoes":"options"}'::jsonb)
 WHERE compensation IS NOT NULL;

-- confirmação pendente guardada em `flow_data`: o token é a ação de botão (`aprovar:<versão>`, `cancelar`, `sair`, `descartar`)
UPDATE telegram_link
   SET flow_data = jsonb_set(flow_data, '{confirmation}', to_jsonb(CASE
         WHEN flow_data ->> 'confirmation' LIKE 'aprovar:%' THEN 'approve:' || substr(flow_data ->> 'confirmation', 9)
         WHEN flow_data ->> 'confirmation' = 'cancelar' THEN 'cancel'
         WHEN flow_data ->> 'confirmation' = 'sair' THEN 'sign-out'
         WHEN flow_data ->> 'confirmation' = 'descartar' THEN 'discard'
         ELSE flow_data ->> 'confirmation' END))
 WHERE flow_data ? 'confirmation' AND jsonb_typeof(flow_data -> 'confirmation') = 'string';

-- 4. Objetos recriados, com os mesmos nomes e os literais novos.
CREATE INDEX ix_channel_action_delivery ON channel_action USING btree (next_attempt_at) WHERE state = 'PENDING';
CREATE INDEX ix_channel_action_live_chat ON channel_action USING btree (chat_id, seq) WHERE state IN ('PENDING', 'DELIVERED');
CREATE INDEX ix_lesson_report_approved ON lesson_report USING btree (teacher_id, student_id) WHERE status = 'APPROVED';
CREATE UNIQUE INDEX uk_lesson_report_open ON lesson_report USING btree (teacher_id, student_id) WHERE status IN ('PROCESSING', 'PROCESSING_FAILED', 'AWAITING_ANSWERS', 'AWAITING_REVIEW');
CREATE INDEX ix_processing_job_pending ON processing_job USING btree (created_at) WHERE status = 'PENDING';
CREATE UNIQUE INDEX uk_processing_job_registration_running ON processing_job USING btree (teacher_id, student_id) WHERE (type = 'LESSON_REGISTRATION' AND status IN ('PENDING', 'RUNNING'));
CREATE UNIQUE INDEX uk_processing_job_semester_consolidation_running ON processing_job USING btree (teacher_id, student_id, ((parameters ->> 'periodKey'::text))) WHERE (type = 'SEMESTER_CONSOLIDATION' AND status IN ('PENDING', 'RUNNING'));
CREATE UNIQUE INDEX uk_semester_report_approved ON semester_report USING btree (teacher_id, student_id, period_key) WHERE status = 'APPROVED';
CREATE UNIQUE INDEX uk_semester_report_open ON semester_report USING btree (teacher_id, student_id, period_key) WHERE status = 'AWAITING_REVIEW';
ALTER TABLE channel_action ADD CONSTRAINT ck_channel_action_origin CHECK (origin IN ('UPDATE_REPLY', 'PROACTIVE', 'COMPENSATION'));
ALTER TABLE channel_action ADD CONSTRAINT ck_channel_action_state CHECK (state IN ('PENDING', 'DELIVERED', 'EXECUTED', 'FAILED', 'PERMANENTLY_FAILED', 'DISCARDED'));
ALTER TABLE channel_action ADD CONSTRAINT ck_channel_action_type CHECK (type IN ('MESSAGE', 'EDIT', 'DOCUMENT', 'CALLBACK_REPLY', 'STEP_MESSAGE'));
ALTER TABLE channel_action ADD CONSTRAINT ck_channel_action_update_reply CHECK ((origin <> 'UPDATE_REPLY' OR (update_id IS NOT NULL)));
ALTER TABLE channel_document ADD CONSTRAINT ck_channel_document_type CHECK (report_type IN ('LESSON', 'SEMESTER'));
ALTER TABLE processed_telegram_update ADD CONSTRAINT ck_processed_telegram_update_origin CHECK (((origin IS NULL) OR origin = 'INTERNAL'));

-- 5. Rede de segurança: nenhum valor fora do conjunto novo em coluna de conjunto fechado, e cada valor cabe na coluna.
DO $$
DECLARE
  found bigint;
BEGIN
  SELECT count(*) INTO found FROM ai_call WHERE report_type IS NOT NULL AND report_type NOT IN ('LESSON', 'SEMESTER');
  IF found > 0 THEN RAISE EXCEPTION 'V8: % valor(es) fora do conjunto novo em ai_call.report_type', found; END IF;
  SELECT count(*) INTO found FROM channel_document WHERE report_type IS NOT NULL AND report_type NOT IN ('LESSON', 'SEMESTER');
  IF found > 0 THEN RAISE EXCEPTION 'V8: % valor(es) fora do conjunto novo em channel_document.report_type', found; END IF;
  SELECT count(*) INTO found FROM report_interaction WHERE report_type IS NOT NULL AND report_type NOT IN ('LESSON', 'SEMESTER');
  IF found > 0 THEN RAISE EXCEPTION 'V8: % valor(es) fora do conjunto novo em report_interaction.report_type', found; END IF;
  SELECT count(*) INTO found FROM processing_job WHERE report_type IS NOT NULL AND report_type NOT IN ('LESSON', 'SEMESTER');
  IF found > 0 THEN RAISE EXCEPTION 'V8: % valor(es) fora do conjunto novo em processing_job.report_type', found; END IF;
  SELECT count(*) INTO found FROM report_version WHERE report_type IS NOT NULL AND report_type NOT IN ('LESSON', 'SEMESTER');
  IF found > 0 THEN RAISE EXCEPTION 'V8: % valor(es) fora do conjunto novo em report_version.report_type', found; END IF;
  SELECT count(*) INTO found FROM telegram_link WHERE focused_report_type IS NOT NULL AND focused_report_type NOT IN ('LESSON', 'SEMESTER');
  IF found > 0 THEN RAISE EXCEPTION 'V8: % valor(es) fora do conjunto novo em telegram_link.focused_report_type', found; END IF;
  SELECT count(*) INTO found FROM pdf_document WHERE type IS NOT NULL AND type NOT IN ('REVIEW', 'OFFICIAL');
  IF found > 0 THEN RAISE EXCEPTION 'V8: % valor(es) fora do conjunto novo em pdf_document.type', found; END IF;
  SELECT count(*) INTO found FROM lesson_report WHERE status IS NOT NULL AND status NOT IN ('PROCESSING', 'PROCESSING_FAILED', 'AWAITING_ANSWERS', 'AWAITING_REVIEW', 'APPROVED', 'CANCELLED');
  IF found > 0 THEN RAISE EXCEPTION 'V8: % valor(es) fora do conjunto novo em lesson_report.status', found; END IF;
  SELECT count(*) INTO found FROM semester_report WHERE status IS NOT NULL AND status NOT IN ('PROCESSING', 'PROCESSING_FAILED', 'AWAITING_ANSWERS', 'AWAITING_REVIEW', 'APPROVED', 'CANCELLED');
  IF found > 0 THEN RAISE EXCEPTION 'V8: % valor(es) fora do conjunto novo em semester_report.status', found; END IF;
  SELECT count(*) INTO found FROM processing_job WHERE status IS NOT NULL AND status NOT IN ('PENDING', 'RUNNING', 'COMPLETED', 'FAILED', 'DISCARDED');
  IF found > 0 THEN RAISE EXCEPTION 'V8: % valor(es) fora do conjunto novo em processing_job.status', found; END IF;
  SELECT count(*) INTO found FROM processing_job WHERE type IS NOT NULL AND type NOT IN ('LESSON_REGISTRATION', 'RETRY', 'QUESTION_ANSWER', 'CHANGE', 'SEMESTER_CONSOLIDATION');
  IF found > 0 THEN RAISE EXCEPTION 'V8: % valor(es) fora do conjunto novo em processing_job.type', found; END IF;
  SELECT count(*) INTO found FROM processing_job WHERE step IS NOT NULL AND step NOT IN ('UPLOADING', 'TRANSCRIBING', 'STRUCTURING', 'INCORPORATING_ANSWERS', 'TRANSCRIBING_REQUEST', 'APPLYING_CHANGE', 'CONSOLIDATING', 'GENERATING_PDF', 'READY');
  IF found > 0 THEN RAISE EXCEPTION 'V8: % valor(es) fora do conjunto novo em processing_job.step', found; END IF;
  SELECT count(*) INTO found FROM processing_job WHERE result_code IS NOT NULL AND result_code NOT IN ('READY_FOR_REVIEW', 'PENDING_QUESTIONS', 'NEW_VERSION', 'NO_CONTENT_CHANGE', 'ACTION_REQUEST_IGNORED', 'STALE_VERSION', 'AUDIO_NOT_PROCESSABLE', 'STRUCTURING_FAILED', 'CHANGE_FAILED', 'ANSWER_FAILED', 'CONSOLIDATION_FAILED', 'DISCARDED');
  IF found > 0 THEN RAISE EXCEPTION 'V8: % valor(es) fora do conjunto novo em processing_job.result_code', found; END IF;
  SELECT count(*) INTO found FROM lesson_report WHERE processing_step IS NOT NULL AND processing_step NOT IN ('UPLOADING', 'TRANSCRIBING', 'STRUCTURING', 'INCORPORATING_ANSWERS', 'TRANSCRIBING_REQUEST', 'APPLYING_CHANGE', 'CONSOLIDATING', 'GENERATING_PDF', 'READY');
  IF found > 0 THEN RAISE EXCEPTION 'V8: % valor(es) fora do conjunto novo em lesson_report.processing_step', found; END IF;
  SELECT count(*) INTO found FROM semester_report WHERE processing_step IS NOT NULL AND processing_step NOT IN ('UPLOADING', 'TRANSCRIBING', 'STRUCTURING', 'INCORPORATING_ANSWERS', 'TRANSCRIBING_REQUEST', 'APPLYING_CHANGE', 'CONSOLIDATING', 'GENERATING_PDF', 'READY');
  IF found > 0 THEN RAISE EXCEPTION 'V8: % valor(es) fora do conjunto novo em semester_report.processing_step', found; END IF;
  SELECT count(*) INTO found FROM processing_job WHERE medium IS NOT NULL AND medium NOT IN ('TEXT', 'AUDIO');
  IF found > 0 THEN RAISE EXCEPTION 'V8: % valor(es) fora do conjunto novo em processing_job.medium', found; END IF;
  SELECT count(*) INTO found FROM report_interaction WHERE medium IS NOT NULL AND medium NOT IN ('TEXT', 'AUDIO');
  IF found > 0 THEN RAISE EXCEPTION 'V8: % valor(es) fora do conjunto novo em report_interaction.medium', found; END IF;
  SELECT count(*) INTO found FROM telegram_link WHERE flow IS NOT NULL AND flow NOT IN ('NONE', 'LESSON_REGISTRATION', 'SEMESTER', 'HISTORY');
  IF found > 0 THEN RAISE EXCEPTION 'V8: % valor(es) fora do conjunto novo em telegram_link.flow', found; END IF;
  SELECT count(*) INTO found FROM telegram_link WHERE flow_step IS NOT NULL AND flow_step NOT IN ('NONE', 'CHOOSING_STUDENT', 'AWAITING_AUDIO', 'CONFIRMING_AUDIO_STUDENT', 'OPEN_REPORT_NOTICE', 'CHOOSING_SEMESTER', 'CONFIRMING_SEMESTER_GENERATION', 'CHOOSING_HISTORY_ITEM');
  IF found > 0 THEN RAISE EXCEPTION 'V8: % valor(es) fora do conjunto novo em telegram_link.flow_step', found; END IF;
  SELECT count(*) INTO found FROM channel_action WHERE state IS NOT NULL AND state NOT IN ('PENDING', 'DELIVERED', 'EXECUTED', 'FAILED', 'PERMANENTLY_FAILED', 'DISCARDED');
  IF found > 0 THEN RAISE EXCEPTION 'V8: % valor(es) fora do conjunto novo em channel_action.state', found; END IF;
  SELECT count(*) INTO found FROM channel_action WHERE type IS NOT NULL AND type NOT IN ('MESSAGE', 'EDIT', 'DOCUMENT', 'CALLBACK_REPLY', 'STEP_MESSAGE');
  IF found > 0 THEN RAISE EXCEPTION 'V8: % valor(es) fora do conjunto novo em channel_action.type', found; END IF;
  SELECT count(*) INTO found FROM channel_action WHERE origin IS NOT NULL AND origin NOT IN ('UPDATE_REPLY', 'PROACTIVE', 'COMPENSATION');
  IF found > 0 THEN RAISE EXCEPTION 'V8: % valor(es) fora do conjunto novo em channel_action.origin', found; END IF;
  SELECT count(*) INTO found FROM processed_telegram_update WHERE origin IS NOT NULL AND origin NOT IN ('INTERNAL');
  IF found > 0 THEN RAISE EXCEPTION 'V8: % valor(es) fora do conjunto novo em processed_telegram_update.origin', found; END IF;
  SELECT count(*) INTO found FROM report_interaction WHERE type IS NOT NULL AND type NOT IN ('QUESTION_ANSWER', 'CHANGE');
  IF found > 0 THEN RAISE EXCEPTION 'V8: % valor(es) fora do conjunto novo em report_interaction.type', found; END IF;
  SELECT count(*) INTO found FROM report_interaction WHERE result IS NOT NULL AND result NOT IN ('APPLIED', 'NO_CONTENT_CHANGE', 'ACTION_REQUEST_IGNORED', 'FAILED', 'REJECTED_STALE_VERSION');
  IF found > 0 THEN RAISE EXCEPTION 'V8: % valor(es) fora do conjunto novo em report_interaction.result', found; END IF;
  SELECT count(*) INTO found FROM report_version WHERE origin IS NOT NULL AND origin NOT IN ('INITIAL_STRUCTURING', 'QUESTION_ANSWER', 'CHANGE', 'CONSOLIDATION');
  IF found > 0 THEN RAISE EXCEPTION 'V8: % valor(es) fora do conjunto novo em report_version.origin', found; END IF;
  SELECT count(*) INTO found FROM report_version WHERE prompt_version IS NOT NULL AND prompt_version NOT IN ('lesson-structuring.v1', 'answer-incorporation.v1', 'lesson-revision.v1', 'semester-consolidation.v1', 'semester-revision.v1', 'json-correction.v1');
  IF found > 0 THEN RAISE EXCEPTION 'V8: % valor(es) fora do conjunto novo em report_version.prompt_version', found; END IF;
  SELECT count(*) INTO found FROM ai_call WHERE task IS NOT NULL AND task NOT IN ('TRANSCRIPTION', 'STRUCTURING', 'ANSWER_INCORPORATION', 'REVISION', 'CONSOLIDATION');
  IF found > 0 THEN RAISE EXCEPTION 'V8: % valor(es) fora do conjunto novo em ai_call.task', found; END IF;
END $$;

