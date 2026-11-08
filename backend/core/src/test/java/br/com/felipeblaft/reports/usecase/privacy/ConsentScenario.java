package br.com.felipeblaft.reports.usecase.privacy;

import br.com.felipeblaft.reports.domain.access.Actor;
import br.com.felipeblaft.reports.domain.access.PermissionMatrix;
import br.com.felipeblaft.reports.domain.privacy.ConsentInput;
import br.com.felipeblaft.reports.domain.privacy.ConsentTermVersion;
import br.com.felipeblaft.reports.domain.registry.Student;
import br.com.felipeblaft.reports.domain.registry.Teacher;
import br.com.felipeblaft.reports.doubles.ActorFixtures;
import br.com.felipeblaft.reports.doubles.FakeAuthorshipRecorder;
import br.com.felipeblaft.reports.doubles.FixedClock;
import br.com.felipeblaft.reports.doubles.FixedRequestContext;
import br.com.felipeblaft.reports.doubles.InMemoryAssignmentRepository;
import br.com.felipeblaft.reports.doubles.InMemoryConsentRepository;
import br.com.felipeblaft.reports.doubles.InMemoryConsentTermRepository;
import br.com.felipeblaft.reports.doubles.InMemoryStudentRepository;
import br.com.felipeblaft.reports.doubles.InMemoryTeacherRepository;
import br.com.felipeblaft.reports.service.ActorAuthorizationService;
import br.com.felipeblaft.reports.service.AuthorshipService;
import br.com.felipeblaft.reports.service.ConsentGuardService;
import br.com.felipeblaft.reports.service.ConsentRecordingService;
import br.com.felipeblaft.reports.service.StudentAccessService;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/**
 * Cenário do consentimento sobre dublês em memória. Hoje, no fuso da escola, é 2026-09-21; o aluno
 * "menor" nasceu em 2014 e o "adulto" em 1990.
 */
public final class ConsentScenario {

    public static final LocalDate TODAY = LocalDate.of(2026, 9, 21);

    public final FixedClock clock = new FixedClock();
    public final InMemoryTeacherRepository teachers = new InMemoryTeacherRepository();
    public final InMemoryAssignmentRepository assignments =
            new InMemoryAssignmentRepository(teachers);
    public final InMemoryStudentRepository students = new InMemoryStudentRepository(assignments);
    public final InMemoryConsentRepository consents = new InMemoryConsentRepository();
    public final InMemoryConsentTermRepository terms = new InMemoryConsentTermRepository();
    public final FakeAuthorshipRecorder recorder = new FakeAuthorshipRecorder();
    public final FixedRequestContext context = new FixedRequestContext();

    public final AuthorshipService authorship = new AuthorshipService(recorder, clock);
    public final ActorAuthorizationService authorization =
            new ActorAuthorizationService(PermissionMatrix.standard());
    public final StudentAccessService access = new StudentAccessService(students);
    public final ConsentRecordingService recording =
            new ConsentRecordingService(consents, terms, authorship, clock);
    public final ConsentGuardService guard =
            new ConsentGuardService(students, consents, terms, clock, authorship, context);

    public final RecordConsentUseCase record =
            new RecordConsentUseCase(authorization, access, recording, terms, authorship, guard);
    public final RevokeConsentUseCase revoke =
            new RevokeConsentUseCase(authorization, consents, terms, authorship, clock);
    public final PublishConsentTermUseCase publish =
            new PublishConsentTermUseCase(authorization, terms, authorship, clock);
    public final GetCurrentConsentTermUseCase currentTerm =
            new GetCurrentConsentTermUseCase(authorization, terms);
    public final ListConsentTermsUseCase listTerms =
            new ListConsentTermsUseCase(authorization, terms);
    public final ListStudentConsentsUseCase listConsents =
            new ListStudentConsentsUseCase(authorization, access, consents, terms);
    public final ListPendingConsentsUseCase pending =
            new ListPendingConsentsUseCase(authorization, access, consents, terms, clock);

    private int sequence;

    public Actor administrator() {
        return ActorFixtures.administratorWeb();
    }

    public static String correlation() {
        return UUID.randomUUID().toString();
    }

    public Teacher teacher(String name) {
        Teacher teacher =
                Teacher.create(name, "teacher" + (++sequence) + "@example.test", true, clock.now());
        teachers.save(teacher);
        return teacher;
    }

    /** Publica o termo número {@code number} direto no repositório. */
    public ConsentTermVersion term(int number, boolean requiresNew) {
        ConsentTermVersion term =
                new ConsentTermVersion(
                        UUID.randomUUID(),
                        number,
                        LocalDate.of(2026, 1, 1),
                        "Institutional text " + number,
                        requiresNew,
                        ActorFixtures.ADMINISTRATOR_ID,
                        clock.now());
        terms.save(term);
        return term;
    }

    public Student minor(String name, Teacher... owners) {
        return student(name, LocalDate.of(2014, 5, 10), owners);
    }

    public Student adult(String name, Teacher... owners) {
        return student(name, LocalDate.of(1990, 3, 4), owners);
    }

    private Student student(String name, LocalDate birth, Teacher... owners) {
        Student student =
                Student.create(
                        name,
                        birth,
                        new byte[] {1},
                        "hash-" + (++sequence),
                        null,
                        "Piano",
                        clock.now());
        students.save(student);
        for (Teacher owner : owners) {
            assignments.assign(owner.id(), student.id());
        }
        return student;
    }

    public static ConsentInput input(int termVersion) {
        return new ConsentInput(
                "Maria Guardian", "MOTHER", "2026-09-01", termVersion, "SIGNED_PAPER");
    }

    /** Termo 1 publicado e um consentimento válido já gravado para o aluno. */
    public void consentTo(Student student) {
        if (terms.listAll().isEmpty()) {
            term(1, false);
        }
        recording.record(administrator(), student.id(), input(1), correlation());
        recorder.rollBackTransaction();
    }

    public Optional<ConsentTermVersion> currentTermVersion() {
        return ConsentTermVersion.current(terms.listAll());
    }
}
