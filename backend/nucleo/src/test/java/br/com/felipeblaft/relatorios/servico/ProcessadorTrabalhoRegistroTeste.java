package br.com.felipeblaft.relatorios.servico;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.felipeblaft.relatorios.dominio.comum.Canal;
import br.com.felipeblaft.relatorios.dominio.relatorio.OrigemVersao;
import br.com.felipeblaft.relatorios.dominio.relatorio.RelatorioAula;
import br.com.felipeblaft.relatorios.dominio.relatorio.StatusRelatorio;
import br.com.felipeblaft.relatorios.dominio.relatorio.TipoDocumento;
import br.com.felipeblaft.relatorios.dominio.trabalho.ChamadaIa;
import br.com.felipeblaft.relatorios.dominio.trabalho.ResultadoTrabalho;
import br.com.felipeblaft.relatorios.dominio.trabalho.StatusTrabalho;
import br.com.felipeblaft.relatorios.dominio.trabalho.TarefaIa;
import br.com.felipeblaft.relatorios.dominio.trabalho.TrabalhoProcessamento;
import br.com.felipeblaft.relatorios.dubles.CenarioRelatorio;
import br.com.felipeblaft.relatorios.porta.AudioNaoProcessavelExcecao;
import br.com.felipeblaft.relatorios.porta.EntradaTarefa;
import br.com.felipeblaft.relatorios.porta.FormatoNaoSuportadoExcecao;
import br.com.felipeblaft.relatorios.porta.GeracaoPdfPorta.SituacaoDocumento;
import br.com.felipeblaft.relatorios.porta.ProvedorIndisponivelExcecao;
import br.com.felipeblaft.relatorios.porta.ReferenciaArquivoCanal;
import br.com.felipeblaft.relatorios.porta.SaidaIaInvalidaExcecao;
import br.com.felipeblaft.relatorios.porta.TarefaLlm;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/** ProcessadorTrabalhoServico: registro de aula e nova tentativa. */
class ProcessadorTrabalhoRegistroTeste {

    private final CenarioRelatorio c = new CenarioRelatorio();

    private TrabalhoProcessamento registrarEProcessar(Canal canal) {
        TrabalhoProcessamento trabalho =
                c.registrar.executar(
                        c.clara.id(),
                        c.matteo.id(),
                        CenarioRelatorio.arquivo("aula.ogg", 10),
                        canal);
        c.processarFila();
        return c.fila.buscarPorId(trabalho.id()).orElseThrow();
    }

    @Test
    void sucessoSemPerguntasCriaAulaRelatorioVersaoUmEPdf() {
        c.transcricao.programar("Hoje trabalhamos o Czerny com o aluno.");
        c.llm.programar(TarefaLlm.ESTRUTURACAO_AULA, CenarioRelatorio.saida("Evoluiu.", List.of()));

        TrabalhoProcessamento trabalho = registrarEProcessar(Canal.WEB);

        assertThat(trabalho.status()).isEqualTo(StatusTrabalho.CONCLUIDO);
        assertThat(trabalho.resultado()).isEqualTo(ResultadoTrabalho.PRONTO_PARA_CONFERENCIA);
        RelatorioAula relatorio = c.relatorios.buscarPorId(trabalho.relatorioId()).orElseThrow();
        assertThat(relatorio.status()).isEqualTo(StatusRelatorio.AGUARDANDO_CONFERENCIA);
        assertThat(relatorio.transcricao()).isEqualTo("Hoje trabalhamos o Czerny com o aluno.");
        assertThat(relatorio.canalOrigem()).isEqualTo(Canal.WEB);
        assertThat(c.versoes.versoes)
                .singleElement()
                .satisfies(
                        v -> {
                            assertThat(v.origem()).isEqualTo(OrigemVersao.ESTRUTURACAO_INICIAL);
                            assertThat(v.promptVersao()).isEqualTo("estruturacao-aula.v1");
                        });
        assertThat(c.documentosPdf.documentos.values())
                .singleElement()
                .satisfies(d -> assertThat(d.tipo()).isEqualTo(TipoDocumento.CONFERENCIA));
        assertThat(c.pdf.modelos.getFirst().situacao()).isEqualTo(SituacaoDocumento.CONFERENCIA);
        assertThat(c.pdf.modelos.getFirst().alunoNome()).isEqualTo("Matteo Mafra");
        assertThat(c.armazenamento.arquivos)
                .containsKey("relatorios/aula/" + relatorio.id() + "/v1.pdf");
        assertThat(c.notificacao.eventos)
                .containsExactly(
                        "etapa:ENVIANDO",
                        "etapa:TRANSCREVENDO",
                        "etapa:ESTRUTURANDO",
                        "resultado:PRONTO_PARA_CONFERENCIA");
        assertThat(c.custos.chamadas)
                .extracting(ChamadaIa::tarefa)
                .containsExactly(TarefaIa.TRANSCRICAO, TarefaIa.ESTRUTURACAO);
        assertThat(c.custos.chamadas).allMatch(ChamadaIa::sucesso);
        assertThat(relatorio.tempoProcessamentoMs()).isGreaterThanOrEqualTo(0);
    }

    @Test
    void audioApagadoEmTodosOsCaminhosEEntradaDaIaSemDadosDoAluno() {
        c.llm.programar(TarefaLlm.ESTRUTURACAO_AULA, CenarioRelatorio.saida("x", List.of()));
        registrarEProcessar(Canal.WEB);

        assertThat(c.normalizacao.apagados)
                .contains(
                        Path.of("/tmp/audios/aula.ogg"),
                        Path.of("/tmp/audios/aula.ogg.normalizado.ogg"));
        assertThat(c.llm.entradas)
                .singleElement()
                .isInstanceOf(EntradaTarefa.EstruturacaoAula.class)
                .asString()
                .doesNotContain("Matteo", "Mafra");
        assertThat(c.fila.trabalhos.values()).allMatch(t -> t.arquivoTemporario() == null);
    }

    @Test
    void comPerguntasAguardaRespostasSemPdf() {
        c.llm.programar(
                TarefaLlm.ESTRUTURACAO_AULA, CenarioRelatorio.saida("x", List.of("Qual peça?")));

        TrabalhoProcessamento trabalho = registrarEProcessar(Canal.TELEGRAM);

        assertThat(trabalho.resultado()).isEqualTo(ResultadoTrabalho.PERGUNTAS_PENDENTES);
        RelatorioAula relatorio = c.relatorios.buscarPorId(trabalho.relatorioId()).orElseThrow();
        assertThat(relatorio.status()).isEqualTo(StatusRelatorio.AGUARDANDO_RESPOSTAS);
        assertThat(relatorio.perguntasPendentes()).containsExactly("Qual peça?");
        assertThat(c.versoes.versoes).isEmpty();
        assertThat(c.pdf.modelos).isEmpty();
    }

    @Test
    void transcricaoVaziaOuFormatoInvalidoNaoCriaNada() {
        c.transcricao.programar(
                new AudioNaoProcessavelExcecao(AudioNaoProcessavelExcecao.Motivo.SEM_FALA));
        TrabalhoProcessamento semFala = registrarEProcessar(Canal.WEB);
        assertThat(semFala.resultado()).isEqualTo(ResultadoTrabalho.AUDIO_NAO_PROCESSAVEL);
        assertThat(semFala.status()).isEqualTo(StatusTrabalho.FALHOU);

        c.normalizacao.falharCom(new FormatoNaoSuportadoExcecao());
        TrabalhoProcessamento formato = registrarEProcessar(Canal.WEB);
        assertThat(formato.resultado()).isEqualTo(ResultadoTrabalho.AUDIO_NAO_PROCESSAVEL);

        c.transcricao.programar("   ");
        TrabalhoProcessamento vazio = registrarEProcessar(Canal.WEB);
        assertThat(vazio.resultado()).isEqualTo(ResultadoTrabalho.AUDIO_NAO_PROCESSAVEL);

        assertThat(c.relatorios.relatorios).isEmpty();
        assertThat(c.relatorios.aulas).isEmpty();
        assertThat(c.normalizacao.apagados).contains(Path.of("/tmp/audios/aula.ogg"));
        assertThat(c.custos.chamadas).noneMatch(ChamadaIa::sucesso);
        assertThat(c.custos.chamadas)
                .extracting(ChamadaIa::codigoErro)
                .containsExactly("AUDIO_SEM_FALA", "FORMATO_NAO_SUPORTADO", "AUDIO_SEM_FALA");
    }

    @Test
    void provedorIndisponivelTentaDeNovoUmaVez() {
        c.transcricao.programar(
                new ProvedorIndisponivelExcecao("503"), "Transcrição na segunda tentativa.");
        c.llm.programar(TarefaLlm.ESTRUTURACAO_AULA, CenarioRelatorio.saida("x", List.of()));
        assertThat(registrarEProcessar(Canal.WEB).resultado())
                .isEqualTo(ResultadoTrabalho.PRONTO_PARA_CONFERENCIA);

        c.transcricao.programar(
                new ProvedorIndisponivelExcecao("503"), new ProvedorIndisponivelExcecao("503"));
        TrabalhoProcessamento falhou =
                c.registrar.executar(
                        c.clara.id(),
                        c.aluno(c.clara, "Olavo", "Violão").id(),
                        CenarioRelatorio.arquivo("b.ogg", 1),
                        Canal.WEB);
        c.processarFila();
        assertThat(c.fila.buscarPorId(falhou.id()).orElseThrow().resultado())
                .isEqualTo(ResultadoTrabalho.AUDIO_NAO_PROCESSAVEL);
        assertThat(c.custos.chamadas.getLast().codigoErro()).isEqualTo("PROVEDOR_INDISPONIVEL");
    }

    @Test
    void arquivoDoTelegramEBaixadoSoNoProcessamento() {
        c.llm.programar(TarefaLlm.ESTRUTURACAO_AULA, CenarioRelatorio.saida("x", List.of()));
        c.registrar.executar(
                c.clara.id(),
                c.matteo.id(),
                new ReferenciaArquivoCanal("file-9", "u-9", "video/mp4", 31_000_000),
                Canal.TELEGRAM);
        assertThat(c.arquivoCanal.obtidos).isEmpty();
        c.processarFila();
        assertThat(c.arquivoCanal.obtidos)
                .extracting(ReferenciaArquivoCanal::id)
                .containsExactly("file-9");
        assertThat(c.normalizacao.apagados).contains(Path.of("/tmp/audios/canal-file-9"));
    }

    @Test
    void saidaInvalidaFalhaComTranscricaoPreservadaENovaTentativa() {
        c.transcricao.programar("Transcrição preservada.");
        c.llm.programar(TarefaLlm.ESTRUTURACAO_AULA, new SaidaIaInvalidaExcecao("schema"));
        TrabalhoProcessamento trabalho = registrarEProcessar(Canal.WEB);

        assertThat(trabalho.resultado()).isEqualTo(ResultadoTrabalho.FALHA_ESTRUTURACAO);
        RelatorioAula relatorio = c.relatorios.buscarPorId(trabalho.relatorioId()).orElseThrow();
        assertThat(relatorio.status()).isEqualTo(StatusRelatorio.FALHA_PROCESSAMENTO);
        assertThat(relatorio.transcricao()).isEqualTo("Transcrição preservada.");

        c.llm.programar(TarefaLlm.ESTRUTURACAO_AULA, CenarioRelatorio.saida("x", List.of()));
        TrabalhoProcessamento novaTentativa =
                c.tentar.executar(c.clara.id(), relatorio.id(), Canal.TELEGRAM);
        int transcricoes = c.transcricao.recebidos.size();
        c.processarFila();

        assertThat(c.fila.buscarPorId(novaTentativa.id()).orElseThrow().resultado())
                .isEqualTo(ResultadoTrabalho.PRONTO_PARA_CONFERENCIA);
        assertThat(c.transcricao.recebidos).hasSize(transcricoes);
        assertThat(relatorio.status()).isEqualTo(StatusRelatorio.AGUARDANDO_CONFERENCIA);
    }

    @Test
    void cancelamentoDuranteOTrabalhoDescartaOResultado() {
        RelatorioAula falha = cenarioFalha();
        c.llm.programar(TarefaLlm.ESTRUTURACAO_AULA, CenarioRelatorio.saida("x", List.of()));
        TrabalhoProcessamento trabalho = c.tentar.executar(c.clara.id(), falha.id(), Canal.WEB);
        TrabalhoProcessamento reservado = c.fila.reservarProximo(c.relogio.agora()).orElseThrow();
        c.cancelar.descartar(c.clara.id(), falha.id());

        c.processador.processar(reservado);

        assertThat(c.fila.buscarPorId(trabalho.id()).orElseThrow().status())
                .isEqualTo(StatusTrabalho.DESCARTADO);
        assertThat(falha.status()).isEqualTo(StatusRelatorio.CANCELADO);
        assertThat(c.versoes.versoes).isEmpty();
        assertThat(c.notificacao.eventos).doesNotContain("resultado:PRONTO_PARA_CONFERENCIA");
    }

    @Test
    void registroDescartadoDuranteATranscricaoNaoCriaRelatorio() {
        TrabalhoProcessamento trabalho =
                c.registrar.executar(
                        c.clara.id(), c.matteo.id(), CenarioRelatorio.arquivo("a", 1), Canal.WEB);
        TrabalhoProcessamento reservado = c.fila.reservarProximo(c.relogio.agora()).orElseThrow();
        reservado.descartar(c.relogio.agora());

        c.processador.processar(reservado);

        assertThat(c.relatorios.relatorios).isEmpty();
        assertThat(c.fila.buscarPorId(trabalho.id()).orElseThrow().status())
                .isEqualTo(StatusTrabalho.DESCARTADO);
    }

    @Test
    void erroInesperadoFalhaOTrabalhoSemPrenderORelatorio() {
        // sem resposta programada, o dublê lança IllegalStateException
        TrabalhoProcessamento trabalho =
                c.registrar.executar(
                        c.clara.id(), c.matteo.id(), CenarioRelatorio.arquivo("a", 1), Canal.WEB);
        TrabalhoProcessamento reservado = c.fila.reservarProximo(c.relogio.agora()).orElseThrow();
        assertThatThrownBy(() -> c.processador.processar(reservado))
                .isInstanceOf(IllegalStateException.class);

        TrabalhoProcessamento gravado = c.fila.buscarPorId(trabalho.id()).orElseThrow();
        assertThat(gravado.status()).isEqualTo(StatusTrabalho.FALHOU);
        assertThat(gravado.resultado()).isEqualTo(ResultadoTrabalho.FALHA_ESTRUTURACAO);
        assertThat(c.relatorios.buscarPorId(gravado.relatorioId()).orElseThrow().status())
                .isEqualTo(StatusRelatorio.FALHA_PROCESSAMENTO);
    }

    private RelatorioAula cenarioFalha() {
        c.llm.programar(TarefaLlm.ESTRUTURACAO_AULA, new ProvedorIndisponivelExcecao("fora"));
        TrabalhoProcessamento trabalho = registrarEProcessar(Canal.WEB);
        return c.relatorios.buscarPorId(trabalho.relatorioId()).orElseThrow();
    }
}
