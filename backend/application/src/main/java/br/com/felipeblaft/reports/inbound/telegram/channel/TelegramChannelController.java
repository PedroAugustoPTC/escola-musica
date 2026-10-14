package br.com.felipeblaft.reports.inbound.telegram.channel;

import br.com.felipeblaft.reports.config.ApplicationProperties;
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
@RequestMapping("/interno/v1/canal/telegram")
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

    @PostMapping("/atualizacao")
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
        TelegramUpdateResult result = processor.processInternal(update);
        if (!result.repeated()) {
            return ResponseEntity.ok(registerActions(update, result.outputs(), false));
        }
        return ResponseEntity.ok(redeliver(update));
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
                    "canal-erro-mapeamento updateId={} tipo={}",
                    id,
                    error.getClass().getSimpleName());
            actions = mapper.map(processor.errorReply(update), chat, replyIds(id, true));
        }
        List<MappedAction> finalActions = actions;
        transaction.run(
                () -> {
                    save.run(
                            "u" + id,
                            ChannelActionOrigin.RESPOSTA_UPDATE,
                            id,
                            finalActions.stream().map(a -> a.toNewAction(null)).toList());
                    updateRecords.complete(id, finalActions.size());
                });
        return UpdateResponse.of(
                id, finalActions.stream().map(a -> (JsonNode) a.replyNode()).toList());
    }

    /**
     * Update repetido (FR-007): só as ações ainda não confirmadas, com os mesmos ids. Se o primeiro
     * processamento ainda está em curso, espera um pouco; se a linha ficou sem conclusão por muito
     * tempo, o processamento se perdeu e o professor recebe {@code ERRO_INTERNO}.
     */
    private UpdateResponse redeliver(UpdateTelegram update) {
        long id = update.updateId();
        Optional<UpdateStatus> status = updateRecords.status(id);
        if (status.isEmpty() || !status.get().internal()) {
            return UpdateResponse.of(id, List.of());
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
                    ? registerActions(update, processor.errorReply(update), true)
                    : UpdateResponse.of(id, List.of());
        }
        List<ChannelAction> pending = getUnconfirmed.run(id);
        return UpdateResponse.of(
                id,
                pending.stream()
                        .map(a -> (JsonNode) ChannelActionMapper.withActionId(a.id(), a.payload()))
                        .toList());
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

    @PostMapping("/confirmacoes")
    public ResponseEntity<Object> confirmations(@RequestBody(required = false) String body) {
        ConfirmationRequest request;
        try {
            request = body == null ? null : JSON.readValue(body, ConfirmationRequest.class);
        } catch (RuntimeException unreadable) {
            return invalid();
        }
        if (request == null || request.versao() == null) {
            return invalid();
        }
        if (!UpdateResponse.VERSION.equals(request.versao())) {
            return ResponseEntity.badRequest().body(ChannelErrorResponse.unsupportedVersion());
        }
        List<ConfirmationRequest.ActionResultDto> items = request.resultados();
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
                                                        item.acaoId(),
                                                        statusOf(item.situacao()),
                                                        item.codigoMotivo(),
                                                        item.mensagemId()))
                        .toList();
        ConfirmChannelActionsExecutionUseCase.Response response = confirm.run(results);
        countConfirmations(results);
        if (response.contractViolation()) {
            LOG.warn("canal-confirmacao-sem-mensagem-id-em-etapa");
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
                    result.status() == Status.EXECUTADA || result.reasonCode() == null
                            ? "NENHUM"
                            : ConfirmChannelActionsExecutionUseCase.normalize(result.reasonCode());
            metrics.counter(
                            "canal_confirmacoes_total",
                            "situacao",
                            result.status().name(),
                            "codigo",
                            code)
                    .increment();
        }
    }

    private static Status statusOf(String text) {
        if (text == null) {
            return null;
        }
        return switch (text) {
            case "EXECUTADA" -> Status.EXECUTADA;
            case "FALHOU" -> Status.FALHOU;
            default -> null;
        };
    }

    private static ResponseEntity<Object> invalid() {
        return ResponseEntity.badRequest().body(ChannelErrorResponse.invalidRequest());
    }

    // ------------------------------------------------------------------ GET /acoes/{acaoId}

    @GetMapping("/acoes/{acaoId}")
    public ResponseEntity<ActionStateResponse> actionState(@PathVariable String acaoId) {
        if (!ACTION_ID_FORMAT.matcher(acaoId).matches()) {
            return ResponseEntity.notFound().build();
        }
        return query.run(acaoId)
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

    @GetMapping("/documentos/{documentoId}")
    public ResponseEntity<byte[]> document(@PathVariable String documentoId) {
        // URL, caminho, tamanho ou alfabeto errado: 404 antes de tocar no banco (FR-023)
        if (!DOCUMENT_FORMAT.matcher(documentoId).matches()) {
            return ResponseEntity.notFound().build();
        }
        Optional<ObtainedDocument> document = getDocument.run(documentoId);
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
