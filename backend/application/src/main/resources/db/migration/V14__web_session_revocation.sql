-- Feature 005: revogação das sessões do professor (data-model.md §3.3, R-08). A sessão é marcada,
-- nunca removida, para que a próxima requisição receba a resposta certa. A expiração deslizante
-- de 12 h não muda.

ALTER TABLE web_session
    ADD COLUMN revoked_at        timestamptz,
    ADD COLUMN revocation_reason varchar(24);

ALTER TABLE web_session
    ADD CONSTRAINT ck_web_session_reason
        CHECK (revocation_reason IS NULL OR revocation_reason IN ('ACCOUNT_INACTIVATED', 'CODE_REISSUED')),
    ADD CONSTRAINT ck_web_session_revocation
        CHECK ((revoked_at IS NULL) = (revocation_reason IS NULL));
