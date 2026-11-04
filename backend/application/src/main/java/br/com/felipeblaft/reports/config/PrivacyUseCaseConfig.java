package br.com.felipeblaft.reports.config;

import br.com.felipeblaft.reports.domain.operations.AlertType;
import br.com.felipeblaft.reports.port.AuditTrailQueryPort;
import br.com.felipeblaft.reports.port.RequestContextPort;
import br.com.felipeblaft.reports.port.TransactionPort;
import br.com.felipeblaft.reports.service.ActorAuthorizationService;
import br.com.felipeblaft.reports.service.AuthorshipService;
import br.com.felipeblaft.reports.service.OperationalAlertService;
import br.com.felipeblaft.reports.service.TeacherReportAuditService;
import br.com.felipeblaft.reports.usecase.audit.QueryAuditTrailUseCase;
import br.com.felipeblaft.reports.usecase.audit.VerifyAuditChainUseCase;
import org.slf4j.Logger;
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
}
