package br.com.felipeblaft.reports.doubles;

import br.com.felipeblaft.reports.domain.telegram.TelegramLink;
import br.com.felipeblaft.reports.port.TelegramLinkRepository;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class InMemoryTelegramLinkRepository implements TelegramLinkRepository {

    private final Map<UUID, TelegramLink> data = new LinkedHashMap<>();

    @Override
    public Optional<TelegramLink> searchByTelegramUserId(long telegramUserId) {
        return data.values().stream().filter(v -> v.telegramUserId() == telegramUserId).findFirst();
    }

    @Override
    public Optional<TelegramLink> searchByTeacherId(UUID teacherId) {
        return data.values().stream().filter(v -> v.teacherId().equals(teacherId)).findFirst();
    }

    @Override
    public void save(TelegramLink link) {
        data.put(link.id(), link);
    }

    @Override
    public void remove(UUID id) {
        data.remove(id);
    }

    public List<TelegramLink> all() {
        return new ArrayList<>(data.values());
    }
}
