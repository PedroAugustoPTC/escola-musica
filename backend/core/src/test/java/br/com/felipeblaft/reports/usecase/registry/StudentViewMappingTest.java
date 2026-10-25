package br.com.felipeblaft.reports.usecase.registry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.felipeblaft.reports.domain.access.ActionOrigin;
import br.com.felipeblaft.reports.domain.access.Actor;
import br.com.felipeblaft.reports.domain.common.DomainException;
import br.com.felipeblaft.reports.domain.common.ErrorCode;
import br.com.felipeblaft.reports.domain.common.GeneratorId;
import br.com.felipeblaft.reports.domain.registry.Student;
import br.com.felipeblaft.reports.domain.registry.Teacher;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** O aluno devolvido pela situação: responsável parcial ou ausente e ator desconhecido. */
class StudentViewMappingTest {

    private final SituationScenario c = new SituationScenario();
    private final Teacher clara = c.teacher("Clara Souza");

    private Student saved(String guardianName, String phone, String email) {
        Student student =
                new Student(
                        GeneratorId.create(),
                        "Aluno " + UUID.randomUUID(),
                        LocalDate.of(1990, 1, 1),
                        new byte[] {1},
                        "hash-" + UUID.randomUUID(),
                        guardianName,
                        phone,
                        email,
                        "Piano",
                        true,
                        c.clock.now(),
                        c.clock.now());
        c.students.save(student);
        return student;
    }

    private StudentView inactivate(Student student) {
        return c.inactivate.run(
                c.administrator(),
                new InactivateStudentUseCase.Command(
                        student.id(), SituationScenario.correlation()));
    }

    @Test
    void withoutAnyGuardianDataTheViewHasNoGuardian() {
        assertThat(inactivate(saved(null, null, null)).guardian()).isNull();
    }

    @Test
    void anyGuardianFieldIsEnoughToShowTheGuardian() {
        assertThat(inactivate(saved("Responsável", null, null)).guardian()).isNotNull();
        StudentView byPhone = inactivate(saved(null, "11999999999", null));
        assertThat(byPhone.guardian().phone()).isEqualTo("11999999999");
        StudentView byEmail = inactivate(saved(null, null, "resp@example.test"));
        assertThat(byEmail.guardian().email()).isEqualTo("resp@example.test");
        assertThat(byEmail.guardian().toString()).doesNotContain("resp@");
    }

    @Test
    void unknownTeacherActorIsNotFound() {
        Student student = c.student("Matteo Mafra", clara);
        Actor ghost = Actor.teacher(UUID.randomUUID(), ActionOrigin.WEB);

        assertThatThrownBy(
                        () ->
                                c.inactivate.run(
                                        ghost,
                                        new InactivateStudentUseCase.Command(
                                                student.id(), SituationScenario.correlation())))
                .isInstanceOf(DomainException.class)
                .satisfies(
                        e ->
                                assertThat(((DomainException) e).code())
                                        .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));
    }

    @Test
    void teachersListedForTheAdministratorSkipUnknownIds() {
        Student student = c.student("Matteo Mafra", clara);
        c.assignments.assign(UUID.randomUUID(), student.id());

        StudentView view = inactivate(student);

        assertThat(view.teachers())
                .extracting(StudentView.TeacherRef::name)
                .containsExactly("Clara Souza");
    }
}
