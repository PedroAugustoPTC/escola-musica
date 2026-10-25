package br.com.felipeblaft.reports.usecase.seed;

import br.com.felipeblaft.reports.domain.access.Actor;
import br.com.felipeblaft.reports.domain.access.AuthorshipTargetType;
import br.com.felipeblaft.reports.domain.access.Operation;
import br.com.felipeblaft.reports.domain.access.PermissionMatrix;
import br.com.felipeblaft.reports.domain.common.BusinessParameters;
import br.com.felipeblaft.reports.domain.common.DomainException;
import br.com.felipeblaft.reports.domain.common.ErrorCode;
import br.com.felipeblaft.reports.domain.common.Secret;
import br.com.felipeblaft.reports.domain.registry.Cpf;
import br.com.felipeblaft.reports.domain.registry.Student;
import br.com.felipeblaft.reports.domain.registry.Teacher;
import br.com.felipeblaft.reports.domain.registry.TeacherStudent;
import br.com.felipeblaft.reports.doubles.FakeAuthorshipRecorder;
import br.com.felipeblaft.reports.doubles.FakeDataProtection;
import br.com.felipeblaft.reports.doubles.FixedAccessCodeGenerator;
import br.com.felipeblaft.reports.doubles.FixedClock;
import br.com.felipeblaft.reports.doubles.InMemoryAccessAttemptRepository;
import br.com.felipeblaft.reports.doubles.InMemoryAdministratorRepository;
import br.com.felipeblaft.reports.doubles.InMemoryAdministratorSessionRepository;
import br.com.felipeblaft.reports.doubles.InMemoryAssignmentRepository;
import br.com.felipeblaft.reports.doubles.InMemoryChannelActionRepository;
import br.com.felipeblaft.reports.doubles.InMemoryStudentRepository;
import br.com.felipeblaft.reports.doubles.InMemoryTeacherRepository;
import br.com.felipeblaft.reports.doubles.InMemoryTelegramLinkRepository;
import br.com.felipeblaft.reports.doubles.InMemoryWebSessionRepository;
import br.com.felipeblaft.reports.port.StudentRegistrationDraftRepository;
import br.com.felipeblaft.reports.port.TransactionPort;
import br.com.felipeblaft.reports.service.AccessAttemptsControlService;
import br.com.felipeblaft.reports.service.AccessCodeIssuanceService;
import br.com.felipeblaft.reports.service.ActorAuthorizationService;
import br.com.felipeblaft.reports.service.AuthorshipService;
import br.com.felipeblaft.reports.service.SessionRevocationService;
import br.com.felipeblaft.reports.service.TelegramLinkTerminationService;
import br.com.felipeblaft.reports.usecase.registry.AssignTeacherToStudentUseCase;
import br.com.felipeblaft.reports.usecase.registry.GuardianInput;
import br.com.felipeblaft.reports.usecase.registry.RegisterStudentUseCase;
import br.com.felipeblaft.reports.usecase.registry.StudentInput;
import br.com.felipeblaft.reports.usecase.registry.StudentView;
import br.com.felipeblaft.reports.usecase.registry.UpdateStudentUseCase;
import br.com.felipeblaft.reports.usecase.teacher.InactivateTeacherUseCase;
import br.com.felipeblaft.reports.usecase.teacher.ReactivateTeacherUseCase;
import br.com.felipeblaft.reports.usecase.teacher.RegisterTeacherUseCase;
import br.com.felipeblaft.reports.usecase.teacher.TeacherView;
import br.com.felipeblaft.reports.usecase.teacher.TeacherWithCode;
import br.com.felipeblaft.reports.usecase.teacher.UpdateTeacherUseCase;
import java.lang.reflect.Proxy;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * Dublês dos casos de uso que a carga inicial orquestra (regra 5 do tasks.md): herdam das classes
 * reais, construídas com colaboradores válidos, e trocam só o {@code run} por um comportamento
 * mínimo sobre os repositórios em memória. Autorizam pela matriz padrão e gravam a autoria, de modo
 * que os testes conferem o ator de sistema e a origem {@code INITIAL_LOAD}. A integração com os
 * casos de uso reais é do Fecho.
 */
final class RegistryUseCaseDoubles {

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    final FixedClock clock = new FixedClock(Instant.parse("2026-09-21T13:00:00Z"));
    final InMemoryTeacherRepository teachers = new InMemoryTeacherRepository();
    final InMemoryAssignmentRepository assignments = new InMemoryAssignmentRepository();
    final InMemoryStudentRepository students = new InMemoryStudentRepository(assignments);
    final FakeDataProtection protection = new FakeDataProtection();
    final FakeAuthorshipRecorder authorshipRecorder = new FakeAuthorshipRecorder();
    final RecordingTransaction transaction = new RecordingTransaction();

    /** Chamadas feitas a cada dublê, na ordem, para os testes conferirem o que foi orquestrado. */
    final List<String> calls = new ArrayList<>();

    /** Quando preenchido, o dublê de associação falha com este erro de domínio. */
    DomainException assignmentFailure;

    /** Quando preenchido, o dublê de cadastro de aluno falha com este erro. */
    RuntimeException studentRegistrationFailure;

    private final ActorAuthorizationService authorization =
            new ActorAuthorizationService(PermissionMatrix.standard());
    private final AuthorshipService authorship = new AuthorshipService(authorshipRecorder, clock);
    private final AccessCodeIssuanceService issuance =
            new AccessCodeIssuanceService(
                    new FixedAccessCodeGenerator("K7QM2XPA"),
                    protection,
                    teachers,
                    BusinessParameters.defaults());
    private int codeSequence;

    final RegisterTeacherUseCase registerTeacher = new FakeRegisterTeacher();
    final UpdateTeacherUseCase updateTeacher = new FakeUpdateTeacher();
    final InactivateTeacherUseCase inactivateTeacher = new FakeInactivateTeacher();
    final ReactivateTeacherUseCase reactivateTeacher = new FakeReactivateTeacher();
    final RegisterStudentUseCase registerStudent = new FakeRegisterStudent();
    final UpdateStudentUseCase updateStudent = new FakeUpdateStudent();
    final AssignTeacherToStudentUseCase assign = new FakeAssign();

    LoadInitialRegistryUseCase loadUseCase() {
        return new LoadInitialRegistryUseCase(
                registerTeacher,
                updateTeacher,
                inactivateTeacher,
                reactivateTeacher,
                registerStudent,
                updateStudent,
                assign,
                teachers,
                students,
                assignments,
                protection,
                transaction);
    }

    /** Transação que só conta o desfecho (o desfazer real é do adaptador). */
    static final class RecordingTransaction implements TransactionPort {
        int committed;
        int rolledBack;

        @Override
        public <T> T run(Supplier<T> excerpt) {
            try {
                T result = excerpt.get();
                committed++;
                return result;
            } catch (RuntimeException failure) {
                rolledBack++;
                throw failure;
            }
        }

        @Override
        public <T> T runAndRollBack(Supplier<T> excerpt) {
            try {
                return excerpt.get();
            } finally {
                rolledBack++;
            }
        }
    }

    // ---- Validação mínima, no lugar do domínio que ainda é esqueleto

    private static DomainException validation(List<String[]> violations) {
        List<Map<String, String>> fields = new ArrayList<>();
        for (String[] violation : violations) {
            fields.add(Map.of("field", violation[0], "code", violation[1]));
        }
        return new DomainException(ErrorCode.VALIDATION_FAILED, Map.of("fields", fields));
    }

    private static boolean validName(String name) {
        return name != null && name.strip().length() >= 2;
    }

    private static boolean validEmail(String email) {
        return email != null && EMAIL.matcher(email.strip()).matches();
    }

    private static String digits(String text) {
        return text == null ? null : text.replaceAll("\\D", "");
    }

    /** Aluno validado como o núcleo real fará (campos e códigos do contrato). */
    private record Valid(
            String name,
            LocalDate birth,
            Cpf cpf,
            String guardianName,
            String guardianPhone,
            String guardianEmail,
            String instrument) {}

    private Valid validate(StudentInput input, StudentView current, boolean requireCpf) {
        List<String[]> violations = new ArrayList<>();
        String name = input.name() != null ? input.name().strip() : current.name();
        if (!validName(name)) {
            violations.add(new String[] {"name", "INVALID_NAME"});
        }
        LocalDate birth = current == null ? null : current.birthDate();
        if (input.birthDate() != null) {
            try {
                birth = LocalDate.parse(input.birthDate().strip());
                if (birth.isAfter(clock.todayInSchoolTimezone())) {
                    violations.add(new String[] {"birthDate", "INVALID_BIRTH_DATE"});
                }
            } catch (DateTimeParseException error) {
                violations.add(new String[] {"birthDate", "INVALID_BIRTH_DATE"});
            }
        } else if (birth == null) {
            violations.add(new String[] {"birthDate", "INVALID_BIRTH_DATE"});
        }
        Cpf cpf = null;
        if (input.cpf() != null && !input.cpf().value().isBlank()) {
            try {
                cpf = Cpf.of(input.cpf().value());
            } catch (DomainException error) {
                violations.add(new String[] {"cpf", "INVALID_CPF"});
            }
        } else if (requireCpf) {
            violations.add(new String[] {"cpf", "INVALID_CPF"});
        }
        GuardianInput guardian = input.guardian();
        String guardianName =
                current == null || current.guardian() == null ? null : current.guardian().name();
        String guardianPhone =
                current == null || current.guardian() == null ? null : current.guardian().phone();
        String guardianEmail =
                current == null || current.guardian() == null ? null : current.guardian().email();
        if (guardian != null) {
            guardianName = blankToNull(guardian.name());
            guardianPhone = digits(blankToNull(guardian.phone()));
            guardianEmail = blankToNull(guardian.email());
            if (guardianPhone != null && !guardianPhone.matches("\\d{10,11}")) {
                violations.add(new String[] {"guardian.phone", "INVALID_PHONE"});
            }
            if (guardianEmail != null && !validEmail(guardianEmail)) {
                violations.add(new String[] {"guardian.email", "INVALID_EMAIL"});
            }
        }
        boolean minor =
                birth != null && Student.requiresGuardian(birth, clock.todayInSchoolTimezone());
        if (minor && (guardianName == null || guardianPhone == null || guardianEmail == null)) {
            violations.add(new String[] {"guardian", "GUARDIAN_REQUIRED"});
        }
        if (!violations.isEmpty()) {
            throw validation(violations);
        }
        return new Valid(
                name,
                birth,
                cpf,
                guardianName,
                guardianPhone,
                guardianEmail,
                input.instrument() != null && !input.instrument().isBlank()
                        ? input.instrument().strip()
                        : current == null ? null : current.instrument());
    }

    private static String blankToNull(String text) {
        return text == null || text.isBlank() ? null : text.strip();
    }

    private static StudentView view(Student student) {
        StudentView.GuardianView guardian =
                student.guardianName() == null
                        ? null
                        : new StudentView.GuardianView(
                                student.guardianName(),
                                student.guardianPhone(),
                                student.guardianEmail());
        return new StudentView(
                student.id(),
                student.name(),
                student.birthDate(),
                0,
                student.instrument(),
                student.active(),
                guardian,
                List.of(),
                null);
    }

    private static TeacherView view(Teacher teacher) {
        return new TeacherView(
                teacher.id(), teacher.name(), teacher.email(), teacher.active(), false);
    }

    private TeacherWithCode withCode(Teacher teacher) {
        codeSequence++;
        return new TeacherWithCode(
                view(teacher),
                new Secret("CODE" + String.format("%04d", codeSequence)),
                clock.now().plus(Duration.ofDays(7)));
    }

    @SuppressWarnings("unchecked")
    private static <T> T unusedCollaborator(Class<T> type) {
        return (T)
                Proxy.newProxyInstance(
                        type.getClassLoader(),
                        new Class<?>[] {type},
                        (proxy, method, arguments) -> {
                            throw new UnsupportedOperationException(type.getSimpleName());
                        });
    }

    // ---- Dublês

    private final class FakeRegisterTeacher extends RegisterTeacherUseCase {

        FakeRegisterTeacher() {
            super(authorization, teachers, issuance, authorship, clock);
        }

        @Override
        public TeacherWithCode run(Actor actor, Command command) {
            calls.add("registerTeacher");
            authorization.require(actor, Operation.TEACHER_REGISTER);
            List<String[]> violations = new ArrayList<>();
            if (!validName(command.name())) {
                violations.add(new String[] {"name", "INVALID_NAME"});
            }
            if (!validEmail(command.email())) {
                violations.add(new String[] {"email", "INVALID_EMAIL"});
            }
            if (!violations.isEmpty()) {
                throw validation(violations);
            }
            String email = Teacher.normalizeEmail(command.email());
            if (teachers.searchByEmail(email).isPresent()) {
                throw new DomainException(ErrorCode.DUPLICATE_EMAIL);
            }
            Teacher teacher = Teacher.create(command.name().strip(), email, true, clock.now());
            teachers.save(teacher);
            authorship.record(
                    actor,
                    Operation.TEACHER_REGISTER,
                    AuthorshipTargetType.TEACHER,
                    teacher.id(),
                    command.correlationId());
            return withCode(teacher);
        }
    }

    private final class FakeUpdateTeacher extends UpdateTeacherUseCase {

        FakeUpdateTeacher() {
            super(authorization, teachers, new InMemoryTelegramLinkRepository(), authorship, clock);
        }

        @Override
        public TeacherView run(Actor actor, Command command) {
            calls.add("updateTeacher");
            authorization.require(actor, Operation.TEACHER_UPDATE);
            Teacher teacher =
                    teachers.searchById(command.teacherId())
                            .orElseThrow(() -> new DomainException(ErrorCode.RESOURCE_NOT_FOUND));
            if (!validName(command.name())) {
                throw validation(List.<String[]>of(new String[] {"name", "INVALID_NAME"}));
            }
            teacher.rename(command.name().strip(), clock.now());
            teachers.save(teacher);
            authorship.record(
                    actor,
                    Operation.TEACHER_UPDATE,
                    AuthorshipTargetType.TEACHER,
                    teacher.id(),
                    command.correlationId());
            return view(teacher);
        }
    }

    private final class FakeInactivateTeacher extends InactivateTeacherUseCase {

        FakeInactivateTeacher() {
            super(
                    authorization,
                    teachers,
                    new SessionRevocationService(
                            new InMemoryWebSessionRepository(),
                            new InMemoryAdministratorSessionRepository(
                                    new InMemoryAdministratorRepository()),
                            clock),
                    new TelegramLinkTerminationService(
                            new InMemoryTelegramLinkRepository(),
                            unusedCollaborator(StudentRegistrationDraftRepository.class),
                            new InMemoryChannelActionRepository(),
                            clock),
                    authorship,
                    clock);
        }

        @Override
        public TeacherView run(Actor actor, Command command) {
            calls.add("inactivateTeacher");
            authorization.require(actor, Operation.TEACHER_INACTIVATE);
            Teacher teacher = teachers.searchById(command.teacherId()).orElseThrow();
            teacher.inactivate(clock.now());
            teachers.save(teacher);
            authorship.record(
                    actor,
                    Operation.TEACHER_INACTIVATE,
                    AuthorshipTargetType.TEACHER,
                    teacher.id(),
                    command.correlationId());
            return view(teacher);
        }
    }

    private final class FakeReactivateTeacher extends ReactivateTeacherUseCase {

        FakeReactivateTeacher() {
            super(authorization, teachers, issuance, authorship, clock);
        }

        @Override
        public TeacherWithCode run(Actor actor, Command command) {
            calls.add("reactivateTeacher");
            authorization.require(actor, Operation.TEACHER_REACTIVATE);
            Teacher teacher = teachers.searchById(command.teacherId()).orElseThrow();
            teacher.reactivate(clock.now());
            teachers.save(teacher);
            authorship.record(
                    actor,
                    Operation.TEACHER_REACTIVATE,
                    AuthorshipTargetType.TEACHER,
                    teacher.id(),
                    command.correlationId());
            return withCode(teacher);
        }
    }

    private final class FakeRegisterStudent extends RegisterStudentUseCase {

        FakeRegisterStudent() {
            super(
                    authorization,
                    students,
                    assignments,
                    teachers,
                    protection,
                    new AccessAttemptsControlService(
                            new InMemoryAccessAttemptRepository(),
                            protection,
                            clock,
                            BusinessParameters.defaults()),
                    authorship,
                    clock);
        }

        @Override
        public StudentView run(Actor actor, Command command) {
            calls.add("registerStudent");
            authorization.require(actor, Operation.STUDENT_REGISTER);
            if (studentRegistrationFailure != null) {
                throw studentRegistrationFailure;
            }
            Valid data = validate(command.input(), null, true);
            String hash = protection.hashCpf(data.cpf());
            if (students.searchByCpfHash(hash).isPresent()) {
                throw new DomainException(ErrorCode.DUPLICATE_CPF);
            }
            Student student =
                    Student.create(
                            data.name(),
                            data.birth(),
                            protection.encryptCpf(data.cpf()),
                            hash,
                            data.guardianName(),
                            data.guardianPhone(),
                            data.guardianEmail(),
                            data.instrument(),
                            clock.now());
            students.save(student);
            authorship.record(
                    actor,
                    Operation.STUDENT_REGISTER,
                    AuthorshipTargetType.STUDENT,
                    student.id(),
                    command.correlationId());
            return view(student);
        }
    }

    private final class FakeUpdateStudent extends UpdateStudentUseCase {

        FakeUpdateStudent() {
            super(
                    authorization,
                    new br.com.felipeblaft.reports.service.StudentAccessService(students),
                    students,
                    protection,
                    new AccessAttemptsControlService(
                            new InMemoryAccessAttemptRepository(),
                            protection,
                            clock,
                            BusinessParameters.defaults()),
                    authorship,
                    clock);
        }

        @Override
        public StudentView run(Actor actor, Command command) {
            calls.add("updateStudent");
            authorization.require(actor, Operation.STUDENT_UPDATE);
            Student current =
                    Optional.of(command.studentId())
                            .flatMap(students::searchById)
                            .orElseThrow(() -> new DomainException(ErrorCode.RESOURCE_NOT_FOUND));
            Valid data = validate(command.input(), view(current), false);
            Student updated =
                    new Student(
                            current.id(),
                            data.name(),
                            data.birth(),
                            current.encryptedCpf(),
                            current.cpfHash(),
                            data.guardianName(),
                            data.guardianPhone(),
                            data.guardianEmail(),
                            data.instrument(),
                            current.active(),
                            current.createdAt(),
                            clock.now());
            students.save(updated);
            authorship.record(
                    actor,
                    Operation.STUDENT_UPDATE,
                    AuthorshipTargetType.STUDENT,
                    updated.id(),
                    command.correlationId());
            return view(updated);
        }
    }

    private final class FakeAssign extends AssignTeacherToStudentUseCase {

        FakeAssign() {
            super(authorization, students, teachers, assignments, authorship, clock);
        }

        @Override
        public void run(Actor actor, Command command) {
            calls.add("assign");
            authorization.require(actor, Operation.ASSIGNMENT_ASSIGN);
            if (assignmentFailure != null) {
                throw assignmentFailure;
            }
            if (!assignments.exists(command.teacherId(), command.studentId())) {
                assignments.save(
                        TeacherStudent.create(
                                command.teacherId(), command.studentId(), clock.now()));
            }
            authorship.record(
                    actor,
                    Operation.ASSIGNMENT_ASSIGN,
                    AuthorshipTargetType.ASSIGNMENT,
                    command.studentId(),
                    command.correlationId());
        }
    }
}
