-- Feature 006 (visão da escola, data-model.md 4.1 e 5): detalhe das leituras do administrador no registro
-- de autoria e índices dos relatórios aprovados. Migração só aditiva: não apaga nem altera linha existente.
-- O registro continua somente de acréscimo (os gatilhos da V10 seguem valendo) e as colunas novas só
-- aceitam identificador, tipo, data e número: nunca nome, CPF nem conteúdo de relatório.

ALTER TABLE authorship_record
    ADD COLUMN filter_teacher_id    uuid,
    ADD COLUMN filter_report_type   varchar(16),
    ADD COLUMN filter_period_start  date,
    ADD COLUMN filter_period_end    date,
    ADD COLUMN page_number          integer,
    ADD COLUMN document_version     integer;

-- O detalhe só existe nas três operações de leitura da feature.
ALTER TABLE authorship_record
    ADD CONSTRAINT ck_authorship_record_read_detail_operation
        CHECK ((filter_teacher_id IS NULL AND filter_report_type IS NULL AND filter_period_start IS NULL
                AND filter_period_end IS NULL AND page_number IS NULL AND document_version IS NULL)
            OR operation IN ('SCHOOL_HISTORY_QUERY', 'SCHOOL_REPORT_DOCUMENT_OPEN',
                             'SCHOOL_REPORT_DOCUMENT_DOWNLOAD')),
    ADD CONSTRAINT ck_authorship_record_filter_report_type
        CHECK (filter_report_type IS NULL OR filter_report_type IN ('LESSON', 'SEMESTER')),
    ADD CONSTRAINT ck_authorship_record_page_number
        CHECK (page_number IS NULL OR page_number >= 0),
    ADD CONSTRAINT ck_authorship_record_document_version
        CHECK (document_version IS NULL OR document_version >= 1);

-- O alvo passa a aceitar também os relatórios (aula e semestral).
ALTER TABLE authorship_record DROP CONSTRAINT ck_authorship_record_target_type;
ALTER TABLE authorship_record
    ADD CONSTRAINT ck_authorship_record_target_type
        CHECK (target_type IS NULL OR target_type IN ('TEACHER', 'ADMINISTRATOR', 'STUDENT', 'ASSIGNMENT',
                                                      'LESSON_REPORT', 'SEMESTER_REPORT'));

-- Índices parciais dos relatórios aprovados, para a lista da escola por aluno, por professor e por período.
CREATE INDEX ix_lesson_report_approved_student ON lesson_report (student_id) WHERE status = 'APPROVED';
CREATE INDEX ix_lesson_report_approved_teacher ON lesson_report (teacher_id) WHERE status = 'APPROVED';
CREATE INDEX ix_semester_report_approved_studentON semester_report (student_id) WHERE status = 'APPROVED';
CREATE INDEX ix_semester_report_approved_teacher ON semester_report (teacher_id) WHERE status = 'APPROVED';
CREATE INDEX ix_semester_report_approved_period ON semester_report (period_start) WHERE status = 'APPROVED';
