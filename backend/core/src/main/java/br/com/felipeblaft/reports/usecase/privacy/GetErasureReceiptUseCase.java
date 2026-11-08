package br.com.felipeblaft.reports.usecase.privacy;

import br.com.felipeblaft.reports.domain.access.Actor;
import br.com.felipeblaft.reports.domain.access.AuthorshipTargetType;
import br.com.felipeblaft.reports.domain.access.Operation;
import br.com.felipeblaft.reports.domain.common.DomainException;
import br.com.felipeblaft.reports.domain.common.ErrorCode;
import br.com.felipeblaft.reports.domain.privacy.ErasureRequest;
import br.com.felipeblaft.reports.domain.privacy.ErasureScope;
import br.com.felipeblaft.reports.domain.privacy.ErasureStatus;
import br.com.felipeblaft.reports.domain.privacy.PrivacyOfficer;
import br.com.felipeblaft.reports.port.ErasureRequestRepository;
import br.com.felipeblaft.reports.service.ActorAuthorizationService;
import br.com.felipeblaft.reports.service.AuthorshipService;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * Dados do comprovante de uma eliminação concluída ({@code ERASURE_RECEIPT_READ}, FR-025): só o
 * identificador curto do aluno, o protocolo, as datas, o alcance em números e o contato do
 * responsável pela LGPD. Nunca leva nome, CPF nem dado do aluno ou de quem pediu.
 */
public class GetErasureReceiptUseCase {

    public record Receipt(
            UUID requestId,
            String studentShortId,
            String protocolReference,
            LocalDate requestedOn,
            Instant completedAt,
            ErasureScope scope,
            PrivacyOfficer privacyContact) {

        @Override
        public String toString() {
            return "GetErasureReceiptUseCase.Receipt";
        }
    }

    private final ActorAuthorizationService authorization;
    private final ErasureRequestRepository erasures;
    private final PrivacyOfficer privacyContact;
    private final AuthorshipService authorship;

    public GetErasureReceiptUseCase(
            ActorAuthorizationService authorization,
            ErasureRequestRepository erasures,
            PrivacyOfficer privacyContact,
            AuthorshipService authorship) {
        this.authorization = Objects.requireNonNull(authorization);
        this.erasures = Objects.requireNonNull(erasures);
        this.privacyContact = Objects.requireNonNull(privacyContact);
        this.authorship = Objects.requireNonNull(authorship);
    }

    public Receipt run(Actor actor, UUID requestId, String correlationId) {
        authorization.require(actor, Operation.ERASURE_RECEIPT_READ);
        ErasureRequest request =
                erasures.findById(requestId)
                        .filter(found -> found.status() == ErasureStatus.COMPLETED)
                        .orElseThrow(() -> new DomainException(ErrorCode.RESOURCE_NOT_FOUND));
        authorship.record(
                actor,
                Operation.ERASURE_RECEIPT_READ,
                AuthorshipTargetType.ERASURE_REQUEST,
                requestId,
                correlationId);
        return new Receipt(
                request.id(),
                shortId(request.studentId()),
                request.protocolReference(),
                request.requestedOn(),
                request.completedAt(),
                request.scope().orElseThrow(),
                privacyContact);
    }

    static String shortId(UUID studentId) {
        return studentId.toString().replace("-", "").substring(0, 6).toLowerCase(Locale.ROOT);
    }
}
