package br.com.felipeblaft.reports.usecase.privacy;

import br.com.felipeblaft.reports.domain.access.ActionOrigin;
import br.com.felipeblaft.reports.domain.access.Actor;
import br.com.felipeblaft.reports.domain.access.PermissionMatrix;
import br.com.felipeblaft.reports.domain.access.Role;
import br.com.felipeblaft.reports.domain.common.Channel;
import br.com.felipeblaft.reports.domain.common.Medium;
import br.com.felipeblaft.reports.domain.common.ReportType;
import br.com.felipeblaft.reports.domain.privacy.ConsentCollectionMethod;
import br.com.felipeblaft.reports.domain.privacy.ConsentRelationship;
import br.com.felipeblaft.reports.domain.privacy.ErasureConfirmation;
import br.com.felipeblaft.reports.domain.privacy.ErasureRequest;
import br.com.felipeblaft.reports.domain.privacy.GuardianConsent;
import br.com.felipeblaft.reports.domain.privacy.PrivacyOfficer;
import br.com.felipeblaft.reports.domain.registry.Student;
import br.com.felipeblaft.reports.domain.registry.Teacher;
import br.com.felipeblaft.reports.domain.report.DocumentType;
import br.com.felipeblaft.reports.domain.report.InteractionResult;
import br.com.felipeblaft.reports.domain.report.InteractionType;
import br.com.felipeblaft.reports.domain.report.LessonReport;
import br.com.felipeblaft.reports.domain.report.LessonReportContent;
import br.com.felipeblaft.reports.domain.report.PdfDocument;
import br.com.felipeblaft.reports.domain.report.ReportInteraction;
import br.com.felipeblaft.reports.domain.report.ReportStatus;
import br.com.felipeblaft.reports.domain.semester.GeneralInformation;
import br.com.felipeblaft.reports.domain.semester.SemesterPeriod;
import br.com.felipeblaft.reports.domain.semester.SemesterReport;
import br.com.felipeblaft.reports.domain.semester.SemesterReportContent;
import br.com.felipeblaft.reports.doubles.ActorFixtures;
import br.com.felipeblaft.reports.doubles.FakeAuthorshipRecorder;
import br.com.felipeblaft.reports.doubles.FakeDataProtection;
import br.com.felipeblaft.reports.doubles.FixedClock;
import br.com.felipeblaft.reports.doubles.InMemoryAssignmentRepository;
import br.com.felipeblaft.reports.doubles.InMemoryConsentRepository;
import br.com.felipeblaft.reports.doubles.InMemoryErasureRequestRepository;
import br.com.felipeblaft.reports.doubles.InMemoryStudentRegistrationDraftRepository;
import br.com.felipeblaft.reports.doubles.InMemoryStudentRepository;
import br.com.felipeblaft.reports.doubles.InMemoryTeacherRepository;
import br.com.felipeblaft.reports.doubles.ProcessingDoubles.ImmediateTransaction;
import br.com.felipeblaft.reports.doubles.ProcessingDoubles.InMemoryDocumentStorage;
import br.com.felipeblaft.reports.doubles.ProcessingDoubles.InMemoryJobQueue;
import br.com.felipeblaft.reports.doubles.ProcessingDoubles.InMemoryLessonReportRepository;
import br.com.felipeblaft.reports.doubles.ProcessingDoubles.InMemoryPdfDocumentRepository;
import br.com.felipeblaft.reports.doubles.ProcessingDoubles.InMemoryReportInteractionRepository;
import br.com.felipeblaft.reports.doubles.ProcessingDoubles.InMemoryReportVersionRepository;
import br.com.felipeblaft.reports.doubles.ProcessingDoubles.InMemorySemesterReportRepository;
import br.com.felipeblaft.reports.doubles.RecordingAlertService;
import br.com.felipeblaft.reports.service.ActorAuthorizationService;
import br.com.felipeblaft.reports.service.AuthorshipService;
import br.com.felipeblaft.reports.service.ErasureScopeService;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Cenário da eliminação a pedido sobre dublês em memória. Hoje, no fuso da escola, é 2026-09-21; os
 * textos usados nos testes são em inglês (o guardião de idioma varre os literais).
 */
public final class ErasureScenario {

    public static final PrivacyOfficer OFFICER =
            new PrivacyOfficer("Privacy Officer", "dpo@school.test");

    public final FixedClock clock = new FixedClock();
    public final InMemoryTeacherRepository teachers = new InMemoryTeacherRepository();
    public final InMemoryAssignmentRepository assignments =
            new InMemoryAssignmentRepository(teachers);
    public final InMemoryStudentRepository students = new InMemoryStudentRepository(assignments);
    public final InMemoryStudentRegistrationDraftRepository drafts =
            new InMemoryStudentRegistrationDraftRepository();
    public final InMemoryConsentRepository consents = new InMemoryConsentRepository();
    public final InMemoryErasureRequestRepository erasures = new InMemoryErasureRequestRepository();
    public final InMemoryLessonReportRepository lessons = new InMemoryLessonReportRepository();
    public final InMemorySemesterReportRepository semesters =
            new InMemorySemesterReportRepository();
    public final InMemoryReportVersionRepository versions = new InMemoryReportVersionRepository();
    public final InMemoryReportInteractionRepository interactions =
            new InMemoryReportInteractionRepository();
    public final InMemoryPdfDocumentRepository pdfs = new InMemoryPdfDocumentRepository();
    public final InMemoryDocumentStorage storage = new InMemoryDocumentStorage();
    public final InMemoryJobQueue queue = new InMemoryJobQueue();
    public final ImmediateTransaction transaction = new ImmediateTransaction();
    public final RecordingAlertService alerts = new RecordingAlertService();
    public final FakeAuthorshipRecorder recorder = new FakeAuthorshipRecorder();
    public final FakeDataProtection protection = new FakeDataProtection();

    public final AuthorshipService authorship = new AuthorshipService(recorder, clock);
    public final ActorAuthorizationService authorization =
            new ActorAuthorizationService(PermissionMatrix.standard());
    public final ErasureScopeService scopes =
            new ErasureScopeService(lessons, semesters, pdfs, assignments, consents);

    public final RegisterErasureRequestUseCase register =
            new RegisterErasureRequestUseCase(authorization, students, erasures, authorship, clock);
    public final PreviewErasureUseCase preview =
            new PreviewErasureUseCase(
                    authorization,
                    erasures,
                    scopes,
                    assignments,
                    teachers,
                    protection,
                    OFFICER,
                    authorship,
                    transaction,
                    clock);
    public final ConfirmErasureUseCase confirm =
            new ConfirmErasureUseCase(
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
    public final GetErasureReceiptUseCase receipt =
            new GetErasureReceiptUseCase(authorization, erasures, OFFICER, authorship);
    public final ListErasureRequestsUseCase list =
            new ListErasureRequestsUseCase(authorization, erasures, students);

    private int sequence;

    public Actor administrator() {
        return ActorFixtures.administratorWeb();
    }

    public Actor retention() {
        return Actor.retention();
    }

    public Teacher teacher(String name) {
        Teacher teacher =
                Teacher.create(name, "teacher" + (++sequence) + "@example.test", true, clock.now());
        teachers.save(teacher);
        return teacher;
    }

    public Student student(String name, Teacher... owners) {
        Student student =
                Student.create(
                        name,
                        LocalDate.of(2014, 5, 10),
                        new byte[] {1},
                        "hash-" + (++sequence),
                        "Guardian Name",
                        "Piano",
                        clock.now());
        students.save(student);
        for (Teacher owner : owners) {
            assignments.assign(owner.id(), student.id());
        }
        return student;
    }

    public ErasureRequest requestFor(Student student) {
        return register.run(
                administrator(),
                new RegisterErasureRequestUseCase.Command(
                        student.id(),
                        "MOTHER",
                        "Maria Requester",
                        LocalDate.of(2026, 9, 15),
                        "IN_PERSON",
                        "PROTO-2026/15",
                        "req-register"));
    }

    /** Registra, faz a prévia e confirma: devolve o pedido depois da confirmação. */
    public ErasureRequest erase(Student student) {
        ErasureRequest request = requestFor(student);
        return confirmWithNewPreview(request.id());
    }

    public ErasureRequest confirmWithNewPreview(UUID requestId) {
        String token = preview.run(administrator(), requestId, "req-preview").confirmationToken();
        return confirm.run(
                administrator(),
                new ConfirmErasureUseCase.Command(
                        requestId, token, ErasureConfirmation.CONFIRMATION_WORD, "req-confirm"));
    }

    // ------------------------------------------------------------ massa de relatórios

    public LessonReport lesson(
            Student student, Teacher teacher, ReportStatus status, Instant approvedAt) {
        Instant created = clock.now().minusSeconds(86_400L * 40);
        LessonReport report =
                new LessonReport(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        teacher.id(),
                        student.id(),
                        "source-text",
                        LessonReportContent.empty(),
                        List.of(),
                        0,
                        status,
                        null,
                        2,
                        Channel.WEB,
                        null,
                        created,
                        created,
                        approvedAt,
                        approvedAt == null ? null : 2,
                        approvedAt == null ? null : Channel.WEB,
                        status == ReportStatus.CANCELLED ? created : null,
                        0,
                        0);
        lessons.save(report);
        interactions.save(interaction(ReportType.LESSON, report.id()));
        return report;
    }

    public SemesterReport semester(Student student, Teacher teacher, Instant approvedAt) {
        GeneralInformation info =
                new GeneralInformation(
                        "Name",
                        10,
                        "Piano",
                        "Teacher",
                        "2025-2",
                        LocalDate.of(2025, 7, 1),
                        LocalDate.of(2025, 12, 31),
                        3);
        Instant created = clock.now().minusSeconds(86_400L * 40);
        SemesterReport report =
                new SemesterReport(
                        UUID.randomUUID(),
                        teacher.id(),
                        student.id(),
                        new SemesterPeriod(2025, 2),
                        3,
                        0,
                        info,
                        SemesterReportContent.empty(),
                        ReportStatus.APPROVED,
                        null,
                        1,
                        Channel.WEB,
                        created,
                        created,
                        approvedAt,
                        1,
                        Channel.WEB,
                        null,
                        0,
                        0);
        semesters.save(report);
        interactions.save(interaction(ReportType.SEMESTER, report.id()));
        return report;
    }

    public PdfDocument pdf(ReportType type, UUID reportId) {
        PdfDocument document =
                PdfDocument.create(
                        UUID.randomUUID(),
                        DocumentType.OFFICIAL,
                        "key-" + UUID.randomUUID(),
                        3,
                        "h",
                        clock.now().minusSeconds(86_400L * 10));
        storage.save(document.storageKey(), new byte[] {1, 2, 3});
        pdfs.link(document, type, reportId, true, clock.now());
        return document;
    }

    public GuardianConsent consent(Student student) {
        GuardianConsent consent =
                new GuardianConsent(
                        UUID.randomUUID(),
                        student.id(),
                        "Maria Guardian",
                        ConsentRelationship.MOTHER,
                        LocalDate.of(2026, 9, 1),
                        UUID.randomUUID(),
                        1,
                        ConsentCollectionMethod.SIGNED_PAPER,
                        ActorFixtures.ADMINISTRATOR_ID,
                        Role.ADMINISTRATOR,
                        ActionOrigin.WEB,
                        clock.now(),
                        null,
                        null,
                        null);
        consents.save(consent);
        return consent;
    }

    private ReportInteraction interaction(ReportType type, UUID reportId) {
        return ReportInteraction.create(
                type,
                reportId,
                InteractionType.QUESTION_ANSWER,
                Channel.WEB,
                Medium.TEXT,
                "typed-text",
                1,
                null,
                InteractionResult.APPLIED,
                2,
                clock.now());
    }
}
