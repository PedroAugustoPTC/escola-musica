package br.com.felipeblaft.reports.outbound.persistence;

import br.com.felipeblaft.reports.domain.common.Channel;
import br.com.felipeblaft.reports.domain.common.Medium;
import br.com.felipeblaft.reports.domain.common.ReportType;
import br.com.felipeblaft.reports.domain.report.DocumentType;
import br.com.felipeblaft.reports.domain.report.InteractionResult;
import br.com.felipeblaft.reports.domain.report.InteractionType;
import br.com.felipeblaft.reports.domain.report.Lesson;
import br.com.felipeblaft.reports.domain.report.LessonReport;
import br.com.felipeblaft.reports.domain.report.LessonReportContent;
import br.com.felipeblaft.reports.domain.report.PdfDocument;
import br.com.felipeblaft.reports.domain.report.ReportInteraction;
import br.com.felipeblaft.reports.domain.report.ReportStatus;
import br.com.felipeblaft.reports.domain.report.ReportStep;
import br.com.felipeblaft.reports.domain.report.ReportVersion;
import br.com.felipeblaft.reports.domain.report.VersionContent;
import br.com.felipeblaft.reports.domain.report.VersionOrigin;
import br.com.felipeblaft.reports.outbound.persistence.ReportEntities.LessonEntity;
import br.com.felipeblaft.reports.outbound.persistence.ReportEntities.LessonReportEntity;
import br.com.felipeblaft.reports.outbound.persistence.ReportEntities.PdfDocumentEntity;
import br.com.felipeblaft.reports.outbound.persistence.ReportEntities.ReportInteractionEntity;
import br.com.felipeblaft.reports.outbound.persistence.ReportEntities.ReportVersionEntity;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import tools.jackson.core.type.TypeReference;

/** Conversão entre as entidades do relatório de aula e o domínio (seções em jsonb). */
final class LessonReportMapper {

    private static final TypeReference<List<String>> LIST = new TypeReference<>() {};

    /** Leitores do conteúdo de versão por tipo de relatório (o semestral registra o seu). */
    static final Map<ReportType, Function<String, VersionContent>> READERS = new HashMap<>();

    static {
        READERS.put(
                ReportType.LESSON, json -> JsonPersistence.read(json, LessonReportContent.class));
        READERS.put(
                ReportType.SEMESTER,
                json ->
                        JsonPersistence.read(
                                json,
                                br.com.felipeblaft.reports.domain.semester.SemesterReportContent
                                        .class));
    }

    private LessonReportMapper() {}

    static LessonEntity toEntity(Lesson lesson) {
        LessonEntity e = new LessonEntity();
        e.id = lesson.id();
        e.teacherId = lesson.teacherId();
        e.studentId = lesson.studentId();
        e.lessonDate = lesson.lessonDate();
        e.createdAt = lesson.createdAt();
        return e;
    }

    static Lesson toDomain(LessonEntity e) {
        return new Lesson(e.id, e.teacherId, e.studentId, e.lessonDate, e.createdAt);
    }

    static LessonReportEntity toEntity(LessonReport r) {
        LessonReportEntity e = new LessonReportEntity();
        e.id = r.id();
        e.lessonId = r.lessonId();
        e.teacherId = r.teacherId();
        e.studentId = r.studentId();
        e.transcription = r.transcription();
        e.sourceRemovedAt = r.sourceRemovedAt();
        e.anonymizedAt = r.anonymizedAt();
        e.content = JsonPersistence.write(r.content());
        e.pendingQuestions = JsonPersistence.write(r.pendingQuestions());
        e.questionRounds = r.questionRounds();
        e.status = r.status().name();
        e.processingStep = r.step() == null ? null : r.step().name();
        e.currentVersion = r.currentVersion();
        e.originChannel = r.originChannel().name();
        e.failureReason = r.failureReason();
        e.createdAt = r.createdAt();
        e.updatedAt = r.updatedAt();
        e.approvedAt = r.approvedAt();
        e.approvedVersion = r.approvedVersion();
        e.approvalChannel = r.approvalChannel() == null ? null : r.approvalChannel().name();
        e.cancelledAt = r.cancelledAt();
        e.processingTimeMs = r.processingTimeMs();
        e.lockVersion = r.version();
        return e;
    }

    static LessonReport toDomain(LessonReportEntity e) {
        return new LessonReport(
                e.id,
                e.lessonId,
                e.teacherId,
                e.studentId,
                e.transcription,
                JsonPersistence.read(e.content, LessonReportContent.class),
                JsonPersistence.read(e.pendingQuestions, LIST),
                e.questionRounds,
                ReportStatus.valueOf(e.status),
                e.processingStep == null ? null : ReportStep.valueOf(e.processingStep),
                e.currentVersion,
                Channel.valueOf(e.originChannel),
                e.failureReason,
                e.createdAt,
                e.updatedAt,
                e.approvedAt,
                e.approvedVersion,
                e.approvalChannel == null ? null : Channel.valueOf(e.approvalChannel),
                e.cancelledAt,
                e.processingTimeMs,
                e.lockVersion,
                e.sourceRemovedAt,
                e.anonymizedAt);
    }

    /** Conteúdo esvaziado pela retenção: o banco guarda {} e o domínio recebe o conteúdo vazio. */
    private static VersionContent removedContent(ReportType type) {
        return type == ReportType.LESSON
                ? LessonReportContent.empty()
                : br.com.felipeblaft.reports.domain.semester.SemesterReportContent.empty();
    }

    static ReportVersionEntity toEntity(ReportVersion v) {
        ReportVersionEntity e = new ReportVersionEntity();
        e.id = v.id();
        e.reportType = v.reportType().name();
        e.reportId = v.reportId();
        e.number = v.number();
        e.content = JsonPersistence.write(v.content());
        e.origin = v.origin().name();
        e.interactionId = v.interactionId();
        e.channel = v.channel().name();
        e.promptVersion = v.promptVersion();
        e.createdAt = v.createdAt();
        e.contentRemovedAt = v.contentRemovedAt();
        return e;
    }

    static ReportVersion toDomain(ReportVersionEntity e) {
        ReportType type = ReportType.valueOf(e.reportType);
        return new ReportVersion(
                e.id,
                type,
                e.reportId,
                e.number,
                e.contentRemovedAt == null
                        ? READERS.get(type).apply(e.content)
                        : removedContent(type),
                VersionOrigin.valueOf(e.origin),
                e.interactionId,
                Channel.valueOf(e.channel),
                e.promptVersion,
                e.createdAt,
                e.contentRemovedAt);
    }

    static ReportInteractionEntity toEntity(ReportInteraction i) {
        ReportInteractionEntity e = new ReportInteractionEntity();
        e.id = i.id();
        e.reportType = i.reportType().name();
        e.reportId = i.reportId();
        e.type = i.type().name();
        e.channel = i.channel().name();
        e.medium = i.medium().name();
        e.text = i.text();
        e.textRemovedAt = i.textRemovedAt();
        e.baseVersion = i.baseVersion();
        e.round = i.round();
        e.result = i.result().name();
        e.resultingVersion = i.resultingVersion();
        e.createdAt = i.createdAt();
        return e;
    }

    static ReportInteraction toDomain(ReportInteractionEntity e) {
        return new ReportInteraction(
                e.id,
                ReportType.valueOf(e.reportType),
                e.reportId,
                InteractionType.valueOf(e.type),
                Channel.valueOf(e.channel),
                Medium.valueOf(e.medium),
                e.text,
                e.baseVersion,
                e.round,
                InteractionResult.valueOf(e.result),
                e.resultingVersion,
                e.createdAt,
                e.textRemovedAt);
    }

    static PdfDocumentEntity toEntity(PdfDocument d) {
        PdfDocumentEntity e = new PdfDocumentEntity();
        e.id = d.id();
        e.versionId = d.versionId();
        e.type = d.type().name();
        e.storageKey = d.storageKey();
        e.sizeBytes = d.sizeBytes();
        e.sha256 = d.sha256();
        e.generatedAt = d.generatedAt();
        e.contentRemovedAt = d.contentRemovedAt();
        return e;
    }

    static PdfDocument toDomain(PdfDocumentEntity e) {
        return new PdfDocument(
                e.id,
                e.versionId,
                DocumentType.valueOf(e.type),
                e.storageKey,
                e.sizeBytes,
                e.sha256,
                e.generatedAt,
                e.contentRemovedAt);
    }
}
