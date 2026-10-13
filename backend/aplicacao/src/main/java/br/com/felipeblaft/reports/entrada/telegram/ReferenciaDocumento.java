package br.com.felipeblaft.reports.entrada.telegram;

import br.com.felipeblaft.reports.domain.common.ReportType;
import java.util.UUID;

/**
 * Identifica o PDF que um {@link SaidaTelegram.Documento} carrega, sem os bytes: é o que a rota
 * interna do canal usa para emitir o identificador opaco que o orquestrador troca pelos bytes.
 * {@code versao} nulo significa o PDF oficial (aprovado).
 */
public record ReferenciaDocumento(
        UUID professorId, ReportType tipo, UUID relatorioId, Integer versao) {

    public static ReferenciaDocumento conferencia(
            UUID professorId, ReportType tipo, UUID relatorioId, int versao) {
        return new ReferenciaDocumento(professorId, tipo, relatorioId, versao);
    }

    public static ReferenciaDocumento oficial(UUID professorId, ReportType tipo, UUID relatorioId) {
        return new ReferenciaDocumento(professorId, tipo, relatorioId, null);
    }
}
