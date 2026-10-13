package br.com.felipeblaft.reports.entrada.telegram;

import br.com.felipeblaft.reports.domain.common.Channel;
import br.com.felipeblaft.reports.domain.common.DomainException;
import br.com.felipeblaft.reports.domain.common.ErrorCode;
import br.com.felipeblaft.reports.domain.common.ReportType;
import br.com.felipeblaft.reports.domain.job.ProcessingJob;
import br.com.felipeblaft.reports.domain.report.LessonReport;
import br.com.felipeblaft.reports.domain.report.ReportStatus;
import br.com.felipeblaft.reports.domain.telegram.ConversationFlowStep;
import br.com.felipeblaft.reports.domain.telegram.FocusReport;
import br.com.felipeblaft.reports.domain.telegram.TelegramConversation;
import br.com.felipeblaft.reports.entrada.telegram.PalavrasChaveTelegram.Comando;
import br.com.felipeblaft.reports.entrada.telegram.SaidaTelegram.Botao;
import br.com.felipeblaft.reports.entrada.telegram.SaidaTelegram.Mensagem;
import br.com.felipeblaft.reports.entrada.telegram.SaidaTelegram.MensagemEtapa;
import br.com.felipeblaft.reports.entrada.telegram.SaidaTelegram.Teclado;
import br.com.felipeblaft.reports.entrada.telegram.UpdateTelegram.ArquivoTelegram;
import br.com.felipeblaft.reports.entrada.telegram.UpdateTelegram.MensagemTelegram;
import br.com.felipeblaft.reports.port.ChannelFileReference;
import br.com.felipeblaft.reports.usecase.home.SearchStudentsUseCase;
import br.com.felipeblaft.reports.usecase.home.StudentSummary;
import br.com.felipeblaft.reports.usecase.job.QueryJobUseCase;
import br.com.felipeblaft.reports.usecase.lesson.AnswerQuestionsUseCase;
import br.com.felipeblaft.reports.usecase.lesson.ApproveLessonReportUseCase;
import br.com.felipeblaft.reports.usecase.lesson.CancelLessonReportUseCase;
import br.com.felipeblaft.reports.usecase.lesson.GetLessonReportUseCase.LessonReportView;
import br.com.felipeblaft.reports.usecase.lesson.RequestLessonReportChangeUseCase;
import br.com.felipeblaft.reports.usecase.lesson.RetryLessonReportUseCase;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Relatório de aula em foco (estados #3 a #6 da tabela, sequência 5.2, passos 5 a 8): resposta às
 * perguntas e pedido de alteração por texto ou áudio sem confirmação (FR-027b), aprovação e
 * cancelamento com confirmação, nova tentativa e o botão "Continuar no chat".
 */
@Component
@Order(10)
public class FluxoRelatorioAulaTelegram implements FluxoTelegram {

    static final String CONFIRMACAO_APROVAR = "aprovar:";
    static final String CONFIRMACAO_CANCELAR = "cancelar";

    private final ApresentacaoRelatorioAulaTelegram apresentacao;
    private final ApresentadorTelegram apresentador;
    private final AnswerQuestionsUseCase responder;
    private final RequestLessonReportChangeUseCase alterar;
    private final RetryLessonReportUseCase tentar;
    private final ApproveLessonReportUseCase aprovar;
    private final CancelLessonReportUseCase cancelar;
    private final QueryJobUseCase consultarTrabalho;
    private final SearchStudentsUseCase buscarAlunos;
    private final InterpretadorConversaTelegram interpretador;

    @SuppressWarnings("java:S107")
    public FluxoRelatorioAulaTelegram(
            ApresentacaoRelatorioAulaTelegram apresentacao,
            ApresentadorTelegram apresentador,
            AnswerQuestionsUseCase responder,
            RequestLessonReportChangeUseCase alterar,
            RetryLessonReportUseCase tentar,
            ApproveLessonReportUseCase aprovar,
            CancelLessonReportUseCase cancelar,
            QueryJobUseCase consultarTrabalho,
            SearchStudentsUseCase buscarAlunos,
            InterpretadorConversaTelegram interpretador) {
        this.apresentacao = apresentacao;
        this.apresentador = apresentador;
        this.responder = responder;
        this.alterar = alterar;
        this.tentar = tentar;
        this.aprovar = aprovar;
        this.cancelar = cancelar;
        this.consultarTrabalho = consultarTrabalho;
        this.buscarAlunos = buscarAlunos;
        this.interpretador = interpretador;
    }

    // ------------------------------------------------------------------ foco

    /** Relatório de aula em foco ainda aberto; um foco que não vale mais é limpo. */
    private Optional<LessonReportView> foco(ContextoTelegram contexto) {
        TelegramConversation conversa = contexto.conversa();
        Optional<FocusReport> foco =
                conversa.focusReport().filter(f -> f.type() == ReportType.AULA);
        if (foco.isEmpty()) {
            return Optional.empty();
        }
        try {
            LessonReportView visao = apresentacao.obter(contexto.professor().id(), foco.get().id());
            if (!visao.report().status().isOpen()) {
                conversa.cleanFocus();
                return Optional.empty();
            }
            return Optional.of(visao);
        } catch (DomainException naoEncontrado) {
            conversa.cleanFocus();
            return Optional.empty();
        }
    }

    private int versaoBase(ContextoTelegram contexto, LessonReport relatorio) {
        Integer apresentada = contexto.conversa().presentedVersion();
        return apresentada == null ? relatorio.currentVersion() : apresentada;
    }

    // ------------------------------------------------------------------ texto e áudio

    @Override
    public boolean aoTexto(ContextoTelegram contexto, String texto) {
        Optional<LessonReportView> foco = foco(contexto);
        if (foco.isEmpty()) {
            return false;
        }
        LessonReportView visao = foco.get();
        LessonReport relatorio = visao.report();
        if (ApresentacaoRelatorioAulaTelegram.emProcessamento(visao)) {
            contexto.responder(
                    new Mensagem(
                            contexto.chatId(), apresentacao.processando(visao), Teclado.NENHUM));
            return true;
        }
        UUID professorId = contexto.professor().id();
        switch (relatorio.status()) {
            case AGUARDANDO_RESPOSTAS ->
                    etapa(
                            contexto,
                            null,
                            responder.answerText(
                                    professorId, relatorio.id(), texto, Channel.TELEGRAM));
            case AGUARDANDO_CONFERENCIA -> {
                Optional<StudentSummary> outroAluno =
                        buscarAlunos
                                .exactMatch(professorId, texto)
                                .filter(a -> !a.id().equals(relatorio.studentId()));
                if (outroAluno.isPresent()) {
                    perguntarTrocaDeAluno(contexto, visao, outroAluno.get());
                    return true;
                }
                comVersaoVigente(
                        contexto,
                        visao,
                        () ->
                                etapa(
                                        contexto,
                                        null,
                                        alterar.changeByText(
                                                professorId,
                                                relatorio.id(),
                                                texto,
                                                versaoBase(contexto, relatorio),
                                                Channel.TELEGRAM)));
            }
            default -> contexto.responder(apresentacao.falha(contexto.chatId(), null));
        }
        return true;
    }

    @Override
    public boolean aoAudio(
            ContextoTelegram contexto, MensagemTelegram mensagem, ArquivoTelegram arquivo) {
        Optional<LessonReportView> foco = foco(contexto);
        if (foco.isEmpty()) {
            return false;
        }
        LessonReportView visao = foco.get();
        LessonReport relatorio = visao.report();
        if (ApresentacaoRelatorioAulaTelegram.emProcessamento(visao)) {
            contexto.responder(
                    new Mensagem(
                            contexto.chatId(), apresentacao.processando(visao), Teclado.NENHUM));
            return true;
        }
        UUID professorId = contexto.professor().id();
        ChannelFileReference audio = FluxoRegistroAulaTelegram.referencia(mensagem, arquivo);
        switch (relatorio.status()) {
            case AGUARDANDO_RESPOSTAS ->
                    etapa(
                            contexto,
                            null,
                            responder.answerAudio(
                                    professorId, relatorio.id(), audio, Channel.TELEGRAM));
            case AGUARDANDO_CONFERENCIA ->
                    comVersaoVigente(
                            contexto,
                            visao,
                            () ->
                                    etapa(
                                            contexto,
                                            null,
                                            alterar.changeByAudio(
                                                    professorId,
                                                    relatorio.id(),
                                                    audio,
                                                    versaoBase(contexto, relatorio),
                                                    Channel.TELEGRAM)));
            default -> contexto.responder(apresentacao.falha(contexto.chatId(), null));
        }
        return true;
    }

    /** Nome exato de outro aluno durante a conferência: pergunta antes de trocar (estado #4). */
    private void perguntarTrocaDeAluno(
            ContextoTelegram contexto, LessonReportView visao, StudentSummary aluno) {
        TelegramConversation conversa = contexto.conversa();
        conversa.store(InterpretadorConversaTelegram.OPCOES_ALUNOS, aluno.id().toString());
        conversa.defineStep(ConversationFlowStep.ESCOLHENDO_ALUNO);
        contexto.responder(
                new Mensagem(
                        contexto.chatId(),
                        apresentador.texto(
                                "TROCAR_ALUNO_DURANTE_CONFERENCIA",
                                Map.of("aluno", aluno.name(), "alunoAtual", visao.studentName())),
                        Teclado.de(
                                new Botao(
                                        apresentador.rotulo(
                                                "BOTAO_CONFIRMAR_ALUNO",
                                                Map.of("aluno", aluno.name())),
                                        DadosCallbackCodificador.codificar("aluno", "0")),
                                apresentador.botao(
                                        "BOTAO_VOLTAR",
                                        Map.of(),
                                        "foco",
                                        "a" + visao.report().id()))));
    }

    /** Versão apresentada desatualizada: avisa e reapresenta a conferência vigente (FR-033). */
    private void comVersaoVigente(
            ContextoTelegram contexto, LessonReportView visao, Runnable acao) {
        try {
            acao.run();
        } catch (DomainException erro) {
            if (erro.code() != ErrorCode.VERSAO_DESATUALIZADA) {
                throw erro;
            }
            contexto.responder(apresentador.erro(contexto.chatId(), erro.code(), erro.details()));
            apresentar(contexto, visao.report().id(), null);
        }
    }

    private void etapa(ContextoTelegram contexto, Long editar, ProcessingJob trabalho) {
        contexto.responder(
                new MensagemEtapa(
                        contexto.chatId(),
                        editar,
                        textoEtapa(apresentador, trabalho),
                        trabalho.id()));
    }

    static String textoEtapa(ApresentadorTelegram apresentador, ProcessingJob trabalho) {
        return "<i>"
                + ApresentadorTelegram.escapar(
                        apresentador.formatacao().etapa(trabalho.step().name()))
                + "…</i>";
    }

    // ------------------------------------------------------------------ comandos e botões

    @Override
    public boolean aoComando(ContextoTelegram contexto, Comando comando) {
        if (comando != Comando.APROVAR
                && comando != Comando.CANCELAR
                && comando != Comando.TENTAR) {
            return false;
        }
        Optional<LessonReportView> foco = foco(contexto);
        if (foco.isEmpty()) {
            return false;
        }
        LessonReport relatorio = foco.get().report();
        switch (comando) {
            case APROVAR -> pedirAprovacao(contexto, foco.get(), versaoBase(contexto, relatorio));
            case CANCELAR -> pedirCancelamento(contexto);
            default -> tentarNovamente(contexto, relatorio.id());
        }
        return true;
    }

    @Override
    public boolean aoCallback(ContextoTelegram contexto, String acao, String argumento) {
        switch (acao) {
            case "foco" -> {
                return continuarNoChat(contexto, argumento);
            }
            case "aprov", "aprovok", "alt", "canc", "cancok", "pdf", "tent" -> {
                Optional<LessonReportView> foco = foco(contexto);
                if (foco.isEmpty()) {
                    return false;
                }
                return acaoNoFoco(contexto, foco.get(), acao, argumento);
            }
            default -> {
                return false;
            }
        }
    }

    private boolean acaoNoFoco(
            ContextoTelegram contexto, LessonReportView visao, String acao, String argumento) {
        TelegramConversation conversa = contexto.conversa();
        LessonReport relatorio = visao.report();
        switch (acao) {
            case "aprov" -> {
                Integer versao = numero(argumento);
                if (versao == null) {
                    return false;
                }
                conversa.cleanConfirmation();
                pedirAprovacao(contexto, visao, versao);
            }
            case "aprovok" -> {
                Integer versao = numero(argumento);
                if (versao == null
                        || !conversa.pendingConfirmation()
                                .filter((CONFIRMACAO_APROVAR + versao)::equals)
                                .isPresent()) {
                    return false;
                }
                conversa.cleanConfirmation();
                aprovarVersao(contexto, relatorio.id(), versao);
            }
            case "cancok" -> {
                if (!conversa.pendingConfirmation()
                        .filter(CONFIRMACAO_CANCELAR::equals)
                        .isPresent()) {
                    return false;
                }
                conversa.cleanConfirmation();
                cancelarRelatorio(contexto, relatorio.id());
            }
            case "canc" -> {
                conversa.cleanConfirmation();
                pedirCancelamento(contexto);
            }
            case "alt" -> {
                conversa.cleanConfirmation();
                if (relatorio.status() != ReportStatus.AGUARDANDO_CONFERENCIA) {
                    return false;
                }
                contexto.responder(
                        apresentador.simples(contexto.chatId(), "PEDIDO_ALTERACAO_ROTULO"));
            }
            case "pdf" -> {
                if (relatorio.status() != ReportStatus.AGUARDANDO_CONFERENCIA) {
                    return false;
                }
                apresentacao
                        .documentoConferencia(
                                contexto.chatId(), contexto.professor().id(), relatorio)
                        .ifPresentOrElse(
                                contexto::responder,
                                () ->
                                        contexto.responder(
                                                apresentador.simples(
                                                        contexto.chatId(),
                                                        "CONFERENCIA_PDF_GERANDO")));
            }
            default -> {
                conversa.cleanConfirmation();
                tentarNovamente(contexto, relatorio.id());
            }
        }
        return true;
    }

    @Override
    public boolean aoConfirmar(ContextoTelegram contexto, String confirmacao) {
        if (!confirmacao.startsWith(CONFIRMACAO_APROVAR)
                && !confirmacao.equals(CONFIRMACAO_CANCELAR)) {
            return false;
        }
        Optional<LessonReportView> foco = foco(contexto);
        if (foco.isEmpty()) {
            return false;
        }
        UUID relatorioId = foco.get().report().id();
        if (confirmacao.equals(CONFIRMACAO_CANCELAR)) {
            cancelarRelatorio(contexto, relatorioId);
            return true;
        }
        Integer versao = numero(confirmacao.substring(CONFIRMACAO_APROVAR.length()));
        if (versao == null) {
            return false;
        }
        aprovarVersao(contexto, relatorioId, versao);
        return true;
    }

    @Override
    public String oQuePossoReceber(ContextoTelegram contexto) {
        return foco(contexto)
                .map(
                        visao ->
                                visao.report().status() == ReportStatus.AGUARDANDO_RESPOSTAS
                                        ? apresentador.rotulo("RECEBER_RESPOSTA")
                                        : apresentador.rotulo("RECEBER_ALTERACAO"))
                .orElse(null);
    }

    // ------------------------------------------------------------------ ações

    private void pedirAprovacao(ContextoTelegram contexto, LessonReportView visao, int versao) {
        LessonReport relatorio = visao.report();
        if (relatorio.status() != ReportStatus.AGUARDANDO_CONFERENCIA
                || ApresentacaoRelatorioAulaTelegram.emProcessamento(visao)) {
            throw new DomainException(ErrorCode.ACAO_NAO_PERMITIDA_NA_ETAPA);
        }
        if (versao != relatorio.currentVersion()) {
            contexto.responder(
                    apresentador.erro(
                            contexto.chatId(),
                            ErrorCode.VERSAO_DESATUALIZADA,
                            Map.of("versaoVigente", relatorio.currentVersion())));
            apresentar(contexto, relatorio.id(), null);
            return;
        }
        contexto.conversa().askConfirmation(CONFIRMACAO_APROVAR + versao);
        contexto.responder(
                apresentador.confirmacao(
                        contexto.chatId(),
                        apresentador.texto("CONFIRMAR_APROVACAO", Map.of("versao", versao)),
                        apresentador.botao(
                                "BOTAO_CONFIRMAR_APROVACAO",
                                Map.of("versao", versao),
                                "aprovok",
                                Integer.toString(versao))));
    }

    private void aprovarVersao(ContextoTelegram contexto, UUID relatorioId, int versao) {
        ApproveLessonReportUseCase.Approval aprovacao;
        try {
            aprovacao =
                    aprovar.run(contexto.professor().id(), relatorioId, versao, Channel.TELEGRAM);
        } catch (DomainException erro) {
            if (erro.code() != ErrorCode.VERSAO_DESATUALIZADA) {
                throw erro;
            }
            contexto.responder(apresentador.erro(contexto.chatId(), erro.code(), erro.details()));
            apresentar(contexto, relatorioId, null);
            return;
        }
        TelegramConversation conversa = contexto.conversa();
        conversa.cleanFocus();
        conversa.cleanSelection();
        conversa.endFlow();
        contexto.responder(
                new Mensagem(
                        contexto.chatId(),
                        apresentador.texto(
                                "RELATORIO_APROVADO",
                                Map.of(
                                        "data",
                                                apresentador
                                                        .formatacao()
                                                        .data(aprovacao.approvedAt()),
                                        "hora",
                                                apresentador
                                                        .formatacao()
                                                        .hora(aprovacao.approvedAt()))),
                        Teclado.de(
                                apresentador.botao("BOTAO_REGISTRAR_OUTRA_AULA", "reg"),
                                apresentador.botao("BOTAO_HISTORICO", "hist"))));
    }

    private void pedirCancelamento(ContextoTelegram contexto) {
        contexto.conversa().askConfirmation(CONFIRMACAO_CANCELAR);
        contexto.responder(
                apresentador.confirmacao(
                        contexto.chatId(),
                        apresentador.texto("CONFIRMAR_CANCELAMENTO"),
                        apresentador.botao("BOTAO_CONFIRMAR_CANCELAMENTO", "cancok")));
    }

    private void cancelarRelatorio(ContextoTelegram contexto, UUID relatorioId) {
        cancelar.cancel(contexto.professor().id(), relatorioId);
        TelegramConversation conversa = contexto.conversa();
        conversa.cleanFocus();
        conversa.cleanSelection();
        conversa.endFlow();
        contexto.responder(apresentador.simples(contexto.chatId(), "RELATORIO_CANCELADO"));
        interpretador.mostrarInicio(contexto);
    }

    private void tentarNovamente(ContextoTelegram contexto, UUID relatorioId) {
        ProcessingJob trabalho =
                tentar.run(contexto.professor().id(), relatorioId, Channel.TELEGRAM);
        etapa(contexto, contexto.mensagemCallbackId(), trabalho);
    }

    /** Botão "Continuar no chat" / "Continuar: {aluno}": revalida e põe o relatório em foco. */
    private boolean continuarNoChat(ContextoTelegram contexto, String argumento) {
        if (argumento == null || argumento.length() < 2) {
            return false;
        }
        UUID id;
        try {
            id = UUID.fromString(argumento.substring(1));
        } catch (IllegalArgumentException invalido) {
            return false;
        }
        contexto.conversa().cleanConfirmation();
        switch (argumento.charAt(0)) {
            case 'a' -> {
                contexto.conversa().discardAudio();
                apresentar(contexto, id, null);
                return true;
            }
            case 't' -> {
                QueryJobUseCase.JobView trabalho =
                        consultarTrabalho.run(contexto.professor().id(), id);
                if (trabalho.job().reportId() != null
                        && trabalho.job().reportType() == ReportType.AULA) {
                    apresentar(contexto, trabalho.job().reportId(), null);
                } else if (trabalho.job().status().inProgress()) {
                    contexto.responder(
                            apresentador.simples(
                                    contexto.chatId(),
                                    "PROCESSAMENTO_EM_ANDAMENTO",
                                    Map.of(
                                            "etapa",
                                            apresentador
                                                    .formatacao()
                                                    .etapa(trabalho.job().step().name()))));
                } else {
                    return false;
                }
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    private void apresentar(ContextoTelegram contexto, UUID relatorioId, String prefixo) {
        List<SaidaTelegram> saidas =
                apresentacao.apresentar(
                        contexto.chatId(),
                        contexto.professor().id(),
                        relatorioId,
                        contexto.conversa(),
                        prefixo);
        saidas.forEach(contexto::responder);
    }

    private static Integer numero(String argumento) {
        try {
            return argumento == null ? null : Integer.valueOf(argumento);
        } catch (NumberFormatException invalido) {
            return null;
        }
    }
}
