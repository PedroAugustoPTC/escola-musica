package br.com.felipeblaft.reports.usecase.registry;

import br.com.felipeblaft.reports.domain.access.Actor;
import br.com.felipeblaft.reports.domain.access.AttemptScope;
import br.com.felipeblaft.reports.domain.access.AuthorshipRecord;
import br.com.felipeblaft.reports.domain.access.AuthorshipResult;
import br.com.felipeblaft.reports.domain.access.AuthorshipTargetType;
import br.com.felipeblaft.reports.domain.access.Operation;
import br.com.felipeblaft.reports.domain.access.Permission;
import br.com.felipeblaft.reports.domain.common.Channel;
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
 * ({@code CPF_UNAVAILABLE}) ou {@code STUDENT_ALREADY_YOURS}, com autoria da recusa e contagem nos
 * limites contra sondagem (FR-024).
 */
public class RegisterStudentUseCase {

    /**
     * {@code teacherIds} só vale para o administrador; o professor se associa a si mesmo. {@code
     * lookupAlreadyCounted} é verdadeiro na nova verificação da confirmação do Telegram, que não
     * conta outra consulta de CPF (a recusa e o bloqueio continuam valendo).
     */
    public record Command(
            StudentInput input,
            List<UUID> teacherIds,
            String correlationId,
            boolean lookupAlreadyCounted) {

        public Command(StudentInput input, List<UUID> teacherIds, String correlationId) {
            this(input, teacherIds, correlationId, false);
        }

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
    private final TeacherCpfGuard guard;

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
        this.guard = new TeacherCpfGuard(assignments, attempts, authorship);
    }

    public StudentView run(Actor actor, Command command) {
        Permission permission = authorization.require(actor, Operation.STUDENT_REGISTER);
        boolean byTeacher = permission == Permission.ALLOW_OWN_STUDENTS;
        RegistrySupport.requireActiveActor(actor, teachers);
        Instant now = clock.now();
        StudentData data = validate(command.input(), clock.todayInSchoolTimezone());
        List<Teacher> owners = byTeacher ? List.of() : activeTeachers(command.teacherIds());

        Cpf cpf = data.cpf();
        String hash = protection.hashCpf(cpf);
        if (byTeacher) {
            guard.beforeLookup(actor, command.correlationId(), command.lookupAlreadyCounted());
        }
        Optional<Student> existing = students.searchByCpfHash(hash);
        if (existing.isPresent()) {
            throw conflict(actor, byTeacher, existing.get(), command.correlationId());
        }
        Student student = create(data, protection.encryptCpf(cpf), hash, now);
        if (!students.insertIfAbsent(student)) {
            Student holder =
                    students.searchByCpfHash(hash)
                            .orElseThrow(() -> new DomainException(ErrorCode.OPERATION_NOT_SAVED));
            throw conflict(actor, byTeacher, holder, command.correlationId());
        }
        if (byTeacher) {
            assignments.save(TeacherStudent.create(actor.id(), student.id(), now));
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
        return views.of(student, byTeacher);
    }

    /** CPF já usado: ao administrador o nome do aluno existente; ao professor, o código neutro. */
    private DomainException conflict(
            Actor actor, boolean byTeacher, Student holder, String correlationId) {
        return byTeacher ? guard.refuse(actor, holder, correlationId) : duplicate(holder);
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

    /**
     * Regras do professor sobre o CPF digitado (FR-024), compartilhadas por cadastro e edição. A
     * contagem é por professor, a mesma nos dois canais (por isso um canal fixo na chave): o
     * bloqueio vale antes de qualquer consulta de duplicidade; cada consulta conta no teto de
     * consultas; a recusa por aluno alheio deixa autoria e conta no limite de recusas.
     */
    static final class TeacherCpfGuard {

        private final AssignmentRepository assignments;
        private final AccessAttemptsControlService attempts;
        private final AuthorshipService authorship;

        TeacherCpfGuard(
                AssignmentRepository assignments,
                AccessAttemptsControlService attempts,
                AuthorshipService authorship) {
            this.assignments = Objects.requireNonNull(assignments);
            this.attempts = Objects.requireNonNull(attempts);
            this.authorship = Objects.requireNonNull(authorship);
        }

        /** Recusa se bloqueado e conta a consulta (salvo a nova verificação da confirmação). */
        void beforeLookup(Actor actor, String correlationId, boolean alreadyCounted) {
            String key = actor.id().toString();
            try {
                attempts.verifyLock(AttemptScope.TEACHER_CPF_PROBE, Channel.WEB, key);
                attempts.verifyLock(AttemptScope.TEACHER_CPF_LOOKUP, Channel.WEB, key);
            } catch (DomainException blocked) {
                authorship.record(
                        actor,
                        AuthorshipRecord.ATTEMPT_BLOCKED,
                        null,
                        null,
                        AuthorshipResult.BLOCKED,
                        blocked.code().name(),
                        correlationId);
                throw blocked;
            }
            if (!alreadyCounted) {
                attempts.registerFailure(AttemptScope.TEACHER_CPF_LOOKUP, Channel.WEB, key);
            }
        }

        /**
         * Recusa neutra quando o índice único decide a corrida na gravação: o dono do CPF não é
         * consultado (a transação já foi abortada), então só conta no limite de sondagem.
         */
        DomainException refuseConcurrent(Actor actor) {
            attempts.registerFailure(
                    AttemptScope.TEACHER_CPF_PROBE, Channel.WEB, actor.id().toString());
            return new DomainException(ErrorCode.CPF_UNAVAILABLE);
        }

        /**
         * Resposta ao professor para um CPF que já existe: o aluno é dele ({@code
         * STUDENT_ALREADY_YOURS}) ou não é ({@code CPF_UNAVAILABLE}, sem nome, professor ou
         * qualquer dado; deixa autoria e conta a recusa).
         */
        DomainException refuse(Actor actor, Student holder, String correlationId) {
            if (assignments.exists(actor.id(), holder.id())) {
                return new DomainException(ErrorCode.STUDENT_ALREADY_YOURS);
            }
            authorship.record(
                    actor,
                    AuthorshipRecord.CPF_REFUSED_OTHER_STUDENT,
                    AuthorshipTargetType.STUDENT,
                    holder.id(),
                    AuthorshipResult.DENIED,
                    ErrorCode.CPF_UNAVAILABLE.name(),
                    correlationId);
            attempts.registerFailure(
                    AttemptScope.TEACHER_CPF_PROBE, Channel.WEB, actor.id().toString());
            return new DomainException(ErrorCode.CPF_UNAVAILABLE);
        }
    }
}
