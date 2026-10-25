package br.com.felipeblaft.reports.config;

import br.com.felipeblaft.reports.port.AssignmentRepository;
import br.com.felipeblaft.reports.port.ClockPort;
import br.com.felipeblaft.reports.port.CpfDecryptionPort;
import br.com.felipeblaft.reports.port.CpfKeyRotationPort;
import br.com.felipeblaft.reports.port.DataProtectionPort;
import br.com.felipeblaft.reports.port.OpenReportsQueryPort;
import br.com.felipeblaft.reports.port.StudentRegistrationDraftRepository;
import br.com.felipeblaft.reports.port.StudentRepository;
import br.com.felipeblaft.reports.port.TeacherRepository;
import br.com.felipeblaft.reports.port.TelegramLinkRepository;
import br.com.felipeblaft.reports.port.TransactionPort;
import br.com.felipeblaft.reports.service.AccessAttemptsControlService;
import br.com.felipeblaft.reports.service.ActorAuthorizationService;
import br.com.felipeblaft.reports.service.AuthorshipService;
import br.com.felipeblaft.reports.service.StudentAccessService;
import br.com.felipeblaft.reports.usecase.registry.AssignTeacherToStudentUseCase;
import br.com.felipeblaft.reports.usecase.registry.CancelStudentRegistrationUseCase;
import br.com.felipeblaft.reports.usecase.registry.CollectStudentRegistrationFieldUseCase;
import br.com.felipeblaft.reports.usecase.registry.ConfirmStudentRegistrationUseCase;
import br.com.felipeblaft.reports.usecase.registry.GetStudentUseCase;
import br.com.felipeblaft.reports.usecase.registry.InactivateStudentUseCase;
import br.com.felipeblaft.reports.usecase.registry.ListStudentsUseCase;
import br.com.felipeblaft.reports.usecase.registry.PreviewAssignmentRemovalUseCase;
import br.com.felipeblaft.reports.usecase.registry.ReactivateStudentUseCase;
import br.com.felipeblaft.reports.usecase.registry.RegisterStudentUseCase;
import br.com.felipeblaft.reports.usecase.registry.RemoveTeacherFromStudentUseCase;
import br.com.felipeblaft.reports.usecase.registry.StartStudentRegistrationUseCase;
import br.com.felipeblaft.reports.usecase.registry.UpdateStudentUseCase;
import br.com.felipeblaft.reports.usecase.seed.RotateCpfKeysUseCase;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Casos de uso do cadastro de alunos e das associações (feature 005), usados pelo administrador na
 * Web e pelo professor na Web e no Telegram, e a rotação das chaves de CPF. O escopo do professor é
 * decidido no núcleo, pelo {@code StudentAccessService}.
 */
@Configuration(proxyBeanMethods = false)
public class StudentRegistryUseCaseConfig {

    @Bean
    StudentAccessService studentAccessService(StudentRepository students) {
        return new StudentAccessService(students);
    }

    // ---- Alunos

    @Bean
    ListStudentsUseCase listStudentsUseCase(
            ActorAuthorizationService authorization,
            StudentAccessService access,
            AssignmentRepository assignments,
            TeacherRepository teachers,
            ClockPort clock) {
        return new ListStudentsUseCase(authorization, access, assignments, teachers, clock);
    }

    @Bean
    GetStudentUseCase getStudentUseCase(
            ActorAuthorizationService authorization,
            StudentAccessService access,
            AssignmentRepository assignments,
            TeacherRepository teachers,
            AuthorshipService authorship,
            ClockPort clock) {
        return new GetStudentUseCase(
                authorization, access, assignments, teachers, authorship, clock);
    }

    @Bean
    @SuppressWarnings("java:S107")
    RegisterStudentUseCase registerStudentUseCase(
            ActorAuthorizationService authorization,
            StudentRepository students,
            AssignmentRepository assignments,
            TeacherRepository teachers,
            DataProtectionPort protection,
            AccessAttemptsControlService attempts,
            AuthorshipService authorship,
            ClockPort clock) {
        return new RegisterStudentUseCase(
                authorization,
                students,
                assignments,
                teachers,
                protection,
                attempts,
                authorship,
                clock);
    }

    @Bean
    @SuppressWarnings("java:S107")
    UpdateStudentUseCase updateStudentUseCase(
            ActorAuthorizationService authorization,
            StudentAccessService access,
            StudentRepository students,
            AssignmentRepository assignments,
            TeacherRepository teachers,
            DataProtectionPort protection,
            AccessAttemptsControlService attempts,
            AuthorshipService authorship,
            ClockPort clock) {
        return new UpdateStudentUseCase(
                authorization,
                access,
                students,
                assignments,
                teachers,
                protection,
                attempts,
                authorship,
                clock);
    }

    @Bean
    InactivateStudentUseCase inactivateStudentUseCase(
            ActorAuthorizationService authorization,
            StudentAccessService access,
            StudentRepository students,
            AssignmentRepository assignments,
            AuthorshipService authorship,
            ClockPort clock) {
        return new InactivateStudentUseCase(
                authorization, access, students, assignments, authorship, clock);
    }

    @Bean
    ReactivateStudentUseCase reactivateStudentUseCase(
            ActorAuthorizationService authorization,
            StudentAccessService access,
            StudentRepository students,
            AssignmentRepository assignments,
            AuthorshipService authorship,
            ClockPort clock) {
        return new ReactivateStudentUseCase(
                authorization, access, students, assignments, authorship, clock);
    }

    // ---- Associações

    @Bean
    AssignTeacherToStudentUseCase assignTeacherToStudentUseCase(
            ActorAuthorizationService authorization,
            StudentRepository students,
            TeacherRepository teachers,
            AssignmentRepository assignments,
            AuthorshipService authorship,
            ClockPort clock) {
        return new AssignTeacherToStudentUseCase(
                authorization, students, teachers, assignments, authorship, clock);
    }

    @Bean
    RemoveTeacherFromStudentUseCase removeTeacherFromStudentUseCase(
            ActorAuthorizationService authorization,
            AssignmentRepository assignments,
            AuthorshipService authorship,
            ClockPort clock) {
        return new RemoveTeacherFromStudentUseCase(authorization, assignments, authorship, clock);
    }

    @Bean
    PreviewAssignmentRemovalUseCase previewAssignmentRemovalUseCase(
            ActorAuthorizationService authorization,
            AssignmentRepository assignments,
            OpenReportsQueryPort openReports) {
        return new PreviewAssignmentRemovalUseCase(authorization, assignments, openReports);
    }

    // ---- Cadastro de aluno em andamento no Telegram

    @Bean
    StartStudentRegistrationUseCase startStudentRegistrationUseCase(
            ActorAuthorizationService authorization,
            StudentAccessService access,
            TelegramLinkRepository links,
            StudentRegistrationDraftRepository drafts,
            ClockPort clock,
            ApplicationProperties properties) {
        return new StartStudentRegistrationUseCase(
                authorization, access, links, drafts, clock, draftValidity(properties));
    }

    @Bean
    CollectStudentRegistrationFieldUseCase collectStudentRegistrationFieldUseCase(
            ActorAuthorizationService authorization,
            StudentRegistrationDraftRepository drafts,
            DataProtectionPort protection,
            AccessAttemptsControlService attempts,
            AuthorshipService authorship,
            ClockPort clock,
            ApplicationProperties properties) {
        return new CollectStudentRegistrationFieldUseCase(
                authorization,
                drafts,
                protection,
                attempts,
                authorship,
                clock,
                draftValidity(properties));
    }

    @Bean
    ConfirmStudentRegistrationUseCase confirmStudentRegistrationUseCase(
            ActorAuthorizationService authorization,
            StudentRegistrationDraftRepository drafts,
            RegisterStudentUseCase register,
            UpdateStudentUseCase update,
            InactivateStudentUseCase inactivate,
            ReactivateStudentUseCase reactivate,
            CpfDecryptionPort cpfDecryption,
            TransactionPort transaction,
            ClockPort clock) {
        return new ConfirmStudentRegistrationUseCase(
                authorization,
                drafts,
                register,
                update,
                inactivate,
                reactivate,
                cpfDecryption,
                transaction,
                clock);
    }

    @Bean
    CancelStudentRegistrationUseCase cancelStudentRegistrationUseCase(
            ActorAuthorizationService authorization, StudentRegistrationDraftRepository drafts) {
        return new CancelStudentRegistrationUseCase(authorization, drafts);
    }

    // ---- Rotação das chaves de CPF (operação de sistema)

    @Bean
    RotateCpfKeysUseCase rotateCpfKeysUseCase(
            CpfKeyRotationPort rotation, TransactionPort transaction) {
        return new RotateCpfKeysUseCase(rotation, transaction);
    }

    private static Duration draftValidity(ApplicationProperties properties) {
        return Duration.ofMinutes(properties.telegram().studentDraftMinutes());
    }
}
