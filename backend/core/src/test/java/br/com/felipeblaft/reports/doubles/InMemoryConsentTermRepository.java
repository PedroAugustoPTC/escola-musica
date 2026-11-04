package br.com.felipeblaft.reports.doubles;

import br.com.felipeblaft.reports.domain.privacy.ConsentTermVersion;
import br.com.felipeblaft.reports.port.ConsentTermRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Versões do termo em memória, da mais recente à mais antiga. */
public class InMemoryConsentTermRepository implements ConsentTermRepository {

    private final List<ConsentTermVersion> data = new ArrayList<>();

    @Override
    public List<ConsentTermVersion> listAll() {
        return data.stream()
                .sorted(Comparator.comparingInt(ConsentTermVersion::number).reversed())
                .toList();
    }

    @Override
    public void save(ConsentTermVersion term) {
        data.removeIf(existing -> existing.number() == term.number());
        data.add(term);
    }
}
