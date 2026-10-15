package br.com.felipeblaft.reports.outbound.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.felipeblaft.reports.domain.common.DomainException;
import br.com.felipeblaft.reports.support.IntegrationBase;
import br.com.felipeblaft.reports.support.ScenarioIT;
import br.com.felipeblaft.reports.usecase.access.SignInPortalUseCase;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * A falha de código fica gravada mesmo com o rollback do caso de uso (REQUIRES_NEW, lição 4); zera
 * no sucesso e ao fim do bloqueio; a origem nunca fica em claro.
 */
class AccessAttemptIT extends IntegrationBase {

    @Autowired SignInPortalUseCase signIn;
    @Autowired ScenarioIT scenario;

    private int attempts() {
        return jdbc.queryForObject(
                "select coalesce(max(failed_attempts), 0) from access_attempt", Integer.class);
    }

    @Test
    void failureSavedDespiteRollbackAndSourceOnlyAsHash() {
        scenario.clara();
        assertThatThrownBy(() -> signIn.run("ZZZZZZZZ", "198.51.100.7"))
                .isInstanceOf(DomainException.class);
        assertThat(attempts()).isEqualTo(1);
        String origin = jdbc.queryForObject("select source_hash from access_attempt", String.class);
        assertThat(origin).hasSize(64).doesNotContain("198.51");
        assertThat(jdbc.queryForObject("select count(*) from web_session", Integer.class)).isZero();
    }

    @Test
    void resetsOnSuccessAndAtLockEnd() {
        scenario.clara();
        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> signIn.run("ZZZZZZZZ", "ip"))
                    .isInstanceOf(DomainException.class);
        }
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from access_attempt where blocked_until is not null",
                                Integer.class))
                .isEqualTo(1);
        clock.advance(Duration.ofMinutes(15));
        signIn.run(ScenarioIT.CODE_CLARA, "ip");
        assertThat(attempts()).isZero();
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from access_attempt where blocked_until is not null",
                                Integer.class))
                .isZero();
    }
}
