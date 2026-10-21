package br.com.felipeblaft.reports.outbound.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.felipeblaft.reports.domain.registry.Student;
import br.com.felipeblaft.reports.domain.registry.Teacher;
import br.com.felipeblaft.reports.domain.report.LessonReport;
import br.com.felipeblaft.reports.domain.report.OpenReport;
import br.com.felipeblaft.reports.port.OpenReportsQueryPort;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Relatórios em aberto de professor inativo ficam congelados (R-23, T083). Verificação 3 do plano:
 * antes da feature 005 {@code listTeacherOpenReports} filtrava só por professor e situação do
 * relatório, sem olhar a situação do professor nem a associação; estes testes cobrem o filtro
 * novo. O filtro por associação está em {@link OpenReportsQueryAssociationIT}.
 */
class OpenReportsQueryAdapterIT extends RegistryPersistenceBase {

    @Autowired OpenReportsQueryPort query;

    @Test
    void activeTeacherSeesOpenReportOfAssignedStudent() {
        Teacher clara = teacher("Clara Souza", true);
        Student matteo = studentOf(clara, "Matteo Ferreira");
        LessonReport report = openReport(clara, matteo);

        List<OpenReport> list = query.listByTeacher(clara.id());

        assertThat(list).singleElement().satisfies(r -> assertThat(r.reportId()).isEqualTo(report.id()));
        assertThat(query.searchByStudent(clara.id(), matteo.id())).isPresent();
    }

    @Test
    void inactiveTeacherHasNoOpenReportAndNothingIsChanged() {
        Teacher clara = teacher("Clara Souza", true);
        Student matteo = studentOf(clara, "Matteo Ferreira");
        LessonReport report = openReport(clara, matteo);
        Map<String, Object> before =
                jdbc.queryForMap(
                        "select status, updated_at, current_version from lesson_report where id = ?",
                        report.id());

        inactivate(clara);

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
    void reactivatingTheTeacherBringsTheReportBack() {
        Teacher clara = teacher("Clara Souza", true);
        Student matteo = studentOf(clara, "Matteo Ferreira");
        LessonReport report = openReport(clara, matteo);
        inactivate(clara);
        assertThat(query.listByTeacher(clara.id())).isEmpty();

        reactivate(clara);

        assertThat(query.listByTeacher(clara.id()))
                .singleElement()
                .satisfies(r -> assertThat(r.reportId()).isEqualTo(report.id()));
        assertThat(query.searchByStudent(clara.id(), matteo.id())).isPresent();
    }

    @Test
    void inactivatingOneTeacherDoesNotHideTheReportsOfAnother() {
        Teacher clara = teacher("Clara Souza", true);
        Teacher bruno = teacher("Bruno Lima", true);
        Student matteo = studentOf(clara, "Matteo Ferreira");
        Student sofia = studentOf(bruno, "Sofia Alves");
        openReport(clara, matteo);
        LessonReport brunoReport = openReport(bruno, sofia);

        inactivate(clara);

        assertThat(query.listByTeacher(bruno.id()))
                .singleElement()
                .satisfies(r -> assertThat(r.reportId()).isEqualTo(brunoReport.id()));
    }

    @Test
    void lastLessonOfInactiveTeacherIsHiddenAndReturnsOnReactivation() {
        Teacher clara = teacher("Clara Souza", true);
        Student matteo = studentOf(clara, "Matteo Ferreira");
        openReport(clara, matteo);
        Map<UUID, LocalDate> visible = query.lastLessonByStudent(clara.id());
        assertThat(visible).containsKey(matteo.id());

        inactivate(clara);
        assertThat(query.lastLessonByStudent(clara.id())).isEmpty();

        reactivate(clara);
        assertThat(query.lastLessonByStudent(clara.id())).isEqualTo(visible);
    }
}
