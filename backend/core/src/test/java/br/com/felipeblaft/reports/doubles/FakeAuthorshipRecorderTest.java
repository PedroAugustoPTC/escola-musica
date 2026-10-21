package br.com.felipeblaft.reports.doubles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.felipeblaft.reports.domain.access.ActionOrigin;
import br.com.felipeblaft.reports.domain.access.AuthorshipRecord;
import br.com.felipeblaft.reports.domain.access.AuthorshipResult;
import br.com.felipeblaft.reports.domain.access.AuthorshipTargetType;
import br.com.felipeblaft.reports.domain.access.Operation;
import br.com.felipeblaft.reports.domain.access.Role;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class FakeAuthorshipRecorderTest {

    private final FakeAuthorshipRecorder recorder = new FakeAuthorshipRecorder();

    private static AuthorshipRecord record(String operation, AuthorshipResult result) {
        return new AuthorshipRecord(
                UUID.randomUUID(),
                Instant.parse("2026-09-21T13:00:00Z"),
                ActorFixtures.ADMINISTRATOR_ID,
                Role.ADMINISTRATOR,
                ActionOrigin.WEB,
                operation,
                AuthorshipTargetType.TEACHER,
                UUID.randomUUID(),
                result,
                null,
                null,
                "req-1");
    }

    @Test
    void startsEmpty() {
        assertThat(recorder.isEmpty()).isTrue();
        assertThat(recorder.records()).isEmpty();
        assertThat(recorder.last()).isEmpty();
    }

    @Test
    void keepsBothModesInArrivalOrder() {
        AuthorshipRecord first = record("TEACHER_REGISTER", AuthorshipResult.SUCCESS);
        AuthorshipRecord second = record("ATTEMPT_BLOCKED", AuthorshipResult.BLOCKED);
        AuthorshipRecord third = record("TEACHER_UPDATE", AuthorshipResult.SUCCESS);

        recorder.record(first);
        recorder.recordIndependent(second);
        recorder.record(third);

        assertThat(recorder.records()).containsExactly(first, second, third);
        assertThat(recorder.inTransaction()).containsExactly(first, third);
        assertThat(recorder.independent()).containsExactly(second);
        assertThat(recorder.last()).contains(third);
    }

    @Test
    void filtersByOperationNameAndByOperation() {
        AuthorshipRecord register =
                record(Operation.TEACHER_REGISTER.name(), AuthorshipResult.SUCCESS);
        AuthorshipRecord denied = record("AUTHORIZATION_DENIED", AuthorshipResult.DENIED);
        recorder.record(register);
        recorder.recordIndependent(denied);

        assertThat(recorder.ofOperation("AUTHORIZATION_DENIED")).containsExactly(denied);
        assertThat(recorder.ofOperation(Operation.TEACHER_REGISTER)).containsExactly(register);
        assertThat(recorder.ofOperation(Operation.TEACHER_UPDATE)).isEmpty();
    }

    @Test
    void recordFailureAbortsTheOperationAndStoresNothing() {
        IllegalStateException failure = new IllegalStateException("falha ao gravar a autoria");
        recorder.failNextRecordWith(failure);

        assertThatThrownBy(
                        () -> recorder.record(record("TEACHER_REGISTER", AuthorshipResult.SUCCESS)))
                .isSameAs(failure);
        assertThat(recorder.isEmpty()).isTrue();
    }

    @Test
    void recordFailureIsConsumedByTheFirstCallOnly() {
        recorder.failNextRecordWith(new IllegalStateException("failure"));
        AuthorshipRecord ok = record("TEACHER_UPDATE", AuthorshipResult.SUCCESS);

        assertThatThrownBy(() -> recorder.record(ok)).isInstanceOf(IllegalStateException.class);
        recorder.record(ok);

        assertThat(recorder.records()).containsExactly(ok);
    }

    @Test
    void independentRecordIsNotAffectedByTheTransactionFailure() {
        recorder.failNextRecordWith(new IllegalStateException("failure"));
        AuthorshipRecord blocked = record("ATTEMPT_BLOCKED", AuthorshipResult.BLOCKED);

        recorder.recordIndependent(blocked);

        assertThat(recorder.independent()).containsExactly(blocked);
    }

    @Test
    void rollbackDiscardsOnlyTheRecordsOfTheTransaction() {
        AuthorshipRecord inTransaction = record("TEACHER_REGISTER", AuthorshipResult.SUCCESS);
        AuthorshipRecord independent = record("AUTHORIZATION_DENIED", AuthorshipResult.DENIED);
        recorder.record(inTransaction);
        recorder.recordIndependent(independent);

        recorder.rollBackTransaction();

        assertThat(recorder.records()).containsExactly(independent);
        assertThat(recorder.inTransaction()).isEmpty();
    }

    @Test
    void returnedListsAreSnapshots() {
        recorder.record(record("TEACHER_REGISTER", AuthorshipResult.SUCCESS));
        var snapshot = recorder.records();

        recorder.record(record("TEACHER_UPDATE", AuthorshipResult.SUCCESS));

        assertThat(snapshot).hasSize(1);
        assertThatThrownBy(() -> snapshot.add(record("X", AuthorshipResult.FAILURE)))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
