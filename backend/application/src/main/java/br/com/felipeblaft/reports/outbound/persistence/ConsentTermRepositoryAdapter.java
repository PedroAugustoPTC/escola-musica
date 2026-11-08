package br.com.felipeblaft.reports.outbound.persistence;

import br.com.felipeblaft.reports.domain.privacy.ConsentTermVersion;
import br.com.felipeblaft.reports.port.ConsentTermRepository;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
class ConsentTermRepositoryAdapter implements ConsentTermRepository {

    private final ConsentTermJpa jpa;

    ConsentTermRepositoryAdapter(ConsentTermJpa jpa) {
        this.jpa = jpa;
    }

    @Override
    public List<ConsentTermVersion> listAll() {
        return jpa.listNewestFirst().stream().map(ConsentTermRepositoryAdapter::toDomain).toList();
    }

    @Override
    public void save(ConsentTermVersion term) {
        jpa.save(
                new ConsentTermEntity(
                        term.id(),
                        term.number(),
                        term.publishedOn(),
                        term.text(),
                        term.requiresNewConsent(),
                        term.createdBy(),
                        term.createdAt()));
    }

    static ConsentTermVersion toDomain(ConsentTermEntity entity) {
        return new ConsentTermVersion(
                entity.getId(),
                entity.getNumber(),
                entity.getPublishedOn(),
                entity.getText(),
                entity.isRequiresNewConsent(),
                entity.getCreatedBy(),
                entity.getCreatedAt());
    }
}
