package br.com.felipeblaft.reports.outbound.persistence;

import br.com.felipeblaft.reports.domain.common.ReportType;
import br.com.felipeblaft.reports.domain.privacy.RetentionPolicy;
import br.com.felipeblaft.reports.domain.report.DocumentType;
import br.com.felipeblaft.reports.domain.report.PdfDocument;
import br.com.felipeblaft.reports.port.PdfDocumentRepository;
import br.com.felipeblaft.reports.port.PendingFileRemoval;
import br.com.felipeblaft.reports.port.RetentionPdfCandidate;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class PdfDocumentRepositoryAdapter implements PdfDocumentRepository {

    private final PdfDocumentJpa jpa;
    private final JdbcTemplate jdbc;

    PdfDocumentRepositoryAdapter(PdfDocumentJpa jpa, JdbcTemplate jdbc) {
        this.jpa = jpa;
        this.jdbc = jdbc;
    }

    @Override
    public void save(PdfDocument document) {
        jpa.saveAndFlush(LessonReportMapper.toEntity(document));
    }

    @Override
    public Optional<PdfDocument> search(UUID versionId, DocumentType type) {
        return jpa.findByVersionIdAndType(versionId, type.name()).map(LessonReportMapper::toDomain);
    }

    @Override
    public List<UUID> findRetentionCandidates(
            RetentionPolicy policy, Instant now, int limit, UUID afterId) {
        Timestamp cutoff = RetentionCandidates.cutoff(now, policy.unapprovedPdfDays().value());
        String sql =
                "SELECT id FROM ("
                        + candidates("lesson_report", "LESSON")
                        + " UNION "
                        + candidates("semester_report", "SEMESTER")
                        + ") c WHERE (?::uuid IS NULL OR id > ?::uuid) ORDER BY id LIMIT ?";
        return jdbc.queryForList(sql, UUID.class, cutoff, cutoff, afterId, afterId, limit);
    }

    private static String candidates(String reportTable, String reportType) {
        return "SELECT p.id FROM pdf_document p"
                + " JOIN report_version v ON v.id = p.version_id AND v.report_type = '"
                + reportType
                + "' JOIN "
                + reportTable
                + " r ON r.id = v.report_id"
                + " WHERE p.content_removed_at IS NULL AND r.status = 'APPROVED'"
                + " AND r.approved_at <= ? AND v.number <> r.approved_version";
    }

    @Override
    public Optional<RetentionPdfCandidate> findForRetention(UUID pdfId) {
        Optional<PdfDocument> pdf = jpa.findById(pdfId).map(LessonReportMapper::toDomain);
        if (pdf.isEmpty()) {
            return Optional.empty();
        }
        Map<String, Object> version =
                jdbc.queryForMap(
                        "SELECT v.report_type, v.report_id, v.number FROM report_version v"
                                + " WHERE v.id = ?",
                        pdf.get().versionId());
        String table =
                ReportType.LESSON.name().equals(version.get("report_type"))
                        ? "lesson_report"
                        : "semester_report";
        UUID reportId = (UUID) version.get("report_id");
        int number = ((Number) version.get("number")).intValue();
        // bloqueia a linha do relatório: aprovar em paralelo espera esta transação terminar
        List<Map<String, Object>> report =
                jdbc.queryForList(
                        "SELECT approved_at, approved_version FROM "
                                + table
                                + " WHERE id = ? FOR UPDATE",
                        reportId);
        if (report.isEmpty()) {
            return Optional.empty();
        }
        Timestamp approvedAt = (Timestamp) report.getFirst().get("approved_at");
        Number approvedVersion = (Number) report.getFirst().get("approved_version");
        boolean approvedVersionPdf = approvedVersion != null && approvedVersion.intValue() == number;
        return Optional.of(
                new RetentionPdfCandidate(
                        pdf.get(),
                        approvedVersionPdf,
                        approvedAt == null ? null : approvedAt.toInstant()));
    }

    @Override
    public void markContentRemoved(UUID pdfId, Instant now) {
        jdbc.update(
                "UPDATE pdf_document SET content_removed_at = ?"
                        + " WHERE id = ? AND content_removed_at IS NULL",
                Timestamp.from(now),
                pdfId);
    }

    @Override
    public int markContentRemovedForReport(ReportType type, UUID reportId, Instant now) {
        return jdbc.update(
                "UPDATE pdf_document SET content_removed_at = ?"
                        + " WHERE content_removed_at IS NULL AND version_id IN"
                        + " (SELECT id FROM report_version WHERE report_type = ? AND report_id = ?)",
                Timestamp.from(now),
                type.name(),
                reportId);
    }

    @Override
    public List<PendingFileRemoval> findPendingFileRemovals(int limit) {
        return jdbc.query(
                "SELECT id, storage_key FROM pdf_document"
                        + " WHERE content_removed_at IS NOT NULL AND file_removed_at IS NULL"
                        + " ORDER BY id LIMIT ?",
                (rs, row) ->
                        new PendingFileRemoval(
                                rs.getObject("id", UUID.class), rs.getString("storage_key")),
                limit);
    }

    @Override
    public void markFileRemoved(UUID pdfId, Instant now) {
        jdbc.update(
                "UPDATE pdf_document SET file_removed_at = ? WHERE id = ?",
                Timestamp.from(now),
                pdfId);
    }
}
