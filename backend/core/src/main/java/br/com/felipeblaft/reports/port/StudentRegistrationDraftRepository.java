package br.com.felipeblaft.reports.port;

import br.com.felipeblaft.reports.domain.telegram.StudentRegistrationDraft;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Cadastros de aluno em andamento no Telegram (uma linha por vínculo). A linha some junto com o
 * vínculo, por qualquer causa (cascata).
 */
public interface StudentRegistrationDraftRepository {

    Optional<StudentRegistrationDraft> searchByLinkId(UUID linkId);

    /** Insere ou atualiza o cadastro do vínculo. */
    void save(StudentRegistrationDraft draft);

    void removeByLinkId(UUID linkId);

    /** Varredura de rascunhos vencidos; devolve quantos foram apagados. */
    int removeExpired(Instant now);
}
