package br.com.felipeblaft.relatorios.suporte;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * Base dos testes de integração: Spring Boot contra o {@code postgres-teste} de
 * infra/compose.test.yaml, com as tabelas limpas antes de cada teste e relógio fixo.
 */
@SpringBootTest
@ActiveProfiles("teste")
@Import(ConfiguracaoTeste.class)
public abstract class IntegracaoBase {

    @Autowired protected JdbcTemplate jdbc;

    @Autowired protected RelogioAjustavel relogio;

    @BeforeEach
    void limparBanco() {
        List<String> tabelas =
                jdbc.queryForList(
                        "select tablename from pg_tables where schemaname = 'public'"
                                + " and tablename <> 'flyway_schema_history'",
                        String.class);
        if (!tabelas.isEmpty()) {
            jdbc.execute("truncate table " + String.join(", ", tabelas) + " cascade");
        }
        relogio.definir(RelogioAjustavel.PADRAO);
    }
}
