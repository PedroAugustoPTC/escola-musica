package br.com.felipeblaft.reports.usecase.channel;

import br.com.felipeblaft.reports.domain.channel.ChannelDocument;
import br.com.felipeblaft.reports.domain.channel.RetryPolicy;
import br.com.felipeblaft.reports.domain.common.ReportType;
import br.com.felipeblaft.reports.port.ChannelDocumentRepository;
import br.com.felipeblaft.reports.port.ClockPort;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Objects;
import java.util.UUID;

/**
 * Emite o identificador opaco de um PDF para o orquestrador (R-08): token de 256 bits (Base64url,
 * 43 caracteres), com só o hash SHA-256 guardado. Vale pelo prazo de entrega mais uma hora, para
 * cobrir todas as retentativas.
 */
public class EmitChannelDocumentUseCase {

    public static final Duration VALIDITY_SLACK = Duration.ofHours(1);

    private static final SecureRandom RANDOM = new SecureRandom();

    private final ChannelDocumentRepository documents;
    private final ClockPort clock;
    private final RetryPolicy policy;

    public EmitChannelDocumentUseCase(
            ChannelDocumentRepository documents, ClockPort clock, RetryPolicy policy) {
        this.documents = Objects.requireNonNull(documents);
        this.clock = Objects.requireNonNull(clock);
        this.policy = Objects.requireNonNull(policy);
    }

    /**
     * @param versao número da versão de conferência; nulo para o PDF oficial
     * @return o token a entregar ao orquestrador (nunca mais é recuperável do banco)
     */
    public String run(
            UUID teacherId, ReportType type, UUID reportId, Integer version, String fileName) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant now = clock.now();
        documents.emit(
                new ChannelDocument(
                        ChannelDocument.hashDoToken(token),
                        teacherId,
                        type,
                        reportId,
                        version,
                        fileName,
                        now,
                        now.plus(policy.deadline()).plus(VALIDITY_SLACK)));
        return token;
    }
}
