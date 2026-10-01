-- Acesso: sessões da Web, vínculo e conversa do Telegram, tentativas e deduplicação do webhook
-- (data-model.md §4, §5, §6 e §15).

CREATE TABLE sessao_web (
    id            uuid PRIMARY KEY,
    professor_id  uuid        NOT NULL REFERENCES professor (id),
    token_hash    varchar(64) NOT NULL,
    criada_em     timestamptz NOT NULL,
    ultimo_uso_em timestamptz NOT NULL,
    expira_em     timestamptz NOT NULL,
    CONSTRAINT uk_sessao_web_token UNIQUE (token_hash)
);

CREATE INDEX ix_sessao_web_expira ON sessao_web (expira_em);

CREATE TABLE vinculo_telegram (
    id                             uuid PRIMARY KEY,
    professor_id                   uuid        NOT NULL REFERENCES professor (id),
    telegram_usuario_id            bigint      NOT NULL,
    chat_id                        bigint      NOT NULL,
    vinculado_em                   timestamptz NOT NULL,
    aluno_selecionado_id           uuid REFERENCES aluno (id),
    fluxo                          varchar(20) NOT NULL,
    etapa_fluxo                    varchar(40) NOT NULL,
    dados_fluxo                    jsonb       NOT NULL,
    audio_pendente_file_id         varchar(255),
    audio_pendente_file_unique_id  varchar(64),
    audio_pendente_mime            varchar(100),
    audio_pendente_tamanho         bigint,
    audio_pendente_recebido_em     timestamptz,
    relatorio_foco_tipo            varchar(12),
    relatorio_foco_id              uuid,
    versao_apresentada             integer,
    atualizado_em                  timestamptz NOT NULL,
    bloqueio_otimista              bigint      NOT NULL,
    CONSTRAINT uk_vinculo_telegram_professor UNIQUE (professor_id),
    CONSTRAINT uk_vinculo_telegram_usuario UNIQUE (telegram_usuario_id)
);

CREATE TABLE tentativa_acesso (
    id                    uuid PRIMARY KEY,
    canal                 varchar(12) NOT NULL,
    origem_hash           varchar(64) NOT NULL,
    tentativas_incorretas integer     NOT NULL,
    bloqueado_ate         timestamptz,
    atualizado_em         timestamptz NOT NULL,
    CONSTRAINT uk_tentativa_acesso_origem UNIQUE (canal, origem_hash),
    CONSTRAINT ck_tentativa_acesso_positiva CHECK (tentativas_incorretas >= 0)
);

CREATE TABLE update_telegram_processado (
    update_id   bigint PRIMARY KEY,
    recebido_em timestamptz NOT NULL
);

CREATE INDEX ix_update_telegram_recebido ON update_telegram_processado (recebido_em);
