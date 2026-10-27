package br.com.felipeblaft.reports.doubles;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.felipeblaft.reports.domain.access.Administrator;
import br.com.felipeblaft.reports.domain.common.PageRequest;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import org.junit.jupiter.api.Test;

class InMemoryAdministratorRepositoryTest {

    private static final Instant NOW = Instant.parse("2026-09-21T13:00:00Z");

    private final InMemoryAdministratorRepository administrators =
            new InMemoryAdministratorRepository();

    private Administrator saved(String name, String email, boolean active) {
        Administrator administrator =
                Administrator.pending(name, email, "hash", NOW.plus(Duration.ofDays(7)), NOW);
        Administrator result = active ? administrator : administrator.inactivate(NOW);
        administrators.save(result);
        return result;
    }

    @Test
    void findsByIdAndByNormalizedEmail() {
        Administrator ana = saved("Ana Souza", "ana@school.test", true);

        assertThat(administrators.searchById(ana.id())).contains(ana);
        assertThat(administrators.searchByEmail("ana@school.test")).contains(ana);
        assertThat(administrators.searchByEmail("other@school.test")).isEmpty();
        assertThat(administrators.count()).isEqualTo(1);
    }

    @Test
    void insertIfAbsentRefusesADuplicateEmailInactiveOnesIncluded() {
        saved("Ana Souza", "ana@school.test", false);

        boolean inserted =
                administrators.insertIfAbsent(
                        Administrator.pending(
                                "Outra Ana", "ana@school.test", "hash", NOW.plusSeconds(1), NOW));

        assertThat(inserted).isFalse();
        assertThat(administrators.count()).isEqualTo(1);
    }

    @Test
    void insertIfNoneExistsInsertsOnlyTheFirst() {
        Administrator first =
                Administrator.first("Ana", "ana@school.test", "h", Duration.ofDays(7), NOW);
        Administrator second =
                Administrator.first("Bia", "bia@school.test", "h", Duration.ofDays(7), NOW);

        assertThat(administrators.insertIfNoneExists(first)).isTrue();
        assertThat(administrators.insertIfNoneExists(second)).isFalse();
        assertThat(administrators.all()).containsExactly(first);
    }

    @Test
    void listsInAlphabeticalOrderIgnoringAccentsAndCase() {
        Administrator zelia = saved("Zélia Prado", "zelia@school.test", true);
        Administrator alice = saved("alice Rocha", "alice@school.test", true);
        Administrator ines = saved("Inês Alves", "ines@school.test", true);

        assertThat(administrators.list(null, null, PageRequest.first()).items())
                .containsExactly(alice, ines, zelia);
    }

    @Test
    void filtersBySituationAndSearchesByNameOrEmail() {
        Administrator active = saved("Ana Souza", "ana@school.test", true);
        Administrator inactive = saved("Bia Lima", "contato@school.test", false);

        assertThat(administrators.list(null, true, PageRequest.first()).items())
                .containsExactly(active);
        assertThat(administrators.list(null, false, PageRequest.first()).items())
                .containsExactly(inactive);
        assertThat(administrators.list("CONTATO", null, PageRequest.first()).items())
                .containsExactly(inactive);
        assertThat(administrators.list("ana", null, PageRequest.first()).items())
                .containsExactly(active);
        assertThat(administrators.list("zzz", null, PageRequest.first()).total()).isZero();
    }

    @Test
    void paginatesAfterTheFilter() {
        for (int i = 0; i < 5; i++) {
            saved("Admin " + i, "admin" + i + "@school.test", true);
        }

        var page = administrators.list(null, null, new PageRequest(1, 2));

        assertThat(page.items()).hasSize(2);
        assertThat(page.total()).isEqualTo(5);
    }

    @Test
    void locksOnlyTheActiveOnesInFixedIdOrderAndCountsTheLocks() {
        saved("Ana", "ana@school.test", true);
        saved("Bia", "bia@school.test", true);
        saved("Caio", "caio@school.test", false);

        var locked = administrators.lockAllActiveForUpdate();

        assertThat(locked).hasSize(2).allMatch(Administrator::active);
        assertThat(locked).isSortedAccordingTo(Comparator.comparing(Administrator::id));
        assertThat(administrators.locks()).isEqualTo(1);
    }

    @Test
    void countsTheRecordings() {
        saved("Ana", "ana@school.test", true);

        assertThat(administrators.recordings()).isEqualTo(1);
    }
}
