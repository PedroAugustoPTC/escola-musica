package br.com.felipeblaft.reports.usecase.channel;

import br.com.felipeblaft.reports.domain.channel.ChannelDocument;
import br.com.felipeblaft.reports.domain.common.DomainException;
import br.com.felipeblaft.reports.port.ChannelDocumentRepository;
import br.com.felipeblaft.reports.port.ClockPort;
import br.com.felipeblaft.reports.usecase.document.GetReportDocumentUseCase;
import br.com.felipeblaft.reports.usecase.document.GetReportDocumentUseCase.ObtainedDocument;
import java.util.Objects;
import java.util.Optional;

/**
 * Entrega os bytes do PDF por identificador opaco (R-08). Identificador desconhecido, vencido ou
 * cujo documento sumiu resultam todos em "não encontrado", indistinguíveis. A checagem de dono e de
 * associação é repetida na leitura, pelo mesmo caso de uso do portal. Falha real de leitura propaga
 * (nunca bytes parciais).
 */
public class GetChannelDocumentUseCase {

    private final ChannelDocumentRepository documents;
    private final GetReportDocumentUseCase getDocument;
    private final ClockPort clock;

    public GetChannelDocumentUseCase(
            ChannelDocumentRepository documents,
            GetReportDocumentUseCase getDocument,
            ClockPort clock) {
        this.documents = Objects.requireNonNull(documents);
        this.getDocument = Objects.requireNonNull(getDocument);
        this.clock = Objects.requireNonNull(clock);
    }

    /**
     * @param token o identificador opaco recebido; só o seu hash é consultado
     */
    public Optional<ObtainedDocument> run(String token) {
        Optional<ChannelDocument> reference =
                documents
                        .searchByHash(ChannelDocument.hashDoToken(token))
                        .filter(document -> document.valid(clock.now()));
        if (reference.isEmpty()) {
            return Optional.empty();
        }
        ChannelDocument document = reference.get();
        try {
            return Optional.of(
                    document.official()
                            ? getDocument.official(
                                    document.teacherId(),
                                    document.reportType(),
                                    document.reportId())
                            : getDocument.review(
                                    document.teacherId(),
                                    document.reportType(),
                                    document.reportId(),
                                    document.version()));
        } catch (DomainException withoutAccess) {
            // relatório sumiu, deixou de existir a associação ou não está mais aprovado: nenhum
            // motivo é distinguível de "identificador desconhecido" (FR-022)
            return Optional.empty();
        }
    }
}
