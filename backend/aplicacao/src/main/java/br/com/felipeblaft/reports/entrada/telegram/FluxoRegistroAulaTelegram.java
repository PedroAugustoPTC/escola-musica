package br.com.felipeblaft.reports.entrada.telegram;

import br.com.felipeblaft.reports.domain.common.BusinessParameters;
import br.com.felipeblaft.reports.domain.common.Channel;
import br.com.felipeblaft.reports.domain.common.DomainException;
import br.com.felipeblaft.reports.domain.common.ErrorCode;
import br.com.felipeblaft.reports.domain.job.ProcessingJob;
import br.com.felipeblaft.reports.domain.report.OpenReport;
import br.com.felipeblaft.reports.domain.telegram.ConversationFlow;
import br.com.felipeblaft.reports.domain.telegram.ConversationFlowStep;
import br.com.felipeblaft.reports.domain.telegram.PendingAudio;
import br.com.felipeblaft.reports.domain.telegram.TelegramConversation;
import br.com.felipeblaft.reports.entrada.telegram.PalavrasChaveTelegram.Comando;
import br.com.felipeblaft.reports.entrada.telegram.SaidaTelegram.Botao;
import br.com.felipeblaft.reports.entrada.telegram.SaidaTelegram.Mensagem;
import br.com.felipeblaft.reports.entrada.telegram.SaidaTelegram.MensagemEtapa;
import br.com.felipeblaft.reports.entrada.telegram.SaidaTelegram.Teclado;
import br.com.felipeblaft.reports.entrada.telegram.UpdateTelegram.ArquivoTelegram;
import br.com.felipeblaft.reports.entrada.telegram.UpdateTelegram.MensagemTelegram;
import br.com.felipeblaft.reports.port.ChannelFileReference;
import br.com.felipeblaft.reports.port.ClockPort;
import br.com.felipeblaft.reports.usecase.home.SearchStudentsUseCase;
import br.com.felipeblaft.reports.usecase.home.StudentSummary;
import br.com.felipeblaft.reports.usecase.lesson.GetOpenReportUseCase;
import br.com.felipeblaft.reports.usecase.lesson.RegisterLessonByAudioUseCase;
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

    private final RegisterLessonByAudioUseCase registrar;
    private final SearchStudentsUseCase buscarAlunos;
    private final InterpretadorConversaTelegram interpretador;
    private final ApresentadorTelegram apresentador;
    private final BusinessParameters parametros;
    private final ClockPort relogio;
    private final GetOpenReportUseCase obterEmAberto;

    public FluxoRegistroAulaTelegram(
            RegisterLessonByAudioUseCase registrar,
            SearchStudentsUseCase buscarAlunos,
            InterpretadorConversaTelegram interpretador,
            ApresentadorTelegram apresentador,
            BusinessParameters parametros,
            ClockPort relogio,
            GetOpenReportUseCase obterEmAberto) {
        this.obterEmAberto = obterEmAberto;
        this.registrar = registrar;
        this.buscarAlunos = buscarAlunos;
        this.interpretador = interpretador;
        this.apresentador = apresentador;
        this.parametros = parametros;
        this.relogio = relogio;
    }

    /** Referência ao arquivo do Telegram; a voz chega sem MIME declarado e é Ogg/Opus. */
    static ChannelFileReference referencia(MensagemTelegram mensagem, ArquivoTelegram arquivo) {
        String mime = arquivo.mimeType();
        if (mime == null) {
            mime =
                    arquivo == mensagem.voice()
                            ? "audio/ogg"
                            : arquivo == mensagem.videoNote() ? "video/mp4" : "audio/ogg";
        }
        return new ChannelFileReference(
                arquivo.fileId(),
                arquivo.fileUniqueId(),
                mime,
                arquivo.fileSize() == null ? 0 : arquivo.fileSize());
    }

    // ------------------------------------------------------------------ áudio recebido

    @Override
    public boolean aoAudio(
            ContextoTelegram contexto, MensagemTelegram mensagem, ArquivoTelegram arquivo) {
        TelegramConversation conversa = contexto.conversa();
        ChannelFileReference referencia = referencia(mensagem, arquivo);
        if (referencia.size() > parametros.audioLimitBytes()) {
            contexto.responder(
                    apresentador.erro(
                            contexto.chatId(),
                            ErrorCode.AUDIO_MUITO_GRANDE,
                            Map.of("limiteMb", parametros.audioLimitMb())));
            return true;
        }
        conversa.cleanFocus();
        conversa.receiveAudio(
                new PendingAudio(
                        referencia.id(),
                        referencia.uniqueId(),
                        referencia.mime(),
                        referencia.size(),
                        relogio.now()));
        Optional<StudentSummary> selecionado = alunoSelecionado(contexto);
        if (selecionado.isPresent()) {
            conversa.startFlow(
                    ConversationFlow.REGISTRO_AULA,
                    ConversationFlowStep.CONFIRMANDO_ALUNO_DO_AUDIO);
            pedirConfirmacaoDoAluno(contexto, selecionado.get());
        } else {
            conversa.startFlow(
                    ConversationFlow.REGISTRO_AULA, ConversationFlowStep.ESCOLHENDO_ALUNO);
            SearchStudentsUseCase.Result todos = buscarAlunos.run(contexto.professor().id(), "");
            if (todos.students().isEmpty()) {
                conversa.discardAudio();
                contexto.responder(apresentador.simples(contexto.chatId(), "SEM_ALUNOS"));
                return true;
            }
            interpretador.oferecerAlunos(
                    contexto,
                    apresentador.texto("AUDIO_RECEBIDO_ESCOLHER_ALUNO"),
                    todos.students());
        }
        return true;
    }

    private Optional<StudentSummary> alunoSelecionado(ContextoTelegram contexto) {
        UUID alunoId = contexto.conversa().selectedStudentId();
        if (alunoId == null) {
            return Optional.empty();
        }
        return buscarAlunos.run(contexto.professor().id(), "").students().stream()
                .filter(a -> a.id().equals(alunoId))
                .findFirst();
    }

    private void pedirConfirmacaoDoAluno(ContextoTelegram contexto, StudentSummary aluno) {
        contexto.responder(
                new Mensagem(
                        contexto.chatId(),
                        apresentador.texto(
                                "AUDIO_RECEBIDO_CONFIRMAR_ALUNO", Map.of("aluno", aluno.name())),
                        Teclado.de(
                                new Botao(
                                        apresentador.rotulo(
                                                "BOTAO_CONFIRMAR_ALUNO",
                                                Map.of("aluno", aluno.name())),
                                        DadosCallbackCodificador.codificar("confal")),
                                apresentador.botao("BOTAO_TROCAR_ALUNO", "trocal"))));
    }

    // ------------------------------------------------------------------ confirmação do aluno

    @Override
    public boolean aoEscolherAluno(ContextoTelegram contexto, StudentSummary aluno) {
        TelegramConversation conversa = contexto.conversa();
        if (conversa.flow() != ConversationFlow.REGISTRO_AULA
                && conversa.flow() != ConversationFlow.NENHUM) {
            return false;
        }
        if (conversa.pendingAudio().isEmpty()) {
            // aluno com relatório em aberto: avisa antes de pedir o áudio (FR-034)
            Optional<OpenReport> aberto = obterEmAberto.run(contexto.professor().id(), aluno.id());
            if (aberto.isEmpty()) {
                return false;
            }
            avisarEmAberto(contexto, aluno.id(), aberto.get());
            return true;
        }
        // com áudio pendente, escolher o aluno na lista (ou pelo nome) é a confirmação (FR-040)
        registrarAudioPendente(contexto, aluno, null);
        return true;
    }

    @Override
    public boolean aoCallback(ContextoTelegram contexto, String acao, String argumento) {
        TelegramConversation conversa = contexto.conversa();
        switch (acao) {
            case "confal" -> {
                Optional<StudentSummary> aluno = alunoSelecionado(contexto);
                if (conversa.pendingAudio().isEmpty() || aluno.isEmpty()) {
                    return false;
                }
                conversa.cleanConfirmation();
                registrarAudioPendente(contexto, aluno.get(), contexto.mensagemCallbackId());
                return true;
            }
            case "trocal" -> {
                if (conversa.pendingAudio().isEmpty()) {
                    return false;
                }
                conversa.cleanConfirmation();
                interpretador.oferecerAlunos(
                        contexto,
                        apresentador.texto("AUDIO_RECEBIDO_ESCOLHER_ALUNO"),
                        buscarAlunos.run(contexto.professor().id(), "").students());
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
        TelegramConversation conversa = contexto.conversa();
        if (comando != Comando.CANCELAR || conversa.pendingAudio().isEmpty()) {
            return false;
        }
        conversa.discardAudio();
        conversa.endFlow();
        contexto.responder(apresentador.simples(contexto.chatId(), "AUDIO_PENDENTE_DESCARTADO"));
        return true;
    }

    @Override
    public String oQuePossoReceber(ContextoTelegram contexto) {
        return contexto.conversa().pendingAudio().isPresent()
                ? apresentador.rotulo("RECEBER_ALUNO")
                : null;
    }

    /** Guarda o aluno do aviso na conversa e mostra "Você tem um relatório em aberto". */
    void avisarEmAberto(ContextoTelegram contexto, UUID alunoId, OpenReport aberto) {
        TelegramConversation conversa = contexto.conversa();
        conversa.cleanFocus();
        conversa.startFlow(
                ConversationFlow.REGISTRO_AULA, ConversationFlowStep.AVISO_RELATORIO_EM_ABERTO);
        conversa.store(ALUNO_EM_ABERTO, alunoId.toString());
        contexto.responder(apresentador.avisoEmAberto(contexto.chatId(), aberto));
    }

    void registrarAudioPendente(
            ContextoTelegram contexto, StudentSummary aluno, Long editarMensagemId) {
        TelegramConversation conversa = contexto.conversa();
        PendingAudio audio = conversa.pendingAudio().orElseThrow();
        conversa.selectStudent(aluno.id());
        ProcessingJob trabalho;
        try {
            trabalho =
                    registrar.run(
                            contexto.professor().id(),
                            aluno.id(),
                            new ChannelFileReference(
                                    audio.fileId(),
                                    audio.fileUniqueId(),
                                    audio.mime(),
                                    audio.size()),
                            Channel.TELEGRAM);
        } catch (DomainException erro) {
            if (erro.code() != ErrorCode.RELATORIO_EM_ABERTO) {
                conversa.discardAudio();
                throw erro;
            }
            // FR-034: avisa e oferece continuar ou descartar; o áudio só é reaproveitado no
            // descarte
            Optional<OpenReport> aberto = obterEmAberto.run(contexto.professor().id(), aluno.id());
            if (aberto.isEmpty()) {
                throw erro;
            }
            avisarEmAberto(contexto, aluno.id(), aberto.get());
            return;
        }
        conversa.discardAudio();
        conversa.startFlow(ConversationFlow.REGISTRO_AULA, ConversationFlowStep.NENHUMA);
        contexto.responder(
                new MensagemEtapa(
                        contexto.chatId(),
                        editarMensagemId,
                        FluxoRelatorioAulaTelegram.textoEtapa(apresentador, trabalho),
                        trabalho.id()));
    }
}
