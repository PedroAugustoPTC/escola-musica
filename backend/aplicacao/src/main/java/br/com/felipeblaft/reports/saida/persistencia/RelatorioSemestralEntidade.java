package br.com.felipeblaft.reports.saida.persistencia;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.hibernate.annotations.ColumnTransformer;

@Entity
@Table(name = "relatorio_semestral")
class RelatorioSemestralEntidade {
    @Id UUID id;

    @Column(name = "professor_id", nullable = false)
    UUID professorId;

    @Column(name = "aluno_id", nullable = false)
    UUID alunoId;

    @Column(name = "periodo_chave", nullable = false)
    String periodoChave;

    @Column(name = "periodo_inicio", nullable = false)
    LocalDate periodoInicio;

    @Column(name = "periodo_fim", nullable = false)
    LocalDate periodoFim;

    @Column(name = "quantidade_aulas_consideradas", nullable = false)
    int quantidadeAulasConsideradas;

    @Column(name = "quantidade_relatorios_em_aberto", nullable = false)
    int quantidadeRelatoriosEmAberto;

    @Column(name = "informacoes_gerais", nullable = false, columnDefinition = "jsonb")
    @ColumnTransformer(write = "?::jsonb")
    String informacoesGerais;

    @Column(nullable = false, columnDefinition = "jsonb")
    @ColumnTransformer(write = "?::jsonb")
    String conteudo;

    @Column(nullable = false)
    String status;

    @Column(name = "etapa_processamento")
    String etapaProcessamento;

    @Column(name = "versao_vigente", nullable = false)
    int versaoVigente;

    @Column(name = "canal_origem", nullable = false)
    String canalOrigem;

    @Column(name = "criado_em", nullable = false)
    Instant criadoEm;

    @Column(name = "atualizado_em", nullable = false)
    Instant atualizadoEm;

    @Column(name = "aprovado_em")
    Instant aprovadoEm;

    @Column(name = "versao_aprovada")
    Integer versaoAprovada;

    @Column(name = "canal_aprovacao")
    String canalAprovacao;

    @Column(name = "cancelado_em")
    Instant canceladoEm;

    @Column(name = "tempo_processamento_ms", nullable = false)
    long tempoProcessamentoMs;

    @Version
    @Column(name = "bloqueio_otimista", nullable = false)
    long bloqueioOtimista;
}
