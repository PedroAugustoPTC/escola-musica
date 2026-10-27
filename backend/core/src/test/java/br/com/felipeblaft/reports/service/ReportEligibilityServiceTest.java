package br.com.felipeblaft.reports.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.felipeblaft.reports.domain.common.DomainException;
import br.com.felipeblaft.reports.domain.common.ErrorCode;
import br.com.felipeblaft.reports.domain.registry.Student;
import br.com.felipeblaft.reports.domain.registry.Teacher;
import br.com.felipeblaft.reports.doubles.AccessScenario;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Professor e aluno precisam estar ativos para iniciar relatório novo (R-23, FR-022). */
class ReportEligibilityServiceTest {

    private final AccessScenario c = new AccessScenario();
    private final ReportEligibilityService service =
            new ReportEligibilityService(c.teachers, c.students);
    private final Teacher clara = c.teacher("Clara Souza", "clara@example.test", "K7QM2XPA");
    private final Student matteo = c.student(clara, "Matteo Mafra", "Piano");

    private static ErrorCode codeOf(Throwable thrown) {
        return ((DomainException) thrown).code();
    }

    @Test
    void activeTeacherAndStudentPass() {
        assertThatCode(() -> service.requireActiveTeacher(clara.id())).doesNotThrowAnyException();
        assertThatCode(() -> service.requireActiveTeacher(clara)).doesNotThrowAnyException();
        assertThatCode(() -> service.requireActiveStudent(matteo.id())).doesNotThrowAnyException();
    }

    @Test
    void inactiveTeacherIsRefusedByIdAndByObject() {
        clara.inactivate(c.clock.now());
        c.teachers.save(clara);

        assertThatThrownBy(() -> service.requireActiveTeacher(clara.id()))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.TEACHER_INACTIVE));
        assertThatThrownBy(() -> service.requireActiveTeacher(clara))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.TEACHER_INACTIVE));
    }

    @Test
    void unknownIdsAreNotFound() {
        assertThatThrownBy(() -> service.requireActiveTeacher(UUID.randomUUID()))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));
        assertThatThrownBy(() -> service.requireActiveStudent(UUID.randomUUID()))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));
    }

    @Test
    void inactiveStudentIsRefused() {
        matteo.inactivate(c.clock.now());
        c.students.save(matteo);

        assertThatThrownBy(() -> service.requireActiveStudent(matteo.id()))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.STUDENT_INACTIVE));
    }
}
