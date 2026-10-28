package br.com.felipeblaft.reports.security;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.felipeblaft.reports.support.AdministrationStackBase;
import br.com.felipeblaft.reports.support.AdministratorFixture;
import br.com.felipeblaft.reports.support.SimultaneousRunner;
import jakarta.servlet.http.Cookie;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

/**
 * SC-009 (R-15), pela API real e com Postgres real: inativações simultâneas, inclusive cruzadas
 * (cada um inativa o outro), sempre terminam com pelo menos um administrador ativo. Cada resposta é
 * 200 (inativou), 409 {@code LAST_ADMINISTRATOR} ou 401 (a sessão já tinha sido encerrada pela
 * outra inativação).
 */
class LastAdministratorConcurrencyIT extends AdministrationStackBase {

    private record Account(AdministratorFixture fixture, Cookie session) {}

    private Account account(int number) throws Exception {
        AdministratorFixture fixture =
                insertAdministrator("Admin " + number, "admin" + number + "@example.test");
        return new Account(fixture, signInAdministrator(fixture, "198.51.100." + (100 + number)));
    }

    private Callable<Integer> inactivate(Account by, Account target) {
        return () ->
                call(
                                HttpMethod.POST,
                                ADMIN
                                        + "/administrators/"
                                        + target.fixture().administrator().id()
                                        + "/inactivation",
                                by.session())
                        .getStatus();
    }

    private int activeCount() {
        return jdbc.queryForObject(
                "select count(*) from administrator where active", Integer.class);
    }

    private static void assertPossibleStatuses(List<Integer> statuses) {
        assertThat(statuses).allMatch(status -> status == 200 || status == 409 || status == 401);
    }

    @Test
    void crossedInactivationsOfTwoAdministratorsLeaveExactlyOneActive() throws Exception {
        for (int round = 0; round < 6; round++) {
            truncateAll();
            Account first = account(1);
            Account second = account(2);

            List<Integer> statuses =
                    SimultaneousRunner.runTogether(
                            List.of(inactivate(first, second), inactivate(second, first)));

            assertPossibleStatuses(statuses);
            assertThat(statuses.stream().filter(status -> status == 200).count())
                    .as("rodada %d: exatamente uma inativação vale", round)
                    .isEqualTo(1);
            assertThat(activeCount()).as("rodada %d", round).isEqualTo(1);
        }
    }

    @Test
    void manyCrossedInactivationsNeverEmptyTheSet() throws Exception {
        for (int round = 0; round < 3; round++) {
            truncateAll();
            List<Account> accounts = new ArrayList<>();
            for (int number = 1; number <= 4; number++) {
                accounts.add(account(number));
            }
            // cada um inativa o próximo, em círculo, todos ao mesmo tempo
            List<Callable<Integer>> tasks = new ArrayList<>();
            for (int i = 0; i < accounts.size(); i++) {
                tasks.add(inactivate(accounts.get(i), accounts.get((i + 1) % accounts.size())));
            }

            List<Integer> statuses = SimultaneousRunner.runTogether(tasks);

            assertPossibleStatuses(statuses);
            assertThat(activeCount()).as("rodada %d", round).isGreaterThanOrEqualTo(1);
            long inactivated = statuses.stream().filter(status -> status == 200).count();
            assertThat(activeCount()).isEqualTo(4 - (int) inactivated);
        }
    }

    @Test
    void severalAdministratorsInactivatingTheSameOneInactivateItOnce() throws Exception {
        Account target = account(1);
        Account second = account(2);
        Account third = account(3);

        List<Integer> statuses =
                SimultaneousRunner.runTogether(
                        List.of(inactivate(second, target), inactivate(third, target)));

        // a segunda chamada encontra a conta já inativa e devolve o estado atual (200)
        assertThat(statuses).containsOnly(200);
        assertThat(activeCount()).isEqualTo(2);
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from authorship_record"
                                        + " where operation = 'ADMINISTRATOR_INACTIVATE'",
                                Integer.class))
                .isEqualTo(1);
    }

    @Test
    void theOnlyActiveAdministratorCannotBeInactivated() throws Exception {
        Account only = account(1);

        int status = inactivate(only, only).call();

        assertThat(status).isEqualTo(409);
        assertThat(activeCount()).isEqualTo(1);
        assertThat(authorship("ADMINISTRATOR_INACTIVATE")).isEmpty();
    }
}
