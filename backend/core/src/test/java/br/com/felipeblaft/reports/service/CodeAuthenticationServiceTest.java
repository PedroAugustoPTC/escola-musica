package br.com.felipeblaft.reports.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.felipeblaft.reports.domain.access.AccessAttempt;
import br.com.felipeblaft.reports.domain.access.CodeRejectedException;
import br.com.felipeblaft.reports.domain.common.Channel;
import br.com.felipeblaft.reports.domain.common.DomainException;
import br.com.felipeblaft.reports.domain.common.ErrorCode;
import br.com.felipeblaft.reports.domain.registry.Teacher;
import br.com.felipeblaft.reports.usecase.teacher.TeacherScenario;
import java.time.Duration;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;

/**
 * Código inexistente, vencido, substituído e de professor inativo têm a mesma resposta e a mesma
 * contagem de tentativas: o bloqueio não é um oráculo (FR-042).
 */
class CodeAuthenticationServiceTest {

    private final TeacherScenario c = new TeacherScenario();
    private final Teacher clara = c.teacher("Clara Souza", "clara@example.test", "K7QM2XPA");

    private static DomainException failure(ThrowingCallable action) {
        return (DomainException)
                assertThatThrownBy(action).isInstanceOf(DomainException.class).actual();
    }

    private int failures(String origin) {
        return c.attemptsRepository.all().stream()
                .mapToInt(AccessAttempt::failedAttempts)
                .max()
                .orElse(0);
    }

    @Test
    void validCodeAuthenticatesAndDoesNotConsumeTheCode() {
        assertThat(c.authentication.authenticate(Channel.WEB, "ip", " k7qm-2xpa ").id())
                .isEqualTo(clara.id());
        assertThat(c.authentication.authenticate(Channel.TELEGRAM, "1", "K7QM2XPA").id())
                .isEqualTo(clara.id());
        assertThat(clara.accessCodeHash()).isEqualTo("code:K7QM2XPA");
    }

    @Test
    void unknownMalformedAndNullCodesAreInvalidAndCounted() {
        for (String attempt : new String[] {"ZZZZZZZZ", "short", null}) {
            DomainException error =
                    failure(() -> c.authentication.authenticate(Channel.WEB, "ip", attempt));
            assertThat(error.code()).isEqualTo(ErrorCode.INVALID_CODE);
            assertThat(error.details()).isEmpty();
        }
        assertThat(failures("ip")).isEqualTo(3);
    }

    @Test
    void expiredCodeIsInvalidAndCountedLikeAnUnknownOne() {
        c.clock.advance(Duration.ofDays(7));

        DomainException error =
                failure(() -> c.authentication.authenticate(Channel.WEB, "ip", "K7QM2XPA"));

        assertThat(error.code()).isEqualTo(ErrorCode.INVALID_CODE);
        assertThat(error.details()).isEmpty();
        assertThat(failures("ip")).isEqualTo(1);
    }

    @Test
    void expiredAndUnknownCodesLeadToTheSameBlockAfterTheSameNumberOfAttempts() {
        TeacherScenario expiredScenario = new TeacherScenario();
        expiredScenario.teacher("Clara Souza", "clara@example.test", "K7QM2XPA");
        expiredScenario.clock.advance(Duration.ofDays(7));
        TeacherScenario unknownScenario = new TeacherScenario();

        for (int i = 0; i < 5; i++) {
            failure(
                    () ->
                            expiredScenario.authentication.authenticate(
                                    Channel.WEB, "ip", "K7QM2XPA"));
            failure(
                    () ->
                            unknownScenario.authentication.authenticate(
                                    Channel.WEB, "ip", "ZZZZZZZZ"));
        }

        DomainException expired =
                failure(
                        () ->
                                expiredScenario.authentication.authenticate(
                                        Channel.WEB, "ip", "K7QM2XPA"));
        DomainException unknown =
                failure(
                        () ->
                                unknownScenario.authentication.authenticate(
                                        Channel.WEB, "ip", "ZZZZZZZZ"));
        assertThat(expired.code()).isEqualTo(ErrorCode.TEMPORARILY_BLOCKED);
        assertThat(unknown.code()).isEqualTo(ErrorCode.TEMPORARILY_BLOCKED);
        assertThat(expired.details()).isEqualTo(unknown.details());
    }

    @Test
    void replacedCodeAndInactiveTeacherAreInvalidAndCounted() {
        c.generator.schedule("NEWC2XPA");
        c.issuance.emit(clara, c.clock.now());
        c.teachers.save(clara);

        assertThat(
                        failure(() -> c.authentication.authenticate(Channel.WEB, "ip", "K7QM2XPA"))
                                .code())
                .isEqualTo(ErrorCode.INVALID_CODE);
        assertThat(c.authentication.authenticate(Channel.WEB, "ip", "NEWC2XPA").id())
                .isEqualTo(clara.id());

        clara.inactivate(c.clock.now());
        assertThat(
                        failure(
                                        () ->
                                                c.authentication.authenticate(
                                                        Channel.WEB, "other", "NEWC2XPA"))
                                .code())
                .isEqualTo(ErrorCode.INVALID_CODE);
        assertThat(failures("other")).isEqualTo(1);
    }

    @Test
    void inactiveTeacherWhoseHashSurvivedIsStillInvalid() {
        clara.updateRegistry(clara.name(), false, c.clock.now());

        assertThat(
                        failure(() -> c.authentication.authenticate(Channel.WEB, "ip", "K7QM2XPA"))
                                .code())
                .isEqualTo(ErrorCode.INVALID_CODE);
        assertThat(failures("ip")).isEqualTo(1);
    }

    @Test
    void lockIsCheckedBeforeTheCodeEvenWithTheRightOne() {
        for (int i = 0; i < 5; i++) {
            failure(() -> c.authentication.authenticate(Channel.WEB, "ip", "ZZZZZZZZ"));
        }

        DomainException error =
                failure(() -> c.authentication.authenticate(Channel.WEB, "ip", "K7QM2XPA"));

        assertThat(error.code()).isEqualTo(ErrorCode.TEMPORARILY_BLOCKED);
        assertThat(error.details()).containsEntry("remainingSeconds", 900L);
    }

    @Test
    void rejectionTellsWhoseCodeItWasOnlyToTheCallerAndNeverInTheResponse() {
        c.clock.advance(Duration.ofDays(7));

        CodeRejectedException expired =
                (CodeRejectedException)
                        failure(() -> c.authentication.authenticate(Channel.WEB, "ip", "K7QM2XPA"));
        CodeRejectedException unknown =
                (CodeRejectedException)
                        failure(() -> c.authentication.authenticate(Channel.WEB, "ip", "ZZZZZZZZ"));

        assertThat(expired.knownTeacherId()).contains(clara.id());
        assertThat(expired.auditCode()).isEqualTo("CODE_EXPIRED");
        assertThat(unknown.knownTeacherId()).isEmpty();
        assertThat(unknown.auditCode()).isEqualTo("UNKNOWN_CODE");
        assertThat(expired.getMessage()).isEqualTo(unknown.getMessage());
        assertThat(expired.toString())
                .doesNotContain("K7QM2XPA")
                .doesNotContain(clara.id().toString());
    }
}
