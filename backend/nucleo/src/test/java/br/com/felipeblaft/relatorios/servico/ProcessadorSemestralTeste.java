package br.com.felipeblaft.relatorios.servico;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.felipeblaft.relatorios.dominio.comum.Canal;
import br.com.felipeblaft.relatorios.dominio.comum.CodigoErro;
import br.com.felipeblaft.relatorios.dominio.comum.ExcecaoDominio;
import br.com.felipeblaft.relatorios.dominio.comum.Meio;
import br.com.felipeblaft.relatorios.dominio.comum.TipoRelatorio;
import br.com.felipeblaft.relatorios.dominio.relatorio.ResultadoInteracao;
import br.com.felipeblaft.relatorios.dominio.semestral.ConteudoRelatorioSemestral;
import br.com.felipeblaft.relatorios.dominio.semestral.InformacoesGerais;
import br.com.felipeblaft.relatorios.dominio.semestral.PeriodoSemestral;
import br.com.felipeblaft.relatorios.dominio.semestral.RelatorioSemestral;
import br.com.felipeblaft.relatorios.dominio.trabalho.EtapaTrabalho;
import br.com.felipeblaft.relatorios.dominio.trabalho.ResultadoTrabalho;
import br.com.felipeblaft.relatorios.dominio.trabalho.StatusTrabalho;
import br.com.felipeblaft.relatorios.dominio.trabalho.TipoTrabalho;
import br.com.felipeblaft.relatorios.dominio.trabalho.TrabalhoProcessamento;
import br.com.felipeblaft.relatorios.dubles.CenarioRelatorio;
import br.com.felipeblaft.relatorios.porta.AudioNaoProcessavelExcecao;
import br.com.felipeblaft.relatorios.porta.ProvedorIndisponivelExcecao;
import br.com.felipeblaft.relatorios.porta.SaidaRelatorioSemestralIa;
import br.com.felipeblaft.relatorios.porta.TarefaLlm;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Caminhos de falha, descarte e concorrência do processamento do semestral (FR-045, FR-022). */
class ProcessadorSemestralTeste {

    private final CenarioRelatorio c = new CenarioRelatorio();

    private RelatorioSemestral emConferencia() {
        c.aulaAprovada(c.matteo, LocalDate.of(2026, 3, 10), "Escala");
        c.llm.programar(
                TarefaLlm.CONSOLIDACAO_SEMESTRAL, CenarioRelatorio.saidaSemestral("Bom semestre."));
        c.gerarSemestral.executar(c.clara.id(), c.matteo.id(), "2026-1", false, Canal.WEB);
        c.processarFila();
        return c.semestrais.relatorios.values().iterator().next();
    }

    private TrabalhoProcessamento ultimo() {
        return c.fila.trabalhos.values().stream().reduce((a, b) -> b).orElseThrow();
    }

    private ResultadoInteracao ultimaInteracao(RelatorioSemestral relatorio) {
        return c.interacoes
                .ultima(TipoRelatorio.SEMESTRAL, relatorio.id())
                .orElseThrow()
                .resultado();
    }

    @Test
    void aulasDesaparecidasAntesDaConsolidacaoFalhamSemChamarAIa() {
        c.aulaAprovada(c.matteo, LocalDate.of(2026, 3, 10), "Escala");
        c.gerarSemestral.executar(c.clara.id(), c.matteo.id(), "2026-1", false, Canal.WEB);
        c.relatorios.relatorios.clear();

        c.processarFila();

        assertThat(ultimo().resultado()).isEqualTo(ResultadoTrabalho.FALHA_CONSOLIDACAO);
        assertThat(c.llm.entradas).isEmpty();
    }

    @Test
    void semestralAbertoEmOutroCanalDuranteAConsolidacaoNaoDuplica() {
        c.aulaAprovada(c.matteo, LocalDate.of(2026, 3, 10), "Escala");
        c.llm.programar(TarefaLlm.CONSOLIDACAO_SEMESTRAL, CenarioRelatorio.saidaSemestral("Bom."));
        c.gerarSemestral.executar(c.clara.id(), c.matteo.id(), "2026-1", false, Canal.WEB);
        PeriodoSemestral periodo = PeriodoSemestral.de("2026-1");
        c.semestrais.salvar(
                RelatorioSemestral.criar(
                        c.clara.id(),
                        c.matteo.id(),
                        periodo,
                        1,
                        0,
                        InformacoesGerais.calcular(
                                "Matteo Mafra",
                                LocalDate.of(2012, 5, 10),
                                "Piano",
                                "Clara Souza",
                                periodo,
                                1,
                                LocalDate.of(2026, 9, 21)),
                        ConteudoRelatorioSemestral.vazio(),
                        Canal.TELEGRAM,
                        c.relogio.agora()));

        c.processarFila();

        assertThat(c.semestrais.relatorios).hasSize(1);
        assertThat(ultimo().resultado()).isEqualTo(ResultadoTrabalho.FALHA_CONSOLIDACAO);
    }

    @Test
    void alteracaoPorAudioComTranscricaoETextoEntendido() {
        RelatorioSemestral relatorio = emConferencia();
        c.transcricao.programar("Parecer: muito bom.");
        c.llm.programar(TarefaLlm.REVISAO_SEMESTRAL, CenarioRelatorio.saidaSemestral("Muito bom."));

        c.alterarSemestral.alterarPorAudio(
                c.clara.id(),
                relatorio.id(),
                CenarioRelatorio.arquivo("pedido.ogg", 100),
                1,
                Canal.WEB);
        c.processarFila();

        assertThat(relatorio.versaoVigente()).isEqualTo(2);
        assertThat(ultimo().meio()).isEqualTo(Meio.AUDIO);
        assertThat(ultimo().parametro(TrabalhoProcessamento.TEXTO_ENTENDIDO))
                .contains("Parecer: muito bom.");
        assertThat(c.normalizacao.apagados).isNotEmpty();
    }

    @Test
    void transcricaoDoPedidoFalhaSemNovaVersao() {
        RelatorioSemestral relatorio = emConferencia();
        c.transcricao.programar(
                new AudioNaoProcessavelExcecao(AudioNaoProcessavelExcecao.Motivo.SEM_FALA));

        c.alterarSemestral.alterarPorAudio(
                c.clara.id(),
                relatorio.id(),
                CenarioRelatorio.arquivo("mudo.ogg", 100),
                1,
                Canal.TELEGRAM);
        c.processarFila();

        assertThat(ultimo().resultado()).isEqualTo(ResultadoTrabalho.FALHA_ALTERACAO);
        assertThat(relatorio.versaoVigente()).isEqualTo(1);
        assertThat(relatorio.etapa()).isNull();
    }

    @Test
    void falhaDaIaNaRevisaoRegistraAInteracaoESemMudancaNaoCriaVersao() {
        RelatorioSemestral relatorio = emConferencia();
        c.llm.programar(
                TarefaLlm.REVISAO_SEMESTRAL,
                new ProvedorIndisponivelExcecao("teste"),
                SaidaRelatorioSemestralIa.de(relatorio.conteudo(), false, "NENHUM"));

        c.alterarSemestral.alterarPorTexto(
                c.clara.id(), relatorio.id(), "Mude algo.", 1, Canal.WEB);
        c.processarFila();
        assertThat(ultimo().resultado()).isEqualTo(ResultadoTrabalho.FALHA_ALTERACAO);
        assertThat(ultimaInteracao(relatorio)).isEqualTo(ResultadoInteracao.FALHOU);

        c.alterarSemestral.alterarPorTexto(
                c.clara.id(), relatorio.id(), "Mude de novo.", 1, Canal.WEB);
        c.processarFila();
        assertThat(ultimo().resultado()).isEqualTo(ResultadoTrabalho.SEM_ALTERACAO_DE_CONTEUDO);
        assertThat(ultimaInteracao(relatorio))
                .isEqualTo(ResultadoInteracao.SEM_ALTERACAO_DE_CONTEUDO);
        assertThat(relatorio.versaoVigente()).isEqualTo(1);
    }

    @Test
    void versaoAlteradaEmOutroCanalAntesDoProcessamentoERecusada() {
        RelatorioSemestral relatorio = emConferencia();
        c.alterarSemestral.alterarPorTexto(c.clara.id(), relatorio.id(), "Mude.", 1, Canal.WEB);
        relatorio.aplicarAlteracao(ConteudoRelatorioSemestral.vazio(), c.relogio.agora());

        c.processarFila();

        assertThat(ultimo().resultado()).isEqualTo(ResultadoTrabalho.VERSAO_DESATUALIZADA);
        assertThat(ultimaInteracao(relatorio))
                .isEqualTo(ResultadoInteracao.RECUSADA_VERSAO_DESATUALIZADA);
    }

    @Test
    void cancelamentoDuranteOProcessamentoDescartaOResultado() {
        RelatorioSemestral relatorio = emConferencia();
        c.alterarSemestral.alterarPorTexto(c.clara.id(), relatorio.id(), "Mude.", 1, Canal.WEB);
        TrabalhoProcessamento trabalho = c.fila.reservarProximo(c.relogio.agora()).orElseThrow();
        relatorio.cancelar(c.relogio.agora());

        c.processador.processar(trabalho);

        assertThat(trabalho.status()).isEqualTo(StatusTrabalho.DESCARTADO);
        assertThat(relatorio.versaoVigente()).isEqualTo(1);
    }

    @Test
    void erroInesperadoNaoDeixaOSemestralPresoNaEtapa() {
        RelatorioSemestral relatorio = emConferencia();
        c.llm.programar(TarefaLlm.REVISAO_SEMESTRAL, new IllegalStateException("falha inesperada"));
        c.alterarSemestral.alterarPorTexto(c.clara.id(), relatorio.id(), "Mude.", 1, Canal.WEB);

        assertThatThrownBy(c::processarFila).isInstanceOf(IllegalStateException.class);

        assertThat(relatorio.etapa()).isNull();
        assertThat(ultimo().resultado()).isEqualTo(ResultadoTrabalho.FALHA_ALTERACAO);
    }

    @Test
    void descarteDeTrabalhoQueNaoERegistroNemConsolidacaoERecusado() {
        RelatorioSemestral relatorio = emConferencia();
        TrabalhoProcessamento alteracao =
                c.alterarSemestral.alterarPorTexto(
                        c.clara.id(), relatorio.id(), "Mude.", 1, Canal.WEB);
        assertThatThrownBy(() -> c.descartarRegistro.executar(c.clara.id(), alteracao.id()))
                .isInstanceOfSatisfying(
                        ExcecaoDominio.class,
                        e ->
                                assertThat(e.codigo())
                                        .isEqualTo(CodigoErro.ACAO_NAO_PERMITIDA_NA_ETAPA));
        c.llm.programar(TarefaLlm.REVISAO_SEMESTRAL, CenarioRelatorio.saidaSemestral("Outro."));
        c.processarFila();

        c.relatorios.relatorios.clear();
        c.aulaAprovada(c.matteo, LocalDate.of(2026, 9, 1), "Escala");
        TrabalhoProcessamento consolidacao =
                c.gerarSemestral.executar(c.clara.id(), c.matteo.id(), "2026-2", false, Canal.WEB);
        c.descartarRegistro.executar(c.clara.id(), consolidacao.id());
        assertThat(consolidacao.status()).isEqualTo(StatusTrabalho.DESCARTADO);
        assertThatThrownBy(() -> c.descartarRegistro.executar(c.clara.id(), consolidacao.id()))
                .isInstanceOf(ExcecaoDominio.class);
        assertThat(consolidacao.tipo()).isEqualTo(TipoTrabalho.CONSOLIDACAO_SEMESTRAL);
        assertThat(consolidacao.etapa()).isEqualTo(EtapaTrabalho.CONSOLIDANDO);
    }

    @Test
    void audioAcimaDoLimiteNaAlteracaoERecusadoEApagado() {
        RelatorioSemestral relatorio = emConferencia();
        var grande = CenarioRelatorio.arquivo("grande.ogg", c.parametros.limiteAudioBytes() + 1);

        assertThatThrownBy(
                        () ->
                                c.alterarSemestral.alterarPorAudio(
                                        c.clara.id(), relatorio.id(), grande, 1, Canal.WEB))
                .isInstanceOfSatisfying(
                        ExcecaoDominio.class,
                        e -> assertThat(e.codigo()).isEqualTo(CodigoErro.AUDIO_MUITO_GRANDE));
        assertThat(c.normalizacao.apagados).contains(grande.caminho());
        assertThat(c.processadorSemestral.atende(ultimo())).isTrue();
        assertThat(List.of(relatorio.etapa() == null)).containsExactly(true);
    }
}
