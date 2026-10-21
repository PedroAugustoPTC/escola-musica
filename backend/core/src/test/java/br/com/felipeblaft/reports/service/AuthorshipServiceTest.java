package br.com.felipeblaft.reports.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.felipeblaft.reports.domain.access.ActionOrigin;
import br.com.felipeblaft.reports.domain.access.Actor;
import br.com.felipeblaft.reports.domain.access.AuthorshipRecord;
import br.com.felipeblaft.reports.domain.access.AuthorshipResult;
import br.com.felipeblaft.reports.domain.access.AuthorshipTargetType;
import br.com.felipeblaft.reports.domain.access.Operation;
import br.com.felipeblaft.reports.domain.access.Role;
import br.com.felipeblaft.reports.doubles.FixedClock;
import br.com.felipeblaft.reports.port.AuthorshipRecorderPort;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * FR-040: o serviço monta o registro e escolhe o modo de gravação (mesma transação no sucesso;
 * independente em falha, negativa e bloqueio). Nunca grava CPF, e-mail desconhecido, senha nem
 * código: só identificadores, códigos de erro e o resumo da origem.
 */
class AuthorshipServiceTest {

    /** Dublê local: guarda cada registro com o modo em que foi gravado. */
    private static final class Recorder implements AuthorshipRecorderPort {
        final List<AuthorshipRecord> sameTransaction = new ArrayList<>();
        final List<AuthorshipRecord> independent = new ArrayList<>();

        @Override
        public void record(AuthorshipRecord record) {
            sameTransaction.add(record);
        }

        @Override
        public void recordIndependent(AuthorshipRecord record) {
            independent.add(record);
        }
    }

    private static final String ORIGIN_HASH = "0123456789abcdef".repeat(4);

    private final Recorder recorder = new Recorder();
    private final FixedClock clock = new FixedClock();
    private final AuthorshipService service = new AuthorshipService(recorder, clock);
    private final UUID administratorId = UUID.randomUUID();
    private final UUID target = UUID.randomUUID();

    @Test
    void successOfAnOperationIsRecordedInTheSameTransaction() {
        service.record(
                Actor.administrator(administratorId, false),
                Operation.TEACHER_REGISTER,
                AuthorshipTargetType.TEACHER,
                target,
                "req-1");

        assertThat(recorder.independent).isEmpty();
        AuthorshipRecord saved = recorder.sameTransaction.get(0);
        assertThat(saved.id()).isNotNull();
        assertThat(saved.occurredAt()).isEqualTo(clock.now());
        assertThat(saved.authorId()).isEqualTo(administratorId);
        assertThat(saved.authorRole()).isEqualTo(Role.ADMINISTRATOR);
        assertThat(saved.origin()).isEqualTo(ActionOrigin.WEB);
        assertThat(saved.operation()).isEqualTo("TEACHER_REGISTER");
        assertThat(saved.targetType()).isEqualTo(AuthorshipTargetType.TEACHER);
        assertThat(saved.targetId()).isEqualTo(target);
        assertThat(saved.result()).isEqualTo(AuthorshipResult.SUCCESS);
        assertThat(saved.errorCode()).isNull();
        assertThat(saved.originHash()).isNull();
        assertThat(saved.correlationId()).isEqualTo("req-1");
    }

    @Test
    void failuresDenialsAndBlocksAreRecordedIndependently() {
        Actor teacher = Actor.teacher(UUID.randomUUID(), ActionOrigin.TELEGRAM);
        for (AuthorshipResult result :
                List.of(
                        AuthorshipResult.FAILURE,
                        AuthorshipResult.DENIED,
                        AuthorshipResult.BLOCKED)) {
            service.record(
                    teacher,
                    AuthorshipRecord.CPF_REFUSED_OTHER_STUDENT,
                    AuthorshipTargetType.STUDENT,
                    null,
                    result,
                    "CPF_UNAVAILABLE",
                    "4711");
        }

        assertThat(recorder.sameTransaction).isEmpty();
        assertThat(recorder.independent).hasSize(3);
        AuthorshipRecord denied = recorder.independent.get(1);
        assertThat(denied.authorRole()).isEqualTo(Role.TEACHER);
        assertThat(denied.origin()).isEqualTo(ActionOrigin.TELEGRAM);
        assertThat(denied.errorCode()).isEqualTo("CPF_UNAVAILABLE");
        assertThat(denied.originHash()).isNull();
    }

    @Test
    void systemActorHasNoAuthorAndKeepsItsOrigin() {
        service.record(
                Actor.system(ActionOrigin.INITIAL_LOAD),
                Operation.STUDENT_REGISTER,
                AuthorshipTargetType.STUDENT,
                target,
                "load-1");

        AuthorshipRecord saved = recorder.sameTransaction.get(0);
        assertThat(saved.authorId()).isNull();
        assertThat(saved.authorRole()).isNull();
        assertThat(saved.origin()).isEqualTo(ActionOrigin.INITIAL_LOAD);
    }

    @Test
    void successfulSignInKeepsTheOriginHashInTheSameTransaction() {
        service.recordAuthentication(
                ActionOrigin.WEB,
                Role.ADMINISTRATOR,
                administratorId,
                Operation.ADMIN_SIGN_IN.name(),
                AuthorshipResult.SUCCESS,
                null,
                ORIGIN_HASH,
                "req-2");

        assertThat(recorder.independent).isEmpty();
        AuthorshipRecord saved = recorder.sameTransaction.get(0);
        assertThat(saved.authorId()).isEqualTo(administratorId);
        assertThat(saved.originHash()).isEqualTo(ORIGIN_HASH);
        assertThat(saved.operation()).isEqualTo("ADMIN_SIGN_IN");
    }

    @Test
    void failedSignInWithUnknownEmailHasNoAuthorAndNeverTheTypedEmail() {
        service.recordAuthentication(
                ActionOrigin.WEB,
                null,
                null,
                Operation.ADMIN_SIGN_IN.name(),
                AuthorshipResult.FAILURE,
                "UNKNOWN_EMAIL",
                ORIGIN_HASH,
                "req-3");

        assertThat(recorder.sameTransaction).isEmpty();
        AuthorshipRecord saved = recorder.independent.get(0);
        assertThat(saved.authorId()).isNull();
        assertThat(saved.authorRole()).isNull();
        assertThat(saved.errorCode()).isEqualTo("UNKNOWN_EMAIL");
        assertThat(saved.toString()).doesNotContain("@");
    }

    @Test
    void freeTextIsNeverAcceptedAsErrorCodeOrCorrelation() {
        Actor administrator = Actor.administrator(administratorId, false);
        assertThatThrownBy(
                        () ->
                                service.record(
                                        administrator,
                                        "ADMIN_SIGN_IN",
                                        null,
                                        null,
                                        AuthorshipResult.FAILURE,
                                        "marta@school.com",
                                        "req-4"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(
                        () ->
                                service.record(
                                        administrator,
                                        "ADMIN_SIGN_IN",
                                        null,
                                        null,
                                        AuthorshipResult.FAILURE,
                                        "CODE",
                                        "marta@school.com"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(
                        () ->
                                service.record(
                                        administrator,
                                        "ADMIN_SIGN_IN",
                                        null,
                                        null,
                                        AuthorshipResult.FAILURE,
                                        "CODE",
                                        null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(
                        () ->
                                service.record(
                                        administrator,
                                        " ",
                                        null,
                                        null,
                                        AuthorshipResult.SUCCESS,
                                        null,
                                        "req-5"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(recorder.sameTransaction).isEmpty();
        assertThat(recorder.independent).isEmpty();
    }

    @Test
    void personalDataIsNeverAcceptedAsCorrelationOrOriginHash() {
        Actor administrator = Actor.administrator(administratorId, false);
        // CPF cru, CPF mascarado, telefone e código de ativação de 12 caracteres
        for (String leaked :
                List.of("52998224725", "529.982.247-25", "11987654321", "KQMXPTRWHNCV")) {
            assertThatThrownBy(
                            () ->
                                    service.record(
                                            administrator,
                                            Operation.TEACHER_UPDATE,
                                            AuthorshipTargetType.TEACHER,
                                            target,
                                            leaked))
                    .as(leaked)
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageNotContaining(leaked);
            assertThatThrownBy(
                            () ->
                                    service.recordAuthentication(
                                            ActionOrigin.WEB,
                                            null,
                                            null,
                                            "ADMIN_SIGN_IN",
                                            AuthorshipResult.FAILURE,
                                            "UNKNOWN_EMAIL",
                                            leaked,
                                            "req-9"))
                    .as(leaked)
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageNotContaining(leaked);
            assertThatThrownBy(
                            () ->
                                    service.recordAuthentication(
                                            ActionOrigin.WEB,
                                            null,
                                            null,
                                            "ADMIN_SIGN_IN",
                                            AuthorshipResult.FAILURE,
                                            "UNKNOWN_EMAIL",
                                            ORIGIN_HASH,
                                            leaked))
                    .as(leaked)
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageNotContaining(leaked);
        }
        assertThat(recorder.sameTransaction).isEmpty();
        assertThat(recorder.independent).isEmpty();
    }

    @Test
    void uuidAndTelegramUpdateIdAreValidCorrelations() {
        Actor administrator = Actor.administrator(administratorId, false);
        String uuid = UUID.randomUUID().toString();

        service.record(
                administrator,
                Operation.TEACHER_UPDATE,
                AuthorshipTargetType.TEACHER,
                target,
                uuid);
        service.record(
                administrator,
                Operation.TEACHER_UPDATE,
                AuthorshipTargetType.TEACHER,
                target,
                "987654321");

        assertThat(recorder.sameTransaction)
                .extracting(AuthorshipRecord::correlationId)
                .containsExactly(uuid, "987654321");
    }

    @Test
    void failureWithoutErrorCodeIsAccepted() {
        service.record(
                Actor.administrator(administratorId, false),
                "TEACHER_UPDATE",
                AuthorshipTargetType.TEACHER,
                target,
                AuthorshipResult.FAILURE,
                null,
                "req-8");

        assertThat(recorder.independent.get(0).errorCode()).isNull();
    }

    @Test
    void successWithAnErrorCodeIsRejected() {
        assertThatThrownBy(
                        () ->
                                service.record(
                                        Actor.administrator(administratorId, false),
                                        "TEACHER_UPDATE",
                                        AuthorshipTargetType.TEACHER,
                                        target,
                                        AuthorshipResult.SUCCESS,
                                        "SOMETHING",
                                        "req-6"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void failureToRecordPropagatesSoTheOperationIsUndone() {
        AuthorshipService failing =
                new AuthorshipService(
                        new AuthorshipRecorderPort() {
                            @Override
                            public void record(AuthorshipRecord record) {
                                throw new IllegalStateException("falha ao gravar");
                            }

                            @Override
                            public void recordIndependent(AuthorshipRecord record) {
                                throw new IllegalStateException("falha ao gravar");
                            }
                        },
                        clock);
        assertThatThrownBy(
                        () ->
                                failing.record(
                                        Actor.administrator(administratorId, false),
                                        Operation.TEACHER_UPDATE,
                                        AuthorshipTargetType.TEACHER,
                                        target,
                                        "req-7"))
                .isInstanceOf(IllegalStateException.class);
    }
}
