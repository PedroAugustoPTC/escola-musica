package br.com.felipeblaft.reports.usecase.registry;

import br.com.felipeblaft.reports.domain.access.Actor;
import br.com.felipeblaft.reports.domain.access.PermissionMatrix;
import br.com.felipeblaft.reports.domain.common.Channel;
import br.com.felipeblaft.reports.domain.common.ReportType;
import br.com.felipeblaft.reports.domain.registry.Student;
import br.com.felipeblaft.reports.domain.registry.Teacher;
import br.com.felipeblaft.reports.domain.report.OpenReport;
import br.com.felipeblaft.reports.domain.report.ReportStatus;
import br.com.felipeblaft.reports.doubles.ActorFixtures;
import br.com.felipeblaft.reports.doubles.FakeAuthorshipRecorder;
import br.com.felipeblaft.reports.doubles.FakeOpenReportsQuery;
import br.com.felipeblaft.reports.doubles.FixedClock;
import br.com.felipeblaft.reports.doubles.InMemoryAssignmentRepository;
import br.com.felipeblaft.reports.doubles.InMemoryStudentRepository;
import br.com.felipeblaft.reports.doubles.InMemoryTeacherRepository;
import br.com.felipeblaft.reports.service.ActorAuthorizationService;
import br.com.felipeblaft.reports.service.AuthorizationService;
import br.com.felipeblaft.reports.service.AuthorshipService;
import br.com.felipeblaft.reports.service.ReportEligibilityService;
import br.com.felipeblaft.reports.service.StudentAccessService;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Cenário dos casos de uso de situação do aluno e de associação: professores, alunos e associações
 * em memória, em que o dublê de associações conhece os professores (só os ativos contam, FR-026),
 * mais o registro de autoria e os casos de uso do fluxo.
 */
public class SituationScenario {

    public final FixedClock clock = new FixedClock();
    public final InMemoryTeacherRepository teachers = new InMemoryTeacherRepository();
    public final InMemoryAssignmentRepository assignments =
            new InMemoryAssignmentRepository(teachers);
    public final InMemoryStudentRepository students = new InMemoryStudentRepository(assignments);
    public final FakeOpenReportsQuery open = new FakeOpenReportsQuery();
    public final FakeAuthorshipRecorder recorder = new FakeAuthorshipRecorder();

    public final AuthorshipService authorship = new AuthorshipService(recorder, clock);
    public final ActorAuthorizationService authorization =
            new ActorAuthorizationService(PermissionMatrix.standard());
    public final StudentAccessService access = new StudentAccessService(students);
    public final AuthorizationService assignmentAuthorization =
            new AuthorizationService(assignments);
    public final ReportEligibilityService eligibility =
            new ReportEligibilityService(teachers, students);

    public final InactivateStudentUseCase inactivate =
            new InactivateStudentUseCase(
                    authorization, access, students, teachers, assignments, authorship, clock);
    public final ReactivateStudentUseCase reactivate =
            new ReactivateStudentUseCase(
                    authorization, access, students, teachers, assignments, authorship, clock);
    public final AssignTeacherToStudentUseCase assign =
            new AssignTeacherToStudentUseCase(
                    authorization, access, teachers, assignments, authorship, clock);
    public final RemoveTeacherFromStudentUseCase remove =
            new RemoveTeacherFromStudentUseCase(authorization, assignments, authorship);
    public final PreviewAssignmentRemovalUseCase preview =
            new PreviewAssignmentRemovalUseCase(authorization, assignments, open);

    private int sequence;

    public Actor administrator() {
        return ActorFixtures.administratorWeb();
    }

    public static String correlation() {
        return UUID.randomUUID().toString();
    }

    /** Professor ativo, já guardado. */
    public Teacher teacher(String name) {
        Teacher teacher =
                Teacher.create(
                        name, "professor" + (++sequence) + "@example.test", true, clock.now());
        teachers.save(teacher);
        return teacher;
    }

    /** Professor inativo, já guardado. */
    public Teacher inactiveTeacher(String name) {
        Teacher teacher = teacher(name);
        teacher.inactivate(clock.now());
        teachers.save(teacher);
        return teacher;
    }

    /** Aluno ativo sem nenhum professor. */
    public Student student(String name) {
        Student student =
                Student.create(
                        name,
                        LocalDate.of(2012, 1, 1),
                        new byte[] {1},
                        "cpf-hash-" + (++sequence),
                        "Responsável",
                        "Piano",
                        clock.now());
        students.save(student);
        return student;
    }

    /** Aluno ativo associado ao professor. */
    public Student student(String name, Teacher teacher) {
        Student student = student(name);
        assignments.assign(teacher.id(), student.id());
        return student;
    }

    public void inactivateDirectly(Student student) {
        student.inactivate(clock.now());
        students.save(student);
    }

    /** Relatório de aula em aberto do professor para o aluno, no dublê da consulta. */
    public OpenReport openReport(Teacher teacher, Student student) {
        OpenReport report =
                new OpenReport(
                        ReportType.LESSON,
                        UUID.randomUUID(),
                        null,
                        student.id(),
                        student.name(),
                        ReportStatus.AWAITING_REVIEW,
                        null,
                        null,
                        Channel.WEB,
                        clock.now());
        open.add(teacher.id(), report);
        return report;
    }
}
