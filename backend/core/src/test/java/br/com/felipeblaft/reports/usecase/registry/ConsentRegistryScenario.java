package br.com.felipeblaft.reports.usecase.registry;

import br.com.felipeblaft.reports.domain.access.Actor;
import br.com.felipeblaft.reports.domain.common.BusinessParameters;
import br.com.felipeblaft.reports.domain.common.Secret;
import br.com.felipeblaft.reports.domain.privacy.ConsentInput;
import br.com.felipeblaft.reports.domain.registry.Cpf;
import br.com.felipeblaft.reports.domain.registry.Student;
import br.com.felipeblaft.reports.domain.registry.Teacher;
import br.com.felipeblaft.reports.doubles.FakeDataProtection;
import br.com.felipeblaft.reports.doubles.InMemoryAccessAttemptRepository;
import br.com.felipeblaft.reports.service.AccessAttemptsControlService;
import br.com.felipeblaft.reports.usecase.privacy.ConsentScenario;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Cadastro e edição de aluno com a verificação do consentimento ligada, sobre o cenário de
 * consentimento. Hoje, no fuso da escola, é 2026-09-21.
 */
final class ConsentRegistryScenario {

    static final String CPF_A = "529.982.247-25";
    static final String CPF_B = "123.456.789-09";
    static final String CORRELATION = "req-1";

    final ConsentScenario base = new ConsentScenario();
    final FakeDataProtection protection = new FakeDataProtection();
    final AccessAttemptsControlService attempts =
            new AccessAttemptsControlService(
                    new InMemoryAccessAttemptRepository(),
                    protection,
                    base.clock,
                    BusinessParameters.defaults());

    final RegisterStudentUseCase register =
            new RegisterStudentUseCase(
                    base.authorization,
                    base.students,
                    base.assignments,
                    base.teachers,
                    protection,
                    attempts,
                    base.authorship,
                    base.clock,
                    base.recording);
    final UpdateStudentUseCase update =
            new UpdateStudentUseCase(
                    base.authorization,
                    base.access,
                    base.students,
                    base.assignments,
                    base.teachers,
                    protection,
                    attempts,
                    base.authorship,
                    base.clock,
                    base.recording);

    ConsentRegistryScenario() {
        base.term(1, false);
    }

    Teacher teacher(String name) {
        return base.teacher(name);
    }

    static StudentInput adult(String name, String cpf) {
        return new StudentInput(name, "1990-03-04", new Secret(cpf), null, null);
    }

    static StudentInput minor(String name, String cpf) {
        return new StudentInput(
                name,
                "2014-05-10",
                new Secret(cpf),
                new GuardianInput("Maria Silva", "(11) 98765-4321", "Maria@Example.com"),
                "Piano");
    }

    static ConsentInput consent() {
        return ConsentScenario.input(1);
    }

    RegisterStudentUseCase.Command registration(
            StudentInput input, ConsentInput consent, UUID... teacherIds) {
        return new RegisterStudentUseCase.Command(input, List.of(teacherIds), CORRELATION, consent);
    }

    /** Aluno adulto já cadastrado, associado aos professores dados. */
    Student existingAdult(String name, String cpf, Teacher... owners) {
        Student student =
                Student.create(
                        name,
                        LocalDate.parse("1990-03-04"),
                        protection.encryptCpf(Cpf.of(cpf)),
                        protection.hashCpf(Cpf.of(cpf)),
                        null,
                        "Piano",
                        base.clock.now());
        base.students.save(student);
        for (Teacher owner : owners) {
            base.assignments.assign(owner.id(), student.id());
        }
        return student;
    }

    static Actor administrator() {
        return br.com.felipeblaft.reports.doubles.ActorFixtures.administratorWeb();
    }
}
