package br.com.felipeblaft.reports.support;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Textos que os testes de privacidade varrem (SC-004): as linhas das tabelas, o registro de autoria
 * e os rastros de pilha dos eventos de log. Funções puras sobre o banco de teste e sobre a captura
 * de logs, para servir também aos testes que não herdam de {@link RealStackBase} (por exemplo, os
 * do perfil {@code seed}).
 */
public final class PrivacyProbes {

    private PrivacyProbes() {}

    /** Todas as linhas de uma tabela como texto (vazio se não há linhas). */
    public static String tableText(JdbcTemplate jdbc, String table) {
        String text =
                jdbc.queryForObject(
                        "select string_agg(t::text, E'\\n') from " + table + " t", String.class);
        return text == null ? "" : text;
    }

    /** O registro de autoria inteiro como texto. */
    public static String authorshipText(JdbcTemplate jdbc) {
        return tableText(jdbc, "authorship_record");
    }

    /** Nomes das tabelas do banco, menos o histórico do Flyway. */
    public static List<String> tables(JdbcTemplate jdbc) {
        return jdbc.queryForList(
                "select tablename from pg_tables where schemaname = 'public'"
                        + " and tablename <> 'flyway_schema_history' order by tablename",
                String.class);
    }

    /** Só os rastros de pilha (com as causas) dos eventos capturados. */
    public static String traceText(LogCapture logs) {
        StringBuilder traces = new StringBuilder();
        for (ILoggingEvent event : logs.events()) {
            if (event.getThrowableProxy() != null) {
                traces.append(ThrowableProxyUtil.asString(event.getThrowableProxy())).append('\n');
            }
        }
        return traces.toString();
    }
}
