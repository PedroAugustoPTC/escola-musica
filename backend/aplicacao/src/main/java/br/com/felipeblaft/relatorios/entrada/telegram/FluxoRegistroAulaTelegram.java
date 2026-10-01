package br.com.felipeblaft.relatorios.entrada.telegram;

import br.com.felipeblaft.relatorios.casodeuso.aula.RegistrarAulaPorAudioCasoDeUso;
import br.com.felipeblaft.relatorios.casodeuso.inicio.AlunoResumo;
import br.com.felipeblaft.relatorios.casodeuso.inicio.BuscarAlunosCasoDeUso;
import br.com.felipeblaft.relatorios.dominio.comum.Canal;
import br.com.felipeblaft.relatorios.dominio.comum.CodigoErro;
import br.com.felipeblaft.relatorios.dominio.comum.ExcecaoDominio;
import br.com.felipeblaft.relatorios.dominio.comum.ParametrosNegocio;
import br.com.felipeblaft.relatorios.dominio.telegram.AudioPendente;
import br.com.felipeblaft.relatorios.dominio.telegram.ConversaTelegram;
import br.com.felipeblaft.relatorios.dominio.telegram.EtapaFluxoConversa;
import br.com.felipeblaft.relatorios.dominio.telegram.FluxoConversa;
import br.com.felipeblaft.relatorios.dominio.trabalho.TrabalhoProcessamento;
import br.com.felipeblaft.relatorios.entrada.telegram.PalavrasChaveTelegram.Comando;
import br.com.felipeblaft.relatorios.entrada.telegram.SaidaTelegram.Botao;
import br.com.felipeblaft.relatorios.entrada.telegram.SaidaTelegram.Mensagem;
import br.com.felipeblaft.relatorios.entrada.telegram.SaidaTelegram.MensagemEtapa;
import br.com.felipeblaft.relatorios.entrada.telegram.SaidaTelegram.Teclado;
import br.com.felipeblaft.relatorios.entrada.telegram.UpdateTelegram.ArquivoTelegram;
import br.com.felipeblaft.relatorios.entrada.telegram.UpdateTelegram.MensagemTelegram;
import br.com.felipeblaft.relatorios.porta.ReferenciaArquivoCanal;
import br.com.felipeblaft.relatorios.porta.RelogioPorta;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Registro de aula pelo Telegram (estados #7 e #10, sequência 5.2, passos 1 a 4): o áudio fica
 * pendente só como {@code file_id} até o professor confirmar o aluno (FR-040); confirmado, vira
 * {@code RegistrarAulaPorAudioCasoDeUso}, o mesmo da Web.
 */
@Component
@Order(20)
public class FluxoRegistroAulaTelegram implements FluxoTelegram {

    static final String ALUNO_EM_ABERTO = "alunoEmAberto";

    private final RegistrarAulaPorAudioCasoDeUso registrar;
    private final BuscarAlunosCasoDeUso buscarAlunos;
    private final InterpretadorConversaTelegram interpretador;
    private final ApresentadorTelegram apresentador;
    private final ParametrosNegocio parametros;
    private final RelogioPorta relogio;

    public FluxoRegistroAulaTelegram(
            RegistrarAulaPorAudioCasoDeUso registrar,
            BuscarAlunosCasoDeUso buscarAlunos,
            InterpretadorConversaTelegram interpretador,
            ApresentadorTelegram apresentador,
            ParametrosNegocio parametros,
            RelogioPorta relogio) {
        this.registrar = registrar;
        this.buscarAlunos = buscarAlunos;
        this.interpretador = interpretador;
        this.apresentador = apresentador;
        this.parametros = parametros;
        this.relogio = relogio;
    }

    /** Referência ao arquivo do Telegram; a voz chega sem MIME declarado e é Ogg/Opus. */
    static ReferenciaArquivoCanal referencia(MensagemTelegram mensagem, ArquivoTelegram arquivo) {
        String mime = arquivo.mimeType();
        if (mime == null) {
            mime =
                    arquivo == mensagem.voice()
                            ? "audio/ogg"
                            : arquivo == mensagem.videoNote() ? "video/mp4" : "audio/ogg";
        }
        return new ReferenciaArquivoCanal(
                arquivo.fileId(),
                arquivo.fileUniqueId(),
                mime,
                arquivo.fileSize() == null ? 0 : arquivo.fileSize());
    }

    // ------------------------------------------------------------------ áudio recebido

    @Override
    public boolean aoAudio(
            ContextoTelegram contexto, MensagemTelegram mensagem, ArquivoTelegram arquivo) {
        ConversaTelegram conversa = contexto.conversa();
        ReferenciaArquivoCanal referencia = referencia(mensagem, arquivo);
        if (referencia.tamanho() > parametros.limiteAudioBytes()) {
            contexto.responder(
                    apresentador.erro(
                            contexto.chatId(),
                            CodigoErro.AUDIO_MUITO_GRANDE,
                            Map.of("limiteMb", parametros.limiteAudioMb())));
            return true;
        }
        conversa.limparFoco();
        conversa.receberAudio(
                new AudioPendente(
                        referencia.id(),
                        referencia.idUnico(),
                        referencia.mime(),
                        referencia.tamanho(),
                        relogio.agora()));
        Optional<AlunoResumo> selecionado = alunoSelecionado(contexto);
        if (selecionado.isPresent()) {
            conversa.iniciarFluxo(
                    FluxoConversa.REGISTRO_AULA, EtapaFluxoConversa.CONFIRMANDO_ALUNO_DO_AUDIO);
            pedirConfirmacaoDoAluno(contexto, selecionado.get());
        } else {
            conversa.iniciarFluxo(FluxoConversa.REGISTRO_AULA, EtapaFluxoConversa.ESCOLHENDO_ALUNO);
            BuscarAlunosCasoDeUso.Resultado todos =
                    buscarAlunos.executar(contexto.professor().id(), "");
            if (todos.alunos().isEmpty()) {
                conversa.descartarAudio();
                contexto.responder(apresentador.simples(contexto.chatId(), "SEM_ALUNOS"));
                return true;
            }
            interpretador.oferecerAlunos(
                    contexto, apresentador.texto("AUDIO_RECEBIDO_ESCOLHER_ALUNO"), todos.alunos());
        }
        return true;
    }

    private Optional<AlunoResumo> alunoSelecionado(ContextoTelegram contexto) {
        UUID alunoId = contexto.conversa().alunoSelecionadoId();
        if (alunoId == null) {
            return Optional.empty();
        }
        return buscarAlunos.executar(contexto.professor().id(), "").alunos().stream()
                .filter(a -> a.id().equals(alunoId))
                .findFirst();
    }

    private void pedirConfirmacaoDoAluno(ContextoTelegram contexto, AlunoResumo aluno) {
        contexto.responder(
                new Mensagem(
                        contexto.chatId(),
                        apresentador.texto(
                                "AUDIO_RECEBIDO_CONFIRMAR_ALUNO", Map.of("aluno", aluno.nome())),
                        Teclado.de(
                                new Botao(
                                        apresentador.rotulo(
                                                "BOTAO_CONFIRMAR_ALUNO",
                                                Map.of("aluno", aluno.nome())),
                                        DadosCallbackCodificador.codificar("confal")),
                                apresentador.botao("BOTAO_TROCAR_ALUNO", "trocal"))));
    }

    // ------------------------------------------------------------------ confirmação do aluno

    @Override
    public boolean aoEscolherAluno(ContextoTelegram contexto, AlunoResumo aluno) {
        if (contexto.conversa().audioPendente().isEmpty()) {
            return false;
        }
        // com áudio pendente, escolher o aluno na lista (ou pelo nome) é a confirmação (FR-040)
        registrarAudioPendente(contexto, aluno, null);
        return true;
    }

    @Override
    public boolean aoCallback(ContextoTelegram contexto, String acao, String argumento) {
        ConversaTelegram conversa = contexto.conversa();
        switch (acao) {
            case "confal" -> {
                Optional<AlunoResumo> aluno = alunoSelecionado(contexto);
                if (conversa.audioPendente().isEmpty() || aluno.isEmpty()) {
                    return false;
                }
                conversa.limparConfirmacao();
                registrarAudioPendente(contexto, aluno.get(), contexto.mensagemCallbackId());
                return true;
            }
            case "trocal" -> {
                if (conversa.audioPendente().isEmpty()) {
                    return false;
                }
                conversa.limparConfirmacao();
                interpretador.oferecerAlunos(
                        contexto,
                        apresentador.texto("AUDIO_RECEBIDO_ESCOLHER_ALUNO"),
                        buscarAlunos.executar(contexto.professor().id(), "").alunos());
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    /** "cancelar" sem relatório em foco descarta o áudio pendente. */
    @Override
    public boolean aoComando(ContextoTelegram contexto, Comando comando) {
        ConversaTelegram conversa = contexto.conversa();
        if (comando != Comando.CANCELAR || conversa.audioPendente().isEmpty()) {
            return false;
        }
        conversa.descartarAudio();
        conversa.encerrarFluxo();
        contexto.responder(apresentador.simples(contexto.chatId(), "AUDIO_PENDENTE_DESCARTADO"));
        return true;
    }

    @Override
    public String oQuePossoReceber(ContextoTelegram contexto) {
        return contexto.conversa().audioPendente().isPresent()
                ? apresentador.rotulo("RECEBER_ALUNO")
                : null;
    }

    private void registrarAudioPendente(
            ContextoTelegram contexto, AlunoResumo aluno, Long editarMensagemId) {
        ConversaTelegram conversa = contexto.conversa();
        AudioPendente audio = conversa.audioPendente().orElseThrow();
        conversa.selecionarAluno(aluno.id());
        TrabalhoProcessamento trabalho;
        try {
            trabalho =
                    registrar.executar(
                            contexto.professor().id(),
                            aluno.id(),
                            new ReferenciaArquivoCanal(
                                    audio.fileId(),
                                    audio.fileUniqueId(),
                                    audio.mime(),
                                    audio.tamanho()),
                            Canal.TELEGRAM);
        } catch (ExcecaoDominio erro) {
            if (erro.codigo() != CodigoErro.RELATORIO_EM_ABERTO) {
                conversa.descartarAudio();
                throw erro;
            }
            // FR-034: avisa e oferece continuar ou descartar; o áudio só é reaproveitado no
            // descarte
            conversa.iniciarFluxo(
                    FluxoConversa.REGISTRO_AULA, EtapaFluxoConversa.AVISO_RELATORIO_EM_ABERTO);
            conversa.guardar(ALUNO_EM_ABERTO, aluno.id().toString());
            contexto.responder(
                    new Mensagem(
                            contexto.chatId(),
                            apresentador
                                    .erro(contexto.chatId(), erro.codigo(), erro.detalhes())
                                    .texto(),
                            Teclado.de(
                                    apresentador.botao("BOTAO_CONTINUAR", "cont"),
                                    apresentador.botao("BOTAO_DESCARTAR", "desc"))));
            return;
        }
        conversa.descartarAudio();
        conversa.iniciarFluxo(FluxoConversa.REGISTRO_AULA, EtapaFluxoConversa.NENHUMA);
        contexto.responder(
                new MensagemEtapa(
                        contexto.chatId(),
                        editarMensagemId,
                        FluxoRelatorioAulaTelegram.textoEtapa(apresentador, trabalho),
                        trabalho.id()));
    }
}
