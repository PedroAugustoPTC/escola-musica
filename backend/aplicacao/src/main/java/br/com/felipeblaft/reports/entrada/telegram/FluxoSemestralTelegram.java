package br.com.felipeblaft.reports.entrada.telegram;

import br.com.felipeblaft.reports.configuracao.SecoesRelatorioSemestral;
import br.com.felipeblaft.reports.domain.common.Channel;
import br.com.felipeblaft.reports.domain.common.DomainException;
import br.com.felipeblaft.reports.domain.common.ErrorCode;
import br.com.felipeblaft.reports.domain.common.ReportType;
import br.com.felipeblaft.reports.domain.job.ProcessingJob;
import br.com.felipeblaft.reports.domain.registry.StudentSearch;
import br.com.felipeblaft.reports.domain.report.OpenReport;
import br.com.felipeblaft.reports.domain.semester.SemesterReport;
import br.com.felipeblaft.reports.domain.telegram.ConversationFlow;
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
import br.com.felipeblaft.reports.usecase.home.SearchStudentsUseCase;
import br.com.felipeblaft.reports.usecase.home.StudentSummary;
import br.com.felipeblaft.reports.usecase.lesson.ApproveLessonReportUseCase.Approval;
import br.com.felipeblaft.reports.usecase.semester.ApproveSemesterReportUseCase;
import br.com.felipeblaft.reports.usecase.semester.CancelSemesterReportUseCase;
import br.com.felipeblaft.reports.usecase.semester.GenerateSemesterReportUseCase;
import br.com.felipeblaft.reports.usecase.semester.GetSemesterPreviewUseCase;
import br.com.felipeblaft.reports.usecase.semester.GetSemesterPreviewUseCase.Preview;
import br.com.felipeblaft.reports.usecase.semester.GetSemesterReportUseCase.SemesterReportView;
import br.com.felipeblaft.reports.usecase.semester.ListAvailableSemestersUseCase;
import br.com.felipeblaft.reports.usecase.semester.ListAvailableSemestersUseCase.Semester;
import br.com.felipeblaft.reports.usecase.semester.RequestSemesterReportChangeUseCase;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Relatório semestral pelo bot (sequência 5.4, estado #8): aluno, período, prévia, geração e a
 * conferência com alteração, aprovação e cancelamento — os mesmos casos de uso da Web.
 */
@Component
@Order(8)
public class FluxoSemestralTelegram implements FluxoTelegram {

    static final String ALUNO = "semAluno";
    static final String OPCOES = "semOpcoes";
    static final String PERIODO = "semPeriodo";

    private final ListAvailableSemestersUseCase listar;
    private final GetSemesterPreviewUseCase previa;
    private final GenerateSemesterReportUseCase gerar;
    private final RequestSemesterReportChangeUseCase alterar;
    private final ApproveSemesterReportUseCase aprovar;
    private final CancelSemesterReportUseCase cancelar;
    private final SearchStudentsUseCase buscarAlunos;
    private final ApresentacaoRelatorioSemestralTelegram apresentacao;
    private final ApresentadorTelegram apresentador;
    private final SecoesRelatorioSemestral secoes;
    private final InterpretadorConversaTelegram interpretador;

    @SuppressWarnings("java:S107")
    public FluxoSemestralTelegram(
            ListAvailableSemestersUseCase listar,
            GetSemesterPreviewUseCase previa,
            GenerateSemesterReportUseCase gerar,
            RequestSemesterReportChangeUseCase alterar,
            ApproveSemesterReportUseCase aprovar,
            CancelSemesterReportUseCase cancelar,
            SearchStudentsUseCase buscarAlunos,
            ApresentacaoRelatorioSemestralTelegram apresentacao,
            ApresentadorTelegram apresentador,
            SecoesRelatorioSemestral secoes,
            InterpretadorConversaTelegram interpretador) {
        this.listar = listar;
        this.previa = previa;
        this.gerar = gerar;
        this.alterar = alterar;
        this.aprovar = aprovar;
        this.cancelar = cancelar;
        this.buscarAlunos = buscarAlunos;
        this.apresentacao = apresentacao;
        this.apresentador = apresentador;
        this.secoes = secoes;
        this.interpretador = interpretador;
    }

    // ------------------------------------------------------------------ escolha do período

    @Override
    public boolean aoEscolherAluno(ContextoTelegram contexto, StudentSummary aluno) {
        if (contexto.conversa().flow() != ConversationFlow.SEMESTRAL) {
            return false;
        }
        oferecerPeriodos(contexto, aluno);
        return true;
    }

    private void oferecerPeriodos(ContextoTelegram contexto, StudentSummary aluno) {
        TelegramConversation conversa = contexto.conversa();
        conversa.cleanFocus();
        conversa.startFlow(ConversationFlow.SEMESTRAL, ConversationFlowStep.ESCOLHENDO_SEMESTRE);
        conversa.store(ALUNO, aluno.id().toString());
        List<Semester> semestres = listar.run(contexto.professor().id(), aluno.id());
        if (semestres.isEmpty()) {
            contexto.responder(apresentador.simples(contexto.chatId(), "SEM_SEMESTRES"));
            return;
        }
        conversa.store(
                OPCOES, String.join(",", semestres.stream().map(s -> s.period().key()).toList()));
        StringBuilder texto =
                new StringBuilder(
                        apresentador.texto("ESCOLHER_SEMESTRE", Map.of("aluno", aluno.name())));
        List<List<Botao>> linhas = new ArrayList<>();
        for (int i = 0; i < semestres.size(); i++) {
            Semester semestre = semestres.get(i);
            String rotulo = secoes.rotuloPeriodo(semestre.period().key());
            texto.append('\n')
                    .append(
                            apresentador.texto(
                                    "SEMESTRE_INTERVALO",
                                    Map.of(
                                            "rotulo", rotulo,
                                            "inicio",
                                                    apresentador
                                                            .formatacao()
                                                            .data(semestre.period().start()),
                                            "fim",
                                                    apresentador
                                                            .formatacao()
                                                            .data(semestre.period().end()))));
            String botao =
                    (semestre.preSelected() ? "★ " : "")
                            + rotulo
                            + (semestre.inProgress()
                                    ? " · " + apresentador.rotulo("SEMESTRE_EM_ANDAMENTO")
                                    : "");
            linhas.add(
                    List.of(
                            new Botao(
                                    botao,
                                    DadosCallbackCodificador.codificar(
                                            "per", Integer.toString(i)))));
        }
        linhas.add(List.of(apresentador.botao("BOTAO_INICIO", "inicio")));
        contexto.responder(new Mensagem(contexto.chatId(), texto.toString(), new Teclado(linhas)));
    }

    private Optional<UUID> aluno(ContextoTelegram contexto) {
        return contexto.conversa().data(ALUNO).map(UUID::fromString);
    }

    private Optional<StudentSummary> resumo(ContextoTelegram contexto, UUID alunoId) {
        return buscarAlunos.run(contexto.professor().id(), "").students().stream()
                .filter(a -> a.id().equals(alunoId))
                .findFirst();
    }

    private boolean escolherPeriodo(ContextoTelegram contexto, String chave) {
        Optional<UUID> alunoId = aluno(contexto);
        if (alunoId.isEmpty()) {
            return false;
        }
        TelegramConversation conversa = contexto.conversa();
        conversa.store(PERIODO, chave);
        conversa.defineStep(ConversationFlowStep.CONFIRMANDO_GERACAO_SEMESTRAL);
        Preview p = previa.run(contexto.professor().id(), alunoId.get(), chave);
        String rotulo = secoes.rotuloPeriodo(chave);
        long chat = contexto.chatId();
        Botao voltar = apresentador.botao("BOTAO_VOLTAR", "voltar");
        if (p.approvedSemesterId().isPresent()) {
            contexto.responder(
                    new Mensagem(
                            chat, apresentador.texto("SEMESTRAL_JA_APROVADO"), Teclado.de(voltar)));
        } else if (p.openSemester().isPresent()) {
            conversa.defineStep(ConversationFlowStep.AVISO_RELATORIO_EM_ABERTO);
            contexto.responder(apresentador.avisoEmAberto(chat, p.openSemester().get()));
        } else if (p.approvedLessons() == 0) {
            contexto.responder(
                    new Mensagem(
                            chat,
                            apresentador.texto("SEM_AULAS_APROVADAS_NO_PERIODO"),
                            Teclado.de(voltar)));
        } else {
            String texto =
                    apresentador.texto(
                            "SEMESTRAL_PREVIA",
                            Map.of("rotulo", rotulo, "quantidade", p.approvedLessons()));
            if (p.openLessonReports() > 0) {
                texto +=
                        "\n"
                                + apresentador.texto(
                                        "HA_RELATORIOS_EM_ABERTO",
                                        Map.of("quantidade", p.openLessonReports()));
                contexto.responder(
                        new Mensagem(
                                chat,
                                texto,
                                Teclado.de(
                                        apresentador.botao("BOTAO_GERAR_MESMO_ASSIM", "gerarsim"),
                                        voltar)));
            } else {
                contexto.responder(
                        new Mensagem(
                                chat,
                                texto,
                                Teclado.de(
                                        apresentador.botao("BOTAO_GERAR_SEMESTRAL", "gerar"),
                                        voltar)));
            }
        }
        return true;
    }

    private void gerar(ContextoTelegram contexto, boolean mesmoComEmAberto) {
        UUID alunoId = aluno(contexto).orElseThrow();
        String chave = contexto.conversa().data(PERIODO).orElseThrow();
        ProcessingJob trabalho =
                gerar.run(
                        contexto.professor().id(),
                        alunoId,
                        chave,
                        mesmoComEmAberto,
                        Channel.TELEGRAM);
        contexto.conversa().endFlow();
        contexto.responder(
                new MensagemEtapa(
                        contexto.chatId(),
                        contexto.mensagemCallbackId(),
                        FluxoRelatorioAulaTelegram.textoEtapa(apresentador, trabalho),
                        trabalho.id()));
    }

    // ------------------------------------------------------------------ semestral em foco

    private Optional<SemesterReportView> foco(ContextoTelegram contexto) {
        TelegramConversation conversa = contexto.conversa();
        Optional<FocusReport> foco =
                conversa.focusReport().filter(f -> f.type() == ReportType.SEMESTRAL);
        if (foco.isEmpty()) {
            return Optional.empty();
        }
        try {
            SemesterReportView visao =
                    apresentacao.obter(contexto.professor().id(), foco.get().id());
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

    private int versaoBase(ContextoTelegram contexto, SemesterReport relatorio) {
        Integer apresentada = contexto.conversa().presentedVersion();
        return apresentada == null ? relatorio.currentVersion() : apresentada;
    }

    @Override
    public boolean aoTexto(ContextoTelegram contexto, String texto) {
        Optional<SemesterReportView> foco = foco(contexto);
        if (foco.isPresent()) {
            SemesterReportView visao = foco.get();
            if (ApresentacaoRelatorioSemestralTelegram.emProcessamento(visao)) {
                contexto.responder(
                        new Mensagem(
                                contexto.chatId(),
                                apresentacao.processando(visao),
                                Teclado.NENHUM));
                return true;
            }
            SemesterReport relatorio = visao.report();
            comVersaoVigente(
                    contexto,
                    relatorio.id(),
                    () ->
                            etapa(
                                    contexto,
                                    alterar.changeByText(
                                            contexto.professor().id(),
                                            relatorio.id(),
                                            texto,
                                            versaoBase(contexto, relatorio),
                                            Channel.TELEGRAM)));
            return true;
        }
        TelegramConversation conversa = contexto.conversa();
        if (conversa.flow() == ConversationFlow.SEMESTRAL
                && conversa.step() == ConversationFlowStep.ESCOLHENDO_SEMESTRE) {
            String normalizado = StudentSearch.normalize(texto);
            for (String chave : opcoes(conversa)) {
                if (normalizado.equals(chave)
                        || normalizado.equals(
                                StudentSearch.normalize(secoes.rotuloPeriodo(chave)))) {
                    return escolherPeriodo(contexto, chave);
                }
            }
        }
        return false;
    }

    @Override
    public boolean aoAudio(
            ContextoTelegram contexto, MensagemTelegram mensagem, ArquivoTelegram arquivo) {
        Optional<SemesterReportView> foco = foco(contexto);
        if (foco.isEmpty()) {
            return false;
        }
        SemesterReportView visao = foco.get();
        if (ApresentacaoRelatorioSemestralTelegram.emProcessamento(visao)) {
            contexto.responder(
                    new Mensagem(
                            contexto.chatId(), apresentacao.processando(visao), Teclado.NENHUM));
            return true;
        }
        SemesterReport relatorio = visao.report();
        comVersaoVigente(
                contexto,
                relatorio.id(),
                () ->
                        etapa(
                                contexto,
                                alterar.changeByAudio(
                                        contexto.professor().id(),
                                        relatorio.id(),
                                        FluxoRegistroAulaTelegram.referencia(mensagem, arquivo),
                                        versaoBase(contexto, relatorio),
                                        Channel.TELEGRAM)));
        return true;
    }

    private void comVersaoVigente(ContextoTelegram contexto, UUID relatorioId, Runnable acao) {
        try {
            acao.run();
        } catch (DomainException erro) {
            if (erro.code() != ErrorCode.VERSAO_DESATUALIZADA) {
                throw erro;
            }
            contexto.responder(apresentador.erro(contexto.chatId(), erro.code(), erro.details()));
            apresentar(contexto, relatorioId);
        }
    }

    private void etapa(ContextoTelegram contexto, ProcessingJob trabalho) {
        contexto.responder(
                new MensagemEtapa(
                        contexto.chatId(),
                        null,
                        FluxoRelatorioAulaTelegram.textoEtapa(apresentador, trabalho),
                        trabalho.id()));
    }

    private void apresentar(ContextoTelegram contexto, UUID relatorioId) {
        apresentacao
                .apresentar(
                        contexto.chatId(),
                        contexto.professor().id(),
                        relatorioId,
                        contexto.conversa(),
                        null)
                .forEach(contexto::responder);
    }

    private List<String> opcoes(TelegramConversation conversa) {
        return conversa.data(OPCOES).map(s -> List.of(s.split(","))).orElse(List.of());
    }

    // ------------------------------------------------------------------ botões e comandos

    @Override
    public boolean aoCallback(ContextoTelegram contexto, String acao, String argumento) {
        TelegramConversation conversa = contexto.conversa();
        boolean noFluxo = conversa.flow() == ConversationFlow.SEMESTRAL;
        switch (acao) {
            case "per" -> {
                List<String> opcoes = opcoes(conversa);
                try {
                    int indice = Integer.parseInt(argumento);
                    if (!noFluxo || indice < 0 || indice >= opcoes.size()) {
                        return false;
                    }
                    conversa.cleanConfirmation();
                    return escolherPeriodo(contexto, opcoes.get(indice));
                } catch (NumberFormatException invalido) {
                    return false;
                }
            }
            case "gerar", "gerarsim" -> {
                if (!noFluxo
                        || conversa.step() != ConversationFlowStep.CONFIRMANDO_GERACAO_SEMESTRAL) {
                    return false;
                }
                gerar(contexto, acao.equals("gerarsim"));
                return true;
            }
            case "voltar" -> {
                Optional<StudentSummary> aluno =
                        aluno(contexto).flatMap(id -> resumo(contexto, id));
                if (!noFluxo || aluno.isEmpty()) {
                    return false;
                }
                oferecerPeriodos(contexto, aluno.get());
                return true;
            }
            case "cont" -> {
                return noFluxo
                        && conversa.step() == ConversationFlowStep.AVISO_RELATORIO_EM_ABERTO
                        && continuar(contexto);
            }
            case "desc" -> {
                if (!noFluxo || conversa.step() != ConversationFlowStep.AVISO_RELATORIO_EM_ABERTO) {
                    return false;
                }
                pedirDescarte(contexto);
                return true;
            }
            case "descok" -> {
                if (!noFluxo
                        || !conversa.pendingConfirmation()
                                .filter(FluxoRetomadaTelegram.CONFIRMACAO_DESCARTE::equals)
                                .isPresent()) {
                    return false;
                }
                conversa.cleanConfirmation();
                return descartar(contexto);
            }
            case "foco" -> {
                return continuarNoChat(contexto, argumento);
            }
            case "aprov", "aprovok", "alt", "canc", "cancok", "pdf" -> {
                Optional<SemesterReportView> foco = foco(contexto);
                return foco.isPresent() && acaoNoFoco(contexto, foco.get(), acao, argumento);
            }
            default -> {
                return false;
            }
        }
    }

    @Override
    public boolean aoComando(ContextoTelegram contexto, Comando comando) {
        TelegramConversation conversa = contexto.conversa();
        if (conversa.flow() == ConversationFlow.SEMESTRAL
                && conversa.step() == ConversationFlowStep.AVISO_RELATORIO_EM_ABERTO) {
            if (comando == Comando.CONTINUAR) {
                return continuar(contexto);
            }
            if (comando == Comando.DESCARTAR) {
                pedirDescarte(contexto);
                return true;
            }
        }
        if (comando != Comando.APROVAR && comando != Comando.CANCELAR) {
            return false;
        }
        Optional<SemesterReportView> foco = foco(contexto);
        if (foco.isEmpty()) {
            return false;
        }
        if (comando == Comando.APROVAR) {
            pedirAprovacao(contexto, foco.get(), versaoBase(contexto, foco.get().report()));
        } else {
            pedirCancelamento(contexto);
        }
        return true;
    }

    @Override
    public boolean aoConfirmar(ContextoTelegram contexto, String confirmacao) {
        TelegramConversation conversa = contexto.conversa();
        if (FluxoRetomadaTelegram.CONFIRMACAO_DESCARTE.equals(confirmacao)
                && conversa.flow() == ConversationFlow.SEMESTRAL) {
            return descartar(contexto);
        }
        Optional<SemesterReportView> foco = foco(contexto);
        if (foco.isEmpty()) {
            return false;
        }
        UUID id = foco.get().report().id();
        if (FluxoRelatorioAulaTelegram.CONFIRMACAO_CANCELAR.equals(confirmacao)) {
            cancelarSemestral(contexto, id);
            return true;
        }
        if (confirmacao.startsWith(FluxoRelatorioAulaTelegram.CONFIRMACAO_APROVAR)) {
            try {
                aprovarVersao(
                        contexto,
                        id,
                        Integer.parseInt(
                                confirmacao.substring(
                                        FluxoRelatorioAulaTelegram.CONFIRMACAO_APROVAR.length())));
                return true;
            } catch (NumberFormatException invalido) {
                return false;
            }
        }
        return false;
    }

    @Override
    public String oQuePossoReceber(ContextoTelegram contexto) {
        if (foco(contexto).isPresent()) {
            return apresentador.rotulo("RECEBER_ALTERACAO");
        }
        return null;
    }

    private boolean acaoNoFoco(
            ContextoTelegram contexto, SemesterReportView visao, String acao, String argumento) {
        TelegramConversation conversa = contexto.conversa();
        SemesterReport relatorio = visao.report();
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
                                .filter(
                                        (FluxoRelatorioAulaTelegram.CONFIRMACAO_APROVAR + versao)
                                                ::equals)
                                .isPresent()) {
                    return false;
                }
                conversa.cleanConfirmation();
                aprovarVersao(contexto, relatorio.id(), versao);
            }
            case "cancok" -> {
                if (!conversa.pendingConfirmation()
                        .filter(FluxoRelatorioAulaTelegram.CONFIRMACAO_CANCELAR::equals)
                        .isPresent()) {
                    return false;
                }
                conversa.cleanConfirmation();
                cancelarSemestral(contexto, relatorio.id());
            }
            case "canc" -> {
                conversa.cleanConfirmation();
                pedirCancelamento(contexto);
            }
            case "alt" ->
                    contexto.responder(
                            apresentador.simples(contexto.chatId(), "PEDIDO_ALTERACAO_ROTULO"));
            default ->
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
        return true;
    }

    private void pedirAprovacao(ContextoTelegram contexto, SemesterReportView visao, int versao) {
        SemesterReport relatorio = visao.report();
        if (ApresentacaoRelatorioSemestralTelegram.emProcessamento(visao)) {
            throw new DomainException(ErrorCode.ACAO_NAO_PERMITIDA_NA_ETAPA);
        }
        if (versao != relatorio.currentVersion()) {
            contexto.responder(
                    apresentador.erro(
                            contexto.chatId(),
                            ErrorCode.VERSAO_DESATUALIZADA,
                            Map.of("versaoVigente", relatorio.currentVersion())));
            apresentar(contexto, relatorio.id());
            return;
        }
        contexto.conversa()
                .askConfirmation(FluxoRelatorioAulaTelegram.CONFIRMACAO_APROVAR + versao);
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
        Approval aprovacao;
        try {
            aprovacao =
                    aprovar.run(contexto.professor().id(), relatorioId, versao, Channel.TELEGRAM);
        } catch (DomainException erro) {
            if (erro.code() != ErrorCode.VERSAO_DESATUALIZADA) {
                throw erro;
            }
            contexto.responder(apresentador.erro(contexto.chatId(), erro.code(), erro.details()));
            apresentar(contexto, relatorioId);
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
                                apresentador.botao("BOTAO_HISTORICO", "hist"),
                                apresentador.botao("BOTAO_INICIO", "inicio"))));
    }

    private void pedirCancelamento(ContextoTelegram contexto) {
        contexto.conversa().askConfirmation(FluxoRelatorioAulaTelegram.CONFIRMACAO_CANCELAR);
        contexto.responder(
                apresentador.confirmacao(
                        contexto.chatId(),
                        apresentador.texto("CONFIRMAR_CANCELAMENTO"),
                        apresentador.botao("BOTAO_CONFIRMAR_CANCELAMENTO", "cancok")));
    }

    private void cancelarSemestral(ContextoTelegram contexto, UUID relatorioId) {
        cancelar.cancel(contexto.professor().id(), relatorioId);
        TelegramConversation conversa = contexto.conversa();
        conversa.cleanFocus();
        conversa.cleanSelection();
        conversa.endFlow();
        contexto.responder(apresentador.simples(contexto.chatId(), "RELATORIO_CANCELADO"));
        interpretador.mostrarInicio(contexto);
    }

    // ------------------------------------------------------------------ retomada do semestral

    private Optional<OpenReport> emAberto(ContextoTelegram contexto) {
        Optional<UUID> alunoId = aluno(contexto);
        Optional<String> chave = contexto.conversa().data(PERIODO);
        if (alunoId.isEmpty() || chave.isEmpty()) {
            return Optional.empty();
        }
        return previa.run(contexto.professor().id(), alunoId.get(), chave.get()).openSemester();
    }

    private boolean continuar(ContextoTelegram contexto) {
        Optional<OpenReport> aberto = emAberto(contexto);
        contexto.conversa().endFlow();
        if (aberto.isEmpty()) {
            interpretador.mostrarInicio(contexto);
            return true;
        }
        if (aberto.get().reportId() != null) {
            apresentar(contexto, aberto.get().reportId());
        } else {
            String etapa =
                    aberto.get().step() == null ? "CONSOLIDANDO" : aberto.get().step().name();
            contexto.responder(
                    apresentador.simples(
                            contexto.chatId(),
                            "PROCESSAMENTO_EM_ANDAMENTO",
                            Map.of("etapa", apresentador.formatacao().etapa(etapa))));
        }
        return true;
    }

    private void pedirDescarte(ContextoTelegram contexto) {
        contexto.conversa().askConfirmation(FluxoRetomadaTelegram.CONFIRMACAO_DESCARTE);
        contexto.responder(
                apresentador.confirmacao(
                        contexto.chatId(),
                        apresentador.texto("CONFIRMAR_DESCARTE"),
                        apresentador.botao("BOTAO_CONFIRMAR_DESCARTE", "descok")));
    }

    private boolean descartar(ContextoTelegram contexto) {
        Optional<OpenReport> aberto = emAberto(contexto);
        UUID professorId = contexto.professor().id();
        aberto.ifPresent(
                a -> {
                    if (a.reportId() != null) {
                        cancelar.cancel(professorId, a.reportId());
                    } else if (a.jobId() != null) {
                        cancelar.discardConsolidation(professorId, a.jobId());
                    }
                });
        contexto.responder(apresentador.simples(contexto.chatId(), "RELATORIO_CANCELADO"));
        Optional<String> chave = contexto.conversa().data(PERIODO);
        return chave.map(c -> escolherPeriodo(contexto, c)).orElse(true);
    }

    /** Botão "Continuar: {aluno}" de um semestral (argumento {@code s<UUID>}). */
    private boolean continuarNoChat(ContextoTelegram contexto, String argumento) {
        if (argumento == null || argumento.length() < 2 || argumento.charAt(0) != 's') {
            return false;
        }
        try {
            UUID id = UUID.fromString(argumento.substring(1));
            contexto.conversa().cleanConfirmation();
            apresentar(contexto, id);
            return true;
        } catch (IllegalArgumentException invalido) {
            return false;
        }
    }

    private static Integer numero(String argumento) {
        try {
            return argumento == null ? null : Integer.valueOf(argumento);
        } catch (NumberFormatException invalido) {
            return null;
        }
    }
}
