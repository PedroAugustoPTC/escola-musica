package br.com.felipeblaft.relatorios.saida.ia;

import br.com.felipeblaft.relatorios.configuracao.PropriedadesAplicacao;
import br.com.felipeblaft.relatorios.porta.EntradaTarefa;
import br.com.felipeblaft.relatorios.porta.LlmPorta;
import br.com.felipeblaft.relatorios.porta.ProvedorIndisponivelExcecao;
import br.com.felipeblaft.relatorios.porta.RespostaLlm;
import br.com.felipeblaft.relatorios.porta.SaidaIaInvalidaExcecao;
import br.com.felipeblaft.relatorios.porta.TarefaLlm;
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
public class LlmOpenAiCompativelAdaptador implements LlmPorta {

    record DefinicaoTarefa(String prompt, String schema) {}

    static final Map<TarefaLlm, DefinicaoTarefa> TAREFAS = new EnumMap<>(TarefaLlm.class);

    static {
        TAREFAS.put(
                TarefaLlm.ESTRUTURACAO_AULA,
                new DefinicaoTarefa("estruturacao-aula.v1", "relatorio-aula"));
        TAREFAS.put(
                TarefaLlm.INCORPORACAO_RESPOSTAS,
                new DefinicaoTarefa("incorporacao-respostas.v1", "relatorio-aula"));
        TAREFAS.put(
                TarefaLlm.REVISAO_AULA, new DefinicaoTarefa("revisao-aula.v1", "relatorio-aula"));
        TAREFAS.put(
                TarefaLlm.CONSOLIDACAO_SEMESTRAL,
                new DefinicaoTarefa("consolidacao-semestral.v1", "relatorio-semestral"));
        TAREFAS.put(
                TarefaLlm.REVISAO_SEMESTRAL,
                new DefinicaoTarefa("revisao-semestral.v1", "relatorio-semestral"));
    }

    static final Duration TIMEOUT = Duration.ofSeconds(45);
    private static final BigDecimal MILHAO = BigDecimal.valueOf(1_000_000);
    private static final JsonMapper JSON =
            JsonMapper.builder().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();
    private static final SchemaRegistry REGISTRO =
            SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12);

    private final PropriedadesAplicacao.Llm configuracao;
    private final ClienteHttpIa http;
    private final Map<String, String> prompts = new ConcurrentHashMap<>();
    private final Map<String, Schema> schemas = new ConcurrentHashMap<>();

    public LlmOpenAiCompativelAdaptador(PropriedadesAplicacao.Llm configuracao) {
        this(configuracao, new ClienteHttpIa(Duration.ofSeconds(1)));
    }

    LlmOpenAiCompativelAdaptador(PropriedadesAplicacao.Llm configuracao, ClienteHttpIa http) {
        this.configuracao = Objects.requireNonNull(configuracao);
        this.http = http;
    }

    private record Chamada(String conteudo, int tokensEntrada, int tokensSaida) {}

    @Override
    public <T> RespostaLlm<T> gerarJson(
            TarefaLlm tarefa, EntradaTarefa entrada, Class<T> tipoSaida) {
        long inicio = System.nanoTime();
        DefinicaoTarefa definicao = TAREFAS.get(tarefa);
        List<Map<String, String>> mensagens = new ArrayList<>();
        mensagens.add(Map.of("role", "system", "content", prompt(definicao.prompt())));
        mensagens.add(Map.of("role", "user", "content", JSON.writeValueAsString(entrada)));

        Chamada primeira = chamar(mensagens);
        int tokensEntrada = primeira.tokensEntrada();
        int tokensSaida = primeira.tokensSaida();
        String conteudo = limpar(primeira.conteudo());
        List<String> erros = validar(definicao.schema(), conteudo);
        if (!erros.isEmpty()) {
            mensagens.add(Map.of("role", "assistant", "content", conteudo));
            mensagens.add(
                    Map.of(
                            "role",
                            "user",
                            "content",
                            prompt("correcao-json.v1") + "\n" + String.join("\n", erros)));
            Chamada correcao;
            try {
                correcao = chamar(mensagens);
            } catch (ProvedorIndisponivelExcecao fora) {
                // a primeira chamada já foi cobrada: o consumo segue na exceção (FR-056)
                throw new ProvedorIndisponivelExcecao(
                        fora.getMessage(),
                        tokensEntrada,
                        tokensSaida,
                        custo(tokensEntrada, tokensSaida));
            }
            tokensEntrada += correcao.tokensEntrada();
            tokensSaida += correcao.tokensSaida();
            conteudo = limpar(correcao.conteudo());
            if (!validar(definicao.schema(), conteudo).isEmpty()) {
                throw new SaidaIaInvalidaExcecao(
                        "saida-fora-do-schema",
                        tokensEntrada,
                        tokensSaida,
                        custo(tokensEntrada, tokensSaida));
            }
        }
        T saida;
        try {
            saida = JSON.readValue(conteudo, tipoSaida);
        } catch (JacksonException erro) {
            throw new SaidaIaInvalidaExcecao(
                    "saida-nao-desserializavel",
                    tokensEntrada,
                    tokensSaida,
                    custo(tokensEntrada, tokensSaida));
        }
        return new RespostaLlm<>(
                saida,
                definicao.prompt(),
                tokensEntrada,
                tokensSaida,
                custo(tokensEntrada, tokensSaida),
                ClienteHttpIa.provedorModelo(configuracao.url(), configuracao.modelo()),
                Duration.ofNanos(System.nanoTime() - inicio).toMillis());
    }

    private Chamada chamar(List<Map<String, String>> mensagens) {
        Map<String, Object> corpo = new LinkedHashMap<>();
        corpo.put("model", configuracao.modelo());
        corpo.put("temperature", 0.2);
        corpo.put("response_format", Map.of("type", "json_object"));
        corpo.put("messages", mensagens);
        String json = JSON.writeValueAsString(corpo);
        ClienteHttpIa.Resposta resposta =
                http.executar(
                        () ->
                                ClienteHttpIa.base(
                                                configuracao.url() + "/chat/completions",
                                                configuracao.chave(),
                                                TIMEOUT)
                                        .header("Content-Type", "application/json")
                                        .POST(
                                                HttpRequest.BodyPublishers.ofString(
                                                        json, StandardCharsets.UTF_8))
                                        .build());
        if (!resposta.sucesso()) {
            throw new ProvedorIndisponivelExcecao("status-" + resposta.status());
        }
        try {
            JsonNode no = JSON.readTree(resposta.corpo());
            String conteudo =
                    no.path("choices").path(0).path("message").path("content").asString("");
            JsonNode uso = no.path("usage");
            return new Chamada(
                    conteudo,
                    uso.path("prompt_tokens").asInt(0),
                    uso.path("completion_tokens").asInt(0));
        } catch (JacksonException erro) {
            throw new ProvedorIndisponivelExcecao("resposta-invalida");
        }
    }

    /** Remove cercas de código que alguns modelos acrescentam ao JSON. */
    static String limpar(String conteudo) {
        String texto = conteudo == null ? "" : conteudo.strip();
        if (texto.startsWith("```")) {
            int inicio = texto.indexOf('{');
            int fim = texto.lastIndexOf('}');
            if (inicio >= 0 && fim > inicio) {
                return texto.substring(inicio, fim + 1);
            }
        }
        return texto;
    }

    List<String> validar(String schema, String conteudo) {
        try {
            JSON.readTree(conteudo);
        } catch (JacksonException erro) {
            return List.of("O texto não é um JSON válido.");
        }
        return schema(schema).validate(conteudo, InputFormat.JSON).stream()
                .map(erro -> erro.toString())
                .toList();
    }

    private Schema schema(String nome) {
        return schemas.computeIfAbsent(
                nome,
                n -> {
                    try (InputStream entrada = recurso("ia/schemas/" + n + ".schema.json")) {
                        return REGISTRO.getSchema(entrada);
                    } catch (IOException erro) {
                        throw new UncheckedIOException(erro);
                    }
                });
    }

    String prompt(String nome) {
        return prompts.computeIfAbsent(
                nome,
                n -> {
                    try (InputStream entrada = recurso("prompts/" + n + ".txt")) {
                        return new String(entrada.readAllBytes(), StandardCharsets.UTF_8);
                    } catch (IOException erro) {
                        throw new UncheckedIOException(erro);
                    }
                });
    }

    private static InputStream recurso(String caminho) {
        return Objects.requireNonNull(
                LlmOpenAiCompativelAdaptador.class.getClassLoader().getResourceAsStream(caminho),
                "recurso ausente: " + caminho);
    }

    private BigDecimal custo(int entrada, int saida) {
        BigDecimal precoEntrada =
                Objects.requireNonNullElse(
                        configuracao.precoUsdPorMilhaoEntrada(), BigDecimal.ZERO);
        BigDecimal precoSaida =
                Objects.requireNonNullElse(configuracao.precoUsdPorMilhaoSaida(), BigDecimal.ZERO);
        return BigDecimal.valueOf(entrada)
                .multiply(precoEntrada)
                .add(BigDecimal.valueOf(saida).multiply(precoSaida))
                .divide(MILHAO, MathContext.DECIMAL64);
    }
}
