package br.com.felipeblaft.reports.config;

import br.com.felipeblaft.reports.domain.channel.RetryPolicy;
import br.com.felipeblaft.reports.domain.common.BusinessParameters;
import br.com.felipeblaft.reports.port.AccessAttemptRepository;
import br.com.felipeblaft.reports.port.AccessCodeGeneratorPort;
import br.com.felipeblaft.reports.port.AssignmentRepository;
import br.com.felipeblaft.reports.port.AudioNormalizationPort;
import br.com.felipeblaft.reports.port.ChannelActionRepository;
import br.com.felipeblaft.reports.port.ChannelDocumentRepository;
import br.com.felipeblaft.reports.port.ChannelFilePort;
import br.com.felipeblaft.reports.port.ChannelOrchestratorPort;
import br.com.felipeblaft.reports.port.ClockPort;
import br.com.felipeblaft.reports.port.CostRecordPort;
import br.com.felipeblaft.reports.port.DataProtectionPort;
import br.com.felipeblaft.reports.port.DocumentStoragePort;
import br.com.felipeblaft.reports.port.JobQueuePort;
import br.com.felipeblaft.reports.port.LessonReportRepository;
import br.com.felipeblaft.reports.port.LlmPort;
import br.com.felipeblaft.reports.port.OpenReportsQueryPort;
import br.com.felipeblaft.reports.port.PdfDocumentRepository;
import br.com.felipeblaft.reports.port.PdfGenerationPort;
import br.com.felipeblaft.reports.port.ReportInteractionRepository;
import br.com.felipeblaft.reports.port.ReportVersionRepository;
import br.com.felipeblaft.reports.port.SemesterReportRepository;
import br.com.felipeblaft.reports.port.StudentRepository;
import br.com.felipeblaft.reports.port.TeacherNotificationPort;
import br.com.felipeblaft.reports.port.TeacherRepository;
import br.com.felipeblaft.reports.port.TelegramLinkRepository;
import br.com.felipeblaft.reports.port.TransactionPort;
import br.com.felipeblaft.reports.port.TranscriptionPort;
import br.com.felipeblaft.reports.port.WebSessionRepository;
import br.com.felipeblaft.reports.service.AccessAttemptsControlService;
import br.com.felipeblaft.reports.service.AccessCodeIssuanceService;
import br.com.felipeblaft.reports.service.AccessRenewalService;
import br.com.felipeblaft.reports.service.AudioSendingService;
import br.com.felipeblaft.reports.service.AuthorizationService;
import br.com.felipeblaft.reports.service.CodeAuthenticationService;
import br.com.felipeblaft.reports.service.DocumentGenerationService;
import br.com.felipeblaft.reports.service.JobProcessorService;
import br.com.felipeblaft.reports.service.ReportAccessService;
import br.com.felipeblaft.reports.service.SemesterAccessService;
import br.com.felipeblaft.reports.service.SemesterProcessorService;
import br.com.felipeblaft.reports.service.TemporaryAudioService;
import br.com.felipeblaft.reports.usecase.access.EndTelegramLinkUseCase;
import br.com.felipeblaft.reports.usecase.access.EndWebSessionUseCase;
import br.com.felipeblaft.reports.usecase.access.IdentifyTelegramTeacherUseCase;
import br.com.felipeblaft.reports.usecase.access.LinkTelegramUseCase;
import br.com.felipeblaft.reports.usecase.access.QueryTelegramLinkUseCase;
import br.com.felipeblaft.reports.usecase.access.SignInPortalUseCase;
import br.com.felipeblaft.reports.usecase.access.UpdateTelegramConversationUseCase;
import br.com.felipeblaft.reports.usecase.access.ValidateSessionWebUseCase;
import br.com.felipeblaft.reports.usecase.channel.CleanChannelActionsUseCase;
import br.com.felipeblaft.reports.usecase.channel.ConfirmChannelActionsExecutionUseCase;
import br.com.felipeblaft.reports.usecase.channel.DeliverPendingActionsUseCase;
import br.com.felipeblaft.reports.usecase.channel.EmitChannelDocumentUseCase;
import br.com.felipeblaft.reports.usecase.channel.GetChannelDocumentUseCase;
import br.com.felipeblaft.reports.usecase.channel.GetUnconfirmedActionsOfUpdateUseCase;
import br.com.felipeblaft.reports.usecase.channel.QueryChannelActionUseCase;
import br.com.felipeblaft.reports.usecase.channel.SaveChannelActionsUseCase;
import br.com.felipeblaft.reports.usecase.document.GetReportDocumentUseCase;
import br.com.felipeblaft.reports.usecase.history.QueryStudentHistoryUseCase;
import br.com.felipeblaft.reports.usecase.home.GetStudentSummaryUseCase;
import br.com.felipeblaft.reports.usecase.home.GetTeacherHomeUseCase;
import br.com.felipeblaft.reports.usecase.home.SearchStudentsUseCase;
import br.com.felipeblaft.reports.usecase.job.DiscardRegistrationInProcessingUseCase;
import br.com.felipeblaft.reports.usecase.job.QueryJobUseCase;
import br.com.felipeblaft.reports.usecase.lesson.AnswerQuestionsUseCase;
import br.com.felipeblaft.reports.usecase.lesson.ApproveLessonReportUseCase;
import br.com.felipeblaft.reports.usecase.lesson.CancelLessonReportUseCase;
import br.com.felipeblaft.reports.usecase.lesson.GetLessonReportUseCase;
import br.com.felipeblaft.reports.usecase.lesson.GetOpenReportUseCase;
import br.com.felipeblaft.reports.usecase.lesson.RegisterLessonByAudioUseCase;
import br.com.felipeblaft.reports.usecase.lesson.RequestLessonReportChangeUseCase;
import br.com.felipeblaft.reports.usecase.lesson.RetryLessonReportUseCase;
import br.com.felipeblaft.reports.usecase.seed.EmitAccessCodeUseCase;
import br.com.felipeblaft.reports.usecase.seed.LoadInitialRegistryUseCase;
import br.com.felipeblaft.reports.usecase.semester.ApproveSemesterReportUseCase;
import br.com.felipeblaft.reports.usecase.semester.CancelSemesterReportUseCase;
import br.com.felipeblaft.reports.usecase.semester.GenerateSemesterReportUseCase;
import br.com.felipeblaft.reports.usecase.semester.GetSemesterPreviewUseCase;
import br.com.felipeblaft.reports.usecase.semester.GetSemesterReportUseCase;
import br.com.felipeblaft.reports.usecase.semester.ListAvailableSemestersUseCase;
import br.com.felipeblaft.reports.usecase.semester.RequestSemesterReportChangeUseCase;
import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Instancia os casos de uso e serviços do núcleo como beans. O núcleo não tem anotações de
 * framework; toda a montagem fica aqui. Os da feature 005 ficam em {@link
 * AdministrationUseCaseConfig}, {@link TeacherManagementUseCaseConfig} e {@link
 * StudentRegistryUseCaseConfig}, sem repetir bean deste arquivo.
 */
@Configuration(proxyBeanMethods = false)
public class UseCaseConfig {

    @Bean
    BusinessParameters businessParameters(ApplicationProperties properties) {
        return new BusinessParameters(
                Duration.ofDays(properties.code().validityDays()),
                properties.access().maxAttempts(),
                Duration.ofMinutes(properties.access().lockMinutes()),
                Duration.ofHours(properties.session().hours()),
                Duration.ofMinutes(properties.telegram().pendingAudioMinutes()),
                properties.audio().limitBytes());
    }

    // ---- Serviços

    @Bean
    AuthorizationService authorizationService(AssignmentRepository assignments) {
        return new AuthorizationService(assignments);
    }

    @Bean
    AccessCodeIssuanceService accessCodeIssuanceService(
            AccessCodeGeneratorPort generator,
            DataProtectionPort protection,
            TeacherRepository teachers,
            BusinessParameters parameters) {
        return new AccessCodeIssuanceService(generator, protection, teachers, parameters);
    }

    @Bean
    AccessAttemptsControlService accessAttemptsControlService(
            AccessAttemptRepository attempts,
            DataProtectionPort protection,
            ClockPort clock,
            BusinessParameters parameters) {
        return new AccessAttemptsControlService(attempts, protection, clock, parameters);
    }

    @Bean
    AccessRenewalService accessRenewalService(
            TeacherRepository teachers, ClockPort clock, BusinessParameters parameters) {
        return new AccessRenewalService(teachers, clock, parameters);
    }

    @Bean
    CodeAuthenticationService codeAuthenticationService(
            AccessAttemptsControlService attempts,
            TeacherRepository teachers,
            DataProtectionPort protection,
            ClockPort clock) {
        return new CodeAuthenticationService(attempts, teachers, protection, clock);
    }

    // ---- Carga inicial

    @Bean
    LoadInitialRegistryUseCase loadInitialRegistryUseCase(
            TeacherRepository teachers,
            StudentRepository students,
            AssignmentRepository assignments,
            DataProtectionPort protection,
            AccessCodeIssuanceService issuance,
            ClockPort clock) {
        return new LoadInitialRegistryUseCase(
                teachers, students, assignments, protection, issuance, clock);
    }

    @Bean
    EmitAccessCodeUseCase emitAccessCodeUseCase(
            TeacherRepository teachers, AccessCodeIssuanceService issuance, ClockPort clock) {
        return new EmitAccessCodeUseCase(teachers, issuance, clock);
    }

    // ---- Acesso

    @Bean
    SignInPortalUseCase signInPortalUseCase(
            CodeAuthenticationService authentication,
            AccessRenewalService renewal,
            WebSessionRepository sessions,
            TelegramLinkRepository links,
            DataProtectionPort protection,
            ClockPort clock,
            BusinessParameters parameters) {
        return new SignInPortalUseCase(
                authentication, renewal, sessions, links, protection, clock, parameters);
    }

    @Bean
    ValidateSessionWebUseCase validateSessionWebUseCase(
            WebSessionRepository sessions,
            TeacherRepository teachers,
            AccessRenewalService renewal,
            DataProtectionPort protection,
            ClockPort clock,
            BusinessParameters parameters) {
        return new ValidateSessionWebUseCase(
                sessions, teachers, renewal, protection, clock, parameters);
    }

    @Bean
    EndWebSessionUseCase endWebSessionUseCase(
            WebSessionRepository sessions, DataProtectionPort protection) {
        return new EndWebSessionUseCase(sessions, protection);
    }

    @Bean
    LinkTelegramUseCase linkTelegramUseCase(
            CodeAuthenticationService authentication,
            AccessRenewalService renewal,
            TelegramLinkRepository links,
            ClockPort clock) {
        return new LinkTelegramUseCase(authentication, renewal, links, clock);
    }

    @Bean
    IdentifyTelegramTeacherUseCase identifyTeacherTelegramUseCase(
            TelegramLinkRepository links,
            TeacherRepository teachers,
            AccessRenewalService renewal) {
        return new IdentifyTelegramTeacherUseCase(links, teachers, renewal);
    }

    @Bean
    EndTelegramLinkUseCase endTelegramLinkUseCase(TelegramLinkRepository links) {
        return new EndTelegramLinkUseCase(links);
    }

    @Bean
    UpdateTelegramConversationUseCase updateConversationTelegramUseCase(
            TelegramLinkRepository links, ClockPort clock) {
        return new UpdateTelegramConversationUseCase(links, clock);
    }

    @Bean
    QueryTelegramLinkUseCase queryLinkTelegramUseCase(TelegramLinkRepository links) {
        return new QueryTelegramLinkUseCase(links);
    }

    // ---- Início e alunos

    @Bean
    GetTeacherHomeUseCase getTeacherHomeUseCase(
            StudentRepository students, OpenReportsQueryPort query) {
        return new GetTeacherHomeUseCase(students, query);
    }

    @Bean
    SearchStudentsUseCase searchStudentsUseCase(
            StudentRepository students, OpenReportsQueryPort query) {
        return new SearchStudentsUseCase(students, query);
    }

    @Bean
    GetStudentSummaryUseCase getStudentSummaryUseCase(
            StudentRepository students,
            OpenReportsQueryPort query,
            AuthorizationService authorization) {
        return new GetStudentSummaryUseCase(students, query, authorization);
    }

    // ---- Registro de aula (US1): serviços

    @Bean
    ReportAccessService reportAccessService(
            LessonReportRepository reports, AssignmentRepository assignments) {
        return new ReportAccessService(reports, assignments);
    }

    @Bean
    TemporaryAudioService temporaryAudioService(
            ChannelFilePort channelFile,
            AudioNormalizationPort normalization,
            TranscriptionPort transcription) {
        return new TemporaryAudioService(channelFile, normalization, transcription);
    }

    @Bean
    AudioSendingService audioSendingService(
            BusinessParameters parameters, TemporaryAudioService audio) {
        return new AudioSendingService(parameters, audio);
    }

    @Bean
    DocumentGenerationService documentGenerationService(
            PdfGenerationPort pdfGeneration,
            DocumentStoragePort storage,
            PdfDocumentRepository documents,
            LessonReportRepository reports,
            StudentRepository students,
            TeacherRepository teachers,
            ClockPort clock) {
        return new DocumentGenerationService(
                pdfGeneration, storage, documents, reports, students, teachers, clock);
    }

    @Bean
    @SuppressWarnings("java:S107")
    JobProcessorService jobProcessorService(
            JobQueuePort queue,
            LessonReportRepository reports,
            ReportVersionRepository versions,
            ReportInteractionRepository interactions,
            TemporaryAudioService audio,
            LlmPort llm,
            DocumentGenerationService documents,
            TeacherNotificationPort notification,
            CostRecordPort costs,
            TransactionPort transaction,
            ClockPort clock) {
        return new JobProcessorService(
                queue,
                reports,
                versions,
                interactions,
                audio,
                llm,
                documents,
                notification,
                costs,
                transaction,
                clock);
    }

    // ---- Registro de aula (US1): casos de uso

    @Bean
    RegisterLessonByAudioUseCase registerLessonByAudioUseCase(
            AuthorizationService authorization,
            OpenReportsQueryPort open,
            AudioSendingService audioSending,
            JobQueuePort queue,
            ClockPort clock) {
        return new RegisterLessonByAudioUseCase(authorization, open, audioSending, queue, clock);
    }

    @Bean
    AnswerQuestionsUseCase answerQuestionsUseCase(
            ReportAccessService access,
            LessonReportRepository reports,
            AudioSendingService audioSending,
            JobQueuePort queue,
            ClockPort clock) {
        return new AnswerQuestionsUseCase(access, reports, audioSending, queue, clock);
    }

    @Bean
    RequestLessonReportChangeUseCase requestLessonReportChangeUseCase(
            ReportAccessService access,
            LessonReportRepository reports,
            AudioSendingService audioSending,
            JobQueuePort queue,
            ClockPort clock) {
        return new RequestLessonReportChangeUseCase(access, reports, audioSending, queue, clock);
    }

    @Bean
    RetryLessonReportUseCase retryLessonReportUseCase(
            ReportAccessService access,
            LessonReportRepository reports,
            JobQueuePort queue,
            ClockPort clock) {
        return new RetryLessonReportUseCase(access, reports, queue, clock);
    }

    @Bean
    ApproveLessonReportUseCase approveLessonReportUseCase(
            ReportAccessService access,
            LessonReportRepository reports,
            ReportVersionRepository versions,
            DocumentGenerationService documents,
            ClockPort clock) {
        return new ApproveLessonReportUseCase(access, reports, versions, documents, clock);
    }

    @Bean
    CancelLessonReportUseCase cancelLessonReportUseCase(
            ReportAccessService access,
            LessonReportRepository reports,
            JobQueuePort queue,
            ClockPort clock) {
        return new CancelLessonReportUseCase(access, reports, queue, clock);
    }

    @Bean
    @SuppressWarnings("java:S107")
    GetLessonReportUseCase getLessonReportUseCase(
            ReportAccessService access,
            LessonReportRepository reports,
            StudentRepository students,
            TeacherRepository teachers,
            ReportInteractionRepository interactions,
            ReportVersionRepository versions,
            PdfDocumentRepository documents,
            JobQueuePort queue) {
        return new GetLessonReportUseCase(
                access, reports, students, teachers, interactions, versions, documents, queue);
    }

    @Bean
    QueryJobUseCase queryJobUseCase(JobQueuePort queue, ClockPort clock) {
        return new QueryJobUseCase(queue, clock);
    }

    @Bean
    GetReportDocumentUseCase getReportDocumentUseCase(
            ReportAccessService access,
            LessonReportRepository reports,
            ReportVersionRepository versions,
            PdfDocumentRepository documents,
            DocumentGenerationService generation) {
        return new GetReportDocumentUseCase(access, reports, versions, documents, generation);
    }

    // ---- Retomada entre canais (US3)

    @Bean
    GetOpenReportUseCase getOpenReportUseCase(
            AuthorizationService authorization, OpenReportsQueryPort query) {
        return new GetOpenReportUseCase(authorization, query);
    }

    @Bean
    DiscardRegistrationInProcessingUseCase discardRegistrationInProcessingUseCase(
            JobQueuePort queue, TemporaryAudioService audio, ClockPort clock) {
        return new DiscardRegistrationInProcessingUseCase(queue, audio, clock);
    }

    // ---- Relatório semestral (US4)

    @Bean
    SemesterAccessService semesterAccessService(
            SemesterReportRepository semesterReports, AssignmentRepository assignments) {
        return new SemesterAccessService(semesterReports, assignments);
    }

    @Bean
    @SuppressWarnings("java:S107")
    SemesterProcessorService semesterProcessorService(
            SemesterReportRepository semesterReports,
            LessonReportRepository lessons,
            StudentRepository students,
            TeacherRepository teachers,
            ReportVersionRepository versions,
            ReportInteractionRepository interactions,
            DocumentGenerationService documents,
            JobQueuePort queue,
            TransactionPort transaction,
            ClockPort clock,
            JobProcessorService processor) {
        SemesterProcessorService semester =
                new SemesterProcessorService(
                        semesterReports,
                        lessons,
                        students,
                        teachers,
                        versions,
                        interactions,
                        documents,
                        queue,
                        transaction,
                        clock);
        processor.registerComplement(semester);
        return semester;
    }

    @Bean
    ListAvailableSemestersUseCase listAvailableSemestersUseCase(
            AuthorizationService authorization,
            StudentRepository students,
            LessonReportRepository lessons,
            ClockPort clock) {
        return new ListAvailableSemestersUseCase(authorization, students, lessons, clock);
    }

    @Bean
    GetSemesterPreviewUseCase getSemesterPreviewUseCase(
            AuthorizationService authorization,
            StudentRepository students,
            LessonReportRepository lessons,
            SemesterReportRepository semesterReports,
            JobQueuePort queue) {
        return new GetSemesterPreviewUseCase(
                authorization, students, lessons, semesterReports, queue);
    }

    @Bean
    GenerateSemesterReportUseCase generateSemesterReportUseCase(
            GetSemesterPreviewUseCase preview, JobQueuePort queue, ClockPort clock) {
        return new GenerateSemesterReportUseCase(preview, queue, clock);
    }

    @Bean
    RequestSemesterReportChangeUseCase requestSemesterReportChangeUseCase(
            SemesterAccessService access,
            SemesterReportRepository semesterReports,
            AudioSendingService audioSending,
            JobQueuePort queue,
            ClockPort clock) {
        return new RequestSemesterReportChangeUseCase(
                access, semesterReports, audioSending, queue, clock);
    }

    @Bean
    ApproveSemesterReportUseCase approveSemesterReportUseCase(
            SemesterAccessService access,
            SemesterReportRepository semesterReports,
            ReportVersionRepository versions,
            DocumentGenerationService documents,
            ClockPort clock) {
        return new ApproveSemesterReportUseCase(
                access, semesterReports, versions, documents, clock);
    }

    @Bean
    CancelSemesterReportUseCase cancelSemesterReportUseCase(
            SemesterAccessService access,
            SemesterReportRepository semesterReports,
            JobQueuePort queue,
            ClockPort clock) {
        return new CancelSemesterReportUseCase(access, semesterReports, queue, clock);
    }

    @Bean
    GetSemesterReportUseCase getSemesterReportUseCase(
            SemesterAccessService access,
            ReportInteractionRepository interactions,
            ReportVersionRepository versions,
            PdfDocumentRepository documents,
            DocumentGenerationService generation,
            JobQueuePort queue,
            GetReportDocumentUseCase getDocument) {
        GetSemesterReportUseCase get =
                new GetSemesterReportUseCase(
                        access, interactions, versions, documents, generation, queue);
        getDocument.registerSemesterSource(get);
        return get;
    }

    // ---- Canal Telegram interno (feature 002)

    @Bean
    RetryPolicy channelRetryPolicy(ApplicationProperties properties) {
        ApplicationProperties.TelegramChannel channel = properties.telegramChannel();
        return new RetryPolicy(
                Duration.ofSeconds(channel.retryInitialSeconds()),
                Duration.ofSeconds(channel.retryCeilingSeconds()),
                Duration.ofHours(channel.deadlineHours()),
                () -> ThreadLocalRandom.current().nextDouble(-1.0, 1.0));
    }

    @Bean
    SaveChannelActionsUseCase saveChannelActionsUseCase(
            ChannelActionRepository actions, ClockPort clock, RetryPolicy policy) {
        return new SaveChannelActionsUseCase(actions, clock, policy);
    }

    @Bean
    EmitChannelDocumentUseCase emitChannelDocumentUseCase(
            ChannelDocumentRepository documents, ClockPort clock, RetryPolicy policy) {
        return new EmitChannelDocumentUseCase(documents, clock, policy);
    }

    @Bean
    GetUnconfirmedActionsOfUpdateUseCase getUnconfirmedActionsOfUpdateUseCase(
            ChannelActionRepository actions) {
        return new GetUnconfirmedActionsOfUpdateUseCase(actions);
    }

    @Bean
    ConfirmChannelActionsExecutionUseCase confirmChannelActionsExecutionUseCase(
            ChannelActionRepository actions,
            JobQueuePort queue,
            ClockPort clock,
            RetryPolicy policy) {
        return new ConfirmChannelActionsExecutionUseCase(actions, queue, clock, policy);
    }

    @Bean
    QueryChannelActionUseCase queryChannelActionUseCase(ChannelActionRepository actions) {
        return new QueryChannelActionUseCase(actions);
    }

    @Bean
    GetChannelDocumentUseCase getChannelDocumentUseCase(
            ChannelDocumentRepository documents,
            GetReportDocumentUseCase getDocument,
            ClockPort clock) {
        return new GetChannelDocumentUseCase(documents, getDocument, clock);
    }

    @Bean
    DeliverPendingActionsUseCase deliverPendingActionsUseCase(
            ChannelActionRepository actions,
            ChannelOrchestratorPort orchestrator,
            JobQueuePort queue,
            TelegramLinkRepository links,
            ClockPort clock,
            RetryPolicy policy,
            ApplicationProperties properties) {
        ApplicationProperties.TelegramChannel channel = properties.telegramChannel();
        return new DeliverPendingActionsUseCase(
                actions,
                orchestrator,
                queue,
                links,
                clock,
                policy,
                Duration.ofSeconds(channel.confirmationMaxSeconds()),
                Duration.ofSeconds(channel.leaseSeconds()));
    }

    @Bean
    CleanChannelActionsUseCase cleanChannelActionsUseCase(
            ChannelActionRepository actions,
            ChannelDocumentRepository documents,
            ClockPort clock,
            ApplicationProperties properties) {
        ApplicationProperties.TelegramChannel channel = properties.telegramChannel();
        return new CleanChannelActionsUseCase(
                actions,
                documents,
                clock,
                Duration.ofHours(channel.payloadRetentionHours()),
                Duration.ofDays(channel.recordRetentionDays()));
    }

    // ---- Histórico (US5)

    @Bean
    QueryStudentHistoryUseCase queryStudentHistoryUseCase(
            GetStudentSummaryUseCase studentSummary,
            LessonReportRepository lessons,
            SemesterReportRepository semesterReports) {
        return new QueryStudentHistoryUseCase(studentSummary, lessons, semesterReports);
    }
}
