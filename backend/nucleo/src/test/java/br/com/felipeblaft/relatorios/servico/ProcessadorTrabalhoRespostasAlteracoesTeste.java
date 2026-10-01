package br.com.felipeblaft.relatorios.servico;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.felipeblaft.relatorios.dominio.comum.Canal;
import br.com.felipeblaft.relatorios.dominio.comum.Meio;
import br.com.felipeblaft.relatorios.dominio.relatorio.InteracaoRelatorio;
import br.com.felipeblaft.relatorios.dominio.relatorio.OrigemVersao;
import br.com.felipeblaft.relatorios.dominio.relatorio.RelatorioAula;
import br.com.felipeblaft.relatorios.dominio.relatorio.ResultadoInteracao;
import br.com.felipeblaft.relatorios.dominio.relatorio.StatusRelatorio;
import br.com.felipeblaft.relatorios.dominio.relatorio.TipoInteracao;
import br.com.felipeblaft.relatorios.dominio.trabalho.ResultadoTrabalho;
import br.com.felipeblaft.relatorios.dominio.trabalho.TrabalhoProcessamento;
import br.com.felipeblaft.relatorios.dubles.CenarioRelatorio;
import br.com.felipeblaft.relatorios.porta.AudioNaoProcessavelExcecao;
import br.com.felipeblaft.relatorios.porta.EntradaTarefa;
import br.com.felipeblaft.relatorios.porta.ProvedorIndisponivelExcecao;
import br.com.felipeblaft.relatorios.porta.SaidaIaInvalidaExcecao;
import br.com.felipeblaft.relatorios.porta.TarefaLlm;
import java.util.List;
import org.junit.jupiter.api.Test;

/** ProcessadorTrabalhoServico: respostas às perguntas e alterações, por texto e por áudio. */
class ProcessadorTrabalhoRespostasAlteracoesTeste {

    private final CenarioRelatorio c = new CenarioRelatorio();

    private ResultadoTrabalho resultado(TrabalhoProcessamento trabalho) {
        c.processarFila();
        return c.fila.buscarPorId(trabalho.id()).orElseThrow().resultado();
    }

    // ------------------------------------------------------------ respostas

    @Test
    void respostaPorTextoIncorporadaVaiParaConferencia() {
        RelatorioAula relatorio = c.relatorioComPerguntas();
        c.llm.programar(
                TarefaLlm.INCORPORACAO_RESPOSTAS,
                CenarioRelatorio.saida("Estudou três vezes.", List.of()));

        TrabalhoProcessamento trabalho =
                c.responder.responderTexto(
                        c.clara.id(), relatorio.id(), "Czerny; estudou três vezes.", Canal.WEB);

        assertThat(resultado(trabalho)).isEqualTo(ResultadoTrabalho.PRONTO_PARA_CONFERENCIA);
        assertThat(relatorio.status()).isEqualTo(StatusRelatorio.AGUARDANDO_CONFERENCIA);
        assertThat(relatorio.conteudo().evolucao()).isEqualTo("Estudou três vezes.");
        assertThat(c.versoes.versoes)
                .singleElement()
                .extracting(v -> v.origem())
                .isEqualTo(OrigemVersao.RESPOSTA_PERGUNTAS);
        InteracaoRelatorio interacao = c.interacoes.interacoes.getFirst();
        assertThat(interacao.tipo()).isEqualTo(TipoInteracao.RESPOSTA_PERGUNTAS);
        assertThat(interacao.meio()).isEqualTo(Meio.TEXTO);
        assertThat(interacao.canal()).isEqualTo(Canal.WEB);
        assertThat(interacao.rodada()).isEqualTo(1);
        assertThat(c.versoes.versoes.getFirst().interacaoId()).isEqualTo(interacao.id());
        EntradaTarefa.IncorporacaoRespostas entrada =
                (EntradaTarefa.IncorporacaoRespostas) c.llm.entradas.getLast();
        assertThat(entrada.perguntas())
                .containsExactly("Qual repertório foi trabalhado?", "Estudou em casa?");
        assertThat(entrada.resposta()).isEqualTo("Czerny; estudou três vezes.");
    }

    @Test
    void respostaPorAudioMostraOTextoEntendidoESegundaRodada() {
        RelatorioAula relatorio = c.relatorioComPerguntas();
        c.transcricao.programar("Foi o Czerny.");
        c.llm.programar(
                TarefaLlm.INCORPORACAO_RESPOSTAS,
                CenarioRelatorio.saida("x", List.of("E a escala?")));

        TrabalhoProcessamento trabalho =
                c.responder.responderAudio(
                        c.clara.id(),
                        relatorio.id(),
                        CenarioRelatorio.arquivo("resp.ogg", 10),
                        Canal.TELEGRAM);

        assertThat(resultado(trabalho)).isEqualTo(ResultadoTrabalho.PERGUNTAS_PENDENTES);
        assertThat(c.fila.buscarPorId(trabalho.id()).orElseThrow().parametros())
                .containsEntry("textoEntendido", "Foi o Czerny.");
        assertThat(relatorio.rodadasPerguntas()).isEqualTo(2);
        assertThat(relatorio.etapa()).isNull();
        assertThat(c.interacoes.interacoes.getFirst().meio()).isEqualTo(Meio.AUDIO);
        assertThat(c.notificacao.eventos)
                .contains("etapa:TRANSCREVENDO_PEDIDO", "etapa:INCORPORANDO_RESPOSTAS");
    }

    @Test
    void transcricaoDaRespostaFalhaSemInteracaoESemConsumirRodada() {
        RelatorioAula relatorio = c.relatorioComPerguntas();
        c.transcricao.programar(
                new AudioNaoProcessavelExcecao(AudioNaoProcessavelExcecao.Motivo.SEM_FALA));

        TrabalhoProcessamento trabalho =
                c.responder.responderAudio(
                        c.clara.id(),
                        relatorio.id(),
                        CenarioRelatorio.arquivo("r.ogg", 10),
                        Canal.WEB);

        assertThat(resultado(trabalho)).isEqualTo(ResultadoTrabalho.FALHA_RESPOSTA);
        assertThat(relatorio.status()).isEqualTo(StatusRelatorio.AGUARDANDO_RESPOSTAS);
        assertThat(relatorio.rodadasPerguntas()).isEqualTo(1);
        assertThat(relatorio.etapa()).isNull();
        assertThat(c.interacoes.interacoes).isEmpty();
    }

    @Test
    void falhaDaIaNaRespostaRegistraInteracaoQueFalhou() {
        RelatorioAula relatorio = c.relatorioComPerguntas();
        c.llm.programar(TarefaLlm.INCORPORACAO_RESPOSTAS, new SaidaIaInvalidaExcecao("x"));
        TrabalhoProcessamento trabalho =
                c.responder.responderTexto(c.clara.id(), relatorio.id(), "ok", Canal.WEB);
        assertThat(resultado(trabalho)).isEqualTo(ResultadoTrabalho.FALHA_RESPOSTA);
        assertThat(c.interacoes.interacoes.getFirst().resultado())
                .isEqualTo(ResultadoInteracao.FALHOU);
        assertThat(relatorio.status()).isEqualTo(StatusRelatorio.AGUARDANDO_RESPOSTAS);
    }

    // ------------------------------------------------------------ alterações

    @Test
    void alteracaoPorTextoCriaNovaVersaoComPdf() {
        RelatorioAula relatorio = c.relatorioEmConferencia();
        c.llm.programar(
                TarefaLlm.REVISAO_AULA,
                CenarioRelatorio.revisao("Leitura mais fácil.", false, "NENHUM"));

        TrabalhoProcessamento trabalho =
                c.alterar.alterarPorTexto(
                        c.clara.id(),
                        relatorio.id(),
                        "A leitura foi mais fácil.",
                        1,
                        Canal.TELEGRAM);

        assertThat(resultado(trabalho)).isEqualTo(ResultadoTrabalho.NOVA_VERSAO);
        assertThat(relatorio.versaoVigente()).isEqualTo(2);
        assertThat(relatorio.conteudo().evolucao()).isEqualTo("Leitura mais fácil.");
        assertThat(c.versoes.versoes).hasSize(2);
        assertThat(c.versoes.versoes.getFirst().conteudo())
                .isNotEqualTo(c.versoes.versoes.getLast().conteudo());
        assertThat(c.pdf.modelos).extracting(m -> m.versao()).containsExactly(1, 2);
        InteracaoRelatorio interacao = c.interacoes.interacoes.getLast();
        assertThat(interacao.resultado()).isEqualTo(ResultadoInteracao.APLICADA);
        assertThat(interacao.versaoBase()).isEqualTo(1);
        assertThat(interacao.versaoResultante()).isEqualTo(2);
    }

    @Test
    void alteracaoPorAudioDevolveOTextoEntendido() {
        RelatorioAula relatorio = c.relatorioEmConferencia();
        c.transcricao.programar("Troque a observação para: estudou pouco.");
        c.llm.programar(
                TarefaLlm.REVISAO_AULA,
                CenarioRelatorio.revisao("Nova evolução.", false, "NENHUM"));

        TrabalhoProcessamento trabalho =
                c.alterar.alterarPorAudio(
                        c.clara.id(),
                        relatorio.id(),
                        CenarioRelatorio.arquivo("alt.ogg", 5),
                        1,
                        Canal.WEB);

        assertThat(resultado(trabalho)).isEqualTo(ResultadoTrabalho.NOVA_VERSAO);
        assertThat(c.fila.buscarPorId(trabalho.id()).orElseThrow().parametros())
                .containsEntry("textoEntendido", "Troque a observação para: estudou pouco.");
        assertThat(((EntradaTarefa.RevisaoAula) c.llm.entradas.getLast()).pedido())
                .isEqualTo("Troque a observação para: estudou pouco.");
        assertThat(c.interacoes.interacoes.getLast().meio()).isEqualTo(Meio.AUDIO);
    }

    @Test
    void pedidoDeAcaoNuncaAprovaNemCancela() {
        RelatorioAula relatorio = c.relatorioEmConferencia();
        c.llm.programar(
                TarefaLlm.REVISAO_AULA,
                CenarioRelatorio.revisao("Evoluiu na articulação.", true, "APROVAR"));
        TrabalhoProcessamento trabalho =
                c.alterar.alterarPorTexto(
                        c.clara.id(), relatorio.id(), "pode aprovar", 1, Canal.WEB);

        assertThat(resultado(trabalho)).isEqualTo(ResultadoTrabalho.PEDIDO_DE_ACAO_IGNORADO);
        assertThat(relatorio.status()).isEqualTo(StatusRelatorio.AGUARDANDO_CONFERENCIA);
        assertThat(relatorio.versaoVigente()).isEqualTo(1);
        assertThat(relatorio.etapa()).isNull();
        assertThat(c.interacoes.interacoes.getLast().resultado())
                .isEqualTo(ResultadoInteracao.PEDIDO_DE_ACAO_IGNORADO);
    }

    @Test
    void semAlteracaoDeConteudoOuConteudoIdenticoNaoCriaVersao() {
        RelatorioAula relatorio = c.relatorioEmConferencia();
        c.llm.programar(
                TarefaLlm.REVISAO_AULA,
                CenarioRelatorio.revisao("x", true, "NENHUM"),
                CenarioRelatorio.revisao("Evoluiu na articulação.", false, null));

        TrabalhoProcessamento sem =
                c.alterar.alterarPorTexto(c.clara.id(), relatorio.id(), "hmm", 1, Canal.WEB);
        assertThat(resultado(sem)).isEqualTo(ResultadoTrabalho.SEM_ALTERACAO_DE_CONTEUDO);
        TrabalhoProcessamento identico =
                c.alterar.alterarPorTexto(c.clara.id(), relatorio.id(), "mantenha", 1, Canal.WEB);
        assertThat(resultado(identico)).isEqualTo(ResultadoTrabalho.SEM_ALTERACAO_DE_CONTEUDO);

        assertThat(relatorio.versaoVigente()).isEqualTo(1);
        assertThat(c.versoes.versoes).hasSize(1);
    }

    @Test
    void versaoBaseDesatualizadaNoProcessamentoERecusada() {
        RelatorioAula relatorio = c.relatorioEmConferencia();
        c.llm.programar(TarefaLlm.REVISAO_AULA, CenarioRelatorio.revisao("A.", false, "NENHUM"));
        TrabalhoProcessamento primeira =
                c.alterar.alterarPorTexto(c.clara.id(), relatorio.id(), "a", 1, Canal.WEB);
        // um segundo pedido já na fila, feito sobre a versão 1, chega depois da versão 2
        relatorio.concluirEtapa(c.relogio.agora());
        TrabalhoProcessamento segunda =
                c.alterar.alterarPorTexto(c.clara.id(), relatorio.id(), "b", 1, Canal.TELEGRAM);

        assertThat(resultado(primeira)).isEqualTo(ResultadoTrabalho.NOVA_VERSAO);
        assertThat(c.fila.buscarPorId(segunda.id()).orElseThrow().resultado())
                .isEqualTo(ResultadoTrabalho.VERSAO_DESATUALIZADA);
        assertThat(c.interacoes.interacoes.getLast().resultado())
                .isEqualTo(ResultadoInteracao.RECUSADA_VERSAO_DESATUALIZADA);
        assertThat(relatorio.etapa()).isNull();
    }

    @Test
    void falhasNaAlteracaoMantemAVersao() {
        RelatorioAula relatorio = c.relatorioEmConferencia();
        c.transcricao.programar(
                new AudioNaoProcessavelExcecao(AudioNaoProcessavelExcecao.Motivo.VAZIO));
        TrabalhoProcessamento audio =
                c.alterar.alterarPorAudio(
                        c.clara.id(),
                        relatorio.id(),
                        CenarioRelatorio.arquivo("v.ogg", 1),
                        1,
                        Canal.WEB);
        assertThat(resultado(audio)).isEqualTo(ResultadoTrabalho.FALHA_ALTERACAO);
        assertThat(c.interacoes.interacoes).isEmpty();

        c.llm.programar(TarefaLlm.REVISAO_AULA, new ProvedorIndisponivelExcecao("503"));
        TrabalhoProcessamento ia =
                c.alterar.alterarPorTexto(c.clara.id(), relatorio.id(), "x", 1, Canal.WEB);
        assertThat(resultado(ia)).isEqualTo(ResultadoTrabalho.FALHA_ALTERACAO);
        assertThat(c.interacoes.interacoes.getLast().resultado())
                .isEqualTo(ResultadoInteracao.FALHOU);
        assertThat(relatorio.versaoVigente()).isEqualTo(1);
        assertThat(relatorio.etapa()).isNull();
        assertThat(ProcessadorTrabalhoServico.codigoDaFalha(new SaidaIaInvalidaExcecao("x")))
                .isEqualTo("SAIDA_INVALIDA");
    }

    @Test
    void processadorComplementarAtendeOutrosTipos() {
        final boolean[] chamado = new boolean[1];
        c.processador.registrarComplementar(
                new ProcessadorTrabalhoServico.ProcessadorComplementar() {
                    @Override
                    public boolean atende(TrabalhoProcessamento trabalho) {
                        return true;
                    }

                    @Override
                    public void processar(
                            TrabalhoProcessamento trabalho,
                            ProcessadorTrabalhoServico.ApoioProcessamento apoio) {
                        chamado[0] = true;
                    }
                });
        TrabalhoProcessamento semestral =
                TrabalhoProcessamento.novo(
                        br.com.felipeblaft.relatorios.dominio.trabalho.TipoTrabalho
                                .CONSOLIDACAO_SEMESTRAL,
                        c.clara.id(),
                        c.matteo.id(),
                        null,
                        null,
                        Canal.WEB,
                        Meio.TEXTO,
                        br.com.felipeblaft.relatorios.dominio.trabalho.EtapaTrabalho.CONSOLIDANDO,
                        c.relogio.agora());
        c.fila.enfileirar(semestral);
        c.processarFila();
        assertThat(chamado[0]).isTrue();
    }
}
