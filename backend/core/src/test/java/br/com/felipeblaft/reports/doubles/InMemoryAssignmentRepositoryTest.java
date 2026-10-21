package br.com.felipeblaft.reports.doubles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.felipeblaft.reports.domain.registry.Teacher;
import br.com.felipeblaft.reports.domain.registry.TeacherStudent;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class InMemoryAssignmentRepositoryTest {

    private static final Instant NOW = Instant.parse("2026-09-21T13:00:00Z");

    private final InMemoryAssignmentRepository assignments = new InMemoryAssignmentRepository();
    private final UUID clara = UUID.randomUUID();
    private final UUID bruno = UUID.randomUUID();
    private final UUID matteo = UUID.randomUUID();

    @Test
    void deleteRemovesTheAssociationAndReportsIt() {
        assignments.assign(clara, matteo);

        assertThat(assignments.delete(clara, matteo)).isTrue();

        assertThat(assignments.exists(clara, matteo)).isFalse();
    }

    @Test
    void deleteOfNonexistentAssociationReportsFalse() {
        assignments.assign(clara, matteo);

        assertThat(assignments.delete(bruno, matteo)).isFalse();
        assertThat(assignments.exists(clara, matteo)).isTrue();
    }

    @Test
    void deleteTouchesOnlyThePairInformed() {
        assignments.assign(clara, matteo);
        assignments.assign(bruno, matteo);

        assignments.delete(clara, matteo);

        assertThat(assignments.listTeachersOfStudent(matteo)).containsExactly(bruno);
    }

    @Test
    void listsTheTeachersOfTheStudentInAssociationOrder() {
        assignments.assign(clara, matteo);
        assignments.assign(bruno, matteo);
        assignments.assign(bruno, UUID.randomUUID());

        assertThat(assignments.listTeachersOfStudent(matteo)).containsExactly(clara, bruno);
        assertThat(assignments.listTeachersOfStudent(UUID.randomUUID())).isEmpty();
    }

    @Test
    void savingTheSamePairTwiceViolatesUniqueness() {
        assignments.save(TeacherStudent.create(clara, matteo, NOW));

        assertThatThrownBy(() -> assignments.save(TeacherStudent.create(clara, matteo, NOW)))
                .isInstanceOf(IllegalStateException.class);
        assertThat(assignments.all()).hasSize(1);
    }

    @Test
    void withoutTeacherRepositoryEveryAssociatedTeacherCountsAsActive() {
        assignments.assign(clara, matteo);
        assignments.assign(bruno, matteo);

        assertThat(assignments.countActiveTeachers(matteo)).isEqualTo(2);
        assertThat(assignments.countActiveTeachers(UUID.randomUUID())).isZero();
    }

    @Test
    void withTeacherRepositoryOnlyActiveTeachersCount() {
        InMemoryTeacherRepository teachers = new InMemoryTeacherRepository();
        InMemoryAssignmentRepository withTeachers = new InMemoryAssignmentRepository(teachers);
        Teacher active = Teacher.create("Clara", "clara@school.test", true, NOW);
        Teacher inactive = Teacher.create("Bruno", "bruno@school.test", false, NOW);
        teachers.save(active);
        teachers.save(inactive);
        UUID unknown = UUID.randomUUID();
        withTeachers.assign(active.id(), matteo);
        withTeachers.assign(inactive.id(), matteo);
        withTeachers.assign(unknown, matteo);

        assertThat(withTeachers.countActiveTeachers(matteo)).isEqualTo(1);
    }

    @Test
    void inactivatingAfterwardsIsSeenByTheNextCount() {
        InMemoryTeacherRepository teachers = new InMemoryTeacherRepository();
        InMemoryAssignmentRepository withTeachers = new InMemoryAssignmentRepository(teachers);
        Teacher teacher = Teacher.create("Clara", "clara@school.test", true, NOW);
        teachers.save(teacher);
        withTeachers.assign(teacher.id(), matteo);
        assertThat(withTeachers.countActiveTeachers(matteo)).isEqualTo(1);

        teacher.inactivate(NOW);

        assertThat(withTeachers.countActiveTeachers(matteo)).isZero();
    }
}
