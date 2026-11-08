package br.com.felipeblaft.reports.domain.access;

import br.com.felipeblaft.reports.domain.common.ReportType;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Detalhe do registro de autoria das leituras da visão da escola (feature 006, data-model.md §4):
 * os filtros válidos e a página da consulta, ou a versão do PDF entregue. Só identificadores, tipo,
 * datas e números; nunca nome, CPF nem conteúdo de relatório. Todos os campos são opcionais.
 *
 * @param filterTeacherId filtro de professor da consulta
 * @param filterReportType filtro de tipo ({@code LESSON} ou {@code SEMESTER}); nulo = os dois
 * @param pageNumber página entregue, a partir de 0
 * @param documentVersion versão do PDF entregue, a partir de 1
 * @param filterOperation filtro de operação da consulta da trilha (feature 007)
 * @param filterActorId filtro de autor da consulta da trilha
 * @param filterTargetId filtro de alvo da consulta da trilha; também o aluno de um consentimento
 * @param itemCount quantidade de itens tratados (retenção, eliminação, expurgo, verificação)
 */
public record AuthorshipReadDetail(
        UUID filterTeacherId,
        ReportType filterReportType,
        LocalDate filterPeriodStart,
        LocalDate filterPeriodEnd,
        Integer pageNumber,
        Integer documentVersion,
        String filterOperation,
        UUID filterActorId,
        UUID filterTargetId,
        Integer itemCount) {

    /** Detalhe da visão da escola (feature 006), sem os campos da trilha. */
    public AuthorshipReadDetail(
            UUID filterTeacherId,
            ReportType filterReportType,
            LocalDate filterPeriodStart,
            LocalDate filterPeriodEnd,
            Integer pageNumber,
            Integer documentVersion) {
        this(
                filterTeacherId,
                filterReportType,
                filterPeriodStart,
                filterPeriodEnd,
                pageNumber,
                documentVersion,
                null,
                null,
                null,
                null);
    }

    public AuthorshipReadDetail {
        if (pageNumber != null && pageNumber < 0) {
            throw new IllegalArgumentException("página de autoria deve ser maior ou igual a zero");
        }
        if (documentVersion != null && documentVersion < 1) {
            throw new IllegalArgumentException("versão do documento deve ser maior que zero");
        }
        if (itemCount != null && itemCount < 0) {
            throw new IllegalArgumentException("quantidade de itens não pode ser negativa");
        }
    }

    /** Detalhe de um evento que informa só a versão do relatório (aprovada ou lida). */
    public static AuthorshipReadDetail ofDocumentVersion(int documentVersion) {
        return ofDocument(documentVersion);
    }

    /** Detalhe de uma página lida do histórico do professor: só a página. */
    public static AuthorshipReadDetail ofPage(int pageNumber) {
        return new AuthorshipReadDetail(null, null, null, null, pageNumber, null);
    }

    /**
     * Detalhe de uma consulta da trilha: período, filtros de operação, autor e alvo, e a página
     * (feature 007).
     */
    public static AuthorshipReadDetail ofTrailQuery(
            LocalDate from,
            LocalDate to,
            String operation,
            UUID actorId,
            UUID targetId,
            int pageNumber) {
        return new AuthorshipReadDetail(
                null, null, from, to, pageNumber, null, operation, actorId, targetId, null);
    }

    /** Detalhe de um evento que só informa quantos itens tratou. */
    public static AuthorshipReadDetail ofCount(int itemCount) {
        return new AuthorshipReadDetail(
                null, null, null, null, null, null, null, null, null, itemCount);
    }

    /** O aluno a quem o consentimento se refere (a trilha só guarda o identificador). */
    public static AuthorshipReadDetail ofConsentStudent(UUID studentId) {
        return new AuthorshipReadDetail(
                null, null, null, null, null, null, null, null, studentId, null);
    }

    /** Há algum campo de leitura ou de filtro (fora a contagem de itens)? */
    public boolean hasReadFields() {
        return filterTeacherId != null
                || filterReportType != null
                || filterPeriodStart != null
                || filterPeriodEnd != null
                || pageNumber != null
                || documentVersion != null
                || filterOperation != null
                || filterActorId != null
                || filterTargetId != null;
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
