package br.com.felipeblaft.reports.doubles;

import br.com.felipeblaft.reports.domain.common.Channel;
import br.com.felipeblaft.reports.domain.job.ProcessingJob;
import br.com.felipeblaft.reports.domain.registry.Student;
import br.com.felipeblaft.reports.domain.registry.Teacher;
import br.com.felipeblaft.reports.domain.report.LessonReport;
import br.com.felipeblaft.reports.doubles.ProcessingDoubles.FakeAudioNormalization;
import br.com.felipeblaft.reports.doubles.ProcessingDoubles.FakeChannelFile;
import br.com.felipeblaft.reports.doubles.ProcessingDoubles.FakePdfGeneration;
import br.com.felipeblaft.reports.doubles.ProcessingDoubles.ImmediateTransaction;
import br.com.felipeblaft.reports.doubles.ProcessingDoubles.InMemoryCostRecord;
import br.com.felipeblaft.reports.doubles.ProcessingDoubles.InMemoryDocumentStorage;
import br.com.felipeblaft.reports.doubles.ProcessingDoubles.InMemoryJobQueue;
import br.com.felipeblaft.reports.doubles.ProcessingDoubles.InMemoryLessonReportRepository;
import br.com.felipeblaft.reports.doubles.ProcessingDoubles.InMemoryPdfDocumentRepository;
import br.com.felipeblaft.reports.doubles.ProcessingDoubles.InMemoryReportInteractionRepository;
import br.com.felipeblaft.reports.doubles.ProcessingDoubles.InMemoryReportVersionRepository;
import br.com.felipeblaft.reports.doubles.ProcessingDoubles.RecordingNotification;
import br.com.felipeblaft.reports.doubles.ProcessingDoubles.ScriptedLlm;
import br.com.felipeblaft.reports.doubles.ProcessingDoubles.ScriptedTranscription;
import br.com.felipeblaft.reports.port.AiLessonReportOutput;
import br.com.felipeblaft.reports.port.TaskLlm;
import br.com.felipeblaft.reports.port.TemporaryFile;
import br.com.felipeblaft.reports.service.AudioSendingService;
import br.com.felipeblaft.reports.service.DocumentGenerationService;
import br.com.felipeblaft.reports.service.JobProcessorService;
import br.com.felipeblaft.reports.service.ReportAccessService;
import br.com.felipeblaft.reports.service.TemporaryAudioService;
import br.com.felipeblaft.reports.usecase.document.GetReportDocumentUseCase;
import br.com.felipeblaft.reports.usecase.job.QueryJobUseCase;
import br.com.felipeblaft.reports.usecase.lesson.AnswerQuestionsUseCase;
import br.com.felipeblaft.reports.usecase.lesson.ApproveLessonReportUseCase;
import br.com.felipeblaft.reports.usecase.lesson.CancelLessonReportUseCase;
import br.com.felipeblaft.reports.usecase.lesson.GetLessonReportUseCase;
import br.com.felipeblaft.reports.usecase.lesson.RegisterLessonByAudioUseCase;
import br.com.felipeblaft.reports.usecase.lesson.RequestLessonReportChangeUseCase;
import br.com.felipeblaft.reports.usecase.lesson.RetryLessonReportUseCase;
import java.nio.file.Path;
import java.util.List;

/** Cenário completo de relatório de aula com todos os dublês, serviços e casos de uso. */
public class ReportScenario extends AccessScenario {

    public final ScriptedTranscription transcription = new ScriptedTranscription();
    public final FakeAudioNormalization normalization = new FakeAudioNormalization();
    public final FakeChannelFile channelFile = new FakeChannelFile();
    public final ScriptedLlm llm = new ScriptedLlm();
    public final FakePdfGeneration pdf = new FakePdfGeneration();
    public final InMemoryDocumentStorage storage = new InMemoryDocumentStorage();
    public final RecordingNotification notification = new RecordingNotification();
    public final InMemoryCostRecord costs = new InMemoryCostRecord();
    public final ImmediateTransaction transaction = new ImmediateTransaction();
    public final InMemoryJobQueue queue = new InMemoryJobQueue();
    public final InMemoryLessonReportRepository reports = new InMemoryLessonReportRepository();
    public final InMemoryReportVersionRepository versions = new InMemoryReportVersionRepository();
    public final InMemoryReportInteractionRepository interactions =
            new InMemoryReportInteractionRepository();
    public final InMemoryPdfDocumentRepository pdfDocuments = new InMemoryPdfDocumentRepository();

    public final TemporaryAudioService audio =
            new TemporaryAudioService(channelFile, normalization, transcription);
    public final AudioSendingService audioSending = new AudioSendingService(parameters, audio);
    public final ReportAccessService access = new ReportAccessService(reports, assignments);
    public final DocumentGenerationService documents =
            new DocumentGenerationService(
                    pdf, storage, pdfDocuments, reports, students, teachers, clock);
    public final JobProcessorService processor =
            new JobProcessorService(
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

    public final RegisterLessonByAudioUseCase register =
            new RegisterLessonByAudioUseCase(authorization, open, audioSending, queue, clock);
    public final AnswerQuestionsUseCase answer =
            new AnswerQuestionsUseCase(access, reports, audioSending, queue, clock);
    public final RequestLessonReportChangeUseCase change =
            new RequestLessonReportChangeUseCase(access, reports, audioSending, queue, clock);
    public final RetryLessonReportUseCase retry =
            new RetryLessonReportUseCase(access, reports, queue, clock);
    public final ApproveLessonReportUseCase approve =
            new ApproveLessonReportUseCase(access, reports, versions, documents, clock);
    public final CancelLessonReportUseCase cancel =
            new CancelLessonReportUseCase(access, reports, queue, clock);
    public final GetLessonReportUseCase get =
            new GetLessonReportUseCase(
                    access,
                    reports,
                    students,
                    teachers,
                    interactions,
                    versions,
                    pdfDocuments,
                    queue);
    public final QueryJobUseCase queryJob = new QueryJobUseCase(queue, clock);
    public final br.com.felipeblaft.reports.usecase.lesson.GetOpenReportUseCase getOpen =
            new br.com.felipeblaft.reports.usecase.lesson.GetOpenReportUseCase(authorization, open);
    public final br.com.felipeblaft.reports.usecase.job.DiscardRegistrationInProcessingUseCase
            discardRegistration =
                    new br.com.felipeblaft.reports.usecase.job
                            .DiscardRegistrationInProcessingUseCase(queue, audio, clock);
    public final GetReportDocumentUseCase getDocument =
            new GetReportDocumentUseCase(access, reports, versions, pdfDocuments, documents);

    // ---- Semestral (US4)
    public final ProcessingDoubles.InMemorySemesterReportRepository semester =
            new ProcessingDoubles.InMemorySemesterReportRepository();
    public final br.com.felipeblaft.reports.service.SemesterAccessService semesterAccess =
            new br.com.felipeblaft.reports.service.SemesterAccessService(semester, assignments);
    public final br.com.felipeblaft.reports.service.SemesterProcessorService semesterProcessor =
            new br.com.felipeblaft.reports.service.SemesterProcessorService(
                    semester,
                    reports,
                    students,
                    teachers,
                    versions,
                    interactions,
                    documents,
                    queue,
                    transaction,
                    clock);
    public final br.com.felipeblaft.reports.usecase.semester.ListAvailableSemestersUseCase
            listSemesters =
                    new br.com.felipeblaft.reports.usecase.semester.ListAvailableSemestersUseCase(
                            authorization, students, reports, clock);
    public final br.com.felipeblaft.reports.usecase.semester.GetSemesterPreviewUseCase preview =
            new br.com.felipeblaft.reports.usecase.semester.GetSemesterPreviewUseCase(
                    authorization, students, reports, semester, queue);
    public final br.com.felipeblaft.reports.usecase.semester.GenerateSemesterReportUseCase
            generateSemester =
                    new br.com.felipeblaft.reports.usecase.semester.GenerateSemesterReportUseCase(
                            preview, queue, clock);
    public final br.com.felipeblaft.reports.usecase.semester.RequestSemesterReportChangeUseCase
            changeSemester =
                    new br.com.felipeblaft.reports.usecase.semester
                            .RequestSemesterReportChangeUseCase(
                            semesterAccess, semester, audioSending, queue, clock);
    public final br.com.felipeblaft.reports.usecase.semester.ApproveSemesterReportUseCase
            approveSemester =
                    new br.com.felipeblaft.reports.usecase.semester.ApproveSemesterReportUseCase(
                            semesterAccess, semester, versions, documents, clock);
    public final br.com.felipeblaft.reports.usecase.semester.CancelSemesterReportUseCase
            cancelSemester =
                    new br.com.felipeblaft.reports.usecase.semester.CancelSemesterReportUseCase(
                            semesterAccess, semester, queue, clock);
    public final br.com.felipeblaft.reports.usecase.semester.GetSemesterReportUseCase getSemester =
            new br.com.felipeblaft.reports.usecase.semester.GetSemesterReportUseCase(
                    semesterAccess, interactions, versions, pdfDocuments, documents, queue);

    {
        processor.registerComplement(semesterProcessor);
        getDocument.registerSemesterSource(getSemester);
    }

    public final Teacher clara = teacher("Clara Souza", "clara@exemplo.test", "K7QM2XPA");
    public final Teacher bruno = teacher("Bruno Lima", "bruno@exemplo.test", "BRUN2XPA");
    public final Student matteo = student(clara, "Matteo Mafra", "Piano");
    public final Student helena = student(bruno, "Helena Prado", "Flauta");

    public static TemporaryFile file(String name, long size) {
        return new TemporaryFile(Path.of("/tmp/audios/" + name), size, "audio/ogg");
    }

    public static AiLessonReportOutput output(String progress, List<String> questions) {
        return new AiLessonReportOutput(
                List.of("Estudo nº 5 de Czerny"),
                progress,
                List.of("Independência entre as mãos"),
                List.of("Escala de Sol maior com metrônomo"),
                "",
                questions,
                false,
                "NENHUM");
    }

    public static AiLessonReportOutput revision(
            String progress, boolean withoutChange, String actionRequest) {
        return new AiLessonReportOutput(
                List.of("Estudo nº 5 de Czerny"),
                progress,
                List.of("Independência entre as mãos"),
                List.of("Escala de Sol maior com metrônomo"),
                "",
                List.of("pergunta ignorada na revisão"),
                withoutChange,
                actionRequest);
    }

    /** Processa todos os trabalhos pendentes, na ordem da fila. */
    public void processQueue() {
        while (true) {
            var next = queue.reserveNext(clock.now());
            if (next.isEmpty()) {
                return;
            }
            processor.process(next.get());
        }
    }

    /** Registra uma aula e processa até a conferência na versão 1 (sem perguntas). */
    public LessonReport reportInReview() {
        llm.schedule(TaskLlm.ESTRUTURACAO_AULA, output("Evoluiu na articulação.", List.of()));
        ProcessingJob job =
                register.run(clara.id(), matteo.id(), file("aula.ogg", 1000), Channel.WEB);
        processQueue();
        return reports.searchById(queue.searchById(job.id()).orElseThrow().reportId())
                .orElseThrow();
    }

    /** Registra uma aula que fica aguardando respostas (rodada 1). */
    public LessonReport reportWithQuestions() {
        llm.schedule(
                TaskLlm.ESTRUTURACAO_AULA,
                output("Evoluiu.", List.of("Qual repertório foi trabalhado?", "Estudou em casa?")));
        ProcessingJob job =
                register.run(clara.id(), matteo.id(), file("aula.ogg", 1000), Channel.TELEGRAM);
        processQueue();
        return reports.searchById(queue.searchById(job.id()).orElseThrow().reportId())
                .orElseThrow();
    }

    /** Outro aluno de Clara com relatório aguardando respostas. */
    public LessonReport reportWithQuestionsOfOtherStudent() {
        Student olavo = student(clara, "Olavo Torrente", "Violão");
        llm.schedule(TaskLlm.ESTRUTURACAO_AULA, output("x", List.of("Qual peça?")));
        ProcessingJob job =
                register.run(clara.id(), olavo.id(), file("olavo.ogg", 10), Channel.WEB);
        processQueue();
        return reports.searchById(queue.searchById(job.id()).orElseThrow().reportId())
                .orElseThrow();
    }

    /** Relatório de aula já aprovado do aluno, com aula na data dada (para o semestral). */
    public LessonReport approvedLesson(Student student, java.time.LocalDate data, String content) {
        java.time.Instant now = clock.now();
        br.com.felipeblaft.reports.domain.report.Lesson lesson =
                br.com.felipeblaft.reports.domain.report.Lesson.create(
                        teacherOf(student), student.id(), data, now);
        reports.saveLesson(lesson);
        LessonReport report =
                new LessonReport(
                        br.com.felipeblaft.reports.domain.common.GeneratorId.create(),
                        lesson.id(),
                        lesson.teacherId(),
                        student.id(),
                        "transcrição de " + data,
                        new br.com.felipeblaft.reports.domain.report.LessonReportContent(
                                List.of(content), "Evoluiu em " + data, List.of(), List.of(), ""),
                        List.of(),
                        0,
                        br.com.felipeblaft.reports.domain.report.ReportStatus.APROVADO,
                        null,
                        1,
                        Channel.WEB,
                        null,
                        now,
                        now,
                        now,
                        1,
                        Channel.WEB,
                        null,
                        0,
                        0);
        reports.save(report);
        return report;
    }

    private java.util.UUID teacherOf(Student student) {
        return student.id().equals(helena.id()) ? bruno.id() : clara.id();
    }

    public static br.com.felipeblaft.reports.port.AiSemesterReportOutput semesterOutput(
            String opinion) {
        return br.com.felipeblaft.reports.port.AiSemesterReportOutput.of(
                new br.com.felipeblaft.reports.domain.semester.SemesterReportContent(
                        new br.com.felipeblaft.reports.domain.semester.SemesterReportContent
                                .AttendanceAndStudy("Frequentou as aulas", "Regular", "Alto", null),
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        opinion),
                false,
                "NENHUM");
    }
}
