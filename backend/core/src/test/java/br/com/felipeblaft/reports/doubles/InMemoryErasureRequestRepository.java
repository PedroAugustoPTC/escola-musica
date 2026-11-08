package br.com.felipeblaft.reports.doubles;

import br.com.felipeblaft.reports.domain.common.DomainException;
import br.com.felipeblaft.reports.domain.common.ErrorCode;
import br.com.felipeblaft.reports.domain.privacy.ErasedStudent;
import br.com.felipeblaft.reports.domain.privacy.ErasureRequest;
import br.com.felipeblaft.reports.domain.privacy.ErasureStatus;
import br.com.felipeblaft.reports.port.ErasureRequestRepository;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Pedidos de eliminação em memória; um pedido não concluído por aluno, como no índice único. */
public class InMemoryErasureRequestRepository implements ErasureRequestRepository {

    private final Map<UUID, ErasureRequest> data = new LinkedHashMap<>();
    private final Map<UUID, ErasedStudent> erased = new LinkedHashMap<>();

    @Override
    public void save(ErasureRequest request) {
        boolean otherOpen =
                data.values().stream()
                        .anyMatch(
                                other ->
                                        !other.id().equals(request.id())
                                                && other.studentId().equals(request.studentId())
                                                && other.status() != ErasureStatus.COMPLETED);
        if (otherOpen && request.status() != ErasureStatus.COMPLETED) {
            throw new DomainException(ErrorCode.ERASURE_ALREADY_OPEN);
        }
        data.put(request.id(), request);
    }

    @Override
    public Optional<ErasureRequest> findById(UUID id) {
        return Optional.ofNullable(data.get(id));
    }

    @Override
    public Optional<ErasureRequest> findForUpdate(UUID id) {
        return findById(id);
    }

    @Override
    public Optional<ErasureRequest> findOpenByStudent(UUID studentId) {
        return data.values().stream()
                .filter(r -> r.studentId().equals(studentId))
                .filter(r -> r.status() != ErasureStatus.COMPLETED)
                .findFirst();
    }

    @Override
    public List<ErasureRequest> listAll() {
        return data.values().stream()
                .sorted(Comparator.comparing(ErasureRequest::createdAt).reversed())
                .toList();
    }

    @Override
    public List<ErasureRequest> listInProgress() {
        return data.values().stream().filter(r -> r.status() == ErasureStatus.IN_PROGRESS).toList();
    }

    @Override
    public void recordErased(ErasedStudent student) {
        erased.putIfAbsent(student.studentId(), student);
    }

    @Override
    public boolean isErased(UUID studentId) {
        return erased.containsKey(studentId);
    }

    public Set<UUID> erasedIds() {
        return new LinkedHashSet<>(erased.keySet());
    }
}
