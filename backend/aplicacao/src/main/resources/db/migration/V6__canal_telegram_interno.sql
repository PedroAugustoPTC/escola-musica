-- API interna do canal Telegram (feature 002, data-model.md §1): outbox de ações, documentos por
-- identificador opaco e duas colunas em update_telegram_processado. Migração aditiva: o webhook
-- antigo não toca nas colunas novas e continua funcionando durante o deploy.

ALTER TABLE update_telegram_processado
    ADD COLUMN total_acoes integer,
    ADD COLUMN origem      varchar(10),
    ADD CONSTRAINT ck_update_telegram_total_acoes CHECK (total_acoes IS NULL OR total_acoes >= 0),
    ADD CONSTRAINT ck_update_telegram_origem CHECK (origem IS NULL OR origem = 'INTERNO');

-- Uma linha por ação: respostas a update, proativas e compensações. Sem chaves estrangeiras: a
-- retenção da outbox e a das outras tabelas são independentes.
CREATE TABLE acao_canal (
    id                   varchar(120) NOT NULL,
    seq                  bigint GENERATED ALWAYS AS IDENTITY,
    lote_id              varchar(120) NOT NULL,
    canal                varchar(12)  NOT NULL,
    origem               varchar(16)  NOT NULL,
    update_id            bigint,
    chat_id              bigint       NOT NULL,
    professor_id         uuid,
    trabalho_id          uuid,
    tipo                 varchar(20)  NOT NULL,
    payload              jsonb,
    compensacao          jsonb,
    estado               varchar(18)  NOT NULL,
    tentativas           integer      NOT NULL DEFAULT 0,
    proxima_tentativa_em timestamptz,
    prazo_em             timestamptz  NOT NULL,
    versao               integer      NOT NULL DEFAULT 0,
    criado_em            timestamptz  NOT NULL,
    entregue_em          timestamptz,
    finalizado_em        timestamptz,
    codigo_motivo        varchar(40),
    PRIMARY KEY (id),
    CONSTRAINT uk_acao_canal_seq UNIQUE (seq),
    CONSTRAINT ck_acao_canal_estado CHECK (
        estado IN ('PENDENTE', 'ENTREGUE', 'EXECUTADA', 'FALHOU', 'FALHA_DEFINITIVA', 'DESCARTADA')),
    CONSTRAINT ck_acao_canal_tipo CHECK (
        tipo IN ('MENSAGEM', 'EDICAO', 'DOCUMENTO', 'RESPOSTA_CALLBACK', 'MENSAGEM_ETAPA')),
    CONSTRAINT ck_acao_canal_origem CHECK (
        origem IN ('RESPOSTA_UPDATE', 'PROATIVA', 'COMPENSACAO')),
    CONSTRAINT ck_acao_canal_tentativas CHECK (tentativas >= 0),
    CONSTRAINT ck_acao_canal_resposta_update CHECK (origem <> 'RESPOSTA_UPDATE' OR update_id IS NOT NULL)
);

CREATE INDEX ix_acao_canal_entrega ON acao_canal (proxima_tentativa_em) WHERE estado = 'PENDENTE';
CREATE INDEX ix_acao_canal_chat_vivas ON acao_canal (chat_id, seq) WHERE estado IN ('PENDENTE', 'ENTREGUE');
CREATE INDEX ix_acao_canal_update ON acao_canal (update_id, seq) WHERE update_id IS NOT NULL;
CREATE INDEX ix_acao_canal_lote ON acao_canal (lote_id, seq);
CREATE INDEX ix_acao_canal_expurgo ON acao_canal (finalizado_em) WHERE finalizado_em IS NOT NULL;

-- Identificador opaco de PDF para o orquestrador: só o hash do token é guardado.
CREATE TABLE documento_canal (
    token_hash     char(64)     PRIMARY KEY,
    professor_id   uuid         NOT NULL,
    relatorio_tipo varchar(12)  NOT NULL,
    relatorio_id   uuid         NOT NULL,
    versao         integer,
    nome_arquivo   varchar(120) NOT NULL,
    criado_em      timestamptz  NOT NULL,
    expira_em      timestamptz  NOT NULL,
    CONSTRAINT ck_documento_canal_tipo CHECK (relatorio_tipo IN ('AULA', 'SEMESTRAL')),
    CONSTRAINT ck_documento_canal_versao CHECK (versao IS NULL OR versao > 0)
);

CREATE INDEX ix_documento_canal_expira ON documento_canal (expira_em);
