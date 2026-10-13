package br.com.felipeblaft.reports.service;

import br.com.felipeblaft.reports.domain.common.DomainException;
import br.com.felipeblaft.reports.domain.common.ErrorCode;
import br.com.felipeblaft.reports.port.AssignmentRepository;
import java.util.Objects;
import java.util.UUID;

/**
 * Regras de autorização comuns aos dois canais (FR-009, FR-010, Princípio IV): aluno exige
 * associação; relatório de outro professor é tratado como inexistente.
 */
public class AuthorizationService {

    private final AssignmentRepository assignments;

    public AuthorizationService(AssignmentRepository assignments) {
        this.assignments = Objects.requireNonNull(assignments);
    }

    public void requireAssignment(UUID teacherId, UUID studentId) {
        if (!assignments.exists(teacherId, studentId)) {
            throw new DomainException(ErrorCode.ALUNO_NAO_ASSOCIADO);
        }
    }

    public void requireOwner(UUID teacherId, UUID resourceTeacher) {
        if (!Objects.equals(teacherId, resourceTeacher)) {
            throw new DomainException(ErrorCode.RECURSO_NAO_ENCONTRADO);
        }
    }
}
