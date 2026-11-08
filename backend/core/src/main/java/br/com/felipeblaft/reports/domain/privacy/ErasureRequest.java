package br.com.felipeblaft.reports.domain.privacy;

import br.com.felipeblaft.reports.domain.common.DomainException;
import br.com.felipeblaft.reports.domain.common.ErrorCode;
import br.com.felipeblaft.reports.domain.registry.PersonName;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Pedido de eliminação dos dados de um aluno (FR-020 a FR-026). O nome de quem pediu existe só até
 * a conclusão; o {@code toString} nunca o revela.
 *
 * @param requesterName {@code null} depois de {@code COMPLETED}
 * @param pending confirmação pendente, ou {@code null} fora da prévia
 * @param recordedScope alcance gravado na confirmação, ou {@code null} antes dela
 */
public record ErasureRequest(
        UUID id,
        UUID studentId,
        String requesterRole,
        String requesterName,
        LocalDate requestedOn,
        String receivedVia,
        String protocolReference,
        ErasureStatus status,
        UUID registeredBy,
        ErasureConfirmation pending,
        UUID confirmedBy,
        Instant confirmedAt,
        Instant completedAt,
        ErasureScope recordedScope,
        Instant createdAt) {

    private static final Pattern PROTOCOL = Pattern.compile("^[A-Za-z0-9./-]{1,40}$");
    private static final Set<String> ROLES =
            Set.of("STUDENT", "MOTHER", "FATHER", "LEGAL_GUARDIAN", "OTHER");
    private static final Set<String> VIAS =
            Set.of("IN_PERSON", "EMAIL", "PHONE", "LETTER", "OTHER");

    /** Cria o pedido em {@code RECEIVED}, validando a entrada. */
    public static ErasureRequest register(
            UUID id,
            UUID studentId,
            String requesterRole,
            String requesterName,
            LocalDate requestedOn,
            String receivedVia,
            String protocolReference,
            UUID registeredBy,
            LocalDate today,
            Instant now) {
        if (requesterRole == null || !ROLES.contains(requesterRole)) {
            throw invalid("requesterRole");
        }
        if (receivedVia == null || !VIAS.contains(receivedVia)) {
            throw invalid("receivedVia");
        }
        if (protocolReference == null || !PROTOCOL.matcher(protocolReference).matches()) {
            throw invalid("protocolReference");
        }
        if (requestedOn == null || requestedOn.isAfter(today)) {
            throw invalid("requestedOn");
        }
        String name;
        try {
            name = PersonName.of(requesterName).value();
        } catch (DomainException error) {
            throw invalid("requesterName");
        }
        return new ErasureRequest(
                id,
                studentId,
                requesterRole,
                name,
                requestedOn,
                receivedVia,
                protocolReference,
                ErasureStatus.RECEIVED,
                registeredBy,
                null,
                null,
                null,
                null,
                null,
                now);
    }

    public Optional<ErasureConfirmation> confirmation() {
        return Optional.ofNullable(pending);
    }

    public Optional<ErasureScope> scope() {
        return Optional.ofNullable(recordedScope);
    }

    /**
     * Emite novo token; o anterior deixa de valer. Também serve para retomar {@code IN_PROGRESS}.
     */
    public ErasureRequest withPreview(String tokenHash, Instant now) {
        requireNotCompleted();
        return copy(
                status,
                ErasureConfirmation.issue(tokenHash, now),
                confirmedBy,
                confirmedAt,
                completedAt,
                recordedScope);
    }

    /** Consome o token e passa a {@code IN_PROGRESS}, gravando o alcance. */
    public ErasureRequest confirm(String tokenHash, UUID by, ErasureScope scope, Instant now) {
        requireNotCompleted();
        if (pending == null || !pending.matches(tokenHash, now)) {
            throw new DomainException(ErrorCode.CONFIRMATION_EXPIRED);
        }
        return copy(ErasureStatus.IN_PROGRESS, null, by, now, null, scope);
    }

    /** Conclui depois que os arquivos foram removidos; apaga o nome de quem pediu. */
    public ErasureRequest complete(Instant now) {
        requireNotCompleted();
        if (status != ErasureStatus.IN_PROGRESS) {
            throw new DomainException(ErrorCode.INVALID_DATA, Map.of("status", status.name()));
        }
        return new ErasureRequest(
                id,
                studentId,
                requesterRole,
                null,
                requestedOn,
                receivedVia,
                protocolReference,
                ErasureStatus.COMPLETED,
                registeredBy,
                null,
                confirmedBy,
                confirmedAt,
                now,
                recordedScope,
                createdAt);
    }

    private void requireNotCompleted() {
        if (status == ErasureStatus.COMPLETED) {
            throw new DomainException(ErrorCode.ERASURE_ALREADY_COMPLETED);
        }
    }

    private ErasureRequest copy(
            ErasureStatus newStatus,
            ErasureConfirmation newPending,
            UUID newConfirmedBy,
            Instant newConfirmedAt,
            Instant newCompletedAt,
            ErasureScope newScope) {
        return new ErasureRequest(
                id,
                studentId,
                requesterRole,
                requesterName,
                requestedOn,
                receivedVia,
                protocolReference,
                newStatus,
                registeredBy,
                newPending,
                newConfirmedBy,
                newConfirmedAt,
                newCompletedAt,
                newScope,
                createdAt);
    }

    private static DomainException invalid(String field) {
        return new DomainException(
                ErrorCode.VALIDATION_FAILED,
                Map.of("fields", Map.of(field, ErrorCode.INVALID_DATA.name())));
    }

    @Override
    public String toString() {
        return "ErasureRequest[id=" + id + ", status=" + status + "]";
    }
}
