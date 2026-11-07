package br.com.felipeblaft.reports.outbound.ai;

import br.com.felipeblaft.reports.config.ApplicationProperties;
import br.com.felipeblaft.reports.domain.operations.PriceTable;
import br.com.felipeblaft.reports.domain.operations.PriceUnit;
import br.com.felipeblaft.reports.port.ClockPort;
import br.com.felipeblaft.reports.port.InvalidAiOutputException;
import br.com.felipeblaft.reports.port.LlmPort;
import br.com.felipeblaft.reports.port.ProviderUnavailableException;
import br.com.felipeblaft.reports.port.ResponseLlm;
import br.com.felipeblaft.reports.port.TaskInput;
import br.com.felipeblaft.reports.port.TaskLlm;
import com.networknt.schema.InputFormat;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.math.MathContext;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * LLM pela API {@code chat/completions} compatível com a da OpenAI (R-08, R-09): prompt versionado
 * por tarefa, modo JSON, temperatura 0,2, timeout de 45 s, validação contra o JSON Schema da tarefa
 * e uma chamada de correção. Campos desconhecidos são descartados (FR-020).
 */
public class LlmOpenAiCompatibleAdapter implements LlmPort {

    record TaskDefinition(String prompt, String schema) {}

    static final Map<TaskLlm, TaskDefinition> TASKS = new EnumMap<>(TaskLlm.class);

    static {
        TASKS.put(
                TaskLlm.LESSON_STRUCTURING,
                new TaskDefinition("lesson-structuring.v1", "lesson-report"));
        TASKS.put(
                TaskLlm.ANSWER_INCORPORATION,
                new TaskDefinition("answer-incorporation.v1", "lesson-report"));
        TASKS.put(
                TaskLlm.LESSON_REVISION, new TaskDefinition("lesson-revision.v1", "lesson-report"));
        TASKS.put(
                TaskLlm.SEMESTER_CONSOLIDATION,
                new TaskDefinition("semester-consolidation.v1", "semester-report"));
        TASKS.put(
                TaskLlm.SEMESTER_REVISION,
                new TaskDefinition("semester-revision.v1", "semester-report"));
    }

    static final Duration TIMEOUT = Duration.ofSeconds(45);
    private static final BigDecimal MILLION = BigDecimal.valueOf(1_000_000);
    private static final JsonMapper JSON =
            JsonMapper.builder().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();
    private static final SchemaRegistry REGISTRATION =
            SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12);

    private final ApplicationProperties.Llm config;
    private final AiHttpClient http;
    private final AiPricing pricing;
    private final Map<String, String> prompts = new ConcurrentHashMap<>();
    private final Map<String, Schema> schemas = new ConcurrentHashMap<>();

    public LlmOpenAiCompatibleAdapter(ApplicationProperties.Llm config) {
        this(config, new AiHttpClient(Duration.ofSeconds(1)));
    }

    /** Preço pela tabela com vigência (FR-052); o das propriedades vale como reserva. */
    public LlmOpenAiCompatibleAdapter(
            ApplicationProperties.Llm config, PriceTable prices, ClockPort clock) {
        this(
                config,
                new AiHttpClient(Duration.ofSeconds(1)),
                new AiPricing(prices, clock, String.valueOf(config.model())));
    }

    LlmOpenAiCompatibleAdapter(ApplicationProperties.Llm config, AiHttpClient http) {
        this(config, http, null);
    }

    LlmOpenAiCompatibleAdapter(
            ApplicationProperties.Llm config, AiHttpClient http, AiPricing pricing) {
        this.config = Objects.requireNonNull(config);
        this.http = http;
        this.pricing = pricing;
    }

    @Override
    public String providerName() {
        return AiHttpClient.modelProvider(config.url(), config.model());
    }

    private record Call(String content, int inputTokens, int outputTokens) {}

    @Override
    public <T> ResponseLlm<T> generateJson(TaskLlm task, TaskInput input, Class<T> outputType) {
        long start = System.nanoTime();
        TaskDefinition definition = TASKS.get(task);
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", prompt(definition.prompt())));
        messages.add(Map.of("role", "user", "content", JSON.writeValueAsString(input)));

        Call first = call(messages);
        int inputTokens = first.inputTokens();
        int outputTokens = first.outputTokens();
        String content = clean(first.content());
        List<String> errors = validate(definition.schema(), content);
        if (!errors.isEmpty()) {
            messages.add(Map.of("role", "assistant", "content", content));
            messages.add(
                    Map.of(
                            "role",
                            "user",
                            "content",
                            prompt("json-correction.v1") + "\n" + String.join("\n", errors)));
            Call correction;
            try {
                correction = call(messages);
            } catch (ProviderUnavailableException outside) {
                // a primeira chamada já foi cobrada: o consumo segue na exceção (FR-056)
                throw new ProviderUnavailableException(
                        outside.getMessage(),
                        inputTokens,
                        outputTokens,
                        cost(inputTokens, outputTokens));
            }
            inputTokens += correction.inputTokens();
            outputTokens += correction.outputTokens();
            content = clean(correction.content());
            if (!validate(definition.schema(), content).isEmpty()) {
                throw new InvalidAiOutputException(
                        "output-outside-schema",
                        inputTokens,
                        outputTokens,
                        cost(inputTokens, outputTokens));
            }
        }
        T output;
        try {
            output = JSON.readValue(content, outputType);
        } catch (JacksonException error) {
            throw new InvalidAiOutputException(
                    "non-deserializable-output",
                    inputTokens,
                    outputTokens,
                    cost(inputTokens, outputTokens));
        }
        return new ResponseLlm<>(
                output,
                definition.prompt(),
                inputTokens,
                outputTokens,
                cost(inputTokens, outputTokens),
                AiHttpClient.modelProvider(config.url(), config.model()),
                Duration.ofNanos(System.nanoTime() - start).toMillis());
    }

    private Call call(List<Map<String, String>> messages) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", config.model());
        body.put("temperature", 0.2);
        body.put("response_format", Map.of("type", "json_object"));
        body.put("messages", messages);
        String json = JSON.writeValueAsString(body);
        AiHttpClient.Response response =
                http.run(
                        () ->
                                AiHttpClient.base(
                                                config.url() + "/chat/completions",
                                                config.key(),
                                                TIMEOUT)
                                        .header("Content-Type", "application/json")
                                        .POST(
                                                HttpRequest.BodyPublishers.ofString(
                                                        json, StandardCharsets.UTF_8))
                                        .build());
        if (!response.success()) {
            throw new ProviderUnavailableException("status-" + response.status());
        }
        try {
            JsonNode no = JSON.readTree(response.body());
            String content =
                    no.path("choices").path(0).path("message").path("content").asString("");
            JsonNode use = no.path("usage");
            return new Call(
                    content,
                    use.path("prompt_tokens").asInt(0),
                    use.path("completion_tokens").asInt(0));
        } catch (JacksonException error) {
            throw new ProviderUnavailableException("invalid-answer");
        }
    }

    /** Remove cercas de código que alguns modelos acrescentam ao JSON. */
    static String clean(String content) {
        String text = content == null ? "" : content.strip();
        if (text.startsWith("```")) {
            int start = text.indexOf('{');
            int end = text.lastIndexOf('}');
            if (start >= 0 && end > start) {
                return text.substring(start, end + 1);
            }
        }
        return text;
    }

    List<String> validate(String schema, String content) {
        try {
            JSON.readTree(content);
        } catch (JacksonException error) {
            return List.of("O texto não é um JSON válido.");
        }
        return schema(schema).validate(content, InputFormat.JSON).stream()
                .map(error -> error.toString())
                .toList();
    }

    private Schema schema(String name) {
        return schemas.computeIfAbsent(
                name,
                n -> {
                    try (InputStream input = resource("ai/schemas/" + n + ".schema.json")) {
                        return REGISTRATION.getSchema(input);
                    } catch (IOException error) {
                        throw new UncheckedIOException(error);
                    }
                });
    }

    String prompt(String name) {
        return prompts.computeIfAbsent(
                name,
                n -> {
                    try (InputStream input = resource("prompts/" + n + ".txt")) {
                        return new String(input.readAllBytes(), StandardCharsets.UTF_8);
                    } catch (IOException error) {
                        throw new UncheckedIOException(error);
                    }
                });
    }

    private static InputStream resource(String path) {
        return Objects.requireNonNull(
                LlmOpenAiCompatibleAdapter.class.getClassLoader().getResourceAsStream(path),
                "recurso ausente: " + path);
    }

    private BigDecimal cost(int input, int output) {
        BigDecimal inputPrice =
                pricing == null
                        ? Objects.requireNonNullElse(
                                config.priceUsdPerMillionInput(), BigDecimal.ZERO)
                        : pricing.price(
                                PriceUnit.INPUT_MILLION_TOKENS, config.priceUsdPerMillionInput());
        BigDecimal outputPrice =
                pricing == null
                        ? Objects.requireNonNullElse(
                                config.priceUsdPerMillionOutput(), BigDecimal.ZERO)
                        : pricing.price(
                                PriceUnit.OUTPUT_MILLION_TOKENS, config.priceUsdPerMillionOutput());
        return BigDecimal.valueOf(input)
                .multiply(inputPrice)
                .add(BigDecimal.valueOf(output).multiply(outputPrice))
                .divide(MILLION, MathContext.DECIMAL64);
    }
}
