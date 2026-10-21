package br.com.felipeblaft.reports.outbound.persistence;

import br.com.felipeblaft.reports.domain.access.AuthorshipRecord;
import br.com.felipeblaft.reports.outbound.PendingImplementation;
import br.com.felipeblaft.reports.port.AuthorshipRecorderPort;
import org.springframework.stereotype.Repository;

/**
 * Registro de autoria (somente inserção). Stub da Fundação (feature 005): a tabela {@code
 * authorship_record} (V10) e a gravação real, na mesma transação ({@code record}) e em transação
 * independente ({@code recordIndependent}), chegam com o fluxo W-F.
 */
@Repository
class AuthorshipRecordAdapter implements AuthorshipRecorderPort {

    @Override
    public void record(AuthorshipRecord record) {
        throw PendingImplementation.of("W-F");
    }

    @Override
    public void recordIndependent(AuthorshipRecord record) {
        throw PendingImplementation.of("W-F");
    }
}
