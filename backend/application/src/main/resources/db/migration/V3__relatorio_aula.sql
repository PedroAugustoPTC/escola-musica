-- Relatório de aula, versões imutáveis, interações e PDFs (data-model.md §7, §8, §10 a §12).

CREATE TABLE aula (
    id           uuid PRIMARY KEY,
    professor_id uuid        NOT NULL REFERENCES professor (id),
    aluno_id     uuid        NOT NULL REFERENCES aluno (id),
    data_aula    date        NOT NULL,
    criado_em    timestamptz NOT NULL
);

CREATE INDEX ix_aula_professor_aluno ON aula (professor_id, aluno_id, data_aula);

CREATE TABLE relatorio_aula (
    id                     uuid PRIMARY KEY,
    aula_id                uuid        NOT NULL REFERENCES aula (id),
    professor_id           uuid        NOT NULL REFERENCES professor (id),
    aluno_id               uuid        NOT NULL REFERENCES aluno (id),
    transcricao            text        NOT NULL,
    conteudo               jsonb       NOT NULL,
    perguntas_pendentes    jsonb       NOT NULL,
    rodadas_perguntas      integer     NOT NULL,
    status                 varchar(30) NOT NULL,
    etapa_processamento    varchar(30),
    versao_vigente         integer     NOT NULL,
    canal_origem           varchar(12) NOT NULL,
    motivo_falha           varchar(40),
    criado_em              timestamptz NOT NULL,
    atualizado_em          timestamptz NOT NULL,
    aprovado_em            timestamptz,
    versao_aprovada        integer,
    canal_aprovacao        varchar(12),
    cancelado_em           timestamptz,
    tempo_processamento_ms bigint      NOT NULL,
    bloqueio_otimista      bigint      NOT NULL,
    CONSTRAINT uk_relatorio_aula_aula UNIQUE (aula_id),
    CONSTRAINT ck_relatorio_aula_rodadas CHECK (rodadas_perguntas BETWEEN 0 AND 2)
);

-- No máximo um relatório de aula em aberto por professor e aluno (FR-034).
CREATE UNIQUE INDEX uk_relatorio_aula_em_aberto
    ON relatorio_aula (professor_id, aluno_id)
    WHERE status IN ('EM_PROCESSAMENTO', 'FALHA_PROCESSAMENTO', 'AGUARDANDO_RESPOSTAS', 'AGUARDANDO_CONFERENCIA');

CREATE INDEX ix_relatorio_aula_aprovados ON relatorio_aula (professor_id, aluno_id) WHERE status = 'APROVADO';

CREATE TABLE interacao_relatorio (
    id                uuid PRIMARY KEY,
    relatorio_tipo    varchar(12) NOT NULL,
    relatorio_id      uuid        NOT NULL,
    tipo              varchar(30) NOT NULL,
    canal             varchar(12) NOT NULL,
    meio              varchar(10) NOT NULL,
    texto             text        NOT NULL,
    versao_base       integer     NOT NULL,
    rodada            integer,
    resultado         varchar(40) NOT NULL,
    versao_resultante integer,
    criado_em         timestamptz NOT NULL
);

CREATE INDEX ix_interacao_relatorio ON interacao_relatorio (relatorio_tipo, relatorio_id, criado_em);

CREATE TABLE versao_relatorio (
    id             uuid PRIMARY KEY,
    relatorio_tipo varchar(12) NOT NULL,
    relatorio_id   uuid        NOT NULL,
    numero         integer     NOT NULL,
    conteudo       jsonb       NOT NULL,
    origem         varchar(30) NOT NULL,
    interacao_id   uuid REFERENCES interacao_relatorio (id),
    canal          varchar(12) NOT NULL,
    prompt_versao  varchar(40),
    criada_em      timestamptz NOT NULL,
    CONSTRAINT uk_versao_relatorio UNIQUE (relatorio_tipo, relatorio_id, numero),
    CONSTRAINT ck_versao_relatorio_numero CHECK (numero >= 1)
);

CREATE TABLE documento_pdf (
    id                  uuid PRIMARY KEY,
    versao_id           uuid         NOT NULL REFERENCES versao_relatorio (id),
    tipo                varchar(12)  NOT NULL,
    chave_armazenamento varchar(255) NOT NULL,
    tamanho_bytes       bigint       NOT NULL,
    sha256              varchar(64)  NOT NULL,
    gerado_em           timestamptz  NOT NULL,
    CONSTRAINT uk_documento_pdf UNIQUE (versao_id, tipo)
);
