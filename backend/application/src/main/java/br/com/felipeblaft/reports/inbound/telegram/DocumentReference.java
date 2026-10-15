package br.com.felipeblaft.reports.inbound.telegram;

import br.com.felipeblaft.reports.domain.common.ReportType;
import java.util.UUID;

/**
 * Identifica o PDF que um {@link TelegramOutput.Document} carrega, sem os bytes: é o que a rota
 * interna do canal usa para emitir o identificador opaco que o orquestrador troca pelos bytes.
 * {@code versao} nulo significa o PDF oficial (aprovado).
 */
public record DocumentReference(UUID teacherId, ReportType type, UUID reportId, Integer version) {

    public static DocumentReference review(
            UUID teacherId, ReportType type, UUID reportId, int version) {
        return new DocumentReference(teacherId, type, reportId, version);
    }

    public static DocumentReference official(UUID teacherId, ReportType type, UUID reportId) {
        return new DocumentReference(teacherId, type, reportId, null);
    }
}
