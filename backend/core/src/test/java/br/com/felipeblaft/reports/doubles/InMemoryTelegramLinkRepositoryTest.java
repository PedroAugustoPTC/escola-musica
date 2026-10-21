package br.com.felipeblaft.reports.doubles;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.felipeblaft.reports.domain.telegram.TelegramLink;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class InMemoryTelegramLinkRepositoryTest {

    private static final Instant NOW = Instant.parse("2026-09-21T13:00:00Z");

    private final InMemoryTelegramLinkRepository links = new InMemoryTelegramLinkRepository();

    private TelegramLink saved(UUID teacherId, long telegramUserId) {
        TelegramLink link = TelegramLink.create(teacherId, telegramUserId, telegramUserId, NOW);
        links.save(link);
        return link;
    }

    @Test
    void searchesByAccountAndByTeacher() {
        UUID teacher = UUID.randomUUID();
        TelegramLink link = saved(teacher, 111L);
        saved(UUID.randomUUID(), 222L);

        assertThat(links.searchByTelegramUserId(111L)).contains(link);
        assertThat(links.searchByTeacherId(teacher)).contains(link);
        assertThat(links.searchByTelegramUserId(999L)).isEmpty();
    }

    @Test
    void removeDeletesTheLink() {
        TelegramLink link = saved(UUID.randomUUID(), 111L);

        links.remove(link.id());

        assertThat(links.all()).isEmpty();
    }

    @Test
    void removeCascadesToTheRegisteredListenersWithTheLinkId() {
        List<UUID> cascaded = new ArrayList<>();
        links.cascadeRemovalTo(cascaded::add);
        TelegramLink link = saved(UUID.randomUUID(), 111L);
        saved(UUID.randomUUID(), 222L);

        links.remove(link.id());

        assertThat(cascaded).containsExactly(link.id());
    }

    @Test
    void removingAnUnknownLinkDoesNotCascade() {
        List<UUID> cascaded = new ArrayList<>();
        links.cascadeRemovalTo(cascaded::add);

        links.remove(UUID.randomUUID());

        assertThat(cascaded).isEmpty();
    }

    @Test
    void countsTheRemovals() {
        TelegramLink first = saved(UUID.randomUUID(), 111L);
        TelegramLink second = saved(UUID.randomUUID(), 222L);

        links.remove(first.id());
        links.remove(second.id());
        links.remove(second.id());

        assertThat(links.removals()).isEqualTo(2);
    }
}
