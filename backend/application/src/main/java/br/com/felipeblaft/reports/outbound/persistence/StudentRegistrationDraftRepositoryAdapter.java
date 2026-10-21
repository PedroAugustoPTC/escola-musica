package br.com.felipeblaft.reports.outbound.persistence;

import br.com.felipeblaft.reports.domain.telegram.StudentRegistrationDraft;
import br.com.felipeblaft.reports.outbound.PendingImplementation;
import br.com.felipeblaft.reports.port.StudentRegistrationDraftRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

/**
 * Cadastro de aluno em andamento no Telegram (CPF só cifrado). Stub da Fundação (feature 005): a
 * tabela (V12), a entidade e o adaptador real chegam com o fluxo W-G.
 */
@Repository
class StudentRegistrationDraftRepositoryAdapter implements StudentRegistrationDraftRepository {

    @Override
    public Optional<StudentRegistrationDraft> searchByLinkId(UUID linkId) {
        throw PendingImplementation.of("W-G");
    }

    @Override
    public void save(StudentRegistrationDraft draft) {
        throw PendingImplementation.of("W-G");
    }

    @Override
    public void removeByLinkId(UUID linkId) {
        throw PendingImplementation.of("W-G");
    }

    @Override
    public int removeExpired(Instant now) {
        throw PendingImplementation.of("W-G");
    }
}
