package br.com.felipeblaft.reports.usecase.teacher;

import br.com.felipeblaft.reports.domain.access.AccessCode;
import br.com.felipeblaft.reports.domain.access.Actor;
import br.com.felipeblaft.reports.domain.access.AuthorshipTargetType;
import br.com.felipeblaft.reports.domain.access.Operation;
import br.com.felipeblaft.reports.domain.common.DomainException;
import br.com.felipeblaft.reports.domain.common.ErrorCode;
import br.com.felipeblaft.reports.domain.common.Secret;
import br.com.felipeblaft.reports.domain.registry.Teacher;
import br.com.felipeblaft.reports.domain.registry.TeacherIdentity;
import br.com.felipeblaft.reports.port.ClockPort;
import br.com.felipeblaft.reports.port.TeacherRepository;
import br.com.felipeblaft.reports.service.AccessCodeIssuanceService;
import br.com.felipeblaft.reports.service.ActorAuthorizationService;
import br.com.felipeblaft.reports.service.AuthorshipService;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * Cadastra um professor ativo ({@code TEACHER_REGISTER}), com código de acesso de 8 caracteres e 7
 * dias exibido uma única vez; e-mail único sem diferenciar caixa nem espaços, inclusive entre
 * inativos, também sob concorrência (inserção atômica). A duplicidade identifica o professor
 * existente pelo nome, para o administrador. Serve também à carga inicial (ator de sistema).
 */
public class RegisterTeacherUseCase {

    public record Command(String name, String email, String correlationId) {

        @Override
        public String toString() {
            return "RegisterTeacherUseCase.Command";
        }
    }

    private final ActorAuthorizationService authorization;
    private final TeacherRepository teachers;
    private final AccessCodeIssuanceService issuance;
    private final AuthorshipService authorship;
    private final ClockPort clock;

    public RegisterTeacherUseCase(
            ActorAuthorizationService authorization,
            TeacherRepository teachers,
            AccessCodeIssuanceService issuance,
            AuthorshipService authorship,
            ClockPort clock) {
        this.authorization = Objects.requireNonNull(authorization);
        this.teachers = Objects.requireNonNull(teachers);
        this.issuance = Objects.requireNonNull(issuance);
        this.authorship = Objects.requireNonNull(authorship);
        this.clock = Objects.requireNonNull(clock);
    }

    public TeacherWithCode run(Actor actor, Command command) {
        authorization.require(actor, Operation.TEACHER_REGISTER);
        TeacherIdentity identity = TeacherIdentity.of(command.name(), command.email());
        Instant now = clock.now();
        Teacher teacher = Teacher.create(identity.name(), identity.email(), true, now);
        AccessCode code = issuance.emit(teacher, now);
        if (!teachers.insertIfAbsent(teacher)) {
            Map<String, Object> details =
                    teachers.searchByEmail(identity.email())
                            .<Map<String, Object>>map(existing -> Map.of("name", existing.name()))
                            .orElse(Map.of());
            throw new DomainException(ErrorCode.DUPLICATE_EMAIL, details);
        }
        authorship.record(
                actor,
                Operation.TEACHER_REGISTER,
                AuthorshipTargetType.TEACHER,
                teacher.id(),
                command.correlationId());
        return new TeacherWithCode(
                TeacherView.of(teacher, false),
                new Secret(code.value()),
                teacher.accessCodeExpiresAt());
    }
}
