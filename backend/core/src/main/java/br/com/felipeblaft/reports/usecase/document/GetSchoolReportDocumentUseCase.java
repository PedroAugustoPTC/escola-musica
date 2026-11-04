package br.com.felipeblaft.reports.usecase.document;

import br.com.felipeblaft.reports.domain.access.Actor;
import br.com.felipeblaft.reports.domain.access.AuthorshipReadDetail;
import br.com.felipeblaft.reports.domain.access.AuthorshipResult;
import br.com.felipeblaft.reports.domain.access.AuthorshipTargetType;
import br.com.felipeblaft.reports.domain.access.Operation;
import br.com.felipeblaft.reports.domain.common.DomainException;
import br.com.felipeblaft.reports.domain.common.ErrorCode;
import br.com.felipeblaft.reports.domain.common.ReportType;
import br.com.felipeblaft.reports.domain.report.LessonReport;
import br.com.felipeblaft.reports.domain.report.ReportStatus;
import br.com.felipeblaft.reports.domain.semester.SemesterReport;
import br.com.felipeblaft.reports.port.LessonReportRepository;
import br.com.felipeblaft.reports.port.SemesterReportRepository;
import br.com.felipeblaft.reports.port.TransactionPort;
import br.com.felipeblaft.reports.service.ActorAuthorizationService;
import br.com.felipeblaft.reports.service.AuthorshipService;
import br.com.felipeblaft.reports.service.OfficialDocumentService;
import br.com.felipeblaft.reports.usecase.document.GetReportDocumentUseCase.ObtainedDocument;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Entrega, à escola, do PDF oficial da versão aprovada de um relatório de qualquer professor
 * ({@code SCHOOL_REPORT_DOCUMENT_OPEN} e {@code _DOWNLOAD}). A autorização é a primeira instrução;
 * o relatório é carregado por uma consulta só pelo identificador, e ausente ou não aprovado dão o
 * mesmo {@code RESOURCE_NOT_FOUND} (FR-018). Não existe pedido por número de versão. O sucesso é
 * gravado em transação própria, depois da leitura e antes da entrega (se o registro falhar, nada é entregue); a negativa e o
 * PDF indisponível, em transação independente.
 */
public class GetSchoolReportDocumentUseCase {

    /** Modo de entrega: abrir no navegador ou baixar. */
    public enum Disposition {
        OPEN(Operation.SCHOOL_REPORT_DOCUMENT_OPEN),
        DOWNLOAD(Operation.SCHOOL_REPORT_DOCUMENT_DOWNLOAD);

        private final Operation operation;

        Disposition(Operation operation) {
            this.operation = operation;
        }

        public Operation operation() {
            return operation;
        }
    }

    public record Command(
            ReportType type, UUID reportId, Disposition disposition, String correlationId) {

        public Command {
            Objects.requireNonNull(type);
            Objects.requireNonNull(reportId);
            Objects.requireNonNull(disposition);
            Objects.requireNonNull(correlationId);
        }
    }

    /** Relatório aprovado já resolvido: versão aprovada e nome sugerido. */
    private record Approved(int version, String fileName) {}

    private final ActorAuthorizationService authorization;
    private final LessonReportRepository lessonReports;
    private final SemesterReportRepository semesterReports;
    private final OfficialDocumentService officialDocument;
    private final AuthorshipService authorship;
    private final TransactionPort transaction;

    public GetSchoolReportDocumentUseCase(
            ActorAuthorizationService authorization,
            LessonReportRepository lessonReports,
            SemesterReportRepository semesterReports,
            OfficialDocumentService officialDocument,
            AuthorshipService authorship,
            TransactionPort transaction) {
        this.authorization = Objects.requireNonNull(authorization);
        this.lessonReports = Objects.requireNonNull(lessonReports);
        this.semesterReports = Objects.requireNonNull(semesterReports);
        this.officialDocument = Objects.requireNonNull(officialDocument);
        this.authorship = Objects.requireNonNull(authorship);
        this.transaction = Objects.requireNonNull(transaction);
    }

    public ObtainedDocument run(Actor actor, Command command) {
        Operation operation = command.disposition().operation();
        authorization.require(actor, operation);
        AuthorshipTargetType target =
                command.type() == ReportType.LESSON
                        ? AuthorshipTargetType.LESSON_REPORT
                        : AuthorshipTargetType.SEMESTER_REPORT;
        // consulta e leitura do PDF ficam fora da transação (E/S lenta não segura conexão); só a
        // gravação do sucesso é atômica, e se ela falhar nada é entregue
        Optional<Approved> found = findApproved(command.type(), command.reportId());
        if (found.isEmpty()) {
            throw refuse(actor, operation, target, command, ErrorCode.RESOURCE_NOT_FOUND);
        }
        Approved approved = found.get();
        Optional<byte[]> bytes =
                officialDocument.readOfficial(
                        command.type(), command.reportId(), approved.version());
        if (bytes.isEmpty()) {
            throw refuse(actor, operation, target, command, ErrorCode.INTERNAL_ERROR);
        }
        transaction.run(
                () ->
                        authorship.recordRead(
                                actor,
                                operation,
                                target,
                                command.reportId(),
                                AuthorshipReadDetail.ofDocument(approved.version()),
                                AuthorshipResult.SUCCESS,
                                null,
                                command.correlationId()));
        return new ObtainedDocument(bytes.get(), approved.fileName());
    }

    /** Uma consulta só pelo identificador; ausente e não aprovado voltam vazios do mesmo jeito. */
    private Optional<Approved> findApproved(ReportType type, UUID reportId) {
        if (type == ReportType.LESSON) {
            Optional<LessonReport> report = lessonReports.searchById(reportId);
            if (report.isPresent() && report.get().status() == ReportStatus.APPROVED) {
                return Optional.of(lessonApproved(report.get()));
            }
            return Optional.empty();
        }
        Optional<SemesterReport> report = semesterReports.searchById(reportId);
        if (report.isPresent() && report.get().status() == ReportStatus.APPROVED) {
            return Optional.of(semesterApproved(report.get()));
        }
        return Optional.empty();
    }

    private Approved lessonApproved(LessonReport report) {
        LocalDate lessonDate =
                lessonReports.searchLesson(report.lessonId()).orElseThrow().lessonDate();
        return new Approved(
                report.approvedVersion(), OfficialDocumentService.lessonFileName(lessonDate));
    }

    private static Approved semesterApproved(SemesterReport report) {
        return new Approved(
                report.approvedVersion(),
                OfficialDocumentService.semesterFileName(report.period().key()));
    }

    /** Grava a negativa em transação independente e devolve a exceção a lançar. */
    private DomainException refuse(
            Actor actor,
            Operation operation,
            AuthorshipTargetType target,
            Command command,
            ErrorCode code) {
        authorship.recordRead(
                actor,
                operation,
                target,
                command.reportId(),
                null,
                AuthorshipResult.FAILURE,
                code.name(),
                command.correlationId());
        return new DomainException(code);
    }
}
