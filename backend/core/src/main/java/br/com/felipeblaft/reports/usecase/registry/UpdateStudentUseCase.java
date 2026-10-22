package br.com.felipeblaft.reports.usecase.registry;

import br.com.felipeblaft.reports.domain.access.Actor;
import br.com.felipeblaft.reports.domain.access.AuthorshipTargetType;
import br.com.felipeblaft.reports.domain.access.Operation;
import br.com.felipeblaft.reports.domain.access.Permission;
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
import br.com.felipeblaft.reports.service.StudentAccessService;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Edita um aluno ({@code STUDENT_UPDATE}); campos ausentes não mudam. Menor de 18 anos na data da
 * edição exige os três dados do responsável; o CPF novo segue o mesmo caminho do cadastro (em
 * branco mantém o atual). Serve também à carga inicial. Ramo do professor: tarefa T149.
 */
public class UpdateStudentUseCase {

    public record Command(UUID studentId, StudentInput input, String correlationId) {

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
    private final GetStudentUseCase.Views views;

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
        this.authorization = Objects.requireNonNull(authorization);
        this.access = Objects.requireNonNull(access);
        this.students = Objects.requireNonNull(students);
        this.protection = Objects.requireNonNull(protection);
        this.authorship = Objects.requireNonNull(authorship);
        this.clock = Objects.requireNonNull(clock);
        this.views = new GetStudentUseCase.Views(assignments, teachers, clock);
    }

    public StudentView run(Actor actor, Command command) {
        Permission permission = authorization.require(actor, Operation.STUDENT_UPDATE);
        if (permission == Permission.ALLOW_OWN_STUDENTS) {
            throw new UnsupportedOperationException("UpdateStudentUseCase: ramo do professor");
        }
        Student student =
                GetStudentUseCase.findVisibleOrRecordDenial(
                        access, authorship, actor, command.studentId(), command.correlationId());
        Instant now = clock.now();
        StudentData data = merge(student, command.input());

        ProtectedCpf newCpf = null;
        if (data.cpf() != null) {
            newCpf = protectIfFree(student, data.cpf());
        }
        if (student.applyEdit(data, newCpf, now)) {
            students.save(student);
            authorship.record(
                    actor,
                    Operation.STUDENT_UPDATE,
                    AuthorshipTargetType.STUDENT,
                    student.id(),
                    command.correlationId());
        }
        return views.of(student, false);
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
     * CPF informado: o do próprio aluno não muda nada; o de outro aluno é duplicidade (o nome dele
     * vai ao administrador); livre, vira o valor protegido a gravar.
     */
    private ProtectedCpf protectIfFree(Student student, Cpf cpf) {
        String hash = protection.hashCpf(cpf);
        Optional<Student> holder = students.searchByCpfHash(hash);
        if (holder.isEmpty()) {
            return new ProtectedCpf(protection.encryptCpf(cpf), hash);
        }
        if (!holder.get().id().equals(student.id())) {
            throw RegisterStudentUseCase.duplicate(holder.get());
        }
        return null;
    }
}
