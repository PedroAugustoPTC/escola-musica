package br.com.felipeblaft.reports.support;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Base dos testes de integração: Spring Boot contra o {@code postgres-test} de
 * infra/compose.test.yaml, com as tabelas limpas antes de cada teste e relógio fixo.
 *
 * <p>O servidor HTTP embutido sobe sempre, em porta aleatória (os testes continuam usando MockMvc).
 * No transporte {@code n8n} (feature 004) o n8n em contêiner chama o backend por um proxy de porta
 * fixa ({@link TelegramConversationBase}) que encaminha ao contexto do teste em execução.
 */
@SpringBootTest(webEnvironment = WebEnvironment.DEFINED_PORT)
@ActiveProfiles("test")
@Import(ConfigTest.class)
public abstract class IntegrationBase {

    /** Porta HTTP real do contexto deste teste. */
    @Value("${local.server.port:0}")
    protected int serverPort;

    @DynamicPropertySource
    static void randomServerPort(DynamicPropertyRegistry registration) {
        registration.add("server.port", () -> 0);
    }

    @Autowired protected JdbcTemplate jdbc;

    @Autowired protected AdjustableClock clock;

    @BeforeEach
    void cleanDatabase() {
        truncateAll();
    }

    /** Esvazia todas as tabelas e volta o relógio ao padrão (também usado no meio de um teste). */
    protected void truncateAll() {
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
