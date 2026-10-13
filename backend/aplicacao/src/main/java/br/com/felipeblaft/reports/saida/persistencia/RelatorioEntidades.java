package br.com.felipeblaft.reports.saida.persistencia;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.hibernate.annotations.ColumnTransformer;

/** Entidades JPA do relatório de aula e do processamento (separadas do domínio, R-03). */
final class RelatorioEntidades {

    private RelatorioEntidades() {}

    @Entity(name = "AulaEntidade")
    @Table(name = "aula")
    static class AulaEntidade {
        @Id UUID id;

        @Column(name = "professor_id", nullable = false)
        UUID professorId;

        @Column(name = "aluno_id", nullable = false)
        UUID alunoId;

        @Column(name = "data_aula", nullable = false)
        LocalDate dataAula;

        @Column(name = "criado_em", nullable = false)
        Instant criadoEm;
    }

    @Entity(name = "RelatorioAulaEntidade")
    @Table(name = "relatorio_aula")
    static class RelatorioAulaEntidade {
        @Id UUID id;

        @Column(name = "aula_id", nullable = false)
        UUID aulaId;

        @Column(name = "professor_id", nullable = false)
        UUID professorId;

        @Column(name = "aluno_id", nullable = false)
        UUID alunoId;

        @Column(nullable = false)
        String transcricao;

        @Column(nullable = false, columnDefinition = "jsonb")
        @ColumnTransformer(write = "?::jsonb")
        String conteudo;

        @Column(name = "perguntas_pendentes", nullable = false, columnDefinition = "jsonb")
        @ColumnTransformer(write = "?::jsonb")
        String perguntasPendentes;

        @Column(name = "rodadas_perguntas", nullable = false)
        int rodadasPerguntas;

        @Column(nullable = false)
        String status;

        @Column(name = "etapa_processamento")
        String etapaProcessamento;

        @Column(name = "versao_vigente", nullable = false)
        int versaoVigente;

        @Column(name = "canal_origem", nullable = false)
        String canalOrigem;

        @Column(name = "motivo_falha")
        String motivoFalha;

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

    @Entity(name = "VersaoRelatorioEntidade")
    @Table(name = "versao_relatorio")
    static class VersaoRelatorioEntidade {
        @Id UUID id;

        @Column(name = "relatorio_tipo", nullable = false)
        String relatorioTipo;

        @Column(name = "relatorio_id", nullable = false)
        UUID relatorioId;

        @Column(nullable = false)
        int numero;

        @Column(nullable = false, columnDefinition = "jsonb")
        @ColumnTransformer(write = "?::jsonb")
        String conteudo;

        @Column(nullable = false)
        String origem;

        @Column(name = "interacao_id")
        UUID interacaoId;

        @Column(nullable = false)
        String canal;

        @Column(name = "prompt_versao")
        String promptVersao;

        @Column(name = "criada_em", nullable = false)
        Instant criadaEm;
    }

    @Entity(name = "InteracaoRelatorioEntidade")
    @Table(name = "interacao_relatorio")
    static class InteracaoRelatorioEntidade {
        @Id UUID id;

        @Column(name = "relatorio_tipo", nullable = false)
        String relatorioTipo;

        @Column(name = "relatorio_id", nullable = false)
        UUID relatorioId;

        @Column(nullable = false)
        String tipo;

        @Column(nullable = false)
        String canal;

        @Column(nullable = false)
        String meio;

        @Column(nullable = false)
        String texto;

        @Column(name = "versao_base", nullable = false)
        int versaoBase;

        Integer rodada;

        @Column(nullable = false)
        String resultado;

        @Column(name = "versao_resultante")
        Integer versaoResultante;

        @Column(name = "criado_em", nullable = false)
        Instant criadoEm;
    }

    @Entity(name = "DocumentoPdfEntidade")
    @Table(name = "documento_pdf")
    static class DocumentoPdfEntidade {
        @Id UUID id;

        @Column(name = "versao_id", nullable = false)
        UUID versaoId;

        @Column(nullable = false)
        String tipo;

        @Column(name = "chave_armazenamento", nullable = false)
        String chaveArmazenamento;

        @Column(name = "tamanho_bytes", nullable = false)
        long tamanhoBytes;

        @Column(nullable = false)
        String sha256;

        @Column(name = "gerado_em", nullable = false)
        Instant geradoEm;
    }

    @Entity(name = "TrabalhoProcessamentoEntidade")
    @Table(name = "trabalho_processamento")
    static class TrabalhoProcessamentoEntidade {
        @Id UUID id;

        @Column(nullable = false)
        String tipo;

        @Column(name = "professor_id", nullable = false)
        UUID professorId;

        @Column(name = "aluno_id", nullable = false)
        UUID alunoId;

        @Column(name = "relatorio_tipo")
        String relatorioTipo;

        @Column(name = "relatorio_id")
        UUID relatorioId;

        @Column(name = "canal_solicitacao", nullable = false)
        String canalSolicitacao;

        @Column(nullable = false)
        String meio;

        @Column(nullable = false, columnDefinition = "jsonb")
        @ColumnTransformer(write = "?::jsonb")
        String parametros;

        @Column(name = "arquivo_temporario")
        String arquivoTemporario;

        @Column(nullable = false)
        String status;

        @Column(nullable = false)
        String etapa;

        @Column(name = "resultado_codigo")
        String resultadoCodigo;

        @Column(name = "relatorio_resultante_id")
        UUID relatorioResultanteId;

        @Column(nullable = false)
        int tentativas;

        @Column(name = "aviso_demora_enviado", nullable = false)
        boolean avisoDemoraEnviado;

        @Column(name = "criado_em", nullable = false)
        Instant criadoEm;

        @Column(name = "iniciado_em")
        Instant iniciadoEm;

        @Column(name = "concluido_em")
        Instant concluidoEm;
    }

    @Entity(name = "ChamadaIaEntidade")
    @Table(name = "chamada_ia")
    static class ChamadaIaEntidade {
        @Id UUID id;

        @Column(name = "trabalho_id", nullable = false)
        UUID trabalhoId;

        @Column(name = "relatorio_tipo")
        String relatorioTipo;

        @Column(name = "relatorio_id")
        UUID relatorioId;

        @Column(nullable = false)
        String tarefa;

        @Column(name = "provedor_modelo")
        String provedorModelo;

        @Column(name = "segundos_audio")
        BigDecimal segundosAudio;

        @Column(name = "tokens_entrada")
        Integer tokensEntrada;

        @Column(name = "tokens_saida")
        Integer tokensSaida;

        @Column(name = "duracao_ms", nullable = false)
        long duracaoMs;

        @Column(name = "custo_estimado_usd")
        BigDecimal custoEstimadoUsd;

        @Column(nullable = false)
        boolean sucesso;

        @Column(name = "codigo_erro")
        String codigoErro;

        @Column(name = "criado_em", nullable = false)
        Instant criadoEm;
    }
}
