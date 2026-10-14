package br.com.felipeblaft.reports.outbound.persistence;

import br.com.felipeblaft.reports.domain.report.Lesson;
import br.com.felipeblaft.reports.domain.report.LessonReport;
import br.com.felipeblaft.reports.outbound.persistence.ReportEntities.LessonReportEntity;
import br.com.felipeblaft.reports.port.ConcurrencyConflictException;
import br.com.felipeblaft.reports.port.LessonReportRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Repository;

@Repository
class LessonReportRepositoryAdapter implements LessonReportRepository {

    private final LessonJpa lessons;
    private final LessonReportJpa reports;

    LessonReportRepositoryAdapter(LessonJpa lessons, LessonReportJpa reports) {
        this.lessons = lessons;
        this.reports = reports;
    }

    @Override
    public void saveLesson(Lesson lesson) {
        lessons.save(LessonReportMapper.toEntity(lesson));
    }

    @Override
    public Optional<Lesson> searchLesson(UUID lessonId) {
        return lessons.findById(lessonId).map(LessonReportMapper::toDomain);
    }

    @Override
    public void save(LessonReport report) {
        try {
            LessonReportEntity saved = reports.saveAndFlush(LessonReportMapper.toEntity(report));
            report.registerPersistedVersion(saved.lockVersion);
        } catch (ObjectOptimisticLockingFailureException
                | DataIntegrityViolationException conflict) {
            throw new ConcurrencyConflictException("relatorio_aula");
        }
    }

    @Override
    public Optional<LessonReport> searchById(UUID id) {
        return reports.findById(id).map(LessonReportMapper::toDomain);
    }

    @Override
    public Optional<LessonReport> searchForUpdate(UUID id) {
        return reports.searchForUpdate(id).map(LessonReportMapper::toDomain);
    }

    @Override
    public Optional<LessonReport> searchOpenByStudent(UUID teacherId, UUID studentId) {
        return reports.searchOpen(teacherId, studentId).map(LessonReportMapper::toDomain);
    }

    @Override
    public List<ApprovedLesson> listApprovedInPeriod(
            UUID teacherId, UUID studentId, LocalDate start, LocalDate end) {
        return reports.approvedInPeriod(teacherId, studentId, start, end).stream()
                .map(
                        line -> {
                            LessonReport report =
                                    LessonReportMapper.toDomain((LessonReportEntity) line[0]);
                            return new ApprovedLesson(
                                    report.id(),
                                    (LocalDate) line[1],
                                    report.content(),
                                    report.approvedAt());
                        })
                .toList();
    }

    @Override
    public List<ApprovedLesson> listApproved(UUID teacherId, UUID studentId) {
        return reports.approved(teacherId, studentId).stream().map(this::approved).toList();
    }

    private ApprovedLesson approved(Object[] line) {
        LessonReport report = LessonReportMapper.toDomain((LessonReportEntity) line[0]);
        return new ApprovedLesson(
                report.id(), (LocalDate) line[1], report.content(), report.approvedAt());
    }

    @Override
    public int countOpenInPeriod(UUID teacherId, UUID studentId, LocalDate start, LocalDate end) {
        return (int) reports.countOpenInPeriod(teacherId, studentId, start, end);
    }

    @Override
    public Optional<LocalDate> lessonFirstDate(UUID teacherId, UUID studentId) {
        return reports.firstLesson(teacherId, studentId);
    }
}
