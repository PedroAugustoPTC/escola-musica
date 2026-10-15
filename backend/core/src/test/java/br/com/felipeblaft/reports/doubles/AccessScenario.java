package br.com.felipeblaft.reports.doubles;

import br.com.felipeblaft.reports.domain.access.AccessCode;
import br.com.felipeblaft.reports.domain.common.BusinessParameters;
import br.com.felipeblaft.reports.domain.registry.Student;
import br.com.felipeblaft.reports.domain.registry.Teacher;
import br.com.felipeblaft.reports.service.AccessAttemptsControlService;
import br.com.felipeblaft.reports.service.AccessRenewalService;
import br.com.felipeblaft.reports.service.AuthorizationService;
import br.com.felipeblaft.reports.service.CodeAuthenticationService;
import java.time.LocalDate;

/** Monta repositórios em memória e serviços de acesso compartilhados pelos testes. */
public class AccessScenario {

    public final FixedClock clock = new FixedClock();
    public final BusinessParameters parameters = BusinessParameters.defaults();
    public final FakeDataProtection protection = new FakeDataProtection();
    public final InMemoryTeacherRepository teachers = new InMemoryTeacherRepository();
    public final InMemoryAssignmentRepository assignments = new InMemoryAssignmentRepository();
    public final InMemoryStudentRepository students = new InMemoryStudentRepository(assignments);
    public final InMemoryWebSessionRepository sessions = new InMemoryWebSessionRepository();
    public final InMemoryTelegramLinkRepository links = new InMemoryTelegramLinkRepository();
    public final InMemoryAccessAttemptRepository attemptsRepository =
            new InMemoryAccessAttemptRepository();
    public final FakeOpenReportsQuery open = new FakeOpenReportsQuery();

    public final AccessAttemptsControlService attempts =
            new AccessAttemptsControlService(attemptsRepository, protection, clock, parameters);
    public final AccessRenewalService renewal =
            new AccessRenewalService(teachers, clock, parameters);
    public final CodeAuthenticationService authentication =
            new CodeAuthenticationService(attempts, teachers, protection, clock);
    public final AuthorizationService authorization = new AuthorizationService(assignments);

    /** Professor ativo com o código informado, válido por 7 dias a partir de agora. */
    public Teacher teacher(String name, String email, String code) {
        Teacher teacher = Teacher.create(name, email, true, clock.now());
        teacher.replaceCode(
                protection.hashAccessCode(AccessCode.of(code)),
                clock.now(),
                parameters.codeValidity());
        teachers.save(teacher);
        return teacher;
    }

    /** Aluno associado ao professor. */
    public Student student(Teacher teacher, String name, String instrument) {
        Student student =
                Student.create(
                        name,
                        LocalDate.of(2012, 1, 1),
                        new byte[] {1},
                        "cpf:" + name,
                        "Responsável",
                        instrument,
                        clock.now());
        students.save(student);
        assignments.assign(teacher.id(), student.id());
        return student;
    }
}
