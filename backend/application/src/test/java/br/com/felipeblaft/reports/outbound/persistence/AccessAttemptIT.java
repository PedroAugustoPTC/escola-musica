package br.com.felipeblaft.reports.outbound.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.felipeblaft.reports.domain.access.AttemptScope;
import br.com.felipeblaft.reports.domain.common.Channel;
import br.com.felipeblaft.reports.domain.common.DomainException;
import br.com.felipeblaft.reports.port.AccessAttemptRepository;
import br.com.felipeblaft.reports.service.AccessAttemptsControlService;
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

    @Autowired AccessAttemptRepository repository;

    @Test
    void legacyRowWithNullWindowStartKeepsCountingAndBlocksUnderTheWindowedPolicy() {
        AccessAttemptsControlService service = AttemptServiceFactory.create(repository, clock);
        String key = "professor-legado";
        // cria a linha pelo serviço e a leva ao estado anterior à V11 (falhas contadas, sem janela)
        service.registerFailure(AttemptScope.TEACHER_CPF_PROBE, Channel.WEB, key);
        jdbc.update(
                "update access_attempt set failed_attempts = 3, window_started_at = null"
                        + " where scope = 'TEACHER_CPF_PROBE'");

        service.registerFailure(AttemptScope.TEACHER_CPF_PROBE, Channel.WEB, key);
        service.registerFailure(AttemptScope.TEACHER_CPF_PROBE, Channel.WEB, key);

        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from access_attempt where scope ="
                                        + " 'TEACHER_CPF_PROBE' and blocked_until is not null"
                                        + " and window_started_at is not null",
                                Integer.class))
                .isEqualTo(1);
        assertThatThrownBy(
                        () -> service.verifyLock(AttemptScope.TEACHER_CPF_PROBE, Channel.WEB, key))
                .isInstanceOf(DomainException.class);
    }

    @Test
    void legacyTeacherCodeRowWithNullWindowStartStillBlocksAtTheLimit() {
        AccessAttemptsControlService service = AttemptServiceFactory.create(repository, clock);
        service.registerFailure(Channel.WEB, "ip-legado");
        jdbc.update(
                "update access_attempt set failed_attempts = 4, window_started_at = null"
                        + " where scope = 'TEACHER_CODE'");

        service.registerFailure(Channel.WEB, "ip-legado");

        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from access_attempt where blocked_until is not"
                                        + " null",
                                Integer.class))
                .isEqualTo(1);
    }
}
