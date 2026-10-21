package br.com.felipeblaft.reports.architecture.fixture;

import br.com.felipeblaft.reports.domain.access.AuthorshipRecord;
import java.util.UUID;

/** Porta de autoria que viola a regra de somente inserção (apenas autoteste do ArchUnit). */
public interface MutableRecorderFixture {

    void record(AuthorshipRecord record);

    void delete(UUID id);
}
