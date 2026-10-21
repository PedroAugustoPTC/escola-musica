package br.com.felipeblaft.reports.outbound.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.felipeblaft.reports.domain.common.Channel;
import br.com.felipeblaft.reports.domain.common.Medium;
import br.com.felipeblaft.reports.domain.job.JobStep;
import br.com.felipeblaft.reports.domain.job.JobType;
import br.com.felipeblaft.reports.domain.job.ProcessingJob;
import br.com.felipeblaft.reports.domain.registry.Student;
import br.com.felipeblaft.reports.domain.registry.Teacher;
import br.com.felipeblaft.reports.domain.report.LessonReport;
import br.com.felipeblaft.reports.domain.report.OpenReport;
import br.com.felipeblaft.reports.port.JobQueuePort;
import br.com.felipeblaft.reports.port.OpenReportsQueryPort;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Relatórios em aberto só aparecem enquanto o aluno continua associado ao professor; ao retirar a
 * associação eles ficam congelados (preservados, sem alteração) e voltam na reassociação (R-23,
 * T161). Arquivo próprio para não disputar o da história US2.
 */
class OpenReportsQueryAssociationIT extends RegistryPersistenceBase {

    @Autowired OpenReportsQueryPort query;
    @Autowired JobQueuePort queue;

    @Test
    void removedAssociationHidesTheReportWithoutChangingIt() {
        Teacher clara = teacher("Clara Souza", true);
        Student matteo = studentOf(clara, "Matteo Ferreira");
        LessonReport report = openReport(clara, matteo);
        Map<String, Object> before =
                jdbc.queryForMap(
                        "select status, updated_at, current_version from lesson_report where id = ?",
                        report.id());
        assertThat(query.listByTeacher(clara.id())).hasSize(1);

        assignments.delete(clara.id(), matteo.id());

        assertThat(query.listByTeacher(clara.id())).isEmpty();
        assertThat(query.searchByStudent(clara.id(), matteo.id())).isEmpty();
        assertThat(query.lastLessonByStudent(clara.id())).isEmpty();
        assertThat(
                        jdbc.queryForMap(
                                "select status, updated_at, current_version from lesson_report"
                                        + " where id = ?",
                                report.id()))
                .isEqualTo(before);
    }

    @Test
    void reassociationBringsTheFrozenReportBack() {
        Teacher clara = teacher("Clara Souza", true);
        Student matteo = studentOf(clara, "Matteo Ferreira");
        LessonReport report = openReport(clara, matteo);
        assignments.delete(clara.id(), matteo.id());
        assertThat(query.listByTeacher(clara.id())).isEmpty();

        assign(clara, matteo);

        assertThat(query.listByTeacher(clara.id()))
                .singleElement()
                .satisfies(r -> assertThat(r.reportId()).isEqualTo(report.id()));
        assertThat(query.searchByStudent(clara.id(), matteo.id())).isPresent();
        assertThat(query.lastLessonByStudent(clara.id())).containsKey(matteo.id());
    }

    @Test
    void onlyTheRemovedStudentIsHidden() {
        Teacher clara = teacher("Clara Souza", true);
        Student matteo = studentOf(clara, "Matteo Ferreira");
        Student sofia = studentOf(clara, "Sofia Alves");
        openReport(clara, matteo);
        LessonReport sofiaReport = openReport(clara, sofia);

        assignments.delete(clara.id(), matteo.id());

        List<OpenReport> list = query.listByTeacher(clara.id());
        assertThat(list)
                .singleElement()
                .satisfies(r -> assertThat(r.reportId()).isEqualTo(sofiaReport.id()));
        assertThat(query.lastLessonByStudent(clara.id()))
                .containsOnlyKeys(sofia.id());
    }

    @Test
    void studentNeverAssignedToTheTeacherIsNotListed() {
        Teacher clara = teacher("Clara Souza", true);
        Teacher bruno = teacher("Bruno Lima", true);
        Student matteo = studentOf(bruno, "Matteo Ferreira");
        // relatório gravado com o par errado (dado antigo): sem associação, nunca aparece
        openReport(clara, matteo);

        assertThat(query.listByTeacher(clara.id())).isEmpty();
        assertThat(query.searchByStudent(clara.id(), matteo.id())).isEmpty();
    }

    @Test
    void registrationInProgressFollowsTheAssociationToo() {
        Teacher clara = teacher("Clara Souza", true);
        Student matteo = studentOf(clara, "Matteo Ferreira");
        queue.enqueue(
                ProcessingJob.create(
                        JobType.LESSON_REGISTRATION,
                        clara.id(),
                        matteo.id(),
                        null,
                        null,
                        Channel.WEB,
                        Medium.AUDIO,
                        JobStep.UPLOADING,
                        now()));
        assertThat(query.listByTeacher(clara.id())).hasSize(1);
        assertThat(query.searchByStudent(clara.id(), matteo.id())).isPresent();

        assignments.delete(clara.id(), matteo.id());

        assertThat(query.listByTeacher(clara.id())).isEmpty();
        assertThat(query.searchByStudent(clara.id(), matteo.id())).isEmpty();
    }

    @Test
    void countOpenReportsCountsTheFrozenOnesOfThePair() {
        Teacher clara = teacher("Clara Souza", true);
        Teacher bruno = teacher("Bruno Lima", true);
        Student matteo = studentOf(clara, "Matteo Ferreira");
        Student sofia = studentOf(clara, "Sofia Alves");
        assign(bruno, matteo);
        openReport(clara, matteo);
        openReport(bruno, matteo);
        queue.enqueue(
                ProcessingJob.create(
                        JobType.LESSON_REGISTRATION,
                        clara.id(),
                        sofia.id(),
                        null,
                        null,
                        Channel.WEB,
                        Medium.AUDIO,
                        JobStep.UPLOADING,
                        now()));

        // a prévia da retirada conta os relatórios que ficarão congelados, só do par
        assertThat(query.countOpenReports(clara.id(), matteo.id())).isEqualTo(1);
        assertThat(query.countOpenReports(bruno.id(), matteo.id())).isEqualTo(1);
        assertThat(query.countOpenReports(clara.id(), sofia.id())).isEqualTo(1);
        assertThat(query.countOpenReports(bruno.id(), sofia.id())).isZero();

        assignments.delete(clara.id(), matteo.id());
        assertThat(query.countOpenReports(clara.id(), matteo.id())).isEqualTo(1);
    }
}
