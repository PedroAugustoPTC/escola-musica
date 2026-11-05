package br.com.felipeblaft.reports.doubles;

import br.com.felipeblaft.reports.domain.privacy.GuardianConsent;
import br.com.felipeblaft.reports.port.ConsentRepository;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Consentimentos em memória: a revogação regrava o mesmo registro; o histórico é preservado. */
public class InMemoryConsentRepository implements ConsentRepository {

    private final Map<UUID, GuardianConsent> data = new LinkedHashMap<>();
    private int saves;

    @Override
    public void save(GuardianConsent consent) {
        data.put(consent.id(), consent);
        saves++;
    }

    @Override
    public Optional<GuardianConsent> findById(UUID id) {
        return Optional.ofNullable(data.get(id));
    }

    @Override
    public List<GuardianConsent> findByStudent(UUID studentId) {
        return data.values().stream()
                .filter(consent -> consent.studentId().equals(studentId))
                .sorted(Comparator.comparing(GuardianConsent::recordedAt).reversed())
                .toList();
    }

    @Override
    public Map<UUID, List<GuardianConsent>> findByStudents(Collection<UUID> studentIds) {
        Map<UUID, List<GuardianConsent>> result = new LinkedHashMap<>();
        for (UUID id : studentIds) {
            List<GuardianConsent> history = new ArrayList<>(findByStudent(id));
            if (!history.isEmpty()) {
                result.put(id, history);
            }
        }
        return result;
    }

    @Override
    public int removeNamesByStudent(UUID studentId) {
        int treated = 0;
        for (GuardianConsent consent : List.copyOf(data.values())) {
            if (consent.studentId().equals(studentId)) {
                data.put(consent.id(), consent.withoutName());
                treated++;
            }
        }
        return treated;
    }

    public int size() {
        return data.size();
    }

    public int saves() {
        return saves;
    }
}
