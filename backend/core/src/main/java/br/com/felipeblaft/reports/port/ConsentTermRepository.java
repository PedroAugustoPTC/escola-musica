package br.com.felipeblaft.reports.port;

import br.com.felipeblaft.reports.domain.privacy.ConsentTermVersion;
import java.util.List;

/** Versões do termo de consentimento (FR-032): só acréscimo, nunca edição. */
public interface ConsentTermRepository {

    /** Todas as versões, da mais recente (maior número) à mais antiga. */
    List<ConsentTermVersion> listAll();

    void save(ConsentTermVersion term);
}
