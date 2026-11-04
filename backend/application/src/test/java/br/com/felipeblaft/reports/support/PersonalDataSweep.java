package br.com.felipeblaft.reports.support;

import br.com.felipeblaft.reports.support.ResponseSweeper.Place;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Varredura reaproveitável de dado pessoal da feature 007 (SC-001): procura os valores canário
 * (nome do aluno, CPF com e sem máscara, nascimento nos formatos, dados do responsável, trecho de
 * transcrição e trecho de texto de relatório) em (a) logs capturados, (b) toda coluna de texto e
 * {@code jsonb} de {@code ai_call}, (c) {@code operational_alert}, (d) {@code authorship_record},
 * (e) {@code retention_run_item} e {@code erasure_request} (menos {@code requester_name} antes de o
 * pedido concluir, único lugar onde o nome de quem pediu existe, por necessidade do protocolo).
 *
 * <p>Cada achado cita passo, rótulo e lugar, nunca o valor. Quem usa acumula em um {@link
 * LeakAudit} e falha uma vez, ao final, com todos os achados.
 */
public final class PersonalDataSweep {

    private final JdbcTemplate jdbc;
    private final Map<String, String> values;

    private PersonalDataSweep(JdbcTemplate jdbc, Map<String, String> values) {
        this.jdbc = jdbc;
        this.values = values;
    }

    /**
     * Varre os valores canário padrão (aluno, responsável, CPF, transcrição e texto de relatório).
     */
    public static PersonalDataSweep of(JdbcTemplate jdbc) {
        return new PersonalDataSweep(jdbc, CanaryStudent.sweepValuesWithCpf());
    }

    /** Varre valores específicos (rótulo para valor). */
    public static PersonalDataSweep of(JdbcTemplate jdbc, Map<String, String> values) {
        return new PersonalDataSweep(jdbc, Map.copyOf(values));
    }

    /** Os valores procurados, para quem monta a própria {@link LeakAudit}. */
    public Map<String, String> values() {
        return values;
    }

    /** Uma auditoria nova com os valores desta varredura. */
    public LeakAudit newAudit() {
        return LeakAudit.searching(values);
    }

    /** Varre as tabelas e, se informado, o texto dos logs capturados. */
    public void sweep(LeakAudit audit, String step, LogCapture log) {
        if (log != null) {
            audit.text(step, Place.LOG, log.text());
        }
        audit.text(step, Place.AI_CALL, tableText("ai_call", List.of()));
        audit.text(step, Place.OPERATIONAL_ALERT, tableText("operational_alert", List.of()));
        audit.text(step, Place.AUTHORSHIP, tableText("authorship_record", List.of()));
        audit.text(step, Place.RETENTION_ITEM, tableText("retention_run_item", List.of()));
        audit.text(step, Place.ERASURE_REQUEST, erasureRequestText());
    }

    /** Todo texto e {@code jsonb} das colunas da tabela, em uma só cadeia. */
    private String tableText(String table, List<String> excludedColumns) {
        List<String> columns =
                jdbc.queryForList(
                        "select column_name from information_schema.columns where table_schema ="
                                + " current_schema() and table_name = ? and (data_type in ('text',"
                                + " 'character varying', 'jsonb', 'json'))",
                        String.class,
                        table);
        columns = columns.stream().filter(c -> !excludedColumns.contains(c)).toList();
        if (columns.isEmpty()) {
            return "";
        }
        String expression =
                columns.stream()
                        .map(c -> "coalesce(\"" + c + "\"::text, '')")
                        .reduce((a, b) -> a + " || ' ' || " + b)
                        .orElse("''");
        String joined =
                jdbc.queryForObject(
                        "select coalesce(string_agg(" + expression + ", E'\\n'), '') from " + table,
                        String.class);
        return joined == null ? "" : joined;
    }

    /** O nome de quem pediu só vale depois de concluído o pedido (e então já é nulo). */
    private String erasureRequestText() {
        String others = tableText("erasure_request", List.of("requester_name"));
        String name =
                jdbc.queryForObject(
                        "select coalesce(string_agg(requester_name, ' '), '') from erasure_request"
                                + " where status = 'COMPLETED'",
                        String.class);
        return others + "\n" + (name == null ? "" : name);
    }
}
