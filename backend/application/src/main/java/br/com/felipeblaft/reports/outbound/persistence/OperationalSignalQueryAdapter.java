package br.com.felipeblaft.reports.outbound.persistence;

import br.com.felipeblaft.reports.port.OperationalSignalQueryPort;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Sinais da apuração de alertas (FR-055): chamadas de IA ({@code ai_call}) e a fila de entrega do
 * canal ({@code channel_action}). Só contagens e momentos; nenhum dado pessoal é lido.
 */
@Repository
class OperationalSignalQueryAdapter implements OperationalSignalQueryPort {

    private final JdbcTemplate jdbc;

    OperationalSignalQueryAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<ProviderWindow> providerWindows(Instant since) {
        Timestamp from = Timestamp.from(since);
        List<String> providers =
                jdbc.query(
                        "SELECT DISTINCT provider_model FROM ai_call WHERE created_at >= ? AND"
                                + " provider_model IS NOT NULL",
                        (rs, row) -> rs.getString(1),
                        from);
        List<ProviderWindow> windows = new ArrayList<>();
        for (String provider : providers) {
            List<Boolean> outcomes =
                    jdbc.query(
                            "SELECT success FROM ai_call WHERE provider_model = ? AND created_at >="
                                    + " ? ORDER BY created_at DESC, id DESC",
                            (rs, row) -> rs.getBoolean(1),
                            provider,
                            from);
            int failures = (int) outcomes.stream().filter(ok -> !ok).count();
            int trailing = 0;
            for (boolean ok : outcomes) {
                if (ok) {
                    break;
                }
                trailing++;
            }
            windows.add(new ProviderWindow(provider, outcomes.size(), failures, trailing));
        }
        return windows;
    }

    @Override
    public QueueSnapshot queueSnapshot() {
        return jdbc.queryForObject(
                "SELECT count(*) AS pending, min(created_at) AS oldest FROM channel_action WHERE"
                        + " state = 'PENDING'",
                (rs, row) -> {
                    Timestamp oldest = rs.getTimestamp("oldest");
                    return new QueueSnapshot(
                            rs.getInt("pending"), oldest == null ? null : oldest.toInstant());
                });
    }
}
