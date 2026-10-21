package br.com.felipeblaft.reports.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.felipeblaft.reports.domain.common.DomainException;
import br.com.felipeblaft.reports.domain.common.ErrorCode;
import br.com.felipeblaft.reports.doubles.InMemoryAssignmentRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AuthorizationServiceTest {

    private final InMemoryAssignmentRepository assignments = new InMemoryAssignmentRepository();
    private final AuthorizationService service = new AuthorizationService(assignments);
    private final UUID clara = UUID.randomUUID();
    private final UUID bruno = UUID.randomUUID();
    private final UUID matteo = UUID.randomUUID();

    @Test
    void assignedStudentPasses() {
        assignments.assign(clara, matteo);
        assertThatCode(() -> service.requireAssignment(clara, matteo)).doesNotThrowAnyException();
    }

    @Test
    void otherTeachersStudentIsDenied() {
        assignments.assign(bruno, matteo);
        assertThatThrownBy(() -> service.requireAssignment(clara, matteo))
                .isInstanceOf(DomainException.class)
                .hasMessage(ErrorCode.RESOURCE_NOT_FOUND.name());
    }

    @Test
    void otherTeachersResourceIsNonexistent() {
        assertThatCode(() -> service.requireOwner(clara, clara)).doesNotThrowAnyException();
        assertThatThrownBy(() -> service.requireOwner(clara, bruno))
                .isInstanceOf(DomainException.class)
                .hasMessage(ErrorCode.RESOURCE_NOT_FOUND.name());
    }
}
