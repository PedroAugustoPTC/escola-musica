package br.com.felipeblaft.reports.doubles;

import br.com.felipeblaft.reports.port.OperationalSignalQueryPort;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Sinais programados pelo teste: provedores da janela e a fila do canal. */
public class FakeOperationalSignals implements OperationalSignalQueryPort {

    public final List<ProviderWindow> providers = new ArrayList<>();
    public QueueSnapshot queue = new QueueSnapshot(0, null);
    public Instant lastSince;

    @Override
    public List<ProviderWindow> providerWindows(Instant since) {
        lastSince = since;
        return List.copyOf(providers);
    }

    @Override
    public QueueSnapshot queueSnapshot() {
        return queue;
    }
}
