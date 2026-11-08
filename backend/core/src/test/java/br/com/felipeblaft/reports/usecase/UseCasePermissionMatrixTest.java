package br.com.felipeblaft.reports.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import br.com.felipeblaft.reports.domain.access.ActionOrigin;
import br.com.felipeblaft.reports.domain.access.Actor;
import br.com.felipeblaft.reports.domain.access.AuthorshipRecord;
import br.com.felipeblaft.reports.domain.access.Operation;
import br.com.felipeblaft.reports.domain.access.Permission;
import br.com.felipeblaft.reports.domain.access.PermissionMatrix;
import br.com.felipeblaft.reports.domain.common.BusinessParameters;
import br.com.felipeblaft.reports.domain.common.DomainException;
import br.com.felipeblaft.reports.domain.common.ErrorCode;
import br.com.felipeblaft.reports.domain.common.Secret;
import br.com.felipeblaft.reports.doubles.ActorFixtures;
import br.com.felipeblaft.reports.doubles.FakeAuthorshipRecorder;
import br.com.felipeblaft.reports.doubles.FixedClock;
import br.com.felipeblaft.reports.port.AuthorshipRecorderPort;
import br.com.felipeblaft.reports.port.ClockPort;
import br.com.felipeblaft.reports.service.ActorAuthorizationService;
import br.com.felipeblaft.reports.service.AuthorshipService;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.lang.reflect.RecordComponent;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Autorização no núcleo (FR-002, FR-048, SC-005): cada caso de uso de {@code
 * usecase.administrator}, {@code usecase.teacher} e {@code usecase.registry} é chamado com cada
 * ator (administrador na Web, professor na Web, professor no Telegram e o sistema nas duas origens)
 * e a matriz decide, sem a cadeia de segurança da Web. Onde a matriz de {@code
 * contracts/permission-matrix.json} diz {@code DENY}, o resultado é {@code RESOURCE_NOT_FOUND},
 * nenhuma porta é tocada (repositório, hash, transação, fila) e o único registro de autoria
 * admissível é {@code AUTHORIZATION_DENIED}. Onde a matriz permite, o caso de uso passa pela
 * decisão (o resto da execução roda contra portas vazias e não interessa aqui).
 *
 * <p>A matriz esperada é lida direto do arquivo do contrato (sem biblioteca de JSON no núcleo, por
 * expressão regular), de modo que o teste compara o <b>código</b> com o <b>contrato</b>, e não com
 * a própria {@link PermissionMatrix}. Caso de uso sem operação mapeada reprova, e operação da
 * matriz sem caso de uso também.
 */
class UseCasePermissionMatrixTest {

    private static final String BASE = "br.com.felipeblaft.reports.usecase.";
    private static final List<String> PACKAGES =
            List.of(BASE + "administrator", BASE + "teacher", BASE + "registry");
    private static final Path CONTRACT =
            Path.of("../../specs/005-admin-registrations-access/contracts/permission-matrix.json");
    private static final Pattern OPERATION_OBJECT =
            Pattern.compile(
                    "\\{\\s*\"operation\"\\s*:\\s*\"(\\w+)\"(.*?\"system\"\\s*:\\s*\\[[^\\]]*\\])",
                    Pattern.DOTALL);

    /** Operações da matriz que não têm caso de uso nestes pacotes, e onde moram. */
    private static final Map<Operation, String> OPERATIONS_ELSEWHERE =
            Map.ofEntries(
                    Map.entry(
                            Operation.TEACHER_SIGN_IN,
                            "usecase.access.SignInPortalUseCase (entrada pública)"),
                    Map.entry(
                            Operation.LESSON_AND_SEMESTER_REPORTS,
                            "usecase.lesson, semester, history, home, job e document (feature 001)"),
                    Map.entry(
                            Operation.SCHOOL_HISTORY_QUERY,
                            "usecase.history.QuerySchoolHistoryUseCase (feature 006)"),
                    Map.entry(
                            Operation.SCHOOL_REPORT_DOCUMENT_OPEN,
                            "usecase.document.GetSchoolReportDocumentUseCase (feature 006)"),
                    Map.entry(
                            Operation.SCHOOL_REPORT_DOCUMENT_DOWNLOAD,
                            "usecase.document.GetSchoolReportDocumentUseCase (feature 006)"),
                    Map.entry(
                            Operation.AUDIT_TRAIL_QUERY,
                            "usecase.audit.QueryAuditTrailUseCase (feature 007)"),
                    Map.entry(
                            Operation.AUDIT_TRAIL_VERIFY,
                            "usecase.audit.VerifyAuditChainUseCase (feature 007)"),
                    Map.entry(
                            Operation.CONSENT_READ,
                            "usecase.privacy.ListStudentConsentsUseCase (feature 007)"),
                    Map.entry(
                            Operation.CONSENT_RECORD,
                            "usecase.privacy.RecordConsentUseCase (feature 007)"),
                    Map.entry(
                            Operation.CONSENT_REVOKE,
                            "usecase.privacy.RevokeConsentUseCase (feature 007)"),
                    Map.entry(
                            Operation.CONSENT_PENDING_READ,
                            "usecase.privacy.ListPendingConsentsUseCase (feature 007)"),
                    Map.entry(
                            Operation.CONSENT_TERM_READ,
                            "usecase.privacy.GetCurrentConsentTermUseCase (feature 007)"),
                    Map.entry(
                            Operation.CONSENT_TERM_PUBLISH,
                            "usecase.privacy.PublishConsentTermUseCase (feature 007)"),
                    Map.entry(
                            Operation.ERASURE_REQUEST_REGISTER,
                            "usecase.privacy.RegisterErasureRequestUseCase (feature 007)"),
                    Map.entry(
                            Operation.ERASURE_REQUEST_READ,
                            "usecase.privacy.ListErasureRequestsUseCase (feature 007)"),
                    Map.entry(
                            Operation.ERASURE_PREVIEW,
                            "usecase.privacy.PreviewErasureUseCase (feature 007)"),
                    Map.entry(
                            Operation.ERASURE_CONFIRM,
                            "usecase.privacy.ConfirmErasureUseCase (feature 007)"),
                    Map.entry(
                            Operation.ERASURE_RECEIPT_READ,
                            "usecase.privacy.GetErasureReceiptUseCase (feature 007)"),
                    Map.entry(
                            Operation.OPERATIONS_COST_READ,
                            "usecase.operations.GetMonthlyCostReportUseCase (feature 007)"),
                    Map.entry(
                            Operation.OPERATIONS_ALERT_READ,
                            "usecase.operations.ListOperationalAlertsUseCase (feature 007)"),
                    Map.entry(
                            Operation.OPERATIONS_ALERT_ACK,
                            "usecase.operations.AcknowledgeOperationalAlertUseCase (feature 007)"));

    // ------------------------------------------------------------------ mapa caso de uso →
    // operação

    /** Como o caso de uso se liga à matriz. */
    private sealed interface Binding permits Single, Draft, PublicEntry, Infrastructure {}

    /** Uma operação da matriz; o primeiro argumento é o ator. */
    private record Single(Operation operation) implements Binding {}

    /**
     * Cadastro em andamento do Telegram: existe só para o professor no Telegram; a operação da
     * matriz depende do tipo do rascunho (cadastrar, editar, inativar ou reativar).
     */
    private record Draft(Set<Operation> operations) implements Binding {}

    /** Entrada pública: não recebe ator, e a matriz a declara {@code PUBLIC}. */
    private record PublicEntry(Operation operation) implements Binding {}

    /** Validação da credencial que antecede a criação do ator; não é uma operação da matriz. */
    private record Infrastructure(String reason) implements Binding {}

    private static final Set<Operation> DRAFT_OPERATIONS =
            EnumSet.of(
                    Operation.STUDENT_REGISTER,
                    Operation.STUDENT_UPDATE,
                    Operation.STUDENT_INACTIVATE,
                    Operation.STUDENT_REACTIVATE);

    private static final Map<String, Binding> BINDINGS = bindings();

    private static Map<String, Binding> bindings() {
        Map<String, Binding> map = new java.util.TreeMap<>();
        map.put(
                "administrator.ActivateAdministratorUseCase",
                new PublicEntry(Operation.ADMIN_ACTIVATE));
        map.put(
                "administrator.BootstrapFirstAdministratorUseCase",
                new Single(Operation.FIRST_ADMINISTRATOR_BOOTSTRAP));
        map.put(
                "administrator.ChangeProvisionalPasswordUseCase",
                new Single(Operation.ADMIN_CHANGE_PASSWORD));
        map.put(
                "administrator.CreateAdministratorUseCase",
                new Single(Operation.ADMINISTRATOR_CREATE));
        map.put(
                "administrator.GetAdministratorSessionUseCase",
                new Single(Operation.ADMIN_GET_SESSION));
        map.put(
                "administrator.InactivateAdministratorUseCase",
                new Single(Operation.ADMINISTRATOR_INACTIVATE));
        map.put(
                "administrator.ListAdministratorsUseCase",
                new Single(Operation.ADMINISTRATOR_LIST));
        map.put(
                "administrator.ReactivateAdministratorUseCase",
                new Single(Operation.ADMINISTRATOR_REACTIVATE));
        map.put(
                "administrator.SignInAdministratorUseCase",
                new PublicEntry(Operation.ADMIN_SIGN_IN));
        map.put("administrator.SignOutAdministratorUseCase", new Single(Operation.ADMIN_SIGN_OUT));
        map.put(
                "administrator.ValidateAdministratorSessionUseCase",
                new Infrastructure("valida o cookie e entrega o ator; a matriz vale depois dele"));
        map.put("teacher.GetTeacherUseCase", new Single(Operation.TEACHER_GET));
        map.put("teacher.InactivateTeacherUseCase", new Single(Operation.TEACHER_INACTIVATE));
        map.put("teacher.ListTeachersUseCase", new Single(Operation.TEACHER_LIST));
        map.put("teacher.ReactivateTeacherUseCase", new Single(Operation.TEACHER_REACTIVATE));
        map.put("teacher.RegisterTeacherUseCase", new Single(Operation.TEACHER_REGISTER));
        map.put(
                "teacher.ReissueAccessCodeUseCase",
                new Single(Operation.TEACHER_REISSUE_ACCESS_CODE));
        map.put(
                "teacher.UnlinkTeacherTelegramUseCase",
                new Single(Operation.TEACHER_UNLINK_TELEGRAM));
        map.put("teacher.UpdateTeacherUseCase", new Single(Operation.TEACHER_UPDATE));
        map.put("registry.AssignTeacherToStudentUseCase", new Single(Operation.ASSIGNMENT_ASSIGN));
        map.put("registry.CancelStudentRegistrationUseCase", new Draft(DRAFT_OPERATIONS));
        map.put("registry.CollectStudentRegistrationFieldUseCase", new Draft(DRAFT_OPERATIONS));
        map.put("registry.ConfirmStudentRegistrationUseCase", new Draft(DRAFT_OPERATIONS));
        map.put("registry.GetStudentUseCase", new Single(Operation.STUDENT_GET));
        map.put("registry.InactivateStudentUseCase", new Single(Operation.STUDENT_INACTIVATE));
        map.put("registry.ListStudentsUseCase", new Single(Operation.STUDENT_LIST));
        map.put(
                "registry.PreviewAssignmentRemovalUseCase",
                new Single(Operation.ASSIGNMENT_PREVIEW_REMOVAL));
        map.put("registry.ReactivateStudentUseCase", new Single(Operation.STUDENT_REACTIVATE));
        map.put("registry.RegisterStudentUseCase", new Single(Operation.STUDENT_REGISTER));
        map.put(
                "registry.RemoveTeacherFromStudentUseCase",
                new Single(Operation.ASSIGNMENT_REMOVE));
        map.put("registry.StartStudentRegistrationUseCase", new Draft(DRAFT_OPERATIONS));
        map.put("registry.UpdateStudentUseCase", new Single(Operation.STUDENT_UPDATE));
        return map;
    }

    // ------------------------------------------------------------------ matriz do contrato

    private record Line(
            Operation operation,
            Permission administratorWeb,
            Permission teacherWeb,
            Permission teacherTelegram,
            Set<ActionOrigin> system) {}

    private static final Map<Operation, Line> MATRIX = readMatrix();

    private static Map<Operation, Line> readMatrix() {
        try {
            String json = Files.readString(CONTRACT, StandardCharsets.UTF_8);
            Map<Operation, Line> lines = new EnumMap<>(Operation.class);
            Matcher matcher = OPERATION_OBJECT.matcher(json);
            while (matcher.find()) {
                String body = matcher.group(2);
                Operation operation = Operation.valueOf(matcher.group(1));
                lines.put(
                        operation,
                        new Line(
                                operation,
                                Permission.valueOf(text(body, "administratorWeb")),
                                Permission.valueOf(text(body, "teacherWeb")),
                                Permission.valueOf(text(body, "teacherTelegram")),
                                origins(body)));
            }
            return lines;
        } catch (IOException failure) {
            throw new IllegalStateException("matriz não encontrada em " + CONTRACT, failure);
        }
    }

    private static String text(String body, String field) {
        Matcher matcher = Pattern.compile("\"" + field + "\"\\s*:\\s*\"(\\w+)\"").matcher(body);
        if (!matcher.find()) {
            throw new IllegalStateException("campo ausente na matriz: " + field);
        }
        return matcher.group(1);
    }

    private static Set<ActionOrigin> origins(String body) {
        Matcher matcher = Pattern.compile("\"system\"\\s*:\\s*\\[([^\\]]*)\\]").matcher(body);
        Set<ActionOrigin> origins = EnumSet.noneOf(ActionOrigin.class);
        if (matcher.find()) {
            Matcher name = Pattern.compile("\"(\\w+)\"").matcher(matcher.group(1));
            while (name.find()) {
                origins.add(ActionOrigin.valueOf(name.group(1)));
            }
        }
        return origins;
    }

    // ------------------------------------------------------------------ atores

    private record Caller(String name, Actor actor, Function<Line, Permission> expected) {

        @Override
        public String toString() {
            return name;
        }
    }

    private static List<Caller> callers() {
        return List.of(
                new Caller(
                        "administrador na Web",
                        ActorFixtures.administratorWeb(),
                        Line::administratorWeb),
                new Caller("professor na Web", ActorFixtures.teacherWeb(), Line::teacherWeb),
                new Caller(
                        "professor no Telegram",
                        ActorFixtures.teacherTelegram(),
                        Line::teacherTelegram),
                new Caller(
                        "sistema na inicialização",
                        ActorFixtures.bootstrap(),
                        line -> systemPermission(line, ActionOrigin.SYSTEM_STARTUP)),
                new Caller(
                        "sistema na carga inicial",
                        ActorFixtures.initialLoad(),
                        line -> systemPermission(line, ActionOrigin.INITIAL_LOAD)));
    }

    private static Permission systemPermission(Line line, ActionOrigin origin) {
        return line.system().contains(origin) ? Permission.ALLOW : Permission.DENY;
    }

    // ------------------------------------------------------------------ descoberta

    private static List<Class<?>> useCases() {
        List<Class<?>> classes = new ArrayList<>();
        for (JavaClass type :
                new ClassFileImporter().importPackages(PACKAGES.toArray(String[]::new))) {
            if (type.getSimpleName().endsWith("UseCase") && !type.isNestedClass()) {
                classes.add(type.reflect());
            }
        }
        classes.sort(Comparator.comparing(Class::getName));
        return classes;
    }

    private static String key(Class<?> useCase) {
        return useCase.getName().substring(BASE.length());
    }

    @Test
    void theContractListsEveryOperationOfTheCore() {
        assertThat(MATRIX.keySet()).containsExactlyInAnyOrder(Operation.values());
    }

    @Test
    void everyUseCaseHasAnOperationMappedInTheMatrix() {
        List<String> found = useCases().stream().map(UseCasePermissionMatrixTest::key).toList();

        assertThat(found)
                .as("caso de uso sem operação mapeada (acrescente-o a BINDINGS)")
                .isNotEmpty()
                .allSatisfy(name -> assertThat(BINDINGS).containsKey(name));
        assertThat(BINDINGS.keySet())
                .as("mapeamento de caso de uso que não existe mais")
                .allSatisfy(name -> assertThat(found).contains(name));
    }

    @Test
    void everyOperationOfTheMatrixHasAUseCaseOrAnExplicitOtherHome() {
        Set<Operation> covered = EnumSet.noneOf(Operation.class);
        BINDINGS.values()
                .forEach(
                        binding -> {
                            if (binding instanceof Single single) {
                                covered.add(single.operation());
                            } else if (binding instanceof PublicEntry entry) {
                                covered.add(entry.operation());
                            }
                        });
        covered.addAll(OPERATIONS_ELSEWHERE.keySet());

        assertThat(covered)
                .as("operação da matriz sem caso de uso que a execute")
                .containsExactlyInAnyOrder(Operation.values());
    }

    @Test
    void publicEntriesAreDeclaredPublicAndTakeNoActor() {
        BINDINGS.forEach(
                (name, binding) -> {
                    if (binding instanceof PublicEntry entry) {
                        Line line = MATRIX.get(entry.operation());
                        assertThat(line.administratorWeb()).as(name).isEqualTo(Permission.PUBLIC);
                        assertThat(line.teacherWeb()).as(name).isEqualTo(Permission.PUBLIC);
                        assertThat(line.teacherTelegram()).as(name).isEqualTo(Permission.DENY);
                        assertThat(line.system()).as(name).isEmpty();
                        Class<?> useCase = useCaseNamed(name);
                        for (Method method : runMethods(useCase)) {
                            assertThat(method.getParameterTypes())
                                    .as("%s.%s não recebe ator", name, method.getName())
                                    .doesNotContain(Actor.class);
                        }
                        assertThat(constructorOf(useCase).getParameterTypes())
                                .as("%s não consulta a matriz", name)
                                .doesNotContain(ActorAuthorizationService.class);
                    }
                });
    }

    private static Class<?> useCaseNamed(String name) {
        return useCases().stream().filter(c -> key(c).equals(name)).findFirst().orElseThrow();
    }

    // ------------------------------------------------------------------ a matriz no caso de uso

    static Stream<Arguments> useCasesAndCallers() {
        List<Arguments> arguments = new ArrayList<>();
        for (Class<?> useCase : useCases()) {
            Binding binding = BINDINGS.get(key(useCase));
            if (binding instanceof Single || binding instanceof Draft) {
                for (Caller caller : callers()) {
                    arguments.add(Arguments.of(useCase.getSimpleName(), useCase, caller));
                }
            }
        }
        return arguments.stream();
    }

    @ParameterizedTest(name = "{0} com {2}")
    @MethodSource("useCasesAndCallers")
    void eachUseCaseAppliesTheMatrixToEachActor(String ignored, Class<?> useCase, Caller caller) {
        Binding binding = BINDINGS.get(key(useCase));
        Harness harness = new Harness();
        Object instance = harness.instantiate(useCase);

        boolean denied = isDenied(binding, caller);

        for (Method method : runMethods(useCase)) {
            Throwable thrown = invoke(instance, method, caller.actor());
            if (denied) {
                assertThat(thrown)
                        .as("%s.%s para %s", useCase.getSimpleName(), method.getName(), caller)
                        .isInstanceOf(DomainException.class);
                assertThat(((DomainException) thrown).code())
                        .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
            } else if (binding instanceof Single single) {
                assertThat(harness.authorization.granted)
                        .as(
                                "%s.%s para %s deveria passar pela matriz em %s",
                                useCase.getSimpleName(),
                                method.getName(),
                                caller,
                                single.operation())
                        .contains(single.operation());
                assertThat(harness.authorization.denied).isEmpty();
            } else {
                // cadastro em andamento: ou passou pela matriz, ou seguiu adiante sem a negativa
                boolean reached =
                        !harness.authorization.granted.isEmpty()
                                || !(thrown instanceof DomainException domain
                                        && domain.code() == ErrorCode.RESOURCE_NOT_FOUND);
                assertThat(reached)
                        .as("%s.%s para %s", useCase.getSimpleName(), method.getName(), caller)
                        .isTrue();
            }
            harness.authorization.reset();
        }

        if (denied) {
            assertThat(harness.touchedPorts)
                    .as("%s negado a %s não toca porta alguma", useCase.getSimpleName(), caller)
                    .isEmpty();
            assertThat(harness.recorder.records())
                    .as("a negativa só deixa o registro AUTHORIZATION_DENIED")
                    .allSatisfy(
                            record ->
                                    assertThat(record.operation())
                                            .isEqualTo(AuthorshipRecord.AUTHORIZATION_DENIED));
        }
    }

    /**
     * Negado quando a matriz diz {@code DENY}. O cadastro em andamento vale só para o professor no
     * Telegram (o rascunho é da conversa dele) e exige as quatro operações de aluno.
     */
    private static boolean isDenied(Binding binding, Caller caller) {
        if (binding instanceof Single single) {
            return !caller.expected().apply(MATRIX.get(single.operation())).allowed();
        }
        Draft draft = (Draft) binding;
        boolean telegramTeacher =
                caller.actor().role() != null && caller.actor().origin() == ActionOrigin.TELEGRAM;
        return !telegramTeacher
                || draft.operations().stream()
                        .anyMatch(op -> !caller.expected().apply(MATRIX.get(op)).allowed());
    }

    private static List<Method> runMethods(Class<?> useCase) {
        return Arrays.stream(useCase.getDeclaredMethods())
                .filter(m -> Modifier.isPublic(m.getModifiers()) && !m.isSynthetic())
                .filter(m -> m.getName().startsWith("run"))
                .sorted(Comparator.comparing(Method::getName))
                .toList();
    }

    private static Constructor<?> constructorOf(Class<?> type) {
        return Arrays.stream(type.getConstructors())
                .max(Comparator.comparingInt(Constructor::getParameterCount))
                .orElseThrow(() -> new IllegalStateException("sem construtor público: " + type));
    }

    /** Chama o método e devolve o que ele lançou, ou nulo. Limite de tempo contra laços. */
    private static Throwable invoke(Object instance, Method method, Actor actor) {
        Object[] arguments = new Object[method.getParameterCount()];
        Class<?>[] types = method.getParameterTypes();
        for (int i = 0; i < types.length; i++) {
            arguments[i] = types[i] == Actor.class ? actor : defaultValue(types[i]);
        }
        return assertTimeoutPreemptively(
                Duration.ofSeconds(10),
                () -> {
                    try {
                        method.invoke(instance, arguments);
                        return null;
                    } catch (InvocationTargetException failure) {
                        return failure.getCause();
                    }
                });
    }

    private static Object defaultValue(Class<?> type) {
        if (type == UUID.class) {
            return UUID.fromString("00000000-0000-4000-8000-0000000000c1");
        }
        if (type == String.class) {
            return "x";
        }
        if (type == Secret.class) {
            return new Secret("x");
        }
        if (type == Instant.class) {
            return Instant.parse("2026-09-21T13:00:00Z");
        }
        if (type == LocalDate.class) {
            return LocalDate.of(2026, 9, 21);
        }
        if (type == boolean.class || type == Boolean.class) {
            return false;
        }
        if (type == int.class || type == Integer.class) {
            return 0;
        }
        if (type == long.class || type == Long.class) {
            return 0L;
        }
        if (type.isEnum()) {
            return type.getEnumConstants()[0];
        }
        if (type == List.class) {
            return List.of();
        }
        if (type.isRecord()) {
            RecordComponent[] components = type.getRecordComponents();
            Class<?>[] parameterTypes =
                    Arrays.stream(components)
                            .map(RecordComponent::getType)
                            .toArray(Class<?>[]::new);
            Object[] values =
                    Arrays.stream(parameterTypes)
                            .map(UseCasePermissionMatrixTest::defaultValue)
                            .toArray();
            try {
                Constructor<?> canonical = type.getDeclaredConstructor(parameterTypes);
                canonical.setAccessible(true);
                return canonical.newInstance(values);
            } catch (ReflectiveOperationException | RuntimeException failure) {
                return null;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ montagem do caso de uso

    /** Consulta a matriz de verdade e guarda o que foi concedido e o que foi negado. */
    private static final class RecordingAuthorization extends ActorAuthorizationService {

        final List<Operation> granted = new ArrayList<>();
        final List<Operation> denied = new ArrayList<>();

        RecordingAuthorization() {
            super(PermissionMatrix.standard());
        }

        @Override
        public Permission require(Actor actor, Operation operation) {
            try {
                Permission permission = super.require(actor, operation);
                granted.add(operation);
                return permission;
            } catch (DomainException refusal) {
                denied.add(operation);
                throw refusal;
            }
        }

        void reset() {
            granted.clear();
            denied.clear();
        }
    }

    /**
     * Monta qualquer caso de uso com portas vazias que contam as chamadas. Os serviços do núcleo
     * (classes) são os verdadeiros, montados sobre essas portas; o relógio e a autoria são os
     * dublês do núcleo.
     */
    private static final class Harness {

        final RecordingAuthorization authorization = new RecordingAuthorization();
        final FakeAuthorshipRecorder recorder = new FakeAuthorshipRecorder();
        final List<String> touchedPorts = new ArrayList<>();
        private final ClockPort clock = new FixedClock();
        private final Map<Class<?>, Object> instances = new HashMap<>();

        Object instantiate(Class<?> type) {
            if (type == ClockPort.class) {
                return clock;
            }
            if (type == AuthorshipRecorderPort.class) {
                return recorder;
            }
            if (type == ActorAuthorizationService.class) {
                return authorization;
            }
            if (type == BusinessParameters.class) {
                return BusinessParameters.defaults();
            }
            if (type == Duration.class) {
                return Duration.ofMinutes(30);
            }
            if (type == Map.class) {
                // política por escopo do serviço de tentativas: vazia, vale a padrão
                return Map.of();
            }
            Object existing = instances.get(type);
            if (existing != null) {
                return existing;
            }
            Object created;
            if (type == AuthorshipService.class) {
                created = new AuthorshipService(recorder, clock);
            } else if (type.isInterface()) {
                if (!type.getPackageName().equals("br.com.felipeblaft.reports.port")) {
                    throw new IllegalStateException("dependência que não é porta: " + type);
                }
                created = emptyPort(type);
            } else {
                created = construct(type);
            }
            instances.put(type, created);
            return created;
        }

        private Object construct(Class<?> type) {
            Constructor<?> constructor = constructorOf(type);
            Object[] arguments =
                    Arrays.stream(constructor.getParameterTypes()).map(this::instantiate).toArray();
            try {
                return constructor.newInstance(arguments);
            } catch (ReflectiveOperationException failure) {
                throw new IllegalStateException("não foi possível montar " + type, failure);
            }
        }

        private Object emptyPort(Class<?> port) {
            return Proxy.newProxyInstance(
                    port.getClassLoader(),
                    new Class<?>[] {port},
                    (proxy, method, arguments) -> {
                        if (method.getDeclaringClass() == Object.class) {
                            return method.getName().equals("toString")
                                    ? port.getSimpleName()
                                    : null;
                        }
                        touchedPorts.add(port.getSimpleName() + "." + method.getName());
                        return emptyValue(method.getReturnType());
                    });
        }

        private static Object emptyValue(Class<?> type) {
            if (type == Optional.class) {
                return Optional.empty();
            }
            if (type == List.class) {
                return List.of();
            }
            if (type == Set.class) {
                return Set.of();
            }
            if (type == Map.class) {
                return Map.of();
            }
            if (type == boolean.class) {
                return false;
            }
            if (type == int.class) {
                return 0;
            }
            if (type == long.class) {
                return 0L;
            }
            return null;
        }
    }
}
