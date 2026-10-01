package br.com.felipeblaft.relatorios.servico;

import br.com.felipeblaft.relatorios.dominio.comum.Meio;
import br.com.felipeblaft.relatorios.dominio.comum.TipoRelatorio;
import br.com.felipeblaft.relatorios.dominio.relatorio.Aula;
import br.com.felipeblaft.relatorios.dominio.relatorio.EtapaRelatorio;
import br.com.felipeblaft.relatorios.dominio.relatorio.InteracaoRelatorio;
import br.com.felipeblaft.relatorios.dominio.relatorio.OrigemVersao;
import br.com.felipeblaft.relatorios.dominio.relatorio.RelatorioAula;
import br.com.felipeblaft.relatorios.dominio.relatorio.ResultadoInteracao;
import br.com.felipeblaft.relatorios.dominio.relatorio.StatusRelatorio;
import br.com.felipeblaft.relatorios.dominio.relatorio.TipoInteracao;
import br.com.felipeblaft.relatorios.dominio.relatorio.VersaoRelatorio;
import br.com.felipeblaft.relatorios.dominio.trabalho.ChamadaIa;
import br.com.felipeblaft.relatorios.dominio.trabalho.EtapaTrabalho;
import br.com.felipeblaft.relatorios.dominio.trabalho.ResultadoTrabalho;
import br.com.felipeblaft.relatorios.dominio.trabalho.StatusTrabalho;
import br.com.felipeblaft.relatorios.dominio.trabalho.TarefaIa;
import br.com.felipeblaft.relatorios.dominio.trabalho.TrabalhoProcessamento;
import br.com.felipeblaft.relatorios.porta.ArquivoTemporario;
import br.com.felipeblaft.relatorios.porta.AudioNaoProcessavelExcecao;
import br.com.felipeblaft.relatorios.porta.ConflitoConcorrenciaExcecao;
import br.com.felipeblaft.relatorios.porta.EntradaAudio;
import br.com.felipeblaft.relatorios.porta.EntradaTarefa;
import br.com.felipeblaft.relatorios.porta.FilaTrabalhoPorta;
import br.com.felipeblaft.relatorios.porta.FormatoNaoSuportadoExcecao;
import br.com.felipeblaft.relatorios.porta.InteracaoRelatorioRepositorio;
import br.com.felipeblaft.relatorios.porta.LlmPorta;
import br.com.felipeblaft.relatorios.porta.NotificacaoProfessorPorta;
import br.com.felipeblaft.relatorios.porta.ProvedorIndisponivelExcecao;
import br.com.felipeblaft.relatorios.porta.ReferenciaArquivoCanal;
import br.com.felipeblaft.relatorios.porta.RegistroCustoPorta;
import br.com.felipeblaft.relatorios.porta.RelatorioAulaRepositorio;
import br.com.felipeblaft.relatorios.porta.RelogioPorta;
import br.com.felipeblaft.relatorios.porta.RespostaLlm;
import br.com.felipeblaft.relatorios.porta.ResultadoTranscricao;
import br.com.felipeblaft.relatorios.porta.SaidaIaInvalidaExcecao;
import br.com.felipeblaft.relatorios.porta.SaidaRelatorioAulaIa;
import br.com.felipeblaft.relatorios.porta.TarefaLlm;
import br.com.felipeblaft.relatorios.porta.TransacaoPorta;
import br.com.felipeblaft.relatorios.porta.VersaoRelatorioRepositorio;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Executa os trabalhos da fila (R-06): registro de aula, nova tentativa, resposta às perguntas e
 * alteração. As etapas longas (transcrição, IA) ficam fora de transação; cada gravação de estado é
 * atômica e notificada. Aplica as regras da saída da IA (contracts/portas-provedores.md §6.1),
 * registra interações, versões, PDFs, custo e tempo, e descarta o resultado se o relatório foi
 * cancelado ou o trabalho descartado enquanto processava.
 */
public class ProcessadorTrabalhoServico {

    /** Processamento de outros relatórios (ex.: consolidação e alteração do semestral). */
    public interface ProcessadorComplementar {
        boolean atende(TrabalhoProcessamento trabalho);

        void processar(TrabalhoProcessamento trabalho, ApoioProcessamento apoio);

        /** Erro inesperado: o relatório não pode ficar preso numa etapa (FR-022). */
        default void falhou(TrabalhoProcessamento trabalho) {}

        /** Soma o tempo do trabalho concluído ao relatório (dentro da transação de conclusão). */
        default void registrarTempo(TrabalhoProcessamento trabalho) {}
    }

    /** Operações comuns oferecidas aos processadores complementares. */
    public interface ApoioProcessamento {
        void avancar(TrabalhoProcessamento trabalho, EtapaTrabalho etapa);

        void concluir(TrabalhoProcessamento trabalho, ResultadoTrabalho resultado);

        /** O trabalho foi descartado em outro canal; se sim, encerra sem resultado. */
        boolean descartado(TrabalhoProcessamento trabalho);

        /** Texto digitado ou transcrito do áudio; vazio se a transcrição falhou. */
        Optional<String> textoDoPedido(TrabalhoProcessamento trabalho);

        <T> Optional<RespostaLlm<T>> chamarLlm(
                TrabalhoProcessamento trabalho,
                TarefaLlm tarefa,
                TarefaIa tarefaIa,
                EntradaTarefa entrada,
                Class<T> tipoSaida);
    }

    private final ApoioProcessamento apoio =
            new ApoioProcessamento() {
                @Override
                public void avancar(TrabalhoProcessamento trabalho, EtapaTrabalho etapa) {
                    ProcessadorTrabalhoServico.this.avancar(trabalho, etapa);
                }

                @Override
                public void concluir(TrabalhoProcessamento trabalho, ResultadoTrabalho resultado) {
                    ProcessadorTrabalhoServico.this.concluir(trabalho, resultado);
                }

                @Override
                public boolean descartado(TrabalhoProcessamento trabalho) {
                    return ProcessadorTrabalhoServico.this.descartado(trabalho);
                }

                @Override
                public Optional<String> textoDoPedido(TrabalhoProcessamento trabalho) {
                    return ProcessadorTrabalhoServico.this.textoDoPedido(trabalho);
                }

                @Override
                public <T> Optional<RespostaLlm<T>> chamarLlm(
                        TrabalhoProcessamento trabalho,
                        TarefaLlm tarefa,
                        TarefaIa tarefaIa,
                        EntradaTarefa entrada,
                        Class<T> tipoSaida) {
                    return ProcessadorTrabalhoServico.this.chamarLlmTipado(
                            trabalho, tarefa, tarefaIa, entrada, tipoSaida);
                }
            };

    private final FilaTrabalhoPorta fila;
    private final RelatorioAulaRepositorio relatorios;
    private final VersaoRelatorioRepositorio versoes;
    private final InteracaoRelatorioRepositorio interacoes;
    private final AudioTemporarioServico audio;
    private final LlmPorta llm;
    private final GeracaoDocumentoServico documentos;
    private final NotificacaoProfessorPorta notificacao;
    private final RegistroCustoPorta custos;
    private final TransacaoPorta transacao;
    private final RelogioPorta relogio;
    private ProcessadorComplementar complementar;

    public ProcessadorTrabalhoServico(
            FilaTrabalhoPorta fila,
            RelatorioAulaRepositorio relatorios,
            VersaoRelatorioRepositorio versoes,
            InteracaoRelatorioRepositorio interacoes,
            AudioTemporarioServico audio,
            LlmPorta llm,
            GeracaoDocumentoServico documentos,
            NotificacaoProfessorPorta notificacao,
            RegistroCustoPorta custos,
            TransacaoPorta transacao,
            RelogioPorta relogio) {
        this.fila = Objects.requireNonNull(fila);
        this.relatorios = Objects.requireNonNull(relatorios);
        this.versoes = Objects.requireNonNull(versoes);
        this.interacoes = Objects.requireNonNull(interacoes);
        this.audio = Objects.requireNonNull(audio);
        this.llm = Objects.requireNonNull(llm);
        this.documentos = Objects.requireNonNull(documentos);
        this.notificacao = Objects.requireNonNull(notificacao);
        this.custos = Objects.requireNonNull(custos);
        this.transacao = Objects.requireNonNull(transacao);
        this.relogio = Objects.requireNonNull(relogio);
    }

    public void registrarComplementar(ProcessadorComplementar processador) {
        this.complementar = processador;
    }

    /**
     * Processa um trabalho já reservado (em execução). Erros inesperados (inclusive {@link Error})
     * viram falha do trabalho, para que ele nunca fique preso em execução.
     */
    public void processar(TrabalhoProcessamento trabalho) {
        try {
            if (complementar != null && complementar.atende(trabalho)) {
                complementar.processar(trabalho, apoio);
                return;
            }
            switch (trabalho.tipo()) {
                case REGISTRO_AULA -> registrarAula(trabalho);
                case NOVA_TENTATIVA -> novaTentativa(trabalho);
                case RESPOSTA_PERGUNTAS -> responder(trabalho);
                case ALTERACAO -> alterarAula(trabalho);
                default -> throw new IllegalStateException("tipo de trabalho sem processador");
            }
        } catch (RuntimeException | Error inesperado) {
            falhaInesperada(trabalho);
            throw inesperado;
        } finally {
            descartarArquivo(trabalho);
        }
    }

    // ------------------------------------------------------------ registro de aula

    private void registrarAula(TrabalhoProcessamento trabalho) {
        notificacao.notificarEtapa(trabalho);
        avancar(trabalho, EtapaTrabalho.TRANSCREVENDO);
        Optional<ResultadoTranscricao> transcricao = transcrever(trabalho);
        if (transcricao.isEmpty()) {
            concluir(trabalho, ResultadoTrabalho.AUDIO_NAO_PROCESSAVEL);
            return;
        }
        if (descartado(trabalho)) {
            return;
        }
        Instant agora = relogio.agora();
        RelatorioAula relatorio =
                transacao.executar(
                        () -> {
                            Aula aula =
                                    Aula.nova(
                                            trabalho.professorId(),
                                            trabalho.alunoId(),
                                            relogio.hojeNoFusoDaEscola(),
                                            agora);
                            relatorios.salvarAula(aula);
                            RelatorioAula novo =
                                    RelatorioAula.criar(
                                            aula,
                                            transcricao.get().texto(),
                                            trabalho.canalSolicitacao(),
                                            agora);
                            relatorios.salvar(novo);
                            trabalho.vincularRelatorio(TipoRelatorio.AULA, novo.id());
                            trabalho.avancar(EtapaTrabalho.ESTRUTURANDO);
                            fila.atualizar(trabalho);
                            return novo;
                        });
        notificacao.notificarEtapa(trabalho);
        estruturar(trabalho, relatorio);
    }

    private void novaTentativa(TrabalhoProcessamento trabalho) {
        RelatorioAula relatorio = relatorios.buscarPorId(trabalho.relatorioId()).orElseThrow();
        avancar(trabalho, EtapaTrabalho.ESTRUTURANDO);
        estruturar(trabalho, relatorio);
    }

    private void estruturar(TrabalhoProcessamento trabalho, RelatorioAula relatorio) {
        Optional<RespostaLlm<SaidaRelatorioAulaIa>> resposta =
                chamarLlm(
                        trabalho,
                        TarefaLlm.ESTRUTURACAO_AULA,
                        TarefaIa.ESTRUTURACAO,
                        new EntradaTarefa.EstruturacaoAula(relatorio.transcricao()));
        if (resposta.isEmpty()) {
            boolean gravou =
                    comRelatorioAberto(
                            trabalho,
                            atual -> {
                                atual.falharEstruturacao(relogio.agora());
                                return true;
                            });
            if (gravou) {
                concluir(trabalho, ResultadoTrabalho.FALHA_ESTRUTURACAO);
            }
            return;
        }
        SaidaRelatorioAulaIa saida = resposta.get().saida();
        String prompt = resposta.get().versaoPrompt();
        final boolean[] conferencia = new boolean[1];
        boolean gravou =
                comRelatorioAberto(
                        trabalho,
                        atual -> {
                            Instant agora = relogio.agora();
                            conferencia[0] =
                                    atual.registrarEstruturacao(
                                            saida.conteudo(), saida.perguntasPendentes(), agora);
                            if (conferencia[0]) {
                                criarVersao(
                                        atual,
                                        OrigemVersao.ESTRUTURACAO_INICIAL,
                                        null,
                                        trabalho,
                                        prompt);
                            }
                            return true;
                        });
        if (gravou) {
            concluir(
                    trabalho,
                    conferencia[0]
                            ? ResultadoTrabalho.PRONTO_PARA_CONFERENCIA
                            : ResultadoTrabalho.PERGUNTAS_PENDENTES);
        }
    }

    // ------------------------------------------------------------ respostas às perguntas

    private void responder(TrabalhoProcessamento trabalho) {
        Optional<String> texto = textoDoPedido(trabalho);
        if (texto.isEmpty()) {
            encerrarEtapaSemMudanca(trabalho, ResultadoTrabalho.FALHA_RESPOSTA);
            return;
        }
        RelatorioAula relatorio = relatorios.buscarPorId(trabalho.relatorioId()).orElseThrow();
        if (trabalho.meio() == Meio.AUDIO) {
            comRelatorioAberto(
                    trabalho,
                    atual -> {
                        atual.iniciarEtapa(EtapaRelatorio.INCORPORANDO_RESPOSTAS, relogio.agora());
                        return true;
                    });
        }
        avancar(trabalho, EtapaTrabalho.INCORPORANDO_RESPOSTAS);
        int rodada = relatorio.rodadasPerguntas();
        Optional<RespostaLlm<SaidaRelatorioAulaIa>> resposta =
                chamarLlm(
                        trabalho,
                        TarefaLlm.INCORPORACAO_RESPOSTAS,
                        TarefaIa.INCORPORACAO_RESPOSTAS,
                        new EntradaTarefa.IncorporacaoRespostas(
                                relatorio.conteudo(),
                                relatorio.perguntasPendentes(),
                                texto.get(),
                                rodada));
        if (resposta.isEmpty()) {
            boolean gravou =
                    comRelatorioAberto(
                            trabalho,
                            atual -> {
                                registrarInteracao(
                                        atual,
                                        trabalho,
                                        TipoInteracao.RESPOSTA_PERGUNTAS,
                                        texto.get(),
                                        rodada,
                                        ResultadoInteracao.FALHOU,
                                        null);
                                atual.concluirEtapa(relogio.agora());
                                return true;
                            });
            if (gravou) {
                concluir(trabalho, ResultadoTrabalho.FALHA_RESPOSTA);
            }
            return;
        }
        SaidaRelatorioAulaIa saida = resposta.get().saida();
        final boolean[] conferencia = new boolean[1];
        boolean gravou =
                comRelatorioAberto(
                        trabalho,
                        atual -> {
                            InteracaoRelatorio interacao =
                                    registrarInteracao(
                                            atual,
                                            trabalho,
                                            TipoInteracao.RESPOSTA_PERGUNTAS,
                                            texto.get(),
                                            rodada,
                                            ResultadoInteracao.APLICADA,
                                            null);
                            conferencia[0] =
                                    atual.incorporarResposta(
                                            saida.conteudo(),
                                            saida.perguntasPendentes(),
                                            relogio.agora());
                            if (conferencia[0]) {
                                criarVersao(
                                        atual,
                                        OrigemVersao.RESPOSTA_PERGUNTAS,
                                        interacao,
                                        trabalho,
                                        resposta.get().versaoPrompt());
                            }
                            return true;
                        });
        if (gravou) {
            concluir(
                    trabalho,
                    conferencia[0]
                            ? ResultadoTrabalho.PRONTO_PARA_CONFERENCIA
                            : ResultadoTrabalho.PERGUNTAS_PENDENTES);
        }
    }

    // ------------------------------------------------------------ alteração

    private void alterarAula(TrabalhoProcessamento trabalho) {
        Optional<String> texto = textoDoPedido(trabalho);
        if (texto.isEmpty()) {
            encerrarEtapaSemMudanca(trabalho, ResultadoTrabalho.FALHA_ALTERACAO);
            return;
        }
        int versaoBase =
                trabalho.parametro(TrabalhoProcessamento.VERSAO_BASE)
                        .map(Integer::parseInt)
                        .orElse(0);
        RelatorioAula relatorio = relatorios.buscarPorId(trabalho.relatorioId()).orElseThrow();
        if (relatorio.versaoVigente() != versaoBase) {
            boolean gravou =
                    comRelatorioAberto(
                            trabalho,
                            atual -> {
                                registrarInteracao(
                                        atual,
                                        trabalho,
                                        TipoInteracao.ALTERACAO,
                                        texto.get(),
                                        null,
                                        ResultadoInteracao.RECUSADA_VERSAO_DESATUALIZADA,
                                        null);
                                atual.concluirEtapa(relogio.agora());
                                return true;
                            });
            if (gravou) {
                concluir(trabalho, ResultadoTrabalho.VERSAO_DESATUALIZADA);
            }
            return;
        }
        comRelatorioAberto(
                trabalho,
                atual -> {
                    atual.iniciarEtapa(EtapaRelatorio.APLICANDO_ALTERACAO, relogio.agora());
                    return true;
                });
        avancar(trabalho, EtapaTrabalho.APLICANDO_ALTERACAO);
        Optional<RespostaLlm<SaidaRelatorioAulaIa>> resposta =
                chamarLlm(
                        trabalho,
                        TarefaLlm.REVISAO_AULA,
                        TarefaIa.REVISAO,
                        new EntradaTarefa.RevisaoAula(relatorio.conteudo(), texto.get()));
        if (resposta.isEmpty()) {
            boolean gravou =
                    comRelatorioAberto(
                            trabalho,
                            atual -> {
                                registrarInteracao(
                                        atual,
                                        trabalho,
                                        TipoInteracao.ALTERACAO,
                                        texto.get(),
                                        null,
                                        ResultadoInteracao.FALHOU,
                                        null);
                                atual.concluirEtapa(relogio.agora());
                                return true;
                            });
            if (gravou) {
                concluir(trabalho, ResultadoTrabalho.FALHA_ALTERACAO);
            }
            return;
        }
        SaidaRelatorioAulaIa saida = resposta.get().saida();
        final ResultadoTrabalho[] resultado = new ResultadoTrabalho[1];
        boolean gravou =
                comRelatorioAberto(
                        trabalho,
                        atual -> {
                            Instant agora = relogio.agora();
                            if (saida.semAlteracao() || saida.conteudo().equals(atual.conteudo())) {
                                ResultadoInteracao semMudanca =
                                        saida.semAlteracao() && saida.pedidoDeAcaoIndicado()
                                                ? ResultadoInteracao.PEDIDO_DE_ACAO_IGNORADO
                                                : ResultadoInteracao.SEM_ALTERACAO_DE_CONTEUDO;
                                registrarInteracao(
                                        atual,
                                        trabalho,
                                        TipoInteracao.ALTERACAO,
                                        texto.get(),
                                        null,
                                        semMudanca,
                                        null);
                                atual.concluirEtapa(agora);
                                resultado[0] =
                                        semMudanca == ResultadoInteracao.PEDIDO_DE_ACAO_IGNORADO
                                                ? ResultadoTrabalho.PEDIDO_DE_ACAO_IGNORADO
                                                : ResultadoTrabalho.SEM_ALTERACAO_DE_CONTEUDO;
                                return true;
                            }
                            int nova = atual.aplicarAlteracao(saida.conteudo(), agora);
                            InteracaoRelatorio interacao =
                                    registrarInteracao(
                                            atual,
                                            trabalho,
                                            TipoInteracao.ALTERACAO,
                                            texto.get(),
                                            null,
                                            ResultadoInteracao.APLICADA,
                                            nova);
                            criarVersao(
                                    atual,
                                    OrigemVersao.ALTERACAO,
                                    interacao,
                                    trabalho,
                                    resposta.get().versaoPrompt());
                            resultado[0] = ResultadoTrabalho.NOVA_VERSAO;
                            return true;
                        });
        if (gravou) {
            concluir(trabalho, resultado[0]);
        }
    }

    // ------------------------------------------------------------ apoio

    /** Texto digitado ou transcrito do áudio; vazio se a transcrição falhou (nada é aplicado). */
    private Optional<String> textoDoPedido(TrabalhoProcessamento trabalho) {
        if (trabalho.meio() == Meio.TEXTO) {
            return trabalho.parametro(TrabalhoProcessamento.TEXTO);
        }
        avancar(trabalho, EtapaTrabalho.TRANSCREVENDO_PEDIDO);
        Optional<ResultadoTranscricao> transcricao = transcrever(trabalho);
        transcricao.ifPresent(
                t -> {
                    trabalho.definirParametro(
                            TrabalhoProcessamento.TEXTO_ENTENDIDO, t.texto().strip());
                    fila.atualizar(trabalho);
                });
        return transcricao.map(t -> t.texto().strip());
    }

    private void encerrarEtapaSemMudanca(
            TrabalhoProcessamento trabalho, ResultadoTrabalho resultado) {
        boolean gravou =
                comRelatorioAberto(
                        trabalho,
                        atual -> {
                            atual.concluirEtapa(relogio.agora());
                            return true;
                        });
        if (gravou) {
            concluir(trabalho, resultado);
        }
    }

    private Optional<ResultadoTranscricao> transcrever(TrabalhoProcessamento trabalho) {
        long inicio = System.nanoTime();
        try {
            ResultadoTranscricao resultado = audio.transcrever(fonte(trabalho));
            custos.registrar(
                    ChamadaIa.nova(
                            trabalho.id(),
                            trabalho.relatorioTipo(),
                            trabalho.relatorioId(),
                            TarefaIa.TRANSCRICAO,
                            resultado.provedorModelo(),
                            BigDecimal.valueOf(resultado.duracaoSegundos()),
                            null,
                            null,
                            decorrido(inicio),
                            resultado.custoEstimado(),
                            true,
                            null,
                            relogio.agora()));
            return Optional.of(resultado);
        } catch (AudioNaoProcessavelExcecao
                | FormatoNaoSuportadoExcecao
                | ProvedorIndisponivelExcecao falha) {
            registrarFalhaIa(trabalho, TarefaIa.TRANSCRICAO, codigoDaFalha(falha), inicio);
            return Optional.empty();
        } finally {
            trabalho.definirArquivoTemporario(null);
        }
    }

    private Optional<RespostaLlm<SaidaRelatorioAulaIa>> chamarLlm(
            TrabalhoProcessamento trabalho,
            TarefaLlm tarefa,
            TarefaIa tarefaIa,
            EntradaTarefa entrada) {
        return chamarLlmTipado(trabalho, tarefa, tarefaIa, entrada, SaidaRelatorioAulaIa.class);
    }

    private <T> Optional<RespostaLlm<T>> chamarLlmTipado(
            TrabalhoProcessamento trabalho,
            TarefaLlm tarefa,
            TarefaIa tarefaIa,
            EntradaTarefa entrada,
            Class<T> tipoSaida) {
        long inicio = System.nanoTime();
        try {
            RespostaLlm<T> resposta = llm.gerarJson(tarefa, entrada, tipoSaida);
            custos.registrar(
                    ChamadaIa.nova(
                            trabalho.id(),
                            trabalho.relatorioTipo(),
                            trabalho.relatorioId(),
                            tarefaIa,
                            resposta.provedorModelo(),
                            null,
                            resposta.tokensEntrada(),
                            resposta.tokensSaida(),
                            decorrido(inicio),
                            resposta.custoEstimado(),
                            true,
                            null,
                            relogio.agora()));
            return Optional.of(resposta);
        } catch (SaidaIaInvalidaExcecao | ProvedorIndisponivelExcecao falha) {
            registrarFalhaIa(trabalho, tarefaIa, codigoDaFalha(falha), inicio);
            return Optional.empty();
        }
    }

    static String codigoDaFalha(RuntimeException falha) {
        return switch (falha) {
            case AudioNaoProcessavelExcecao a -> "AUDIO_" + a.motivo().name();
            case FormatoNaoSuportadoExcecao f -> "FORMATO_NAO_SUPORTADO";
            case SaidaIaInvalidaExcecao s -> "SAIDA_INVALIDA";
            default -> "PROVEDOR_INDISPONIVEL";
        };
    }

    private void registrarFalhaIa(
            TrabalhoProcessamento trabalho, TarefaIa tarefa, String codigo, long inicio) {
        custos.registrar(
                ChamadaIa.nova(
                        trabalho.id(),
                        trabalho.relatorioTipo(),
                        trabalho.relatorioId(),
                        tarefa,
                        null,
                        null,
                        null,
                        null,
                        decorrido(inicio),
                        BigDecimal.ZERO,
                        false,
                        codigo,
                        relogio.agora()));
    }

    private static long decorrido(long inicioNanos) {
        return Duration.ofNanos(System.nanoTime() - inicioNanos).toMillis();
    }

    static EntradaAudio fonte(TrabalhoProcessamento trabalho) {
        if (trabalho.arquivoTemporario() != null) {
            long tamanho =
                    trabalho.parametro(TrabalhoProcessamento.ARQUIVO_TAMANHO)
                            .map(Long::parseLong)
                            .orElse(0L);
            return new ArquivoTemporario(
                    Path.of(trabalho.arquivoTemporario()),
                    tamanho,
                    trabalho.parametro(TrabalhoProcessamento.ARQUIVO_MIME).orElse(null));
        }
        return new ReferenciaArquivoCanal(
                trabalho.parametro(TrabalhoProcessamento.ARQUIVO_CANAL).orElseThrow(),
                trabalho.parametro(TrabalhoProcessamento.ARQUIVO_CANAL_UNICO).orElse(null),
                trabalho.parametro(TrabalhoProcessamento.ARQUIVO_MIME).orElse(null),
                trabalho.parametro(TrabalhoProcessamento.ARQUIVO_TAMANHO)
                        .map(Long::parseLong)
                        .orElse(0L));
    }

    private void descartarArquivo(TrabalhoProcessamento trabalho) {
        if (trabalho.arquivoTemporario() != null) {
            audio.descartar(fonte(trabalho));
            trabalho.definirArquivoTemporario(null);
        }
    }

    private void criarVersao(
            RelatorioAula relatorio,
            OrigemVersao origem,
            InteracaoRelatorio interacao,
            TrabalhoProcessamento trabalho,
            String prompt) {
        VersaoRelatorio versao =
                VersaoRelatorio.nova(
                        TipoRelatorio.AULA,
                        relatorio.id(),
                        relatorio.versaoVigente(),
                        relatorio.conteudo(),
                        origem,
                        interacao == null ? null : interacao.id(),
                        trabalho.canalSolicitacao(),
                        prompt,
                        relogio.agora());
        versoes.salvar(versao);
        documentos.gerarConferencia(relatorio, versao);
    }

    private InteracaoRelatorio registrarInteracao(
            RelatorioAula relatorio,
            TrabalhoProcessamento trabalho,
            TipoInteracao tipo,
            String texto,
            Integer rodada,
            ResultadoInteracao resultado,
            Integer versaoResultante) {
        int versaoBase =
                trabalho.parametro(TrabalhoProcessamento.VERSAO_BASE)
                        .map(Integer::parseInt)
                        .orElse(relatorio.versaoVigente());
        InteracaoRelatorio interacao =
                InteracaoRelatorio.nova(
                        TipoRelatorio.AULA,
                        relatorio.id(),
                        tipo,
                        trabalho.canalSolicitacao(),
                        trabalho.meio(),
                        texto,
                        versaoBase,
                        rodada,
                        resultado,
                        versaoResultante,
                        relogio.agora());
        interacoes.salvar(interacao);
        return interacao;
    }

    /** Operação de gravação sobre o relatório; devolve o que a operação devolve. */
    private interface OperacaoRelatorio {
        boolean aplicar(RelatorioAula atual);
    }

    /**
     * Relê o relatório numa transação e aplica a operação. Se o relatório foi cancelado ou o
     * trabalho descartado (ou outro canal gravou antes), o resultado é descartado e devolve {@code
     * false}.
     */
    private boolean comRelatorioAberto(TrabalhoProcessamento trabalho, OperacaoRelatorio operacao) {
        try {
            return transacao.executar(
                    () -> {
                        RelatorioAula atual =
                                relatorios
                                        .buscarParaAtualizar(trabalho.relatorioId())
                                        .orElseThrow();
                        if (atual.status() == StatusRelatorio.CANCELADO
                                || atual.status() == StatusRelatorio.APROVADO
                                || estaDescartado(trabalho)) {
                            return false;
                        }
                        boolean aplicado = operacao.aplicar(atual);
                        relatorios.salvar(atual);
                        return aplicado;
                    });
        } catch (ConflitoConcorrenciaExcecao conflito) {
            RelatorioAula atual = relatorios.buscarPorId(trabalho.relatorioId()).orElseThrow();
            if (atual.status() == StatusRelatorio.CANCELADO || estaDescartado(trabalho)) {
                marcarDescartado(trabalho);
                return false;
            }
            throw conflito;
        }
    }

    private boolean estaDescartado(TrabalhoProcessamento trabalho) {
        return fila.buscarPorId(trabalho.id())
                .map(t -> t.status() == StatusTrabalho.DESCARTADO)
                .orElse(true);
    }

    /** Verifica o descarte; se descartado, encerra o trabalho sem resultado. */
    private boolean descartado(TrabalhoProcessamento trabalho) {
        if (estaDescartado(trabalho)) {
            marcarDescartado(trabalho);
            return true;
        }
        return false;
    }

    private void marcarDescartado(TrabalhoProcessamento trabalho) {
        trabalho.descartar(relogio.agora());
    }

    private void avancar(TrabalhoProcessamento trabalho, EtapaTrabalho etapa) {
        trabalho.avancar(etapa);
        fila.atualizar(trabalho);
        notificacao.notificarEtapa(trabalho);
    }

    private void concluir(TrabalhoProcessamento trabalho, ResultadoTrabalho resultado) {
        if (estaDescartado(trabalho)) {
            marcarDescartado(trabalho);
            return;
        }
        Instant agora = relogio.agora();
        trabalho.concluir(resultado, agora);
        transacao.executar(
                () -> {
                    fila.atualizar(trabalho);
                    if (complementar != null
                            && trabalho.relatorioTipo() != null
                            && trabalho.relatorioTipo() != TipoRelatorio.AULA) {
                        complementar.registrarTempo(trabalho);
                    }
                    if (trabalho.relatorioId() != null
                            && trabalho.relatorioTipo() == TipoRelatorio.AULA) {
                        relatorios
                                .buscarPorId(trabalho.relatorioId())
                                .ifPresent(
                                        relatorio -> {
                                            relatorio.somarTempoProcessamento(trabalho.duracaoMs());
                                            relatorios.salvar(relatorio);
                                        });
                    }
                });
        notificacao.notificarResultado(trabalho);
    }

    /** Erro inesperado: o trabalho falha e o relatório não fica preso numa etapa (FR-022). */
    private void falhaInesperada(TrabalhoProcessamento trabalho) {
        ResultadoTrabalho resultado =
                switch (trabalho.tipo()) {
                    case RESPOSTA_PERGUNTAS -> ResultadoTrabalho.FALHA_RESPOSTA;
                    case ALTERACAO -> ResultadoTrabalho.FALHA_ALTERACAO;
                    case CONSOLIDACAO_SEMESTRAL -> ResultadoTrabalho.FALHA_CONSOLIDACAO;
                    default ->
                            trabalho.relatorioId() == null
                                    ? ResultadoTrabalho.AUDIO_NAO_PROCESSAVEL
                                    : ResultadoTrabalho.FALHA_ESTRUTURACAO;
                };
        try {
            if (complementar != null && complementar.atende(trabalho)) {
                complementar.falhou(trabalho);
            } else if (trabalho.relatorioId() != null
                    && trabalho.relatorioTipo() == TipoRelatorio.AULA) {
                comRelatorioAberto(
                        trabalho,
                        atual -> {
                            if (atual.status() == StatusRelatorio.EM_PROCESSAMENTO) {
                                atual.falharEstruturacao(relogio.agora());
                            } else {
                                atual.concluirEtapa(relogio.agora());
                            }
                            return true;
                        });
            }
            if (trabalho.status().emCurso()) {
                concluir(trabalho, resultado);
            }
        } catch (RuntimeException ignorada) {
            // o erro original é relatado por quem chamou
        }
    }
}
