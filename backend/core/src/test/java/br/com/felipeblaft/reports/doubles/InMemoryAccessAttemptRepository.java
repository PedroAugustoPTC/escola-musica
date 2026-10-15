package br.com.felipeblaft.reports.doubles;

import br.com.felipeblaft.reports.domain.access.AccessAttempt;
import br.com.felipeblaft.reports.domain.common.Channel;
import br.com.felipeblaft.reports.port.AccessAttemptRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class InMemoryAccessAttemptRepository implements AccessAttemptRepository {

    private final List<AccessAttempt> data = new ArrayList<>();

    @Override
    public Optional<AccessAttempt> search(Channel channel, String originHash) {
        return data.stream()
                .filter(t -> t.channel() == channel && t.originHash().equals(originHash))
                .findFirst();
    }

    @Override
    public void save(AccessAttempt attempt) {
        data.removeIf(t -> t.id().equals(attempt.id()));
        data.add(attempt);
    }

    public List<AccessAttempt> all() {
        return List.copyOf(data);
    }
}
