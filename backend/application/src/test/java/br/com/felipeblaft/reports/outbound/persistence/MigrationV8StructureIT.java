package br.com.felipeblaft.reports.outbound.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.felipeblaft.reports.support.MigrationTestSupport;
import br.com.felipeblaft.reports.support.MigrationTestSupport.Catalog;
import java.io.IOException;
import java.io.InputStream;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Feature 003, R-03 (verificação da V8): a estrutura depois da V8 é a da V7. A única diferença
 * permitida são as definições dos objetos cujo predicado cita valor de enumeração (índices parciais
 * e CHECKs), que a V8 recria com os mesmos nomes e os literais novos. Cada definição esperada é a
 * da V7 com os literais trocados pelo mapa de {@code db/v8-values.json}.
 */
class MigrationV8StructureIT {

    private static final String SCHEMA = "migration_v8_structure_test";

    private final MigrationTestSupport db = new MigrationTestSupport(SCHEMA);

    @BeforeEach
    void clean() throws SQLException {
        db.dropSchema();
    }

    @AfterEach
    void cleanSchema() throws SQLException {
        db.dropSchema();
    }

    private static JsonNode values() throws IOException {
        try (InputStream in =
                MigrationV8StructureIT.class.getResourceAsStream("/db/v8-values.json")) {
            return JsonMapper.builder().build().readTree(in);
        }
    }

    private static String withNewLiterals(String definition, JsonNode pairs) {
        String result = definition;
        for (var pair : pairs.properties()) {
            result =
                    result.replace(
                            "'" + pair.getKey() + "'", "'" + pair.getValue().asString() + "'");
        }
        return result;
    }

    @Test
    void catalogAfterV8EqualsCatalogAfterV7ExceptTheDeclaredEnumerationLiterals() throws Exception {
        db.flyway("7").migrate();
        Catalog v7 = db.catalog();
        db.flyway("8").migrate();
        Catalog v8 = db.catalog();
        JsonNode values = values();

        assertThat(v8.columns()).isEqualTo(v7.columns());
        assertThat(v8.sequences()).isEqualTo(v7.sequences());
        assertThat(v8.constraints().keySet()).isEqualTo(v7.constraints().keySet());
        assertThat(v8.indexes().keySet()).isEqualTo(v7.indexes().keySet());

        Set<String> declared = new HashSet<>();
        values.get("objects").forEach(o -> declared.add(o.get("name").asString()));
        assertThat(declared).hasSize(15);

        int recreated = 0;
        for (Map.Entry<String, String> entry : v7.indexes().entrySet()) {
            String name = entry.getKey().substring(entry.getKey().indexOf('.') + 1);
            String expected =
                    declared.contains(name)
                            ? withNewLiterals(entry.getValue(), values.get("literalPairs"))
                            : entry.getValue();
            assertThat(v8.indexes().get(entry.getKey()))
                    .as("índice %s", entry.getKey())
                    .isEqualTo(expected);
            if (declared.contains(name)) {
                recreated++;
            }
        }
        for (Map.Entry<String, String> entry : v7.constraints().entrySet()) {
            String name = entry.getKey().substring(entry.getKey().indexOf('.') + 1);
            String expected =
                    declared.contains(name)
                            ? withNewLiterals(entry.getValue(), values.get("literalPairs"))
                            : entry.getValue();
            assertThat(v8.constraints().get(entry.getKey()))
                    .as("restrição %s", entry.getKey())
                    .isEqualTo(expected);
            if (declared.contains(name)) {
                recreated++;
            }
        }
        assertThat(recreated).isEqualTo(15);
    }

    @Test
    void noEnumerationLiteralOfTheOldVocabularySurvivesInAnyDefinition() throws Exception {
        db.flyway("8").migrate();
        Catalog v8 = db.catalog();
        JsonNode pairs = values().get("literalPairs");

        for (var definition : v8.indexes().entrySet()) {
            for (var pair : pairs.properties()) {
                assertThat(definition.getValue())
                        .as("índice %s", definition.getKey())
                        .doesNotContain("'" + pair.getKey() + "'");
            }
        }
        for (var definition : v8.constraints().entrySet()) {
            for (var pair : pairs.properties()) {
                assertThat(definition.getValue())
                        .as("restrição %s", definition.getKey())
                        .doesNotContain("'" + pair.getKey() + "'");
            }
        }
    }
}
