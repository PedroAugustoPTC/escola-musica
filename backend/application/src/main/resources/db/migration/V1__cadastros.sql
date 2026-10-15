-- Cadastros: professores, alunos e associações (data-model.md §1–3).
-- Valores padrão e carimbos de tempo são preenchidos pela aplicação (lição 14 do MVP).

CREATE TABLE professor (
    id                        uuid PRIMARY KEY,
    nome                      varchar(255) NOT NULL,
    email                     varchar(255) NOT NULL,
    ativo                     boolean      NOT NULL,
    codigo_acesso_hash        varchar(64),
    codigo_acesso_expira_em   timestamptz,
    codigo_acesso_renovado_em timestamptz,
    criado_em                 timestamptz  NOT NULL,
    atualizado_em             timestamptz  NOT NULL,
    CONSTRAINT uk_professor_email UNIQUE (email),
    CONSTRAINT ck_professor_email_minusculo CHECK (email = lower(email))
);

CREATE UNIQUE INDEX uk_professor_codigo_acesso_hash
    ON professor (codigo_acesso_hash) WHERE codigo_acesso_hash IS NOT NULL;

CREATE TABLE aluno (
    id               uuid PRIMARY KEY,
    nome             varchar(255) NOT NULL,
    data_nascimento  date         NOT NULL,
    cpf_cifrado      bytea        NOT NULL,
    cpf_hash         varchar(64)  NOT NULL,
    nome_responsavel varchar(255),
    instrumento      varchar(80),
    ativo            boolean      NOT NULL,
    criado_em        timestamptz  NOT NULL,
    atualizado_em    timestamptz  NOT NULL,
    CONSTRAINT uk_aluno_cpf_hash UNIQUE (cpf_hash)
);

CREATE TABLE professor_aluno (
    id           uuid PRIMARY KEY,
    professor_id uuid        NOT NULL REFERENCES professor (id),
    aluno_id     uuid        NOT NULL REFERENCES aluno (id),
    criado_em    timestamptz NOT NULL,
    CONSTRAINT uk_professor_aluno UNIQUE (professor_id, aluno_id)
);

CREATE INDEX ix_professor_aluno_aluno ON professor_aluno (aluno_id);
