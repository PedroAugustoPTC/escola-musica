package br.com.felipeblaft.reports.doubles;

import br.com.felipeblaft.reports.port.AlertHistoryRetentionPort;
import java.time.Instant;

/** Dublê do expurgo de alertas: devolve a quantidade programada e guarda o corte recebido. */
public class FakeAlertHistoryRetention implements AlertHistoryRetentionPort {

    public int toPurge;
    public Instant lastCutoff;

    @Override
    public int purgeNormalizedBefore(Instant cutoff) {
        lastCutoff = cutoff;
        return toPurge;
    }
}
