package br.com.felipeblaft.reports.support;

import br.com.felipeblaft.reports.domain.access.AccessCode;
import br.com.felipeblaft.reports.domain.job.ProcessingJob;
import br.com.felipeblaft.reports.domain.registry.Cpf;
import br.com.felipeblaft.reports.domain.registry.Student;
import br.com.felipeblaft.reports.domain.registry.Teacher;
import br.com.felipeblaft.reports.domain.registry.TeacherStudent;
import br.com.felipeblaft.reports.port.AssignmentRepository;
import br.com.felipeblaft.reports.port.ClockPort;
import br.com.felipeblaft.reports.port.DataProtectionPort;
import br.com.felipeblaft.reports.port.JobQueuePort;
import br.com.felipeblaft.reports.port.StudentRepository;
import br.com.felipeblaft.reports.port.TeacherRepository;
import br.com.felipeblaft.reports.service.JobProcessorService;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** Cria cadastros fictícios com códigos conhecidos para os testes de integração. */
@Component
public class ScenarioIT {

    public static final String CODE_CLARA = "K7QM2XPA";
    public static final String CODE_BRUNO = "BRUN2XPA";

    private final TeacherRepository teachers;
    private final StudentRepository students;
    private final AssignmentRepository assignments;
    private final DataProtectionPort protection;
    private final ClockPort clock;
    private final JobQueuePort queue;
    private final JobProcessorService processor;

    @org.springframework.beans.factory.annotation.Autowired
    private br.com.felipeblaft.reports.port.LessonReportRepository lessonReports;

    @org.springframework.beans.factory.annotation.Autowired
    private br.com.felipeblaft.reports.port.ReportVersionRepository versions;

    @org.springframework.beans.factory.annotation.Autowired
    private br.com.felipeblaft.reports.service.DocumentGenerationService documents;

    @org.springframework.beans.factory.annotation.Autowired
    private br.com.felipeblaft.reports.port.ConsentRepository consents;

    @org.springframework.beans.factory.annotation.Autowired
    private br.com.felipeblaft.reports.port.ConsentTermRepository consentTerms;

    private int cpfSequence;

    /**
     * Tempo que cada trabalho leva, simulado avançando o relógio ajustável durante o processamento;
     * zero (o padrão) mantém o relógio parado. Quem altera restaura o zero ao terminar.
     */
    public volatile Duration timePerJob = Duration.ZERO;

    public ScenarioIT(
            TeacherRepository teachers,
            StudentRepository students,
            AssignmentRepository assignments,
            DataProtectionPort protection,
            ClockPort clock,
            JobQueuePort queue,
            JobProcessorService processor) {
        this.teachers = teachers;
        this.students = students;
        this.assignments = assignments;
        this.protection = protection;
        this.clock = clock;
        this.queue = queue;
        this.processor = processor;
    }

    /**
     * Processa a fila até esvaziar, como o worker faria (os workers ficam desligados nos testes).
     */
    public int processQueue() {
        int processed = 0;
        Optional<ProcessingJob> next;
        while ((next = queue.reserveNext(clock.now())).isPresent()) {
            if (!timePerJob.isZero() && clock instanceof AdjustableClock adjustable) {
                adjustable.advance(timePerJob);
            }
            processor.process(next.get());
            processed++;
        }
        return processed;
    }

    public Teacher teacher(String name, String email, String code) {
        Teacher teacher = Teacher.create(name, email, true, clock.now());
        teacher.replaceCode(
                protection.hashAccessCode(AccessCode.of(code)), clock.now(), Duration.ofDays(7));
        teachers.save(teacher);
        return teacher;
    }

    public Teacher clara() {
        return teacher("Clara Souza", "clara@example.test", CODE_CLARA);
    }

    public Teacher bruno() {
        return teacher("Bruno Lima", "bruno@example.test", CODE_BRUNO);
    }

    /**
     * Aluno menor (2012) já com consentimento válido do responsável, para os testes que não tratam
     * do consentimento (feature 007): sem ele, a IA fica bloqueada. Use {@link
     * #studentWithoutConsent} quando o consentimento for o assunto.
     */
    public Student student(Teacher teacher, String name, String instrument) {
        Student student = studentWithoutConsent(teacher, name, instrument);
        grantConsent(student);
        return student;
    }

    /** Registra um consentimento válido (termo 1, criado se ainda não existe) para o aluno. */
    public void grantConsent(Student student) {
        java.util.List<br.com.felipeblaft.reports.domain.privacy.ConsentTermVersion> all =
                consentTerms.listAll();
        br.com.felipeblaft.reports.domain.privacy.ConsentTermVersion term =
                all.isEmpty()
                        ? new br.com.felipeblaft.reports.domain.privacy.ConsentTermVersion(
                                java.util.UUID.randomUUID(),
                                1,
                                LocalDate.of(2025, 1, 1),
                                "Institutional text",
                                false,
                                java.util.UUID.randomUUID(),
                                clock.now())
                        : all.getFirst();
        if (all.isEmpty()) {
            consentTerms.save(term);
        }
        consents.save(
                new br.com.felipeblaft.reports.domain.privacy.GuardianConsent(
                        java.util.UUID.randomUUID(),
                        student.id(),
                        "Responsável Fictício",
                        br.com.felipeblaft.reports.domain.privacy.ConsentRelationship.MOTHER,
                        LocalDate.of(2025, 1, 2),
                        term.id(),
                        term.number(),
                        br.com.felipeblaft.reports.domain.privacy.ConsentCollectionMethod
                                .SIGNED_PAPER,
                        null,
                        null,
                        br.com.felipeblaft.reports.domain.access.ActionOrigin.INITIAL_LOAD,
                        clock.now(),
                        null,
                        null,
                        null));
    }

    /** Aluno menor sem nenhum consentimento: a IA fica bloqueada para ele. */
    public Student studentWithoutConsent(Teacher teacher, String name, String instrument) {
        Cpf cpf = Cpf.of(cpfValid(++cpfSequence));
        Student student =
                Student.create(
                        name,
                        LocalDate.of(2012, 5, 10),
                        protection.encryptCpf(cpf),
                        protection.hashCpf(cpf),
                        "Responsável Fictício",
                        instrument,
                        clock.now());
        students.save(student);
        assignments.save(TeacherStudent.create(teacher.id(), student.id(), clock.now()));
        return student;
    }

    /** Relatório de aula já aprovado, com aula na data dada (base do semestral e do histórico). */
    public br.com.felipeblaft.reports.domain.report.LessonReport approvedLesson(
            Teacher teacher, Student student, LocalDate data, String content) {
        java.time.Instant now = clock.now();
        br.com.felipeblaft.reports.domain.report.Lesson lesson =
                br.com.felipeblaft.reports.domain.report.Lesson.create(
                        teacher.id(), student.id(), data, now);
        lessonReports.saveLesson(lesson);
        br.com.felipeblaft.reports.domain.report.LessonReport report =
                new br.com.felipeblaft.reports.domain.report.LessonReport(
                        br.com.felipeblaft.reports.domain.common.GeneratorId.create(),
                        lesson.id(),
                        teacher.id(),
                        student.id(),
                        "transcrição fictícia",
                        new br.com.felipeblaft.reports.domain.report.LessonReportContent(
                                java.util.List.of(content),
                                "Evoluiu bem.",
                                java.util.List.of(),
                                java.util.List.of(),
                                ""),
                        java.util.List.of(),
                        0,
                        br.com.felipeblaft.reports.domain.report.ReportStatus.APPROVED,
                        null,
                        1,
                        br.com.felipeblaft.reports.domain.common.Channel.WEB,
                        null,
                        now,
                        now,
                        now,
                        1,
                        br.com.felipeblaft.reports.domain.common.Channel.WEB,
                        null,
                        0,
                        0);
        lessonReports.save(report);
        br.com.felipeblaft.reports.domain.report.ReportVersion version =
                br.com.felipeblaft.reports.domain.report.ReportVersion.create(
                        br.com.felipeblaft.reports.domain.common.ReportType.LESSON,
                        report.id(),
                        1,
                        report.content(),
                        br.com.felipeblaft.reports.domain.report.VersionOrigin.INITIAL_STRUCTURING,
                        null,
                        br.com.felipeblaft.reports.domain.common.Channel.WEB,
                        "lesson-structuring.v1",
                        now);
        versions.save(version);
        documents.generateOfficial(report, version);
        return report;
    }

    /** CPF fictício válido a partir de um número de sequência. */
    static String cpfValid(int n) {
        String base = String.format("%09d", 100_000_000 + n * 7919);
        int d1 = digit(base, 9);
        int d2 = digit(base + d1, 10);
        return base + d1 + d2;
    }

    private static int digit(String digits, int quantity) {
        int sum = 0;
        for (int i = 0; i < quantity; i++) {
            sum += (digits.charAt(i) - '0') * (quantity + 1 - i);
        }
        int rest = (sum * 10) % 11;
        return rest == 10 ? 0 : rest;
    }
}
