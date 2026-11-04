package br.com.felipeblaft.reports.config;

import br.com.felipeblaft.reports.domain.operations.AlertType;
import br.com.felipeblaft.reports.port.AuditTrailQueryPort;
import br.com.felipeblaft.reports.port.ClockPort;
import br.com.felipeblaft.reports.port.ConsentRepository;
import br.com.felipeblaft.reports.port.ConsentTermRepository;
import br.com.felipeblaft.reports.port.StudentRepository;
import br.com.felipeblaft.reports.port.RequestContextPort;
import br.com.felipeblaft.reports.port.TransactionPort;
import br.com.felipeblaft.reports.service.ActorAuthorizationService;
import br.com.felipeblaft.reports.service.AuthorshipService;
import br.com.felipeblaft.reports.service.ConsentGuardService;
import br.com.felipeblaft.reports.service.ConsentRecordingService;
import br.com.felipeblaft.reports.service.JobProcessorService;
import br.com.felipeblaft.reports.service.StudentAccessService;
import br.com.felipeblaft.reports.service.OperationalAlertService;
import br.com.felipeblaft.reports.service.TeacherReportAuditService;
import br.com.felipeblaft.reports.usecase.audit.QueryAuditTrailUseCase;
import br.com.felipeblaft.reports.usecase.audit.VerifyAuditChainUseCase;
import br.com.felipeblaft.reports.usecase.lesson.AnswerQuestionsUseCase;
import br.com.felipeblaft.reports.usecase.lesson.RegisterLessonByAudioUseCase;
import br.com.felipeblaft.reports.usecase.lesson.RequestLessonReportChangeUseCase;
import br.com.felipeblaft.reports.usecase.lesson.RetryLessonReportUseCase;
import br.com.felipeblaft.reports.usecase.privacy.GetCurrentConsentTermUseCase;
import br.com.felipeblaft.reports.usecase.privacy.ListConsentTermsUseCase;
import br.com.felipeblaft.reports.usecase.privacy.ListPendingConsentsUseCase;
import br.com.felipeblaft.reports.usecase.privacy.ListStudentConsentsUseCase;
import br.com.felipeblaft.reports.usecase.privacy.PublishConsentTermUseCase;
import br.com.felipeblaft.reports.usecase.privacy.RecordConsentUseCase;
import br.com.felipeblaft.reports.usecase.privacy.RevokeConsentUseCase;
import br.com.felipeblaft.reports.usecase.registry.CollectStudentRegistrationFieldUseCase;
import br.com.felipeblaft.reports.usecase.semester.GenerateSemesterReportUseCase;
import br.com.felipeblaft.reports.usecase.semester.RequestSemesterReportChangeUseCase;
import org.slf4j.Logger;
import org.springframework.beans.factory.InitializingBean;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Casos de uso da feature 007 (privacidade, retenção e operação). Cada história acrescenta os seus;
 * aqui ficam a trilha de auditoria e os serviços que ela usa. Sem regra de negócio.
 */
@Configuration(proxyBeanMethods = false)
public class PrivacyUseCaseConfig {

    private static final Logger LOG = LoggerFactory.getLogger(PrivacyUseCaseConfig.class);

    @Bean
    TeacherReportAuditService teacherReportAuditService(
            AuthorshipService authorship, RequestContextPort context) {
        return new TeacherReportAuditService(authorship, context);
    }

    /**
     * Alerta operacional provisório: registra no log, sem dado pessoal. A história de custos e
     * alertas troca por quem persiste e normaliza.
     */
    @Bean
    @ConditionalOnMissingBean(OperationalAlertService.class)
    OperationalAlertService loggingOperationalAlertService() {
        return (AlertType type, String component, String detailCode) ->
                LOG.error(
                        "Alerta operacional {} no componente {} (motivo {}).",
                        type,
                        component,
                        detailCode);
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
     * Liga a barreira de consentimento nos casos de uso que alcançam a IA e no processador
     * (FR-034, SC-005). Sem ela, o menor sem consentimento chegaria ao provedor.
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
            ConsentTermRepository terms) {
        return () -> {
            collectRegistration.useConsentTerms(terms);
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
            AuthorshipService authorship) {
        return new RecordConsentUseCase(authorization, access, recording, terms, authorship);
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
}
