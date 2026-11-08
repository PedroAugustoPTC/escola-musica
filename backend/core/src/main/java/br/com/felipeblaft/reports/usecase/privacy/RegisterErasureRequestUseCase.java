package br.com.felipeblaft.reports.usecase.privacy;

import br.com.felipeblaft.reports.domain.access.Actor;
import br.com.felipeblaft.reports.domain.access.AuthorshipTargetType;
import br.com.felipeblaft.reports.domain.access.Operation;
import br.com.felipeblaft.reports.domain.common.DomainException;
import br.com.felipeblaft.reports.domain.common.ErrorCode;
import br.com.felipeblaft.reports.domain.common.GeneratorId;
import br.com.felipeblaft.reports.domain.privacy.ErasureRequest;
import br.com.felipeblaft.reports.port.ClockPort;
import br.com.felipeblaft.reports.port.ErasureRequestRepository;
import br.com.felipeblaft.reports.port.StudentRepository;
import br.com.felipeblaft.reports.service.ActorAuthorizationService;
import br.com.felipeblaft.reports.service.AuthorshipService;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Registra o pedido de eliminação de um aluno ({@code ERASURE_REQUEST_REGISTER}, FR-020): só o
 * administrador, na Web. O pedido nasce {@code RECEIVED}; nada é apagado ainda. Um aluno já
 * eliminado ou com pedido em aberto é recusado com código próprio.
 */
public class RegisterErasureRequestUseCase {

    public record Command(
            UUID studentId,
            String requesterRole,
            String requesterName,
            LocalDate requestedOn,
            String receivedVia,
            String protocolReference,
            String correlationId) {

        @Override
        public String toString() {
            return "RegisterErasureRequestUseCase.Command";
        }
    }

    private final ActorAuthorizationService authorization;
    private final StudentRepository students;
    private final ErasureRequestRepository erasures;
    private final AuthorshipService authorship;
    private final ClockPort clock;

    public RegisterErasureRequestUseCase(
            ActorAuthorizationService authorization,
            StudentRepository students,
            ErasureRequestRepository erasures,
            AuthorshipService authorship,
            ClockPort clock) {
        this.authorization = Objects.requireNonNull(authorization);
        this.students = Objects.requireNonNull(students);
        this.erasures = Objects.requireNonNull(erasures);
        this.authorship = Objects.requireNonNull(authorship);
        this.clock = Objects.requireNonNull(clock);
    }

    public ErasureRequest run(Actor actor, Command command) {
        authorization.require(actor, Operation.ERASURE_REQUEST_REGISTER);
        if (erasures.isErased(command.studentId())) {
            throw new DomainException(ErrorCode.STUDENT_ALREADY_ERASED);
        }
        if (students.searchById(command.studentId()).isEmpty()) {
            throw new DomainException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        if (erasures.findOpenByStudent(command.studentId()).isPresent()) {
            throw new DomainException(ErrorCode.ERASURE_ALREADY_OPEN);
        }
        ErasureRequest request =
                ErasureRequest.register(
                        GeneratorId.create(),
                        command.studentId(),
                        command.requesterRole(),
                        command.requesterName(),
                        command.requestedOn(),
                        command.receivedVia(),
                        command.protocolReference(),
                        actor.id(),
                        clock.todayInSchoolTimezone(),
                        clock.now());
        erasures.save(request);
        authorship.record(
                actor,
                Operation.ERASURE_REQUEST_REGISTER,
                AuthorshipTargetType.ERASURE_REQUEST,
                request.id(),
                command.correlationId());
        return request;
    }
}
