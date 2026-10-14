package br.com.felipeblaft.reports.outbound.persistence;

import br.com.felipeblaft.reports.domain.common.Channel;
import br.com.felipeblaft.reports.domain.report.ReportStatus;
import br.com.felipeblaft.reports.domain.report.ReportStep;
import br.com.felipeblaft.reports.domain.semester.GeneralInformation;
import br.com.felipeblaft.reports.domain.semester.SemesterPeriod;
import br.com.felipeblaft.reports.domain.semester.SemesterReport;
import br.com.felipeblaft.reports.domain.semester.SemesterReportContent;
import br.com.felipeblaft.reports.port.ConcurrencyConflictException;
import br.com.felipeblaft.reports.port.SemesterReportRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Repository;

/** Relatórios semestrais (Informações Gerais e seções 2 a 10 em jsonb). */
@Repository
class SemesterReportRepositoryAdapter implements SemesterReportRepository {

    private final SemesterReportJpa jpa;

    SemesterReportRepositoryAdapter(SemesterReportJpa jpa) {
        this.jpa = jpa;
    }

    @Override
    public void save(SemesterReport report) {
        try {
            SemesterReportEntity saved = jpa.saveAndFlush(toEntity(report));
            report.registerPersistedVersion(saved.lockVersion);
        } catch (OptimisticLockingFailureException | DataIntegrityViolationException conflict) {
            throw new ConcurrencyConflictException("relatorio_semestral");
        }
    }

    @Override
    public Optional<SemesterReport> searchById(UUID id) {
        return jpa.findById(id).map(SemesterReportRepositoryAdapter::toDomain);
    }

    @Override
    public Optional<SemesterReport> searchForUpdate(UUID id) {
        return jpa.searchForUpdate(id).map(SemesterReportRepositoryAdapter::toDomain);
    }

    @Override
    public Optional<SemesterReport> searchApproved(
            UUID teacherId, UUID studentId, String periodKey) {
        return jpa.doTrio(teacherId, studentId, periodKey, ReportStatus.APROVADO.name())
                .map(SemesterReportRepositoryAdapter::toDomain);
    }

    @Override
    public Optional<SemesterReport> searchOpen(UUID teacherId, UUID studentId, String periodKey) {
        return jpa.doTrio(
                        teacherId, studentId, periodKey, ReportStatus.AGUARDANDO_CONFERENCIA.name())
                .map(SemesterReportRepositoryAdapter::toDomain);
    }

    @Override
    public List<SemesterReport> listApproved(UUID teacherId, UUID studentId) {
        return jpa.approved(teacherId, studentId).stream()
                .map(SemesterReportRepositoryAdapter::toDomain)
                .toList();
    }

    List<SemesterReport> teacherOpenReports(UUID teacherId) {
        return jpa.teacherOpenReports(teacherId).stream()
                .map(SemesterReportRepositoryAdapter::toDomain)
                .toList();
    }

    static SemesterReportEntity toEntity(SemesterReport r) {
        SemesterReportEntity e = new SemesterReportEntity();
        e.id = r.id();
        e.teacherId = r.teacherId();
        e.studentId = r.studentId();
        e.periodKey = r.period().key();
        e.periodStart = r.period().start();
        e.periodEnd = r.period().end();
        e.consideredLessonCount = r.consideredLessonCount();
        e.openReportCount = r.openReportCount();
        e.generalInformation = JsonPersistence.write(r.generalInformation());
        e.content = JsonPersistence.write(r.content());
        e.status = r.status().name();
        e.processingStep = r.step() == null ? null : r.step().name();
        e.currentVersion = r.currentVersion();
        e.originChannel = r.originChannel().name();
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

    static SemesterReport toDomain(SemesterReportEntity e) {
        return new SemesterReport(
                e.id,
                e.teacherId,
                e.studentId,
                SemesterPeriod.of(e.periodKey),
                e.consideredLessonCount,
                e.openReportCount,
                JsonPersistence.read(e.generalInformation, GeneralInformation.class),
                JsonPersistence.read(e.content, SemesterReportContent.class),
                ReportStatus.valueOf(e.status),
                e.processingStep == null ? null : ReportStep.valueOf(e.processingStep),
                e.currentVersion,
                Channel.valueOf(e.originChannel),
                e.createdAt,
                e.updatedAt,
                e.approvedAt,
                e.approvedVersion,
                e.approvalChannel == null ? null : Channel.valueOf(e.approvalChannel),
                e.cancelledAt,
                e.processingTimeMs,
                e.lockVersion);
    }
}
