package br.com.felipeblaft.reports.config;

import br.com.felipeblaft.reports.domain.access.PermissionMatrix;
import br.com.felipeblaft.reports.port.ActivationCodeGeneratorPort;
import br.com.felipeblaft.reports.port.AdministratorRepository;
import br.com.felipeblaft.reports.port.AdministratorSessionRepository;
import br.com.felipeblaft.reports.port.AuthorshipRecorderPort;
import br.com.felipeblaft.reports.port.ClockPort;
import br.com.felipeblaft.reports.port.DataProtectionPort;
import br.com.felipeblaft.reports.port.PasswordHashPort;
import br.com.felipeblaft.reports.port.WebSessionRepository;
import br.com.felipeblaft.reports.service.AccessAttemptsControlService;
import br.com.felipeblaft.reports.service.ActivationCodeIssuanceService;
import br.com.felipeblaft.reports.service.ActorAuthorizationService;
import br.com.felipeblaft.reports.service.AuthorshipService;
import br.com.felipeblaft.reports.service.SessionRevocationService;
import br.com.felipeblaft.reports.usecase.administrator.ActivateAdministratorUseCase;
import br.com.felipeblaft.reports.usecase.administrator.BootstrapFirstAdministratorUseCase;
import br.com.felipeblaft.reports.usecase.administrator.ChangeProvisionalPasswordUseCase;
import br.com.felipeblaft.reports.usecase.administrator.CreateAdministratorUseCase;
import br.com.felipeblaft.reports.usecase.administrator.GetAdministratorSessionUseCase;
import br.com.felipeblaft.reports.usecase.administrator.InactivateAdministratorUseCase;
import br.com.felipeblaft.reports.usecase.administrator.ListAdministratorsUseCase;
import br.com.felipeblaft.reports.usecase.administrator.ReactivateAdministratorUseCase;
import br.com.felipeblaft.reports.usecase.administrator.SignInAdministratorUseCase;
import br.com.felipeblaft.reports.usecase.administrator.SignOutAdministratorUseCase;
import br.com.felipeblaft.reports.usecase.administrator.ValidateAdministratorSessionUseCase;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Serviços comuns da feature 005 (matriz de permissões, autorização, autoria, revogação de sessões
 * e código de ativação) e casos de uso do acesso do administrador. O núcleo não tem anotações de
 * framework; toda a montagem fica na aplicação. Os casos de uso de professor e de aluno estão em
 * {@link TeacherManagementUseCaseConfig} e {@link StudentRegistryUseCaseConfig}.
 */
@Configuration(proxyBeanMethods = false)
public class AdministrationUseCaseConfig {

    // ---- Serviços comuns

    @Bean
    PermissionMatrix permissionMatrix() {
        return PermissionMatrix.standard();
    }

    @Bean
    ActorAuthorizationService actorAuthorizationService(PermissionMatrix matrix) {
        return new ActorAuthorizationService(matrix);
    }

    @Bean
    AuthorshipService authorshipService(AuthorshipRecorderPort recorder, ClockPort clock) {
        return new AuthorshipService(recorder, clock);
    }

    @Bean
    ActivationCodeIssuanceService activationCodeIssuanceService(
            ActivationCodeGeneratorPort generator, DataProtectionPort protection) {
        return new ActivationCodeIssuanceService(generator, protection);
    }

    @Bean
    SessionRevocationService sessionRevocationService(
            WebSessionRepository teacherSessions,
            AdministratorSessionRepository administratorSessions,
            ClockPort clock) {
        return new SessionRevocationService(teacherSessions, administratorSessions, clock);
    }

    // ---- Acesso do administrador

    @Bean
    SignInAdministratorUseCase signInAdministratorUseCase(
            AdministratorRepository administrators,
            AdministratorSessionRepository sessions,
            PasswordHashPort passwords,
            DataProtectionPort protection,
            AccessAttemptsControlService attempts,
            AuthorshipService authorship,
            ClockPort clock,
            ApplicationProperties properties) {
        return new SignInAdministratorUseCase(
                administrators,
                sessions,
                passwords,
                protection,
                attempts,
                authorship,
                clock,
                sessionDuration(properties));
    }

    @Bean
    ValidateAdministratorSessionUseCase validateAdministratorSessionUseCase(
            AdministratorSessionRepository sessions,
            DataProtectionPort protection,
            ClockPort clock) {
        return new ValidateAdministratorSessionUseCase(sessions, protection, clock);
    }

    @Bean
    GetAdministratorSessionUseCase getAdministratorSessionUseCase(
            ActorAuthorizationService authorization,
            AdministratorSessionRepository sessions,
            AdministratorRepository administrators,
            DataProtectionPort protection) {
        return new GetAdministratorSessionUseCase(
                authorization, sessions, administrators, protection);
    }

    @Bean
    SignOutAdministratorUseCase signOutAdministratorUseCase(
            ActorAuthorizationService authorization,
            AdministratorSessionRepository sessions,
            DataProtectionPort protection,
            AuthorshipService authorship) {
        return new SignOutAdministratorUseCase(authorization, sessions, protection, authorship);
    }

    @Bean
    ActivateAdministratorUseCase activateAdministratorUseCase(
            AdministratorRepository administrators,
            DataProtectionPort protection,
            PasswordHashPort passwords,
            AccessAttemptsControlService attempts,
            AuthorshipService authorship,
            ClockPort clock) {
        return new ActivateAdministratorUseCase(
                administrators, protection, passwords, attempts, authorship, clock);
    }

    @Bean
    @SuppressWarnings("java:S107")
    ChangeProvisionalPasswordUseCase changeProvisionalPasswordUseCase(
            ActorAuthorizationService authorization,
            AdministratorRepository administrators,
            AdministratorSessionRepository sessions,
            SessionRevocationService revocation,
            PasswordHashPort passwords,
            DataProtectionPort protection,
            AuthorshipService authorship,
            ClockPort clock,
            ApplicationProperties properties) {
        return new ChangeProvisionalPasswordUseCase(
                authorization,
                administrators,
                sessions,
                revocation,
                passwords,
                protection,
                authorship,
                clock,
                sessionDuration(properties));
    }

    // ---- Gestão de administradores

    @Bean
    ListAdministratorsUseCase listAdministratorsUseCase(
            ActorAuthorizationService authorization, AdministratorRepository administrators) {
        return new ListAdministratorsUseCase(authorization, administrators);
    }

    @Bean
    CreateAdministratorUseCase createAdministratorUseCase(
            ActorAuthorizationService authorization,
            AdministratorRepository administrators,
            ActivationCodeIssuanceService issuance,
            AuthorshipService authorship,
            ClockPort clock) {
        return new CreateAdministratorUseCase(
                authorization, administrators, issuance, authorship, clock);
    }

    @Bean
    InactivateAdministratorUseCase inactivateAdministratorUseCase(
            ActorAuthorizationService authorization,
            AdministratorRepository administrators,
            SessionRevocationService revocation,
            AuthorshipService authorship,
            ClockPort clock) {
        return new InactivateAdministratorUseCase(
                authorization, administrators, revocation, authorship, clock);
    }

    @Bean
    ReactivateAdministratorUseCase reactivateAdministratorUseCase(
            ActorAuthorizationService authorization,
            AdministratorRepository administrators,
            ActivationCodeIssuanceService issuance,
            AuthorshipService authorship,
            ClockPort clock) {
        return new ReactivateAdministratorUseCase(
                authorization, administrators, issuance, authorship, clock);
    }

    // ---- Primeiro administrador (inicialização)

    @Bean
    BootstrapFirstAdministratorUseCase bootstrapFirstAdministratorUseCase(
            ActorAuthorizationService authorization,
            AdministratorRepository administrators,
            PasswordHashPort passwords,
            AuthorshipService authorship,
            ClockPort clock,
            ApplicationProperties properties) {
        return new BootstrapFirstAdministratorUseCase(
                authorization,
                administrators,
                passwords,
                authorship,
                clock,
                Duration.ofDays(properties.administrator().provisionalPasswordDays()));
    }

    private static Duration sessionDuration(ApplicationProperties properties) {
        return Duration.ofHours(properties.administrator().sessionHours());
    }
}
