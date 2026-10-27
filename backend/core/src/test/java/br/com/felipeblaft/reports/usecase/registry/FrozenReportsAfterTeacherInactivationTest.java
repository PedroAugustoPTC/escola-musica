package br.com.felipeblaft.reports.usecase.registry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.felipeblaft.reports.domain.common.Channel;
import br.com.felipeblaft.reports.domain.common.DomainException;
import br.com.felipeblaft.reports.domain.common.ErrorCode;
import br.com.felipeblaft.reports.domain.registry.Student;
import br.com.felipeblaft.reports.domain.registry.Teacher;
import br.com.felipeblaft.reports.domain.report.OpenReport;
import br.com.felipeblaft.reports.doubles.ReportScenario;
import br.com.felipeblaft.reports.service.ReportEligibilityService;
import br.com.felipeblaft.reports.usecase.lesson.RegisterLessonByAudioUseCase;
import br.com.felipeblaft.reports.usecase.registry.PreviewAssignmentRemovalUseCase.Result;
import br.com.felipeblaft.reports.usecase.semester.GenerateSemesterReportUseCase;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/**
 * Relatórios de professor inativo (R-23): congelados e preservados, fora das listas e das ações, e
 * de volta quando o professor é reativado. Nenhum estado novo de relatório existe.
 */
class FrozenReportsAfterTeacherInactivationTest {

    private final ReportScenario s = new ReportScenario();
    private final ReportEligibilityService eligibility =
            new ReportEligibilityService(s.teachers, s.students);
    private final RegisterLessonByAudioUseCase register =
            new RegisterLessonByAudioUseCase(
                    s.authorization, s.open, s.audioSending, s.queue, s.clock, eligibility);
    private final GenerateSemesterReportUseCase generate =
            new GenerateSemesterReportUseCase(s.preview, s.queue, s.clock, eligibility);

    private static ErrorCode codeOf(Throwable thrown) {
        return ((DomainException) thrown).code();
    }

    private void setActive(Teacher teacher, boolean active) {
        if (active) {
            teacher.reactivate(s.clock.now());
        } else {
            teacher.inactivate(s.clock.now());
        }
        s.teachers.save(teacher);
    }

    @Test
    void inactiveTeacherCannotStartAReportOrGenerateASemester() {
        s.approvedLesson(s.matteo, LocalDate.of(2026, 3, 10), "Escala de Dó");
        setActive(s.clara, false);

        assertThatThrownBy(
                        () ->
                                register.run(
                                        s.clara.id(),
                                        s.matteo.id(),
                                        ReportScenario.file("a.ogg", 10),
                                        Channel.WEB))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.TEACHER_INACTIVE));
        assertThatThrownBy(
                        () ->
                                generate.run(
                                        s.clara.id(), s.matteo.id(), "2026-1", false, Channel.WEB))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.TEACHER_INACTIVE));
        assertThat(s.queue.jobs).isEmpty();
        // o áudio de um envio recusado é descartado
        assertThat(s.normalization.deleted).isNotEmpty();
    }

    @Test
    void reportsStayPreservedWhileInactiveAndTheTeacherCanActAgainAfterReactivation() {
        s.approvedLesson(s.matteo, LocalDate.of(2026, 3, 10), "Escala de Dó");
        OpenReport open =
                new OpenReport(
                        br.com.felipeblaft.reports.domain.common.ReportType.LESSON,
                        java.util.UUID.randomUUID(),
                        null,
                        s.matteo.id(),
                        s.matteo.name(),
                        br.com.felipeblaft.reports.domain.report.ReportStatus.AWAITING_REVIEW,
                        null,
                        null,
                        Channel.WEB,
                        s.clock.now());
        s.open.add(s.clara.id(), open);
        setActive(s.clara, false);

        assertThat(s.open.listByTeacher(s.clara.id())).containsExactly(open);
        assertThat(s.assignments.exists(s.clara.id(), s.matteo.id())).isTrue();

        setActive(s.clara, true);
        // com o relatório em aberto, o novo registro é recusado como antes (nada mudou)
        assertThatThrownBy(
                        () ->
                                register.run(
                                        s.clara.id(),
                                        s.matteo.id(),
                                        ReportScenario.file("b.ogg", 10),
                                        Channel.WEB))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.REPORT_ALREADY_OPEN));
        assertThat(s.open.listByTeacher(s.clara.id())).containsExactly(open);
    }

    @Test
    void activeTeacherWithActiveStudentRegistersNormally() {
        var job =
                register.run(
                        s.clara.id(), s.matteo.id(), ReportScenario.file("c.ogg", 10), Channel.WEB);

        assertThat(s.queue.jobs).containsKey(job.id());
    }

    @Test
    void inactivationNeverBlocksTheAdministratorsRemovalPreview() {
        SituationScenario c = new SituationScenario();
        Teacher clara = c.teacher("Clara Souza");
        Student matteo = c.student("Matteo Mafra", clara);
        c.openReport(clara, matteo);
        clara.inactivate(c.clock.now());
        c.teachers.save(clara);

        Result result =
                c.preview.run(
                        c.administrator(),
                        new PreviewAssignmentRemovalUseCase.Command(matteo.id(), clara.id()));

        assertThat(result.frozenReports()).isEqualTo(1);
        assertThat(c.assignments.countActiveTeachers(matteo.id())).isZero();
    }
}
