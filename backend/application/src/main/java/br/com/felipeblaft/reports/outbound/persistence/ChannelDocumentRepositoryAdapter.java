package br.com.felipeblaft.reports.outbound.persistence;

import br.com.felipeblaft.reports.domain.channel.ChannelDocument;
import br.com.felipeblaft.reports.domain.common.ReportType;
import br.com.felipeblaft.reports.port.ChannelDocumentRepository;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
class ChannelDocumentRepositoryAdapter implements ChannelDocumentRepository {

    private final ChannelDocumentJpa jpa;

    ChannelDocumentRepositoryAdapter(ChannelDocumentJpa jpa) {
        this.jpa = jpa;
    }

    @Override
    @Transactional
    public void emit(ChannelDocument document) {
        ChannelDocumentEntity e = new ChannelDocumentEntity();
        e.tokenHash = document.tokenHash();
        e.teacherId = document.teacherId();
        e.reportType = document.reportType().name();
        e.reportId = document.reportId();
        e.version = document.version();
        e.fileName = document.fileName();
        e.createdAt = document.createdAt();
        e.expiresAt = document.expiresAt();
        jpa.saveAndFlush(e);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ChannelDocument> searchByHash(String tokenHash) {
        return jpa.findById(tokenHash)
                .map(
                        e ->
                                new ChannelDocument(
                                        e.tokenHash,
                                        e.teacherId,
                                        ReportType.valueOf(e.reportType),
                                        e.reportId,
                                        e.version,
                                        e.fileName,
                                        e.createdAt,
                                        e.expiresAt));
    }

    @Override
    @Transactional
    public int purgeExpired(Instant now) {
        return jpa.removeExpired(now);
    }
}
