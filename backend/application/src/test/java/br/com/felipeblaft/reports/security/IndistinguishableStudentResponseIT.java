package br.com.felipeblaft.reports.security;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.felipeblaft.reports.domain.registry.Student;
import br.com.felipeblaft.reports.domain.registry.Teacher;
import br.com.felipeblaft.reports.support.ScenarioIT;
import jakarta.servlet.http.Cookie;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.Callable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Indistinguibilidade do aluno alheio e do aluno inexistente (T138, SC-006, R-14): para o mesmo
 * pedido da professora A, o aluno de B e um identificador que não existe têm o mesmo status, o mesmo
 * corpo e os mesmos cabeçalhos relevantes na Web (inclusive as rotas da 001 por aluno), e o mesmo
 * texto e os mesmos botões no Telegram. O tempo também não pode distinguir os dois casos: 50
 * medições de aquecimento e 200 medições alternadas de cada caso, com a ordem sorteada por uma
 * semente fixa; passa se a razão das medianas (alheio ÷ inexistente) ficar em [0,5; 2,0] e a dos
 * p95 em [0,5; 3,0]. O teste imprime as razões.
 *
 * <p>Estabilidade: a medição é em processo (MockMvc), sem rede, com aquecimento e semente fixa; só
 * o tempo de relógio de cada chamada é medido. Se a máquina estiver sob carga, rode o teste isolado
 * (a razão de medianas é robusta a picos, a de p95 menos).
 */
class IndistinguishableStudentResponseIT extends SecurityBase {

    private static final UUID ABSENT = UUID.fromString("00000000-0000-4000-8000-000000000999");
    private static final int WARM_UP = 50;
    private static final int MEASUREMENTS = 200;
    private static final long SEED = 20_261_027L;

    /** Cabeçalhos que variam por natureza (carimbo de tempo e token de CSRF rotativo). */
    private static final Set<String> VOLATILE_HEADERS = Set.of("date", "set-cookie");

    private Teacher clara;
    private Teacher bruno;
    private Student brunoStudent;
    private Cookie claraSession;
    private Reports brunoReports;

    @BeforeEach
    void prepare() throws Exception {
        clara = scenario.clara();
        bruno = scenario.bruno();
        scenario.student(clara, "Matteo Mafra", "Piano");
        brunoStudent = scenario.student(bruno, "Helena Prado", "Flauta");
        Cookie brunoSession = signInTeacher(ScenarioIT.CODE_BRUNO);
        claraSession = signInTeacher(ScenarioIT.CODE_CLARA);
        brunoReports = createReports(bruno, brunoStudent, brunoSession);
        link(USER_CLARA, ScenarioIT.CODE_CLARA);
    }

    private record Route(String label, HttpMethod method, String template, String body) {}

    private List<Route> routes() {
        return List.of(
                new Route("abrir meu aluno", HttpMethod.GET, "/my-students/{id}", null),
                new Route(
                        "editar meu aluno",
                        HttpMethod.PATCH,
                        "/my-students/{id}",
                        "{\"instrument\":\"Violão\"}"),
                new Route(
                        "editar o CPF do meu aluno",
                        HttpMethod.PATCH,
                        "/my-students/{id}",
                        "{\"cpf\":\"529.982.247-25\"}"),
                new Route("inativar", HttpMethod.POST, "/my-students/{id}/inactivation", null),
                new Route("reativar", HttpMethod.POST, "/my-students/{id}/reactivation", null),
                new Route("001: aluno", HttpMethod.GET, "/students/{id}", null),
                new Route("001: em aberto", HttpMethod.GET, "/students/{id}/open-report", null),
                new Route("001: semestres", HttpMethod.GET, "/students/{id}/semesters", null),
                new Route(
                        "001: prévia", HttpMethod.GET, "/students/{id}/semesters/2026-1/preview", null),
                new Route(
                        "001: gerar semestral",
                        HttpMethod.POST,
                        "/students/{id}/semester-reports",
                        "{\"periodKey\":\"2026-1\"}"),
                new Route("001: histórico", HttpMethod.GET, "/students/{id}/history", null));
    }

    private MvcResult run(Route route, UUID id) throws Exception {
        return call(
                route.method(),
                API + route.template().replace("{id}", id.toString()),
                route.body(),
                claraSession);
    }

    private static Map<String, List<String>> relevantHeaders(MvcResult result) {
        Map<String, List<String>> headers = new TreeMap<>();
        for (String name : result.getResponse().getHeaderNames()) {
            String lower = name.toLowerCase(java.util.Locale.ROOT);
            if (VOLATILE_HEADERS.contains(lower)) {
                continue;
            }
            headers.put(lower, result.getResponse().getHeaders(name));
        }
        return headers;
    }

    private static Set<String> cookieNames(MvcResult result) {
        Set<String> names = new java.util.TreeSet<>();
        for (String cookie : result.getResponse().getHeaders("Set-Cookie")) {
            names.add(cookie.substring(0, cookie.indexOf('=')));
        }
        return names;
    }

    @Test
    void statusBodyAndHeadersAreTheSameForAnotherTeachersStudentAndOneThatDoesNotExist()
            throws Exception {
        List<String> differences = new ArrayList<>();
        for (Route route : routes()) {
            MvcResult foreign = run(route, brunoStudent.id());
            MvcResult absent = run(route, ABSENT);

            if (foreign.getResponse().getStatus() != absent.getResponse().getStatus()) {
                differences.add(route.label() + ": status");
            }
            if (!bodyOf(foreign).equals(bodyOf(absent))) {
                differences.add(route.label() + ": corpo");
            }
            if (!relevantHeaders(foreign).equals(relevantHeaders(absent))) {
                differences.add(
                        route.label()
                                + ": cabeçalhos "
                                + relevantHeaders(foreign)
                                + " × "
                                + relevantHeaders(absent));
            }
            if (!cookieNames(foreign).equals(cookieNames(absent))) {
                differences.add(route.label() + ": cookies");
            }
            assertThat(foreign.getResponse().getStatus()).as(route.label()).isEqualTo(404);
        }
        assertThat(differences).isEmpty();
    }

    @Test
    void reportsOfAnotherTeachersStudentAnswerLikeReportsThatDoNotExist() throws Exception {
        List<String> differences = new ArrayList<>();
        Map<String, String> paths = new TreeMap<>();
        paths.put("GET /lesson-reports/{r}", "/lesson-reports/" + brunoReports.openLesson());
        paths.put("GET /semester-reports/{r}", "/semester-reports/" + brunoReports.semester());
        paths.put("GET /jobs/{r}", "/jobs/" + brunoReports.job());
        paths.put(
                "GET official pdf",
                "/lesson-reports/" + brunoReports.approvedLesson() + "/official-pdf");
        for (Map.Entry<String, String> entry : paths.entrySet()) {
            String absentPath = entry.getValue().replaceAll("[0-9a-f-]{36}", ABSENT.toString());
            MvcResult foreign = call(HttpMethod.GET, API + entry.getValue(), null, claraSession);
            MvcResult absent = call(HttpMethod.GET, API + absentPath, null, claraSession);
            if (foreign.getResponse().getStatus() != absent.getResponse().getStatus()
                    || !bodyOf(foreign).equals(bodyOf(absent))
                    || !relevantHeaders(foreign).equals(relevantHeaders(absent))) {
                differences.add(entry.getKey());
            }
        }
        assertThat(differences).isEmpty();
    }

    @Test
    void theBotAnswersTheSameTextAndButtonsForAnotherTeachersStudentAndOneThatDoesNotExist()
            throws Exception {
        String absent = say(USER_CLARA, "Zacarias Inexistente").fingerprint().replace("Zacarias Inexistente", "§");
        for (String typed : List.of("Helena Prado", "Helena", "Prado")) {
            assertThat(say(USER_CLARA, typed).fingerprint().replace(typed, "§"))
                    .as(typed)
                    .isEqualTo(absent);
        }
        String forgedAbsent = forgedTap("v1:reg-open:7");
        assertThat(forgedTap("v1:reg-open:" + brunoStudent.id())).isEqualTo(forgedAbsent);
    }

    private String forgedTap(String data) throws Exception {
        say(USER_CLARA, "meus alunos");
        return tap(USER_CLARA, data).fingerprint();
    }

    // ------------------------------------------------------------------ tempo

    @Test
    void responseTimeDoesNotTellTheTwoCasesApartOnTheWeb() throws Exception {
        List<String> report = new ArrayList<>();
        for (Route route :
                List.of(
                        routes().get(0), // abrir
                        routes().get(1), // editar
                        routes().get(3), // inativar
                        routes().get(5))) { // 001: aluno
            report.add(
                    measure(
                            "Web " + route.label(),
                            () -> {
                                run(route, brunoStudent.id());
                                return null;
                            },
                            () -> {
                                run(route, ABSENT);
                                return null;
                            }));
        }
        report.forEach(System.out::println);
    }

    @Test
    void responseTimeDoesNotTellTheTwoCasesApartOnTheBot() throws Exception {
        String typedForeign = "Helena Prado";
        String typedAbsent = "Zacarias Inexistente";
        String line =
                measure(
                        "Telegram nome digitado",
                        () -> {
                            say(USER_CLARA, typedForeign);
                            return null;
                        },
                        () -> {
                            say(USER_CLARA, typedAbsent);
                            return null;
                        });
        System.out.println(line);
    }

    /**
     * Mede os dois casos alternados: aquecimento, depois {@value #MEASUREMENTS} medições de cada
     * um, com a ordem de cada par sorteada pela semente fixa. Falha fora dos limites e devolve a
     * linha com as razões.
     */
    private String measure(String name, Callable<Void> foreign, Callable<Void> absent)
            throws Exception {
        Random order = new Random(SEED);
        for (int i = 0; i < WARM_UP; i++) {
            if (order.nextBoolean()) {
                foreign.call();
                absent.call();
            } else {
                absent.call();
                foreign.call();
            }
        }
        List<Long> foreignTimes = new ArrayList<>();
        List<Long> absentTimes = new ArrayList<>();
        for (int i = 0; i < MEASUREMENTS; i++) {
            if (order.nextBoolean()) {
                foreignTimes.add(timed(foreign));
                absentTimes.add(timed(absent));
            } else {
                absentTimes.add(timed(absent));
                foreignTimes.add(timed(foreign));
            }
        }
        double medianRatio = median(foreignTimes) / median(absentTimes);
        double p95Ratio = percentile95(foreignTimes) / percentile95(absentTimes);
        String line =
                String.format(
                        "[T138] %s: mediana alheio=%.2f ms inexistente=%.2f ms razão=%.3f; "
                                + "p95 alheio=%.2f ms inexistente=%.2f ms razão=%.3f",
                        name,
                        median(foreignTimes) / 1e6,
                        median(absentTimes) / 1e6,
                        medianRatio,
                        percentile95(foreignTimes) / 1e6,
                        percentile95(absentTimes) / 1e6,
                        p95Ratio);
        assertThat(medianRatio).as(line).isBetween(0.5, 2.0);
        assertThat(p95Ratio).as(line).isBetween(0.5, 3.0);
        return line;
    }

    private static long timed(Callable<Void> action) throws Exception {
        long start = System.nanoTime();
        action.call();
        return System.nanoTime() - start;
    }

    private static double median(List<Long> times) {
        List<Long> sorted = new ArrayList<>(times);
        Collections.sort(sorted);
        int n = sorted.size();
        return n % 2 == 1
                ? sorted.get(n / 2)
                : (sorted.get(n / 2 - 1) + sorted.get(n / 2)) / 2.0;
    }

    private static double percentile95(List<Long> times) {
        List<Long> sorted = new ArrayList<>(times);
        Collections.sort(sorted);
        int index = (int) Math.ceil(0.95 * sorted.size()) - 1;
        return sorted.get(Math.max(0, index));
    }
}
