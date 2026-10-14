package br.com.felipeblaft.reports.support;

import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Apoio dos testes de migração da V7 (feature 003, R-03): aplica o Flyway em um esquema próprio do
 * postgres-test (até a V6, depois até a V7), captura a assinatura do catálogo (tabelas, colunas com
 * tipo, nulabilidade e padrão, restrições, índices e sequências) e aplica a ela o mapa de
 * renomeação de {@code db/rename-map.json}. Sem Spring e sem tocar o código de produção.
 */
public final class MigrationTestSupport {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final Pattern WORD = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    private final String schema;
    private final String url =
            System.getenv()
                    .getOrDefault(
                            "DATABASE_URL", "jdbc:postgresql://localhost:5432/relatorios_teste");
    private final String user = System.getenv().getOrDefault("DATABASE_USER", "relatorios");
    private final String password = System.getenv().getOrDefault("DATABASE_PASSWORD", "teste");

    public MigrationTestSupport(String schema) {
        this.schema = schema;
    }

    public String schema() {
        return schema;
    }

    /**
     * Assinatura do catálogo: cada mapa tem uma linha legível por objeto, ordenada, para diferença
     * clara.
     */
    public record Catalog(
            Map<String, List<String>> columns,
            Map<String, String> constraints,
            Map<String, String> indexes,
            Map<String, String> sequences) {}

    // ---- Flyway e conexão
    // ------------------------------------------------------------------------------------

    /** Flyway no esquema próprio; {@code target} nulo migra até a última versão. */
    public Flyway flyway(String target) {
        var configuration =
                Flyway.configure()
                        .dataSource(url, user, password)
                        .schemas(schema)
                        .defaultSchema(schema)
                        .locations("classpath:db/migration");
        configuration.target(
                target == null ? MigrationVersion.LATEST : MigrationVersion.fromVersion(target));
        return configuration.load();
    }

    public Connection connection() throws SQLException {
        Connection connection = DriverManager.getConnection(url, user, password);
        connection.setSchema(schema);
        return connection;
    }

    public void execute(String sql) throws SQLException {
        try (Connection connection = connection();
                Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    public int update(String sql) throws SQLException {
        try (Connection connection = connection();
                Statement statement = connection.createStatement()) {
            return statement.executeUpdate(sql);
        }
    }

    public List<String> column(String sql) throws SQLException {
        List<String> values = new ArrayList<>();
        try (Connection connection = connection();
                Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery(sql)) {
            while (rows.next()) {
                values.add(rows.getString(1));
            }
        }
        return values;
    }

    public String single(String sql) throws SQLException {
        List<String> values = column(sql);
        return values.isEmpty() ? null : values.getFirst();
    }

    public void dropSchema() throws SQLException {
        try (Connection connection = DriverManager.getConnection(url, user, password);
                Statement statement = connection.createStatement()) {
            statement.execute("drop schema if exists " + schema + " cascade");
        }
    }

    // ---- catálogo
    // ---------------------------------------------------------------------------------------------

    public Catalog catalog() throws SQLException {
        Map<String, List<String>> columns = new TreeMap<>();
        Map<String, String> constraints = new TreeMap<>();
        Map<String, String> indexes = new TreeMap<>();
        Map<String, String> sequences = new TreeMap<>();
        try (Connection connection = connection();
                Statement statement = connection.createStatement()) {
            try (ResultSet rows =
                    statement.executeQuery(
                            """
                            select c.relname, a.attname, format_type(a.atttypid, a.atttypmod),
                                   a.attnotnull, pg_get_expr(d.adbin, d.adrelid), a.attidentity, a.attgenerated
                              from pg_class c
                              join pg_namespace n on n.oid = c.relnamespace
                              join pg_attribute a on a.attrelid = c.oid and a.attnum > 0 and not a.attisdropped
                              left join pg_attrdef d on d.adrelid = a.attrelid and d.adnum = a.attnum
                             where n.nspname = '%s' and c.relkind = 'r' and c.relname <> 'flyway_schema_history'
                             order by c.relname, a.attnum
                            """
                                    .formatted(schema))) {
                while (rows.next()) {
                    columns.computeIfAbsent(rows.getString(1), key -> new ArrayList<>())
                            .add(
                                    "%s|%s|notNull=%s|default=%s|identity=%s|generated=%s"
                                            .formatted(
                                                    rows.getString(2),
                                                    rows.getString(3),
                                                    rows.getBoolean(4),
                                                    rows.getString(5),
                                                    rows.getString(6).trim(),
                                                    rows.getString(7).trim()));
                }
            }
            try (ResultSet rows =
                    statement.executeQuery(
                            """
                            select cl.relname, c.conname, c.contype, pg_get_constraintdef(c.oid)
                              from pg_constraint c
                              join pg_class cl on cl.oid = c.conrelid
                              join pg_namespace n on n.oid = c.connamespace
                             where n.nspname = '%s' and cl.relname <> 'flyway_schema_history'
                            """
                                    .formatted(schema))) {
                while (rows.next()) {
                    constraints.put(
                            rows.getString(1) + "." + rows.getString(2),
                            rows.getString(3) + "|" + rows.getString(4));
                }
            }
            try (ResultSet rows =
                    statement.executeQuery(
                            "select tablename, indexname, indexdef from pg_indexes where schemaname = '"
                                    + schema
                                    + "' and tablename <> 'flyway_schema_history'")) {
                while (rows.next()) {
                    indexes.put(
                            rows.getString(1) + "." + rows.getString(2),
                            rows.getString(3).replace(" " + schema + ".", " "));
                }
            }
            try (ResultSet rows =
                    statement.executeQuery(
                            "select sequencename, data_type, start_value, increment_by, cycle from pg_sequences"
                                    + " where schemaname = '"
                                    + schema
                                    + "'")) {
                while (rows.next()) {
                    sequences.put(
                            rows.getString(1),
                            "%s|start=%s|increment=%s|cycle=%s"
                                    .formatted(
                                            rows.getString(2),
                                            rows.getString(3),
                                            rows.getString(4),
                                            rows.getString(5)));
                }
            }
        }
        return new Catalog(columns, constraints, indexes, sequences);
    }

    // ---- mapa de renomeação
    // -----------------------------------------------------------------------------------

    /**
     * {@code db/rename-map.json}: tabelas, colunas, restrições, índices, sequências e chaves JSON.
     */
    public static JsonNode renameMap() throws IOException {
        try (InputStream input =
                MigrationTestSupport.class.getResourceAsStream("/db/rename-map.json")) {
            if (input == null) {
                throw new IllegalStateException(
                        "db/rename-map.json não está no classpath de teste");
            }
            return JSON.readTree(input);
        }
    }

    /**
     * O catálogo da V6 com o mapa aplicado: o que a V7 deve produzir (a única diferença permitida é
     * a chave JSON).
     */
    public static Catalog renamed(Catalog old, JsonNode map) {
        Map<String, String> words = new LinkedHashMap<>();
        for (var entry : map.get("tables").properties()) {
            words.put(entry.getKey(), entry.getValue().asString());
        }
        for (var table : map.get("columns").properties()) {
            for (var column : table.getValue().properties()) {
                words.put(column.getKey(), column.getValue().asString());
            }
        }
        for (String group : List.of("constraints", "indexes", "sequences")) {
            for (var entry : map.get(group).properties()) {
                words.put(entry.getKey(), entry.getValue().asString());
            }
        }
        String periodKey = null;
        for (JsonNode set : map.get("jsonKeys")) {
            if (set.get("table").asString().equals("processing_job")
                    && set.get("column").asString().equals("parameters")) {
                periodKey = set.get("keys").get("periodoChave").asString();
            }
        }

        Map<String, List<String>> columns = new TreeMap<>();
        for (var table : old.columns().entrySet()) {
            String newTable = map.get("tables").get(table.getKey()).asString();
            List<String> newColumns = new ArrayList<>();
            for (String column : table.getValue()) {
                String[] parts = column.split("\\|", 2);
                String newName = map.get("columns").get(table.getKey()).get(parts[0]).asString();
                newColumns.add(newName + "|" + code(parts[1], words, null));
            }
            columns.put(newTable, newColumns);
        }
        Map<String, String> constraints = new TreeMap<>();
        for (var entry : old.constraints().entrySet()) {
            String[] key = entry.getKey().split("\\.", 2);
            String newName = name(map.get("constraints"), key[1]);
            constraints.put(
                    map.get("tables").get(key[0]).asString() + "." + newName,
                    code(entry.getValue(), words, null));
        }
        Map<String, String> indexes = new TreeMap<>();
        for (var entry : old.indexes().entrySet()) {
            String[] key = entry.getKey().split("\\.", 2);
            String newName =
                    map.get("indexes").has(key[1])
                            ? map.get("indexes").get(key[1]).asString()
                            : name(map.get("constraints"), key[1]);
            indexes.put(
                    map.get("tables").get(key[0]).asString() + "." + newName,
                    code(entry.getValue(), words, periodKey));
        }
        Map<String, String> sequences = new TreeMap<>();
        for (var entry : old.sequences().entrySet()) {
            sequences.put(name(map.get("sequences"), entry.getKey()), entry.getValue());
        }
        return new Catalog(columns, constraints, indexes, sequences);
    }

    private static String name(JsonNode group, String old) {
        return group.has(old) ? group.get(old).asString() : old;
    }

    /**
     * Troca as palavras do SQL pelo mapa, fora dos literais entre aspas simples. Só o literal da
     * chave JSON do índice por expressão ({@code 'periodoChave'}) muda de nome, e só por {@code
     * periodKey}.
     */
    private static String code(String sql, Map<String, String> words, String periodKey) {
        StringBuilder out = new StringBuilder();
        int i = 0;
        while (i < sql.length()) {
            if (sql.charAt(i) == '\'') {
                int end = i + 1;
                while (end < sql.length()) {
                    if (sql.charAt(end) == '\'') {
                        if (end + 1 < sql.length() && sql.charAt(end + 1) == '\'') {
                            end += 2;
                            continue;
                        }
                        break;
                    }
                    end++;
                }
                String literal = sql.substring(i, Math.min(end + 1, sql.length()));
                out.append(
                        periodKey != null && literal.equals("'periodoChave'")
                                ? "'" + periodKey + "'"
                                : literal);
                i = end + 1;
            } else {
                int next = sql.indexOf('\'', i);
                String segment = sql.substring(i, next < 0 ? sql.length() : next);
                Matcher matcher = WORD.matcher(segment);
                out.append(
                        matcher.replaceAll(
                                match ->
                                        Matcher.quoteReplacement(
                                                words.getOrDefault(match.group(), match.group()))));
                i = next < 0 ? sql.length() : next;
            }
        }
        return out.toString();
    }
}
