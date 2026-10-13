package br.com.felipeblaft.reports.domain.telegram;

import br.com.felipeblaft.reports.domain.common.ReportType;
import java.util.Objects;
import java.util.UUID;

/** Relatório apresentado no chat (perguntas ou conferência), ao qual texto e áudio se aplicam. */
public record FocusReport(ReportType type, UUID id) {

    public FocusReport {
        Objects.requireNonNull(type);
        Objects.requireNonNull(id);
    }
}
