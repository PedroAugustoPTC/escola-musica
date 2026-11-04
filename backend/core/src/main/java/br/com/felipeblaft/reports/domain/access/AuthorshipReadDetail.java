package br.com.felipeblaft.reports.domain.access;

import br.com.felipeblaft.reports.domain.common.ReportType;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Detalhe do registro de autoria das leituras da visão da escola (feature 006, data-model.md §4):
 * os filtros válidos e a página da consulta, ou a versão do PDF entregue. Só identificadores,
 * tipo, datas e números; nunca nome, CPF nem conteúdo de relatório. Todos os campos são opcionais.
 *
 * @param filterTeacherId filtro de professor da consulta
 * @param filterReportType filtro de tipo ({@code LESSON} ou {@code SEMESTER}); nulo = os dois
 * @param pageNumber página entregue, a partir de 0
 * @param documentVersion versão do PDF entregue, a partir de 1
 */
public record AuthorshipReadDetail(
        UUID filterTeacherId,
        ReportType filterReportType,
        LocalDate filterPeriodStart,
        LocalDate filterPeriodEnd,
        Integer pageNumber,
        Integer documentVersion) {

    public AuthorshipReadDetail {
        if (pageNumber != null && pageNumber < 0) {
            throw new IllegalArgumentException("página de autoria deve ser maior ou igual a zero");
        }
        if (documentVersion != null && documentVersion < 1) {
            throw new IllegalArgumentException("versão do documento deve ser maior que zero");
        }
    }

    /** Detalhe de uma consulta de histórico: filtros válidos e página. */
    public static AuthorshipReadDetail ofQuery(
            UUID teacherId, ReportType type, LocalDate from, LocalDate to, int pageNumber) {
        return new AuthorshipReadDetail(teacherId, type, from, to, pageNumber, null);
    }

    /** Detalhe de uma entrega de PDF: a versão entregue. */
    public static AuthorshipReadDetail ofDocument(int documentVersion) {
        return new AuthorshipReadDetail(null, null, null, null, null, documentVersion);
    }

    /** Não revela os valores. */
    @Override
    public String toString() {
        return "AuthorshipReadDetail[...]";
    }
}
