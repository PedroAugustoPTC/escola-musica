package br.com.felipeblaft.reports.support;

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
public final class ActionsSchemaV1 {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final SchemaRegistry REGISTRATION =
            SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12);
    private static final Map<String, Schema> CACHE = new ConcurrentHashMap<>();

    private ActionsSchemaV1() {}

    /** Caminho de um arquivo de {@code specs/002-api-interna-canal-telegram/contracts}. */
    public static Path contract(String file) {
        String portal = System.getenv("CONTRACT_API");
        if (portal != null && Files.exists(Path.of(portal))) {
            // .../specs/001-fluxo-relatorios/contracts/api-portal.openapi.yaml -> .../specs
            Path specs = Path.of(portal).toAbsolutePath().getParent().getParent().getParent();
            Path candidate =
                    specs.resolve("002-api-interna-canal-telegram")
                            .resolve("contracts")
                            .resolve(file);
            if (Files.exists(candidate)) {
                return candidate;
            }
        }
        return Path.of("../../specs/002-api-interna-canal-telegram/contracts", file)
                .toAbsolutePath()
                .normalize();
    }

    /** Mensagens de violação do JSON contra {@code #/$defs/<definicao>}; vazio se válido. */
    public static List<String> validate(String definition, String json) {
        return schema(definition).validate(json, InputFormat.JSON).stream()
                .map(Object::toString)
                .toList();
    }

    /** Falha o teste, com as violações, se o corpo não segue a definição. */
    public static void require(String definition, String json) {
        List<String> violations = validate(definition, json);
        if (!violations.isEmpty()) {
            throw new AssertionError(
                    "corpo fora de #/$defs/" + definition + ": " + violations + "\n" + json);
        }
    }

    public static void require(String definition, JsonNode json) {
        require(definition, json.toString());
    }

    private static Schema schema(String definition) {
        return CACHE.computeIfAbsent(
                definition,
                name -> {
                    try {
                        JsonNode contract =
                                JSON.readTree(
                                        Files.readString(
                                                contract("actions-v1.schema.json"),
                                                StandardCharsets.UTF_8));
                        ObjectNode wrapper = JSON.createObjectNode();
                        wrapper.put("$schema", "https://json-schema.org/draft/2020-12/schema");
                        wrapper.put("$ref", "#/$defs/" + name);
                        wrapper.set("$defs", contract.get("$defs"));
                        return REGISTRATION.getSchema(
                                new ByteArrayInputStream(
                                        wrapper.toString().getBytes(StandardCharsets.UTF_8)));
                    } catch (IOException error) {
                        throw new UncheckedIOException(error);
                    }
                });
    }
}
