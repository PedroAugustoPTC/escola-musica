package br.com.felipeblaft.relatorios.porta;

import br.com.felipeblaft.relatorios.dominio.relatorio.ConteudoRelatorioAula;
import br.com.felipeblaft.relatorios.dominio.semestral.ConteudoRelatorioSemestral;
import br.com.felipeblaft.relatorios.dominio.semestral.InformacoesGerais;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Geração do PDF com a identidade visual (contracts/portas-provedores.md §4, FR-049). O mesmo PDF
 * vale para os dois canais.
 */
public interface GeracaoPdfPorta {

    DocumentoGerado gerarRelatorioAula(ModeloPdfRelatorioAula modelo);

    DocumentoGerado gerarRelatorioSemestral(ModeloPdfRelatorioSemestral modelo);

    enum SituacaoDocumento {
        CONFERENCIA,
        OFICIAL
    }

    /** Quem aprovou, quando e qual versão (FR-030). */
    record Aprovacao(String professorNome, Instant aprovadoEm, int versao) {}

    record ModeloPdfRelatorioAula(
            String alunoNome,
            String professorNome,
            LocalDate dataAula,
            ConteudoRelatorioAula conteudo,
            int versao,
            SituacaoDocumento situacao,
            Aprovacao aprovacao) {

        public ModeloPdfRelatorioAula {
            Objects.requireNonNull(alunoNome);
            Objects.requireNonNull(professorNome);
            Objects.requireNonNull(dataAula);
            Objects.requireNonNull(conteudo);
            Objects.requireNonNull(situacao);
            if (situacao == SituacaoDocumento.OFICIAL) {
                Objects.requireNonNull(aprovacao);
            }
        }
    }

    /**
     * Semestral: Informações Gerais (sistema), seções 2 a 10 e a quantidade de aulas consolidadas.
     */
    record ModeloPdfRelatorioSemestral(
            InformacoesGerais informacoesGerais,
            ConteudoRelatorioSemestral conteudo,
            int versao,
            SituacaoDocumento situacao,
            Aprovacao aprovacao) {

        public ModeloPdfRelatorioSemestral {
            Objects.requireNonNull(informacoesGerais);
            Objects.requireNonNull(conteudo);
            Objects.requireNonNull(situacao);
            if (situacao == SituacaoDocumento.OFICIAL) {
                Objects.requireNonNull(aprovacao);
            }
        }
    }

    record DocumentoGerado(byte[] bytes, String sha256, long tamanho) {}
}
