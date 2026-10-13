package br.com.felipeblaft.reports.doubles;

import br.com.felipeblaft.reports.domain.channel.ChannelDocument;
import br.com.felipeblaft.reports.port.ChannelDocumentRepository;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public class InMemoryChannelDocumentRepository implements ChannelDocumentRepository {

    public final Map<String, ChannelDocument> data = new LinkedHashMap<>();

    @Override
    public void emit(ChannelDocument document) {
        data.put(document.tokenHash(), document);
    }

    @Override
    public Optional<ChannelDocument> searchByHash(String tokenHash) {
        return Optional.ofNullable(data.get(tokenHash));
    }

    @Override
    public int purgeExpired(Instant now) {
        int before = data.size();
        data.values().removeIf(d -> !d.valid(now));
        return before - data.size();
    }
}
