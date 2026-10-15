-- Fila de trabalhos persistida e registro de custo das chamadas de IA (data-model.md §13, §14).

CREATE TABLE trabalho_processamento (
    id                      uuid PRIMARY KEY,
    tipo                    varchar(30) NOT NULL,
    professor_id            uuid        NOT NULL REFERENCES professor (id),
    aluno_id                uuid        NOT NULL REFERENCES aluno (id),
    relatorio_tipo          varchar(12),
    relatorio_id            uuid,
    canal_solicitacao       varchar(12) NOT NULL,
    meio                    varchar(10) NOT NULL,
    parametros              jsonb       NOT NULL,
    arquivo_temporario      varchar(255),
    status                  varchar(20) NOT NULL,
    etapa                   varchar(30) NOT NULL,
    resultado_codigo        varchar(60),
    relatorio_resultante_id uuid,
    tentativas              integer     NOT NULL,
    aviso_demora_enviado    boolean     NOT NULL,
    criado_em               timestamptz NOT NULL,
    iniciado_em             timestamptz,
    concluido_em            timestamptz
);

-- Um áudio em transcrição conta como relatório em aberto: um registro em curso por par (FR-034).
CREATE UNIQUE INDEX uk_trabalho_registro_em_curso
    ON trabalho_processamento (professor_id, aluno_id)
    WHERE tipo = 'REGISTRO_AULA' AND status IN ('PENDENTE', 'EM_EXECUCAO');

CREATE INDEX ix_trabalho_pendente ON trabalho_processamento (criado_em) WHERE status = 'PENDENTE';
CREATE INDEX ix_trabalho_relatorio ON trabalho_processamento (relatorio_tipo, relatorio_id);

CREATE TABLE chamada_ia (
    id                 uuid PRIMARY KEY,
    trabalho_id        uuid        NOT NULL REFERENCES trabalho_processamento (id),
    relatorio_tipo     varchar(12),
    relatorio_id       uuid,
    tarefa             varchar(30) NOT NULL,
    provedor_modelo    varchar(120),
    segundos_audio     numeric(10, 2),
    tokens_entrada     integer,
    tokens_saida       integer,
    duracao_ms         bigint      NOT NULL,
    custo_estimado_usd numeric(12, 6),
    sucesso            boolean     NOT NULL,
    codigo_erro        varchar(60),
    criado_em          timestamptz NOT NULL
);

CREATE INDEX ix_chamada_ia_relatorio ON chamada_ia (relatorio_tipo, relatorio_id);
