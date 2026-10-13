package br.com.felipeblaft.reports.saida.telegram;

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
import br.com.felipeblaft.reports.entrada.telegram.ApresentacaoRelatorioAulaTelegram;
import br.com.felipeblaft.reports.entrada.telegram.ApresentacaoRelatorioSemestralTelegram;
import br.com.felipeblaft.reports.entrada.telegram.ApresentadorTelegram;
import br.com.felipeblaft.reports.entrada.telegram.PublicacaoAcaoTelegramPorta;
import br.com.felipeblaft.reports.entrada.telegram.SaidaTelegram;
import br.com.felipeblaft.reports.entrada.telegram.SaidaTelegram.Mensagem;
import br.com.felipeblaft.reports.entrada.telegram.SaidaTelegram.Teclado;
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
public class NotificacaoTelegramAdaptador implements TeacherNotificationPort {

    private static final Logger LOG = LoggerFactory.getLogger(NotificacaoTelegramAdaptador.class);
    private static final int TENTATIVAS_GRAVACAO = 3;

    private final TelegramLinkRepository vinculos;
    private final StudentRepository alunos;
    private final JobQueuePort fila;
    private final ApresentacaoRelatorioAulaTelegram apresentacao;
    private final ApresentacaoRelatorioSemestralTelegram apresentacaoSemestral;
    private final ApresentadorTelegram apresentador;
    private final PublicacaoAcaoTelegramPorta publicacao;

    public NotificacaoTelegramAdaptador(
            TelegramLinkRepository vinculos,
            StudentRepository alunos,
            JobQueuePort fila,
            ApresentacaoRelatorioAulaTelegram apresentacao,
            ApresentacaoRelatorioSemestralTelegram apresentacaoSemestral,
            ApresentadorTelegram apresentador,
            PublicacaoAcaoTelegramPorta publicacao) {
        this.vinculos = vinculos;
        this.alunos = alunos;
        this.fila = fila;
        this.apresentacao = apresentacao;
        this.apresentacaoSemestral = apresentacaoSemestral;
        this.apresentador = apresentador;
        this.publicacao = publicacao;
    }

    // ------------------------------------------------------------------ etapas

    @Override
    public void notifyStep(ProcessingJob trabalho) {
        if (trabalho.requestChannel() != Channel.TELEGRAM || trabalho.step() == JobStep.ENVIANDO) {
            return;
        }
        protegido(
                "etapa",
                () -> {
                    Optional<TelegramLink> vinculo =
                            vinculos.searchByTeacherId(trabalho.teacherId());
                    if (vinculo.isEmpty()) {
                        return;
                    }
                    // a etapa edita a mensagem de etapa já conhecida do trabalho; sem ela, o envio
                    // direto
                    // nada faz e o despacho do orquestrador a resolve pelo trabalho no momento da
                    // entrega
                    publicacao.publicar(
                            "etapa:" + trabalho.id() + ":" + trabalho.step().name(),
                            trabalho.teacherId(),
                            List.of(
                                    new SaidaTelegram.MensagemEtapa(
                                            vinculo.get().chatId(),
                                            mensagemEtapa(trabalho).orElse(null),
                                            textoEtapa(trabalho),
                                            trabalho.id())));
                });
    }

    /** Id da mensagem de etapa: no trabalho em memória ou, se o envio o gravou depois, no banco. */
    private Optional<Long> mensagemEtapa(ProcessingJob trabalho) {
        Optional<String> valor =
                trabalho.parameter(ProcessingJob.STEP_MESSAGE)
                        .or(
                                () ->
                                        fila.searchById(trabalho.id())
                                                .flatMap(
                                                        t ->
                                                                t.parameter(
                                                                        ProcessingJob
                                                                                .STEP_MESSAGE)));
        valor.ifPresent(v -> trabalho.defineParameter(ProcessingJob.STEP_MESSAGE, v));
        try {
            return valor.map(Long::valueOf);
        } catch (NumberFormatException invalido) {
            return Optional.empty();
        }
    }

    private String textoEtapa(ProcessingJob trabalho) {
        return "<i>"
                + ApresentadorTelegram.escapar(
                        apresentador.formatacao().etapa(trabalho.step().name()))
                + "…</i>";
    }

    // ------------------------------------------------------------------ demora

    @Override
    public void notifyDelay(ProcessingJob trabalho) {
        if (trabalho.requestChannel() != Channel.TELEGRAM) {
            return;
        }
        protegido(
                "demora",
                () ->
                        vinculos.searchByTeacherId(trabalho.teacherId())
                                .ifPresent(
                                        vinculo ->
                                                publicacao.publicar(
                                                        "demora:" + trabalho.id(),
                                                        trabalho.teacherId(),
                                                        List.of(
                                                                apresentador.simples(
                                                                        vinculo.chatId(),
                                                                        "PROCESSAMENTO_DEMORADO")))));
    }

    // ------------------------------------------------------------------ resultado

    @Override
    public void notifyResult(ProcessingJob trabalho) {
        if (trabalho.result() == null || trabalho.result() == JobResult.DESCARTADO) {
            return;
        }
        protegido(
                "resultado",
                () -> {
                    if (trabalho.requestChannel() == Channel.WEB) {
                        avisoDaWeb(trabalho);
                    } else {
                        resultadoNoChat(trabalho);
                    }
                });
    }

    private void avisoDaWeb(ProcessingJob trabalho) {
        Optional<TelegramLink> vinculo = vinculos.searchByTeacherId(trabalho.teacherId());
        if (vinculo.isEmpty()) {
            return;
        }
        String codigo =
                switch (trabalho.result()) {
                    case PRONTO_PARA_CONFERENCIA -> "AVISO_RESULTADO_WEB_PRONTO";
                    case PERGUNTAS_PENDENTES -> "AVISO_RESULTADO_WEB_PERGUNTAS";
                    case NOVA_VERSAO -> "AVISO_RESULTADO_WEB_NOVA_VERSAO";
                    case FALHA_ESTRUTURACAO, AUDIO_NAO_PROCESSAVEL, FALHA_CONSOLIDACAO ->
                            "AVISO_RESULTADO_WEB_FALHA";
                    default -> null;
                };
        if (codigo == null) {
            return;
        }
        String aluno = alunos.searchById(trabalho.studentId()).map(a -> a.name()).orElse("");
        boolean semestral = trabalho.reportType() == ReportType.SEMESTRAL;
        Integer versao = null;
        if (trabalho.reportId() != null) {
            versao =
                    semestral
                            ? apresentacaoSemestral
                                    .obter(trabalho.teacherId(), trabalho.reportId())
                                    .report()
                                    .currentVersion()
                            : apresentacao
                                    .obter(trabalho.teacherId(), trabalho.reportId())
                                    .report()
                                    .currentVersion();
        }
        Teclado teclado =
                trabalho.reportId() == null
                        ? Teclado.NENHUM
                        : Teclado.de(
                                apresentador.botao(
                                        "CONTINUAR_NO_CHAT",
                                        Map.of(),
                                        "foco",
                                        (semestral ? "s" : "a") + trabalho.reportId()));
        publicacao.publicar(
                "resultado:" + trabalho.id(),
                trabalho.teacherId(),
                List.of(
                        new Mensagem(
                                vinculo.get().chatId(),
                                apresentador.texto(
                                        codigo,
                                        Map.of(
                                                "aluno",
                                                aluno,
                                                "versao",
                                                versao == null ? "" : versao)),
                                teclado)));
    }

    /**
     * Resultado de um trabalho pedido no chat: grava o foco na conversa (com nova tentativa diante
     * de outro update simultâneo) e só depois envia as mensagens.
     */
    private void resultadoNoChat(ProcessingJob trabalho) {
        for (int tentativa = 1; tentativa <= TENTATIVAS_GRAVACAO; tentativa++) {
            Optional<TelegramLink> vinculo = vinculos.searchByTeacherId(trabalho.teacherId());
            if (vinculo.isEmpty()) {
                return;
            }
            List<SaidaTelegram> saidas = saidasDoResultado(trabalho, vinculo.get());
            try {
                vinculos.save(vinculo.get());
            } catch (OptimisticLockingFailureException | ConcurrencyConflictException conflito) {
                if (tentativa < TENTATIVAS_GRAVACAO) {
                    continue;
                }
                LOG.warn("telegram-foco-nao-gravado trabalhoId={}", trabalho.id());
            }
            List<SaidaTelegram> todas = new ArrayList<>();
            etapaPronta(trabalho, vinculo.get().chatId()).ifPresent(todas::add);
            todas.addAll(saidas);
            if (!todas.isEmpty()) {
                publicacao.publicar("resultado:" + trabalho.id(), trabalho.teacherId(), todas);
            }
            return;
        }
    }

    /** A mensagem de etapa vira "Pronto" (editada no lugar) antes das mensagens do resultado. */
    private Optional<SaidaTelegram> etapaPronta(ProcessingJob trabalho, long chatId) {
        if (trabalho.result() != JobResult.PRONTO_PARA_CONFERENCIA
                && trabalho.result() != JobResult.NOVA_VERSAO) {
            return Optional.empty();
        }
        return Optional.of(
                new SaidaTelegram.MensagemEtapa(
                        chatId,
                        mensagemEtapa(trabalho).orElse(null),
                        "<i>" + apresentador.texto("ETAPA_PRONTO") + "</i>",
                        trabalho.id()));
    }

    List<SaidaTelegram> saidasDoResultado(ProcessingJob trabalho, TelegramLink vinculo) {
        long chatId = vinculo.chatId();
        TelegramConversation conversa = vinculo.conversation();
        Optional<String> entendido = textoEntendido(trabalho);
        switch (trabalho.result()) {
            case AUDIO_NAO_PROCESSAVEL -> {
                return List.of(apresentador.simples(chatId, "AUDIO_NAO_PROCESSAVEL"));
            }
            case FALHA_CONSOLIDACAO -> {
                return List.of(apresentador.simples(chatId, "FALHA_CONSOLIDACAO"));
            }
            default -> {
                // demais resultados têm relatório de aula
            }
        }
        if (trabalho.reportId() == null) {
            return List.of();
        }
        if (trabalho.reportType() == ReportType.SEMESTRAL) {
            return saidasDoSemestral(trabalho, chatId, conversa, entendido);
        }
        LessonReportView visao;
        try {
            visao = apresentacao.obter(trabalho.teacherId(), trabalho.reportId());
        } catch (DomainException naoEncontrado) {
            return List.of();
        }
        LessonReport relatorio = visao.report();
        int vigente = relatorio.currentVersion();
        switch (trabalho.result()) {
            case SEM_ALTERACAO_DE_CONTEUDO, PEDIDO_DE_ACAO_IGNORADO, FALHA_ALTERACAO -> {
                conversa.focus(new FocusReport(ReportType.AULA, relatorio.id()), vigente);
                String texto =
                        apresentador.texto(trabalho.result().name(), Map.of("versao", vigente))
                                + entendido
                                        .map(
                                                t ->
                                                        "\n"
                                                                + apresentador.texto(
                                                                        "TEXTO_ENTENDIDO",
                                                                        Map.of("texto", t)))
                                        .orElse("");
                return List.of(apresentacao.acoesConferencia(chatId, vigente, texto));
            }
            default -> {
                return new ArrayList<>(
                        apresentacao.apresentar(
                                chatId,
                                trabalho.teacherId(),
                                visao,
                                conversa,
                                prefixo(trabalho, vigente, entendido)));
            }
        }
    }

    private List<SaidaTelegram> saidasDoSemestral(
            ProcessingJob trabalho,
            long chatId,
            TelegramConversation conversa,
            Optional<String> entendido) {
        String prefixo =
                switch (trabalho.result()) {
                    case NOVA_VERSAO,
                            SEM_ALTERACAO_DE_CONTEUDO,
                            PEDIDO_DE_ACAO_IGNORADO,
                            FALHA_ALTERACAO -> {
                        int vigente =
                                apresentacaoSemestral
                                        .obter(trabalho.teacherId(), trabalho.reportId())
                                        .report()
                                        .currentVersion();
                        String codigo =
                                trabalho.result() == JobResult.NOVA_VERSAO
                                        ? "ALTERACAO_APLICADA"
                                        : trabalho.result().name();
                        yield apresentador.texto(codigo, Map.of("versao", vigente))
                                + entendido
                                        .map(
                                                t ->
                                                        "\n"
                                                                + apresentador.texto(
                                                                        "TEXTO_ENTENDIDO",
                                                                        Map.of("texto", t)))
                                        .orElse("");
                    }
                    case VERSAO_DESATUALIZADA ->
                            apresentador.texto(
                                    "VERSAO_DESATUALIZADA",
                                    Map.of(
                                            "versaoVigente",
                                            apresentacaoSemestral
                                                    .obter(
                                                            trabalho.teacherId(),
                                                            trabalho.reportId())
                                                    .report()
                                                    .currentVersion()));
                    default -> null;
                };
        try {
            return new ArrayList<>(
                    apresentacaoSemestral.apresentar(
                            chatId, trabalho.teacherId(), trabalho.reportId(), conversa, prefixo));
        } catch (DomainException naoEncontrado) {
            return List.of();
        }
    }

    private String prefixo(ProcessingJob trabalho, int vigente, Optional<String> entendido) {
        return switch (trabalho.result()) {
            case NOVA_VERSAO ->
                    apresentador.texto("ALTERACAO_APLICADA", Map.of("versao", vigente))
                            + entendido
                                    .map(
                                            t ->
                                                    "\n"
                                                            + apresentador.texto(
                                                                    "TEXTO_ENTENDIDO",
                                                                    Map.of("texto", t)))
                                    .orElse("");
            case VERSAO_DESATUALIZADA ->
                    ApresentadorTelegram.escapar(
                            apresentador.rotulo(
                                    ErrorCode.VERSAO_DESATUALIZADA.messageKey(),
                                    Map.of("versaoVigente", vigente)));
            case FALHA_RESPOSTA -> apresentador.texto("FALHA_RESPOSTA");
            case PRONTO_PARA_CONFERENCIA, PERGUNTAS_PENDENTES ->
                    trabalho.type() == JobType.RESPOSTA_PERGUNTAS && entendido.isPresent()
                            ? apresentador.texto(
                                    "RESPOSTA_INCORPORADA", Map.of("texto", entendido.get()))
                            : null;
            default -> null;
        };
    }

    private static Optional<String> textoEntendido(ProcessingJob trabalho) {
        return trabalho.parameter(ProcessingJob.UNDERSTOOD_TEXT)
                .or(() -> trabalho.parameter(ProcessingJob.TEXT))
                .filter(t -> !t.isBlank());
    }

    private static void protegido(String aviso, Runnable acao) {
        try {
            acao.run();
        } catch (RuntimeException erro) {
            LOG.warn(
                    "telegram-notificacao-falhou aviso={} tipo={}",
                    aviso,
                    erro.getClass().getSimpleName());
        }
    }
}
