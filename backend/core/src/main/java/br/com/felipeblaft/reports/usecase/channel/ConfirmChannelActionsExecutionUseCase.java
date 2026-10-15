package br.com.felipeblaft.reports.usecase.channel;

import br.com.felipeblaft.reports.domain.channel.ChannelAction;
import br.com.felipeblaft.reports.domain.channel.ChannelActionType;
import br.com.felipeblaft.reports.domain.channel.ConfirmationResult;
import br.com.felipeblaft.reports.domain.channel.RetryPolicy;
import br.com.felipeblaft.reports.port.ChannelActionRepository;
import br.com.felipeblaft.reports.port.ClockPort;
import br.com.felipeblaft.reports.port.JobQueuePort;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Recebe o resultado da execução de cada ação pelo orquestrador (FR-007b, FR-017). Idempotente;
 * cada resultado é validado isoladamente; o backend decide a compensação quando uma edição falha
 * por mensagem não editável ou um documento fica indisponível. O orquestrador só classifica o erro:
 * o que fazer depois é daqui.
 */
public class ConfirmChannelActionsExecutionUseCase {

    public static final String MESSAGE_NOT_EDITABLE = ChannelAction.MESSAGE_NOT_EDITABLE_CODE;
    public static final String DOCUMENT_UNAVAILABLE = ChannelAction.DOCUMENT_UNAVAILABLE_CODE;
    public static final String TELEGRAM_ERROR = "TELEGRAM_ERROR";

    private static final Set<String> KNOWN_CODES =
            Set.of(
                    MESSAGE_NOT_EDITABLE,
                    "CHAT_UNAVAILABLE",
                    "ACTION_NOT_SUPPORTED",
                    DOCUMENT_UNAVAILABLE,
                    "RATE_LIMITED",
                    TELEGRAM_ERROR);
    private static final Pattern CODE_FORMAT = Pattern.compile("^[A-Z][A-Z0-9_]{1,39}$");
    private static final Pattern ACTION_ID_FORMAT = Pattern.compile("^[A-Za-z0-9:_.-]{3,120}$");
    private static final int SAVE_ATTEMPTS = 3;

    public enum Status {
        EXECUTED,
        FAILED
    }

    public record Result(String actionId, Status status, String reasonCode, Long messageId) {}

    /**
     * @param aceitas confirmações aplicadas ou idênticas às já registradas
     * @param ignoradas ids desconhecidos, vencidos, divergentes ou com resultado inválido
     * @param violacaoDeContrato o orquestrador omitiu o {@code mensagemId} obrigatório de uma etapa
     *     executada (os demais resultados foram aplicados normalmente)
     */
    public record Response(int accepted, List<String> ignored, boolean contractViolation) {}

    private final ChannelActionRepository actions;
    private final JobQueuePort queue;
    private final ClockPort clock;
    private final RetryPolicy policy;

    public ConfirmChannelActionsExecutionUseCase(
            ChannelActionRepository actions,
            JobQueuePort queue,
            ClockPort clock,
            RetryPolicy policy) {
        this.actions = Objects.requireNonNull(actions);
        this.queue = Objects.requireNonNull(queue);
        this.clock = Objects.requireNonNull(clock);
        this.policy = Objects.requireNonNull(policy);
    }

    public Response run(List<Result> results) {
        int accepted = 0;
        boolean violation = false;
        List<String> ignored = new ArrayList<>();
        for (Result result : results) {
            Outcome outcome = confirm(result);
            switch (outcome) {
                case ACCEPTS -> accepted++;
                case IGNORED -> registerIgnored(ignored, result);
                case VIOLATION -> {
                    violation = true;
                    registerIgnored(ignored, result);
                }
            }
        }
        return new Response(accepted, ignored, violation);
    }

    private enum Outcome {
        ACCEPTS,
        IGNORED,
        VIOLATION
    }

    private Outcome confirm(Result result) {
        if (result == null
                || result.actionId() == null
                || result.actionId().isBlank()
                || result.status() == null
                || (result.status() == Status.FAILED
                        && (result.reasonCode() == null || result.reasonCode().isBlank()))) {
            return Outcome.IGNORED;
        }
        for (int attempt = 0; attempt < SAVE_ATTEMPTS; attempt++) {
            Optional<ChannelAction> found = actions.searchById(result.actionId());
            if (found.isEmpty()) {
                return Outcome.IGNORED;
            }
            ChannelAction action = found.get();
            int readVersion = action.version();
            Instant now = clock.now();
            if (result.status() == Status.EXECUTED) {
                Outcome outcome = executed(action, result, readVersion, now);
                if (outcome != null) {
                    return outcome;
                }
            } else {
                Outcome outcome = failed(action, result, readVersion, now);
                if (outcome != null) {
                    return outcome;
                }
            }
        }
        return Outcome.IGNORED;
    }

    /**
     * @return {@code null} se a gravação perdeu a disputa e deve ser refeita
     */
    private Outcome executed(ChannelAction action, Result result, int readVersion, Instant now) {
        boolean stepWithJob =
                action.type() == ChannelActionType.STEP_MESSAGE && action.jobId() != null;
        if (stepWithJob && (result.messageId() == null || result.messageId() < 1)) {
            return Outcome.VIOLATION;
        }
        ConfirmationResult effect = action.confirmExecuted(now);
        if (effect == ConfirmationResult.DIVERGENT) {
            return Outcome.IGNORED;
        }
        if (effect == ConfirmationResult.REPEATED) {
            return Outcome.ACCEPTS;
        }
        if (!actions.updateIfVersion(action, readVersion)) {
            return null;
        }
        if (stepWithJob) {
            // grava o id da mensagem de etapa no trabalho; as próximas etapas editam a mesma
            // mensagem
            queue.registerStepMessage(action.jobId(), result.messageId());
        }
        return Outcome.ACCEPTS;
    }

    /**
     * @return {@code null} se a gravação perdeu a disputa e deve ser refeita
     */
    private Outcome failed(ChannelAction action, Result result, int readVersion, Instant now) {
        String code = normalize(result.reasonCode());
        ConfirmationResult effect = action.confirmFailure(code, now);
        if (effect == ConfirmationResult.DIVERGENT) {
            return Outcome.IGNORED;
        }
        if (effect == ConfirmationResult.REPEATED) {
            return Outcome.ACCEPTS;
        }
        if (!actions.updateIfVersion(action, readVersion)) {
            return null;
        }
        if (action.compensatesBy(code)) {
            // uma só compensação por ação (id <acaoId>-c); uma compensação que falha não gera outra
            actions.saveBatch(List.of(action.createCompensation(now, policy)));
        }
        return Outcome.ACCEPTS;
    }

    /**
     * Código novo do orquestrador (ou texto livre) nunca é gravado como veio: vira {@code
     * ERRO_TELEGRAM}.
     */
    public static String normalize(String code) {
        String clean = code.trim();
        return KNOWN_CODES.contains(clean) && CODE_FORMAT.matcher(clean).matches()
                ? clean
                : TELEGRAM_ERROR;
    }

    /** Só ecoa ids com formato de {@code acaoId}: nada de texto livre de volta ao orquestrador. */
    private static void registerIgnored(List<String> ignored, Result result) {
        if (result != null
                && result.actionId() != null
                && ACTION_ID_FORMAT.matcher(result.actionId()).matches()) {
            ignored.add(result.actionId());
        }
    }
}
