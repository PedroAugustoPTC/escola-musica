package br.com.felipeblaft.reports.outbound.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.felipeblaft.reports.architecture.GlossaryFile;
import br.com.felipeblaft.reports.support.MigrationTestSupport;
import br.com.felipeblaft.reports.support.MigrationTestSupport.Catalog;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/**
 * Feature 003, R-03 (verificação 1 e 5), FR-014: a V7 só muda NOMES. Captura o catálogo na V6,
 * migra para a V7 e exige que o catálogo novo seja idêntico ao antigo com o mapa de renomeação
 * aplicado (tipos, nulabilidade, padrões, definição dos CHECKs e dos índices, restrições e
 * sequência); o mapa cobre toda tabela e coluna da V6; e o esquema final não tem nome em português.
 */
class MigrationV7StructureIT {

    private static final String SCHEMA = "migration_v7_structure_test";

    private final MigrationTestSupport db = new MigrationTestSupport(SCHEMA);

    @BeforeEach
    @AfterEach
    void cleanSchema() throws SQLException {
        db.dropSchema();
    }

    @Test
    void catalogAfterV7EqualsCatalogAfterV6WithTheRenameMapApplied() throws Exception {
        db.flyway("6").migrate();
        Catalog v6 = db.catalog();
        db.flyway("7").migrate();
        Catalog v7 = db.catalog();

        Catalog expected = MigrationTestSupport.renamed(v6, MigrationTestSupport.renameMap());

        assertThat(v7.columns()).isEqualTo(expected.columns());
        assertThat(v7.constraints()).isEqualTo(expected.constraints());
        assertThat(v7.indexes()).isEqualTo(expected.indexes());
        assertThat(v7.sequences()).isEqualTo(expected.sequences());
        assertThat(v7.columns()).hasSize(17);
        assertThat(v7.constraints()).hasSize(v6.constraints().size());
        assertThat(v7.indexes()).hasSize(v6.indexes().size());
    }

    @Test
    void renameMapCoversEveryTableColumnConstraintIndexAndSequenceOfV6() throws Exception {
        db.flyway("6").migrate();
        Catalog v6 = db.catalog();
        JsonNode map = MigrationTestSupport.renameMap();

        for (var table : v6.columns().entrySet()) {
            assertThat(map.get("tables").has(table.getKey()))
                    .as("tabela %s no mapa", table.getKey())
                    .isTrue();
            for (String column : table.getValue()) {
                String name = column.substring(0, column.indexOf('|'));
                assertThat(map.get("columns").get(table.getKey()).has(name))
                        .as("coluna %s.%s no mapa", table.getKey(), name)
                        .isTrue();
            }
        }
        // nada a mais no mapa do que existe na V6
        assertThat(map.get("tables").size()).isEqualTo(v6.columns().size());
        for (var table : map.get("columns").properties()) {
            assertThat(v6.columns()).containsKey(table.getKey());
            assertThat(table.getValue().size()).isEqualTo(v6.columns().get(table.getKey()).size());
        }
        assertThat(map.get("sequences").size()).isEqualTo(v6.sequences().size());
        for (String key : v6.constraints().keySet()) {
            String name = key.substring(key.indexOf('.') + 1);
            assertThat(map.get("constraints").has(name)).as("restrição %s no mapa", key).isTrue();
        }
    }

    @Test
    void finalSchemaHasNoPortugueseNames() throws Exception {
        db.flyway("7").migrate();
        Catalog v7 = db.catalog();
        GlossaryFile glossary = GlossaryFile.load();

        List<String> portuguese = new ArrayList<>();
        TreeSet<String> names = new TreeSet<>();
        v7.columns()
                .forEach(
                        (table, columns) -> {
                            names.add(table);
                            columns.forEach(
                                    column -> names.add(column.substring(0, column.indexOf('|'))));
                        });
        v7.constraints().keySet().forEach(key -> names.add(key.substring(key.indexOf('.') + 1)));
        v7.indexes().keySet().forEach(key -> names.add(key.substring(key.indexOf('.') + 1)));
        names.addAll(v7.sequences().keySet());
        for (String name : names) {
            if (!glossary.examine(name, "db:" + name, GlossaryFile.Kind.IDENTIFIER).isEmpty()) {
                portuguese.add(name);
            }
        }

        assertThat(portuguese).as("nomes do esquema ainda em português").isEmpty();
    }

    @Test
    void flywayHistoryKeepsTheChecksumsOfV1ToV6() throws Exception {
        db.flyway("6").migrate();
        List<String> before =
                db.column(
                        "select version || ':' || checksum from flyway_schema_history"
                                + " where version is not null order by installed_rank");
        db.flyway("7").migrate();
        List<String> after =
                db.column(
                        "select version || ':' || checksum from flyway_schema_history"
                                + " where version is not null order by installed_rank");

        assertThat(before).hasSize(6);
        assertThat(after).hasSize(7);
        assertThat(after.subList(0, 6)).isEqualTo(before);
        assertThat(after.getLast()).startsWith("7:");
    }
}
