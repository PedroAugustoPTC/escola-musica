package br.com.felipeblaft.reports.security;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.felipeblaft.reports.support.AdministrationStackBase;
import br.com.felipeblaft.reports.support.AdministratorFixture;
import jakarta.servlet.http.Cookie;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * Registro de autoria da gestão de administradores (FR-032, FR-033, FR-040): criar, ativar,
 * inativar e reativar deixam registro com papel, canal e resultado corretos; o código de ativação e
 * a senha nunca são gravados, e a ativação com código errado é falha sem o código digitado.
 */
class AuthorshipAdministratorOperationsIT extends AdministrationStackBase {

    private static final String NEW_EMAIL = "novo.admin@example.test";
    private static final String NEW_PASSWORD = "senha-nova-bem-longa-9";
    private static final String ORIGIN = "198.51.100.70";
    private static final String HEX64 = "[0-9a-f]{64}";

    private AdministratorFixture creator;
    private Cookie session;

    @BeforeEach
    void prepare() throws Exception {
        creator = insertAdministrator();
        session = signInAdministrator(creator);
        clearAuthorship();
    }

    private MockHttpServletResponse create() throws Exception {
        MockHttpServletResponse response =
                call(
                        HttpMethod.POST,
                        ADMIN + "/administrators",
                        session,
                        Map.of("name", "Novo Administrador", "email", NEW_EMAIL));
        assertThat(response.getStatus()).isEqualTo(201);
        return response;
    }

    private MockHttpServletResponse activate(String code) throws Exception {
        return call(
                HttpMethod.POST,
                ADMIN + "/activation",
                null,
                Map.of("email", NEW_EMAIL, "activationCode", code, "newPassword", NEW_PASSWORD));
    }

    private static UUID idOf(MockHttpServletResponse created) throws Exception {
        return UUID.fromString(body(created).path("administrator").path("id").asString());
    }

    private void assertByCreator(Map<String, Object> record, UUID target) {
        assertRecord(record, "ADMINISTRATOR", "WEB", "SUCCESS");
        assertThat(record.get("author_id")).isEqualTo(creator.administrator().id());
        assertThat(record.get("target_type")).isEqualTo("ADMINISTRATOR");
        assertThat(record.get("target_id")).isEqualTo(target);
        assertThat(record.get("origin_hash")).isNull();
    }

    @Test
    void createInactivateAndReactivateLeaveARecordWithoutTheActivationCode() throws Exception {
        MockHttpServletResponse created = create();
        UUID target = idOf(created);
        MockHttpServletResponse inactivation =
                call(
                        HttpMethod.POST,
                        ADMIN + "/administrators/" + target + "/inactivation",
                        session);
        MockHttpServletResponse reactivation =
                call(
                        HttpMethod.POST,
                        ADMIN + "/administrators/" + target + "/reactivation",
                        session);

        assertThat(inactivation.getStatus()).isEqualTo(200);
        assertThat(reactivation.getStatus()).isEqualTo(200);
        assertByCreator(onlyAuthorship("ADMINISTRATOR_CREATE"), target);
        assertByCreator(onlyAuthorship("ADMINISTRATOR_INACTIVATE"), target);
        assertByCreator(onlyAuthorship("ADMINISTRATOR_REACTIVATE"), target);
        assertThat(authorshipDump())
                .doesNotContain(body(created).path("activationCode").asString())
                .doesNotContain(body(reactivation).path("activationCode").asString())
                .doesNotContain(NEW_EMAIL);
    }

    @Test
    void activationLeavesARecordOfThePersonWhoActivatedWithoutTheCodeNorThePassword()
            throws Exception {
        MockHttpServletResponse created = create();
        UUID target = idOf(created);
        String code = body(created).path("activationCode").asString();

        MockHttpServletResponse activation = activate(code);

        assertThat(activation.getStatus()).isEqualTo(204);
        Map<String, Object> record = onlyAuthorship("ADMIN_ACTIVATE");
        assertRecord(record, "ADMINISTRATOR", "WEB", "SUCCESS");
        assertThat(record.get("author_id")).isEqualTo(target);
        assertThat((String) record.get("origin_hash")).matches(HEX64);
        assertThat(authorshipDump()).doesNotContain(code).doesNotContain(NEW_PASSWORD);
        // a senha escolhida entra; a conta ativada passa a entrar com ela
        assertThat(signInAdministratorRaw(NEW_EMAIL, NEW_PASSWORD, ORIGIN).getStatus())
                .isEqualTo(200);
    }

    @Test
    void activationWithAWrongCodeIsAFailureWithoutTheTypedCode() throws Exception {
        UUID target = idOf(create());
        String wrong = "ZZZZZZZZZZZZ";

        MockHttpServletResponse activation = activate(wrong);

        assertThat(activation.getStatus()).isEqualTo(401);
        assertThat(errorCode(activation)).isEqualTo("INVALID_ACTIVATION");
        Map<String, Object> record = onlyAuthorship("ADMIN_ACTIVATE");
        assertRecord(record, "ADMINISTRATOR", "WEB", "FAILURE");
        assertThat(record.get("author_id")).isEqualTo(target);
        assertThat(record.get("error_code")).isEqualTo("INVALID_ACTIVATION");
        assertThat(authorshipDump()).doesNotContain(wrong).doesNotContain(NEW_PASSWORD);
    }

    @Test
    void duplicateEmailAndLastAdministratorLeaveNoSuccessRecord() throws Exception {
        MockHttpServletResponse duplicate =
                call(
                        HttpMethod.POST,
                        ADMIN + "/administrators",
                        session,
                        Map.of("name", "Repetido", "email", creator.email().toUpperCase()));
        MockHttpServletResponse last =
                call(
                        HttpMethod.POST,
                        ADMIN + "/administrators/" + creator.administrator().id() + "/inactivation",
                        session);

        assertThat(duplicate.getStatus()).isEqualTo(409);
        assertThat(errorCode(duplicate)).isEqualTo("DUPLICATE_EMAIL");
        assertThat(last.getStatus()).isEqualTo(409);
        assertThat(errorCode(last)).isEqualTo("LAST_ADMINISTRATOR");
        assertThat(authorship()).isEmpty();
    }
}
