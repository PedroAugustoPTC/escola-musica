-- Feature 007 (privacidade, retenção e operação; data-model.md): migração ADITIVA. Não apaga linha existente;
-- afrouxa NOT NULL só onde a retenção e a eliminação precisam remover valor. Seções:
--   1. alterações em tabelas existentes (aluno, relatórios, chamadas de IA, trilha de autoria)
--   2. tabelas novas (âncora da trilha, consentimento, eliminação, retenção, alertas)
--   3. gatilhos de só acréscimo e função de expurgo da trilha
--   4. papel de banco da aplicação (reports_app)

-- ---------------------------------------------------------------------------------------------------------
-- 1.1 student (eliminação, FR-022): os dados pessoais ficam nulos só no aluno eliminado.
-- ---------------------------------------------------------------------------------------------------------
ALTER TABLE student
    ALTER COLUMN name DROP NOT NULL,
    ALTER COLUMN birth_date DROP NOT NULL,
    ALTER COLUMN cpf_encrypted DROP NOT NULL,
    ALTER COLUMN cpf_hash DROP NOT NULL,
    ADD COLUMN erased_at timestamptz;

ALTER TABLE student
    ADD CONSTRAINT ck_student_erased CHECK (
        (erased_at IS NULL
            AND name IS NOT NULL AND birth_date IS NOT NULL
            AND cpf_encrypted IS NOT NULL AND cpf_hash IS NOT NULL)
        OR
        (erased_at IS NOT NULL
            AND name IS NULL AND birth_date IS NULL AND cpf_encrypted IS NULL AND cpf_hash IS NULL
            AND guardian_name IS NULL AND guardian_phone IS NULL AND guardian_email IS NULL
            AND instrument IS NULL AND active = false));

-- ---------------------------------------------------------------------------------------------------------
-- 1.2 Relatórios, interações, versões e PDFs (retenção e eliminação).
-- ---------------------------------------------------------------------------------------------------------
ALTER TABLE lesson_report
    ALTER COLUMN transcription DROP NOT NULL,
    ADD COLUMN source_removed_at timestamptz,
    ADD COLUMN anonymized_at timestamptz;

ALTER TABLE semester_report
    ALTER COLUMN general_information DROP NOT NULL,
    ADD COLUMN source_removed_at timestamptz,
    ADD COLUMN anonymized_at timestamptz;

-- general_information faz parte do semestral oficial: só fica nulo quando o relatório foi anonimizado.
ALTER TABLE semester_report
    ADD CONSTRAINT ck_semester_report_general_information
        CHECK (general_information IS NOT NULL OR anonymized_at IS NOT NULL);

ALTER TABLE report_interaction
    ALTER COLUMN text DROP NOT NULL,
    ADD COLUMN text_removed_at timestamptz;

ALTER TABLE report_version ADD COLUMN content_removed_at timestamptz;
ALTER TABLE pdf_document ADD COLUMN content_removed_at timestamptz;

-- Candidatos da rotina de retenção (lotes por id; só o que ainda não foi tratado).
CREATE INDEX ix_lesson_report_retention ON lesson_report (approved_at, cancelled_at)
    WHERE source_removed_at IS NULL OR anonymized_at IS NULL;
CREATE INDEX ix_semester_report_retention ON semester_report (approved_at, cancelled_at)
    WHERE source_removed_at IS NULL OR anonymized_at IS NULL;
CREATE INDEX ix_pdf_document_removal ON pdf_document (content_removed_at) WHERE content_removed_at IS NOT NULL;

-- ---------------------------------------------------------------------------------------------------------
-- 1.3 ai_call (métricas, FR-051): canal da chamada, herdado do trabalho de processamento.
-- ---------------------------------------------------------------------------------------------------------
ALTER TABLE ai_call ADD COLUMN channel varchar(12);
UPDATE ai_call SET channel = processing_job.request_channel
    FROM processing_job WHERE processing_job.id = ai_call.job_id;
ALTER TABLE ai_call
    ALTER COLUMN channel SET NOT NULL,
    ADD CONSTRAINT ck_ai_call_channel CHECK (channel IN ('WEB', 'TELEGRAM'));
CREATE INDEX ix_ai_call_created ON ai_call (created_at);
CREATE INDEX ix_ai_call_provider_recent ON ai_call (provider_model, created_at DESC);

-- ---------------------------------------------------------------------------------------------------------
-- 1.4 authorship_record -> trilha única encadeada (FR-040 a FR-046).
-- ---------------------------------------------------------------------------------------------------------
ALTER TABLE authorship_record
    ADD COLUMN sequence        bigint,
    ADD COLUMN previous_hash   varchar(64),
    ADD COLUMN record_hash     varchar(64),
    ADD COLUMN item_count      integer,
    ADD COLUMN filter_operation varchar(48),
    ADD COLUMN filter_actor_id  uuid,
    ADD COLUMN filter_target_id uuid;

-- A migração é a única exceção aos gatilhos de só acréscimo: desliga e religa dentro desta transação.
ALTER TABLE authorship_record DISABLE TRIGGER tr_authorship_record_no_change;

UPDATE authorship_record AS target
    SET sequence = ordered.position
    FROM (SELECT id, row_number() OVER (ORDER BY occurred_at, id) AS position FROM authorship_record) AS ordered
    WHERE target.id = ordered.id;

-- Hashes das linhas antigas com a MESMA forma canônica do núcleo (AuditChain): campos em ordem fixa separados
-- por '|', NULL como texto vazio, data em ISO-8601 UTC com microssegundos.
DO
$$
DECLARE
    item     record;
    previous text := repeat('0', 64);
    current_hash text;
    canonical text;
BEGIN
    FOR item IN SELECT * FROM authorship_record ORDER BY sequence
        LOOP
            canonical := item.sequence::text
                || '|' || to_char(item.occurred_at AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS.US"Z"')
                || '|' || coalesce(item.author_id::text, '')
                || '|' || coalesce(item.author_role, '')
                || '|' || item.origin
                || '|' || item.operation
                || '|' || coalesce(item.target_type, '')
                || '|' || coalesce(item.target_id::text, '')
                || '|' || item.result
                || '|' || coalesce(item.error_code, '')
                || '|' || coalesce(item.origin_hash, '')
                || '|' || item.correlation_id
                || '|' || coalesce(item.filter_teacher_id::text, '')
                || '|' || coalesce(item.filter_report_type, '')
                || '|' || coalesce(item.filter_period_start::text, '')
                || '|' || coalesce(item.filter_period_end::text, '')
                || '|' || coalesce(item.filter_operation, '')
                || '|' || coalesce(item.filter_actor_id::text, '')
                || '|' || coalesce(item.filter_target_id::text, '')
                || '|' || coalesce(item.page_number::text, '')
                || '|' || coalesce(item.document_version::text, '')
                || '|' || coalesce(item.item_count::text, '');
            current_hash := encode(sha256(convert_to(previous || '|' || canonical, 'UTF8')), 'hex');
            UPDATE authorship_record SET previous_hash = previous, record_hash = current_hash WHERE id = item.id;
            previous := current_hash;
        END LOOP;
END
$$;

ALTER TABLE authorship_record ENABLE TRIGGER tr_authorship_record_no_change;

-- A sequência passa a ser gerada pelo banco, a partir do maior valor já atribuído.
ALTER TABLE authorship_record
    ALTER COLUMN sequence SET NOT NULL,
    ALTER COLUMN previous_hash SET NOT NULL,
    ALTER COLUMN record_hash SET NOT NULL,
    ALTER COLUMN sequence ADD GENERATED BY DEFAULT AS IDENTITY;
DO
$$
DECLARE
    highest bigint;
BEGIN
    SELECT coalesce(max(sequence), 0) + 1 INTO highest FROM authorship_record;
    EXECUTE format('ALTER TABLE authorship_record ALTER COLUMN sequence RESTART WITH %s', highest);
END
$$;

ALTER TABLE authorship_record
    ADD CONSTRAINT uk_authorship_record_sequence UNIQUE (sequence),
    ADD CONSTRAINT ck_authorship_record_previous_hash CHECK (previous_hash ~ '^[0-9a-f]{64}$'),
    ADD CONSTRAINT ck_authorship_record_record_hash CHECK (record_hash ~ '^[0-9a-f]{64}$'),
    ADD CONSTRAINT ck_authorship_record_item_count CHECK (item_count IS NULL OR item_count >= 0);

-- Novas origens (rotinas agendadas) e novos tipos de alvo.
ALTER TABLE authorship_record DROP CONSTRAINT ck_authorship_record_origin;
ALTER TABLE authorship_record
    ADD CONSTRAINT ck_authorship_record_origin
        CHECK (origin IN ('WEB', 'TELEGRAM', 'SYSTEM_STARTUP', 'INITIAL_LOAD', 'RETENTION', 'ALERT_EVALUATION'));

ALTER TABLE authorship_record DROP CONSTRAINT ck_authorship_record_target_type;
ALTER TABLE authorship_record
    ADD CONSTRAINT ck_authorship_record_target_type
        CHECK (target_type IS NULL OR target_type IN ('TEACHER', 'ADMINISTRATOR', 'STUDENT', 'ASSIGNMENT',
                                                      'LESSON_REPORT', 'SEMESTER_REPORT', 'CONSENT',
                                                      'CONSENT_TERM', 'ERASURE_REQUEST', 'RETENTION_RUN',
                                                      'AUDIT_TRAIL', 'ALERT'));

-- Detalhe tipado (nunca nome, CPF nem conteúdo): só nas operações de leitura, aprovação, consulta da trilha e
-- registro de consentimento listadas em audit-events.md. Superconjunto da V16: as leituras da 006 seguem aceitas.
ALTER TABLE authorship_record DROP CONSTRAINT ck_authorship_record_read_detail_operation;
ALTER TABLE authorship_record
    ADD CONSTRAINT ck_authorship_record_read_detail_operation
        CHECK ((filter_teacher_id IS NULL AND filter_report_type IS NULL AND filter_period_start IS NULL
                AND filter_period_end IS NULL AND page_number IS NULL AND document_version IS NULL
                AND filter_operation IS NULL AND filter_actor_id IS NULL AND filter_target_id IS NULL)
            OR operation IN ('SCHOOL_HISTORY_QUERY', 'SCHOOL_REPORT_DOCUMENT_OPEN',
                             'SCHOOL_REPORT_DOCUMENT_DOWNLOAD', 'HISTORY_QUERY', 'REPORT_DOCUMENT_OPEN',
                             'REPORT_DOCUMENT_DOWNLOAD', 'REPORT_DOCUMENT_SENT', 'LESSON_REPORT_APPROVE',
                             'LESSON_REPORT_CANCEL', 'SEMESTER_REPORT_APPROVE', 'SEMESTER_REPORT_CANCEL',
                             'AUDIT_TRAIL_QUERY', 'CONSENT_RECORD')),
    ADD CONSTRAINT ck_authorship_record_item_count_operation
        CHECK (item_count IS NULL
            OR operation IN ('RETENTION_RUN', 'ERASURE_COMPLETED', 'AUDIT_TRAIL_PURGED', 'AUDIT_TRAIL_VERIFY')),
    ADD CONSTRAINT ck_authorship_record_filter_operation_format
        CHECK (filter_operation IS NULL OR filter_operation ~ '^[A-Z][A-Z0-9_]{0,47}$');

-- A consulta de 90 dias por período, autor, alvo e operação.
CREATE INDEX ix_authorship_record_operation ON authorship_record (operation, occurred_at);

-- ---------------------------------------------------------------------------------------------------------
-- 2. Tabelas novas.
-- ---------------------------------------------------------------------------------------------------------

-- 2.1 Âncora da trilha: o último hash removido pelo expurgo; a verificação parte da âncora mais recente.
CREATE TABLE audit_chain_anchor
(
    id                    uuid PRIMARY KEY,
    purged_until_sequence bigint      NOT NULL,
    last_record_hash      varchar(64) NOT NULL,
    purged_at             timestamptz NOT NULL,
    purged_count          integer     NOT NULL,
    CONSTRAINT ck_audit_chain_anchor_hash CHECK (last_record_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_audit_chain_anchor_count CHECK (purged_count >= 1)
);

-- 2.2 Versões do termo de consentimento (FR-032): imutáveis; vigente = maior número.
CREATE TABLE consent_term_version
(
    id                   uuid PRIMARY KEY,
    number               integer     NOT NULL,
    published_on         date        NOT NULL,
    text                 text        NOT NULL,
    requires_new_consent boolean     NOT NULL,
    created_by           uuid        NOT NULL,
    created_at           timestamptz NOT NULL,
    CONSTRAINT uk_consent_term_version_number UNIQUE (number),
    CONSTRAINT ck_consent_term_version_number CHECK (number >= 1),
    CONSTRAINT ck_consent_term_version_text CHECK (char_length(text) BETWEEN 1 AND 20000)
);

-- 2.3 Consentimento do responsável (FR-030 a FR-033). Cada registro é novo; o anterior fica (histórico).
CREATE TABLE student_consent
(
    id                     uuid PRIMARY KEY,
    student_id             uuid         NOT NULL REFERENCES student (id),
    consenting_name        varchar(255),
    relationship           varchar(16)  NOT NULL,
    consented_on           date         NOT NULL,
    term_version_id        uuid         NOT NULL REFERENCES consent_term_version (id),
    collection_method      varchar(16)  NOT NULL,
    recorded_by_id         uuid,
    recorded_by_role       varchar(16),
    recorded_origin        varchar(24)  NOT NULL,
    recorded_at            timestamptz  NOT NULL,
    revoked_on             date,
    revoked_by_role        varchar(16),
    revocation_recorded_by uuid,
    CONSTRAINT ck_student_consent_relationship
        CHECK (relationship IN ('MOTHER', 'FATHER', 'LEGAL_GUARDIAN', 'OTHER')),
    CONSTRAINT ck_student_consent_method CHECK (collection_method IN ('SIGNED_PAPER', 'DIGITAL', 'OTHER')),
    CONSTRAINT ck_student_consent_recorded_role
        CHECK (recorded_by_role IS NULL OR recorded_by_role IN ('ADMINISTRATOR', 'TEACHER')),
    CONSTRAINT ck_student_consent_origin CHECK (recorded_origin IN ('WEB', 'TELEGRAM', 'INITIAL_LOAD')),
    CONSTRAINT ck_student_consent_revoked_role
        CHECK (revoked_by_role IS NULL OR revoked_by_role IN ('MOTHER', 'FATHER', 'LEGAL_GUARDIAN', 'OTHER',
                                                              'STUDENT')),
    CONSTRAINT ck_student_consent_revocation
        CHECK ((revoked_on IS NULL AND revoked_by_role IS NULL AND revocation_recorded_by IS NULL)
            OR (revoked_on IS NOT NULL AND revoked_on >= consented_on))
);
CREATE INDEX ix_student_consent_student ON student_consent (student_id, recorded_at DESC);

-- 2.4 Pedido de eliminação (FR-020 a FR-025).
CREATE TABLE erasure_request
(
    id                      uuid PRIMARY KEY,
    student_id              uuid        NOT NULL REFERENCES student (id),
    requester_role          varchar(16) NOT NULL,
    requester_name          varchar(255),
    requested_on            date        NOT NULL,
    received_via            varchar(16) NOT NULL,
    protocol_reference      varchar(40) NOT NULL,
    status                  varchar(16) NOT NULL,
    registered_by           uuid        NOT NULL,
    confirmation_token_hash varchar(64),
    confirmation_expires_at timestamptz,
    confirmed_by            uuid,
    confirmed_at            timestamptz,
    completed_at            timestamptz,
    scope_lesson_reports    integer,
    scope_semester_reports  integer,
    scope_pdfs              integer,
    scope_assignments       integer,
    scope_consents          integer,
    created_at              timestamptz NOT NULL,
    lock_version            bigint      NOT NULL,
    CONSTRAINT ck_erasure_request_role
        CHECK (requester_role IN ('STUDENT', 'MOTHER', 'FATHER', 'LEGAL_GUARDIAN', 'OTHER')),
    CONSTRAINT ck_erasure_request_via CHECK (received_via IN ('IN_PERSON', 'EMAIL', 'PHONE', 'LETTER', 'OTHER')),
    CONSTRAINT ck_erasure_request_status CHECK (status IN ('RECEIVED', 'IN_PROGRESS', 'COMPLETED')),
    CONSTRAINT ck_erasure_request_protocol CHECK (protocol_reference ~ '^[A-Za-z0-9./-]{1,40}$'),
    CONSTRAINT ck_erasure_request_token CHECK (confirmation_token_hash IS NULL
        OR confirmation_token_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_erasure_request_name_after_completion
        CHECK (status <> 'COMPLETED' OR requester_name IS NULL)
);
-- Um pedido não concluído por aluno.
CREATE UNIQUE INDEX uk_erasure_request_open_student ON erasure_request (student_id) WHERE status <> 'COMPLETED';

-- 2.5 Alunos eliminados: base para reaplicar as eliminações depois de uma restauração (FR-026).
CREATE TABLE erased_student
(
    student_id uuid PRIMARY KEY,
    erased_at  timestamptz NOT NULL
);

-- 2.6 Execuções da retenção (FR-015) e os itens tratados, sem conteúdo.
CREATE TABLE retention_run
(
    id              uuid PRIMARY KEY,
    started_at      timestamptz NOT NULL,
    finished_at     timestamptz,
    status          varchar(12) NOT NULL,
    error_code      varchar(40),
    policy_snapshot jsonb       NOT NULL,
    CONSTRAINT ck_retention_run_status CHECK (status IN ('RUNNING', 'SUCCEEDED', 'FAILED', 'INTERRUPTED')),
    CONSTRAINT ck_retention_run_error_code CHECK (error_code IS NULL OR error_code ~ '^[A-Z][A-Z0-9_]{0,39}$')
);
CREATE UNIQUE INDEX uk_retention_run_running ON retention_run ((true)) WHERE status = 'RUNNING';
CREATE INDEX ix_retention_run_started ON retention_run (started_at);

CREATE TABLE retention_run_item
(
    id        uuid PRIMARY KEY,
    run_id    uuid        NOT NULL REFERENCES retention_run (id) ON DELETE CASCADE,
    item_type varchar(32) NOT NULL,
    target_id uuid,
    count     integer     NOT NULL,
    CONSTRAINT ck_retention_run_item_type CHECK (item_type IN (
        'LESSON_SOURCE', 'SEMESTER_SOURCE', 'UNAPPROVED_PDF', 'CANCELLED_LESSON_REPORT',
        'CANCELLED_SEMESTER_REPORT', 'PDF_FILE', 'AUDIT_EVENTS', 'ALERT_HISTORY', 'RUN_HISTORY')),
    CONSTRAINT ck_retention_run_item_count CHECK (count >= 0)
);
CREATE INDEX ix_retention_run_item_run ON retention_run_item (run_id);

-- 2.7 Alertas operacionais (FR-055, FR-056): sem identificador de aluno e sem texto livre.
CREATE TABLE operational_alert
(
    id            uuid PRIMARY KEY,
    type          varchar(32) NOT NULL,
    component     varchar(64) NOT NULL,
    detail_code   varchar(40) NOT NULL,
    status        varchar(12) NOT NULL,
    started_at    timestamptz NOT NULL,
    last_seen_at  timestamptz NOT NULL,
    normalized_at timestamptz,
    occurrences   integer     NOT NULL,
    CONSTRAINT ck_operational_alert_type CHECK (type IN (
        'AI_PROVIDER_FAILING', 'CHANNEL_QUEUE_BACKLOG', 'STORAGE_FAILED', 'RETENTION_FAILED',
        'AUDIT_CHAIN_BROKEN')),
    CONSTRAINT ck_operational_alert_component CHECK (component ~ '^[a-z0-9._-]{1,64}$'),
    CONSTRAINT ck_operational_alert_detail CHECK (detail_code ~ '^[A-Z][A-Z0-9_]{0,39}$'),
    CONSTRAINT ck_operational_alert_status CHECK (status IN ('ACTIVE', 'NORMALIZED')),
    CONSTRAINT ck_operational_alert_occurrences CHECK (occurrences >= 1),
    CONSTRAINT ck_operational_alert_normalized
        CHECK ((status = 'ACTIVE' AND normalized_at IS NULL) OR (status = 'NORMALIZED' AND normalized_at IS NOT NULL))
);
-- Um alerta ativo por condição.
CREATE UNIQUE INDEX uk_operational_alert_active ON operational_alert (type, component) WHERE status = 'ACTIVE';
CREATE INDEX ix_operational_alert_seen ON operational_alert (last_seen_at);

-- Cadastro guiado do Telegram: o consentimento coletado fica no rascunho até a confirmação (sem nome em log).
ALTER TABLE student_registration_draft ADD COLUMN consent jsonb;

-- ---------------------------------------------------------------------------------------------------------
-- 3. Gatilhos de só acréscimo e expurgo da trilha.
-- ---------------------------------------------------------------------------------------------------------

-- UPDATE e TRUNCATE seguem recusados SEMPRE; DELETE só passa quando purge_authorship_record define a chave local.
DROP TRIGGER tr_authorship_record_no_change ON authorship_record;
CREATE FUNCTION authorship_record_guard() RETURNS trigger
    LANGUAGE plpgsql AS
$$
BEGIN
    IF TG_OP = 'DELETE' AND current_setting('reports.audit_purge', true) = 'on' THEN
        RETURN NULL;
    END IF;
    RAISE EXCEPTION 'authorship_record é somente de acréscimo (%)', TG_OP
        USING ERRCODE = 'integrity_constraint_violation';
END;
$$;
CREATE TRIGGER tr_authorship_record_no_change
    BEFORE UPDATE OR DELETE ON authorship_record
    FOR EACH STATEMENT EXECUTE FUNCTION authorship_record_guard();

-- As tabelas de acréscimo novas usam a função recusa-tudo da V10.
CREATE TRIGGER tr_audit_chain_anchor_no_change
    BEFORE UPDATE OR DELETE ON audit_chain_anchor
    FOR EACH STATEMENT EXECUTE FUNCTION authorship_record_append_only();
CREATE TRIGGER tr_audit_chain_anchor_no_truncate
    BEFORE TRUNCATE ON audit_chain_anchor
    FOR EACH STATEMENT EXECUTE FUNCTION authorship_record_append_only();
CREATE TRIGGER tr_erased_student_no_change
    BEFORE UPDATE OR DELETE ON erased_student
    FOR EACH STATEMENT EXECUTE FUNCTION authorship_record_append_only();
CREATE TRIGGER tr_erased_student_no_truncate
    BEFORE TRUNCATE ON erased_student
    FOR EACH STATEMENT EXECUTE FUNCTION authorship_record_append_only();

-- Expurgo: remove o prefixo da cadeia anterior ao corte e grava a âncora, na mesma transação. O piso de 365 dias
-- vale no banco, qualquer que seja a configuração da aplicação (FR-043).
CREATE FUNCTION purge_authorship_record(cutoff timestamptz) RETURNS integer
    LANGUAGE plpgsql
    SECURITY DEFINER
    SET search_path FROM CURRENT AS
$$
DECLARE
    last_sequence bigint;
    last_hash     varchar(64);
    removed       integer;
BEGIN
    IF cutoff > now() - interval '365 days' THEN
        RAISE EXCEPTION 'corte da trilha abaixo do piso de 365 dias'
            USING ERRCODE = 'check_violation';
    END IF;
    -- Só o prefixo contínuo da cadeia pode sair: até a linha anterior à primeira ainda dentro do prazo.
    SELECT coalesce(min(sequence) - 1, (SELECT max(sequence) FROM authorship_record))
    INTO last_sequence
    FROM authorship_record
    WHERE occurred_at >= cutoff;
    IF last_sequence IS NULL THEN
        RETURN 0;
    END IF;
    SELECT record_hash INTO last_hash FROM authorship_record WHERE sequence = last_sequence;
    IF last_hash IS NULL THEN
        RETURN 0;
    END IF;
    PERFORM set_config('reports.audit_purge', 'on', true);
    DELETE FROM authorship_record WHERE sequence <= last_sequence;
    GET DIAGNOSTICS removed = ROW_COUNT;
    PERFORM set_config('reports.audit_purge', 'off', true);
    IF removed > 0 THEN
        INSERT INTO audit_chain_anchor (id, purged_until_sequence, last_record_hash, purged_at, purged_count)
        VALUES (gen_random_uuid(), last_sequence, last_hash, now(), removed);
    END IF;
    RETURN removed;
END;
$$;
REVOKE ALL ON FUNCTION purge_authorship_record(timestamptz) FROM PUBLIC;

-- ---------------------------------------------------------------------------------------------------------
-- 4. Papel de banco da aplicação (R-03): o pool usa reports_app; o Flyway e o dono seguem com o papel atual.
--    A senha NÃO está aqui: infra/scripts/lib/db-roles.sh aplica LOGIN e senha a partir do ambiente.
-- ---------------------------------------------------------------------------------------------------------
DO
$$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'reports_app') THEN
        CREATE ROLE reports_app NOLOGIN;
    END IF;
END
$$;

-- O esquema é o corrente (nos testes de migração é um esquema próprio; em produção é o public).
DO
$$
DECLARE
    target_schema text := current_schema();
BEGIN
    EXECUTE format('REVOKE CREATE ON SCHEMA %I FROM PUBLIC', target_schema);
    EXECUTE format('GRANT USAGE ON SCHEMA %I TO reports_app', target_schema);
    EXECUTE format('GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA %I TO reports_app', target_schema);
    EXECUTE format('GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA %I TO reports_app', target_schema);
    -- Tabelas de acréscimo: sem UPDATE nem DELETE para a aplicação (o expurgo passa pela função).
    EXECUTE format('REVOKE UPDATE, DELETE ON %1$I.authorship_record, %1$I.audit_chain_anchor, %1$I.erased_student FROM reports_app',
                   target_schema);
    IF to_regclass(format('%I.flyway_schema_history', target_schema)) IS NOT NULL THEN
        EXECUTE format('REVOKE ALL ON %I.flyway_schema_history FROM reports_app', target_schema);
    END IF;
    EXECUTE format('GRANT EXECUTE ON FUNCTION %I.purge_authorship_record(timestamptz) TO reports_app', target_schema);
    EXECUTE format('ALTER DEFAULT PRIVILEGES IN SCHEMA %I GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO reports_app',
                   target_schema);
    EXECUTE format('ALTER DEFAULT PRIVILEGES IN SCHEMA %I GRANT USAGE, SELECT ON SEQUENCES TO reports_app',
                   target_schema);
END
$$;
