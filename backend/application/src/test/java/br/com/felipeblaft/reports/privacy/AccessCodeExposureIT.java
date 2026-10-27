package br.com.felipeblaft.reports.privacy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import br.com.felipeblaft.reports.support.LeakAudit;
import br.com.felipeblaft.reports.support.RealStackBase;
import br.com.felipeblaft.reports.support.ResponseSweeper;
import br.com.felipeblaft.reports.support.ResponseSweeper.Place;
import jakarta.servlet.http.Cookie;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;

/**
 * FR-011 e SC-004: o código de acesso do professor e o código de ativação do administrador saem
 * <b>só</b> na resposta da operação que o gerou, com {@code Cache-Control: no-store}, e nunca em
 * URL, cabeçalho, listagem, recarga, log, rastro de exceção, {@code authorship_record} nem em
 * tabela alguma (o banco guarda só a derivada). Parametrizado pelas cinco operações que devolvem
 * segredo, com a pilha real.
 */
class AccessCodeExposureIT extends RealStackBase {

    /** Operações que devolvem segredo; cada uma devolve a resposta e o nome do campo do código. */
    enum SecretOperation {
        TEACHER_REGISTRATION("accessCode"),
        TEACHER_REACTIVATION("accessCode"),
        ACCESS_CODE_REISSUE("accessCode"),
        ADMINISTRATOR_CREATION("activationCode"),
        ADMINISTRATOR_REACTIVATION("activationCode");

        private final String field;

        SecretOperation(String field) {
            this.field = field;
        }
    }

    @ParameterizedTest
    @EnumSource(SecretOperation.class)
    void theCodeLeavesOnlyInTheResponseOfTheOperationThatIssuedIt(SecretOperation operation)
            throws Exception {
        Cookie admin = insertAndSignInAsAdministrator();
        logs.clear();

        MvcResult issued = perform(operation, admin);

        // a resposta da operação: segredo presente, sem cache, fora de URL e de cabeçalhos
        assertThat(issued.getResponse().getStatus()).isBetween(200, 201);
        assertThat(issued.getResponse().getHeader("Cache-Control")).contains("no-store");
        JsonNode json = body(issued);
        String code = json.path(operation.field).asString();
        assertThat(code).as("código na resposta da operação").isNotBlank();
        assertThat(json.path("validUntil").asString()).isNotBlank();
        Map<String, String> secret = Map.of("código emitido", code);
        LeakAudit audit = LeakAudit.searching(secret);
        audit.exchange(
                "resposta da operação (só o corpo pode ter o código)",
                issued,
                ResponseSweeper.searching(secret).allowing(Place.BODY));

        // listagens, detalhes e recargas depois da emissão
        audit.exchange("lista de professores", adminGet(admin, ADMIN_BASE + "/teachers"));
        audit.exchange(
                "lista de professores inativos",
                adminGet(admin, ADMIN_BASE + "/teachers?active=false"));
        audit.exchange("lista de administradores", adminGet(admin, ADMIN_BASE + "/administrators"));
        audit.exchange("sessão", adminGet(admin, ADMIN_BASE + "/session"));
        for (UUID teacher : teacherIds()) {
            audit.exchange("professor", adminGet(admin, ADMIN_BASE + "/teachers/" + teacher));
        }

        // o uso do código também não o devolve
        MvcResult used = useTheCode(operation, code);
        audit.exchange("uso do código", used);
        audit.exchange("lista depois do uso", adminGet(admin, ADMIN_BASE + "/teachers"));

        audit.text("log", Place.LOG, logText());
        audit.text("rastros de exceção", Place.EXCEPTION_TRACE, traceText());
        audit.text("authorship_record", Place.AUTHORSHIP, authorshipText());
        for (String table : tables()) {
            audit.text("tabela " + table, Place.AUTHORSHIP, tableText(table));
        }
        assertThat(audit.sweeps()).isGreaterThan(8);
        audit.assertClean();
    }

    // ---------------------------------------------------------------- as operações

    private MvcResult perform(SecretOperation operation, Cookie admin) throws Exception {
        switch (operation) {
            case TEACHER_REGISTRATION:
                return createTeacher(admin, "Professora Sigilosa", "sigilosa@example.test")
                        .response();
            case TEACHER_REACTIVATION:
                {
                    CreatedTeacher teacher =
                            createTeacher(admin, "Professor Reativado", "reativado@example.test");
                    mvcStatus(
                            adminPost(
                                    admin,
                                    ADMIN_BASE + "/teachers/" + teacher.id() + "/inactivation",
                                    null),
                            200);
                    return adminPost(
                            admin,
                            ADMIN_BASE + "/teachers/" + teacher.id() + "/reactivation",
                            null);
                }
            case ACCESS_CODE_REISSUE:
                {
                    CreatedTeacher teacher =
                            createTeacher(admin, "Professor Reemitido", "reemitido@example.test");
                    return adminPost(
                            admin,
                            ADMIN_BASE + "/teachers/" + teacher.id() + "/access-code",
                            "{\"endTelegramLink\":false}");
                }
            case ADMINISTRATOR_CREATION:
                return adminPost(
                        admin,
                        ADMIN_BASE + "/administrators",
                        "{\"name\":\"Administrador Novo\",\"email\":\"novo.admin@example.test\"}");
            case ADMINISTRATOR_REACTIVATION:
                {
                    MvcResult created =
                            adminPost(
                                    admin,
                                    ADMIN_BASE + "/administrators",
                                    "{\"name\":\"Administrador Parado\","
                                            + "\"email\":\"parado.admin@example.test\"}");
                    mvcStatus(created, 201);
                    String id = body(created).path("administrator").path("id").asString();
                    mvcStatus(
                            adminPost(
                                    admin,
                                    ADMIN_BASE + "/administrators/" + id + "/inactivation",
                                    null),
                            200);
                    return adminPost(
                            admin, ADMIN_BASE + "/administrators/" + id + "/reactivation", null);
                }
            default:
                throw new IllegalStateException(operation.name());
        }
    }

    /** Usa o código como o destinatário usaria (entrar como professor; ativar a conta). */
    private MvcResult useTheCode(SecretOperation operation, String code) throws Exception {
        if (operation.field.equals("accessCode")) {
            return call(
                    post("/api/v1/session")
                            .with(csrf().asHeader())
                            .contentType("application/json")
                            .content("{\"code\":\"" + code + "\"}"));
        }
        String email =
                jdbc.queryForObject(
                        "select email from administrator where email like '%.admin@%'",
                        String.class);
        return call(
                post(ADMIN_BASE + "/activation")
                        .with(csrf().asHeader())
                        .contentType("application/json")
                        .content(
                                "{\"email\":\""
                                        + email
                                        + "\",\"activationCode\":\""
                                        + code
                                        + "\",\"newPassword\":\"a-definitive-secret-Jh2-canary\"}"));
    }

    private List<UUID> teacherIds() {
        return jdbc.queryForList("select id from teacher", UUID.class);
    }
}
