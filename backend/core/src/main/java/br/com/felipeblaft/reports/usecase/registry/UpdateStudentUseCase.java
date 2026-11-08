package br.com.felipeblaft.reports.usecase.registry;

import br.com.felipeblaft.reports.domain.access.Actor;
import br.com.felipeblaft.reports.domain.access.AuthorshipTargetType;
import br.com.felipeblaft.reports.domain.access.Operation;
import br.com.felipeblaft.reports.domain.access.Permission;
import br.com.felipeblaft.reports.domain.common.DomainException;
import br.com.felipeblaft.reports.domain.common.ErrorCode;
import br.com.felipeblaft.reports.domain.privacy.ConsentInput;
import br.com.felipeblaft.reports.domain.privacy.ConsentPolicy;
import br.com.felipeblaft.reports.domain.privacy.ConsentStatus;
import br.com.felipeblaft.reports.domain.registry.Cpf;
import br.com.felipeblaft.reports.domain.registry.ProtectedCpf;
import br.com.felipeblaft.reports.domain.registry.Student;
import br.com.felipeblaft.reports.domain.registry.StudentData;
import br.com.felipeblaft.reports.port.AssignmentRepository;
import br.com.felipeblaft.reports.port.ClockPort;
import br.com.felipeblaft.reports.port.DataProtectionPort;
import br.com.felipeblaft.reports.port.StudentRepository;
import br.com.felipeblaft.reports.port.TeacherRepository;
import br.com.felipeblaft.reports.service.AccessAttemptsControlService;
import br.com.felipeblaft.reports.service.ActorAuthorizationService;
import br.com.felipeblaft.reports.service.AuthorshipService;
import br.com.felipeblaft.reports.service.ConsentRecordingService;
import br.com.felipeblaft.reports.service.StudentAccessService;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Edita um aluno ({@code STUDENT_UPDATE}); campos ausentes não mudam. Menor de 18 anos na data da
 * edição exige os três dados do responsável; o CPF novo segue o mesmo caminho do cadastro (em
 * branco mantém o atual), inclusive as regras do professor contra sondagem (FR-024). O professor só
 * edita aluno associado a ele. Serve também à carga inicial.
 */
public class UpdateStudentUseCase {

    /**
     * {@code lookupAlreadyCounted} é verdadeiro na nova verificação da confirmação do Telegram, que
     * não conta outra consulta de CPF.
     */
    public record Command(
            UUID studentId,
            StudentInput input,
            String correlationId,
            boolean lookupAlreadyCounted,
            ConsentInput consent) {

        public Command(UUID studentId, StudentInput input, String correlationId) {
            this(studentId, input, correlationId, false, null);
        }

        public Command(
                UUID studentId,
                StudentInput input,
                String correlationId,
                boolean lookupAlreadyCounted) {
            this(studentId, input, correlationId, lookupAlreadyCounted, null);
        }

        /** Edição com o consentimento do responsável (exigido se o aluno passa a ser menor). */
        public Command(
                UUID studentId, StudentInput input, String correlationId, ConsentInput consent) {
            this(studentId, input, correlationId, false, consent);
        }

        @Override
        public String toString() {
            return "UpdateStudentUseCase.Command[studentId=" + studentId + "]";
        }
    }

    private final ActorAuthorizationService authorization;
    private final StudentAccessService access;
    private final StudentRepository students;
    private final DataProtectionPort protection;
    private final AuthorshipService authorship;
    private final ClockPort clock;
    private final TeacherRepository teachers;
    private final GetStudentUseCase.Views views;
    private final RegisterStudentUseCase.TeacherCpfGuard guard;
    private final ConsentRecordingService consents;

    /**
     * Sem a verificação do consentimento: só para os cenários anteriores à feature 007. A montagem
     * de produção usa o construtor completo.
     */
    public UpdateStudentUseCase(
            ActorAuthorizationService authorization,
            StudentAccessService access,
            StudentRepository students,
            AssignmentRepository assignments,
            TeacherRepository teachers,
            DataProtectionPort protection,
            AccessAttemptsControlService attempts,
            AuthorshipService authorship,
            ClockPort clock) {
        this(
                authorization,
                access,
                students,
                assignments,
                teachers,
                protection,
                attempts,
                authorship,
                clock,
                null);
    }

    public UpdateStudentUseCase(
            ActorAuthorizationService authorization,
            StudentAccessService access,
            StudentRepository students,
            AssignmentRepository assignments,
            TeacherRepository teachers,
            DataProtectionPort protection,
            AccessAttemptsControlService attempts,
            AuthorshipService authorship,
            ClockPort clock,
            ConsentRecordingService consents) {
        this.consents = consents;
        this.authorization = Objects.requireNonNull(authorization);
        this.access = Objects.requireNonNull(access);
        this.students = Objects.requireNonNull(students);
        this.protection = Objects.requireNonNull(protection);
        this.authorship = Objects.requireNonNull(authorship);
        this.clock = Objects.requireNonNull(clock);
        this.teachers = Objects.requireNonNull(teachers);
        this.views = new GetStudentUseCase.Views(assignments, teachers, clock);
        this.guard = new RegisterStudentUseCase.TeacherCpfGuard(assignments, attempts, authorship);
    }

    public StudentView run(Actor actor, Command command) {
        Permission permission = authorization.require(actor, Operation.STUDENT_UPDATE);
        boolean byTeacher = permission == Permission.ALLOW_OWN_STUDENTS;
        RegistrySupport.requireActiveActor(actor, teachers);
        Student student =
                RegistrySupport.findVisibleOrRecordDenial(
                        access, authorship, actor, command.studentId(), command.correlationId());
        Instant now = clock.now();
        StudentData data = merge(student, command.input());
        boolean recordConsent = checkConsent(actor, student, data, command.consent());

        ProtectedCpf newCpf = null;
        if (data.cpf() != null) {
            if (byTeacher) {
                guard.beforeLookup(actor, command.correlationId(), command.lookupAlreadyCounted());
            }
            newCpf = protectIfFree(actor, byTeacher, student, data.cpf(), command.correlationId());
        }
        if (student.applyEdit(data, newCpf, now)) {
            saveOrRefuseDuplicate(actor, byTeacher, student);
            authorship.record(
                    actor,
                    Operation.STUDENT_UPDATE,
                    AuthorshipTargetType.STUDENT,
                    student.id(),
                    command.correlationId());
        }
        if (recordConsent) {
            consents.record(actor, student.id(), command.consent(), command.correlationId());
        }
        return views.of(student, byTeacher);
    }

    /**
     * Antes de gravar: a edição que torna o aluno menor exige o {@link ConsentInput} ({@code
     * VALIDATION_FAILED} em {@code consent}). Devolve se o consentimento enviado deve ser gravado.
     */
    private boolean checkConsent(
            Actor actor, Student student, StudentData data, ConsentInput input) {
        if (consents == null || actor.isSystem()) {
            return false;
        }
        LocalDate today = clock.todayInSchoolTimezone();
        LocalDate birth = data.birthDate().value();
        if (!ConsentPolicy.required(student.birthDate(), today)) {
            // só a edição que torna o aluno menor exige o consentimento; o menor já cadastrado sem
            // ele aparece nas pendências e fica com a IA bloqueada
            ConsentPolicy.requireOnRegistration(birth, today, ConsentStatus.MISSING, input);
        }
        if (input != null && ConsentPolicy.required(birth, today)) {
            consents.check(input);
            return true;
        }
        return false;
    }

    /**
     * Grava; se outra edição ou cadastro tomou o mesmo CPF entre a verificação e a gravação (o
     * índice único recusa e o adaptador traduz em {@code DUPLICATE_CPF}), responde como o cadastro:
     * ao administrador, a duplicidade com o nome do aluno que tem o CPF; ao professor, o código
     * neutro, contando a recusa no limite contra sondagem (FR-024).
     */
    private void saveOrRefuseDuplicate(Actor actor, boolean byTeacher, Student student) {
        try {
            students.save(student);
        } catch (DomainException conflict) {
            if (conflict.code() != ErrorCode.DUPLICATE_CPF || !byTeacher) {
                throw conflict;
            }
            throw guard.refuseConcurrent(actor);
        }
    }

    /**
     * Mescla o que veio com o que já está gravado (campo nulo mantém o atual; o responsável enviado
     * substitui os três campos juntos) e valida o resultado inteiro, como no cadastro.
     */
    private StudentData merge(Student student, StudentInput input) {
        GuardianInput guardian = input.guardian();
        return StudentData.forEdit(
                input.name() != null ? input.name() : student.name(),
                input.birthDate() != null ? input.birthDate() : student.birthDate().toString(),
                input.cpf() == null ? null : input.cpf().value(),
                input.instrument() != null ? input.instrument() : student.instrument(),
                guardian != null ? guardian.name() : student.guardianName(),
                guardian != null ? guardian.phone() : student.guardianPhone(),
                guardian != null ? guardian.email() : student.guardianEmail(),
                clock.todayInSchoolTimezone());
    }

    /**
     * CPF informado: o do próprio aluno não muda nada; o de outro aluno é duplicidade (ao
     * administrador, o nome dele; ao professor, o código neutro); livre, vira o valor protegido a
     * gravar.
     */
    private ProtectedCpf protectIfFree(
            Actor actor, boolean byTeacher, Student student, Cpf cpf, String correlationId) {
        String hash = protection.hashCpf(cpf);
        Optional<Student> holder = students.searchByCpfHash(hash);
        if (holder.isEmpty()) {
            return new ProtectedCpf(protection.encryptCpf(cpf), hash);
        }
        if (holder.get().id().equals(student.id())) {
            return null;
        }
        throw byTeacher
                ? guard.refuse(actor, holder.get(), correlationId)
                : RegisterStudentUseCase.duplicate(holder.get());
    }
}
