package br.com.felipeblaft.reports.outbound.persistence;

import static br.com.felipeblaft.reports.outbound.persistence.AdministratorTestData.NOW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.felipeblaft.reports.domain.access.Administrator;
import br.com.felipeblaft.reports.port.AdministratorRepository;
import br.com.felipeblaft.reports.support.IntegrationBase;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Invariantes de concorrência do administrador (data-model.md §11, SC-009): nunca zero
 * administradores ativos, e-mail concorrente e código de ativação pendente único.
 */
class AdministratorConcurrencyIT extends IntegrationBase {

    @Autowired AdministratorRepository administrators;
    @Autowired PlatformTransactionManager transactionManager;

    /**
     * Inativa o administrador somente se sobrar outro ativo, tudo sob o bloqueio de todos os ativos
     * (o desenho do caso de uso de inativar, R-15). Devolve {@code true} se inativou.
     */
    private boolean inactivateKeepingOne(Administrator target) {
        return new TransactionTemplate(transactionManager)
                .execute(
                        status -> {
                            List<Administrator> active = administrators.lockAllActiveForUpdate();
                            if (active.size() <= 1) {
                                return false;
                            }
                            administrators.save(
                                    new Administrator(
                                            target.id(),
                                            target.name(),
                                            target.email(),
                                            false,
                                            null,
                                            false,
                                            null,
                                            null,
                                            null,
                                            target.activatedAt(),
                                            target.createdAt(),
                                            NOW.plusSeconds(1)));
                            pause();
                            return true;
                        });
    }

    /** Segura a transação um instante para a outra chegar ao bloqueio (exercita a espera). */
    private static void pause() {
        try {
            Thread.sleep(300);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Test
    void twoSimultaneousInactivationsAlwaysLeaveAtLeastOneActive() throws Exception {
        Administrator first = AdministratorTestData.active("Um", "one@example.test");
        Administrator second = AdministratorTestData.active("Dois", "two@example.test");
        administrators.insertIfAbsent(first);
        administrators.insertIfAbsent(second);

        List<Boolean> results =
                ConcurrentRunner.runTogether(
                        List.<Callable<Boolean>>of(
                                () -> inactivateKeepingOne(first),
                                () -> inactivateKeepingOne(second)));

        assertThat(results.stream().filter(Boolean::booleanValue)).hasSize(1);
        assertThat(administrators.list(null, true, br.com.felipeblaft.reports.domain.common.PageRequest.first()).total())
                .isEqualTo(1);
    }

    @Test
    void manySimultaneousInactivationsNeverEmptyTheSet() throws Exception {
        List<Administrator> all = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            Administrator admin = AdministratorTestData.active("Admin " + i, "a" + i + "@example.test");
            administrators.insertIfAbsent(admin);
            all.add(admin);
        }
        List<Callable<Boolean>> tasks = new ArrayList<>();
        for (Administrator admin : all) {
            tasks.add(() -> inactivateKeepingOne(admin));
        }

        List<Boolean> results = ConcurrentRunner.runTogether(tasks);

        assertThat(results.stream().filter(Boolean::booleanValue)).hasSize(3);
        assertThat(jdbc.queryForObject("select count(*) from administrator where active", Integer.class))
                .isEqualTo(1);
    }

    @Test
    void lockAllActiveReturnsOnlyActiveOnesInAStableOrder() {
        Administrator a = AdministratorTestData.active("A", "a@example.test");
        Administrator b = AdministratorTestData.active("B", "b@example.test");
        administrators.insertIfAbsent(a);
        administrators.insertIfAbsent(b);
        administrators.insertIfAbsent(AdministratorTestData.inactive("C", "c@example.test"));

        List<Administrator> locked =
                new TransactionTemplate(transactionManager)
                        .execute(status -> administrators.lockAllActiveForUpdate());

        assertThat(locked).extracting(Administrator::id).containsExactlyInAnyOrder(a.id(), b.id());
    }

    @Test
    void parallelInsertsOfTheSameEmailInsertExactlyOne() throws Exception {
        List<Callable<Boolean>> tasks = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            Administrator admin = AdministratorTestData.active("Admin " + i, "race@example.test");
            tasks.add(() -> administrators.insertIfAbsent(admin));
        }

        List<Boolean> results = ConcurrentRunner.runTogether(tasks);

        assertThat(results.stream().filter(Boolean::booleanValue)).hasSize(1);
        assertThat(administrators.count()).isEqualTo(1);
    }

    @Test
    void duplicateInsertInsideATransactionKeepsTheTransactionUsable() {
        administrators.insertIfAbsent(AdministratorTestData.active("Um", "dup@example.test"));

        Long count =
                new TransactionTemplate(transactionManager)
                        .execute(
                                status -> {
                                    boolean inserted =
                                            administrators.insertIfAbsent(
                                                    AdministratorTestData.active(
                                                            "Dois", "dup@example.test"));
                                    assertThat(inserted).isFalse();
                                    return administrators.count();
                                });

        assertThat(count).isEqualTo(1L);
    }

    @Test
    void pendingActivationCodeHashStaysUniqueAcrossAdministrators() {
        administrators.insertIfAbsent(
                AdministratorTestData.pending("Um", "one@example.test", "e".repeat(64)));

        assertThatThrownBy(
                        () ->
                                administrators.insertIfAbsent(
                                        AdministratorTestData.pending(
                                                "Dois", "two@example.test", "e".repeat(64))))
                .isInstanceOf(DataIntegrityViolationException.class);
        // depois de usado (hash apagado), o mesmo valor pode voltar a existir
        jdbc.update("update administrator set activation_code_hash = null");
        assertThat(
                        administrators.insertIfAbsent(
                                AdministratorTestData.pending(
                                        "Dois", "two@example.test", "e".repeat(64))))
                .isTrue();
    }
}
