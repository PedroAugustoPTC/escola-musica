package br.com.felipeblaft.reports.usecase.registry;

import br.com.felipeblaft.reports.domain.access.PermissionMatrix;
import br.com.felipeblaft.reports.domain.common.BusinessParameters;
import br.com.felipeblaft.reports.domain.common.Secret;
import br.com.felipeblaft.reports.domain.registry.Student;
import br.com.felipeblaft.reports.domain.registry.Teacher;
import br.com.felipeblaft.reports.doubles.FakeAuthorshipRecorder;
import br.com.felipeblaft.reports.doubles.FakeDataProtection;
import br.com.felipeblaft.reports.doubles.FixedClock;
import br.com.felipeblaft.reports.doubles.InMemoryAccessAttemptRepository;
import br.com.felipeblaft.reports.doubles.InMemoryAssignmentRepository;
import br.com.felipeblaft.reports.doubles.InMemoryStudentRepository;
import br.com.felipeblaft.reports.doubles.InMemoryTeacherRepository;
import br.com.felipeblaft.reports.port.StudentRepository;
import br.com.felipeblaft.reports.service.AccessAttemptsControlService;
import br.com.felipeblaft.reports.service.ActorAuthorizationService;
import br.com.felipeblaft.reports.service.AuthorshipService;
import br.com.felipeblaft.reports.service.StudentAccessService;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Monta os casos de uso de alunos sobre dublês em memória, com relógio, proteção e autoria
 * controláveis. Hoje, no fuso da escola, é 2026-09-21.
 */
final class RegistryScenario {

    static final String CPF_A = "529.982.247-25";
    static final String CPF_B = "123.456.789-09";
    static final String CPF_C = "111.444.777-35";
    static final String CPF_D = "390.533.447-05";
    static final String CORRELATION = "req-1";

    final FixedClock clock = new FixedClock();
    final FakeDataProtection protection = new FakeDataProtection();
    final FakeAuthorshipRecorder recorder = new FakeAuthorshipRecorder();
    final InMemoryTeacherRepository teachers = new InMemoryTeacherRepository();
    final InMemoryAssignmentRepository assignments = new InMemoryAssignmentRepository(teachers);
    final InMemoryStudentRepository students;
    final InMemoryAccessAttemptRepository attemptsRepository =
            new InMemoryAccessAttemptRepository();
    final AccessAttemptsControlService attempts =
            new AccessAttemptsControlService(
                    attemptsRepository, protection, clock, BusinessParameters.defaults());
    final AuthorshipService authorship = new AuthorshipService(recorder, clock);
    final ActorAuthorizationService authorization =
            new ActorAuthorizationService(PermissionMatrix.standard());

    final RegisterStudentUseCase register;
    final UpdateStudentUseCase update;
    final ListStudentsUseCase list;
    final GetStudentUseCase get;

    RegistryScenario() {
        this(null);
    }

    /** {@code override} troca o repositório de alunos (para simular corrida de gravação). */
    RegistryScenario(
            java.util.function.Function<InMemoryAssignmentRepository, InMemoryStudentRepository>
                    override) {
        students =
                override == null
                        ? new InMemoryStudentRepository(assignments)
                        : override.apply(assignments);
        StudentAccessService access = new StudentAccessService(students);
        register =
                new RegisterStudentUseCase(
                        authorization,
                        students,
                        assignments,
                        teachers,
                        protection,
                        attempts,
                        authorship,
                        clock);
        update =
                new UpdateStudentUseCase(
                        authorization,
                        access,
                        students,
                        assignments,
                        teachers,
                        protection,
                        attempts,
                        authorship,
                        clock);
        list = new ListStudentsUseCase(authorization, access, assignments, teachers, clock);
        get =
                new GetStudentUseCase(
                        authorization, access, assignments, teachers, authorship, clock);
    }

    StudentRepository studentRepository() {
        return students;
    }

    Teacher teacher(String name, boolean active) {
        Teacher teacher =
                Teacher.create(
                        name,
                        name.toLowerCase(java.util.Locale.ROOT).replace(' ', '.') + "@school.com",
                        active,
                        clock.now());
        teachers.save(teacher);
        return teacher;
    }

    Teacher teacher(String name) {
        return teacher(name, true);
    }

    /** Aluno já cadastrado e associado aos professores dados. */
    Student student(String name, String birth, String cpf, String instrument, Teacher... owners) {
        Student student =
                Student.create(
                        name,
                        LocalDate.parse(birth),
                        protection.encryptCpf(
                                br.com.felipeblaft.reports.domain.registry.Cpf.of(cpf)),
                        protection.hashCpf(br.com.felipeblaft.reports.domain.registry.Cpf.of(cpf)),
                        null,
                        instrument,
                        clock.now());
        students.save(student);
        for (Teacher owner : owners) {
            assignments.assign(owner.id(), student.id());
        }
        return student;
    }

    static StudentInput adult(String name, String cpf) {
        return new StudentInput(name, "1990-03-04", new Secret(cpf), null, null);
    }

    static StudentInput minor(String name, String cpf) {
        return new StudentInput(
                name,
                "2014-05-10",
                new Secret(cpf),
                new GuardianInput("Maria Silva", "(11) 98765-4321", "Maria@Example.com"),
                "Piano");
    }

    static RegisterStudentUseCase.Command registerCommand(StudentInput input, UUID... teacherIds) {
        return new RegisterStudentUseCase.Command(
                input, java.util.List.of(teacherIds), CORRELATION);
    }

    static UpdateStudentUseCase.Command updateCommand(UUID studentId, StudentInput input) {
        return new UpdateStudentUseCase.Command(studentId, input, CORRELATION);
    }
}
