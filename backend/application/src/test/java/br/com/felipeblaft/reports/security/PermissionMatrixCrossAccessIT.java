package br.com.felipeblaft.reports.security;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.felipeblaft.reports.domain.access.Operation;
import br.com.felipeblaft.reports.domain.access.Permission;
import br.com.felipeblaft.reports.domain.access.PermissionMatrix;
import br.com.felipeblaft.reports.domain.registry.Student;
import br.com.felipeblaft.reports.domain.registry.Teacher;
import br.com.felipeblaft.reports.support.AdministratorFixture;
import br.com.felipeblaft.reports.support.MatrixLoader;
import br.com.felipeblaft.reports.support.MatrixLoader.Route;
import br.com.felipeblaft.reports.support.MatrixLoader.Row;
import br.com.felipeblaft.reports.support.ScenarioIT;
import jakarta.servlet.http.Cookie;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Acesso cruzado gerado da matriz (T048 e T218, SC-005): para cada operação de {@code
 * permission-matrix.json} e para cada ator (administrador na Web, professor na Web, professor no
 * Telegram, sem credencial), chama a rota ou o comando <b>reais</b> (Postgres, cadeias de segurança
 * e casos de uso verdadeiros) e compara o resultado com a decisão da matriz. Permitido é "a
 * operação respondeu"; negado é a resposta de rota inexistente ({@code 404 RESOURCE_NOT_FOUND}), o
 * {@code 401} sem credencial na Web e a negativa neutra no Telegram.
 *
 * <p><b>Regra do Fecho: zero linhas puladas.</b> Toda rota da matriz precisa de sonda (e a que não
 * tem, reprova), toda operação permitida a um ator precisa de rota no espaço dele ({@code /admin}
 * para o administrador, o restante para o professor) e toda operação do Telegram precisa de comando.
 * Uma rota da matriz sem sonda mapeada aqui não é pulada: é falha.
 */
class PermissionMatrixCrossAccessIT extends SecurityBase {

    private static final MatrixLoader.Matrix MATRIX = MatrixLoader.load();
    private static final long USER_ANONYMOUS = 7009L;
    private static final long USER_DORA = USER_ANONYMOUS;
    private static final String CODE_DORA = "DRKA2XPA";

    /** Rotas da matriz sem caminho fixo e as sondas concretas que as representam. */
    private static final Map<String, List<String>> WILDCARDS =
            Map.of(
                    "/students/{studentId}/**",
                    List.of(
                            "GET /students/{studentId}/open-report",
                            "GET /students/{studentId}/history",
                            "GET /students/{studentId}/semesters"),
                    "/lesson-reports/**",
                    List.of("GET /lesson-reports/{lessonId}"),
                    "/semester-reports/**",
                    List.of("GET /semester-reports/{semesterId}"),
                    "/jobs/**",
                    List.of("GET /jobs/{jobId}"));

    /**
     * O que a pessoa escreveria no bot para tentar cada operação. Para as operações que existem no
     * Telegram, é o comando verdadeiro; para as administrativas, é o que o administrador faria na
     * Web (que o bot não conhece).
     */
    private static final Map<Operation, List<String>> TELEGRAM_PROBES =
            Map.ofEntries(
                    Map.entry(Operation.ADMIN_SIGN_IN, List.of("entrar como administrador")),
                    Map.entry(Operation.ADMIN_ACTIVATE, List.of("ativar conta de administrador")),
                    Map.entry(Operation.ADMIN_GET_SESSION, List.of("ver minha sessão administrativa")),
                    Map.entry(Operation.ADMIN_SIGN_OUT, List.of("encerrar sessão administrativa")),
                    Map.entry(Operation.ADMIN_CHANGE_PASSWORD, List.of("trocar a senha provisória")),
                    Map.entry(Operation.TEACHER_SIGN_IN, List.of(CODE_DORA)),
                    Map.entry(Operation.TEACHER_LIST, List.of("listar professores")),
                    Map.entry(Operation.TEACHER_GET, List.of("ver o professor Bruno Lima")),
                    Map.entry(Operation.TEACHER_REGISTER, List.of("criar professor")),
                    Map.entry(Operation.TEACHER_UPDATE, List.of("editar professor")),
                    Map.entry(Operation.TEACHER_INACTIVATE, List.of("inativar professor")),
                    Map.entry(Operation.TEACHER_REACTIVATE, List.of("reativar professor")),
                    Map.entry(Operation.TEACHER_REISSUE_ACCESS_CODE, List.of("reemitir código")),
                    Map.entry(
                            Operation.TEACHER_UNLINK_TELEGRAM,
                            List.of("desvincular o telegram de um professor")),
                    Map.entry(Operation.ADMINISTRATOR_LIST, List.of("listar administradores")),
                    Map.entry(Operation.ADMINISTRATOR_CREATE, List.of("criar administrador")),
                    Map.entry(Operation.ADMINISTRATOR_INACTIVATE, List.of("inativar administrador")),
                    Map.entry(Operation.ADMINISTRATOR_REACTIVATE, List.of("reativar administrador")),
                    Map.entry(
                            Operation.FIRST_ADMINISTRATOR_BOOTSTRAP,
                            List.of("criar o primeiro administrador")),
                    Map.entry(Operation.ASSIGNMENT_ASSIGN, List.of("associar professor a aluno")),
                    Map.entry(Operation.ASSIGNMENT_REMOVE, List.of("retirar professor de aluno")),
                    Map.entry(
                            Operation.ASSIGNMENT_PREVIEW_REMOVAL,
                            List.of("prévia da retirada de professor")),
                    // operações do professor: o comando verdadeiro (o primeiro passo)
                    Map.entry(Operation.STUDENT_LIST, List.of("meus alunos")),
                    Map.entry(Operation.STUDENT_GET, List.of("meus alunos")),
                    Map.entry(Operation.STUDENT_REGISTER, List.of("cadastrar aluno")),
                    Map.entry(Operation.STUDENT_UPDATE, List.of("meus alunos")),
                    Map.entry(Operation.STUDENT_INACTIVATE, List.of("meus alunos")),
                    Map.entry(Operation.STUDENT_REACTIVATE, List.of("meus alunos")),
                    Map.entry(
                            Operation.LESSON_AND_SEMESTER_REPORTS,
                            List.of("histórico", "relatório semestral")));

    private enum Outcome {
        REACHED,
        DENIED_404,
        DENIED_401,
        SERVER_ERROR
    }

    /** Dados do cenário de uma operação. */
    private record Fx(
            Cookie administrator,
            Cookie clara,
            Map<String, String> variables,
            Teacher bruno,
            Student claraStudent) {}

    static Stream<Arguments> operations() {
        return MATRIX.rows().stream().map(row -> Arguments.of(row.operation()));
    }

    @Test
    void theMatrixOfTheContractIsTheMatrixOfTheCore() {
        PermissionMatrix core = PermissionMatrix.standard();
        assertThat(MATRIX.rows()).extracting(Row::operation).containsExactlyInAnyOrder(Operation.values());
        assertThat(core.rules().keySet()).containsExactlyInAnyOrder(Operation.values());
    }

    @Test
    void everyOperationOfTheMatrixHasATelegramProbe() {
        assertThat(TELEGRAM_PROBES.keySet())
                .as("operação da matriz sem comando mapeado para o Telegram (SC-005: zero puladas)")
                .containsExactlyInAnyOrder(Operation.values());
    }

    @Test
    void everyWebRouteOfTheMatrixHasAProbe() {
        List<String> unmapped = new ArrayList<>();
        for (Row row : MATRIX.rows()) {
            for (Route route : row.webRoutes()) {
                if (route.path().contains("**") && !WILDCARDS.containsKey(route.path())) {
                    unmapped.add(row.operation() + " " + route.path());
                }
            }
            boolean allowedOnWeb =
                    row.administratorWeb().allowed() || row.teacherWeb().allowed();
            if (allowedOnWeb && row.webRoutes().isEmpty()) {
                unmapped.add(row.operation() + " permitida na Web e sem rota");
            }
        }
        assertThat(unmapped).as("rotas da matriz sem sonda").isEmpty();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("operations")
    void eachActorGetsWhatTheMatrixSays(Operation operation) throws Exception {
        Row row = MATRIX.row(operation);
        Fx fx = prepare(operation);
        List<String> failures = new ArrayList<>();

        // permitido a um ator exige rota no espaço dele (zero puladas)
        requireRouteFor("administrador", row.administratorWeb(), row, true, failures);
        requireRouteFor("professor", row.teacherWeb(), row, false, failures);

        Map<String, Integer> before = snapshot();

        // primeiro tudo o que deve ser negado (não muda nada), depois o que deve responder
        webChecks(row, fx, false, failures);
        telegramChecks(row, fx, false, failures);
        assertThat(snapshot()).as("a negativa não altera dado algum").isEqualTo(before);
        webChecks(row, fx, true, failures);
        telegramChecks(row, fx, true, failures);

        assertThat(failures).as("divergências da matriz em %s", operation).isEmpty();
    }

    // ------------------------------------------------------------------ cenário

    private Fx prepare(Operation operation) throws Exception {
        Teacher clara = scenario.clara();
        Teacher bruno = scenario.bruno();
        scenario.teacher("Dora Tavares", "dora@example.test", CODE_DORA);
        Student claraStudent = scenario.student(clara, "Matteo Mafra", "Piano");
        Student brunoStudent = scenario.student(bruno, "Helena Prado", "Flauta");
        link(USER_CLARA, ScenarioIT.CODE_CLARA);
        link(USER_BRUNO, ScenarioIT.CODE_BRUNO);

        Cookie administrator = signInAdministrator();
        AdministratorFixture second =
                AdministratorFixture.insertActive(
                        administrators,
                        hasher,
                        clock.now(),
                        "Segundo Administrador",
                        "second.administrator@example.test");
        Cookie claraCookie = signInTeacher(ScenarioIT.CODE_CLARA);

        Map<String, String> variables = new HashMap<>();
        boolean pairOfBruno =
                operation == Operation.ASSIGNMENT_REMOVE
                        || operation == Operation.ASSIGNMENT_PREVIEW_REMOVAL;
        variables.put("studentId", (pairOfBruno ? brunoStudent : claraStudent).id().toString());
        variables.put("teacherId", bruno.id().toString());
        variables.put("administratorId", second.administrator().id().toString());

        if (operation == Operation.ADMINISTRATOR_REACTIVATE) {
            jdbc.update(
                    "update administrator set active = false where id = ?",
                    second.administrator().id());
        }
        if (operation == Operation.STUDENT_REACTIVATE) {
            jdbc.update(
                    "update student set active = false where id = ?",
                    claraStudent.id());
        }
        if (operation == Operation.LESSON_AND_SEMESTER_REPORTS) {
            variables.put(
                    "lessonId",
                    scenario.approvedLesson(
                                    clara,
                                    claraStudent,
                                    LocalDate.of(2026, 3, 10),
                                    "Sopro com apoio diafragmático")
                            .id()
                            .toString());
            MvcResult generation =
                    call(
                            HttpMethod.POST,
                            API + "/students/" + claraStudent.id() + "/semester-reports",
                            "{\"periodKey\":\"2026-1\"}",
                            claraCookie);
            variables.put("jobId", jsonOf(generation).path("jobId").asString());
            scenario.processQueue();
            variables.put(
                    "semesterId", jdbc.queryForObject("select id from semester_report", String.class));
        }
        API_BOTS.resetRequests();
        return new Fx(administrator, claraCookie, variables, bruno, claraStudent);
    }

    private Map<String, Integer> snapshot() {
        Map<String, Integer> counts = new java.util.TreeMap<>();
        for (String table :
                List.of(
                        "administrator",
                        "teacher",
                        "student",
                        "teacher_student_assignment",
                        "lesson_report",
                        "semester_report")) {
            counts.put(table, count(table));
        }
        counts.put("inactive students", jdbc.queryForObject("select count(*) from student where not active", Integer.class));
        counts.put("inactive teachers", jdbc.queryForObject("select count(*) from teacher where not active", Integer.class));
        return counts;
    }

    // ------------------------------------------------------------------ Web

    private static boolean isAdministratorRoute(Route route) {
        return route.path().startsWith("/admin");
    }

    private void requireRouteFor(
            String actor, Permission permission, Row row, boolean administrator, List<String> failures) {
        if (permission == Permission.DENY || permission == Permission.PUBLIC) {
            return;
        }
        boolean mapped =
                row.webRoutes().stream()
                        .anyMatch(route -> isAdministratorRoute(route) == administrator);
        if (!mapped) {
            failures.add(
                    row.operation()
                            + " é permitida ao "
                            + actor
                            + " mas não tem rota Web no espaço dele");
        }
    }

    private void webChecks(Row row, Fx fx, boolean reaching, List<String> failures)
            throws Exception {
        for (Route declared : row.webRoutes()) {
            for (Route probe : expand(declared, failures)) {
                String path = API + probe.pathWith(fx.variables());
                String body = body(probe);
                boolean administratorSpace = isAdministratorRoute(declared);

                checkWeb(row, "administrador", row.administratorWeb(), administratorSpace, probe, path, body, fx.administrator(), reaching, failures);
                checkWeb(row, "professor", row.teacherWeb(), !administratorSpace, probe, path, body, fx.clara(), reaching, failures);
                // sem credencial: só a operação pública responde; o resto é 401
                boolean publicOperation = row.administratorWeb() == Permission.PUBLIC || row.teacherWeb() == Permission.PUBLIC;
                Outcome expected = publicOperation ? Outcome.REACHED : Outcome.DENIED_401;
                if ((expected == Outcome.REACHED) == reaching) {
                    MvcResult result = call(HttpMethod.valueOf(probe.method()), path, body);
                    Outcome actual = classify(result);
                    if (actual != expected) {
                        failures.add(
                                describe(row, "sem credencial", probe, path, expected, actual, result));
                    }
                }
            }
        }
    }

    @SuppressWarnings("java:S107")
    private void checkWeb(
            Row row,
            String actor,
            Permission permission,
            boolean ownSpace,
            Route probe,
            String path,
            String body,
            Cookie cookie,
            boolean reaching,
            List<String> failures)
            throws Exception {
        Outcome expected;
        if (permission == Permission.PUBLIC) {
            expected = Outcome.REACHED;
        } else if (permission.allowed() && ownSpace) {
            expected = Outcome.REACHED;
        } else {
            // credencial de um papel na rota do outro, ou operação negada ao papel: rota inexistente
            expected = Outcome.DENIED_404;
        }
        if ((expected == Outcome.REACHED) != reaching) {
            return;
        }
        MvcResult result = call(HttpMethod.valueOf(probe.method()), path, body, cookie);
        Outcome actual = classify(result);
        if (actual != expected) {
            failures.add(describe(row, actor, probe, path, expected, actual, result));
            return;
        }
        if (expected == Outcome.DENIED_404) {
            String baseline = path.startsWith(API + "/admin") ? API + "/admin/nao-existe" : API + "/nao-existe";
            MvcResult unknown = call(HttpMethod.GET, baseline, null, cookie);
            if (!bodyOf(result).equals(bodyOf(unknown))
                    || !String.valueOf(result.getResponse().getContentType())
                            .equals(String.valueOf(unknown.getResponse().getContentType()))) {
                failures.add(
                        row.operation()
                                + " "
                                + actor
                                + ": a negativa difere da resposta de rota inexistente em "
                                + probe.method()
                                + " "
                                + probe.path());
            }
        }
    }

    private List<Route> expand(Route declared, List<String> failures) {
        if (declared.path().contains("**")) {
            List<String> concrete = WILDCARDS.get(declared.path());
            if (concrete == null) {
                failures.add("rota sem sonda mapeada: " + declared.path());
                return List.of();
            }
            return concrete.stream().map(Route::parse).toList();
        }
        if (declared.anyMethod()) {
            return List.of(new Route("GET", declared.path()));
        }
        return List.of(declared);
    }

    private static Outcome classify(MvcResult result) {
        int status = result.getResponse().getStatus();
        String code = errorCode(result);
        if (status >= 500) {
            return Outcome.SERVER_ERROR;
        }
        if (status == 404 && code.equals("RESOURCE_NOT_FOUND")) {
            return Outcome.DENIED_404;
        }
        if (status == 401 && Set.of("NOT_AUTHENTICATED", "SESSION_EXPIRED").contains(code)) {
            return Outcome.DENIED_401;
        }
        return Outcome.REACHED;
    }

    private static String describe(
            Row row,
            String actor,
            Route probe,
            String path,
            Outcome expected,
            Outcome actual,
            MvcResult result) {
        return row.operation()
                + " "
                + actor
                + " em "
                + probe.method()
                + " "
                + probe.path()
                + ": esperado "
                + expected
                + " e veio "
                + actual
                + " (status "
                + result.getResponse().getStatus()
                + ", código "
                + errorCode(result)
                + ")";
    }

    /** Corpo válido de cada rota que o exige. */
    private static String body(Route route) {
        return switch (route.method() + " " + route.path()) {
            case "POST /admin/session" ->
                    "{\"email\":\""
                            + AdministratorFixture.DEFAULT_EMAIL
                            + "\",\"password\":\""
                            + AdministratorFixture.DEFAULT_PASSWORD
                            + "\"}";
            case "POST /admin/activation" ->
                    "{\"email\":\"ninguem@example.test\",\"activationCode\":\"AAAA-AAAA-AAAA\",\"newPassword\":\"long-test-password-2\"}";
            case "PUT /admin/password" -> "{\"newPassword\":\"long-test-password-3\"}";
            case "POST /session" -> "{\"code\":\"" + ScenarioIT.CODE_CLARA + "\"}";
            case "POST /admin/teachers" ->
                    "{\"name\":\"Nova Professora\",\"email\":\"nova.professora@example.test\"}";
            case "PATCH /admin/teachers/{teacherId}" -> "{\"name\":\"Bruno Lima Júnior\"}";
            case "POST /admin/teachers/{teacherId}/access-code" -> "{\"endTelegramLink\":false}";
            case "POST /admin/administrators" ->
                    "{\"name\":\"Terceiro Administrador\",\"email\":\"third.administrator@example.test\"}";
            case "POST /admin/students" ->
                    "{\"name\":\"Aluno Novo\",\"birthDate\":\"1990-03-04\",\"cpf\":\"529.982.247-25\","
                            + "\"instrument\":\"Piano\",\"teacherIds\":[]}";
            case "POST /my-students" ->
                    "{\"name\":\"Aluno Novo\",\"birthDate\":\"1990-03-04\",\"cpf\":\"529.982.247-25\","
                            + "\"instrument\":\"Piano\"}";
            case "PATCH /admin/students/{studentId}", "PATCH /my-students/{studentId}" ->
                    "{\"instrument\":\"Violão\"}";
            default -> null;
        };
    }

    // ------------------------------------------------------------------ Telegram

    private void telegramChecks(Row row, Fx fx, boolean reaching, List<String> failures)
            throws Exception {
        List<String> probes = TELEGRAM_PROBES.get(row.operation());
        if (probes == null) {
            failures.add(row.operation() + ": sem comando do Telegram mapeado");
            return;
        }
        Permission permission = row.teacherTelegram();
        boolean teacherReaches = permission.allowed();
        boolean anonymousReaches = permission == Permission.PUBLIC;

        if (!reaching) {
            if (!teacherReaches) {
                for (String probe : probes) {
                    String neutral = neutralReply(USER_CLARA, probe);
                    String actual = say(USER_CLARA, probe).fingerprint().replace(probe, "§");
                    if (!actual.equals(neutral)) {
                        failures.add(
                                row.operation()
                                        + " professor no Telegram: a negativa difere do texto não reconhecido");
                    }
                }
            }
            if (!anonymousReaches) {
                for (String probe : probes) {
                    String orientation = neutralReply(USER_ANONYMOUS, probe);
                    String actual = say(USER_ANONYMOUS, probe).fingerprint().replace(probe, "§");
                    if (!actual.equals(orientation)) {
                        failures.add(
                                row.operation()
                                        + " sem credencial no Telegram: deveria receber só a orientação de entrada");
                    }
                }
            }
            return;
        }

        if (anonymousReaches) {
            int links = count("telegram_link");
            Reply entered = say(USER_ANONYMOUS, probes.getFirst());
            if (isNeutral(entered) || count("telegram_link") != links + 1) {
                failures.add(row.operation() + ": a entrada pública do Telegram não vinculou");
            }
        }
        if (teacherReaches && permission != Permission.PUBLIC) {
            Reply last = teacherScript(row.operation(), fx);
            if (isNeutral(last)) {
                failures.add(
                        row.operation()
                                + " professor no Telegram: o comando foi tratado como não reconhecido (\""
                                + last.plain().replace('\n', '|')
                                + "\")");
            }
            say(USER_CLARA, "cancelar");
        }
    }

    /** A impressão da resposta a um texto sem sentido, com o eco do texto trocado por §. */
    private String neutralReply(long user, String probe) throws Exception {
        String nonsense = "zzqx xqzz kkwv";
        return say(user, nonsense).fingerprint().replace(nonsense, "§");
    }

    /** O comando verdadeiro do professor para a operação; devolve a última resposta. */
    private Reply teacherScript(Operation operation, Fx fx) throws Exception {
        switch (operation) {
            case STUDENT_LIST:
                return say(USER_CLARA, "meus alunos");
            case STUDENT_GET:
                say(USER_CLARA, "meus alunos");
                return tap(USER_CLARA, "v1:reg-open:0");
            case STUDENT_REGISTER:
                return say(USER_CLARA, "cadastrar aluno");
            case STUDENT_UPDATE:
                say(USER_CLARA, "meus alunos");
                tap(USER_CLARA, "v1:reg-open:0");
                return tap(USER_CLARA, "v1:reg-edit:");
            case STUDENT_INACTIVATE:
                say(USER_CLARA, "meus alunos");
                tap(USER_CLARA, "v1:reg-open:0");
                return tap(USER_CLARA, "v1:reg-inactivate:");
            case STUDENT_REACTIVATE:
                say(USER_CLARA, "meus alunos");
                tap(USER_CLARA, "v1:reg-filter:INACTIVE");
                tap(USER_CLARA, "v1:reg-open:0");
                return tap(USER_CLARA, "v1:reg-reactivate:");
            case LESSON_AND_SEMESTER_REPORTS:
                return say(USER_CLARA, "histórico");
            default:
                throw new IllegalStateException("sem roteiro do professor para " + operation);
        }
    }

    /** Resposta de negativa: texto não reconhecido, orientação de entrada ou botão vencido. */
    private static boolean isNeutral(Reply reply) {
        String text = reply.plain();
        return reply.isEmpty()
                || text.contains("Nenhum aluno encontrado")
                || text.contains("envie o código de acesso")
                || text.contains("Este botão não vale mais");
    }
}
