package br.com.felipeblaft.reports.inbound.web;

import br.com.felipeblaft.reports.domain.common.Channel;
import br.com.felipeblaft.reports.domain.common.DomainException;
import br.com.felipeblaft.reports.domain.common.ErrorCode;
import br.com.felipeblaft.reports.inbound.web.LessonReportDtos.ApprovalDto;
import br.com.felipeblaft.reports.inbound.web.LessonReportDtos.ApprovalRequest;
import br.com.felipeblaft.reports.inbound.web.LessonReportDtos.ChangeTextRequest;
import br.com.felipeblaft.reports.inbound.web.LessonReportDtos.JobRefDto;
import br.com.felipeblaft.reports.inbound.web.LessonReportDtos.LessonReportDto;
import br.com.felipeblaft.reports.inbound.web.LessonReportDtos.TextAnswerRequest;
import br.com.felipeblaft.reports.inbound.web.security.AuthenticatedTeacher;
import br.com.felipeblaft.reports.port.TemporaryFile;
import br.com.felipeblaft.reports.usecase.lesson.AnswerQuestionsUseCase;
import br.com.felipeblaft.reports.usecase.lesson.ApproveLessonReportUseCase;
import br.com.felipeblaft.reports.usecase.lesson.CancelLessonReportUseCase;
import br.com.felipeblaft.reports.usecase.lesson.GetLessonReportUseCase;
import br.com.felipeblaft.reports.usecase.lesson.GetOpenReportUseCase;
import br.com.felipeblaft.reports.usecase.lesson.RegisterLessonByAudioUseCase;
import br.com.felipeblaft.reports.usecase.lesson.RequestLessonReportChangeUseCase;
import br.com.felipeblaft.reports.usecase.lesson.RetryLessonReportUseCase;
import jakarta.validation.Valid;
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

/**
 * Relatório de aula na Web (FR-014 a FR-031): os mesmos casos de uso do Telegram, com o canal
 * {@code WEB}. Os processamentos são assíncronos (202 com o trabalho a acompanhar).
 */
@RestController
@RequestMapping("/api/v1")
public class LessonReportController {

    private final WebAudioReception reception;
    private final RegisterLessonByAudioUseCase register;
    private final GetLessonReportUseCase get;
    private final AnswerQuestionsUseCase answer;
    private final RequestLessonReportChangeUseCase change;
    private final RetryLessonReportUseCase retry;
    private final ApproveLessonReportUseCase approve;
    private final CancelLessonReportUseCase cancel;
    private final GetOpenReportUseCase getOpen;

    @SuppressWarnings("java:S107")
    public LessonReportController(
            WebAudioReception reception,
            RegisterLessonByAudioUseCase register,
            GetLessonReportUseCase get,
            AnswerQuestionsUseCase answer,
            RequestLessonReportChangeUseCase change,
            RetryLessonReportUseCase retry,
            ApproveLessonReportUseCase approve,
            CancelLessonReportUseCase cancel,
            GetOpenReportUseCase getOpen) {
        this.reception = reception;
        this.register = register;
        this.get = get;
        this.answer = answer;
        this.change = change;
        this.retry = retry;
        this.approve = approve;
        this.cancel = cancel;
        this.getOpen = getOpen;
    }

    @PostMapping(
            path = "/students/{studentId}/lesson-reports",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.ACCEPTED)
    public JobRefDto register(
            @AuthenticationPrincipal AuthenticatedTeacher teacher,
            @PathVariable UUID studentId,
            @RequestPart("audio") MultipartFile audio) {
        TemporaryFile file = reception.receive(audio);
        return LessonReportMapper.reference(
                register.run(teacher.id(), studentId, file, Channel.WEB));
    }

    /** Relatório em aberto do aluno (FR-034): 200 com o relatório ou 204 se não houver. */
    @GetMapping("/students/{studentId}/open-report")
    public ResponseEntity<OpenReportDto> openReport(
            @AuthenticationPrincipal AuthenticatedTeacher teacher, @PathVariable UUID studentId) {
        return getOpen.run(teacher.id(), studentId)
                .map(HomeMapper::open)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @GetMapping("/lesson-reports/{reportId}")
    public LessonReportDto get(
            @AuthenticationPrincipal AuthenticatedTeacher teacher, @PathVariable UUID reportId) {
        return LessonReportMapper.report(get.run(teacher.id(), reportId));
    }

    @PostMapping(
            path = "/lesson-reports/{reportId}/answers",
            consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.ACCEPTED)
    public JobRefDto answerText(
            @AuthenticationPrincipal AuthenticatedTeacher teacher,
            @PathVariable UUID reportId,
            @Valid @RequestBody TextAnswerRequest request) {
        return LessonReportMapper.reference(
                answer.answerText(teacher.id(), reportId, request.text(), Channel.WEB));
    }

    @PostMapping(
            path = "/lesson-reports/{reportId}/answers",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.ACCEPTED)
    public JobRefDto answerAudio(
            @AuthenticationPrincipal AuthenticatedTeacher teacher,
            @PathVariable UUID reportId,
            @RequestPart("audio") MultipartFile audio) {
        TemporaryFile file = reception.receive(audio);
        return LessonReportMapper.reference(
                answer.answerAudio(teacher.id(), reportId, file, Channel.WEB));
    }

    @PostMapping(
            path = "/lesson-reports/{reportId}/changes",
            consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.ACCEPTED)
    public JobRefDto changeByText(
            @AuthenticationPrincipal AuthenticatedTeacher teacher,
            @PathVariable UUID reportId,
            @Valid @RequestBody ChangeTextRequest request) {
        return LessonReportMapper.reference(
                change.changeByText(
                        teacher.id(),
                        reportId,
                        request.text(),
                        request.baseVersion(),
                        Channel.WEB));
    }

    @PostMapping(
            path = "/lesson-reports/{reportId}/changes",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.ACCEPTED)
    public JobRefDto changeByAudio(
            @AuthenticationPrincipal AuthenticatedTeacher teacher,
            @PathVariable UUID reportId,
            @RequestPart("audio") MultipartFile audio,
            @RequestParam("baseVersion") String baseVersion) {
        int base = baseVersion(baseVersion);
        TemporaryFile file = reception.receive(audio);
        return LessonReportMapper.reference(
                change.changeByAudio(teacher.id(), reportId, file, base, Channel.WEB));
    }

    @PostMapping("/lesson-reports/{reportId}/retry")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public JobRefDto newAttempt(
            @AuthenticationPrincipal AuthenticatedTeacher teacher, @PathVariable UUID reportId) {
        return LessonReportMapper.reference(retry.run(teacher.id(), reportId, Channel.WEB));
    }

    @PostMapping(
            path = "/lesson-reports/{reportId}/approval",
            consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApprovalDto approve(
            @AuthenticationPrincipal AuthenticatedTeacher teacher,
            @PathVariable UUID reportId,
            @Valid @RequestBody ApprovalRequest request) {
        return LessonReportMapper.approval(
                approve.run(teacher.id(), reportId, request.confirmedVersion(), Channel.WEB));
    }

    @PostMapping("/lesson-reports/{reportId}/cancellation")
    public ResponseEntity<Void> cancel(
            @AuthenticationPrincipal AuthenticatedTeacher teacher, @PathVariable UUID reportId) {
        cancel.cancel(teacher.id(), reportId);
        return ResponseEntity.noContent().build();
    }

    private static int baseVersion(String value) {
        try {
            int version = Integer.parseInt(value.strip());
            if (version < 1) {
                throw new DomainException(ErrorCode.INVALID_DATA);
            }
            return version;
        } catch (NumberFormatException invalid) {
            throw new DomainException(ErrorCode.INVALID_DATA);
        }
    }
}
