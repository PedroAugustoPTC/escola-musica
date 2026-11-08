package br.com.felipeblaft.reports.service;

import br.com.felipeblaft.reports.domain.access.Actor;
import br.com.felipeblaft.reports.domain.access.AuthorshipReadDetail;
import br.com.felipeblaft.reports.domain.access.AuthorshipResult;
import br.com.felipeblaft.reports.domain.access.AuthorshipTargetType;
import br.com.felipeblaft.reports.domain.audit.AuditEventType;
import br.com.felipeblaft.reports.domain.common.ReportType;
import br.com.felipeblaft.reports.port.RequestContextPort;
import java.util.Objects;
import java.util.UUID;

/**
 * Registra na trilha o que o professor decide e lê sobre relatórios (FR-044): aprovar, cancelar,
 * consultar o histórico e abrir, baixar ou receber um PDF. O sucesso entra na MESMA transação do
 * caso de uso que o chamou, de modo que sem o evento a operação (ou a leitura) não vale. O canal e
 * o identificador de correlação vêm do contexto da requisição; o evento guarda só identificadores,
 * o tipo do relatório e a versão (nunca nome, CPF nem conteúdo).
 */
public class TeacherReportAuditService {

    private final AuthorshipService authorship;
    private final RequestContextPort context;

    public TeacherReportAuditService(AuthorshipService authorship, RequestContextPort context) {
        this.authorship = Objects.requireNonNull(authorship);
        this.context = Objects.requireNonNull(context);
    }

    /** Aprovação ou cancelamento de um relatório; a versão só existe na aprovação. */
    public void reportDecided(
            UUID teacherId, AuditEventType event, ReportType type, UUID reportId, Integer version) {
        authorship.recordEvent(
                actor(teacherId),
                event.name(),
                target(type),
                reportId,
                version == null ? null : AuthorshipReadDetail.ofDocumentVersion(version),
                AuthorshipResult.SUCCESS,
                null,
                context.correlationId());
    }

    /** Consulta do histórico de um aluno (a primeira página: o histórico não pagina). */
    public void historyQueried(UUID teacherId, UUID studentId) {
        authorship.recordEvent(
                actor(teacherId),
                AuditEventType.HISTORY_QUERY.name(),
                AuthorshipTargetType.STUDENT,
                studentId,
                AuthorshipReadDetail.ofPage(0),
                AuthorshipResult.SUCCESS,
                null,
                context.correlationId());
    }

    /** Leitura de um PDF: enviado no chat quando o canal é o Telegram; aberto ou baixado na Web. */
    public void documentRead(
            UUID teacherId, ReportType type, UUID reportId, Integer version, boolean download) {
        if (!context.active()) {
            // entrega automática do PDF de conferência pelo trabalho de fundo: não é ação do
            // professor
            return;
        }
        Actor actor = actor(teacherId);
        AuditEventType event =
                actor.origin() == br.com.felipeblaft.reports.domain.access.ActionOrigin.TELEGRAM
                        ? AuditEventType.REPORT_DOCUMENT_SENT
                        : download
                                ? AuditEventType.REPORT_DOCUMENT_DOWNLOAD
                                : AuditEventType.REPORT_DOCUMENT_OPEN;
        authorship.recordEvent(
                actor,
                event.name(),
                target(type),
                reportId,
                version == null ? null : AuthorshipReadDetail.ofDocumentVersion(version),
                AuthorshipResult.SUCCESS,
                null,
                context.correlationId());
    }

    /** Fim da sessão Web do professor. */
    public void signedOut(UUID teacherId) {
        authorship.recordEvent(
                actor(teacherId),
                AuditEventType.TEACHER_SIGN_OUT.name(),
                AuthorshipTargetType.TEACHER,
                teacherId,
                null,
                AuthorshipResult.SUCCESS,
                null,
                context.correlationId());
    }

    private Actor actor(UUID teacherId) {
        return Actor.teacher(teacherId, context.origin());
    }

    private static AuthorshipTargetType target(ReportType type) {
        return type == ReportType.SEMESTER
                ? AuthorshipTargetType.SEMESTER_REPORT
                : AuthorshipTargetType.LESSON_REPORT;
    }
}
