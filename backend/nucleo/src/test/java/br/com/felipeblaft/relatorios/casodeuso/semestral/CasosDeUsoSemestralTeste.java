package br.com.felipeblaft.relatorios.casodeuso.semestral;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.felipeblaft.relatorios.casodeuso.aula.AprovarRelatorioAulaCasoDeUso.Aprovacao;
import br.com.felipeblaft.relatorios.casodeuso.documento.ObterDocumentoRelatorioCasoDeUso.DocumentoObtido;
import br.com.felipeblaft.relatorios.casodeuso.semestral.ListarSemestresDisponiveisCasoDeUso.Semestre;
import br.com.felipeblaft.relatorios.casodeuso.semestral.ObterPreviaSemestralCasoDeUso.Previa;
import br.com.felipeblaft.relatorios.casodeuso.semestral.ObterRelatorioSemestralCasoDeUso.VisaoRelatorioSemestral;
import br.com.felipeblaft.relatorios.dominio.comum.Canal;
import br.com.felipeblaft.relatorios.dominio.comum.CodigoErro;
import br.com.felipeblaft.relatorios.dominio.comum.ExcecaoDominio;
import br.com.felipeblaft.relatorios.dominio.comum.TipoRelatorio;
import br.com.felipeblaft.relatorios.dominio.relatorio.RelatorioAula;
import br.com.felipeblaft.relatorios.dominio.relatorio.ResultadoInteracao;
import br.com.felipeblaft.relatorios.dominio.relatorio.StatusRelatorio;
import br.com.felipeblaft.relatorios.dominio.semestral.RelatorioSemestral;
import br.com.felipeblaft.relatorios.dominio.trabalho.ResultadoTrabalho;
import br.com.felipeblaft.relatorios.dominio.trabalho.TipoTrabalho;
import br.com.felipeblaft.relatorios.dominio.trabalho.TrabalhoProcessamento;
import br.com.felipeblaft.relatorios.dubles.CenarioRelatorio;
import br.com.felipeblaft.relatorios.porta.EntradaConsolidacaoSemestral;
import br.com.felipeblaft.relatorios.porta.EntradaRevisaoSemestral;
import br.com.felipeblaft.relatorios.porta.ProvedorIndisponivelExcecao;
import br.com.felipeblaft.relatorios.porta.TarefaLlm;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Casos de uso e consolidação do semestral (US4, data-model.md §9). */
class CasosDeUsoSemestralTeste {

    private final CenarioRelatorio c = new CenarioRelatorio();

    private static void codigo(Runnable acao, CodigoErro esperado) {
        assertThatThrownBy(acao::run)
                .isInstanceOfSatisfying(
                        ExcecaoDominio.class, e -> assertThat(e.codigo()).isEqualTo(esperado));
    }

    private void duasAulasAprovadasNo1Semestre() {
        c.aulaAprovada(c.matteo, LocalDate.of(2026, 3, 10), "Escala de Dó");
        c.aulaAprovada(c.matteo, LocalDate.of(2026, 5, 20), "Estudo nº 5");
    }

    /** Gera, processa e devolve o semestral de Matteo no 1º semestre de 2026. */
    private RelatorioSemestral semestralEmConferencia() {
        duasAulasAprovadasNo1Semestre();
        c.llm.programar(
                TarefaLlm.CONSOLIDACAO_SEMESTRAL, CenarioRelatorio.saidaSemestral("Bom semestre."));
        c.gerarSemestral.executar(c.clara.id(), c.matteo.id(), "2026-1", false, Canal.WEB);
        c.processarFila();
        return c.semestrais.relatorios.values().iterator().next();
    }

    @Test
    void semestresDesdeAPrimeiraAulaComOMaisRecentePreSelecionado() {
        c.aulaAprovada(c.matteo, LocalDate.of(2025, 11, 3), "Escala");

        List<Semestre> semestres = c.listarSemestres.executar(c.clara.id(), c.matteo.id());

        assertThat(semestres)
                .extracting(s -> s.periodo().chave())
                .containsExactly("2026-2", "2026-1", "2025-2");
        assertThat(semestres.getFirst().emAndamento()).isTrue();
        assertThat(semestres.getFirst().preSelecionado()).isTrue();
        assertThat(semestres.get(1).preSelecionado()).isFalse();
        codigo(
                () -> c.listarSemestres.executar(c.clara.id(), c.helena.id()),
                CodigoErro.ALUNO_NAO_ASSOCIADO);
    }

    @Test
    void previaContaAprovadasEEmAbertoDoPeriodo() {
        duasAulasAprovadasNo1Semestre();
        c.aulaAprovada(c.matteo, LocalDate.of(2026, 8, 1), "Fora do período");

        Previa previa = c.previa.executar(c.clara.id(), c.matteo.id(), "2026-1");

        assertThat(previa.aulasAprovadas()).isEqualTo(2);
        assertThat(previa.relatoriosAulaEmAberto()).isZero();
        assertThat(previa.semestralAprovadoId()).isEmpty();
        assertThat(previa.semestralEmAberto()).isEmpty();
    }

    @Test
    void semAulasAprovadasNadaECriadoENaoChamaAIa() {
        codigo(
                () ->
                        c.gerarSemestral.executar(
                                c.clara.id(), c.matteo.id(), "2026-1", false, Canal.WEB),
                CodigoErro.SEM_AULAS_APROVADAS_NO_PERIODO);
        assertThat(c.fila.trabalhos).isEmpty();
        assertThat(c.llm.entradas).isEmpty();
    }

    @Test
    void relatoriosDeAulaEmAbertoExigemConfirmacao() {
        duasAulasAprovadasNo1Semestre();
        RelatorioAula emAberto = c.relatorioComPerguntas();
        // a aula em aberto é de hoje (2º semestre): leva-se uma do 1º para dentro do período
        c.relatorios.aulas.put(
                emAberto.aulaId(),
                new br.com.felipeblaft.relatorios.dominio.relatorio.Aula(
                        emAberto.aulaId(),
                        c.clara.id(),
                        c.matteo.id(),
                        LocalDate.of(2026, 4, 1),
                        c.relogio.agora()));

        assertThatThrownBy(
                        () ->
                                c.gerarSemestral.executar(
                                        c.clara.id(), c.matteo.id(), "2026-1", false, Canal.WEB))
                .isInstanceOfSatisfying(
                        ExcecaoDominio.class,
                        e -> {
                            assertThat(e.codigo()).isEqualTo(CodigoErro.HA_RELATORIOS_EM_ABERTO);
                            assertThat(e.detalhes()).containsEntry("quantidade", 1);
                        });

        TrabalhoProcessamento trabalho =
                c.gerarSemestral.executar(c.clara.id(), c.matteo.id(), "2026-1", true, Canal.WEB);
        assertThat(trabalho.tipo()).isEqualTo(TipoTrabalho.CONSOLIDACAO_SEMESTRAL);
        assertThat(trabalho.parametro(TrabalhoProcessamento.PERIODO_CHAVE)).contains("2026-1");
    }

    @Test
    void consolidacaoEmCursoContaComoEmAberto() {
        duasAulasAprovadasNo1Semestre();
        c.gerarSemestral.executar(c.clara.id(), c.matteo.id(), "2026-1", false, Canal.TELEGRAM);

        assertThat(c.previa.executar(c.clara.id(), c.matteo.id(), "2026-1").semestralEmAberto())
                .hasValueSatisfying(a -> assertThat(a.canalOrigem()).isEqualTo(Canal.TELEGRAM));
        codigo(
                () ->
                        c.gerarSemestral.executar(
                                c.clara.id(), c.matteo.id(), "2026-1", false, Canal.WEB),
                CodigoErro.RELATORIO_EM_ABERTO);
    }

    @Test
    void consolidacaoUsaSoAsAulasAprovadasComDatasESemDadosPessoais() {
        RelatorioSemestral relatorio = semestralEmConferencia();

        EntradaConsolidacaoSemestral entrada =
                (EntradaConsolidacaoSemestral) c.llm.entradas.getFirst();
        assertThat(entrada.aulas())
                .extracting(a -> a.dataAula())
                .containsExactly(LocalDate.of(2026, 3, 10), LocalDate.of(2026, 5, 20));
        assertThat(entrada.quantidadeAulas()).isEqualTo(2);
        assertThat(entrada.toString()).doesNotContain("Matteo").doesNotContain("2012");
        assertThat(relatorio.status()).isEqualTo(StatusRelatorio.AGUARDANDO_CONFERENCIA);
        assertThat(relatorio.versaoVigente()).isEqualTo(1);
        assertThat(relatorio.quantidadeAulasConsideradas()).isEqualTo(2);
        assertThat(relatorio.informacoesGerais().nome()).isEqualTo("Matteo Mafra");
        assertThat(relatorio.informacoesGerais().professor()).isEqualTo("Clara Souza");
        assertThat(relatorio.conteudo().parecerFinal()).isEqualTo("Bom semestre.");
        assertThat(c.pdf.modelosSemestrais).hasSize(1);
        TrabalhoProcessamento trabalho = c.fila.trabalhos.values().iterator().next();
        assertThat(trabalho.resultado()).isEqualTo(ResultadoTrabalho.PRONTO_PARA_CONFERENCIA);
        assertThat(trabalho.relatorioId()).isEqualTo(relatorio.id());
        assertThat(c.notificacao.eventos)
                .contains("etapa:CONSOLIDANDO", "resultado:PRONTO_PARA_CONFERENCIA");
    }

    @Test
    void falhaNaConsolidacaoNaoCriaNada() {
        duasAulasAprovadasNo1Semestre();
        c.llm.programar(TarefaLlm.CONSOLIDACAO_SEMESTRAL, new ProvedorIndisponivelExcecao("teste"));
        c.gerarSemestral.executar(c.clara.id(), c.matteo.id(), "2026-1", false, Canal.WEB);

        c.processarFila();

        assertThat(c.semestrais.relatorios).isEmpty();
        assertThat(c.fila.trabalhos.values().iterator().next().resultado())
                .isEqualTo(ResultadoTrabalho.FALHA_CONSOLIDACAO);
        // pode gerar de novo
        c.gerarSemestral.executar(c.clara.id(), c.matteo.id(), "2026-1", false, Canal.WEB);
    }

    @Test
    void alteracaoGeraNovaVersaoESemMudancaNao() {
        RelatorioSemestral relatorio = semestralEmConferencia();
        c.llm.programar(
                TarefaLlm.REVISAO_SEMESTRAL,
                CenarioRelatorio.saidaSemestral("Ótimo semestre."),
                br.com.felipeblaft.relatorios.porta.SaidaRelatorioSemestralIa.de(
                        br.com.felipeblaft.relatorios.dominio.semestral.ConteudoRelatorioSemestral
                                .vazio(),
                        true,
                        "APROVAR"));

        c.alterarSemestral.alterarPorTexto(
                c.clara.id(), relatorio.id(), "Diga que foi ótimo.", 1, Canal.TELEGRAM);
        c.processarFila();
        assertThat(relatorio.versaoVigente()).isEqualTo(2);
        assertThat(relatorio.conteudo().parecerFinal()).isEqualTo("Ótimo semestre.");
        assertThat(c.llm.entradas.getLast()).isInstanceOf(EntradaRevisaoSemestral.class);
        assertThat(
                        c.interacoes
                                .ultima(TipoRelatorio.SEMESTRAL, relatorio.id())
                                .orElseThrow()
                                .resultado())
                .isEqualTo(ResultadoInteracao.APLICADA);

        c.alterarSemestral.alterarPorTexto(c.clara.id(), relatorio.id(), "aprova aí", 2, Canal.WEB);
        c.processarFila();
        assertThat(relatorio.versaoVigente()).isEqualTo(2);
        assertThat(c.fila.trabalhos.values().stream().reduce((a, b) -> b).orElseThrow().resultado())
                .isEqualTo(ResultadoTrabalho.PEDIDO_DE_ACAO_IGNORADO);

        codigo(
                () ->
                        c.alterarSemestral.alterarPorTexto(
                                c.clara.id(), relatorio.id(), "x", 1, Canal.WEB),
                CodigoErro.VERSAO_DESATUALIZADA);
        codigo(
                () ->
                        c.alterarSemestral.alterarPorTexto(
                                c.clara.id(), relatorio.id(), "  ", 2, Canal.WEB),
                CodigoErro.ALTERACAO_VAZIA);
    }

    @Test
    void aprovarGeraOficialENovaGeracaoERecusada() {
        RelatorioSemestral relatorio = semestralEmConferencia();

        Aprovacao aprovacao =
                c.aprovarSemestral.executar(c.clara.id(), relatorio.id(), 1, Canal.WEB);

        assertThat(aprovacao.versaoAprovada()).isEqualTo(1);
        assertThat(c.pdf.modelosSemestrais.getLast().situacao().name()).isEqualTo("OFICIAL");
        DocumentoObtido oficial =
                c.obterDocumento.oficial(c.clara.id(), TipoRelatorio.SEMESTRAL, relatorio.id());
        assertThat(oficial.nomeArquivo()).isEqualTo("relatorio-semestral-2026-1.pdf");
        codigo(
                () ->
                        c.gerarSemestral.executar(
                                c.clara.id(), c.matteo.id(), "2026-1", false, Canal.WEB),
                CodigoErro.SEMESTRAL_JA_APROVADO);
        codigo(
                () -> c.aprovarSemestral.executar(c.clara.id(), relatorio.id(), 1, Canal.TELEGRAM),
                CodigoErro.RELATORIO_JA_APROVADO);
    }

    @Test
    void cancelarLiberaNovaGeracaoEOutroProfessorNaoVe() {
        RelatorioSemestral relatorio = semestralEmConferencia();
        codigo(
                () -> c.obterSemestral.executar(c.bruno.id(), relatorio.id()),
                CodigoErro.RECURSO_NAO_ENCONTRADO);

        VisaoRelatorioSemestral visao = c.obterSemestral.executar(c.clara.id(), relatorio.id());
        assertThat(visao.pdfDisponivel()).isTrue();
        assertThat(
                        c.obterDocumento
                                .conferencia(
                                        c.clara.id(), TipoRelatorio.SEMESTRAL, relatorio.id(), 1)
                                .nomeArquivo())
                .isEqualTo("relatorio-semestral-2026-1-v1.pdf");

        c.cancelarSemestral.cancelar(c.clara.id(), relatorio.id());

        assertThat(relatorio.status()).isEqualTo(StatusRelatorio.CANCELADO);
        c.llm.programar(
                TarefaLlm.CONSOLIDACAO_SEMESTRAL, CenarioRelatorio.saidaSemestral("De novo."));
        c.gerarSemestral.executar(c.clara.id(), c.matteo.id(), "2026-1", false, Canal.WEB);
        c.processarFila();
        assertThat(c.semestrais.relatorios).hasSize(2);
    }

    @Test
    void descartarConsolidacaoEmCursoIgnoraOResultado() {
        duasAulasAprovadasNo1Semestre();
        TrabalhoProcessamento trabalho =
                c.gerarSemestral.executar(c.clara.id(), c.matteo.id(), "2026-1", false, Canal.WEB);

        c.cancelarSemestral.descartarConsolidacao(c.clara.id(), trabalho.id());
        c.processarFila();

        assertThat(c.semestrais.relatorios).isEmpty();
        codigo(
                () -> c.cancelarSemestral.descartarConsolidacao(c.bruno.id(), trabalho.id()),
                CodigoErro.RECURSO_NAO_ENCONTRADO);
    }
}
