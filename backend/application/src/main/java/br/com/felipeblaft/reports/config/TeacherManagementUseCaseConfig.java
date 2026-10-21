package br.com.felipeblaft.reports.config;

import br.com.felipeblaft.reports.port.ChannelActionRepository;
import br.com.felipeblaft.reports.port.ClockPort;
import br.com.felipeblaft.reports.port.StudentRegistrationDraftRepository;
import br.com.felipeblaft.reports.port.TeacherRepository;
import br.com.felipeblaft.reports.port.TelegramLinkRepository;
import br.com.felipeblaft.reports.service.AccessCodeIssuanceService;
import br.com.felipeblaft.reports.service.ActorAuthorizationService;
import br.com.felipeblaft.reports.service.AuthorshipService;
import br.com.felipeblaft.reports.service.SessionRevocationService;
import br.com.felipeblaft.reports.service.TelegramLinkTerminationService;
import br.com.felipeblaft.reports.usecase.teacher.GetTeacherUseCase;
import br.com.felipeblaft.reports.usecase.teacher.InactivateTeacherUseCase;
import br.com.felipeblaft.reports.usecase.teacher.ListTeachersUseCase;
import br.com.felipeblaft.reports.usecase.teacher.ReactivateTeacherUseCase;
import br.com.felipeblaft.reports.usecase.teacher.RegisterTeacherUseCase;
import br.com.felipeblaft.reports.usecase.teacher.ReissueAccessCodeUseCase;
import br.com.felipeblaft.reports.usecase.teacher.UnlinkTeacherTelegramUseCase;
import br.com.felipeblaft.reports.usecase.teacher.UpdateTeacherUseCase;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Casos de uso da gestão de professores pelo administrador (feature 005) e o serviço que encerra o
 * vínculo do Telegram por qualquer causa. Os serviços comuns ({@code ActorAuthorizationService},
 * {@code AuthorshipService}, {@code SessionRevocationService}) ficam em {@link
 * AdministrationUseCaseConfig}; o {@code AccessCodeIssuanceService} do professor, em {@link
 * UseCaseConfig}.
 */
@Configuration(proxyBeanMethods = false)
public class TeacherManagementUseCaseConfig {

    @Bean
    TelegramLinkTerminationService telegramLinkTerminationService(
            TelegramLinkRepository links,
            StudentRegistrationDraftRepository drafts,
            ChannelActionRepository actions,
            ClockPort clock) {
        return new TelegramLinkTerminationService(links, drafts, actions, clock);
    }

    @Bean
    ListTeachersUseCase listTeachersUseCase(
            ActorAuthorizationService authorization,
            TeacherRepository teachers,
            TelegramLinkRepository links) {
        return new ListTeachersUseCase(authorization, teachers, links);
    }

    @Bean
    GetTeacherUseCase getTeacherUseCase(
            ActorAuthorizationService authorization,
            TeacherRepository teachers,
            TelegramLinkRepository links) {
        return new GetTeacherUseCase(authorization, teachers, links);
    }

    @Bean
    RegisterTeacherUseCase registerTeacherUseCase(
            ActorAuthorizationService authorization,
            TeacherRepository teachers,
            AccessCodeIssuanceService issuance,
            AuthorshipService authorship,
            ClockPort clock) {
        return new RegisterTeacherUseCase(authorization, teachers, issuance, authorship, clock);
    }

    @Bean
    UpdateTeacherUseCase updateTeacherUseCase(
            ActorAuthorizationService authorization,
            TeacherRepository teachers,
            TelegramLinkRepository links,
            AuthorshipService authorship,
            ClockPort clock) {
        return new UpdateTeacherUseCase(authorization, teachers, links, authorship, clock);
    }

    @Bean
    InactivateTeacherUseCase inactivateTeacherUseCase(
            ActorAuthorizationService authorization,
            TeacherRepository teachers,
            SessionRevocationService revocation,
            TelegramLinkTerminationService linkTermination,
            AuthorshipService authorship,
            ClockPort clock) {
        return new InactivateTeacherUseCase(
                authorization, teachers, revocation, linkTermination, authorship, clock);
    }

    @Bean
    ReactivateTeacherUseCase reactivateTeacherUseCase(
            ActorAuthorizationService authorization,
            TeacherRepository teachers,
            AccessCodeIssuanceService issuance,
            AuthorshipService authorship,
            ClockPort clock) {
        return new ReactivateTeacherUseCase(authorization, teachers, issuance, authorship, clock);
    }

    @Bean
    ReissueAccessCodeUseCase reissueAccessCodeUseCase(
            ActorAuthorizationService authorization,
            TeacherRepository teachers,
            AccessCodeIssuanceService issuance,
            SessionRevocationService revocation,
            TelegramLinkTerminationService linkTermination,
            AuthorshipService authorship,
            ClockPort clock) {
        return new ReissueAccessCodeUseCase(
                authorization, teachers, issuance, revocation, linkTermination, authorship, clock);
    }

    @Bean
    UnlinkTeacherTelegramUseCase unlinkTeacherTelegramUseCase(
            ActorAuthorizationService authorization,
            TeacherRepository teachers,
            TelegramLinkTerminationService linkTermination,
            AuthorshipService authorship) {
        return new UnlinkTeacherTelegramUseCase(
                authorization, teachers, linkTermination, authorship);
    }
}
