package br.com.felipeblaft.reports.outbound.persistence;

import br.com.felipeblaft.reports.inbound.telegram.TelegramUpdateRecords;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
class TelegramUpdateRecordsAdapter implements TelegramUpdateRecords {

    private final ProcessedTelegramUpdateJpa jpa;

    TelegramUpdateRecordsAdapter(ProcessedTelegramUpdateJpa jpa) {
        this.jpa = jpa;
    }

    @Override
    @Transactional
    public boolean registerIfNew(long updateId, Instant now, String origin) {
        return jpa.registerIfNewWithOrigin(updateId, now, origin) > 0;
    }

    @Override
    @Transactional
    public void complete(long updateId, int totalActions) {
        jpa.complete(updateId, totalActions);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UpdateStatus> status(long updateId) {
        return jpa.findById(updateId)
                .map(
                        e ->
                                new UpdateStatus(
                                        e.getTotalActions(),
                                        e.getReceivedAt(),
                                        INTERNAL_ORIGIN.equals(e.getOrigin())));
    }

    @Override
    @Transactional
    public int removeOld(Instant limit) {
        return jpa.removeOld(limit);
    }
}
