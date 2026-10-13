package br.com.felipeblaft.reports.configuracao;

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
 * framework; toda a montagem fica aqui.
 */
@Configuration(proxyBeanMethods = false)
public class CasosDeUsoConfiguracao {

    @Bean
    BusinessParameters parametrosNegocio(PropriedadesAplicacao propriedades) {
        return new BusinessParameters(
                Duration.ofDays(propriedades.codigo().validadeDias()),
                propriedades.acesso().maxTentativas(),
                Duration.ofMinutes(propriedades.acesso().bloqueioMinutos()),
                Duration.ofHours(propriedades.sessao().horas()),
                Duration.ofMinutes(propriedades.telegram().audioPendenteMinutos()),
                propriedades.audio().limiteBytes());
    }

    // ---- Serviços

    @Bean
    AuthorizationService autorizacaoServico(AssignmentRepository associacoes) {
        return new AuthorizationService(associacoes);
    }

    @Bean
    AccessCodeIssuanceService emissaoCodigoAcessoServico(
            AccessCodeGeneratorPort gerador,
            DataProtectionPort protecao,
            TeacherRepository professores,
            BusinessParameters parametros) {
        return new AccessCodeIssuanceService(gerador, protecao, professores, parametros);
    }

    @Bean
    AccessAttemptsControlService controleTentativasAcessoServico(
            AccessAttemptRepository tentativas,
            DataProtectionPort protecao,
            ClockPort relogio,
            BusinessParameters parametros) {
        return new AccessAttemptsControlService(tentativas, protecao, relogio, parametros);
    }

    @Bean
    AccessRenewalService renovacaoAcessoServico(
            TeacherRepository professores, ClockPort relogio, BusinessParameters parametros) {
        return new AccessRenewalService(professores, relogio, parametros);
    }

    @Bean
    CodeAuthenticationService autenticacaoCodigoServico(
            AccessAttemptsControlService tentativas,
            TeacherRepository professores,
            DataProtectionPort protecao,
            ClockPort relogio) {
        return new CodeAuthenticationService(tentativas, professores, protecao, relogio);
    }

    // ---- Carga inicial

    @Bean
    LoadInitialRegistryUseCase carregarCadastroInicialCasoDeUso(
            TeacherRepository professores,
            StudentRepository alunos,
            AssignmentRepository associacoes,
            DataProtectionPort protecao,
            AccessCodeIssuanceService emissao,
            ClockPort relogio) {
        return new LoadInitialRegistryUseCase(
                professores, alunos, associacoes, protecao, emissao, relogio);
    }

    @Bean
    EmitAccessCodeUseCase emitirCodigoAcessoCasoDeUso(
            TeacherRepository professores, AccessCodeIssuanceService emissao, ClockPort relogio) {
        return new EmitAccessCodeUseCase(professores, emissao, relogio);
    }

    // ---- Acesso

    @Bean
    SignInPortalUseCase entrarPortalCasoDeUso(
            CodeAuthenticationService autenticacao,
            AccessRenewalService renovacao,
            WebSessionRepository sessoes,
            TelegramLinkRepository vinculos,
            DataProtectionPort protecao,
            ClockPort relogio,
            BusinessParameters parametros) {
        return new SignInPortalUseCase(
                autenticacao, renovacao, sessoes, vinculos, protecao, relogio, parametros);
    }

    @Bean
    ValidateSessionWebUseCase validarSessaoWebCasoDeUso(
            WebSessionRepository sessoes,
            TeacherRepository professores,
            AccessRenewalService renovacao,
            DataProtectionPort protecao,
            ClockPort relogio,
            BusinessParameters parametros) {
        return new ValidateSessionWebUseCase(
                sessoes, professores, renovacao, protecao, relogio, parametros);
    }

    @Bean
    EndWebSessionUseCase encerrarSessaoWebCasoDeUso(
            WebSessionRepository sessoes, DataProtectionPort protecao) {
        return new EndWebSessionUseCase(sessoes, protecao);
    }

    @Bean
    LinkTelegramUseCase vincularTelegramCasoDeUso(
            CodeAuthenticationService autenticacao,
            AccessRenewalService renovacao,
            TelegramLinkRepository vinculos,
            ClockPort relogio) {
        return new LinkTelegramUseCase(autenticacao, renovacao, vinculos, relogio);
    }

    @Bean
    IdentifyTelegramTeacherUseCase identificarProfessorTelegramCasoDeUso(
            TelegramLinkRepository vinculos,
            TeacherRepository professores,
            AccessRenewalService renovacao) {
        return new IdentifyTelegramTeacherUseCase(vinculos, professores, renovacao);
    }

    @Bean
    EndTelegramLinkUseCase encerrarVinculoTelegramCasoDeUso(TelegramLinkRepository vinculos) {
        return new EndTelegramLinkUseCase(vinculos);
    }

    @Bean
    UpdateTelegramConversationUseCase atualizarConversaTelegramCasoDeUso(
            TelegramLinkRepository vinculos, ClockPort relogio) {
        return new UpdateTelegramConversationUseCase(vinculos, relogio);
    }

    @Bean
    QueryTelegramLinkUseCase consultarVinculoTelegramCasoDeUso(TelegramLinkRepository vinculos) {
        return new QueryTelegramLinkUseCase(vinculos);
    }

    // ---- Início e alunos

    @Bean
    GetTeacherHomeUseCase obterInicioProfessorCasoDeUso(
            StudentRepository alunos, OpenReportsQueryPort consulta) {
        return new GetTeacherHomeUseCase(alunos, consulta);
    }

    @Bean
    SearchStudentsUseCase buscarAlunosCasoDeUso(
            StudentRepository alunos, OpenReportsQueryPort consulta) {
        return new SearchStudentsUseCase(alunos, consulta);
    }

    @Bean
    GetStudentSummaryUseCase obterResumoAlunoCasoDeUso(
            StudentRepository alunos,
            OpenReportsQueryPort consulta,
            AuthorizationService autorizacao) {
        return new GetStudentSummaryUseCase(alunos, consulta, autorizacao);
    }

    // ---- Registro de aula (US1): serviços

    @Bean
    ReportAccessService acessoRelatorioServico(
            LessonReportRepository relatorios, AssignmentRepository associacoes) {
        return new ReportAccessService(relatorios, associacoes);
    }

    @Bean
    TemporaryAudioService audioTemporarioServico(
            ChannelFilePort arquivoCanal,
            AudioNormalizationPort normalizacao,
            TranscriptionPort transcricao) {
        return new TemporaryAudioService(arquivoCanal, normalizacao, transcricao);
    }

    @Bean
    AudioSendingService envioAudioServico(
            BusinessParameters parametros, TemporaryAudioService audio) {
        return new AudioSendingService(parametros, audio);
    }

    @Bean
    DocumentGenerationService geracaoDocumentoServico(
            PdfGenerationPort geracaoPdf,
            DocumentStoragePort armazenamento,
            PdfDocumentRepository documentos,
            LessonReportRepository relatorios,
            StudentRepository alunos,
            TeacherRepository professores,
            ClockPort relogio) {
        return new DocumentGenerationService(
                geracaoPdf, armazenamento, documentos, relatorios, alunos, professores, relogio);
    }

    @Bean
    @SuppressWarnings("java:S107")
    JobProcessorService processadorTrabalhoServico(
            JobQueuePort fila,
            LessonReportRepository relatorios,
            ReportVersionRepository versoes,
            ReportInteractionRepository interacoes,
            TemporaryAudioService audio,
            LlmPort llm,
            DocumentGenerationService documentos,
            TeacherNotificationPort notificacao,
            CostRecordPort custos,
            TransactionPort transacao,
            ClockPort relogio) {
        return new JobProcessorService(
                fila,
                relatorios,
                versoes,
                interacoes,
                audio,
                llm,
                documentos,
                notificacao,
                custos,
                transacao,
                relogio);
    }

    // ---- Registro de aula (US1): casos de uso

    @Bean
    RegisterLessonByAudioUseCase registrarAulaPorAudioCasoDeUso(
            AuthorizationService autorizacao,
            OpenReportsQueryPort emAberto,
            AudioSendingService envioAudio,
            JobQueuePort fila,
            ClockPort relogio) {
        return new RegisterLessonByAudioUseCase(autorizacao, emAberto, envioAudio, fila, relogio);
    }

    @Bean
    AnswerQuestionsUseCase responderPerguntasCasoDeUso(
            ReportAccessService acesso,
            LessonReportRepository relatorios,
            AudioSendingService envioAudio,
            JobQueuePort fila,
            ClockPort relogio) {
        return new AnswerQuestionsUseCase(acesso, relatorios, envioAudio, fila, relogio);
    }

    @Bean
    RequestLessonReportChangeUseCase solicitarAlteracaoRelatorioAulaCasoDeUso(
            ReportAccessService acesso,
            LessonReportRepository relatorios,
            AudioSendingService envioAudio,
            JobQueuePort fila,
            ClockPort relogio) {
        return new RequestLessonReportChangeUseCase(acesso, relatorios, envioAudio, fila, relogio);
    }

    @Bean
    RetryLessonReportUseCase tentarNovamenteRelatorioAulaCasoDeUso(
            ReportAccessService acesso,
            LessonReportRepository relatorios,
            JobQueuePort fila,
            ClockPort relogio) {
        return new RetryLessonReportUseCase(acesso, relatorios, fila, relogio);
    }

    @Bean
    ApproveLessonReportUseCase aprovarRelatorioAulaCasoDeUso(
            ReportAccessService acesso,
            LessonReportRepository relatorios,
            ReportVersionRepository versoes,
            DocumentGenerationService documentos,
            ClockPort relogio) {
        return new ApproveLessonReportUseCase(acesso, relatorios, versoes, documentos, relogio);
    }

    @Bean
    CancelLessonReportUseCase cancelarRelatorioAulaCasoDeUso(
            ReportAccessService acesso,
            LessonReportRepository relatorios,
            JobQueuePort fila,
            ClockPort relogio) {
        return new CancelLessonReportUseCase(acesso, relatorios, fila, relogio);
    }

    @Bean
    @SuppressWarnings("java:S107")
    GetLessonReportUseCase obterRelatorioAulaCasoDeUso(
            ReportAccessService acesso,
            LessonReportRepository relatorios,
            StudentRepository alunos,
            TeacherRepository professores,
            ReportInteractionRepository interacoes,
            ReportVersionRepository versoes,
            PdfDocumentRepository documentos,
            JobQueuePort fila) {
        return new GetLessonReportUseCase(
                acesso, relatorios, alunos, professores, interacoes, versoes, documentos, fila);
    }

    @Bean
    QueryJobUseCase consultarTrabalhoCasoDeUso(JobQueuePort fila, ClockPort relogio) {
        return new QueryJobUseCase(fila, relogio);
    }

    @Bean
    GetReportDocumentUseCase obterDocumentoRelatorioCasoDeUso(
            ReportAccessService acesso,
            LessonReportRepository relatorios,
            ReportVersionRepository versoes,
            PdfDocumentRepository documentos,
            DocumentGenerationService geracao) {
        return new GetReportDocumentUseCase(acesso, relatorios, versoes, documentos, geracao);
    }

    // ---- Retomada entre canais (US3)

    @Bean
    GetOpenReportUseCase obterRelatorioEmAbertoCasoDeUso(
            AuthorizationService autorizacao, OpenReportsQueryPort consulta) {
        return new GetOpenReportUseCase(autorizacao, consulta);
    }

    @Bean
    DiscardRegistrationInProcessingUseCase descartarRegistroEmProcessamentoCasoDeUso(
            JobQueuePort fila, TemporaryAudioService audio, ClockPort relogio) {
        return new DiscardRegistrationInProcessingUseCase(fila, audio, relogio);
    }

    // ---- Relatório semestral (US4)

    @Bean
    SemesterAccessService acessoSemestralServico(
            SemesterReportRepository semestrais, AssignmentRepository associacoes) {
        return new SemesterAccessService(semestrais, associacoes);
    }

    @Bean
    @SuppressWarnings("java:S107")
    SemesterProcessorService processadorSemestralServico(
            SemesterReportRepository semestrais,
            LessonReportRepository aulas,
            StudentRepository alunos,
            TeacherRepository professores,
            ReportVersionRepository versoes,
            ReportInteractionRepository interacoes,
            DocumentGenerationService documentos,
            JobQueuePort fila,
            TransactionPort transacao,
            ClockPort relogio,
            JobProcessorService processador) {
        SemesterProcessorService semestral =
                new SemesterProcessorService(
                        semestrais,
                        aulas,
                        alunos,
                        professores,
                        versoes,
                        interacoes,
                        documentos,
                        fila,
                        transacao,
                        relogio);
        processador.registerComplement(semestral);
        return semestral;
    }

    @Bean
    ListAvailableSemestersUseCase listarSemestresDisponiveisCasoDeUso(
            AuthorizationService autorizacao,
            StudentRepository alunos,
            LessonReportRepository aulas,
            ClockPort relogio) {
        return new ListAvailableSemestersUseCase(autorizacao, alunos, aulas, relogio);
    }

    @Bean
    GetSemesterPreviewUseCase obterPreviaSemestralCasoDeUso(
            AuthorizationService autorizacao,
            StudentRepository alunos,
            LessonReportRepository aulas,
            SemesterReportRepository semestrais,
            JobQueuePort fila) {
        return new GetSemesterPreviewUseCase(autorizacao, alunos, aulas, semestrais, fila);
    }

    @Bean
    GenerateSemesterReportUseCase gerarRelatorioSemestralCasoDeUso(
            GetSemesterPreviewUseCase previa, JobQueuePort fila, ClockPort relogio) {
        return new GenerateSemesterReportUseCase(previa, fila, relogio);
    }

    @Bean
    RequestSemesterReportChangeUseCase solicitarAlteracaoRelatorioSemestralCasoDeUso(
            SemesterAccessService acesso,
            SemesterReportRepository semestrais,
            AudioSendingService envioAudio,
            JobQueuePort fila,
            ClockPort relogio) {
        return new RequestSemesterReportChangeUseCase(
                acesso, semestrais, envioAudio, fila, relogio);
    }

    @Bean
    ApproveSemesterReportUseCase aprovarRelatorioSemestralCasoDeUso(
            SemesterAccessService acesso,
            SemesterReportRepository semestrais,
            ReportVersionRepository versoes,
            DocumentGenerationService documentos,
            ClockPort relogio) {
        return new ApproveSemesterReportUseCase(acesso, semestrais, versoes, documentos, relogio);
    }

    @Bean
    CancelSemesterReportUseCase cancelarRelatorioSemestralCasoDeUso(
            SemesterAccessService acesso,
            SemesterReportRepository semestrais,
            JobQueuePort fila,
            ClockPort relogio) {
        return new CancelSemesterReportUseCase(acesso, semestrais, fila, relogio);
    }

    @Bean
    GetSemesterReportUseCase obterRelatorioSemestralCasoDeUso(
            SemesterAccessService acesso,
            ReportInteractionRepository interacoes,
            ReportVersionRepository versoes,
            PdfDocumentRepository documentos,
            DocumentGenerationService geracao,
            JobQueuePort fila,
            GetReportDocumentUseCase obterDocumento) {
        GetSemesterReportUseCase obter =
                new GetSemesterReportUseCase(
                        acesso, interacoes, versoes, documentos, geracao, fila);
        obterDocumento.registerSemesterSource(obter);
        return obter;
    }

    // ---- Canal Telegram interno (feature 002)

    @Bean
    RetryPolicy politicaRetentativaCanal(PropriedadesAplicacao propriedades) {
        PropriedadesAplicacao.CanalTelegram canal = propriedades.canalTelegram();
        return new RetryPolicy(
                Duration.ofSeconds(canal.retentativaInicialSegundos()),
                Duration.ofSeconds(canal.retentativaTetoSegundos()),
                Duration.ofHours(canal.prazoHoras()),
                () -> ThreadLocalRandom.current().nextDouble(-1.0, 1.0));
    }

    @Bean
    SaveChannelActionsUseCase gravarAcoesCanalCasoDeUso(
            ChannelActionRepository acoes, ClockPort relogio, RetryPolicy politica) {
        return new SaveChannelActionsUseCase(acoes, relogio, politica);
    }

    @Bean
    EmitChannelDocumentUseCase emitirDocumentoCanalCasoDeUso(
            ChannelDocumentRepository documentos, ClockPort relogio, RetryPolicy politica) {
        return new EmitChannelDocumentUseCase(documentos, relogio, politica);
    }

    @Bean
    GetUnconfirmedActionsOfUpdateUseCase obterAcoesNaoConfirmadasDoUpdateCasoDeUso(
            ChannelActionRepository acoes) {
        return new GetUnconfirmedActionsOfUpdateUseCase(acoes);
    }

    @Bean
    ConfirmChannelActionsExecutionUseCase confirmarExecucaoAcoesCanalCasoDeUso(
            ChannelActionRepository acoes,
            JobQueuePort fila,
            ClockPort relogio,
            RetryPolicy politica) {
        return new ConfirmChannelActionsExecutionUseCase(acoes, fila, relogio, politica);
    }

    @Bean
    QueryChannelActionUseCase consultarAcaoCanalCasoDeUso(ChannelActionRepository acoes) {
        return new QueryChannelActionUseCase(acoes);
    }

    @Bean
    GetChannelDocumentUseCase obterDocumentoCanalCasoDeUso(
            ChannelDocumentRepository documentos,
            GetReportDocumentUseCase obterDocumento,
            ClockPort relogio) {
        return new GetChannelDocumentUseCase(documentos, obterDocumento, relogio);
    }

    @Bean
    DeliverPendingActionsUseCase entregarAcoesPendentesCasoDeUso(
            ChannelActionRepository acoes,
            ChannelOrchestratorPort orquestrador,
            JobQueuePort fila,
            TelegramLinkRepository vinculos,
            ClockPort relogio,
            RetryPolicy politica,
            PropriedadesAplicacao propriedades) {
        PropriedadesAplicacao.CanalTelegram canal = propriedades.canalTelegram();
        return new DeliverPendingActionsUseCase(
                acoes,
                orquestrador,
                fila,
                vinculos,
                relogio,
                politica,
                Duration.ofSeconds(canal.confirmacaoMaxSegundos()),
                Duration.ofSeconds(canal.leaseSegundos()));
    }

    @Bean
    CleanChannelActionsUseCase limparAcoesCanalCasoDeUso(
            ChannelActionRepository acoes,
            ChannelDocumentRepository documentos,
            ClockPort relogio,
            PropriedadesAplicacao propriedades) {
        PropriedadesAplicacao.CanalTelegram canal = propriedades.canalTelegram();
        return new CleanChannelActionsUseCase(
                acoes,
                documentos,
                relogio,
                Duration.ofHours(canal.retencaoPayloadHoras()),
                Duration.ofDays(canal.retencaoRegistroDias()));
    }

    // ---- Histórico (US5)

    @Bean
    QueryStudentHistoryUseCase consultarHistoricoAlunoCasoDeUso(
            GetStudentSummaryUseCase resumoAluno,
            LessonReportRepository aulas,
            SemesterReportRepository semestrais) {
        return new QueryStudentHistoryUseCase(resumoAluno, aulas, semestrais);
    }
}
