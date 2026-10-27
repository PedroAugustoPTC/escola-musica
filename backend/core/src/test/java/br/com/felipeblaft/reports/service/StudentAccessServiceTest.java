package br.com.felipeblaft.reports.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.felipeblaft.reports.domain.access.ActionOrigin;
import br.com.felipeblaft.reports.domain.access.Actor;
import br.com.felipeblaft.reports.domain.common.DomainException;
import br.com.felipeblaft.reports.domain.common.ErrorCode;
import br.com.felipeblaft.reports.domain.registry.Student;
import br.com.felipeblaft.reports.domain.registry.StudentScope;
import br.com.felipeblaft.reports.domain.registry.StudentStatusFilter;
import br.com.felipeblaft.reports.port.StudentRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** R-14: o escopo do professor é aplicado na consulta, numa só leitura; alheio ≡ inexistente. */
class StudentAccessServiceTest {

    /** Dublê local: devolve o aluno preparado e guarda cada consulta com o seu escopo. */
    private static final class Students implements StudentRepository {
        final List<StudentScope> scopes = new ArrayList<>();
        final List<StudentStatusFilter> filters = new ArrayList<>();
        Optional<Student> visible = Optional.empty();
        List<Student> listed = List.of();

        @Override
        public Optional<Student> searchById(UUID id) {
            throw new AssertionError("o escopo não pode ser contornado");
        }

        @Override
        public Optional<Student> searchByCpfHash(String cpfHash) {
            throw new AssertionError("fora do escopo");
        }

        @Override
        public List<Student> listByTeacher(UUID teacherId) {
            throw new AssertionError("fora do escopo");
        }

        @Override
        public Optional<Student> findVisible(StudentScope scope, UUID id) {
            scopes.add(scope);
            return visible;
        }

        @Override
        public List<Student> list(StudentScope scope, StudentStatusFilter status) {
            scopes.add(scope);
            filters.add(status);
            return listed;
        }

        @Override
        public boolean insertIfAbsent(Student student) {
            throw new AssertionError("fora do escopo");
        }

        @Override
        public void save(Student student) {
            throw new AssertionError("fora do escopo");
        }
    }

    private final Students students = new Students();
    private final StudentAccessService service = new StudentAccessService(students);
    private final UUID teacherId = UUID.randomUUID();
    private final Actor teacher = Actor.teacher(teacherId, ActionOrigin.WEB);
    private final Student student =
            Student.create(
                    "Matteo",
                    LocalDate.of(2014, 5, 10),
                    new byte[] {1},
                    "hash",
                    null,
                    null,
                    Instant.parse("2026-10-20T12:00:00Z"));

    @Test
    void administratorSeesTheWholeSchool() {
        assertThat(service.scopeOf(Actor.administrator(UUID.randomUUID(), false)))
                .isEqualTo(StudentScope.all());
    }

    @Test
    void startupSystemActorHasNoScopeAndIsDeniedAsNotFound() {
        // negar por padrão: só a carga inicial tem escopo total; a inicialização não consulta aluno
        Actor system = Actor.system(ActionOrigin.SYSTEM_STARTUP);
        assertThatThrownBy(() -> service.scopeOf(system))
                .isInstanceOf(DomainException.class)
                .satisfies(
                        e ->
                                assertThat(((DomainException) e).code())
                                        .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));
        assertThatThrownBy(() -> service.findVisible(system, UUID.randomUUID()))
                .isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> service.list(system, StudentStatusFilter.ALL))
                .isInstanceOf(DomainException.class);
        assertThat(students.scopes).isEmpty();
    }

    @Test
    void initialLoadActorSeesTheWholeSchool() {
        Actor load = Actor.system(ActionOrigin.INITIAL_LOAD);
        students.visible = Optional.of(student);
        students.listed = List.of(student);

        assertThat(service.scopeOf(load)).isEqualTo(StudentScope.all());
        assertThat(service.findVisible(load, student.id())).isSameAs(student);
        assertThat(service.list(load, StudentStatusFilter.ALL)).containsExactly(student);

        assertThat(students.scopes).containsOnly(StudentScope.all());
    }

    @Test
    void teacherSeesOnlyOwnStudentsInBothChannels() {
        assertThat(service.scopeOf(teacher)).isEqualTo(StudentScope.ofTeacher(teacherId));
        assertThat(service.scopeOf(Actor.teacher(teacherId, ActionOrigin.TELEGRAM)))
                .isEqualTo(StudentScope.ofTeacher(teacherId));
    }

    @Test
    void findVisibleAppliesTheScopeInASingleQuery() {
        students.visible = Optional.of(student);

        assertThat(service.findVisible(teacher, student.id())).isSameAs(student);

        assertThat(students.scopes).containsExactly(StudentScope.ofTeacher(teacherId));
    }

    @Test
    void anotherTeachersStudentAndAMissingOneAreIndistinguishable() {
        // o repositório responde igual (vazio) nos dois casos: uma consulta, o mesmo erro
        for (int attempt = 0; attempt < 2; attempt++) {
            assertThatThrownBy(() -> service.findVisible(teacher, UUID.randomUUID()))
                    .isInstanceOfSatisfying(
                            DomainException.class,
                            e -> {
                                assertThat(e.code()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
                                assertThat(e.details()).isEmpty();
                            });
        }
        assertThat(students.scopes).hasSize(2);
    }

    @Test
    void listPassesScopeAndStatus() {
        students.listed = List.of(student);

        List<Student> result = service.list(teacher, StudentStatusFilter.INACTIVE);

        assertThat(result).containsExactly(student);
        assertThat(students.scopes).containsExactly(StudentScope.ofTeacher(teacherId));
        assertThat(students.filters).containsExactly(StudentStatusFilter.INACTIVE);
    }

    @Test
    void administratorListsTheWholeSchool() {
        service.list(Actor.administrator(UUID.randomUUID(), false), StudentStatusFilter.ALL);
        assertThat(students.scopes).containsExactly(StudentScope.all());
    }

    @Test
    void rejectsNullArguments() {
        assertThatThrownBy(() -> new StudentAccessService(null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> service.scopeOf(null)).isInstanceOf(NullPointerException.class);
    }
}
