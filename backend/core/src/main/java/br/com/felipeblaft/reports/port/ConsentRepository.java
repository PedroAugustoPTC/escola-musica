package br.com.felipeblaft.reports.port;

import br.com.felipeblaft.reports.domain.privacy.GuardianConsent;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Consentimentos dos responsáveis (FR-030 a FR-033). Cada registro é novo; o histórico fica. */
public interface ConsentRepository {

    /** Insere o registro ou grava a revogação dele (a única alteração permitida). */
    void save(GuardianConsent consent);

    Optional<GuardianConsent> findById(UUID id);

    /** Histórico do aluno, do mais recente ao mais antigo. */
    List<GuardianConsent> findByStudent(UUID studentId);

    /** Histórico de vários alunos numa só consulta; aluno sem registro não aparece no mapa. */
    Map<UUID, List<GuardianConsent>> findByStudents(Collection<UUID> studentIds);
}
