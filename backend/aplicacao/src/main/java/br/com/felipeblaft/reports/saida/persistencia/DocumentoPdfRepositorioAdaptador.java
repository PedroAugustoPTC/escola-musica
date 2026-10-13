package br.com.felipeblaft.reports.saida.persistencia;

import br.com.felipeblaft.reports.domain.report.DocumentType;
import br.com.felipeblaft.reports.domain.report.PdfDocument;
import br.com.felipeblaft.reports.port.PdfDocumentRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
class DocumentoPdfRepositorioAdaptador implements PdfDocumentRepository {

    private final DocumentoPdfJpa jpa;

    DocumentoPdfRepositorioAdaptador(DocumentoPdfJpa jpa) {
        this.jpa = jpa;
    }

    @Override
    public void save(PdfDocument documento) {
        jpa.saveAndFlush(RelatorioAulaMapeador.paraEntidade(documento));
    }

    @Override
    public Optional<PdfDocument> search(UUID versaoId, DocumentType tipo) {
        return jpa.findByVersaoIdAndTipo(versaoId, tipo.name())
                .map(RelatorioAulaMapeador::paraDominio);
    }
}
