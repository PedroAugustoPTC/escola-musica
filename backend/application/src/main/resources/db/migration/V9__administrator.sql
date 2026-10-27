-- Feature 005: administrador e sessão do administrador (data-model.md §3.1 e §3.2).
-- O e-mail é único por papel (pode coincidir com o de um professor) e sempre em minúsculas.
-- A senha só existe como hash Argon2id e o código de ativação só como HMAC.

CREATE TABLE administrator (
    id                              uuid PRIMARY KEY,
    name                            varchar(120) NOT NULL,
    email                           varchar(254) NOT NULL,
    active                          boolean      NOT NULL,
    password_hash                   varchar(255),
    password_provisional            boolean      NOT NULL DEFAULT false,
    password_provisional_expires_at timestamptz,
    activation_code_hash            varchar(64),
    activation_code_expires_at      timestamptz,
    activated_at                    timestamptz,
    created_at                      timestamptz  NOT NULL,
    updated_at                      timestamptz  NOT NULL,
    CONSTRAINT uk_administrator_email UNIQUE (email),
    CONSTRAINT ck_administrator_email_lowercase CHECK (email = lower(email))
);

-- No máximo um código de ativação pendente por valor; a coluna já garante um por administrador (US7).
CREATE UNIQUE INDEX ux_administrator_activation_code
    ON administrator (activation_code_hash)
    WHERE activation_code_hash IS NOT NULL;

CREATE TABLE administrator_session (
    id                uuid PRIMARY KEY,
    administrator_id  uuid        NOT NULL REFERENCES administrator (id) ON DELETE CASCADE,
    token_hash        varchar(64) NOT NULL,
    created_at        timestamptz NOT NULL,
    expires_at        timestamptz NOT NULL,
    revoked_at        timestamptz,
    revocation_reason varchar(24),
    CONSTRAINT uk_administrator_session_token UNIQUE (token_hash),
    CONSTRAINT ck_administrator_session_reason
        CHECK (revocation_reason IS NULL OR revocation_reason IN ('ACCOUNT_INACTIVATED', 'PASSWORD_CHANGED')),
    CONSTRAINT ck_administrator_session_revocation
        CHECK ((revoked_at IS NULL) = (revocation_reason IS NULL))
);

CREATE INDEX ix_administrator_session_administrator ON administrator_session (administrator_id);
CREATE INDEX ix_administrator_session_expires ON administrator_session (expires_at);
