-- Relatório semestral (data-model.md §9).

CREATE TABLE relatorio_semestral (
    id                              uuid PRIMARY KEY,
    professor_id                    uuid        NOT NULL REFERENCES professor (id),
    aluno_id                        uuid        NOT NULL REFERENCES aluno (id),
    periodo_chave                   varchar(6)  NOT NULL,
    periodo_inicio                  date        NOT NULL,
    periodo_fim                     date        NOT NULL,
    quantidade_aulas_consideradas   integer     NOT NULL,
    quantidade_relatorios_em_aberto integer     NOT NULL,
    informacoes_gerais              jsonb       NOT NULL,
    conteudo                        jsonb       NOT NULL,
    status                          varchar(30) NOT NULL,
    etapa_processamento             varchar(30),
    versao_vigente                  integer     NOT NULL,
    canal_origem                    varchar(12) NOT NULL,
    criado_em                       timestamptz NOT NULL,
    atualizado_em                   timestamptz NOT NULL,
    aprovado_em                     timestamptz,
    versao_aprovada                 integer,
    canal_aprovacao                 varchar(12),
    cancelado_em                    timestamptz,
    tempo_processamento_ms          bigint      NOT NULL,
    bloqueio_otimista               bigint      NOT NULL,
    CONSTRAINT ck_relatorio_semestral_periodo CHECK (periodo_chave ~ '^[0-9]{4}-[12]$'),
    CONSTRAINT ck_relatorio_semestral_aulas CHECK (quantidade_aulas_consideradas >= 1),
    CONSTRAINT ck_relatorio_semestral_em_aberto CHECK (quantidade_relatorios_em_aberto >= 0)
);

-- No máximo um semestral aprovado por professor, aluno e período (FR-046).
CREATE UNIQUE INDEX uk_relatorio_semestral_aprovado
    ON relatorio_semestral (professor_id, aluno_id, periodo_chave)
    WHERE status = 'APROVADO';

-- No máximo um semestral em aberto por professor, aluno e período (FR-034).
CREATE UNIQUE INDEX uk_relatorio_semestral_em_aberto
    ON relatorio_semestral (professor_id, aluno_id, periodo_chave)
    WHERE status = 'AGUARDANDO_CONFERENCIA';

-- Uma consolidação em curso por professor, aluno e período (conta como semestral em aberto).
CREATE UNIQUE INDEX uk_trabalho_consolidacao_em_curso
    ON trabalho_processamento (professor_id, aluno_id, (parametros ->> 'periodoChave'))
    WHERE tipo = 'CONSOLIDACAO_SEMESTRAL' AND status IN ('PENDENTE', 'EM_EXECUCAO');

-- Relatórios de aula aprovados por período (consolidação e histórico).
CREATE INDEX ix_aula_professor_aluno_data ON aula (professor_id, aluno_id, data_aula);
