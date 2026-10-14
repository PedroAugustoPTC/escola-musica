package br.com.felipeblaft.reports.outbound.telegram;

import br.com.felipeblaft.reports.domain.common.Channel;
import br.com.felipeblaft.reports.domain.common.DomainException;
import br.com.felipeblaft.reports.domain.common.ErrorCode;
import br.com.felipeblaft.reports.domain.common.ReportType;
import br.com.felipeblaft.reports.domain.job.JobResult;
import br.com.felipeblaft.reports.domain.job.JobStep;
import br.com.felipeblaft.reports.domain.job.JobType;
import br.com.felipeblaft.reports.domain.job.ProcessingJob;
import br.com.felipeblaft.reports.domain.report.LessonReport;
import br.com.felipeblaft.reports.domain.telegram.FocusReport;
import br.com.felipeblaft.reports.domain.telegram.TelegramConversation;
import br.com.felipeblaft.reports.domain.telegram.TelegramLink;
import br.com.felipeblaft.reports.inbound.telegram.TelegramActionPublicationPort;
import br.com.felipeblaft.reports.inbound.telegram.TelegramLessonReportPresentation;
import br.com.felipeblaft.reports.inbound.telegram.TelegramOutput;
import br.com.felipeblaft.reports.inbound.telegram.TelegramOutput.Keyboard;
import br.com.felipeblaft.reports.inbound.telegram.TelegramOutput.Message;
import br.com.felipeblaft.reports.inbound.telegram.TelegramPresenter;
import br.com.felipeblaft.reports.inbound.telegram.TelegramSemesterReportPresentation;
import br.com.felipeblaft.reports.port.ConcurrencyConflictException;
import br.com.felipeblaft.reports.port.JobQueuePort;
import br.com.felipeblaft.reports.port.StudentRepository;
import br.com.felipeblaft.reports.port.TeacherNotificationPort;
import br.com.felipeblaft.reports.port.TelegramLinkRepository;
import br.com.felipeblaft.reports.usecase.lesson.GetLessonReportUseCase.LessonReportView;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Component;

/**
 * Avisos ativos ao professor pelo bot (FR-021, contracts/telegram-conversa.md §5.2 e §6).
 *
 * <ul>
 *   <li>Trabalho pedido no Telegram: a mensagem de etapa é editada no lugar e o resultado é
 *       entregue pela mesma apresentação da conversa, que põe o relatório em foco.
 *   <li>Trabalho pedido na Web, com vínculo: só um aviso curto com "Continuar no chat", sem mudar o
 *       foco da conversa até o professor tocar no botão.
 * </ul>
 *
 * Falhas de envio nunca interrompem o processamento.
 */
@Component
public class TelegramNotificationAdapter implements TeacherNotificationPort {

    private static final Logger LOG = LoggerFactory.getLogger(TelegramNotificationAdapter.class);
    private static final int SAVE_ATTEMPTS = 3;

    private final TelegramLinkRepository links;
    private final StudentRepository students;
    private final JobQueuePort queue;
    private final TelegramLessonReportPresentation presentation;
    private final TelegramSemesterReportPresentation semesterPresentation;
    private final TelegramPresenter presenter;
    private final TelegramActionPublicationPort publication;

    public TelegramNotificationAdapter(
            TelegramLinkRepository links,
            StudentRepository students,
            JobQueuePort queue,
            TelegramLessonReportPresentation presentation,
            TelegramSemesterReportPresentation semesterPresentation,
            TelegramPresenter presenter,
            TelegramActionPublicationPort publication) {
        this.links = links;
        this.students = students;
        this.queue = queue;
        this.presentation = presentation;
        this.semesterPresentation = semesterPresentation;
        this.presenter = presenter;
        this.publication = publication;
    }

    // ------------------------------------------------------------------ etapas

    @Override
    public void notifyStep(ProcessingJob job) {
        if (job.requestChannel() != Channel.TELEGRAM || job.step() == JobStep.ENVIANDO) {
            return;
        }
        guarded(
                "etapa",
                () -> {
                    Optional<TelegramLink> link = links.searchByTeacherId(job.teacherId());
                    if (link.isEmpty()) {
                        return;
                    }
                    // a etapa edita a mensagem de etapa já conhecida do trabalho; sem ela, o envio
                    // direto
                    // nada faz e o despacho do orquestrador a resolve pelo trabalho no momento da
                    // entrega
                    publication.publish(
                            "etapa:" + job.id() + ":" + job.step().name(),
                            job.teacherId(),
                            List.of(
                                    new TelegramOutput.StepMessage(
                                            link.get().chatId(),
                                            stepMessage(job).orElse(null),
                                            stepText(job),
                                            job.id())));
                });
    }

    /** Id da mensagem de etapa: no trabalho em memória ou, se o envio o gravou depois, no banco. */
    private Optional<Long> stepMessage(ProcessingJob job) {
        Optional<String> value =
                job.parameter(ProcessingJob.STEP_MESSAGE)
                        .or(
                                () ->
                                        queue.searchById(job.id())
                                                .flatMap(
                                                        t ->
                                                                t.parameter(
                                                                        ProcessingJob
                                                                                .STEP_MESSAGE)));
        value.ifPresent(v -> job.defineParameter(ProcessingJob.STEP_MESSAGE, v));
        try {
            return value.map(Long::valueOf);
        } catch (NumberFormatException invalid) {
            return Optional.empty();
        }
    }

    private String stepText(ProcessingJob job) {
        return "<i>"
                + TelegramPresenter.escape(presenter.formatting().step(job.step().name()))
                + "…</i>";
    }

    // ------------------------------------------------------------------ demora

    @Override
    public void notifyDelay(ProcessingJob job) {
        if (job.requestChannel() != Channel.TELEGRAM) {
            return;
        }
        guarded(
                "demora",
                () ->
                        links.searchByTeacherId(job.teacherId())
                                .ifPresent(
                                        link ->
                                                publication.publish(
                                                        "demora:" + job.id(),
                                                        job.teacherId(),
                                                        List.of(
                                                                presenter.simple(
                                                                        link.chatId(),
                                                                        "PROCESSAMENTO_DEMORADO")))));
    }

    // ------------------------------------------------------------------ resultado

    @Override
    public void notifyResult(ProcessingJob job) {
        if (job.result() == null || job.result() == JobResult.DESCARTADO) {
            return;
        }
        guarded(
                "resultado",
                () -> {
                    if (job.requestChannel() == Channel.WEB) {
                        webNotice(job);
                    } else {
                        resultInChat(job);
                    }
                });
    }

    private void webNotice(ProcessingJob job) {
        Optional<TelegramLink> link = links.searchByTeacherId(job.teacherId());
        if (link.isEmpty()) {
            return;
        }
        String code =
                switch (job.result()) {
                    case PRONTO_PARA_CONFERENCIA -> "AVISO_RESULTADO_WEB_PRONTO";
                    case PERGUNTAS_PENDENTES -> "AVISO_RESULTADO_WEB_PERGUNTAS";
                    case NOVA_VERSAO -> "AVISO_RESULTADO_WEB_NOVA_VERSAO";
                    case FALHA_ESTRUTURACAO, AUDIO_NAO_PROCESSAVEL, FALHA_CONSOLIDACAO ->
                            "AVISO_RESULTADO_WEB_FALHA";
                    default -> null;
                };
        if (code == null) {
            return;
        }
        String student = students.searchById(job.studentId()).map(a -> a.name()).orElse("");
        boolean semester = job.reportType() == ReportType.SEMESTRAL;
        Integer version = null;
        if (job.reportId() != null) {
            version =
                    semester
                            ? semesterPresentation
                                    .get(job.teacherId(), job.reportId())
                                    .report()
                                    .currentVersion()
                            : presentation
                                    .get(job.teacherId(), job.reportId())
                                    .report()
                                    .currentVersion();
        }
        Keyboard keyboard =
                job.reportId() == null
                        ? Keyboard.NONE
                        : Keyboard.of(
                                presenter.button(
                                        "CONTINUAR_NO_CHAT",
                                        Map.of(),
                                        "foco",
                                        (semester ? "s" : "a") + job.reportId()));
        publication.publish(
                "resultado:" + job.id(),
                job.teacherId(),
                List.of(
                        new Message(
                                link.get().chatId(),
                                presenter.text(
                                        code,
                                        Map.of(
                                                "aluno",
                                                student,
                                                "versao",
                                                version == null ? "" : version)),
                                keyboard)));
    }

    /**
     * Resultado de um trabalho pedido no chat: grava o foco na conversa (com nova tentativa diante
     * de outro update simultâneo) e só depois envia as mensagens.
     */
    private void resultInChat(ProcessingJob job) {
        for (int attempt = 1; attempt <= SAVE_ATTEMPTS; attempt++) {
            Optional<TelegramLink> link = links.searchByTeacherId(job.teacherId());
            if (link.isEmpty()) {
                return;
            }
            List<TelegramOutput> outputs = resultOutputs(job, link.get());
            try {
                links.save(link.get());
            } catch (OptimisticLockingFailureException | ConcurrencyConflictException conflict) {
                if (attempt < SAVE_ATTEMPTS) {
                    continue;
                }
                LOG.warn("telegram-foco-nao-gravado trabalhoId={}", job.id());
            }
            List<TelegramOutput> all = new ArrayList<>();
            readyStep(job, link.get().chatId()).ifPresent(all::add);
            all.addAll(outputs);
            if (!all.isEmpty()) {
                publication.publish("resultado:" + job.id(), job.teacherId(), all);
            }
            return;
        }
    }

    /** A mensagem de etapa vira "Pronto" (editada no lugar) antes das mensagens do resultado. */
    private Optional<TelegramOutput> readyStep(ProcessingJob job, long chatId) {
        if (job.result() != JobResult.PRONTO_PARA_CONFERENCIA
                && job.result() != JobResult.NOVA_VERSAO) {
            return Optional.empty();
        }
        return Optional.of(
                new TelegramOutput.StepMessage(
                        chatId,
                        stepMessage(job).orElse(null),
                        "<i>" + presenter.text("ETAPA_PRONTO") + "</i>",
                        job.id()));
    }

    List<TelegramOutput> resultOutputs(ProcessingJob job, TelegramLink link) {
        long chatId = link.chatId();
        TelegramConversation conversation = link.conversation();
        Optional<String> understood = understoodText(job);
        switch (job.result()) {
            case AUDIO_NAO_PROCESSAVEL -> {
                return List.of(presenter.simple(chatId, "AUDIO_NAO_PROCESSAVEL"));
            }
            case FALHA_CONSOLIDACAO -> {
                return List.of(presenter.simple(chatId, "FALHA_CONSOLIDACAO"));
            }
            default -> {
                // demais resultados têm relatório de aula
            }
        }
        if (job.reportId() == null) {
            return List.of();
        }
        if (job.reportType() == ReportType.SEMESTRAL) {
            return semesterOutputs(job, chatId, conversation, understood);
        }
        LessonReportView view;
        try {
            view = presentation.get(job.teacherId(), job.reportId());
        } catch (DomainException notFound) {
            return List.of();
        }
        LessonReport report = view.report();
        int current = report.currentVersion();
        switch (job.result()) {
            case SEM_ALTERACAO_DE_CONTEUDO, PEDIDO_DE_ACAO_IGNORADO, FALHA_ALTERACAO -> {
                conversation.focus(new FocusReport(ReportType.AULA, report.id()), current);
                String text =
                        presenter.text(job.result().name(), Map.of("versao", current))
                                + understood
                                        .map(
                                                t ->
                                                        "\n"
                                                                + presenter.text(
                                                                        "TEXTO_ENTENDIDO",
                                                                        Map.of("texto", t)))
                                        .orElse("");
                return List.of(presentation.reviewActions(chatId, current, text));
            }
            default -> {
                return new ArrayList<>(
                        presentation.present(
                                chatId,
                                job.teacherId(),
                                view,
                                conversation,
                                prefix(job, current, understood)));
            }
        }
    }

    private List<TelegramOutput> semesterOutputs(
            ProcessingJob job,
            long chatId,
            TelegramConversation conversation,
            Optional<String> understood) {
        String prefix =
                switch (job.result()) {
                    case NOVA_VERSAO,
                            SEM_ALTERACAO_DE_CONTEUDO,
                            PEDIDO_DE_ACAO_IGNORADO,
                            FALHA_ALTERACAO -> {
                        int current =
                                semesterPresentation
                                        .get(job.teacherId(), job.reportId())
                                        .report()
                                        .currentVersion();
                        String code =
                                job.result() == JobResult.NOVA_VERSAO
                                        ? "ALTERACAO_APLICADA"
                                        : job.result().name();
                        yield presenter.text(code, Map.of("versao", current))
                                + understood
                                        .map(
                                                t ->
                                                        "\n"
                                                                + presenter.text(
                                                                        "TEXTO_ENTENDIDO",
                                                                        Map.of("texto", t)))
                                        .orElse("");
                    }
                    case VERSAO_DESATUALIZADA ->
                            presenter.text(
                                    "VERSAO_DESATUALIZADA",
                                    Map.of(
                                            "versaoVigente",
                                            semesterPresentation
                                                    .get(job.teacherId(), job.reportId())
                                                    .report()
                                                    .currentVersion()));
                    default -> null;
                };
        try {
            return new ArrayList<>(
                    semesterPresentation.present(
                            chatId, job.teacherId(), job.reportId(), conversation, prefix));
        } catch (DomainException notFound) {
            return List.of();
        }
    }

    private String prefix(ProcessingJob job, int current, Optional<String> understood) {
        return switch (job.result()) {
            case NOVA_VERSAO ->
                    presenter.text("ALTERACAO_APLICADA", Map.of("versao", current))
                            + understood
                                    .map(
                                            t ->
                                                    "\n"
                                                            + presenter.text(
                                                                    "TEXTO_ENTENDIDO",
                                                                    Map.of("texto", t)))
                                    .orElse("");
            case VERSAO_DESATUALIZADA ->
                    TelegramPresenter.escape(
                            presenter.label(
                                    ErrorCode.VERSAO_DESATUALIZADA.messageKey(),
                                    Map.of("versaoVigente", current)));
            case FALHA_RESPOSTA -> presenter.text("FALHA_RESPOSTA");
            case PRONTO_PARA_CONFERENCIA, PERGUNTAS_PENDENTES ->
                    job.type() == JobType.RESPOSTA_PERGUNTAS && understood.isPresent()
                            ? presenter.text(
                                    "RESPOSTA_INCORPORADA", Map.of("texto", understood.get()))
                            : null;
            default -> null;
        };
    }

    private static Optional<String> understoodText(ProcessingJob job) {
        return job.parameter(ProcessingJob.UNDERSTOOD_TEXT)
                .or(() -> job.parameter(ProcessingJob.TEXT))
                .filter(t -> !t.isBlank());
    }

    private static void guarded(String notice, Runnable action) {
        try {
            action.run();
        } catch (RuntimeException error) {
            LOG.warn(
                    "telegram-notificacao-falhou aviso={} tipo={}",
                    notice,
                    error.getClass().getSimpleName());
        }
    }
}
