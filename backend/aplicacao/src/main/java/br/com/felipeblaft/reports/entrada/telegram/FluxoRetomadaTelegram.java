package br.com.felipeblaft.reports.entrada.telegram;

import br.com.felipeblaft.reports.domain.common.BusinessParameters;
import br.com.felipeblaft.reports.domain.common.ReportType;
import br.com.felipeblaft.reports.domain.report.OpenReport;
import br.com.felipeblaft.reports.domain.telegram.ConversationFlowStep;
import br.com.felipeblaft.reports.domain.telegram.TelegramConversation;
import br.com.felipeblaft.reports.entrada.telegram.PalavrasChaveTelegram.Comando;
import br.com.felipeblaft.reports.port.ClockPort;
import br.com.felipeblaft.reports.usecase.home.SearchStudentsUseCase;
import br.com.felipeblaft.reports.usecase.home.StudentSummary;
import br.com.felipeblaft.reports.usecase.job.DiscardRegistrationInProcessingUseCase;
import br.com.felipeblaft.reports.usecase.lesson.CancelLessonReportUseCase;
import br.com.felipeblaft.reports.usecase.lesson.GetOpenReportUseCase;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Retomada entre canais (US3, sequência 5.3): depois do aviso "Você tem um relatório em aberto",
 * {@code cont} reapresenta a etapa atual (processamento, perguntas ou conferência na versão
 * vigente) e {@code desc} pede confirmação, descarta e segue para o novo registro, reaproveitando o
 * áudio pendente se ainda for válido.
 */
@Component
@Order(5)
public class FluxoRetomadaTelegram implements FluxoTelegram {

    static final String CONFIRMACAO_DESCARTE = "descartar";

    private final GetOpenReportUseCase obterEmAberto;
    private final CancelLessonReportUseCase cancelar;
    private final DiscardRegistrationInProcessingUseCase descartarRegistro;
    private final SearchStudentsUseCase buscarAlunos;
    private final ApresentacaoRelatorioAulaTelegram apresentacao;
    private final ApresentadorTelegram apresentador;
    private final FluxoRegistroAulaTelegram registro;
    private final InterpretadorConversaTelegram interpretador;
    private final BusinessParameters parametros;
    private final ClockPort relogio;

    @SuppressWarnings("java:S107")
    public FluxoRetomadaTelegram(
            GetOpenReportUseCase obterEmAberto,
            CancelLessonReportUseCase cancelar,
            DiscardRegistrationInProcessingUseCase descartarRegistro,
            SearchStudentsUseCase buscarAlunos,
            ApresentacaoRelatorioAulaTelegram apresentacao,
            ApresentadorTelegram apresentador,
            FluxoRegistroAulaTelegram registro,
            InterpretadorConversaTelegram interpretador,
            BusinessParameters parametros,
            ClockPort relogio) {
        this.obterEmAberto = obterEmAberto;
        this.cancelar = cancelar;
        this.descartarRegistro = descartarRegistro;
        this.buscarAlunos = buscarAlunos;
        this.apresentacao = apresentacao;
        this.apresentador = apresentador;
        this.registro = registro;
        this.interpretador = interpretador;
        this.parametros = parametros;
        this.relogio = relogio;
    }

    /** Aluno do aviso em aberto guardado na conversa, se a conversa está nessa etapa. */
    private Optional<UUID> alunoDoAviso(ContextoTelegram contexto) {
        TelegramConversation conversa = contexto.conversa();
        if (conversa.step() != ConversationFlowStep.AVISO_RELATORIO_EM_ABERTO) {
            return Optional.empty();
        }
        return conversa.data(FluxoRegistroAulaTelegram.ALUNO_EM_ABERTO).map(UUID::fromString);
    }

    @Override
    public boolean aoCallback(ContextoTelegram contexto, String acao, String argumento) {
        switch (acao) {
            case "cont" -> {
                return alunoDoAviso(contexto)
                        .map(aluno -> continuar(contexto, aluno))
                        .orElse(false);
            }
            case "desc" -> {
                return alunoDoAviso(contexto).map(aluno -> pedirDescarte(contexto)).orElse(false);
            }
            case "descok" -> {
                Optional<UUID> aluno = alunoDoAviso(contexto);
                if (aluno.isEmpty()
                        || !contexto.conversa()
                                .pendingConfirmation()
                                .filter(CONFIRMACAO_DESCARTE::equals)
                                .isPresent()) {
                    return false;
                }
                contexto.conversa().cleanConfirmation();
                return descartar(contexto, aluno.get());
            }
            default -> {
                return false;
            }
        }
    }

    @Override
    public boolean aoComando(ContextoTelegram contexto, Comando comando) {
        Optional<UUID> aluno = alunoDoAviso(contexto);
        if (aluno.isEmpty()) {
            return false;
        }
        return switch (comando) {
            case CONTINUAR -> continuar(contexto, aluno.get());
            case DESCARTAR -> pedirDescarte(contexto);
            default -> false;
        };
    }

    @Override
    public boolean aoConfirmar(ContextoTelegram contexto, String confirmacao) {
        if (!CONFIRMACAO_DESCARTE.equals(confirmacao)) {
            return false;
        }
        return alunoDoAviso(contexto).map(aluno -> descartar(contexto, aluno)).orElse(false);
    }

    @Override
    public String oQuePossoReceber(ContextoTelegram contexto) {
        return alunoDoAviso(contexto).isPresent()
                ? apresentador.rotulo("BOTAO_CONTINUAR")
                        + " / "
                        + apresentador.rotulo("BOTAO_DESCARTAR")
                : null;
    }

    // ------------------------------------------------------------------ ações

    private boolean continuar(ContextoTelegram contexto, UUID alunoId) {
        TelegramConversation conversa = contexto.conversa();
        conversa.discardAudio();
        conversa.endFlow();
        Optional<OpenReport> aberto = obterEmAberto.run(contexto.professor().id(), alunoId);
        if (aberto.isEmpty()) {
            // concluído ou descartado no outro canal enquanto isso: segue para um novo registro
            aluno(contexto, alunoId)
                    .ifPresent(a -> interpretador.selecionarParaRegistro(contexto, a));
            return true;
        }
        OpenReport relatorio = aberto.get();
        if (relatorio.reportId() != null && relatorio.type() == ReportType.AULA) {
            apresentacao
                    .apresentar(
                            contexto.chatId(),
                            contexto.professor().id(),
                            relatorio.reportId(),
                            conversa,
                            null)
                    .forEach(contexto::responder);
        } else {
            String etapa = relatorio.step() == null ? "TRANSCREVENDO" : relatorio.step().name();
            contexto.responder(
                    apresentador.simples(
                            contexto.chatId(),
                            "PROCESSAMENTO_EM_ANDAMENTO",
                            Map.of("etapa", apresentador.formatacao().etapa(etapa))));
        }
        return true;
    }

    private boolean pedirDescarte(ContextoTelegram contexto) {
        contexto.conversa().askConfirmation(CONFIRMACAO_DESCARTE);
        contexto.responder(
                apresentador.confirmacao(
                        contexto.chatId(),
                        apresentador.texto("CONFIRMAR_DESCARTE"),
                        apresentador.botao("BOTAO_CONFIRMAR_DESCARTE", "descok")));
        return true;
    }

    private boolean descartar(ContextoTelegram contexto, UUID alunoId) {
        UUID professorId = contexto.professor().id();
        obterEmAberto
                .run(professorId, alunoId)
                .ifPresent(
                        aberto -> {
                            if (aberto.reportId() != null) {
                                cancelar.discard(professorId, aberto.reportId());
                            } else if (aberto.jobId() != null) {
                                descartarRegistro.run(professorId, aberto.jobId());
                            }
                        });
        TelegramConversation conversa = contexto.conversa();
        conversa.cleanFocus();
        conversa.discardAudioIfExpired(relogio.now(), parametros.pendingAudioExpiry());
        Optional<StudentSummary> aluno = aluno(contexto, alunoId);
        if (aluno.isEmpty()) {
            interpretador.mostrarInicio(contexto);
            return true;
        }
        contexto.responder(apresentador.simples(contexto.chatId(), "RELATORIO_CANCELADO"));
        if (conversa.pendingAudio().isPresent()) {
            // o áudio enviado antes do aviso ainda vale: vira o novo registro
            registro.registrarAudioPendente(contexto, aluno.get(), contexto.mensagemCallbackId());
        } else {
            interpretador.selecionarParaRegistro(contexto, aluno.get());
        }
        return true;
    }

    private Optional<StudentSummary> aluno(ContextoTelegram contexto, UUID alunoId) {
        return buscarAlunos.run(contexto.professor().id(), "").students().stream()
                .filter(a -> a.id().equals(alunoId))
                .findFirst();
    }
}
