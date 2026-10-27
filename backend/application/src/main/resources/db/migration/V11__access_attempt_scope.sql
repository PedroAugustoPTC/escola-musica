-- Tentativas de acesso por escopo (data-model.md 3.7, R-05): a contagem passa a ser separada por
-- escopo (código do professor, entrada e ativação do administrador, sondagem e consulta de CPF) e
-- ganha o início da janela de contagem. As linhas existentes viram TEACHER_CODE (comportamento da 001).

ALTER TABLE access_attempt ADD COLUMN scope varchar(24) NOT NULL DEFAULT 'TEACHER_CODE';
ALTER TABLE access_attempt ALTER COLUMN scope DROP DEFAULT;
ALTER TABLE access_attempt ADD COLUMN window_started_at timestamptz;

ALTER TABLE access_attempt
    ADD CONSTRAINT ck_access_attempt_scope
        CHECK (scope IN ('TEACHER_CODE', 'ADMIN_SIGN_IN', 'ADMIN_ACTIVATION', 'TEACHER_CPF_PROBE', 'TEACHER_CPF_LOOKUP'));

ALTER TABLE access_attempt DROP CONSTRAINT uk_access_attempt_source;
ALTER TABLE access_attempt
    ADD CONSTRAINT uk_access_attempt_scope_source UNIQUE (scope, channel, source_hash);
