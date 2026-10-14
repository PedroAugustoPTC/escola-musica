package br.com.felipeblaft.reports.inbound.web;

import br.com.felipeblaft.reports.config.SemesterReportSections;
import br.com.felipeblaft.reports.domain.common.Channel;
import br.com.felipeblaft.reports.domain.common.DomainException;
import br.com.felipeblaft.reports.domain.common.ErrorCode;
import br.com.felipeblaft.reports.domain.job.ProcessingJob;
import br.com.felipeblaft.reports.domain.semester.GeneralInformation;
import br.com.felipeblaft.reports.domain.semester.SemesterReport;
import br.com.felipeblaft.reports.domain.semester.SemesterReportContent;
import br.com.felipeblaft.reports.inbound.web.LessonReportDtos.ApprovalDto;
import br.com.felipeblaft.reports.inbound.web.LessonReportDtos.ApprovalRequest;
import br.com.felipeblaft.reports.inbound.web.LessonReportDtos.ChangeTextRequest;
import br.com.felipeblaft.reports.inbound.web.LessonReportDtos.InteractionDto;
import br.com.felipeblaft.reports.inbound.web.LessonReportDtos.JobRefDto;
import br.com.felipeblaft.reports.inbound.web.security.AuthenticatedTeacher;
import br.com.felipeblaft.reports.port.TemporaryFile;
import br.com.felipeblaft.reports.usecase.semester.ApproveSemesterReportUseCase;
import br.com.felipeblaft.reports.usecase.semester.CancelSemesterReportUseCase;
import br.com.felipeblaft.reports.usecase.semester.GenerateSemesterReportUseCase;
import br.com.felipeblaft.reports.usecase.semester.GetSemesterPreviewUseCase;
import br.com.felipeblaft.reports.usecase.semester.GetSemesterPreviewUseCase.Preview;
import br.com.felipeblaft.reports.usecase.semester.GetSemesterReportUseCase;
import br.com.felipeblaft.reports.usecase.semester.GetSemesterReportUseCase.SemesterReportView;
import br.com.felipeblaft.reports.usecase.semester.ListAvailableSemestersUseCase;
import br.com.felipeblaft.reports.usecase.semester.ListAvailableSemestersUseCase.Semester;
import br.com.felipeblaft.reports.usecase.semester.RequestSemesterReportChangeUseCase;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Relatório semestral na Web (FR-041 a FR-046): os mesmos casos de uso do Telegram. */
@RestController
@RequestMapping("/api/v1")
public class SemesterReportController {

    public record SemesterDto(
            String key,
            String label,
            LocalDate start,
            LocalDate end,
            boolean inProgress,
            boolean preselected) {}

    public record SemesterPreviewDto(
            String periodKey,
            int approvedLessons,
            int openLessonReports,
            UUID approvedSemesterReportId,
            OpenReportDto openSemesterReport) {}

    public record GenerateSemesterRequest(
            @NotNull @Pattern(regexp = "^[0-9]{4}-[12]$") String periodKey,
            Boolean confirmDespiteOpenReports) {}

    public record GeneralInformationDto(
            String name,
            int age,
            String instrument,
            String teacher,
            String periodLabel,
            LocalDate periodStart,
            LocalDate periodEnd,
            int consideredLessonCount) {}

    public record SemesterReportDto(
            UUID reportId,
            UUID studentId,
            String periodKey,
            String status,
            String step,
            UUID activeJobId,
            int currentVersion,
            String originChannel,
            GeneralInformationDto generalInformation,
            SemesterReportContent content,
            int openReportCount,
            InteractionDto lastInteraction,
            boolean pdfAvailable,
            Instant approvedAt,
            Instant updatedAt) {}

    private final WebAudioReception reception;
    private final ListAvailableSemestersUseCase list;
    private final GetSemesterPreviewUseCase preview;
    private final GenerateSemesterReportUseCase generate;
    private final GetSemesterReportUseCase get;
    private final RequestSemesterReportChangeUseCase change;
    private final ApproveSemesterReportUseCase approve;
    private final CancelSemesterReportUseCase cancel;
    private final SemesterReportSections sections;

    @SuppressWarnings("java:S107")
    public SemesterReportController(
            WebAudioReception reception,
            ListAvailableSemestersUseCase list,
            GetSemesterPreviewUseCase preview,
            GenerateSemesterReportUseCase generate,
            GetSemesterReportUseCase get,
            RequestSemesterReportChangeUseCase change,
            ApproveSemesterReportUseCase approve,
            CancelSemesterReportUseCase cancel,
            SemesterReportSections sections) {
        this.reception = reception;
        this.list = list;
        this.preview = preview;
        this.generate = generate;
        this.get = get;
        this.change = change;
        this.approve = approve;
        this.cancel = cancel;
        this.sections = sections;
    }

    @GetMapping("/students/{studentId}/semesters")
    public List<SemesterDto> semesters(
            @AuthenticationPrincipal AuthenticatedTeacher teacher, @PathVariable UUID studentId) {
        return list.run(teacher.id(), studentId).stream().map(this::semester).toList();
    }

    private SemesterDto semester(Semester s) {
        return new SemesterDto(
                s.period().key(),
                sections.periodLabel(s.period().key()),
                s.period().start(),
                s.period().end(),
                s.inProgress(),
                s.preSelected());
    }

    @GetMapping("/students/{studentId}/semesters/{periodKey}/preview")
    public SemesterPreviewDto preview(
            @AuthenticationPrincipal AuthenticatedTeacher teacher,
            @PathVariable UUID studentId,
            @PathVariable String periodKey) {
        Preview p = preview.run(teacher.id(), studentId, periodKey);
        return new SemesterPreviewDto(
                p.period().key(),
                p.approvedLessons(),
                p.openLessonReports(),
                p.approvedSemesterId().orElse(null),
                p.openSemester().map(HomeMapper::open).orElse(null));
    }

    @PostMapping(
            path = "/students/{studentId}/semester-reports",
            consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.ACCEPTED)
    public JobRefDto generate(
            @AuthenticationPrincipal AuthenticatedTeacher teacher,
            @PathVariable UUID studentId,
            @Valid @RequestBody GenerateSemesterRequest request) {
        ProcessingJob job =
                generate.run(
                        teacher.id(),
                        studentId,
                        request.periodKey(),
                        Boolean.TRUE.equals(request.confirmDespiteOpenReports()),
                        Channel.WEB);
        return new JobRefDto(job.id());
    }

    @GetMapping("/semester-reports/{reportId}")
    public SemesterReportDto get(
            @AuthenticationPrincipal AuthenticatedTeacher teacher, @PathVariable UUID reportId) {
        return report(get.run(teacher.id(), reportId));
    }

    @PostMapping(
            path = "/semester-reports/{reportId}/changes",
            consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.ACCEPTED)
    public JobRefDto changeByText(
            @AuthenticationPrincipal AuthenticatedTeacher teacher,
            @PathVariable UUID reportId,
            @Valid @RequestBody ChangeTextRequest request) {
        return new JobRefDto(
                change.changeByText(
                                teacher.id(),
                                reportId,
                                request.text(),
                                request.baseVersion(),
                                Channel.WEB)
                        .id());
    }

    @PostMapping(
            path = "/semester-reports/{reportId}/changes",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.ACCEPTED)
    public JobRefDto changeByAudio(
            @AuthenticationPrincipal AuthenticatedTeacher teacher,
            @PathVariable UUID reportId,
            @RequestPart("audio") MultipartFile audio,
            @RequestParam("baseVersion") String baseVersion) {
        int base;
        try {
            base = Integer.parseInt(baseVersion.strip());
        } catch (NumberFormatException invalid) {
            throw new DomainException(ErrorCode.INVALID_DATA);
        }
        TemporaryFile file = reception.receive(audio);
        return new JobRefDto(
                change.changeByAudio(teacher.id(), reportId, file, base, Channel.WEB).id());
    }

    @PostMapping(
            path = "/semester-reports/{reportId}/approval",
            consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApprovalDto approve(
            @AuthenticationPrincipal AuthenticatedTeacher teacher,
            @PathVariable UUID reportId,
            @Valid @RequestBody ApprovalRequest request) {
        return LessonReportMapper.approval(
                approve.run(teacher.id(), reportId, request.confirmedVersion(), Channel.WEB));
    }

    @PostMapping("/semester-reports/{reportId}/cancellation")
    public ResponseEntity<Void> cancel(
            @AuthenticationPrincipal AuthenticatedTeacher teacher, @PathVariable UUID reportId) {
        cancel.cancel(teacher.id(), reportId);
        return ResponseEntity.noContent().build();
    }

    private SemesterReportDto report(SemesterReportView view) {
        SemesterReport r = view.report();
        GeneralInformation info = r.generalInformation();
        String step =
                view.jobInProgress()
                        .map(t -> t.step().name())
                        .orElse(r.step() == null ? null : r.step().name());
        return new SemesterReportDto(
                r.id(),
                r.studentId(),
                r.period().key(),
                r.status().name(),
                step,
                view.jobInProgress().map(ProcessingJob::id).orElse(null),
                r.currentVersion(),
                r.originChannel().name(),
                new GeneralInformationDto(
                        info.name(),
                        info.age(),
                        info.instrument(),
                        info.teacher(),
                        sections.periodLabel(info.periodKey()),
                        info.periodStart(),
                        info.periodEnd(),
                        info.consideredLessonCount()),
                r.content(),
                r.openReportCount(),
                view.lastInteraction().map(LessonReportMapper::interaction).orElse(null),
                view.pdfAvailable(),
                r.approvedAt(),
                r.updatedAt());
    }
}
