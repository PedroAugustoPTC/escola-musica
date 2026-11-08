package br.com.felipeblaft.reports.service;

import br.com.felipeblaft.reports.domain.common.ReportType;
import br.com.felipeblaft.reports.domain.report.DocumentType;
import br.com.felipeblaft.reports.port.PdfDocumentRepository;
import br.com.felipeblaft.reports.port.ReportVersionRepository;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Leitura do PDF {@code OFFICIAL} da versão aprovada (aula e semestral) e montagem do nome
 * sugerido, sem checar quem pede: a checagem de acesso (dono, no caminho do professor; relatório
 * aprovado, no da escola) é de quem chama. Nunca troca de versão: se o documento da versão pedida
 * não existe ou não pode ser lido, devolve vazio.
 */
public class OfficialDocumentService {

    private final ReportVersionRepository versions;
    private final PdfDocumentRepository documents;
    private final DocumentGenerationService generation;

    public OfficialDocumentService(
            ReportVersionRepository versions,
            PdfDocumentRepository documents,
            DocumentGenerationService generation) {
        this.versions = Objects.requireNonNull(versions);
        this.documents = Objects.requireNonNull(documents);
        this.generation = Objects.requireNonNull(generation);
    }

    /** Bytes do PDF oficial da versão pedida, ou vazio se o documento falta ou é ilegível. */
    public Optional<byte[]> readOfficial(ReportType type, UUID reportId, int approvedVersion) {
        return versions.search(type, reportId, approvedVersion)
                .flatMap(version -> documents.search(version.id(), DocumentType.OFFICIAL))
                .flatMap(generation::open);
    }

    /** Nome sugerido do PDF oficial da aula, o mesmo para o professor e para a escola. */
    public static String lessonFileName(LocalDate lessonDate) {
        return "relatorio-aula-" + lessonDate + ".pdf";
    }

    /** Nome sugerido do PDF oficial do semestral, o mesmo para o professor e para a escola. */
    public static String semesterFileName(String periodKey) {
        return "relatorio-semestral-" + periodKey + ".pdf";
    }
}
