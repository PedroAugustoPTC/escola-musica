package br.com.felipeblaft.reports.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.felipeblaft.reports.domain.access.AccessAttempt;
import br.com.felipeblaft.reports.domain.access.AttemptPolicy;
import br.com.felipeblaft.reports.domain.access.AttemptScope;
import br.com.felipeblaft.reports.domain.common.Channel;
import br.com.felipeblaft.reports.domain.common.DomainException;
import br.com.felipeblaft.reports.domain.common.ErrorCode;
import br.com.felipeblaft.reports.doubles.AccessScenario;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Controle de tentativas por escopo (R-05, R-06, FR-006, FR-024): bloqueio na quinta falha, falha
 * durante o bloqueio não conta nem prorroga, sucesso não zera bloqueio ativo, contagem separada por
 * escopo e janelas da sondagem e da consulta de CPF.
 */
class AccessAttemptsControlServiceScopeTest {

    private static final String ADMIN_ORIGIN = "203.0.113.9";
    private static final String TEACHER_KEY = "11111111-2222-3333-4444-555555555555";

    private final AccessScenario scenario = new AccessScenario();
    private final AccessAttemptsControlService service = scenario.attempts;

    private void failAdmin(int times) {
        for (int i = 0; i < times; i++) {
            service.registerFailure(AttemptScope.ADMIN_SIGN_IN, Channel.WEB, ADMIN_ORIGIN);
        }
    }

    private long remainingSeconds(Runnable verification) {
        try {
            verification.run();
        } catch (DomainException error) {
            return ((Number) error.details().get("remainingSeconds")).longValue();
        }
        return -1;
    }

    @Test
    void fiveFailuresBlockForFifteenMinutes() {
        failAdmin(4);
        assertThatCode(
                        () ->
                                service.verifyLock(
                                        AttemptScope.ADMIN_SIGN_IN, Channel.WEB, ADMIN_ORIGIN))
                .doesNotThrowAnyException();

        AccessAttempt fifth =
                service.registerFailure(AttemptScope.ADMIN_SIGN_IN, Channel.WEB, ADMIN_ORIGIN);

        assertThat(fifth.failedAttempts()).isEqualTo(5);
        assertThat(fifth.blockedUntil())
                .isEqualTo(scenario.clock.now().plus(Duration.ofMinutes(15)));
        assertThatThrownBy(
                        () ->
                                service.verifyLock(
                                        AttemptScope.ADMIN_SIGN_IN, Channel.WEB, ADMIN_ORIGIN))
                .isInstanceOfSatisfying(
                        DomainException.class,
                        error -> {
                            assertThat(error.code()).isEqualTo(ErrorCode.TEMPORARILY_BLOCKED);
                            assertThat(error.details()).isEqualTo(Map.of("remainingSeconds", 900L));
                        });
    }

    @Test
    void failureDuringTheLockNeitherCountsNorExtendsIt() {
        failAdmin(5);
        scenario.clock.advance(Duration.ofMinutes(5));

        AccessAttempt during =
                service.registerFailure(AttemptScope.ADMIN_SIGN_IN, Channel.WEB, ADMIN_ORIGIN);

        assertThat(during.failedAttempts()).isEqualTo(5);
        assertThat(
                        remainingSeconds(
                                () ->
                                        service.verifyLock(
                                                AttemptScope.ADMIN_SIGN_IN,
                                                Channel.WEB,
                                                ADMIN_ORIGIN)))
                .isEqualTo(600L);
    }

    @Test
    void successDoesNotClearAnActiveLockButEndsTheCountOtherwise() {
        failAdmin(2);
        service.confirmSuccess(AttemptScope.ADMIN_SIGN_IN, Channel.WEB, ADMIN_ORIGIN);
        assertThat(
                        scenario.attemptsRepository
                                .search(
                                        AttemptScope.ADMIN_SIGN_IN,
                                        Channel.WEB,
                                        hash(AttemptScope.ADMIN_SIGN_IN, ADMIN_ORIGIN))
                                .orElseThrow()
                                .failedAttempts())
                .isZero();

        failAdmin(5);
        service.confirmSuccess(AttemptScope.ADMIN_SIGN_IN, Channel.WEB, ADMIN_ORIGIN);

        assertThatThrownBy(
                        () ->
                                service.verifyLock(
                                        AttemptScope.ADMIN_SIGN_IN, Channel.WEB, ADMIN_ORIGIN))
                .isInstanceOf(DomainException.class);
    }

    @Test
    void successForAnOriginWithoutFailuresChangesNothing() {
        service.confirmSuccess(AttemptScope.ADMIN_SIGN_IN, Channel.WEB, ADMIN_ORIGIN);

        assertThat(scenario.attemptsRepository.all()).isEmpty();
    }

    @Test
    void countsAreSeparatedByScopeAndRole() {
        failAdmin(5);

        assertThatCode(() -> service.verifyLock(Channel.WEB, ADMIN_ORIGIN))
                .doesNotThrowAnyException();
        assertThatCode(
                        () ->
                                service.verifyLock(
                                        AttemptScope.ADMIN_ACTIVATION, Channel.WEB, ADMIN_ORIGIN))
                .doesNotThrowAnyException();
        service.registerFailure(Channel.WEB, ADMIN_ORIGIN);
        assertThatThrownBy(
                        () ->
                                service.verifyLock(
                                        AttemptScope.ADMIN_SIGN_IN, Channel.WEB, ADMIN_ORIGIN))
                .isInstanceOf(DomainException.class);
        assertThat(
                        scenario.attemptsRepository
                                .search(
                                        AttemptScope.TEACHER_CODE,
                                        Channel.WEB,
                                        scenario.protection.originHash(Channel.WEB, ADMIN_ORIGIN))
                                .orElseThrow()
                                .failedAttempts())
                .isEqualTo(1);
    }

    @Test
    void activationScopeBlocksOnItsOwnWithTheSamePolicy() {
        for (int i = 0; i < 5; i++) {
            service.registerFailure(AttemptScope.ADMIN_ACTIVATION, Channel.WEB, ADMIN_ORIGIN);
        }

        assertThatThrownBy(
                        () ->
                                service.verifyLock(
                                        AttemptScope.ADMIN_ACTIVATION, Channel.WEB, ADMIN_ORIGIN))
                .isInstanceOfSatisfying(
                        DomainException.class,
                        error -> assertThat(error.code()).isEqualTo(ErrorCode.TEMPORARILY_BLOCKED));
        assertThatCode(
                        () ->
                                service.verifyLock(
                                        AttemptScope.ADMIN_SIGN_IN, Channel.WEB, ADMIN_ORIGIN))
                .doesNotThrowAnyException();
    }

    @Test
    void teacherCodeKeepsTheLegacyBehaviourPerChannel() {
        for (int i = 0; i < 5; i++) {
            service.registerFailure(Channel.TELEGRAM, "42");
        }

        assertThatThrownBy(() -> service.verifyLock(Channel.TELEGRAM, "42"))
                .isInstanceOfSatisfying(
                        DomainException.class,
                        error -> assertThat(error.code()).isEqualTo(ErrorCode.TEMPORARILY_BLOCKED));
        assertThatCode(() -> service.verifyLock(Channel.WEB, "42")).doesNotThrowAnyException();

        scenario.clock.advance(Duration.ofMinutes(15));
        assertThatCode(() -> service.verifyLock(Channel.TELEGRAM, "42")).doesNotThrowAnyException();
        service.registerSuccess(Channel.TELEGRAM, "42");
        service.registerFailure(Channel.TELEGRAM, "42");
        assertThat(
                        scenario.attemptsRepository
                                .search(
                                        AttemptScope.TEACHER_CODE,
                                        Channel.TELEGRAM,
                                        scenario.protection.originHash(Channel.TELEGRAM, "42"))
                                .orElseThrow()
                                .failedAttempts())
                .isEqualTo(1);
    }

    @Test
    void nullOriginIsHashedAsEmpty() {
        service.registerFailure(AttemptScope.ADMIN_SIGN_IN, Channel.WEB, null);
        service.registerFailure(AttemptScope.ADMIN_SIGN_IN, Channel.WEB, "");

        assertThat(
                        scenario.attemptsRepository
                                .search(
                                        AttemptScope.ADMIN_SIGN_IN,
                                        Channel.WEB,
                                        hash(AttemptScope.ADMIN_SIGN_IN, ""))
                                .orElseThrow()
                                .failedAttempts())
                .isEqualTo(2);
    }

    @Test
    void cpfProbeBlocksOnTheFifthRefusalInFifteenMinutesWithOperationCode() {
        for (int i = 0; i < 4; i++) {
            scenario.clock.advance(Duration.ofMinutes(2));
            service.registerFailure(AttemptScope.TEACHER_CPF_PROBE, Channel.WEB, TEACHER_KEY);
        }
        assertThatCode(
                        () ->
                                service.verifyLock(
                                        AttemptScope.TEACHER_CPF_PROBE, Channel.WEB, TEACHER_KEY))
                .doesNotThrowAnyException();

        service.registerFailure(AttemptScope.TEACHER_CPF_PROBE, Channel.WEB, TEACHER_KEY);

        assertThatThrownBy(
                        () ->
                                service.verifyLock(
                                        AttemptScope.TEACHER_CPF_PROBE, Channel.WEB, TEACHER_KEY))
                .isInstanceOfSatisfying(
                        DomainException.class,
                        error -> {
                            assertThat(error.code())
                                    .isEqualTo(ErrorCode.OPERATION_TEMPORARILY_BLOCKED);
                            assertThat(error.details()).isEqualTo(Map.of("remainingSeconds", 900L));
                        });
    }

    @Test
    void cpfProbeCountRestartsWhenTheWindowHasPassed() {
        for (int i = 0; i < 4; i++) {
            service.registerFailure(AttemptScope.TEACHER_CPF_PROBE, Channel.WEB, TEACHER_KEY);
        }
        scenario.clock.advance(Duration.ofMinutes(15));

        AccessAttempt restarted =
                service.registerFailure(AttemptScope.TEACHER_CPF_PROBE, Channel.WEB, TEACHER_KEY);

        assertThat(restarted.failedAttempts()).isEqualTo(1);
        assertThat(restarted.blockedUntil()).isNull();
    }

    @Test
    void cpfProbeSuccessDoesNotResetTheWindow() {
        for (int i = 0; i < 4; i++) {
            service.registerFailure(AttemptScope.TEACHER_CPF_PROBE, Channel.WEB, TEACHER_KEY);
        }

        service.confirmSuccess(AttemptScope.TEACHER_CPF_PROBE, Channel.WEB, TEACHER_KEY);
        AccessAttempt fifth =
                service.registerFailure(AttemptScope.TEACHER_CPF_PROBE, Channel.WEB, TEACHER_KEY);

        assertThat(fifth.failedAttempts()).isEqualTo(5);
        assertThat(fifth.blockedUntil()).isNotNull();
    }

    @Test
    void cpfLookupBlocksThirtyLookupsInSixtyMinutesForSixtyMinutes() {
        for (int i = 0; i < 29; i++) {
            scenario.clock.advance(Duration.ofMinutes(1));
            service.registerFailure(AttemptScope.TEACHER_CPF_LOOKUP, Channel.WEB, TEACHER_KEY);
        }
        assertThatCode(
                        () ->
                                service.verifyLock(
                                        AttemptScope.TEACHER_CPF_LOOKUP, Channel.WEB, TEACHER_KEY))
                .doesNotThrowAnyException();

        AccessAttempt thirtieth =
                service.registerFailure(AttemptScope.TEACHER_CPF_LOOKUP, Channel.WEB, TEACHER_KEY);

        assertThat(thirtieth.failedAttempts()).isEqualTo(30);
        assertThat(thirtieth.blockedUntil())
                .isEqualTo(scenario.clock.now().plus(Duration.ofMinutes(60)));
        assertThat(
                        remainingSeconds(
                                () ->
                                        service.verifyLock(
                                                AttemptScope.TEACHER_CPF_LOOKUP,
                                                Channel.WEB,
                                                TEACHER_KEY)))
                .isEqualTo(3600L);
    }

    @Test
    void cpfLookupAndProbeCountApart() {
        for (int i = 0; i < 5; i++) {
            service.registerFailure(AttemptScope.TEACHER_CPF_PROBE, Channel.WEB, TEACHER_KEY);
        }

        assertThatCode(
                        () ->
                                service.verifyLock(
                                        AttemptScope.TEACHER_CPF_LOOKUP, Channel.WEB, TEACHER_KEY))
                .doesNotThrowAnyException();
    }

    @Test
    void lookupCountIsOfTheTeacherNotOfTheOtherTeachers() {
        for (int i = 0; i < 30; i++) {
            service.registerFailure(AttemptScope.TEACHER_CPF_LOOKUP, Channel.WEB, TEACHER_KEY);
        }

        assertThatCode(
                        () ->
                                service.verifyLock(
                                        AttemptScope.TEACHER_CPF_LOOKUP,
                                        Channel.WEB,
                                        "99999999-2222-3333-4444-555555555555"))
                .doesNotThrowAnyException();
    }

    @Test
    void configuredPoliciesOverrideTheDefaultsOfTheirScopeOnly() {
        AccessAttemptsControlService custom =
                new AccessAttemptsControlService(
                        scenario.attemptsRepository,
                        scenario.protection,
                        scenario.clock,
                        scenario.parameters,
                        Map.of(
                                AttemptScope.TEACHER_CPF_PROBE,
                                AttemptPolicy.windowed(
                                        2, Duration.ofMinutes(3), Duration.ofMinutes(10))));

        custom.registerFailure(AttemptScope.TEACHER_CPF_PROBE, Channel.WEB, TEACHER_KEY);
        AccessAttempt second =
                custom.registerFailure(AttemptScope.TEACHER_CPF_PROBE, Channel.WEB, TEACHER_KEY);
        custom.registerFailure(AttemptScope.ADMIN_SIGN_IN, Channel.WEB, ADMIN_ORIGIN);
        AccessAttempt admin =
                custom.registerFailure(AttemptScope.ADMIN_SIGN_IN, Channel.WEB, ADMIN_ORIGIN);

        assertThat(second.blockedUntil())
                .isEqualTo(scenario.clock.now().plus(Duration.ofMinutes(3)));
        assertThat(admin.blockedUntil()).isNull();
        assertThat(AccessAttemptsControlService.defaultPolicies(scenario.parameters))
                .containsKeys(AttemptScope.values());
    }

    private String hash(AttemptScope scope, String key) {
        return scenario.protection.originHash(scope, key);
    }
}
