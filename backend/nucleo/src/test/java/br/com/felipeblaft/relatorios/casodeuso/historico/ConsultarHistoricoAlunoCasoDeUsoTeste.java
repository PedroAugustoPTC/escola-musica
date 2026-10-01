package br.com.felipeblaft.relatorios.casodeuso.historico;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.felipeblaft.relatorios.casodeuso.historico.ConsultarHistoricoAlunoCasoDeUso.Historico;
import br.com.felipeblaft.relatorios.casodeuso.historico.ConsultarHistoricoAlunoCasoDeUso.ItemHistorico;
import br.com.felipeblaft.relatorios.casodeuso.inicio.ObterResumoAlunoCasoDeUso;
import br.com.felipeblaft.relatorios.dominio.comum.Canal;
import br.com.felipeblaft.relatorios.dominio.comum.CodigoErro;
import br.com.felipeblaft.relatorios.dominio.comum.ExcecaoDominio;
import br.com.felipeblaft.relatorios.dominio.comum.TipoRelatorio;
import br.com.felipeblaft.relatorios.dominio.relatorio.RelatorioAula;
import br.com.felipeblaft.relatorios.dominio.semestral.RelatorioSemestral;
import br.com.felipeblaft.relatorios.dubles.CenarioRelatorio;
import br.com.felipeblaft.relatorios.porta.TarefaLlm;
import java.time.Duration;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class ConsultarHistoricoAlunoCasoDeUsoTeste {

    private final CenarioRelatorio c = new CenarioRelatorio();
    private final ConsultarHistoricoAlunoCasoDeUso historico =
            new ConsultarHistoricoAlunoCasoDeUso(
                    new ObterResumoAlunoCasoDeUso(c.alunos, c.emAberto, c.autorizacao),
                    c.relatorios,
                    c.semestrais);

    @Test
    void soAprovadosDoProfessorDoMaisRecenteAoMaisAntigo() {
        RelatorioAula marco = c.aulaAprovada(c.matteo, LocalDate.of(2026, 3, 10), "Escala");
        RelatorioAula maio = c.aulaAprovada(c.matteo, LocalDate.of(2026, 5, 20), "Estudo");
        c.relogio.avancar(Duration.ofMinutes(5));
        RelatorioAula maioDepois =
                c.aulaAprovada(c.matteo, LocalDate.of(2026, 5, 20), "Outro estudo");
        c.relatorioComPerguntas(); // em aberto: não entra
        c.aulaAprovada(c.helena, LocalDate.of(2026, 4, 1), "De outro professor");
        c.llm.programar(TarefaLlm.CONSOLIDACAO_SEMESTRAL, CenarioRelatorio.saidaSemestral("Bom."));
        c.gerarSemestral.executar(c.clara.id(), c.matteo.id(), "2026-1", false, Canal.WEB);
        c.processarFila();
        RelatorioSemestral semestral = c.semestrais.relatorios.values().iterator().next();
        c.aprovarSemestral.executar(c.clara.id(), semestral.id(), 1, Canal.WEB);

        Historico resultado = historico.executar(c.clara.id(), c.matteo.id());

        assertThat(resultado.aluno().nome()).isEqualTo("Matteo Mafra");
        assertThat(resultado.itens())
                .extracting(ItemHistorico::relatorioId)
                .containsExactly(maioDepois.id(), maio.id(), marco.id(), semestral.id());
        assertThat(resultado.itens().getLast().tipo()).isEqualTo(TipoRelatorio.SEMESTRAL);
        assertThat(resultado.itens().getLast().periodo().chave()).isEqualTo("2026-1");
    }

    @Test
    void vazioEAlunoDeOutroProfessor() {
        assertThat(historico.executar(c.clara.id(), c.matteo.id()).itens()).isEmpty();
        assertThatThrownBy(() -> historico.executar(c.clara.id(), c.helena.id()))
                .isInstanceOfSatisfying(
                        ExcecaoDominio.class,
                        e -> assertThat(e.codigo()).isEqualTo(CodigoErro.ALUNO_NAO_ASSOCIADO));
    }

    @Test
    void pdfOficialSoDeRelatorioAprovadoDoProfessor() {
        RelatorioAula conferencia = c.relatorioEmConferencia();
        assertThatThrownBy(
                        () ->
                                c.obterDocumento.oficial(
                                        c.clara.id(), TipoRelatorio.AULA, conferencia.id()))
                .isInstanceOfSatisfying(
                        ExcecaoDominio.class,
                        e -> assertThat(e.codigo()).isEqualTo(CodigoErro.RECURSO_NAO_ENCONTRADO));
        c.aprovar.executar(c.clara.id(), conferencia.id(), conferencia.versaoVigente(), Canal.WEB);
        assertThat(
                        c.obterDocumento
                                .oficial(c.clara.id(), TipoRelatorio.AULA, conferencia.id())
                                .conteudo())
                .isNotEmpty();
        assertThatThrownBy(
                        () ->
                                c.obterDocumento.oficial(
                                        c.bruno.id(), TipoRelatorio.AULA, conferencia.id()))
                .isInstanceOfSatisfying(
                        ExcecaoDominio.class,
                        e -> assertThat(e.codigo()).isEqualTo(CodigoErro.RECURSO_NAO_ENCONTRADO));
    }
}
