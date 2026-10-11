package br.com.felipeblaft.relatorios.arquitetura;

import br.com.felipeblaft.relatorios.arquitetura.GlossaryFile.Violation;
import br.com.felipeblaft.relatorios.arquitetura.RepositoryWalker.Entry;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

/**
 * Guardião de idioma dos artefatos de dados e configuração (feature 003, FR-019): o catálogo do
 * banco depois de todas as migrações (tabelas, colunas, índices, constraints e sequências, exceto
 * {@code flyway_schema_history}) e os arquivos de infraestrutura, workflows, recursos, build,
 * scripts e contratos. Roda contra o {@code postgres-teste}: as migrações são aplicadas num banco
 * descartável, criado e apagado pelo próprio teste.
 *
 * <p>Modo relatório: {@code GUARD_REPORT=1} grava {@code target/guard-violations-artifacts.json}.
 */
class ArtifactsLanguageGuardIT {

    private static final String GUARD = "artifacts";

    @Test
    @DisplayName("o catálogo do banco e os arquivos de configuração usam só nomes em inglês")
    void artifactsUseOnlyEnglishNames() throws Exception {
        GlossaryFile glossary = GlossaryFile.load();
        RepositoryWalker walker = new RepositoryWalker(glossary.root());
        ArtifactScanner scanner = new ArtifactScanner(glossary);
        List<Violation> found = new ArrayList<>();

        for (Entry entry : walker.walk()) {
            // todo arquivo passa pela regra skip-file (é assim que as exceções por pasta provam que
            // estão em uso)
            if (glossary.isSkippedFile(entry.path()) || !RepositoryWalker.inArtifactsScope(entry)) {
                continue;
            }
            found.addAll(
                    glossary.violations(
                            entry.path(),
                            1,
                            entry.directory() ? "directory" : "file",
                            entry.name(),
                            GlossaryFile.Kind.PATH));
            if (!entry.directory() && ArtifactScanner.supports(entry.name())) {
                found.addAll(scanner.scan(entry.path(), walker.read(entry)));
            }
        }
        found.addAll(catalogViolations(scanner));

        glossary.conclude(
                GUARD,
                Path.of("target", "guard-violations-artifacts.json"),
                found,
                RepositoryWalker::ownedByArtifactsGuard);
    }

    /** Aplica todas as migrações num banco descartável e examina os nomes do catálogo. */
    private static List<Violation> catalogViolations(ArtifactScanner scanner) throws Exception {
        // O perfil de testes (application-*.yaml em src/test/resources) traz o datasource e os
        // nomes das variáveis.
        StandardEnvironment environment = new StandardEnvironment();
        for (Resource resource :
                new PathMatchingResourcePatternResolver()
                        .getResources("classpath:/application-*.yaml")) {
            for (PropertySource<?> source :
                    new YamlPropertySourceLoader().load("test-profile", resource)) {
                environment.getPropertySources().addLast(source);
            }
        }
        String url = environment.getProperty("spring.datasource.url");
        String user = environment.getProperty("spring.datasource.username");
        String password = environment.getProperty("spring.datasource.password");

        String scratch = "guard_catalog_" + UUID.randomUUID().toString().replace("-", "");
        int slash = url.lastIndexOf('/');
        int query = url.indexOf('?', slash);
        String scratchUrl =
                url.substring(0, slash + 1) + scratch + (query < 0 ? "" : url.substring(query));

        try (Connection admin = DriverManager.getConnection(url, user, password);
                Statement statement = admin.createStatement()) {
            statement.execute("create database " + scratch);
        }
        try {
            Flyway.configure()
                    .dataSource(scratchUrl, user, password)
                    .locations("classpath:db/migration")
                    .load()
                    .migrate();
            List<Violation> found = new ArrayList<>();
            try (Connection connection = DriverManager.getConnection(scratchUrl, user, password)) {
                collect(
                        connection,
                        scanner,
                        "table",
                        found,
                        "select table_name, table_name from information_schema.tables where table_schema = 'public'");
                collect(
                        connection,
                        scanner,
                        "column",
                        found,
                        "select table_name || '.' || column_name, column_name from information_schema.columns where table_schema = 'public'");
                collect(
                        connection,
                        scanner,
                        "index",
                        found,
                        "select indexname, indexname from pg_indexes where schemaname = 'public'");
                collect(
                        connection,
                        scanner,
                        "constraint",
                        found,
                        "select c.conname, c.conname from pg_constraint c join pg_namespace n on n.oid = c.connamespace where n.nspname = 'public'");
                collect(
                        connection,
                        scanner,
                        "sequence",
                        found,
                        "select sequence_name, sequence_name from information_schema.sequences where sequence_schema = 'public'");
            }
            return found;
        } finally {
            try (Connection admin = DriverManager.getConnection(url, user, password);
                    Statement statement = admin.createStatement()) {
                statement.execute("drop database if exists " + scratch + " with (force)");
            }
        }
    }

    private static void collect(
            Connection connection,
            ArtifactScanner scanner,
            String kind,
            List<Violation> found,
            String sql)
            throws SQLException {
        try (Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery(sql)) {
            while (rows.next()) {
                String identity = rows.getString(1);
                String name = rows.getString(2);
                if (name.startsWith("flyway_schema_history")
                        || identity.startsWith("flyway_schema_history")) {
                    continue;
                }
                found.addAll(scanner.catalogObject(kind, identity, name));
            }
        }
    }
}
