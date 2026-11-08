package br.com.felipeblaft.reports.usecase.registry;

import br.com.felipeblaft.reports.domain.access.ActionOrigin;
import br.com.felipeblaft.reports.domain.access.Actor;
import br.com.felipeblaft.reports.domain.access.Operation;
import br.com.felipeblaft.reports.domain.access.Permission;
import br.com.felipeblaft.reports.domain.access.PermissionMatrix;
import br.com.felipeblaft.reports.domain.common.BusinessParameters;
import br.com.felipeblaft.reports.domain.registry.Cpf;
import br.com.felipeblaft.reports.domain.registry.Student;
import br.com.felipeblaft.reports.domain.registry.Teacher;
import br.com.felipeblaft.reports.domain.telegram.StudentRegistrationDraft;
import br.com.felipeblaft.reports.domain.telegram.StudentRegistrationDraft.Kind;
import br.com.felipeblaft.reports.domain.telegram.TelegramLink;
import br.com.felipeblaft.reports.doubles.FakeAuthorshipRecorder;
import br.com.felipeblaft.reports.doubles.FakeCpfDecryption;
import br.com.felipeblaft.reports.doubles.FakeDataProtection;
import br.com.felipeblaft.reports.doubles.FixedClock;
import br.com.felipeblaft.reports.doubles.InMemoryAccessAttemptRepository;
import br.com.felipeblaft.reports.doubles.InMemoryAssignmentRepository;
import br.com.felipeblaft.reports.doubles.InMemoryStudentRegistrationDraftRepository;
import br.com.felipeblaft.reports.doubles.InMemoryStudentRepository;
import br.com.felipeblaft.reports.doubles.InMemoryTeacherRepository;
import br.com.felipeblaft.reports.doubles.InMemoryTelegramLinkRepository;
import br.com.felipeblaft.reports.doubles.ProcessingDoubles.ImmediateTransaction;
import br.com.felipeblaft.reports.service.AccessAttemptsControlService;
import br.com.felipeblaft.reports.service.ActorAuthorizationService;
import br.com.felipeblaft.reports.service.AuthorshipService;
import br.com.felipeblaft.reports.service.StudentAccessService;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Monta o cadastro em andamento do Telegram sobre dublês em memória: professor com vínculo, alunos
 * dele e de outro professor, rascunhos, e os quatro casos de uso de gravação como dublês que só
 * registram a chamada (a integração real é do Fecho). Hoje, no fuso da escola, é 2026-09-21. As
 * construções dos casos de uso orquestrados ficam todas aqui: se uma assinatura mudar, só este
 * arquivo muda.
 */
final class DraftScenario {

    static final String CPF = "529.982.247-25";
    static final String OTHER_CPF = "111.444.777-35";
    static final Duration VALIDITY = Duration.ofMinutes(30);
    static final String CORRELATION = "12345";

    final FixedClock clock = new FixedClock();
    final FakeDataProtection protection = new FakeDataProtection();
    final FakeCpfDecryption decryption = new FakeCpfDecryption();
    final FakeAuthorshipRecorder recorder = new FakeAuthorshipRecorder();
    final InMemoryTeacherRepository teachers = new InMemoryTeacherRepository();
    final InMemoryAssignmentRepository assignments = new InMemoryAssignmentRepository(teachers);
    final InMemoryStudentRepository students = new InMemoryStudentRepository(assignments);
    final InMemoryTelegramLinkRepository links = new InMemoryTelegramLinkRepository();
    final InMemoryStudentRegistrationDraftRepository drafts =
            new InMemoryStudentRegistrationDraftRepository();
    final ImmediateTransaction transaction = new ImmediateTransaction();
    final AccessAttemptsControlService attempts =
            new AccessAttemptsControlService(
                    new InMemoryAccessAttemptRepository(),
                    protection,
                    clock,
                    BusinessParameters.defaults());
    final AuthorshipService authorship = new AuthorshipService(recorder, clock);
    final ActorAuthorizationService authorization;
    final StudentAccessService access = new StudentAccessService(students);

    final Teacher teacher;
    final Teacher otherTeacher;
    final TelegramLink link;
    final Actor actor;

    final RecordingRegister register;
    final RecordingUpdate update;
    final RecordingInactivate inactivate;
    final RecordingReactivate reactivate;

    DraftScenario() {
        this(PermissionMatrix.standard());
    }

    DraftScenario(PermissionMatrix matrix) {
        authorization = new ActorAuthorizationService(matrix);
        register = new RecordingRegister(this);
        update = new RecordingUpdate(this);
        inactivate = new RecordingInactivate(this);
        reactivate = new RecordingReactivate(this);
        teacher = teacher("Professora Clara");
        otherTeacher = teacher("Professor Bruno");
        link = TelegramLink.create(teacher.id(), 111L, 222L, clock.now());
        links.save(link);
        actor = Actor.teacher(teacher.id(), ActionOrigin.TELEGRAM);
        links.cascadeRemovalTo(drafts::removeByLinkId);
    }

    /** Matriz padrão em que a operação dada é negada a todos. */
    static PermissionMatrix matrixDenying(Operation operation) {
        Map<Operation, PermissionMatrix.Rule> rules =
                new EnumMap<>(PermissionMatrix.standard().rules());
        rules.put(
                operation,
                new PermissionMatrix.Rule(
                        Permission.DENY, Permission.DENY, Permission.DENY, Set.of()));
        return new PermissionMatrix(rules);
    }

    private Teacher teacher(String name) {
        Teacher created =
                Teacher.create(
                        name,
                        name.toLowerCase(java.util.Locale.ROOT).replace(' ', '.') + "@school.com",
                        true,
                        clock.now());
        teachers.save(created);
        return created;
    }

    /** Aluno já cadastrado e associado ao professor dado. */
    Student student(Teacher owner, String name) {
        Cpf cpf = Cpf.of(OTHER_CPF);
        Student student =
                Student.create(
                        name,
                        LocalDate.of(2012, 1, 1),
                        protection.encryptCpf(cpf),
                        protection.hashCpf(cpf) + ":" + name,
                        null,
                        "Violão",
                        clock.now());
        students.save(student);
        assignments.assign(owner.id(), student.id());
        return student;
    }

    /** Aluno já cadastrado com este CPF (resumo de verdade, para a checagem de duplicidade). */
    Student studentWithCpf(Teacher owner, String name, String cpfText) {
        Cpf cpf = Cpf.of(cpfText);
        Student student =
                Student.create(
                        name,
                        LocalDate.of(2012, 1, 1),
                        protection.encryptCpf(cpf),
                        protection.hashCpf(cpf),
                        null,
                        "Violão",
                        clock.now());
        students.save(student);
        assignments.assign(owner.id(), student.id());
        return student;
    }

    Student ownStudent() {
        return student(teacher, "Aluno Próprio");
    }

    Student alienStudent() {
        return student(otherTeacher, "Aluno Alheio");
    }

    /** Cadastro em andamento do vínculo, gravado direto no dublê. */
    StudentRegistrationDraft startDraft(Kind kind, UUID studentId) {
        StudentRegistrationDraft draft =
                StudentRegistrationDraft.start(link.id(), kind, studentId, clock.now(), VALIDITY);
        drafts.save(draft);
        return draft;
    }

    /** Cadastro {@code CREATE} completo, de adulto, com o token dado e já gravado. */
    StudentRegistrationDraft confirmableCreate(String token) {
        StudentRegistrationDraft draft =
                StudentRegistrationDraft.start(link.id(), Kind.CREATE, null, clock.now(), VALIDITY)
                        .withName("Ana Souza", clock.now(), VALIDITY)
                        .withBirthDate(LocalDate.of(1990, 3, 15), clock.now(), VALIDITY)
                        .withCpf(
                                protection.encryptCpf(Cpf.of(CPF)),
                                protection.hashCpf(Cpf.of(CPF)),
                                clock.now(),
                                VALIDITY)
                        .withInstrument("Violão", clock.now(), VALIDITY)
                        .withConfirmationToken(token, clock.now(), VALIDITY);
        drafts.save(draft);
        return draft;
    }

    StartStudentRegistrationUseCase start() {
        return new StartStudentRegistrationUseCase(
                authorization, access, links, drafts, clock, VALIDITY);
    }

    /** Termo 1 já publicado: o consentimento do menor é coletado nos passos C1 a C5. */
    final br.com.felipeblaft.reports.doubles.InMemoryConsentTermRepository consentTerms =
            consentTerms();

    private br.com.felipeblaft.reports.doubles.InMemoryConsentTermRepository consentTerms() {
        var repository = new br.com.felipeblaft.reports.doubles.InMemoryConsentTermRepository();
        repository.save(
                new br.com.felipeblaft.reports.domain.privacy.ConsentTermVersion(
                        UUID.randomUUID(),
                        1,
                        java.time.LocalDate.of(2026, 1, 1),
                        "Institutional text",
                        false,
                        UUID.randomUUID(),
                        java.time.Instant.parse("2026-01-01T12:00:00Z")));
        return repository;
    }

    CollectStudentRegistrationFieldUseCase collect() {
        CollectStudentRegistrationFieldUseCase useCase =
                new CollectStudentRegistrationFieldUseCase(
                        authorization,
                        drafts,
                        students,
                        assignments,
                        protection,
                        attempts,
                        authorship,
                        clock,
                        VALIDITY);
        useCase.useConsentTerms(consentTerms);
        return useCase;
    }

    ConfirmStudentRegistrationUseCase confirm() {
        return new ConfirmStudentRegistrationUseCase(
                authorization,
                drafts,
                register,
                update,
                inactivate,
                reactivate,
                decryption,
                transaction,
                clock);
    }

    CancelStudentRegistrationUseCase cancel() {
        return new CancelStudentRegistrationUseCase(authorization, drafts);
    }

    /** Total de chamadas recebidas pelos quatro casos de uso de gravação. */
    int writes() {
        return register.calls.size()
                + update.calls.size()
                + inactivate.calls.size()
                + reactivate.calls.size();
    }

    static StudentView view(String name) {
        return new StudentView(
                UUID.randomUUID(),
                name,
                LocalDate.of(1990, 3, 15),
                36,
                "Violão",
                true,
                null,
                List.of(),
                true);
    }

    // ---- Dublês dos casos de uso orquestrados

    /** Chamada recebida por um dublê: ator e comando. */
    record Call<C>(Actor actor, C command) {}

    static final class RecordingRegister extends RegisterStudentUseCase {
        final List<Call<Command>> calls = new ArrayList<>();
        RuntimeException failure;
        StudentView result = view("Registrado");

        RecordingRegister(DraftScenario s) {
            super(
                    s.authorization,
                    s.students,
                    s.assignments,
                    s.teachers,
                    s.protection,
                    s.attempts,
                    s.authorship,
                    s.clock);
        }

        @Override
        public StudentView run(Actor actor, Command command) {
            if (failure != null) {
                throw failure;
            }
            calls.add(new Call<>(actor, command));
            return result;
        }
    }

    static final class RecordingUpdate extends UpdateStudentUseCase {
        final List<Call<Command>> calls = new ArrayList<>();
        RuntimeException failure;
        StudentView result = view("Atualizado");

        RecordingUpdate(DraftScenario s) {
            super(
                    s.authorization,
                    s.access,
                    s.students,
                    s.assignments,
                    s.teachers,
                    s.protection,
                    s.attempts,
                    s.authorship,
                    s.clock);
        }

        @Override
        public StudentView run(Actor actor, Command command) {
            if (failure != null) {
                throw failure;
            }
            calls.add(new Call<>(actor, command));
            return result;
        }
    }

    static final class RecordingInactivate extends InactivateStudentUseCase {
        final List<Call<Command>> calls = new ArrayList<>();
        RuntimeException failure;
        StudentView result = view("Inativado");

        RecordingInactivate(DraftScenario s) {
            super(
                    s.authorization,
                    s.access,
                    s.students,
                    s.teachers,
                    s.assignments,
                    s.authorship,
                    s.clock);
        }

        @Override
        public StudentView run(Actor actor, Command command) {
            if (failure != null) {
                throw failure;
            }
            calls.add(new Call<>(actor, command));
            return result;
        }
    }

    static final class RecordingReactivate extends ReactivateStudentUseCase {
        final List<Call<Command>> calls = new ArrayList<>();
        RuntimeException failure;
        StudentView result = view("Reativado");

        RecordingReactivate(DraftScenario s) {
            super(
                    s.authorization,
                    s.access,
                    s.students,
                    s.teachers,
                    s.assignments,
                    s.authorship,
                    s.clock);
        }

        @Override
        public StudentView run(Actor actor, Command command) {
            if (failure != null) {
                throw failure;
            }
            calls.add(new Call<>(actor, command));
            return result;
        }
    }
}
