package br.com.felipeblaft.reports.usecase.seed;

import br.com.felipeblaft.reports.domain.access.AccessCode;
import br.com.felipeblaft.reports.domain.common.DomainException;
import br.com.felipeblaft.reports.domain.common.ErrorCode;
import br.com.felipeblaft.reports.domain.registry.Cpf;
import br.com.felipeblaft.reports.domain.registry.Student;
import br.com.felipeblaft.reports.domain.registry.Teacher;
import br.com.felipeblaft.reports.domain.registry.TeacherStudent;
import br.com.felipeblaft.reports.port.AssignmentRepository;
import br.com.felipeblaft.reports.port.ClockPort;
import br.com.felipeblaft.reports.port.DataProtectionPort;
import br.com.felipeblaft.reports.port.StudentRepository;
import br.com.felipeblaft.reports.port.TeacherRepository;
import br.com.felipeblaft.reports.service.AccessCodeIssuanceService;
import br.com.felipeblaft.reports.usecase.seed.SeedFile.SeedStudent;
import br.com.felipeblaft.reports.usecase.seed.SeedFile.SeedTeacher;
import br.com.felipeblaft.reports.usecase.seed.SeedResult.Count;
import br.com.felipeblaft.reports.usecase.seed.SeedResult.IssuedCode;
import br.com.felipeblaft.reports.usecase.seed.SeedResult.SeedError;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Carga inicial de professores, alunos e associações (FR-011, R-14). Valida o arquivo inteiro antes
 * de gravar qualquer coisa (tudo ou nada); é idempotente por e-mail e por hash do CPF; nunca apaga.
 * Professor novo recebe código, devolvido em claro só no resultado.
 */
public class LoadInitialRegistryUseCase {

    private static final Pattern EMAIL_FORMAT = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private final TeacherRepository teachers;
    private final StudentRepository students;
    private final AssignmentRepository assignments;
    private final DataProtectionPort protection;
    private final AccessCodeIssuanceService issuance;
    private final ClockPort clock;

    public LoadInitialRegistryUseCase(
            TeacherRepository teachers,
            StudentRepository students,
            AssignmentRepository assignments,
            DataProtectionPort protection,
            AccessCodeIssuanceService issuance,
            ClockPort clock) {
        this.teachers = Objects.requireNonNull(teachers);
        this.students = Objects.requireNonNull(students);
        this.assignments = Objects.requireNonNull(assignments);
        this.protection = Objects.requireNonNull(protection);
        this.issuance = Objects.requireNonNull(issuance);
        this.clock = Objects.requireNonNull(clock);
    }

    public SeedResult run(SeedFile file, boolean simulate) {
        Instant now = clock.now();
        LocalDate today = clock.todayInSchoolTimezone();
        List<SeedError> errors = new ArrayList<>();
        Map<Integer, Cpf> validCpfs = new HashMap<>();
        Map<Integer, LocalDate> births = new HashMap<>();
        validateTeachers(file.teachers(), errors);
        validateStudents(file, today, errors, validCpfs, births);

        if (!errors.isEmpty()) {
            return new SeedResult(
                    simulate, new Count(0, 0, 0), new Count(0, 0, 0), 0, errors, List.of());
        }

        List<IssuedCode> codes = new ArrayList<>();
        Map<String, Teacher> teachersByEmail = new LinkedHashMap<>();
        int[] teacherCount = new int[3];
        for (SeedTeacher seed : file.teachers()) {
            String email = Teacher.normalizeEmail(seed.email());
            String name = seed.name().strip();
            boolean active = seed.active() == null || seed.active();
            var existing = teachers.searchByEmail(email);
            Teacher teacher;
            if (existing.isPresent()) {
                teacher = existing.get();
                if (teacher.updateRegistry(name, active, now)) {
                    teacherCount[1]++;
                    save(simulate, () -> teachers.save(teacher));
                } else {
                    teacherCount[2]++;
                }
            } else {
                teacher = Teacher.create(name, email, active, now);
                teacherCount[0]++;
                if (!simulate) {
                    AccessCode code = issuance.emit(teacher, now);
                    teachers.save(teacher);
                    codes.add(new IssuedCode(email, code.value(), teacher.accessCodeExpiresAt()));
                }
            }
            teachersByEmail.put(email, teacher);
        }

        int[] studentCount = new int[3];
        int createdAssignments = 0;
        List<SeedStudent> studentList = file.students();
        for (int i = 0; i < studentList.size(); i++) {
            SeedStudent seed = studentList.get(i);
            Cpf cpf = validCpfs.get(i);
            LocalDate birth = births.get(i);
            String name = seed.name().strip();
            String guardian = optionalText(seed.guardianName());
            String instrument = optionalText(seed.instrument());
            String hash = protection.hashCpf(cpf);
            var existing = students.searchByCpfHash(hash);
            Student student;
            boolean created = existing.isEmpty();
            if (!created) {
                student = existing.get();
                if (student.updateRegistry(name, birth, guardian, instrument, now)) {
                    studentCount[1]++;
                    save(simulate, () -> students.save(student));
                } else {
                    studentCount[2]++;
                }
            } else {
                student =
                        Student.create(
                                name,
                                birth,
                                simulate ? new byte[0] : protection.encryptCpf(cpf),
                                hash,
                                guardian,
                                instrument,
                                now);
                studentCount[0]++;
                save(simulate, () -> students.save(student));
            }
            Set<String> studentEmails = new HashSet<>();
            for (String teacherEmail : seed.teachers()) {
                String email = Teacher.normalizeEmail(teacherEmail);
                if (!studentEmails.add(email)) {
                    continue;
                }
                Teacher teacher =
                        teachersByEmail.computeIfAbsent(
                                email,
                                key ->
                                        teachers.searchByEmail(key)
                                                .orElseThrow(
                                                        () ->
                                                                new DomainException(
                                                                        ErrorCode
                                                                                .TEACHER_NOT_FOUND)));
                if (created || !assignments.exists(teacher.id(), student.id())) {
                    createdAssignments++;
                    save(
                            simulate,
                            () ->
                                    assignments.save(
                                            TeacherStudent.create(
                                                    teacher.id(), student.id(), now)));
                }
            }
        }

        return new SeedResult(
                simulate,
                new Count(teacherCount[0], teacherCount[1], teacherCount[2]),
                new Count(studentCount[0], studentCount[1], studentCount[2]),
                createdAssignments,
                List.of(),
                codes);
    }

    private static void save(boolean simulate, Runnable recording) {
        if (!simulate) {
            recording.run();
        }
    }

    private void validateTeachers(List<SeedTeacher> list, List<SeedError> errors) {
        Set<String> emails = new HashSet<>();
        for (int i = 0; i < list.size(); i++) {
            SeedTeacher seed = list.get(i);
            String email = Teacher.normalizeEmail(seed.email());
            if (email == null || !EMAIL_FORMAT.matcher(email).matches()) {
                errors.add(new SeedError("teachers", i, "email", ErrorCode.INVALID_EMAIL));
            } else if (!emails.add(email)) {
                errors.add(
                        new SeedError("teachers", i, "email", ErrorCode.DUPLICATE_EMAIL_IN_FILE));
            }
            if (optionalText(seed.name()) == null) {
                errors.add(new SeedError("teachers", i, "name", ErrorCode.NAME_REQUIRED));
            }
        }
    }

    private void validateStudents(
            SeedFile file,
            LocalDate today,
            List<SeedError> errors,
            Map<Integer, Cpf> validCpfs,
            Map<Integer, LocalDate> births) {
        Set<String> fileEmails = new HashSet<>();
        for (SeedTeacher teacher : file.teachers()) {
            String email = Teacher.normalizeEmail(teacher.email());
            if (email != null) {
                fileEmails.add(email);
            }
        }
        Set<Cpf> seenCpfs = new HashSet<>();
        List<SeedStudent> list = file.students();
        for (int i = 0; i < list.size(); i++) {
            SeedStudent seed = list.get(i);
            if (optionalText(seed.name()) == null) {
                errors.add(new SeedError("students", i, "name", ErrorCode.NAME_REQUIRED));
            }
            try {
                Cpf cpf = Cpf.of(seed.cpf());
                if (seenCpfs.add(cpf)) {
                    validCpfs.put(i, cpf);
                } else {
                    errors.add(
                            new SeedError("students", i, "cpf", ErrorCode.DUPLICATE_CPF_IN_FILE));
                }
            } catch (DomainException error) {
                errors.add(new SeedError("students", i, "cpf", ErrorCode.INVALID_CPF));
            }
            LocalDate birth = readDate(seed.birthDate());
            if (birth == null || birth.isAfter(today)) {
                errors.add(new SeedError("students", i, "birthDate", ErrorCode.INVALID_BIRTH_DATE));
            } else {
                births.put(i, birth);
                if (Student.requiresGuardian(birth, today)
                        && optionalText(seed.guardianName()) == null) {
                    errors.add(
                            new SeedError(
                                    "students", i, "guardianName", ErrorCode.GUARDIAN_REQUIRED));
                }
            }
            List<String> emails = seed.teachers();
            for (int j = 0; j < emails.size(); j++) {
                String email = Teacher.normalizeEmail(emails.get(j));
                boolean exists =
                        email != null
                                && (fileEmails.contains(email)
                                        || teachers.searchByEmail(email).isPresent());
                if (!exists) {
                    errors.add(
                            new SeedError(
                                    "students",
                                    i,
                                    "teachers[" + j + "]",
                                    ErrorCode.TEACHER_NOT_FOUND));
                }
            }
        }
    }

    private static LocalDate readDate(String text) {
        if (text == null) {
            return null;
        }
        try {
            return LocalDate.parse(text.strip());
        } catch (DateTimeParseException error) {
            return null;
        }
    }

    private static String optionalText(String text) {
        return text == null || text.isBlank() ? null : text.strip();
    }
}
