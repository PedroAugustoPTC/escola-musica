package br.com.felipeblaft.reports.domain.history;

import java.util.Comparator;

/**
 * Ordem única do histórico de relatórios aprovados (FR-007), igual para o professor e para o
 * administrador: data de referência decrescente (aula: data da aula; semestral: início do período),
 * depois aprovação decrescente e, por fim, o identificador do relatório decrescente. É uma ordem
 * total e estável; o adaptador de persistência a traduz em SQL e um teste compara as duas. O
 * desempate pelo identificador compara o texto canônico (hexadecimal minúsculo), que equivale à
 * ordem dos bytes usada pelo banco; {@code UUID.compareTo} compara com sinal e discordaria dele.
 */
public final class HistoryOrder {

    public static final Comparator<ApprovedReportRow> NEWEST_FIRST =
            Comparator.comparing(ApprovedReportRow::reference)
                    .thenComparing(ApprovedReportRow::approvedAt)
                    .thenComparing((ApprovedReportRow row) -> row.reportId().toString())
                    .reversed();

    private HistoryOrder() {}
}
