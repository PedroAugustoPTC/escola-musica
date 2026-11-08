package br.com.felipeblaft.reports.inbound.telegram.channel;

import br.com.felipeblaft.reports.config.ApplicationProperties;
import br.com.felipeblaft.reports.config.log.CorrelationMdc;
import br.com.felipeblaft.reports.domain.channel.ChannelAction;
import br.com.felipeblaft.reports.domain.channel.ChannelActionOrigin;
import br.com.felipeblaft.reports.inbound.internal.security.SecretComparator;
import br.com.felipeblaft.reports.inbound.telegram.TelegramOutput;
import br.com.felipeblaft.reports.inbound.telegram.TelegramUpdateProcessorService;
import br.com.felipeblaft.reports.inbound.telegram.TelegramUpdateRecords;
import br.com.felipeblaft.reports.inbound.telegram.TelegramUpdateRecords.UpdateStatus;
import br.com.felipeblaft.reports.inbound.telegram.TelegramUpdateResult;
import br.com.felipeblaft.reports.inbound.telegram.UpdateTelegram;
import br.com.felipeblaft.reports.inbound.telegram.channel.ChannelActionMapper.MappedAction;
import br.com.felipeblaft.reports.port.ClockPort;
import br.com.felipeblaft.reports.port.TransactionPort;
import br.com.felipeblaft.reports.usecase.channel.ConfirmChannelActionsExecutionUseCase;
import br.com.felipeblaft.reports.usecase.channel.ConfirmChannelActionsExecutionUseCase.Result;
import br.com.felipeblaft.reports.usecase.channel.ConfirmChannelActionsExecutionUseCase.Status;
import br.com.felipeblaft.reports.usecase.channel.GetChannelDocumentUseCase;
import br.com.felipeblaft.reports.usecase.channel.GetUnconfirmedActionsOfUpdateUseCase;
import br.com.felipeblaft.reports.usecase.channel.QueryChannelActionUseCase;
import br.com.felipeblaft.reports.usecase.channel.SaveChannelActionsUseCase;
import br.com.felipeblaft.reports.usecase.document.GetReportDocumentUseCase.ObtainedDocument;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.function.IntFunction;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * API interna do canal Telegram para o orquestrador (contracts/canal-telegram-interno.md). Só
 * adaptação de formato: recebe o update bruto, usa o <strong>mesmo</strong> interpretador do
 * webhook, devolve as ações v1 e recebe as confirmações. Não tem regra de conversa (FR-005) e
 * <strong>nunca</strong> envia nada ao Telegram (FR-010). Logs só com {@code updateId} e {@code
 * acaoId} (FR-029). {@code 401} e {@code 404} saem sem corpo.
 */
@RestController
@RequestMapping("/internal/v1/channel/telegram")
public class TelegramChannelController {

    public static final String SECRET_HEADER = "X-Telegram-Bot-Api-Secret-Token";

    private static final Logger LOG = LoggerFactory.getLogger(TelegramChannelController.class);
    private static final JsonMapper JSON =
            JsonMapper.builder().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();
    private static final Pattern DOCUMENT_FORMAT = Pattern.compile("^[A-Za-z0-9_-]{43}$");
    private static final Pattern ACTION_ID_FORMAT = Pattern.compile("^[A-Za-z0-9:_.-]{3,120}$");
    private static final int MAX_RESULTS = 100;
    private static final Duration PROCESSING_WAIT = Duration.ofSeconds(3);
    private static final long WAIT_INTERVAL_MS = 100;
    private static final Duration LOST_ROW_AGE = Duration.ofSeconds(30);

    private final String webhookSecret;
    private final SecretComparator comparator = new SecretComparator();
    private final TelegramUpdateProcessorService processor;
    private final TelegramUpdateRecords updateRecords;
    private final ChannelActionMapper mapper;
    private final SaveChannelActionsUseCase save;
    private final GetUnconfirmedActionsOfUpdateUseCase getUnconfirmed;
    private final ConfirmChannelActionsExecutionUseCase confirm;
    private final QueryChannelActionUseCase query;
    private final GetChannelDocumentUseCase getDocument;
    private final TransactionPort transaction;
    private final ClockPort clock;
    private final MeterRegistry metrics;

    @SuppressWarnings("java:S107")
    public TelegramChannelController(
            ApplicationProperties properties,
            TelegramUpdateProcessorService processor,
            TelegramUpdateRecords updateRecords,
            ChannelActionMapper mapper,
            SaveChannelActionsUseCase save,
            GetUnconfirmedActionsOfUpdateUseCase getUnconfirmed,
            ConfirmChannelActionsExecutionUseCase confirm,
            QueryChannelActionUseCase query,
            GetChannelDocumentUseCase getDocument,
            TransactionPort transaction,
            ClockPort clock,
            MeterRegistry metrics) {
        this.webhookSecret = properties.telegram().webhookSecret();
        this.processor = processor;
        this.updateRecords = updateRecords;
        this.mapper = mapper;
        this.save = save;
        this.getUnconfirmed = getUnconfirmed;
        this.confirm = confirm;
        this.query = query;
        this.getDocument = getDocument;
        this.transaction = transaction;
        this.clock = clock;
        this.metrics = metrics;
    }

    // ------------------------------------------------------------------ POST /atualizacao

    @PostMapping("/update")
    public ResponseEntity<UpdateResponse> update(
            @RequestHeader(name = SECRET_HEADER, required = false) String receivedSecret,
            @RequestBody(required = false) String body) {
        if (!comparator.equal(receivedSecret, webhookSecret)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        Optional<UpdateTelegram> read = processor.read(body);
        if (read.isEmpty()) {
            return ResponseEntity.ok(UpdateResponse.empty());
        }
        UpdateTelegram update = read.get();
        // o update_id é a correlação da autoria no Telegram: vai a toda linha de log deste pedido
        // (FR-050)
        try (CorrelationMdc scope =
                CorrelationMdc.open(Long.toString(update.updateId()), "TELEGRAM")) {
            TelegramUpdateResult result = processor.processInternal(update);
            if (!result.repeated()) {
                return ResponseEntity.ok(registerActions(update, result.outputs(), false));
            }
            return redeliver(update);
        }
    }

    /** Mapeia, grava na outbox e marca o update como concluído, tudo na mesma transação. */
    private UpdateResponse registerActions(
            UpdateTelegram update, List<TelegramOutput> outputs, boolean lost) {
        long id = update.updateId();
        long chat = chatOf(update);
        List<MappedAction> actions;
        try {
            actions = mapper.map(outputs, chat, replyIds(id, lost));
        } catch (RuntimeException error) {
            // toda mensagem recebida traz ao menos uma ação, mesmo se o mapeamento falhar (FR-039)
            LOG.error(
                    "channel-mapping-error updateId={} type={}",
                    id,
                    error.getClass().getSimpleName());
            actions = mapper.map(processor.errorReply(update), chat, replyIds(id, true));
        }
        List<MappedAction> finalActions = actions;
        transaction.run(
                () -> {
                    save.run(
                            "u" + id,
                            ChannelActionOrigin.UPDATE_REPLY,
                            id,
                            finalActions.stream().map(a -> a.toNewAction(null)).toList());
                    updateRecords.complete(id, finalActions.size());
                });
        return UpdateResponse.of(
                id, finalActions.stream().map(a -> (JsonNode) a.replyNode()).toList());
    }

    /**
     * Update repetido (FR-007): só as ações ainda não confirmadas, com os mesmos ids. Se o primeiro
     * processamento ainda está em curso, espera um pouco; se a linha ainda não foi concluída e é
     * recente, responde {@code 503} (o transporte tenta de novo; devolver lista vazia faria o
     * Telegram dar o update por entregue se o primeiro processamento tivesse morrido com o
     * backend); se a linha ficou sem conclusão por muito tempo, o processamento se perdeu e o
     * professor recebe {@code ERRO_INTERNO}.
     */
    private ResponseEntity<UpdateResponse> redeliver(UpdateTelegram update) {
        long id = update.updateId();
        Optional<UpdateStatus> status = updateRecords.status(id);
        if (status.isEmpty() || !status.get().internal()) {
            return ResponseEntity.ok(UpdateResponse.of(id, List.of()));
        }
        UpdateStatus current = status.get();
        long limit = System.nanoTime() + PROCESSING_WAIT.toNanos();
        while (current.totalActions() == null && System.nanoTime() < limit) {
            if (!sleep()) {
                break;
            }
            Optional<UpdateStatus> created = updateRecords.status(id);
            if (created.isEmpty()) {
                break;
            }
            current = created.get();
        }
        if (current.totalActions() == null) {
            boolean lost =
                    Duration.between(current.receivedAt(), clock.now()).compareTo(LOST_ROW_AGE) > 0;
            return lost
                    ? ResponseEntity.ok(registerActions(update, processor.errorReply(update), true))
                    : ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }
        List<ChannelAction> pending = getUnconfirmed.run(id);
        return ResponseEntity.ok(
                UpdateResponse.of(
                        id,
                        pending.stream()
                                .map(
                                        a ->
                                                (JsonNode)
                                                        ChannelActionMapper.withActionId(
                                                                a.id(), a.payload()))
                                .toList()));
    }

    private static boolean sleep() {
        try {
            Thread.sleep(WAIT_INTERVAL_MS);
            return true;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private static IntFunction<String> replyIds(long updateId, boolean lost) {
        return lost ? pos -> "u" + updateId + "-e" + pos : pos -> "u" + updateId + "-" + pos;
    }

    private static long chatOf(UpdateTelegram update) {
        if (update.message() != null && update.message().chat() != null) {
            return update.message().chat().id();
        }
        if (update.callbackQuery() != null) {
            if (update.callbackQuery().message() != null
                    && update.callbackQuery().message().chat() != null) {
                return update.callbackQuery().message().chat().id();
            }
            if (update.callbackQuery().from() != null) {
                return update.callbackQuery().from().id();
            }
        }
        return 0L;
    }

    // ------------------------------------------------------------------ POST /confirmacoes

    @PostMapping("/confirmations")
    public ResponseEntity<Object> confirmations(@RequestBody(required = false) String body) {
        ConfirmationRequest request;
        try {
            request = body == null ? null : JSON.readValue(body, ConfirmationRequest.class);
        } catch (RuntimeException unreadable) {
            return invalid();
        }
        if (request == null || request.version() == null) {
            return invalid();
        }
        if (!UpdateResponse.VERSION.equals(request.version())) {
            return ResponseEntity.badRequest().body(ChannelErrorResponse.unsupportedVersion());
        }
        List<ConfirmationRequest.ActionResultDto> items = request.results();
        if (items == null || items.isEmpty() || items.size() > MAX_RESULTS) {
            return invalid();
        }
        List<Result> results =
                items.stream()
                        .map(
                                item ->
                                        item == null
                                                ? new Result(null, null, null, null)
                                                : new Result(
                                                        item.actionId(),
                                                        statusOf(item.outcome()),
                                                        item.reasonCode(),
                                                        item.messageId()))
                        .toList();
        ConfirmChannelActionsExecutionUseCase.Response response = confirm.run(results);
        countConfirmations(results);
        if (response.contractViolation()) {
            LOG.warn("channel-confirmation-without-message-id-in-step");
            return invalid();
        }
        return ResponseEntity.ok(
                new ConfirmationResponse(
                        UpdateResponse.VERSION, response.accepted(), response.ignored()));
    }

    /**
     * Métrica sem dado de aluno: só a situação e o código (valor desconhecido vira ERRO_TELEGRAM).
     */
    private void countConfirmations(List<Result> results) {
        for (Result result : results) {
            if (result.status() == null) {
                continue;
            }
            String code =
                    result.status() == Status.EXECUTED || result.reasonCode() == null
                            ? "NONE"
                            : ConfirmChannelActionsExecutionUseCase.normalize(result.reasonCode());
            metrics.counter(
                            "channel_confirmations_total",
                            "outcome",
                            result.status().name(),
                            "code",
                            code)
                    .increment();
        }
    }

    private static Status statusOf(String text) {
        if (text == null) {
            return null;
        }
        return switch (text) {
            case "EXECUTED" -> Status.EXECUTED;
            case "FAILED" -> Status.FAILED;
            default -> null;
        };
    }

    private static ResponseEntity<Object> invalid() {
        return ResponseEntity.badRequest().body(ChannelErrorResponse.invalidRequest());
    }

    // ------------------------------------------------------------------ GET /acoes/{acaoId}

    @GetMapping("/actions/{actionId}")
    public ResponseEntity<ActionStateResponse> actionState(@PathVariable String actionId) {
        if (!ACTION_ID_FORMAT.matcher(actionId).matches()) {
            return ResponseEntity.notFound().build();
        }
        return query.run(actionId)
                .map(
                        action ->
                                ResponseEntity.ok(
                                        new ActionStateResponse(
                                                UpdateResponse.VERSION,
                                                action.id(),
                                                action.state().name(),
                                                action.reasonCode())))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    // ------------------------------------------------------------------ GET /documentos/{id}

    @GetMapping("/documents/{documentId}")
    public ResponseEntity<byte[]> document(@PathVariable String documentId) {
        // URL, caminho, tamanho ou alfabeto errado: 404 antes de tocar no banco (FR-023)
        if (!DOCUMENT_FORMAT.matcher(documentId).matches()) {
            return ResponseEntity.notFound().build();
        }
        Optional<ObtainedDocument> document = getDocument.run(documentId);
        if (document.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .cacheControl(CacheControl.noStore())
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline()
                                .filename(document.get().fileName())
                                .build()
                                .toString())
                .body(document.get().content());
    }
}
