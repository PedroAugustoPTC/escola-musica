package br.com.felipeblaft.reports.inbound.seed;

import br.com.felipeblaft.reports.config.MessageFormatting;
import br.com.felipeblaft.reports.domain.common.Channel;
import br.com.felipeblaft.reports.domain.common.ReportType;
import br.com.felipeblaft.reports.domain.registry.Student;
import br.com.felipeblaft.reports.domain.registry.Teacher;
import br.com.felipeblaft.reports.domain.report.Lesson;
import br.com.felipeblaft.reports.domain.report.LessonReport;
import br.com.felipeblaft.reports.domain.report.ReportVersion;
import br.com.felipeblaft.reports.domain.report.VersionOrigin;
import br.com.felipeblaft.reports.domain.semester.GeneralInformation;
import br.com.felipeblaft.reports.domain.semester.SemesterPeriod;
import br.com.felipeblaft.reports.domain.semester.SemesterReport;
import br.com.felipeblaft.reports.port.AiSemesterReportOutput;
import br.com.felipeblaft.reports.port.ClockPort;
import br.com.felipeblaft.reports.port.LessonReportRepository;
import br.com.felipeblaft.reports.port.ReportVersionRepository;
import br.com.felipeblaft.reports.port.SemesterReportRepository;
import br.com.felipeblaft.reports.port.StudentRepository;
import br.com.felipeblaft.reports.port.TeacherRepository;
import br.com.felipeblaft.reports.port.TransactionPort;
import br.com.felipeblaft.reports.service.DocumentGenerationService;
import br.com.felipeblaft.reports.usecase.seed.LoadInitialRegistryUseCase;
import br.com.felipeblaft.reports.usecase.seed.SeedFile;
import br.com.felipeblaft.reports.usecase.seed.SeedResult;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * Comando {@code massa --arquivo <cadastros.json>}: massa de DEMONSTRAÇÃO para o ambiente local
 * ({@code local.sh subir --massa=true}). Faz a carga dos cadastros fictícios e cria, pelos mesmos
 * objetos de domínio e pelo mesmo gerador de PDF do sistema, aulas aprovadas no semestre anterior e
 * no atual (PDFs de conferência e oficiais), um semestral aprovado, um semestral em conferência e
 * relatórios de aula em aberto. Só roda com o banco vazio, para nunca misturar dados fictícios com
 * dados reais.
 */
@Component
@Profile("seed")
public class DemoDataCommand {

    public static final String NAME = "demo-data";

    private static final String TRANSCRIPTION = "Transcrição de demonstração (massa fictícia).";

    /** Plano por aluno: aulas aprovadas no semestre anterior e no atual, e o dia da semana. */
    private record Plano(int previous, int current, int offset) {}

    private static final Map<String, Plano> PLANOS =
            Map.of(
                    "Matteo Mafra", new Plano(12, 10, 0),
                    "Olavo Torrente", new Plano(8, 8, 1),
                    "Miguel de Castro Torrente", new Plano(0, 6, 2),
                    "Beatriz Arruda", new Plano(0, 4, 3),
                    "Helena Prado", new Plano(4, 3, 1),
                    "Davi Coutinho", new Plano(0, 2, 4));

    private final LoadInitialRegistryUseCase seed;
    private final TeacherRepository teachers;
    private final StudentRepository students;
    private final LessonReportRepository lessonReports;
    private final ReportVersionRepository versions;
    private final SemesterReportRepository semester;
    private final DocumentGenerationService documents;
    private final TransactionPort transaction;
    private final ClockPort clock;
    private final MessageFormatting formatting;
    private final JdbcTemplate jdbc;
    private final JsonMapper json =
            JsonMapper.builder().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();

    @SuppressWarnings("java:S107")
    public DemoDataCommand(
            LoadInitialRegistryUseCase seed,
            TeacherRepository teachers,
            StudentRepository students,
            LessonReportRepository lessonReports,
            ReportVersionRepository versions,
            SemesterReportRepository semester,
            DocumentGenerationService documents,
            TransactionPort transaction,
            ClockPort clock,
            MessageFormatting formatting,
            JdbcTemplate jdbc) {
        this.seed = seed;
        this.teachers = teachers;
        this.students = students;
        this.lessonReports = lessonReports;
        this.versions = versions;
        this.semester = semester;
        this.documents = documents;
        this.transaction = transaction;
        this.clock = clock;
        this.formatting = formatting;
        this.jdbc = jdbc;
    }

    /** Devolve o código de saída do processo (0 = sucesso). */
    public int run(List<String> arguments, PrintStream output) {
        String file = CommandArguments.value(arguments, "--file");
        if (file == null) {
            output.println("Uso: demo-data --file <cadastros.json>");
            return 2;
        }
        Integer existing = jdbc.queryForObject("select count(*) from teacher", Integer.class);
        if (existing != null && existing > 0) {
            output.println(
                    "A massa de demonstração só é criada em banco vazio (há professores cadastrados)."
                            + " Use: local.sh subir --massa=true, que zera os dados locais antes.");
            return 1;
        }
        SeedFile registries;
        try {
            registries = json.readValue(Files.readString(Path.of(file)), SeedFile.class);
        } catch (Exception error) {
            output.println(
                    "Não foi possível ler o arquivo de cadastros (JSON inválido ou ausente).");
            return 1;
        }
        SeedResult result = seed.run(registries, false);
        if (!result.success()) {
            output.println("Carga dos cadastros recusada: " + result.errors());
            return 1;
        }
        Summary summary = generate(registries);
        output.println("Massa de demonstração criada (dados fictícios).");
        output.printf(
                "Professores: %d · Alunos: %d%n",
                result.teachers().created(), result.students().created());
        output.printf(
                "Relatórios de aula: %d aprovados, %d em aberto · Semestrais: %d aprovado(s), %d em"
                        + " conferência · PDFs gerados: %d%n",
                summary.approvedLessons,
                summary.openLessons,
                summary.approvedSemesters,
                summary.semestersInReview,
                summary.pdfs);
        output.println("Códigos de acesso (repasse fora do sistema):");
        for (SeedResult.IssuedCode code : result.issuedCodes()) {
            output.println(CommandArguments.codeLine(code, formatting));
        }
        return 0;
    }

    /** Contadores do que foi criado. */
    static final class Summary {
        int approvedLessons;
        int openLessons;
        int approvedSemesters;
        int semestersInReview;
        int pdfs;
    }

    Summary generate(SeedFile registries) {
        Summary summary = new Summary();
        LocalDate today = clock.todayInSchoolTimezone();
        SemesterPeriod current = SemesterPeriod.containing(today);
        SemesterPeriod previous = current.previous();
        for (SeedFile.SeedTeacher teacherData : registries.teachers()) {
            Teacher teacher =
                    teachers.searchByEmail(teacherData.email().strip().toLowerCase()).orElseThrow();
            List<Student> ofTeacher =
                    students.listByTeacher(teacher.id()).stream()
                            .sorted(Comparator.comparing(Student::name))
                            .toList();
            for (Student student : ofTeacher) {
                Plano plano = PLANOS.get(student.name());
                if (plano == null) {
                    continue; // sem histórico: serve para testar as telas vazias
                }
                List<LocalDate> previousDates =
                        weeks(previous.start(), previous.end(), plano.offset(), plano.previous());
                List<LocalDate> currentDates =
                        weeks(current.start(), today.minusDays(7), plano.offset(), plano.current());
                int index = 0;
                for (LocalDate data : concatenate(previousDates, currentDates)) {
                    approvedLesson(teacher, student, data, index++);
                    summary.approvedLessons++;
                    summary.pdfs += 2;
                }
                switch (student.name()) {
                    case "Matteo Mafra" -> {
                        semester(teacher, student, previous, previousDates.size(), true);
                        summary.approvedSemesters++;
                        summary.pdfs += 2;
                    }
                    case "Olavo Torrente" -> {
                        semester(teacher, student, previous, previousDates.size(), false);
                        summary.semestersInReview++;
                        summary.pdfs++;
                        openLesson(teacher, student, today.minusDays(1), index, false);
                        summary.openLessons++;
                        summary.pdfs++;
                    }
                    case "Miguel de Castro Torrente" -> {
                        openLesson(teacher, student, today, index, true);
                        summary.openLessons++;
                    }
                    default -> {
                        // só aulas aprovadas
                    }
                }
            }
        }
        return summary;
    }

    private static List<LocalDate> concatenate(List<LocalDate> a, List<LocalDate> b) {
        return java.util.stream.Stream.concat(a.stream(), b.stream()).toList();
    }

    /** Uma aula por semana a partir da 2ª semana do período, até o limite e a quantidade. */
    private static List<LocalDate> weeks(
            LocalDate start, LocalDate limit, int offset, int quantity) {
        List<LocalDate> dates = new java.util.ArrayList<>();
        LocalDate data = start.plusDays(7L + offset);
        while (dates.size() < quantity && !data.isAfter(limit)) {
            dates.add(data);
            data = data.plusWeeks(1);
        }
        return dates;
    }

    private Instant as(LocalDate data, int hora, int minute) {
        return data.atTime(hora, minute).atZone(clock.schoolTimezone()).toInstant();
    }

    private void approvedLesson(Teacher teacher, Student student, LocalDate data, int index) {
        transaction.run(
                () -> {
                    Channel channel = index % 3 == 2 ? Channel.TELEGRAM : Channel.WEB;
                    Instant registered = as(data, 18, 10);
                    Lesson lesson = Lesson.create(teacher.id(), student.id(), data, registered);
                    lessonReports.saveLesson(lesson);
                    LessonReport report =
                            LessonReport.create(lesson, TRANSCRIPTION, channel, registered);
                    report.registerStructuring(
                            DemoContents.lesson(student.instrument(), index),
                            List.of(),
                            as(data, 18, 12));
                    report.approve(1, channel, as(data, 18, 40));
                    lessonReports.save(report);
                    ReportVersion version = lessonVersion(report, channel, registered);
                    documents.generateReview(report, version);
                    documents.generateOfficial(report, version);
                });
    }

    private void openLesson(
            Teacher teacher, Student student, LocalDate data, int index, boolean withQuestions) {
        transaction.run(
                () -> {
                    Instant registered = as(data, 17, 5);
                    Lesson lesson = Lesson.create(teacher.id(), student.id(), data, registered);
                    lessonReports.saveLesson(lesson);
                    LessonReport report =
                            LessonReport.create(
                                    lesson, TRANSCRIPTION, Channel.TELEGRAM, registered);
                    boolean createVersion =
                            report.registerStructuring(
                                    DemoContents.lesson(student.instrument(), index),
                                    withQuestions ? DemoContents.QUESTIONS : List.of(),
                                    as(data, 17, 7));
                    lessonReports.save(report);
                    if (createVersion) {
                        documents.generateReview(
                                report, lessonVersion(report, Channel.TELEGRAM, registered));
                    }
                });
    }

    private ReportVersion lessonVersion(LessonReport report, Channel channel, Instant when) {
        ReportVersion version =
                ReportVersion.create(
                        ReportType.LESSON,
                        report.id(),
                        1,
                        report.content(),
                        VersionOrigin.INITIAL_STRUCTURING,
                        null,
                        channel,
                        "lesson-structuring.v1",
                        when);
        versions.save(version);
        return version;
    }

    private void semester(
            Teacher teacher,
            Student student,
            SemesterPeriod period,
            int lessonCount,
            boolean approve) {
        if (lessonCount == 0) {
            return;
        }
        transaction.run(
                () -> {
                    LocalDate generatedAt = period.end().plusDays(3);
                    Instant consolidated = as(generatedAt, 10, 0);
                    GeneralInformation information =
                            GeneralInformation.calculate(
                                    student.name(),
                                    student.birthDate(),
                                    student.instrument(),
                                    teacher.name(),
                                    period,
                                    lessonCount,
                                    generatedAt);
                    SemesterReport report =
                            SemesterReport.create(
                                    teacher.id(),
                                    student.id(),
                                    period,
                                    lessonCount,
                                    0,
                                    information,
                                    semesterContent().content(),
                                    Channel.WEB,
                                    consolidated);
                    if (approve) {
                        report.approve(1, Channel.WEB, as(generatedAt, 10, 30));
                    }
                    semester.save(report);
                    ReportVersion version =
                            ReportVersion.create(
                                    ReportType.SEMESTER,
                                    report.id(),
                                    1,
                                    report.content(),
                                    VersionOrigin.CONSOLIDATION,
                                    null,
                                    Channel.WEB,
                                    "semester-consolidation.v1",
                                    consolidated);
                    versions.save(version);
                    documents.generateReview(report, version);
                    if (approve) {
                        documents.generateOfficial(report, version);
                    }
                });
    }

    /** O mesmo conteúdo semestral da IA simulada (ia/simulado/consolidacao-semestral.json). */
    private AiSemesterReportOutput semesterContent() {
        try (InputStream input =
                DemoDataCommand.class
                        .getClassLoader()
                        .getResourceAsStream("ai/simulated/semester-consolidation.json")) {
            return json.readValue(input, AiSemesterReportOutput.class);
        } catch (java.io.IOException error) {
            throw new java.io.UncheckedIOException(error);
        }
    }
}
