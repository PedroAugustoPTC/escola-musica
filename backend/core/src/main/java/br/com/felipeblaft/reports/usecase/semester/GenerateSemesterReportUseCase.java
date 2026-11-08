package br.com.felipeblaft.reports.usecase.semester;

import br.com.felipeblaft.reports.domain.common.Channel;
import br.com.felipeblaft.reports.domain.common.DomainException;
import br.com.felipeblaft.reports.domain.common.ErrorCode;
import br.com.felipeblaft.reports.domain.common.Medium;
import br.com.felipeblaft.reports.domain.common.ReportType;
import br.com.felipeblaft.reports.domain.job.JobStep;
import br.com.felipeblaft.reports.domain.job.JobType;
import br.com.felipeblaft.reports.domain.job.ProcessingJob;
import br.com.felipeblaft.reports.port.ClockPort;
import br.com.felipeblaft.reports.port.ConcurrencyConflictException;
import br.com.felipeblaft.reports.port.JobQueuePort;
import br.com.felipeblaft.reports.service.ConsentGuardService;
import br.com.felipeblaft.reports.service.ReportEligibilityService;
import br.com.felipeblaft.reports.usecase.semester.GetSemesterPreviewUseCase.Preview;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Gerar o semestral (FR-042, FR-046, FR-034): confere as pré-condições na ordem de data-model.md §9
 * e enfileira a consolidação. Sem aulas aprovadas, nada é criado e a IA não é chamada.
 */
public class GenerateSemesterReportUseCase {

    private ConsentGuardService consent;
    private final GetSemesterPreviewUseCase preview;
    private final JobQueuePort queue;
    private final ClockPort clock;
    private final ReportEligibilityService eligibility;

    /**
     * Sem verificação de situação do professor e do aluno: só para os cenários da 001, que ainda
     * não conhecem a situação. A montagem de produção usa o construtor completo.
     */
    public GenerateSemesterReportUseCase(
            GetSemesterPreviewUseCase preview, JobQueuePort queue, ClockPort clock) {
        this(preview, queue, clock, null);
    }

    public GenerateSemesterReportUseCase(
            GetSemesterPreviewUseCase preview,
            JobQueuePort queue,
            ClockPort clock,
            ReportEligibilityService eligibility) {
        this.eligibility = eligibility;
        this.preview = Objects.requireNonNull(preview);
        this.queue = Objects.requireNonNull(queue);
        this.clock = Objects.requireNonNull(clock);
    }

    public ProcessingJob run(
            UUID teacherId,
            UUID studentId,
            String periodKey,
            boolean confirmEvenWithOpen,
            Channel channel) {
        if (eligibility != null) {
            eligibility.requireActiveTeacher(teacherId);
        }
        Preview current = preview.run(teacherId, studentId, periodKey);
        if (eligibility != null) {
            eligibility.requireActiveStudent(studentId);
        }
        requireConsent(teacherId, studentId);
        requireCanGenerate(current, confirmEvenWithOpen);
        ProcessingJob job =
                ProcessingJob.create(
                        JobType.SEMESTER_CONSOLIDATION,
                        teacherId,
                        studentId,
                        ReportType.SEMESTER,
                        null,
                        channel,
                        Medium.TEXT,
                        JobStep.CONSOLIDATING,
                        clock.now());
        job.defineParameter(ProcessingJob.PERIOD_KEY, current.period().key());
        if (confirmEvenWithOpen) {
            job.defineParameter(ProcessingJob.CONFIRM_OPEN, "true");
        }
        try {
            queue.enqueue(job);
        } catch (ConcurrencyConflictException conflict) {
            requireCanGenerate(preview.run(teacherId, studentId, periodKey), true);
            throw new DomainException(ErrorCode.REPORT_ALREADY_OPEN);
        }
        return job;
    }

    static void requireCanGenerate(Preview preview, boolean confirmEvenWithOpen) {
        if (preview.approvedSemesterId().isPresent()) {
            throw new DomainException(ErrorCode.SEMESTER_REPORT_ALREADY_APPROVED);
        }
        if (preview.openSemester().isPresent()) {
            throw new DomainException(
                    ErrorCode.REPORT_ALREADY_OPEN,
                    Map.of("openReport", preview.openSemester().get().details()));
        }
        if (preview.approvedLessons() == 0) {
            throw new DomainException(ErrorCode.NO_APPROVED_LESSONS_IN_PERIOD);
        }
        if (preview.openLessonReports() > 0 && !confirmEvenWithOpen) {
            throw new DomainException(
                    ErrorCode.OPEN_REPORTS_EXIST, Map.of("quantity", preview.openLessonReports()));
        }
    }

    /**
     * Liga a barreira de consentimento (FR-034): aluno menor sem consentimento válido não chega a
     * nenhum provedor de IA. A montagem de produção a liga; cenários anteriores à feature 007
     * seguem sem ela.
     */
    public void useConsentGuard(ConsentGuardService guard) {
        this.consent = guard;
    }

    private void requireConsent(UUID teacherId, UUID studentId) {
        if (consent != null) {
            consent.requireForAi(teacherId, studentId);
        }
    }
}
