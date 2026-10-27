package br.com.felipeblaft.reports.usecase.seed;

import br.com.felipeblaft.reports.domain.access.ActionOrigin;
import br.com.felipeblaft.reports.domain.access.Actor;
import br.com.felipeblaft.reports.domain.common.DomainException;
import br.com.felipeblaft.reports.domain.common.ErrorCode;
import br.com.felipeblaft.reports.domain.common.Secret;
import br.com.felipeblaft.reports.domain.registry.Cpf;
import br.com.felipeblaft.reports.domain.registry.Student;
import br.com.felipeblaft.reports.domain.registry.Teacher;
import br.com.felipeblaft.reports.port.AssignmentRepository;
import br.com.felipeblaft.reports.port.DataProtectionPort;
import br.com.felipeblaft.reports.port.StudentRepository;
import br.com.felipeblaft.reports.port.TeacherRepository;
import br.com.felipeblaft.reports.port.TransactionPort;
import br.com.felipeblaft.reports.usecase.registry.AssignTeacherToStudentUseCase;
import br.com.felipeblaft.reports.usecase.registry.GuardianInput;
import br.com.felipeblaft.reports.usecase.registry.RegisterStudentUseCase;
import br.com.felipeblaft.reports.usecase.registry.StudentInput;
import br.com.felipeblaft.reports.usecase.registry.StudentView;
import br.com.felipeblaft.reports.usecase.registry.UpdateStudentUseCase;
import br.com.felipeblaft.reports.usecase.seed.SeedFile.SeedStudent;
import br.com.felipeblaft.reports.usecase.seed.SeedFile.SeedTeacher;
import br.com.felipeblaft.reports.usecase.seed.SeedResult.Count;
import br.com.felipeblaft.reports.usecase.seed.SeedResult.IssuedCode;
import br.com.felipeblaft.reports.usecase.seed.SeedResult.SeedError;
import br.com.felipeblaft.reports.usecase.teacher.InactivateTeacherUseCase;
import br.com.felipeblaft.reports.usecase.teacher.ReactivateTeacherUseCase;
import br.com.felipeblaft.reports.usecase.teacher.RegisterTeacherUseCase;
import br.com.felipeblaft.reports.usecase.teacher.TeacherWithCode;
import br.com.felipeblaft.reports.usecase.teacher.UpdateTeacherUseCase;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Carga inicial (FR-044, SC-011): <strong>não tem regra própria</strong>. Cada linha do arquivo
 * vira uma chamada aos mesmos casos de uso da administração, com o ator de sistema de origem {@code
 * INITIAL_LOAD}; validação, unicidade, autoria e código de acesso são deles. Aqui ficam só a
 * leitura do arquivo, a ordem das chamadas, a idempotência (professor por e-mail, aluno por resumo
 * do CPF; nunca apaga), a contagem e o tudo ou nada: o arquivo inteiro roda numa transação que é
 * desfeita se qualquer linha for recusada. Em {@code --simulate} a transação é sempre desfeita, com
 * as mesmas contagens e erros da execução real. Os erros citam seção, índice e campo, nunca o
 * valor.
 */
public class LoadInitialRegistryUseCase {

    /** Identificador de correlação da carga (formato aceito pela autoria). */
    static final String CORRELATION_ID = "load-seed";

    private final RegisterTeacherUseCase registerTeacher;
    private final UpdateTeacherUseCase updateTeacher;
    private final InactivateTeacherUseCase inactivateTeacher;
    private final ReactivateTeacherUseCase reactivateTeacher;
    private final RegisterStudentUseCase registerStudent;
    private final UpdateStudentUseCase updateStudent;
    private final AssignTeacherToStudentUseCase assign;
    private final TeacherRepository teachers;
    private final StudentRepository students;
    private final AssignmentRepository assignments;
    private final DataProtectionPort protection;
    private final TransactionPort transaction;

    @SuppressWarnings("java:S107")
    public LoadInitialRegistryUseCase(
            RegisterTeacherUseCase registerTeacher,
            UpdateTeacherUseCase updateTeacher,
            InactivateTeacherUseCase inactivateTeacher,
            ReactivateTeacherUseCase reactivateTeacher,
            RegisterStudentUseCase registerStudent,
            UpdateStudentUseCase updateStudent,
            AssignTeacherToStudentUseCase assign,
            TeacherRepository teachers,
            StudentRepository students,
            AssignmentRepository assignments,
            DataProtectionPort protection,
            TransactionPort transaction) {
        this.registerTeacher = Objects.requireNonNull(registerTeacher);
        this.updateTeacher = Objects.requireNonNull(updateTeacher);
        this.inactivateTeacher = Objects.requireNonNull(inactivateTeacher);
        this.reactivateTeacher = Objects.requireNonNull(reactivateTeacher);
        this.registerStudent = Objects.requireNonNull(registerStudent);
        this.updateStudent = Objects.requireNonNull(updateStudent);
        this.assign = Objects.requireNonNull(assign);
        this.teachers = Objects.requireNonNull(teachers);
        this.students = Objects.requireNonNull(students);
        this.assignments = Objects.requireNonNull(assignments);
        this.protection = Objects.requireNonNull(protection);
        this.transaction = Objects.requireNonNull(transaction);
    }

    /** Desfaz a transação da carga recusada e leva o resultado para fora dela. */
    private static final class RejectedLoadException extends RuntimeException {

        private final transient SeedResult result;

        RejectedLoadException(SeedResult result) {
            super("carga recusada", null, false, false);
            this.result = result;
        }
    }

    public SeedResult run(SeedFile file, boolean simulate) {
        if (simulate) {
            return transaction.runAndRollBack(() -> execute(file, true));
        }
        try {
            return transaction.run(
                    () -> {
                        SeedResult result = execute(file, false);
                        if (!result.success()) {
                            throw new RejectedLoadException(result);
                        }
                        return result;
                    });
        } catch (RejectedLoadException rejected) {
            return rejected.result;
        }
    }

    private SeedResult execute(SeedFile file, boolean simulate) {
        Load load = new Load(Actor.system(ActionOrigin.INITIAL_LOAD));
        loadTeachers(load, file.teachers());
        loadStudents(load, file.students());
        if (!load.errors.isEmpty()) {
            return new SeedResult(
                    simulate, new Count(0, 0, 0), new Count(0, 0, 0), 0, load.errors, List.of());
        }
        return new SeedResult(
                simulate,
                load.teacherCount.toCount(),
                load.studentCount.toCount(),
                load.createdAssignments,
                List.of(),
                simulate ? List.of() : load.codes);
    }

    // ---- Professores

    private void loadTeachers(Load load, List<SeedTeacher> list) {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < list.size(); i++) {
            SeedTeacher seed = list.get(i);
            String email = Teacher.normalizeEmail(seed.email());
            boolean hasEmail = email != null && !email.isEmpty();
            if (hasEmail && !seen.add(email)) {
                load.errors.add(
                        new SeedError("teachers", i, "email", ErrorCode.DUPLICATE_EMAIL_IN_FILE));
                continue;
            }
            try {
                UUID id = loadTeacher(load, seed, hasEmail ? email : null);
                if (hasEmail) {
                    load.teacherIds.put(email, id);
                }
            } catch (DomainException refusal) {
                report(load, "teachers", i, refusal);
                if (hasEmail) {
                    load.failedTeachers.add(email);
                }
            }
        }
    }

    private UUID loadTeacher(Load load, SeedTeacher seed, String email) {
        boolean wantActive = seed.active() == null || seed.active();
        Optional<Teacher> existing =
                email == null ? Optional.empty() : teachers.searchByEmail(email);
        if (existing.isEmpty()) {
            TeacherWithCode created =
                    registerTeacher.run(
                            load.actor,
                            new RegisterTeacherUseCase.Command(
                                    seed.name(), seed.email(), CORRELATION_ID));
            UUID id = created.teacher().id();
            load.teacherCount.created++;
            if (wantActive) {
                load.codes.add(issued(created));
            } else {
                inactivateTeacher.run(
                        load.actor, new InactivateTeacherUseCase.Command(id, CORRELATION_ID));
            }
            return id;
        }
        Teacher teacher = existing.get();
        UUID id = teacher.id();
        TeacherSnapshot before = new TeacherSnapshot(teacher.name(), teacher.active());
        String name = seed.name() == null ? null : seed.name().strip();
        if (name != null && !name.isEmpty() && !name.equals(before.name())) {
            updateTeacher.run(
                    load.actor,
                    new UpdateTeacherUseCase.Command(
                            id, seed.name(), teacher.email(), CORRELATION_ID));
        }
        if (!wantActive && before.active()) {
            inactivateTeacher.run(
                    load.actor, new InactivateTeacherUseCase.Command(id, CORRELATION_ID));
        } else if (wantActive && !before.active()) {
            load.codes.add(
                    issued(
                            reactivateTeacher.run(
                                    load.actor,
                                    new ReactivateTeacherUseCase.Command(id, CORRELATION_ID))));
        }
        TeacherSnapshot after =
                teachers.searchById(id)
                        .map(t -> new TeacherSnapshot(t.name(), t.active()))
                        .orElse(before);
        if (after.equals(before)) {
            load.teacherCount.unchanged++;
        } else {
            load.teacherCount.updated++;
        }
        return id;
    }

    private static IssuedCode issued(TeacherWithCode created) {
        return new IssuedCode(
                created.teacher().email(), created.accessCode().value(), created.validUntil());
    }

    private record TeacherSnapshot(String name, boolean active) {

        @Override
        public String toString() {
            return "TeacherSnapshot";
        }
    }

    // ---- Alunos

    private void loadStudents(Load load, List<SeedStudent> list) {
        Set<Cpf> seenCpfs = new HashSet<>();
        for (int i = 0; i < list.size(); i++) {
            SeedStudent seed = list.get(i);
            Cpf cpf = parseCpf(seed.cpf());
            if (cpf != null && !seenCpfs.add(cpf)) {
                load.errors.add(
                        new SeedError("students", i, "cpf", ErrorCode.DUPLICATE_CPF_IN_FILE));
                continue;
            }
            Map<Integer, UUID> teacherIds = resolveTeachers(load, i, seed.teachers());
            UUID studentId;
            try {
                studentId = loadStudent(load, seed, cpf);
            } catch (DomainException refusal) {
                report(load, "students", i, refusal);
                continue;
            }
            assignTeachers(load, i, studentId, teacherIds);
        }
    }

    /** Posição na lista do aluno → id do professor; professor desconhecido vira erro. */
    private Map<Integer, UUID> resolveTeachers(Load load, int index, List<String> emails) {
        Map<Integer, UUID> ids = new LinkedHashMap<>();
        Set<String> seen = new HashSet<>();
        for (int j = 0; j < emails.size(); j++) {
            String email = Teacher.normalizeEmail(emails.get(j));
            if (email == null || email.isEmpty()) {
                load.errors.add(teacherNotFound(index, j));
            } else if (seen.add(email) && !load.failedTeachers.contains(email)) {
                UUID id = load.teacherIds.get(email);
                if (id == null) {
                    id = teachers.searchByEmail(email).map(Teacher::id).orElse(null);
                }
                if (id == null) {
                    load.errors.add(teacherNotFound(index, j));
                } else {
                    ids.put(j, id);
                }
            }
        }
        return ids;
    }

    private static SeedError teacherNotFound(int index, int position) {
        return new SeedError(
                "students", index, "teachers[" + position + "]", ErrorCode.TEACHER_NOT_FOUND);
    }

    private UUID loadStudent(Load load, SeedStudent seed, Cpf cpf) {
        GuardianInput guardian = guardian(seed);
        Optional<Student> existing =
                cpf == null ? Optional.empty() : students.searchByCpfHash(protection.hashCpf(cpf));
        if (existing.isEmpty()) {
            StudentInput input =
                    new StudentInput(
                            seed.name(),
                            seed.birthDate(),
                            seed.cpf() == null ? null : new Secret(seed.cpf()),
                            guardian,
                            seed.instrument());
            StudentView created =
                    registerStudent.run(
                            load.actor,
                            new RegisterStudentUseCase.Command(input, List.of(), CORRELATION_ID));
            load.studentCount.created++;
            return created.id();
        }
        Student student = existing.get();
        UUID id = student.id();
        StudentSnapshot before = StudentSnapshot.of(student);
        StudentInput input =
                new StudentInput(seed.name(), seed.birthDate(), null, guardian, seed.instrument());
        updateStudent.run(load.actor, new UpdateStudentUseCase.Command(id, input, CORRELATION_ID));
        StudentSnapshot after = students.searchById(id).map(StudentSnapshot::of).orElse(before);
        if (after.equals(before)) {
            load.studentCount.unchanged++;
        } else {
            load.studentCount.updated++;
        }
        return id;
    }

    private void assignTeachers(Load load, int index, UUID studentId, Map<Integer, UUID> ids) {
        for (Map.Entry<Integer, UUID> entry : ids.entrySet()) {
            UUID teacherId = entry.getValue();
            boolean existed = assignments.exists(teacherId, studentId);
            try {
                assign.run(
                        load.actor,
                        new AssignTeacherToStudentUseCase.Command(
                                studentId, teacherId, CORRELATION_ID));
                if (!existed) {
                    load.createdAssignments++;
                }
            } catch (DomainException refusal) {
                load.errors.add(
                        new SeedError(
                                "students",
                                index,
                                "teachers[" + entry.getKey() + "]",
                                refusal.code()));
            }
        }
    }

    private static GuardianInput guardian(SeedStudent seed) {
        if (isBlank(seed.guardianName())
                && isBlank(seed.guardianPhone())
                && isBlank(seed.guardianEmail())) {
            return null;
        }
        return new GuardianInput(seed.guardianName(), seed.guardianPhone(), seed.guardianEmail());
    }

    private static boolean isBlank(String text) {
        return text == null || text.isBlank();
    }

    private static Cpf parseCpf(String text) {
        try {
            return Cpf.of(text);
        } catch (DomainException invalid) {
            return null;
        }
    }

    /** Estado persistido que a carga compara antes e depois, para contar só o que mudou. */
    private record StudentSnapshot(
            String name,
            LocalDate birthDate,
            String guardianName,
            String guardianPhone,
            String guardianEmail,
            String instrument,
            boolean active) {

        @Override
        public String toString() {
            return "StudentSnapshot";
        }

        static StudentSnapshot of(Student student) {
            return new StudentSnapshot(
                    student.name(),
                    student.birthDate(),
                    student.guardianName(),
                    student.guardianPhone(),
                    student.guardianEmail(),
                    student.instrument(),
                    student.active());
        }
    }

    // ---- Erros

    /** Converte a recusa de um caso de uso em erros por seção, índice e campo (sem valores). */
    private static void report(Load load, String section, int index, DomainException refusal) {
        Object fields = refusal.details().get("fields");
        if (refusal.code() == ErrorCode.VALIDATION_FAILED && fields instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> entry) {
                    load.errors.add(
                            new SeedError(
                                    section,
                                    index,
                                    String.valueOf(entry.get("field")),
                                    codeOf(entry.get("code"))));
                }
            }
            return;
        }
        load.errors.add(new SeedError(section, index, fieldOf(refusal.code()), refusal.code()));
    }

    private static ErrorCode codeOf(Object name) {
        try {
            return ErrorCode.valueOf(String.valueOf(name));
        } catch (IllegalArgumentException unknown) {
            return ErrorCode.VALIDATION_FAILED;
        }
    }

    private static String fieldOf(ErrorCode code) {
        return switch (code) {
            case DUPLICATE_EMAIL -> "email";
            case DUPLICATE_CPF, CPF_UNAVAILABLE -> "cpf";
            case TEACHER_NOT_FOUND -> "teachers";
            default -> "record";
        };
    }

    /** Estado de uma execução. */
    private static final class Load {

        final Actor actor;
        final List<SeedError> errors = new ArrayList<>();
        final List<IssuedCode> codes = new ArrayList<>();
        final Map<String, UUID> teacherIds = new HashMap<>();
        final Set<String> failedTeachers = new HashSet<>();
        final Counter teacherCount = new Counter();
        final Counter studentCount = new Counter();
        int createdAssignments;

        Load(Actor actor) {
            this.actor = actor;
        }
    }

    private static final class Counter {
        int created;
        int updated;
        int unchanged;

        Count toCount() {
            return new Count(created, updated, unchanged);
        }
    }
}
