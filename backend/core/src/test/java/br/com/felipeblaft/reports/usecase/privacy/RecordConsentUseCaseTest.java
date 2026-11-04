package br.com.felipeblaft.reports.usecase.privacy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.felipeblaft.reports.domain.access.ActionOrigin;
import br.com.felipeblaft.reports.domain.access.AuthorshipRecord;
import br.com.felipeblaft.reports.domain.access.AuthorshipResult;
import br.com.felipeblaft.reports.domain.access.AuthorshipTargetType;
import br.com.felipeblaft.reports.domain.common.DomainException;
import br.com.felipeblaft.reports.domain.common.ErrorCode;
import br.com.felipeblaft.reports.domain.privacy.ConsentInput;
import br.com.felipeblaft.reports.domain.privacy.ConsentStatus;
import br.com.felipeblaft.reports.domain.registry.Student;
import br.com.felipeblaft.reports.domain.registry.Teacher;
import br.com.felipeblaft.reports.doubles.ActorFixtures;
import br.com.felipeblaft.reports.usecase.privacy.RecordConsentUseCase.Command;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Registrar consentimento: administrador em qualquer aluno, professor nos próprios, carga inicial. */
class RecordConsentUseCaseTest {

    private final ConsentScenario c = new ConsentScenario();
    private final Teacher clara = c.teacher("Clara Souza");
    private final Teacher bruno = c.teacher("Bruno Lima");

    private Command command(Student student) {
        return new Command(student.id(), ConsentScenario.input(1), ConsentScenario.correlation());
    }

    private static ErrorCode codeOf(Throwable thrown) {
        return ((DomainException) thrown).code();
    }

    @Test
    void administratorRecordsForAnyStudentAndTheEventLeavesInTheSameTransaction() {
        c.term(1, false);
        Student student = c.minor("Matteo Mafra", clara);
        UUID adminId = UUID.randomUUID();

        ConsentView view = c.record.run(ActorFixtures.administratorWeb(adminId), command(student));

        assertThat(view.status()).isEqualTo(ConsentStatus.VALID);
        assertThat(view.termVersion()).isEqualTo(1);
        assertThat(c.consents.findByStudent(student.id())).hasSize(1);
        assertThat(c.recorder.inTransaction()).hasSize(1);
        AuthorshipRecord event = c.recorder.last().orElseThrow();
        assertThat(event.operation()).isEqualTo("CONSENT_RECORD");
        assertThat(event.targetType()).isEqualTo(AuthorshipTargetType.CONSENT);
        assertThat(event.targetId()).isEqualTo(view.id());
        assertThat(event.authorId()).isEqualTo(adminId);
        assertThat(event.origin()).isEqualTo(ActionOrigin.WEB);
        assertThat(event.result()).isEqualTo(AuthorshipResult.SUCCESS);
        assertThat(event.readDetail().filterTargetId()).isEqualTo(student.id());
    }

    @Test
    void teacherRecordsForOwnStudentOnBothChannels() {
        c.term(1, false);
        Student student = c.minor("Matteo Mafra", clara);

        c.record.run(ActorFixtures.teacherWeb(clara.id()), command(student));
        c.record.run(ActorFixtures.teacherTelegram(clara.id()), command(student));

        assertThat(c.consents.findByStudent(student.id())).hasSize(2);
        assertThat(c.recorder.ofOperation("CONSENT_RECORD"))
                .extracting(AuthorshipRecord::origin)
                .containsExactly(ActionOrigin.WEB, ActionOrigin.TELEGRAM);
    }

    @Test
    void teacherCannotRecordForSomeoneElsesStudentAndTheRefusalIsIndistinguishable() {
        c.term(1, false);
        Student other = c.minor("Aluno Alheio", bruno);
        UUID unknown = UUID.randomUUID();

        assertThatThrownBy(() -> c.record.run(ActorFixtures.teacherWeb(clara.id()), command(other)))
                .satisfies(error -> assertThat(codeOf(error)).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));
        assertThatThrownBy(
                        () ->
                                c.record.run(
                                        ActorFixtures.teacherWeb(clara.id()),
                                        new Command(unknown, ConsentScenario.input(1), "req-1")))
                .satisfies(error -> assertThat(codeOf(error)).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));

        assertThat(c.consents.size()).isZero();
        assertThat(c.recorder.ofOperation("AUTHORIZATION_DENIED")).hasSize(2);
    }

    @Test
    void initialLoadRecordsWithoutAuthorOrRole() {
        c.term(1, false);
        Student student = c.minor("Matteo Mafra");

        c.record.run(ActorFixtures.initialLoad(), command(student));

        var consent = c.consents.findByStudent(student.id()).getFirst();
        assertThat(consent.recordedOrigin()).isEqualTo(ActionOrigin.INITIAL_LOAD);
        assertThat(consent.recordedByRole()).isNull();
        assertThat(consent.recordedById()).isNull();
        assertThat(c.recorder.ofOperation("CONSENT_RECORD")).hasSize(1);
    }

    @Test
    void invalidInputIsRefusedWithEveryViolationAndNothingIsSaved() {
        c.term(1, false);
        Student student = c.minor("Matteo Mafra", clara);

        assertThatThrownBy(
                        () ->
                                c.record.run(
                                        ActorFixtures.administratorWeb(),
                                        new Command(
                                                student.id(),
                                                new ConsentInput("A", "X", "2099-01-01", 7, "Y"),
                                                "req-1")))
                .satisfies(error -> assertThat(codeOf(error)).isEqualTo(ErrorCode.VALIDATION_FAILED));

        assertThat(c.consents.size()).isZero();
        assertThat(c.recorder.isEmpty()).isTrue();
    }

    @Test
    void everyNewRecordKeepsThePreviousOneAsHistory() {
        c.term(1, false);
        Student student = c.minor("Matteo Mafra", clara);

        c.record.run(c.administrator(), command(student));
        c.record.run(c.administrator(), command(student));

        assertThat(c.consents.findByStudent(student.id())).hasSize(2);
    }
}
