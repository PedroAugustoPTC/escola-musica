package br.com.felipeblaft.reports.usecase.privacy;

import br.com.felipeblaft.reports.domain.access.Actor;
import br.com.felipeblaft.reports.domain.access.Operation;
import br.com.felipeblaft.reports.domain.common.DomainException;
import br.com.felipeblaft.reports.domain.common.ErrorCode;
import br.com.felipeblaft.reports.domain.privacy.ErasedStudent;
import br.com.felipeblaft.reports.domain.privacy.ErasureRequest;
import br.com.felipeblaft.reports.domain.privacy.ErasureStatus;
import br.com.felipeblaft.reports.domain.registry.Student;
import br.com.felipeblaft.reports.port.ErasureRequestRepository;
import br.com.felipeblaft.reports.port.StudentRepository;
import br.com.felipeblaft.reports.service.ActorAuthorizationService;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Lista e detalha pedidos de eliminação ({@code ERASURE_REQUEST_READ}). O aluno aparece pelo nome
 * enquanto existe e por "Aluno removido · #XXXXXX" depois da eliminação (FR-024).
 */
public class ListErasureRequestsUseCase {

    /** Pedido com o rótulo do aluno resolvido na exibição. */
    public record RequestView(ErasureRequest request, String studentLabel) {

        @Override
        public String toString() {
            return "ListErasureRequestsUseCase.RequestView";
        }
    }

    private final ActorAuthorizationService authorization;
    private final ErasureRequestRepository erasures;
    private final StudentRepository students;

    public ListErasureRequestsUseCase(
            ActorAuthorizationService authorization,
            ErasureRequestRepository erasures,
            StudentRepository students) {
        this.authorization = Objects.requireNonNull(authorization);
        this.erasures = Objects.requireNonNull(erasures);
        this.students = Objects.requireNonNull(students);
    }

    /** Pedidos do mais recente ao mais antigo, filtrados por situação quando informada. */
    public List<RequestView> list(Actor actor, ErasureStatus status) {
        authorization.require(actor, Operation.ERASURE_REQUEST_READ);
        return erasures.listAll().stream()
                .filter(request -> status == null || request.status() == status)
                .map(this::view)
                .toList();
    }

    public RequestView get(Actor actor, UUID requestId) {
        authorization.require(actor, Operation.ERASURE_REQUEST_READ);
        return erasures.findById(requestId)
                .map(this::view)
                .orElseThrow(() -> new DomainException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    private RequestView view(ErasureRequest request) {
        String label =
                erasures.isErased(request.studentId())
                        ? ErasedStudent.label(request.studentId())
                        : students.searchById(request.studentId())
                                .map(Student::name)
                                .orElse(ErasedStudent.label(request.studentId()));
        return new RequestView(request, label);
    }
}
