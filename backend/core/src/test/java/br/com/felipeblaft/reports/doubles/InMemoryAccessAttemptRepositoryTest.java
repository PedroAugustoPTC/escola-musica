package br.com.felipeblaft.reports.doubles;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.felipeblaft.reports.domain.access.AccessAttempt;
import br.com.felipeblaft.reports.domain.access.AttemptPolicy;
import br.com.felipeblaft.reports.domain.access.AttemptScope;
import br.com.felipeblaft.reports.domain.common.Channel;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class InMemoryAccessAttemptRepositoryTest {

    private static final Instant T0 = Instant.parse("2026-09-21T13:00:00Z");
    private static final String ORIGIN = "origin-hash-1";
    private static final AttemptPolicy THREE_THEN_LOCK =
            AttemptPolicy.consecutive(3, Duration.ofMinutes(15));
    private static final AttemptPolicy WINDOWED =
            AttemptPolicy.windowed(3, Duration.ofMinutes(15), Duration.ofMinutes(10));

    private final InMemoryAccessAttemptRepository attempts = new InMemoryAccessAttemptRepository();

    private AccessAttempt fail(AttemptScope scope, Instant now, AttemptPolicy policy) {
        return attempts.registerFailure(scope, Channel.WEB, ORIGIN, now, policy);
    }

    @Test
    void searchIsEmptyBeforeAnyFailure() {
        assertThat(attempts.search(AttemptScope.ADMIN_SIGN_IN, Channel.WEB, ORIGIN)).isEmpty();
    }

    @Test
    void firstFailureCreatesTheRecordWithOneAttempt() {
        AccessAttempt attempt = fail(AttemptScope.ADMIN_SIGN_IN, T0, THREE_THEN_LOCK);

        assertThat(attempt.failedAttempts()).isEqualTo(1);
        assertThat(attempt.blockedUntil()).isNull();
        assertThat(attempts.search(AttemptScope.ADMIN_SIGN_IN, Channel.WEB, ORIGIN))
                .contains(attempt);
    }

    @Test
    void reachingTheMaximumBlocksForTheLockDuration() {
        fail(AttemptScope.ADMIN_SIGN_IN, T0, THREE_THEN_LOCK);
        fail(AttemptScope.ADMIN_SIGN_IN, T0.plusSeconds(1), THREE_THEN_LOCK);

        AccessAttempt blocked =
                fail(AttemptScope.ADMIN_SIGN_IN, T0.plusSeconds(2), THREE_THEN_LOCK);

        assertThat(blocked.failedAttempts()).isEqualTo(3);
        assertThat(blocked.blockedUntil())
                .isEqualTo(T0.plusSeconds(2).plus(Duration.ofMinutes(15)));
    }

    @Test
    void eachScopeCountsApart() {
        fail(AttemptScope.ADMIN_SIGN_IN, T0, THREE_THEN_LOCK);
        fail(AttemptScope.ADMIN_SIGN_IN, T0, THREE_THEN_LOCK);

        AccessAttempt activation = fail(AttemptScope.ADMIN_ACTIVATION, T0, THREE_THEN_LOCK);

        assertThat(activation.failedAttempts()).isEqualTo(1);
        assertThat(attempts.search(AttemptScope.ADMIN_SIGN_IN, Channel.WEB, ORIGIN))
                .hasValueSatisfying(a -> assertThat(a.failedAttempts()).isEqualTo(2));
        assertThat(attempts.search(AttemptScope.TEACHER_CODE, Channel.WEB, ORIGIN)).isEmpty();
    }

    @Test
    void eachChannelAndOriginCountApart() {
        fail(AttemptScope.TEACHER_CODE, T0, THREE_THEN_LOCK);

        AccessAttempt otherChannel =
                attempts.registerFailure(
                        AttemptScope.TEACHER_CODE, Channel.TELEGRAM, ORIGIN, T0, THREE_THEN_LOCK);
        AccessAttempt otherOrigin =
                attempts.registerFailure(
                        AttemptScope.TEACHER_CODE, Channel.WEB, "other", T0, THREE_THEN_LOCK);

        assertThat(otherChannel.failedAttempts()).isEqualTo(1);
        assertThat(otherOrigin.failedAttempts()).isEqualTo(1);
    }

    @Test
    void failureDuringAnActiveLockNeitherCountsNorExtendsIt() {
        AccessAttempt first = null;
        for (int i = 0; i < 3; i++) {
            first = fail(AttemptScope.ADMIN_SIGN_IN, T0, THREE_THEN_LOCK);
        }
        Instant lockedUntil = first.blockedUntil();

        AccessAttempt during =
                fail(AttemptScope.ADMIN_SIGN_IN, T0.plus(Duration.ofMinutes(5)), THREE_THEN_LOCK);

        assertThat(during.failedAttempts()).isEqualTo(3);
        assertThat(during.blockedUntil()).isEqualTo(lockedUntil);
    }

    @Test
    void failureAfterTheLockEndedStartsANewCount() {
        for (int i = 0; i < 3; i++) {
            fail(AttemptScope.ADMIN_SIGN_IN, T0, THREE_THEN_LOCK);
        }

        AccessAttempt after =
                fail(AttemptScope.ADMIN_SIGN_IN, T0.plus(Duration.ofMinutes(16)), THREE_THEN_LOCK);

        assertThat(after.failedAttempts()).isEqualTo(1);
        assertThat(after.blockedUntil()).isNull();
    }

    @Test
    void windowedPolicyAccumulatesFailuresInsideTheWindow() {
        fail(AttemptScope.TEACHER_CPF_PROBE, T0, WINDOWED);
        fail(AttemptScope.TEACHER_CPF_PROBE, T0.plus(Duration.ofMinutes(4)), WINDOWED);

        AccessAttempt third =
                fail(AttemptScope.TEACHER_CPF_PROBE, T0.plus(Duration.ofMinutes(9)), WINDOWED);

        assertThat(third.failedAttempts()).isEqualTo(3);
        assertThat(third.blockedUntil()).isNotNull();
    }

    @Test
    void windowedPolicyRestartsTheCountWhenTheWindowHasPassed() {
        fail(AttemptScope.TEACHER_CPF_PROBE, T0, WINDOWED);
        fail(AttemptScope.TEACHER_CPF_PROBE, T0.plus(Duration.ofMinutes(4)), WINDOWED);

        Instant late = T0.plus(Duration.ofMinutes(11));
        AccessAttempt restarted = fail(AttemptScope.TEACHER_CPF_PROBE, late, WINDOWED);

        assertThat(restarted.failedAttempts()).isEqualTo(1);
        assertThat(attempts.windowStartOf(AttemptScope.TEACHER_CPF_PROBE, Channel.WEB, ORIGIN))
                .contains(late);
    }

    @Test
    void windowStartIsTheFirstFailureOfTheWindow() {
        fail(AttemptScope.TEACHER_CPF_PROBE, T0, WINDOWED);
        fail(AttemptScope.TEACHER_CPF_PROBE, T0.plusSeconds(30), WINDOWED);

        assertThat(attempts.windowStartOf(AttemptScope.TEACHER_CPF_PROBE, Channel.WEB, ORIGIN))
                .contains(T0);
    }

    @Test
    void consecutivePolicyHasNoWindow() {
        fail(AttemptScope.ADMIN_SIGN_IN, T0, THREE_THEN_LOCK);

        assertThat(attempts.windowStartOf(AttemptScope.ADMIN_SIGN_IN, Channel.WEB, ORIGIN))
                .isEmpty();
    }

    @Test
    void successResetsTheCountWhenThereIsNoActiveLock() {
        fail(AttemptScope.ADMIN_SIGN_IN, T0, THREE_THEN_LOCK);
        fail(AttemptScope.ADMIN_SIGN_IN, T0, THREE_THEN_LOCK);

        attempts.confirmSuccess(AttemptScope.ADMIN_SIGN_IN, Channel.WEB, ORIGIN, T0.plusSeconds(5));

        assertThat(attempts.search(AttemptScope.ADMIN_SIGN_IN, Channel.WEB, ORIGIN))
                .hasValueSatisfying(a -> assertThat(a.failedAttempts()).isZero());
    }

    @Test
    void successDoesNotClearAnActiveLock() {
        for (int i = 0; i < 3; i++) {
            fail(AttemptScope.ADMIN_SIGN_IN, T0, THREE_THEN_LOCK);
        }

        attempts.confirmSuccess(
                AttemptScope.ADMIN_SIGN_IN, Channel.WEB, ORIGIN, T0.plus(Duration.ofMinutes(1)));

        assertThat(attempts.search(AttemptScope.ADMIN_SIGN_IN, Channel.WEB, ORIGIN))
                .hasValueSatisfying(
                        a -> {
                            assertThat(a.failedAttempts()).isEqualTo(3);
                            assertThat(a.blockedUntil()).isNotNull();
                        });
    }

    @Test
    void successAfterTheLockEndedResetsTheRecord() {
        for (int i = 0; i < 3; i++) {
            fail(AttemptScope.ADMIN_SIGN_IN, T0, THREE_THEN_LOCK);
        }

        attempts.confirmSuccess(
                AttemptScope.ADMIN_SIGN_IN, Channel.WEB, ORIGIN, T0.plus(Duration.ofMinutes(16)));

        assertThat(attempts.search(AttemptScope.ADMIN_SIGN_IN, Channel.WEB, ORIGIN))
                .hasValueSatisfying(
                        a -> {
                            assertThat(a.failedAttempts()).isZero();
                            assertThat(a.blockedUntil()).isNull();
                        });
    }

    @Test
    void successResetsTheWindowToo() {
        fail(AttemptScope.TEACHER_CPF_PROBE, T0, WINDOWED);

        attempts.confirmSuccess(
                AttemptScope.TEACHER_CPF_PROBE, Channel.WEB, ORIGIN, T0.plusSeconds(5));

        assertThat(attempts.windowStartOf(AttemptScope.TEACHER_CPF_PROBE, Channel.WEB, ORIGIN))
                .isEmpty();
    }

    @Test
    void successForUnknownOriginDoesNothing() {
        attempts.confirmSuccess(AttemptScope.ADMIN_SIGN_IN, Channel.WEB, ORIGIN, T0);

        assertThat(attempts.all()).isEmpty();
    }

    @Test
    void savedLegacyRecordBelongsToTheTeacherCodeScope() {
        AccessAttempt attempt = AccessAttempt.create(Channel.TELEGRAM, ORIGIN, T0);
        attempts.save(attempt);

        assertThat(attempts.search(AttemptScope.TEACHER_CODE, Channel.TELEGRAM, ORIGIN))
                .contains(attempt);
        assertThat(attempts.search(AttemptScope.ADMIN_SIGN_IN, Channel.TELEGRAM, ORIGIN)).isEmpty();
    }

    @Test
    void savingAgainReplacesTheSameRecordAndKeepsItsScope() {
        AccessAttempt created = fail(AttemptScope.ADMIN_SIGN_IN, T0, THREE_THEN_LOCK);

        created.reset(T0.plusSeconds(1));
        attempts.save(created);

        assertThat(attempts.all()).hasSize(1);
        assertThat(attempts.search(AttemptScope.ADMIN_SIGN_IN, Channel.WEB, ORIGIN))
                .hasValueSatisfying(a -> assertThat(a.failedAttempts()).isZero());
    }
}
