package br.com.felipeblaft.reports.doubles;

import br.com.felipeblaft.reports.domain.telegram.TelegramLink;
import br.com.felipeblaft.reports.port.TelegramLinkRepository;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Vínculos do Telegram em memória. A remoção dispara a cascata do banco: quem depende do vínculo (o
 * cadastro de aluno em andamento) se registra com {@link #cascadeRemovalTo(Consumer)} e recebe o id
 * do vínculo removido.
 */
public class InMemoryTelegramLinkRepository implements TelegramLinkRepository {

    private final Map<UUID, TelegramLink> data = new LinkedHashMap<>();
    private final List<Consumer<UUID>> cascades = new ArrayList<>();
    private int removals;

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
        if (data.remove(id) != null) {
            removals++;
            cascades.forEach(cascade -> cascade.accept(id));
        }
    }

    /** Registra quem apaga junto quando um vínculo é removido (cascata do rascunho). */
    public void cascadeRemovalTo(Consumer<UUID> cascade) {
        cascades.add(cascade);
    }

    /** Quantos vínculos foram de fato removidos. */
    public int removals() {
        return removals;
    }

    public List<TelegramLink> all() {
        return new ArrayList<>(data.values());
    }
}
