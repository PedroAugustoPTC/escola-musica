package br.com.felipeblaft.reports.usecase.registry;

import br.com.felipeblaft.reports.domain.access.Actor;
import br.com.felipeblaft.reports.domain.access.AuthorshipTargetType;
import br.com.felipeblaft.reports.domain.access.Operation;
import br.com.felipeblaft.reports.domain.access.Permission;
import br.com.felipeblaft.reports.domain.common.DomainException;
import br.com.felipeblaft.reports.domain.common.ErrorCode;
import br.com.felipeblaft.reports.domain.registry.Cpf;
import br.com.felipeblaft.reports.domain.registry.Guardian;
import br.com.felipeblaft.reports.domain.registry.Student;
import br.com.felipeblaft.reports.domain.registry.StudentData;
import br.com.felipeblaft.reports.domain.registry.Teacher;
import br.com.felipeblaft.reports.domain.registry.TeacherStudent;
import br.com.felipeblaft.reports.port.AssignmentRepository;
import br.com.felipeblaft.reports.port.ClockPort;
import br.com.felipeblaft.reports.port.DataProtectionPort;
import br.com.felipeblaft.reports.port.StudentRepository;
import br.com.felipeblaft.reports.port.TeacherRepository;
import br.com.felipeblaft.reports.service.AccessAttemptsControlService;
import br.com.felipeblaft.reports.service.ActorAuthorizationService;
import br.com.felipeblaft.reports.service.AuthorshipService;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Cadastra um aluno ({@code STUDENT_REGISTER}) pelo administrador (com zero ou mais professores
 * ativos), pela carga inicial (ator de sistema) ou pelo professor (associado só a ele). Valida tudo
 * no núcleo antes de gravar qualquer coisa, grava o CPF só cifrado e trata a duplicidade de CPF: ao
 * administrador indica o nome do aluno existente, nunca o CPF; ao professor, só um código neutro
 * (ramo do professor: tarefa T149).
 */
public class RegisterStudentUseCase {

    /** {@code teacherIds} só vale para o administrador; o professor se associa a si mesmo. */
    public record Command(StudentInput input, List<UUID> teacherIds, String correlationId) {

        @Override
        public String toString() {
            return "RegisterStudentUseCase.Command";
        }
    }

    private final ActorAuthorizationService authorization;
    private final StudentRepository students;
    private final AssignmentRepository assignments;
    private final TeacherRepository teachers;
    private final DataProtectionPort protection;
    private final AuthorshipService authorship;
    private final ClockPort clock;
    private final GetStudentUseCase.Views views;

    public RegisterStudentUseCase(
            ActorAuthorizationService authorization,
            StudentRepository students,
            AssignmentRepository assignments,
            TeacherRepository teachers,
            DataProtectionPort protection,
            AccessAttemptsControlService attempts,
            AuthorshipService authorship,
            ClockPort clock) {
        this.authorization = Objects.requireNonNull(authorization);
        this.students = Objects.requireNonNull(students);
        this.assignments = Objects.requireNonNull(assignments);
        this.teachers = Objects.requireNonNull(teachers);
        this.protection = Objects.requireNonNull(protection);
        this.authorship = Objects.requireNonNull(authorship);
        this.clock = Objects.requireNonNull(clock);
        this.views = new GetStudentUseCase.Views(assignments, teachers, clock);
    }

    public StudentView run(Actor actor, Command command) {
        Permission permission = authorization.require(actor, Operation.STUDENT_REGISTER);
        if (permission == Permission.ALLOW_OWN_STUDENTS) {
            throw new UnsupportedOperationException("RegisterStudentUseCase: ramo do professor");
        }
        Instant now = clock.now();
        StudentData data = validate(command.input(), clock.todayInSchoolTimezone());
        List<Teacher> owners = activeTeachers(command.teacherIds());

        Cpf cpf = data.cpf();
        String hash = protection.hashCpf(cpf);
        Optional<Student> existing = students.searchByCpfHash(hash);
        if (existing.isPresent()) {
            throw duplicate(existing.get());
        }
        Student student = create(data, protection.encryptCpf(cpf), hash, now);
        if (!students.insertIfAbsent(student)) {
            throw duplicate(
                    students.searchByCpfHash(hash)
                            .orElseThrow(() -> new DomainException(ErrorCode.OPERATION_NOT_SAVED)));
        }
        for (Teacher owner : owners) {
            assignments.save(TeacherStudent.create(owner.id(), student.id(), now));
        }
        authorship.record(
                actor,
                Operation.STUDENT_REGISTER,
                AuthorshipTargetType.STUDENT,
                student.id(),
                command.correlationId());
        return views.of(student, false);
    }

    /** Valida o texto bruto do cadastro; o CPF é obrigatório. */
    static StudentData validate(StudentInput input, LocalDate today) {
        GuardianInput guardian = input.guardian();
        return StudentData.of(
                input.name(),
                input.birthDate(),
                input.cpf() == null ? null : input.cpf().value(),
                input.instrument(),
                guardian == null ? null : guardian.name(),
                guardian == null ? null : guardian.phone(),
                guardian == null ? null : guardian.email(),
                today);
    }

    private static Student create(StudentData data, byte[] encryptedCpf, String hash, Instant now) {
        Guardian guardian = data.guardian();
        return Student.create(
                data.name().value(),
                data.birthDate().value(),
                encryptedCpf,
                hash,
                guardian == null ? null : guardian.name().value(),
                guardian == null || guardian.phone() == null ? null : guardian.phone().digits(),
                guardian == null || guardian.email() == null ? null : guardian.email().value(),
                data.instrument() == null ? null : data.instrument().value(),
                now);
    }

    /** Professores escolhidos pelo administrador: existentes e ativos, sem repetição. */
    private List<Teacher> activeTeachers(List<UUID> ids) {
        Set<UUID> distinct = ids == null ? Set.of() : new LinkedHashSet<>(ids);
        return distinct.stream()
                .map(
                        id -> {
                            Teacher teacher =
                                    teachers.searchById(id)
                                            .orElseThrow(RegisterStudentUseCase::unknownTeacher);
                            if (!teacher.active()) {
                                throw new DomainException(ErrorCode.TEACHER_INACTIVE);
                            }
                            return teacher;
                        })
                .toList();
    }

    private static DomainException unknownTeacher() {
        return new DomainException(
                ErrorCode.VALIDATION_FAILED,
                Map.of(
                        "fields",
                        List.of(
                                Map.of(
                                        "field",
                                        "teacherIds",
                                        "code",
                                        ErrorCode.TEACHER_NOT_FOUND.name()))));
    }

    /** CPF já usado: ao administrador, o nome do aluno existente (nunca o CPF). */
    static DomainException duplicate(Student existing) {
        return new DomainException(ErrorCode.DUPLICATE_CPF, Map.of("name", existing.name()));
    }
}
