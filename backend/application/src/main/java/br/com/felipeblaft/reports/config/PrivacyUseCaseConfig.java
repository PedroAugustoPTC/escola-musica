package br.com.felipeblaft.reports.config;

import br.com.felipeblaft.reports.domain.operations.AlertThresholds;
import br.com.felipeblaft.reports.domain.operations.PriceTable;
import br.com.felipeblaft.reports.domain.privacy.PrivacyOfficer;
import br.com.felipeblaft.reports.domain.privacy.RetentionPolicy;
import br.com.felipeblaft.reports.port.AlertHistoryRetentionPort;
import br.com.felipeblaft.reports.port.AssignmentRepository;
import br.com.felipeblaft.reports.port.AuditTrailQueryPort;
import br.com.felipeblaft.reports.port.AuditTrailRetentionPort;
import br.com.felipeblaft.reports.port.ClockPort;
import br.com.felipeblaft.reports.port.ConsentRepository;
import br.com.felipeblaft.reports.port.ConsentTermRepository;
import br.com.felipeblaft.reports.port.CostReportQueryPort;
import br.com.felipeblaft.reports.port.DataProtectionPort;
import br.com.felipeblaft.reports.port.DocumentStoragePort;
import br.com.felipeblaft.reports.port.ErasureRequestRepository;
import br.com.felipeblaft.reports.port.JobQueuePort;
import br.com.felipeblaft.reports.port.LessonReportRepository;
import br.com.felipeblaft.reports.port.OperationalAlertRepository;
import br.com.felipeblaft.reports.port.OperationalSignalQueryPort;
import br.com.felipeblaft.reports.port.PdfDocumentRepository;
import br.com.felipeblaft.reports.port.ReportInteractionRepository;
import br.com.felipeblaft.reports.port.ReportVersionRepository;
import br.com.felipeblaft.reports.port.RequestContextPort;
import br.com.felipeblaft.reports.port.RetentionRunRepository;
import br.com.felipeblaft.reports.port.SemesterReportRepository;
import br.com.felipeblaft.reports.port.StudentRegistrationDraftRepository;
import br.com.felipeblaft.reports.port.StudentRepository;
import br.com.felipeblaft.reports.port.TeacherRepository;
import br.com.felipeblaft.reports.port.TransactionPort;
import br.com.felipeblaft.reports.service.ActorAuthorizationService;
import br.com.felipeblaft.reports.service.AuthorshipService;
import br.com.felipeblaft.reports.service.ConsentGuardService;
import br.com.felipeblaft.reports.service.ConsentRecordingService;
import br.com.felipeblaft.reports.service.ErasureScopeService;
import br.com.felipeblaft.reports.service.JobProcessorService;
import br.com.felipeblaft.reports.service.OperationalAlertService;
import br.com.felipeblaft.reports.service.PersistentOperationalAlertService;
import br.com.felipeblaft.reports.service.StudentAccessService;
import br.com.felipeblaft.reports.service.TeacherReportAuditService;
import br.com.felipeblaft.reports.usecase.audit.QueryAuditTrailUseCase;
import br.com.felipeblaft.reports.usecase.audit.VerifyAuditChainUseCase;
import br.com.felipeblaft.reports.usecase.lesson.AnswerQuestionsUseCase;
import br.com.felipeblaft.reports.usecase.lesson.RegisterLessonByAudioUseCase;
import br.com.felipeblaft.reports.usecase.lesson.RequestLessonReportChangeUseCase;
import br.com.felipeblaft.reports.usecase.lesson.RetryLessonReportUseCase;
import br.com.felipeblaft.reports.usecase.operations.AcknowledgeOperationalAlertUseCase;
import br.com.felipeblaft.reports.usecase.operations.EvaluateOperationalAlertsUseCase;
import br.com.felipeblaft.reports.usecase.operations.GetMonthlyCostReportUseCase;
import br.com.felipeblaft.reports.usecase.operations.ListOperationalAlertsUseCase;
import br.com.felipeblaft.reports.usecase.privacy.ConfirmErasureUseCase;
import br.com.felipeblaft.reports.usecase.privacy.GetCurrentConsentTermUseCase;
import br.com.felipeblaft.reports.usecase.privacy.GetErasureReceiptUseCase;
import br.com.felipeblaft.reports.usecase.privacy.ListConsentTermsUseCase;
import br.com.felipeblaft.reports.usecase.privacy.ListErasureRequestsUseCase;
import br.com.felipeblaft.reports.usecase.privacy.ListPendingConsentsUseCase;
import br.com.felipeblaft.reports.usecase.privacy.ListStudentConsentsUseCase;
import br.com.felipeblaft.reports.usecase.privacy.PreviewErasureUseCase;
import br.com.felipeblaft.reports.usecase.privacy.PublishConsentTermUseCase;
import br.com.felipeblaft.reports.usecase.privacy.RecordConsentUseCase;
import br.com.felipeblaft.reports.usecase.privacy.RegisterErasureRequestUseCase;
import br.com.felipeblaft.reports.usecase.privacy.RevokeConsentUseCase;
import br.com.felipeblaft.reports.usecase.privacy.RunRetentionUseCase;
import br.com.felipeblaft.reports.usecase.registry.CollectStudentRegistrationFieldUseCase;
import br.com.felipeblaft.reports.usecase.registry.ConfirmStudentRegistrationUseCase;
import br.com.felipeblaft.reports.usecase.semester.GenerateSemesterReportUseCase;
import br.com.felipeblaft.reports.usecase.semester.RequestSemesterReportChangeUseCase;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Casos de uso da feature 007 (privacidade, retenção e operação). Cada história acrescenta os seus;
 * aqui ficam a trilha de auditoria e os serviços que ela usa. Sem regra de negócio.
 */
@Configuration(proxyBeanMethods = false)
public class PrivacyUseCaseConfig {

    @Bean
    TeacherReportAuditService teacherReportAuditService(
            AuthorshipService authorship, RequestContextPort context) {
        return new TeacherReportAuditService(authorship, context);
    }

    /** Alertas operacionais persistentes: um ativo por tipo e componente (FR-055, FR-056). */
    @Bean
    OperationalAlertService operationalAlertService(
            OperationalAlertRepository alerts,
            AuthorshipService authorship,
            TransactionPort transaction,
            ClockPort clock) {
        return new PersistentOperationalAlertService(alerts, authorship, transaction, clock);
    }

    @Bean
    EvaluateOperationalAlertsUseCase evaluateOperationalAlertsUseCase(
            OperationalSignalQueryPort signals,
            OperationalAlertRepository repository,
            OperationalAlertService alerts,
            AlertThresholds thresholds,
            ClockPort clock) {
        return new EvaluateOperationalAlertsUseCase(signals, repository, alerts, thresholds, clock);
    }

    @Bean
    ListOperationalAlertsUseCase listOperationalAlertsUseCase(
            ActorAuthorizationService authorization,
            OperationalAlertRepository alerts,
            AuthorshipService authorship,
            TransactionPort transaction) {
        return new ListOperationalAlertsUseCase(authorization, alerts, authorship, transaction);
    }

    @Bean
    AcknowledgeOperationalAlertUseCase acknowledgeOperationalAlertUseCase(
            ActorAuthorizationService authorization,
            OperationalAlertRepository alerts,
            OperationalAlertService alertService,
            AuthorshipService authorship,
            TransactionPort transaction) {
        return new AcknowledgeOperationalAlertUseCase(
                authorization, alerts, alertService, authorship, transaction);
    }

    @Bean
    GetMonthlyCostReportUseCase getMonthlyCostReportUseCase(
            ActorAuthorizationService authorization,
            CostReportQueryPort costs,
            PriceTable prices,
            ClockPort clock,
            AuthorshipService authorship,
            TransactionPort transaction) {
        return new GetMonthlyCostReportUseCase(
                authorization, costs, prices, clock, authorship, transaction);
    }

    @Bean
    QueryAuditTrailUseCase queryAuditTrailUseCase(
            ActorAuthorizationService authorization,
            AuditTrailQueryPort trail,
            AuthorshipService authorship,
            TransactionPort transaction) {
        return new QueryAuditTrailUseCase(authorization, trail, authorship, transaction);
    }

    @Bean
    VerifyAuditChainUseCase verifyAuditChainUseCase(
            ActorAuthorizationService authorization,
            AuditTrailQueryPort trail,
            AuthorshipService authorship,
            OperationalAlertService alerts) {
        return new VerifyAuditChainUseCase(
                authorization,
                trail,
                authorship,
                alerts,
                VerifyAuditChainUseCase.DEFAULT_BATCH_SIZE);
    }

    // ---- Retenção automática (US3)

    @Bean
    RunRetentionUseCase runRetentionUseCase(
            AuthorshipService authorship,
            ClockPort clock,
            TransactionPort transaction,
            RetentionPolicy policy,
            LessonReportRepository lessons,
            SemesterReportRepository semesters,
            ReportVersionRepository versions,
            ReportInteractionRepository interactions,
            PdfDocumentRepository pdfs,
            DocumentStoragePort storage,
            RetentionRunRepository runs,
            AuditTrailRetentionPort trail,
            AlertHistoryRetentionPort alertHistory,
            OperationalAlertService alerts,
            VerifyAuditChainUseCase verifyChain) {
        return new RunRetentionUseCase(
                authorship,
                clock,
                transaction,
                policy,
                lessons,
                semesters,
                versions,
                interactions,
                pdfs,
                storage,
                runs,
                trail,
                alertHistory,
                alerts,
                verifyChain);
    }

    // ---- Consentimento do responsável (US2)

    @Bean
    ConsentRecordingService consentRecordingService(
            ConsentRepository consents,
            ConsentTermRepository terms,
            AuthorshipService authorship,
            ClockPort clock) {
        return new ConsentRecordingService(consents, terms, authorship, clock);
    }

    @Bean
    ConsentGuardService consentGuardService(
            StudentRepository students,
            ConsentRepository consents,
            ConsentTermRepository terms,
            ClockPort clock,
            AuthorshipService authorship,
            RequestContextPort context) {
        return new ConsentGuardService(students, consents, terms, clock, authorship, context);
    }

    /**
     * Liga a barreira de consentimento nos casos de uso que alcançam a IA e no processador (FR-034,
     * SC-005). Sem ela, o menor sem consentimento chegaria ao provedor.
     */
    @Bean
    @SuppressWarnings("java:S107")
    InitializingBean consentGuardWiring(
            ConsentGuardService guard,
            RegisterLessonByAudioUseCase register,
            AnswerQuestionsUseCase answer,
            RequestLessonReportChangeUseCase change,
            RetryLessonReportUseCase retry,
            GenerateSemesterReportUseCase generateSemester,
            RequestSemesterReportChangeUseCase changeSemester,
            JobProcessorService processor,
            CollectStudentRegistrationFieldUseCase collectRegistration,
            ConfirmStudentRegistrationUseCase confirmRegistration,
            RecordConsentUseCase recordConsent,
            ConsentTermRepository terms) {
        return () -> {
            collectRegistration.useConsentTerms(terms);
            confirmRegistration.useConsentRecording(recordConsent);
            register.useConsentGuard(guard);
            answer.useConsentGuard(guard);
            change.useConsentGuard(guard);
            retry.useConsentGuard(guard);
            generateSemester.useConsentGuard(guard);
            changeSemester.useConsentGuard(guard);
            processor.useConsentGuard(guard);
        };
    }

    @Bean
    RecordConsentUseCase recordConsentUseCase(
            ActorAuthorizationService authorization,
            StudentAccessService access,
            ConsentRecordingService recording,
            ConsentTermRepository terms,
            AuthorshipService authorship,
            ConsentGuardService guard) {
        return new RecordConsentUseCase(authorization, access, recording, terms, authorship, guard);
    }

    @Bean
    RevokeConsentUseCase revokeConsentUseCase(
            ActorAuthorizationService authorization,
            ConsentRepository consents,
            ConsentTermRepository terms,
            AuthorshipService authorship,
            ClockPort clock) {
        return new RevokeConsentUseCase(authorization, consents, terms, authorship, clock);
    }

    @Bean
    PublishConsentTermUseCase publishConsentTermUseCase(
            ActorAuthorizationService authorization,
            ConsentTermRepository terms,
            AuthorshipService authorship,
            ClockPort clock) {
        return new PublishConsentTermUseCase(authorization, terms, authorship, clock);
    }

    @Bean
    GetCurrentConsentTermUseCase getCurrentConsentTermUseCase(
            ActorAuthorizationService authorization, ConsentTermRepository terms) {
        return new GetCurrentConsentTermUseCase(authorization, terms);
    }

    @Bean
    ListConsentTermsUseCase listConsentTermsUseCase(
            ActorAuthorizationService authorization, ConsentTermRepository terms) {
        return new ListConsentTermsUseCase(authorization, terms);
    }

    @Bean
    ListStudentConsentsUseCase listStudentConsentsUseCase(
            ActorAuthorizationService authorization,
            StudentAccessService access,
            ConsentRepository consents,
            ConsentTermRepository terms) {
        return new ListStudentConsentsUseCase(authorization, access, consents, terms);
    }

    @Bean
    ListPendingConsentsUseCase listPendingConsentsUseCase(
            ActorAuthorizationService authorization,
            StudentAccessService access,
            ConsentRepository consents,
            ConsentTermRepository terms,
            ClockPort clock) {
        return new ListPendingConsentsUseCase(authorization, access, consents, terms, clock);
    }

    // ---- Eliminação a pedido (US4)

    @Bean
    ErasureScopeService erasureScopeService(
            LessonReportRepository lessons,
            SemesterReportRepository semesters,
            PdfDocumentRepository pdfs,
            AssignmentRepository assignments,
            ConsentRepository consents) {
        return new ErasureScopeService(lessons, semesters, pdfs, assignments, consents);
    }

    @Bean
    RegisterErasureRequestUseCase registerErasureRequestUseCase(
            ActorAuthorizationService authorization,
            StudentRepository students,
            ErasureRequestRepository erasures,
            AuthorshipService authorship,
            ClockPort clock) {
        return new RegisterErasureRequestUseCase(
                authorization, students, erasures, authorship, clock);
    }

    @Bean
    PreviewErasureUseCase previewErasureUseCase(
            ActorAuthorizationService authorization,
            ErasureRequestRepository erasures,
            ErasureScopeService scopes,
            AssignmentRepository assignments,
            TeacherRepository teachers,
            DataProtectionPort protection,
            PrivacyOfficer privacyContact,
            AuthorshipService authorship,
            TransactionPort transaction,
            ClockPort clock) {
        return new PreviewErasureUseCase(
                authorization,
                erasures,
                scopes,
                assignments,
                teachers,
                protection,
                privacyContact,
                authorship,
                transaction,
                clock);
    }

    @Bean
    @SuppressWarnings("java:S107")
    ConfirmErasureUseCase confirmErasureUseCase(
            ActorAuthorizationService authorization,
            ErasureRequestRepository erasures,
            ErasureScopeService scopes,
            LessonReportRepository lessons,
            SemesterReportRepository semesters,
            ReportVersionRepository versions,
            ReportInteractionRepository interactions,
            PdfDocumentRepository pdfs,
            AssignmentRepository assignments,
            ConsentRepository consents,
            StudentRepository students,
            StudentRegistrationDraftRepository drafts,
            JobQueuePort queue,
            DocumentStoragePort storage,
            DataProtectionPort protection,
            OperationalAlertService alerts,
            AuthorshipService authorship,
            TransactionPort transaction,
            ClockPort clock) {
        return new ConfirmErasureUseCase(
                authorization,
                erasures,
                scopes,
                lessons,
                semesters,
                versions,
                interactions,
                pdfs,
                assignments,
                consents,
                students,
                drafts,
                queue,
                storage,
                protection,
                alerts,
                authorship,
                transaction,
                clock);
    }

    @Bean
    GetErasureReceiptUseCase getErasureReceiptUseCase(
            ActorAuthorizationService authorization,
            ErasureRequestRepository erasures,
            PrivacyOfficer privacyContact,
            AuthorshipService authorship) {
        return new GetErasureReceiptUseCase(authorization, erasures, privacyContact, authorship);
    }

    @Bean
    ListErasureRequestsUseCase listErasureRequestsUseCase(
            ActorAuthorizationService authorization,
            ErasureRequestRepository erasures,
            StudentRepository students) {
        return new ListErasureRequestsUseCase(authorization, erasures, students);
    }
}
