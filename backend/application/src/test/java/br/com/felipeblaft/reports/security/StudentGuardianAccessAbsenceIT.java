package br.com.felipeblaft.reports.security;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.felipeblaft.reports.domain.access.Role;
import br.com.felipeblaft.reports.inbound.telegram.TelegramKeywords;
import br.com.felipeblaft.reports.support.ScenarioIT;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * Aluno e responsável não são usuários (SC-007, FR-001): o sistema tem dois papéis; nenhuma rota do
 * Spring, caminho do OpenAPI, comando do Telegram ou tabela de credencial trata aluno ou responsável
 * como quem entra; e uma requisição que se diz de aluno ou de responsável é tratada como não
 * autenticada. O aluno continua existindo como <b>recurso</b> ({@code /students/{id}}), nunca como
 * identidade.
 */
class StudentGuardianAccessAbsenceIT extends SecurityBase {

    /** Responsável sempre é proibido como segmento; aluno só quando seguido de palavra de entrada. */
    private static final Pattern GUARDIAN_AS_USER =
            Pattern.compile("guardian|responsible|parent|responsavel", Pattern.CASE_INSENSITIVE);

    private static final Pattern STUDENT_AS_USER =
            Pattern.compile(
                    "student[s]?[-_/]?(session|login|sign-?in|sign-?out|portal|access|activation|password|account|code|token|credential)"
                            + "|(session|login|sign-?in|portal|access)[-_/]?student",
                    Pattern.CASE_INSENSITIVE);

    private static final Pattern AUTHENTICATION_WORDS =
            Pattern.compile("(^|/)(session|activation|password|login|sign-?in|credential|token)(/|$)");

    private static final Set<String> ALLOWED_SECURITY_SCHEMES = Set.of("adminSession", "session");

    @Autowired ApplicationContext context;

    private static boolean refersToStudentOrGuardianAsUser(String path) {
        return GUARDIAN_AS_USER.matcher(path).find() || STUDENT_AS_USER.matcher(path).find();
    }

    @Test
    void thereAreExactlyTwoRoles() {
        assertThat(Role.values()).containsExactlyInAnyOrder(Role.ADMINISTRATOR, Role.TEACHER);
    }

    @Test
    void noSpringRouteTreatsStudentOrGuardianAsUser() {
        List<String> routes = springRoutes();

        assertThat(routes).as("rotas lidas do Spring").isNotEmpty();
        assertThat(routes).noneMatch(StudentGuardianAccessAbsenceIT::refersToStudentOrGuardianAsUser);
    }

    @Test
    void theOnlyAuthenticationRoutesAreTheTeacherAndAdministratorOnes() {
        List<String> authenticationRoutes =
                springRoutes().stream()
                        .filter(route -> AUTHENTICATION_WORDS.matcher(route.toLowerCase(Locale.ROOT)).find())
                        .filter(route -> !route.startsWith("/actuator"))
                        .toList();

        assertThat(authenticationRoutes)
                .as("toda rota de entrada, sessão ou senha é do professor ou do administrador")
                .isNotEmpty()
                .allSatisfy(
                        route ->
                                assertThat(route)
                                        .matches(
                                                "/api/v1/session|/api/v1/admin/(session|activation|password)"));
    }

    @Test
    void noOpenApiPathOrSecuritySchemeTreatsStudentOrGuardianAsUser() throws IOException {
        for (String contract :
                List.of(
                        "specs/001-fluxo-relatorios/contracts/api-portal.openapi.yaml",
                        "specs/005-admin-registrations-access/contracts/api-administration.openapi.yaml")) {
            String yaml = Files.readString(Path.of("../..", contract), StandardCharsets.UTF_8);
            List<String> paths = new ArrayList<>();
            Matcher path = Pattern.compile("(?m)^  (/[^\\s:]*):\\s*$").matcher(yaml);
            while (path.find()) {
                paths.add(path.group(1));
            }
            assertThat(paths).as("caminhos de %s", contract).isNotEmpty();
            assertThat(paths)
                    .as("caminhos de %s", contract)
                    .noneMatch(StudentGuardianAccessAbsenceIT::refersToStudentOrGuardianAsUser);

            Set<String> schemes = new TreeSet<>();
            Matcher block =
                    Pattern.compile("(?s)securitySchemes:\\s*\\n(.*?)(?=\\n {0,2}\\S|\\z)")
                            .matcher(yaml);
            if (block.find()) {
                Matcher name = Pattern.compile("(?m)^    (\\w+):\\s*$").matcher(block.group(1));
                while (name.find()) {
                    schemes.add(name.group(1));
                }
            }
            assertThat(schemes).as("esquemas de segurança de %s", contract).isSubsetOf(ALLOWED_SECURITY_SCHEMES);
        }
    }

    @Test
    void noTelegramCommandOrKeywordRefersToStudentOrGuardianAsUser() throws Exception {
        assertThat(
                        Arrays.stream(TelegramKeywords.Command.values())
                                .map(Enum::name)
                                .filter(
                                        name ->
                                                name.matches(
                                                        "(?i).*(GUARDIAN|PARENT|RESPONSIBLE).*|.*STUDENT_(SIGN|LOGIN|ACCESS|SESSION).*")))
                .isEmpty();

        List<String> claims =
                List.of(
                        "sou o aluno",
                        "sou responsável",
                        "entrar como aluno",
                        "acesso do responsável",
                        "código do aluno",
                        "login do responsável",
                        "área do aluno");
        for (String claim : claims) {
            assertThat(TelegramKeywords.identify(claim)).as(claim).isEmpty();
        }

        br.com.felipeblaft.reports.domain.registry.Teacher clara = scenario.clara();
        scenario.student(clara, "Matteo Mafra", "Piano");
        link(USER_CLARA, ScenarioIT.CODE_CLARA);
        int links = count("telegram_link");
        for (String claim : claims) {
            // vinculado ou não, é um texto qualquer: nenhum vínculo novo nem papel novo
            Reply linked = say(USER_CLARA, claim);
            assertThat(linked.plain()).contains("Nenhum aluno encontrado");
            Reply unlinked = say(7050L, claim);
            assertThat(unlinked.plain()).contains("envie o código de acesso");
        }
        assertThat(count("telegram_link")).isEqualTo(links);
    }

    @Test
    void noCredentialTableBelongsToStudentOrGuardian() {
        List<Map<String, Object>> credentialColumns =
                jdbc.queryForList(
                        "select table_name, column_name from information_schema.columns "
                                + "where table_schema = 'public' and (column_name ~ '(password|code_hash|token|secret|credential)')");
        Set<String> tables = new TreeSet<>();
        credentialColumns.forEach(row -> tables.add((String) row.get("table_name")));

        assertThat(tables)
                .as("tabelas que guardam credencial")
                .isNotEmpty()
                .allSatisfy(table -> assertThat(refersToStudentOrGuardianAsUser(table)).isFalse())
                .doesNotContain("student", "guardian");

        List<String> allTables =
                jdbc.queryForList(
                        "select table_name from information_schema.tables where table_schema = 'public'",
                        String.class);
        assertThat(allTables)
                .noneMatch(name -> name.matches("(?i).*(guardian|responsible|parent).*"))
                .noneMatch(name -> name.matches("(?i)student_(session|credential|login|account|access).*"));

        // as sessões e os vínculos apontam só para professor ou administrador
        List<String> sessionColumns =
                jdbc.queryForList(
                        "select column_name from information_schema.columns where table_schema = 'public' "
                                + "and table_name in ('web_session', 'administrator_session')",
                        String.class);
        assertThat(sessionColumns).noneMatch(name -> name.matches("(?i).*(student|guardian).*"));
    }

    @Test
    void aRequestThatClaimsToBeAStudentOrGuardianIsUnauthenticated() throws Exception {
        scenario.clara();
        int sessions = count("web_session");
        List<String> protectedRoutes =
                List.of(
                        API + "/my-students",
                        API + "/students",
                        API + "/home",
                        API + "/admin/teachers",
                        API + "/admin/students");
        List<Cookie[]> claimedCookies =
                List.of(
                        new Cookie[] {new Cookie("student_session", "token-do-aluno")},
                        new Cookie[] {new Cookie("guardian_session", "token-do-responsavel")},
                        new Cookie[] {new Cookie("session", "token-de-aluno-que-nao-existe")});

        for (String route : protectedRoutes) {
            for (Cookie[] cookies : claimedCookies) {
                MvcResult result = call(HttpMethod.GET, route, null, cookies);
                assertThat(result.getResponse().getStatus())
                        .as("%s com cookie %s", route, cookies[0].getName())
                        .isEqualTo(401);
            }
            MvcResult withHeaders =
                    mvc.perform(
                                    org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                            .get(route)
                                            .header("X-Role", "STUDENT")
                                            .header("X-User-Type", "guardian")
                                            .header("Authorization", "Bearer student-token")
                                            .queryParam("role", "student"))
                            .andReturn();
            assertThat(withHeaders.getResponse().getStatus()).as(route).isEqualTo(401);
            assertThat(errorCode(withHeaders)).isEqualTo("NOT_AUTHENTICATED");
        }

        // tentar entrar "como aluno" ou "como responsável" não abre sessão alguma
        for (String body :
                List.of(
                        "{\"studentCode\":\"K7QM2XPA\"}",
                        "{\"guardianCode\":\"K7QM2XPA\",\"role\":\"GUARDIAN\"}",
                        "{\"role\":\"STUDENT\",\"cpf\":\"529.982.247-25\"}")) {
            MvcResult result = call(HttpMethod.POST, API + "/session", body);
            assertThat(result.getResponse().getStatus()).as(body).isBetween(400, 401);
            assertThat(result.getResponse().getCookie("session")).isNull();
        }
        assertThat(count("web_session")).isEqualTo(sessions);
    }

    private List<String> springRoutes() {
        List<String> routes = new ArrayList<>();
        for (RequestMappingHandlerMapping mapping :
                context.getBeansOfType(RequestMappingHandlerMapping.class).values()) {
            for (RequestMappingInfo info : mapping.getHandlerMethods().keySet()) {
                if (info.getPathPatternsCondition() != null) {
                    routes.addAll(info.getPathPatternsCondition().getPatternValues());
                }
            }
        }
        return routes.stream().filter(route -> route.startsWith("/")).distinct().sorted().toList();
    }
}
