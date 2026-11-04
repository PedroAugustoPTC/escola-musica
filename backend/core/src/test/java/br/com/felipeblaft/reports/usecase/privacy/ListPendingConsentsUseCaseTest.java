package br.com.felipeblaft.reports.usecase.privacy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.felipeblaft.reports.domain.common.DomainException;
import br.com.felipeblaft.reports.domain.common.ErrorCode;
import br.com.felipeblaft.reports.domain.common.PageRequest;
import br.com.felipeblaft.reports.domain.common.PageResult;
import br.com.felipeblaft.reports.domain.privacy.ConsentStatus;
import br.com.felipeblaft.reports.domain.registry.Student;
import br.com.felipeblaft.reports.domain.registry.Teacher;
import br.com.felipeblaft.reports.doubles.ActorFixtures;
import br.com.felipeblaft.reports.usecase.privacy.ListPendingConsentsUseCase.Pending;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Pendências: menores ativos sem consentimento válido, com o motivo; vazia em operação normal. */
class ListPendingConsentsUseCaseTest {

    private final ConsentScenario c = new ConsentScenario();
    private final Teacher clara = c.teacher("Clara Souza");

    private PageResult<Pending> pending() {
        return c.pending.run(c.administrator(), PageRequest.first());
    }

    @Test
    void emptyInNormalOperation() {
        Student minor = c.minor("Matteo Mafra", clara);
        c.consentTo(minor);
        c.adult("Adult Souza", clara);

        assertThat(pending().items()).isEmpty();
        assertThat(pending().total()).isZero();
    }

    @Test
    void missingRevokedAndSupersededAreListedWithTheirReason() {
        Student missing = c.minor("Missing Student", clara);
        Student revoked = c.minor("Revoked Student", clara);
        Student superseded = c.minor("Superseded Student", clara);
        c.consentTo(revoked);
        c.consentTo(superseded);
        c.revoke.run(
                c.administrator(),
                new RevokeConsentUseCase.Command(
                        c.consents.findByStudent(revoked.id()).getFirst().id(),
                        LocalDate.of(2026, 9, 10),
                        "MOTHER",
                        "req-1"));
        c.term(2, true);
        Student valid = c.minor("Valid Student", clara);
        c.recording.record(c.administrator(), valid.id(), ConsentScenario.input(2), "req-2");

        assertThat(pending().items())
                .extracting(Pending::studentId, Pending::reason)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple(missing.id(), ConsentStatus.MISSING),
                        org.assertj.core.groups.Tuple.tuple(revoked.id(), ConsentStatus.REVOKED),
                        org.assertj.core.groups.Tuple.tuple(superseded.id(), ConsentStatus.SUPERSEDED));
    }

    @Test
    void adultsAndInactiveStudentsNeverAppear() {
        c.adult("Adult Souza", clara);
        Student inactive = c.minor("Inactive Student", clara);
        inactive.inactivate(c.clock.now());
        c.students.save(inactive);

        assertThat(pending().items()).isEmpty();
    }

    @Test
    void studentWhoTurnsEighteenLeavesTheList() {
        Student almostAdult = c.minor("Almost Adult", clara);
        c.students.save(almostAdult);
        assertThat(pending().items()).extracting(Pending::studentId).contains(almostAdult.id());

        c.clock.define(java.time.Instant.parse("2032-06-01T13:00:00Z"));

        assertThat(pending().items()).isEmpty();
    }

    @Test
    void pagesTheResult() {
        for (int i = 0; i < 3; i++) {
            c.minor("Student " + i, clara);
        }

        PageResult<Pending> page = c.pending.run(c.administrator(), new PageRequest(1, 2));

        assertThat(page.total()).isEqualTo(3);
        assertThat(page.items()).hasSize(1);
    }

    @Test
    void onlyTheAdministratorReadsTheList() {
        assertThatThrownBy(() -> c.pending.run(ActorFixtures.teacherWeb(), PageRequest.first()))
                .satisfies(
                        error ->
                                assertThat(((DomainException) error).code())
                                        .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));
    }

    @Test
    void historyOfOneStudentShowsEachRecordStatus() {
        Student student = c.minor("Matteo Mafra", clara);
        c.consentTo(student);
        c.term(2, true);

        List<ConsentView> history = c.listConsents.run(c.administrator(), student.id());

        assertThat(history).extracting(ConsentView::status).containsExactly(ConsentStatus.SUPERSEDED);
    }
}
