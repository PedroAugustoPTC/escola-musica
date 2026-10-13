package br.com.felipeblaft.reports.suporte;

import com.networknt.schema.InputFormat;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Valida corpos JSON contra {@code contracts/acoes-v1.schema.json} da feature 002 (fonte normativa
 * do formato). Cada {@code $defs/<Nome>} é validado de forma independente: o esquema do teste é um
 * embrulho que aponta para a definição e carrega todas as outras, de modo que as referências
 * internas resolvem sem acesso à rede.
 */
public final class EsquemaAcoesV1 {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final SchemaRegistry REGISTRO =
            SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12);
    private static final Map<String, Schema> CACHE = new ConcurrentHashMap<>();

    private EsquemaAcoesV1() {}

    /** Caminho de um arquivo de {@code specs/002-api-interna-canal-telegram/contracts}. */
    public static Path contrato(String arquivo) {
        String portal = System.getenv("CONTRATO_API");
        if (portal != null && Files.exists(Path.of(portal))) {
            // .../specs/001-fluxo-relatorios/contracts/api-portal.openapi.yaml -> .../specs
            Path specs = Path.of(portal).toAbsolutePath().getParent().getParent().getParent();
            Path candidato =
                    specs.resolve("002-api-interna-canal-telegram")
                            .resolve("contracts")
                            .resolve(arquivo);
            if (Files.exists(candidato)) {
                return candidato;
            }
        }
        return Path.of("../../specs/002-api-interna-canal-telegram/contracts", arquivo)
                .toAbsolutePath()
                .normalize();
    }

    /** Mensagens de violação do JSON contra {@code #/$defs/<definicao>}; vazio se válido. */
    public static List<String> validar(String definicao, String json) {
        return schema(definicao).validate(json, InputFormat.JSON).stream()
                .map(Object::toString)
                .toList();
    }

    /** Falha o teste, com as violações, se o corpo não segue a definição. */
    public static void exigir(String definicao, String json) {
        List<String> violacoes = validar(definicao, json);
        if (!violacoes.isEmpty()) {
            throw new AssertionError(
                    "corpo fora de #/$defs/" + definicao + ": " + violacoes + "\n" + json);
        }
    }

    public static void exigir(String definicao, JsonNode json) {
        exigir(definicao, json.toString());
    }

    private static Schema schema(String definicao) {
        return CACHE.computeIfAbsent(
                definicao,
                nome -> {
                    try {
                        JsonNode contrato =
                                JSON.readTree(
                                        Files.readString(
                                                contrato("acoes-v1.schema.json"),
                                                StandardCharsets.UTF_8));
                        ObjectNode embrulho = JSON.createObjectNode();
                        embrulho.put("$schema", "https://json-schema.org/draft/2020-12/schema");
                        embrulho.put("$ref", "#/$defs/" + nome);
                        embrulho.set("$defs", contrato.get("$defs"));
                        return REGISTRO.getSchema(
                                new ByteArrayInputStream(
                                        embrulho.toString().getBytes(StandardCharsets.UTF_8)));
                    } catch (IOException erro) {
                        throw new UncheckedIOException(erro);
                    }
                });
    }
}
