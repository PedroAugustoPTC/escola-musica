-- V12 (feature 005): contato do responsável no aluno e cadastro de aluno em andamento no Telegram.
-- Nada é obrigatório no banco: a exigência dos três dados do responsável para menor de 18 anos é regra do núcleo
-- na data do cadastro ou da edição, e os alunos já existentes, sem contato, continuam válidos até serem editados.

ALTER TABLE student ADD COLUMN guardian_phone varchar(11);
ALTER TABLE student ADD COLUMN guardian_email varchar(254);
ALTER TABLE student
    ADD CONSTRAINT ck_student_guardian_phone CHECK (guardian_phone IS NULL OR guardian_phone ~ '^[0-9]{10,11}$');

-- Uma linha por vínculo do Telegram; some junto com o vínculo, por qualquer causa (ON DELETE CASCADE).
-- O CPF existe aqui só cifrado e como resumo com chave, nunca em claro.
CREATE TABLE student_registration_draft (
    link_id            uuid         PRIMARY KEY REFERENCES telegram_link (id) ON DELETE CASCADE,
    kind               varchar(12)  NOT NULL,
    student_id         uuid REFERENCES student (id),
    provided_fields    varchar(120) NOT NULL,
    name               varchar(120),
    instrument         varchar(60),
    birth_date         date,
    cpf_encrypted      bytea,
    cpf_hash           varchar(64),
    guardian_name      varchar(120),
    guardian_phone     varchar(11),
    guardian_email     varchar(254),
    confirmation_token varchar(16),
    created_at         timestamptz  NOT NULL,
    updated_at         timestamptz  NOT NULL,
    expires_at         timestamptz  NOT NULL,
    CONSTRAINT ck_student_registration_draft_kind CHECK (kind IN ('CREATE', 'UPDATE', 'INACTIVATE', 'REACTIVATE')),
    CONSTRAINT ck_student_registration_draft_student CHECK (kind = 'CREATE' OR student_id IS NOT NULL)
);

CREATE INDEX ix_student_registration_draft_expires ON student_registration_draft (expires_at);
CREATE INDEX ix_student_registration_draft_student ON student_registration_draft (student_id);
