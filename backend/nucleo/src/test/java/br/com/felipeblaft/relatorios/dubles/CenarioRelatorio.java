package br.com.felipeblaft.relatorios.dubles;

import br.com.felipeblaft.relatorios.casodeuso.aula.AprovarRelatorioAulaCasoDeUso;
import br.com.felipeblaft.relatorios.casodeuso.aula.CancelarRelatorioAulaCasoDeUso;
import br.com.felipeblaft.relatorios.casodeuso.aula.ObterRelatorioAulaCasoDeUso;
import br.com.felipeblaft.relatorios.casodeuso.aula.RegistrarAulaPorAudioCasoDeUso;
import br.com.felipeblaft.relatorios.casodeuso.aula.ResponderPerguntasCasoDeUso;
import br.com.felipeblaft.relatorios.casodeuso.aula.SolicitarAlteracaoRelatorioAulaCasoDeUso;
import br.com.felipeblaft.relatorios.casodeuso.aula.TentarNovamenteRelatorioAulaCasoDeUso;
import br.com.felipeblaft.relatorios.casodeuso.documento.ObterDocumentoRelatorioCasoDeUso;
import br.com.felipeblaft.relatorios.casodeuso.trabalho.ConsultarTrabalhoCasoDeUso;
import br.com.felipeblaft.relatorios.dominio.cadastro.Aluno;
import br.com.felipeblaft.relatorios.dominio.cadastro.Professor;
import br.com.felipeblaft.relatorios.dominio.comum.Canal;
import br.com.felipeblaft.relatorios.dominio.relatorio.RelatorioAula;
import br.com.felipeblaft.relatorios.dominio.trabalho.TrabalhoProcessamento;
import br.com.felipeblaft.relatorios.dubles.DublesProcessamento.ArmazenamentoDocumentoEmMemoria;
import br.com.felipeblaft.relatorios.dubles.DublesProcessamento.ArquivoCanalFalso;
import br.com.felipeblaft.relatorios.dubles.DublesProcessamento.DocumentoPdfRepositorioEmMemoria;
import br.com.felipeblaft.relatorios.dubles.DublesProcessamento.FilaTrabalhoEmMemoria;
import br.com.felipeblaft.relatorios.dubles.DublesProcessamento.GeracaoPdfFalsa;
import br.com.felipeblaft.relatorios.dubles.DublesProcessamento.InteracaoRelatorioRepositorioEmMemoria;
import br.com.felipeblaft.relatorios.dubles.DublesProcessamento.LlmRoteirizado;
import br.com.felipeblaft.relatorios.dubles.DublesProcessamento.NormalizacaoAudioFalsa;
import br.com.felipeblaft.relatorios.dubles.DublesProcessamento.NotificacaoRegistradora;
import br.com.felipeblaft.relatorios.dubles.DublesProcessamento.RegistroCustoEmMemoria;
import br.com.felipeblaft.relatorios.dubles.DublesProcessamento.RelatorioAulaRepositorioEmMemoria;
import br.com.felipeblaft.relatorios.dubles.DublesProcessamento.TransacaoImediata;
import br.com.felipeblaft.relatorios.dubles.DublesProcessamento.TranscricaoRoteirizada;
import br.com.felipeblaft.relatorios.dubles.DublesProcessamento.VersaoRelatorioRepositorioEmMemoria;
import br.com.felipeblaft.relatorios.porta.ArquivoTemporario;
import br.com.felipeblaft.relatorios.porta.SaidaRelatorioAulaIa;
import br.com.felipeblaft.relatorios.porta.TarefaLlm;
import br.com.felipeblaft.relatorios.servico.AcessoRelatorioServico;
import br.com.felipeblaft.relatorios.servico.AudioTemporarioServico;
import br.com.felipeblaft.relatorios.servico.EnvioAudioServico;
import br.com.felipeblaft.relatorios.servico.GeracaoDocumentoServico;
import br.com.felipeblaft.relatorios.servico.ProcessadorTrabalhoServico;
import java.nio.file.Path;
import java.util.List;

/** Cenário completo de relatório de aula com todos os dublês, serviços e casos de uso. */
public class CenarioRelatorio extends CenarioAcesso {

    public final TranscricaoRoteirizada transcricao = new TranscricaoRoteirizada();
    public final NormalizacaoAudioFalsa normalizacao = new NormalizacaoAudioFalsa();
    public final ArquivoCanalFalso arquivoCanal = new ArquivoCanalFalso();
    public final LlmRoteirizado llm = new LlmRoteirizado();
    public final GeracaoPdfFalsa pdf = new GeracaoPdfFalsa();
    public final ArmazenamentoDocumentoEmMemoria armazenamento =
            new ArmazenamentoDocumentoEmMemoria();
    public final NotificacaoRegistradora notificacao = new NotificacaoRegistradora();
    public final RegistroCustoEmMemoria custos = new RegistroCustoEmMemoria();
    public final TransacaoImediata transacao = new TransacaoImediata();
    public final FilaTrabalhoEmMemoria fila = new FilaTrabalhoEmMemoria();
    public final RelatorioAulaRepositorioEmMemoria relatorios =
            new RelatorioAulaRepositorioEmMemoria();
    public final VersaoRelatorioRepositorioEmMemoria versoes =
            new VersaoRelatorioRepositorioEmMemoria();
    public final InteracaoRelatorioRepositorioEmMemoria interacoes =
            new InteracaoRelatorioRepositorioEmMemoria();
    public final DocumentoPdfRepositorioEmMemoria documentosPdf =
            new DocumentoPdfRepositorioEmMemoria();

    public final AudioTemporarioServico audio =
            new AudioTemporarioServico(arquivoCanal, normalizacao, transcricao);
    public final EnvioAudioServico envioAudio = new EnvioAudioServico(parametros, audio);
    public final AcessoRelatorioServico acesso =
            new AcessoRelatorioServico(relatorios, associacoes);
    public final GeracaoDocumentoServico documentos =
            new GeracaoDocumentoServico(
                    pdf, armazenamento, documentosPdf, relatorios, alunos, professores, relogio);
    public final ProcessadorTrabalhoServico processador =
            new ProcessadorTrabalhoServico(
                    fila,
                    relatorios,
                    versoes,
                    interacoes,
                    audio,
                    llm,
                    documentos,
                    notificacao,
                    custos,
                    transacao,
                    relogio);

    public final RegistrarAulaPorAudioCasoDeUso registrar =
            new RegistrarAulaPorAudioCasoDeUso(autorizacao, emAberto, envioAudio, fila, relogio);
    public final ResponderPerguntasCasoDeUso responder =
            new ResponderPerguntasCasoDeUso(acesso, relatorios, envioAudio, fila, relogio);
    public final SolicitarAlteracaoRelatorioAulaCasoDeUso alterar =
            new SolicitarAlteracaoRelatorioAulaCasoDeUso(
                    acesso, relatorios, envioAudio, fila, relogio);
    public final TentarNovamenteRelatorioAulaCasoDeUso tentar =
            new TentarNovamenteRelatorioAulaCasoDeUso(acesso, relatorios, fila, relogio);
    public final AprovarRelatorioAulaCasoDeUso aprovar =
            new AprovarRelatorioAulaCasoDeUso(acesso, relatorios, versoes, documentos, relogio);
    public final CancelarRelatorioAulaCasoDeUso cancelar =
            new CancelarRelatorioAulaCasoDeUso(acesso, relatorios, fila, relogio);
    public final ObterRelatorioAulaCasoDeUso obter =
            new ObterRelatorioAulaCasoDeUso(
                    acesso,
                    relatorios,
                    alunos,
                    professores,
                    interacoes,
                    versoes,
                    documentosPdf,
                    fila);
    public final ConsultarTrabalhoCasoDeUso consultarTrabalho =
            new ConsultarTrabalhoCasoDeUso(fila, relogio);
    public final br.com.felipeblaft.relatorios.casodeuso.aula.ObterRelatorioEmAbertoCasoDeUso
            obterEmAberto =
                    new br.com.felipeblaft.relatorios.casodeuso.aula
                            .ObterRelatorioEmAbertoCasoDeUso(autorizacao, emAberto);
    public final br.com.felipeblaft.relatorios.casodeuso.trabalho
                    .DescartarRegistroEmProcessamentoCasoDeUso
            descartarRegistro =
                    new br.com.felipeblaft.relatorios.casodeuso.trabalho
                            .DescartarRegistroEmProcessamentoCasoDeUso(fila, audio, relogio);
    public final ObterDocumentoRelatorioCasoDeUso obterDocumento =
            new ObterDocumentoRelatorioCasoDeUso(
                    acesso, relatorios, versoes, documentosPdf, documentos);

    public final Professor clara = professor("Clara Souza", "clara@exemplo.test", "K7QM2XPA");
    public final Professor bruno = professor("Bruno Lima", "bruno@exemplo.test", "BRUN2XPA");
    public final Aluno matteo = aluno(clara, "Matteo Mafra", "Piano");
    public final Aluno helena = aluno(bruno, "Helena Prado", "Flauta");

    public static ArquivoTemporario arquivo(String nome, long tamanho) {
        return new ArquivoTemporario(Path.of("/tmp/audios/" + nome), tamanho, "audio/ogg");
    }

    public static SaidaRelatorioAulaIa saida(String evolucao, List<String> perguntas) {
        return new SaidaRelatorioAulaIa(
                List.of("Estudo nº 5 de Czerny"),
                evolucao,
                List.of("Independência entre as mãos"),
                List.of("Escala de Sol maior com metrônomo"),
                "",
                perguntas,
                false,
                "NENHUM");
    }

    public static SaidaRelatorioAulaIa revisao(
            String evolucao, boolean semAlteracao, String pedidoDeAcao) {
        return new SaidaRelatorioAulaIa(
                List.of("Estudo nº 5 de Czerny"),
                evolucao,
                List.of("Independência entre as mãos"),
                List.of("Escala de Sol maior com metrônomo"),
                "",
                List.of("pergunta ignorada na revisão"),
                semAlteracao,
                pedidoDeAcao);
    }

    /** Processa todos os trabalhos pendentes, na ordem da fila. */
    public void processarFila() {
        while (true) {
            var proximo = fila.reservarProximo(relogio.agora());
            if (proximo.isEmpty()) {
                return;
            }
            processador.processar(proximo.get());
        }
    }

    /** Registra uma aula e processa até a conferência na versão 1 (sem perguntas). */
    public RelatorioAula relatorioEmConferencia() {
        llm.programar(TarefaLlm.ESTRUTURACAO_AULA, saida("Evoluiu na articulação.", List.of()));
        TrabalhoProcessamento trabalho =
                registrar.executar(clara.id(), matteo.id(), arquivo("aula.ogg", 1000), Canal.WEB);
        processarFila();
        return relatorios
                .buscarPorId(fila.buscarPorId(trabalho.id()).orElseThrow().relatorioId())
                .orElseThrow();
    }

    /** Registra uma aula que fica aguardando respostas (rodada 1). */
    public RelatorioAula relatorioComPerguntas() {
        llm.programar(
                TarefaLlm.ESTRUTURACAO_AULA,
                saida("Evoluiu.", List.of("Qual repertório foi trabalhado?", "Estudou em casa?")));
        TrabalhoProcessamento trabalho =
                registrar.executar(
                        clara.id(), matteo.id(), arquivo("aula.ogg", 1000), Canal.TELEGRAM);
        processarFila();
        return relatorios
                .buscarPorId(fila.buscarPorId(trabalho.id()).orElseThrow().relatorioId())
                .orElseThrow();
    }

    /** Outro aluno de Clara com relatório aguardando respostas. */
    public RelatorioAula relatorioComPerguntasDeOutroAluno() {
        Aluno olavo = aluno(clara, "Olavo Torrente", "Violão");
        llm.programar(TarefaLlm.ESTRUTURACAO_AULA, saida("x", List.of("Qual peça?")));
        TrabalhoProcessamento trabalho =
                registrar.executar(clara.id(), olavo.id(), arquivo("olavo.ogg", 10), Canal.WEB);
        processarFila();
        return relatorios
                .buscarPorId(fila.buscarPorId(trabalho.id()).orElseThrow().relatorioId())
                .orElseThrow();
    }
}
