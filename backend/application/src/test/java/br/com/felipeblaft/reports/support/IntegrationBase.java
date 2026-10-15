package br.com.felipeblaft.reports.support;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * Base dos testes de integração: Spring Boot contra o {@code postgres-test} de
 * infra/compose.test.yaml, com as tabelas limpas antes de cada teste e relógio fixo.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(ConfigTest.class)
public abstract class IntegrationBase {

    @Autowired protected JdbcTemplate jdbc;

    @Autowired protected AdjustableClock clock;

    @BeforeEach
    void cleanDatabase() {
        List<String> tables =
                jdbc.queryForList(
                        "select tablename from pg_tables where schemaname = 'public'"
                                + " and tablename <> 'flyway_schema_history'",
                        String.class);
        if (!tables.isEmpty()) {
            jdbc.execute("truncate table " + String.join(", ", tables) + " cascade");
        }
        clock.define(AdjustableClock.DEFAULT);
    }
}
