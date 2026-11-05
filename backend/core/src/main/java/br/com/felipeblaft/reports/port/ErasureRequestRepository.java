package br.com.felipeblaft.reports.port;

import br.com.felipeblaft.reports.domain.privacy.ErasedStudent;
import br.com.felipeblaft.reports.domain.privacy.ErasureRequest;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Pedidos de eliminação e a lista só de acréscimo dos alunos eliminados (FR-020 a FR-026). */
public interface ErasureRequestRepository {

    /**
     * Grava o pedido (inserção ou nova situação). Um segundo pedido não concluído para o mesmo
     * aluno lança {@code DomainException} com {@code ERASURE_ALREADY_OPEN}.
     */
    void save(ErasureRequest request);

    Optional<ErasureRequest> findById(UUID id);

    /** Lê o pedido bloqueando a linha até o fim da transação (confirmações concorrentes). */
    Optional<ErasureRequest> findForUpdate(UUID id);

    /** Pedido ainda não concluído do aluno, se houver. */
    Optional<ErasureRequest> findOpenByStudent(UUID studentId);

    /** Todos os pedidos, do mais recente ao mais antigo. */
    List<ErasureRequest> listAll();

    /** Pedidos {@code IN_PROGRESS} (arquivos pendentes), para a retomada pela retenção. */
    List<ErasureRequest> listInProgress();

    /** Acrescenta o aluno à lista de eliminados; repetir o mesmo aluno não faz nada. */
    void recordErased(ErasedStudent erased);

    boolean isErased(UUID studentId);
}
