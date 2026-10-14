package br.com.felipeblaft.reports.inbound.seed;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.felipeblaft.reports.config.MessageFormatting;
import br.com.felipeblaft.reports.port.LessonReportRepository;
import br.com.felipeblaft.reports.port.ReportVersionRepository;
import br.com.felipeblaft.reports.port.SemesterReportRepository;
import br.com.felipeblaft.reports.port.StudentRepository;
import br.com.felipeblaft.reports.port.TeacherRepository;
import br.com.felipeblaft.reports.port.TransactionPort;
import br.com.felipeblaft.reports.service.DocumentGenerationService;
import br.com.felipeblaft.reports.support.IntegrationBase;
import br.com.felipeblaft.reports.usecase.seed.LoadInitialRegistryUseCase;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Massa de demonstração do ambiente local (local.sh subir --massa=true). */
class DemoDataIT extends IntegrationBase {

    private static final List<String> ARGUMENTS =
            List.of("demo-data", "--file", "../../infra/carga/demonstracao.json");

    @Autowired private LoadInitialRegistryUseCase seed;
    @Autowired private TeacherRepository teachers;
    @Autowired private StudentRepository students;
    @Autowired private LessonReportRepository lessonReports;
    @Autowired private ReportVersionRepository versions;
    @Autowired private SemesterReportRepository semester;
    @Autowired private DocumentGenerationService documents;
    @Autowired private TransactionPort transaction;
    @Autowired private MessageFormatting formatting;

    private DemoDataCommand command;

    @BeforeEach
    void createCommand() {
        command =
                new DemoDataCommand(
                        seed,
                        teachers,
                        students,
                        lessonReports,
                        versions,
                        semester,
                        documents,
                        transaction,
                        clock,
                        formatting,
                        jdbc);
    }

    private String run() {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        int code = command.run(ARGUMENTS, new PrintStream(bytes, true, StandardCharsets.UTF_8));
        return code + "\n" + bytes.toString(StandardCharsets.UTF_8);
    }

    private Map<String, Integer> statusOf(String table) {
        Map<String, Integer> count = new java.util.TreeMap<>();
        jdbc.queryForList("select status, count(*) as n from " + table + " group by status")
                .forEach(
                        l -> count.put((String) l.get("status"), ((Number) l.get("n")).intValue()));
        return count;
    }

    @Test
    void createsRegistriesReportsAndPdfsInEmptyDatabase() {
        String output = run();

        assertThat(output).startsWith("0\n").contains("Massa de demonstração criada");
        assertThat(output).contains("clara@exemplo.test → ").contains("bruno@exemplo.test → ");
        assertThat(jdbc.queryForObject("select count(*) from teacher", Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("select count(*) from student", Integer.class)).isEqualTo(8);

        Map<String, Integer> lessons = statusOf("lesson_report");
        assertThat(lessons.get("APROVADO")).isGreaterThan(30);
        assertThat(lessons)
                .containsEntry("AGUARDANDO_CONFERENCIA", 1)
                .containsEntry("AGUARDANDO_RESPOSTAS", 1);
        assertThat(statusOf("semester_report"))
                .containsEntry("APROVADO", 1)
                .containsEntry("AGUARDANDO_CONFERENCIA", 1);

        // cada aula aprovada tem PDF de conferência e oficial; o semestral aprovado, os dois também
        Integer official =
                jdbc.queryForObject(
                        "select count(*) from pdf_document where type = 'OFICIAL'", Integer.class);
        assertThat(official).isEqualTo(lessons.get("APROVADO") + 1);
        assertThat(output)
                .contains(
                        "PDFs gerados: "
                                + jdbc.queryForObject(
                                        "select count(*) from pdf_document", Integer.class));

        // alunos sem histórico, para testar as telas vazias
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from lesson_report r join student a on a.id = r.student_id"
                                        + " where a.name in ('Lucas Pimentel', 'Sofia Rangel')",
                                Integer.class))
                .isZero();
    }

    @Test
    void databaseRefusesWithData() {
        run();
        String second = run();

        assertThat(second).startsWith("1\n").contains("só é criada em banco vazio");
        assertThat(jdbc.queryForObject("select count(*) from teacher", Integer.class)).isEqualTo(2);
    }
}
