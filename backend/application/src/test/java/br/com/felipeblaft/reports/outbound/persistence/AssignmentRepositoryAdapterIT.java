package br.com.felipeblaft.reports.outbound.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.felipeblaft.reports.domain.registry.Student;
import br.com.felipeblaft.reports.domain.registry.Teacher;
import org.junit.jupiter.api.Test;

/** Operações novas da associação professor-aluno (feature 005, T161). */
class AssignmentRepositoryAdapterIT extends RegistryPersistenceBase {

    @Test
    void deleteRemovesOnlyThePairAndReportsWhetherItExisted() {
        Teacher clara = teacher("Clara Souza", true);
        Teacher bruno = teacher("Bruno Lima", true);
        Student matteo = studentOf(clara, "Matteo Ferreira");
        assign(bruno, matteo);

        assertThat(assignments.delete(clara.id(), matteo.id())).isTrue();

        assertThat(assignments.exists(clara.id(), matteo.id())).isFalse();
        assertThat(assignments.exists(bruno.id(), matteo.id())).isTrue();
        assertThat(assignments.delete(clara.id(), matteo.id())).isFalse();
        assertThat(students.searchById(matteo.id())).isPresent();
    }

    @Test
    void deleteOfNeverExistingPairReturnsFalse() {
        Teacher clara = teacher("Clara Souza", true);
        Student matteo = student("Matteo Ferreira");

        assertThat(assignments.delete(clara.id(), matteo.id())).isFalse();
        assertThat(assignments.delete(clara.id(), java.util.UUID.randomUUID())).isFalse();
    }

    @Test
    void canAssignAgainAfterDelete() {
        Teacher clara = teacher("Clara Souza", true);
        Student matteo = studentOf(clara, "Matteo Ferreira");
        assignments.delete(clara.id(), matteo.id());

        assign(clara, matteo);

        assertThat(assignments.exists(clara.id(), matteo.id())).isTrue();
    }

    @Test
    void listTeachersOfStudentReturnsAllAssignedAndOnlyThem() {
        Teacher clara = teacher("Clara Souza", true);
        Teacher bruno = teacher("Bruno Lima", true);
        Teacher inactive = teacher("Rita Prado", false);
        Student matteo = studentOf(clara, "Matteo Ferreira");
        assign(bruno, matteo);
        assign(inactive, matteo);
        Student sofia = studentOf(bruno, "Sofia Alves");

        assertThat(assignments.listTeachersOfStudent(matteo.id()))
                .containsExactlyInAnyOrder(clara.id(), bruno.id(), inactive.id());
        assertThat(assignments.listTeachersOfStudent(sofia.id())).containsExactly(bruno.id());
        assertThat(assignments.listTeachersOfStudent(student("Sem Professor").id())).isEmpty();
        assertThat(assignments.listTeachersOfStudent(java.util.UUID.randomUUID())).isEmpty();
    }

    @Test
    void countActiveTeachersIgnoresInactiveTeachers() {
        Teacher clara = teacher("Clara Souza", true);
        Teacher bruno = teacher("Bruno Lima", true);
        Teacher inactive = teacher("Rita Prado", false);
        Student matteo = studentOf(clara, "Matteo Ferreira");
        assign(bruno, matteo);
        assign(inactive, matteo);

        assertThat(assignments.countActiveTeachers(matteo.id())).isEqualTo(2);

        inactivate(bruno);
        assertThat(assignments.countActiveTeachers(matteo.id())).isEqualTo(1);

        assignments.delete(clara.id(), matteo.id());
        assertThat(assignments.countActiveTeachers(matteo.id())).isZero();

        reactivate(bruno);
        assertThat(assignments.countActiveTeachers(matteo.id())).isEqualTo(1);
    }

    @Test
    void countActiveTeachersOfStudentWithoutTeacherIsZero() {
        assertThat(assignments.countActiveTeachers(student("Sem Professor").id())).isZero();
        assertThat(assignments.countActiveTeachers(java.util.UUID.randomUUID())).isZero();
    }
}
