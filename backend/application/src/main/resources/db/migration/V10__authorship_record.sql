-- Registro de autoria da feature 005 (data-model.md 3.4, R-10, FR-040): quem fez o que, quando, por qual
-- canal e com qual resultado. Somente acréscimo: gatilhos impedem UPDATE, DELETE e TRUNCATE.
-- Sem chave estrangeira (sobrevive à remoção de contas) e sem coluna que aceite CPF, dado de aluno ou
-- de responsável, e-mail digitado, senha ou código: os campos de texto só aceitam códigos em
-- maiúsculas, o resumo hexadecimal da origem e identificadores de correlação.

CREATE TABLE authorship_record (
    id             uuid PRIMARY KEY,
    occurred_at    timestamptz NOT NULL,
    author_id      uuid,
    author_role    varchar(16),
    origin         varchar(24) NOT NULL,
    operation      varchar(48) NOT NULL,
    target_type    varchar(24),
    target_id      uuid,
    result         varchar(10) NOT NULL,
    error_code     varchar(40),
    origin_hash    varchar(64),
    correlation_id varchar(64) NOT NULL,
    CONSTRAINT ck_authorship_record_author_role
        CHECK (author_role IS NULL OR author_role IN ('ADMINISTRATOR', 'TEACHER')),
    CONSTRAINT ck_authorship_record_author
        CHECK (author_role IS NOT NULL OR author_id IS NULL),
    CONSTRAINT ck_authorship_record_origin
        CHECK (origin IN ('WEB', 'TELEGRAM', 'SYSTEM_STARTUP', 'INITIAL_LOAD')),
    CONSTRAINT ck_authorship_record_target_type
        CHECK (target_type IS NULL OR target_type IN ('TEACHER', 'ADMINISTRATOR', 'STUDENT', 'ASSIGNMENT')),
    CONSTRAINT ck_authorship_record_result
        CHECK (result IN ('SUCCESS', 'FAILURE', 'DENIED', 'BLOCKED')),
    CONSTRAINT ck_authorship_record_operation
        CHECK (operation ~ '^[A-Z][A-Z0-9_]{0,47}$'),
    CONSTRAINT ck_authorship_record_error_code
        CHECK (error_code IS NULL OR error_code ~ '^[A-Z][A-Z0-9_]{0,39}$'),
    CONSTRAINT ck_authorship_record_origin_hash
        CHECK (origin_hash IS NULL OR origin_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_authorship_record_origin_hash_event
        CHECK (origin_hash IS NULL OR operation IN (
            'ADMIN_SIGN_IN', 'ADMIN_ACTIVATE', 'ADMIN_SIGN_OUT', 'ADMIN_CHANGE_PASSWORD',
            'TEACHER_SIGN_IN', 'TEACHER_LINK_TELEGRAM', 'ATTEMPT_BLOCKED', 'AUTHORIZATION_DENIED')),
    CONSTRAINT ck_authorship_record_correlation
        CHECK (correlation_id ~* '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$'
            OR correlation_id ~ '^[0-9]{1,10}$'
            OR (correlation_id ~ '^(req|load|startup)-[a-z0-9]{1,16}$' AND correlation_id !~ '[0-9]{11}'))
);

CREATE INDEX ix_authorship_record_occurred ON authorship_record (occurred_at);
CREATE INDEX ix_authorship_record_author ON authorship_record (author_id, occurred_at);
CREATE INDEX ix_authorship_record_target ON authorship_record (target_type, target_id);

CREATE FUNCTION authorship_record_append_only() RETURNS trigger
    LANGUAGE plpgsql AS
$$
BEGIN
    RAISE EXCEPTION 'authorship_record é somente de acréscimo (%)', TG_OP
        USING ERRCODE = 'integrity_constraint_violation';
END;
$$;

CREATE TRIGGER tr_authorship_record_no_change
    BEFORE UPDATE OR DELETE ON authorship_record
    FOR EACH STATEMENT EXECUTE FUNCTION authorship_record_append_only();

CREATE TRIGGER tr_authorship_record_no_truncate
    BEFORE TRUNCATE ON authorship_record
    FOR EACH STATEMENT EXECUTE FUNCTION authorship_record_append_only();
