package br.com.felipeblaft.reports.privacy;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.felipeblaft.reports.support.LeakAudit;
import br.com.felipeblaft.reports.support.RealStackBase;
import br.com.felipeblaft.reports.support.ResponseSweeper.Place;
import jakarta.servlet.http.Cookie;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/**
 * FR-005, FR-032, FR-039 e SC-004: a senha digitada e a provisória do administrador nunca aparecem
 * em log, resposta, cabeçalho, mensagem de erro, URL, rastro de exceção, {@code authorship_record}
 * nem em tabela alguma (a senha só existe como hash). O roteiro percorre, com a pilha real, a
 * subida com senha provisória, a entrada errada e certa, a troca obrigatória (recusada e aceita),
 * a criação e a ativação de um segundo administrador (recusada e aceita), corpos malformados e
 * longos demais.
 */
class AdminSecretsNotLoggedIT extends RealStackBase {

    private static final String PROVISIONAL = "provisional-secret-Zq7-canary";
    private static final String WRONG = "wrong-typed-secret-Xk3-canary";
    private static final String WRONG_UNKNOWN_EMAIL = "typed-for-unknown-Rt4-canary";
    private static final String SHORT = "curta-Vw2";
    private static final String NEW_PASSWORD = "new-definitive-secret-Pm9-canary";
    private static final String ACTIVATED = "activated-secret-Hn5-canary";
    private static final String ACTIVATION_WEAK = "weak-Ab1";
    private static final String TRUNCATED = "truncated-body-secret-Lc8-canary";
    private static final String TOO_LONG = "too-long-secret-Dy6-canary-" + "x".repeat(130);

    private static final String BOOT_EMAIL = "boot.admin@example.test";
    private static final String SECOND_EMAIL = "second.admin@example.test";

    private final LeakAudit audit = LeakAudit.searching(secrets());
    private final List<MvcResult> exchanges = new ArrayList<>();

    private void seen(String step, MvcResult result) {
        exchanges.add(result);
        audit.exchange(step, result);
    }

    private static Map<String, String> secrets() {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("senha provisória", PROVISIONAL);
        values.put("senha errada digitada", WRONG);
        values.put("senha digitada com e-mail desconhecido", WRONG_UNKNOWN_EMAIL);
        values.put("senha curta recusada", SHORT);
        values.put("nova senha", NEW_PASSWORD);
        values.put("senha da ativação", ACTIVATED);
        values.put("senha fraca da ativação", ACTIVATION_WEAK);
        values.put("senha de corpo truncado", TRUNCATED);
        values.put("senha longa demais", TOO_LONG.substring(0, 40));
        return values;
    }

    @Test
    void typedAndProvisionalPasswordsNeverLeakThroughTheWholeAdministratorFlow() throws Exception {
        bootstrapAdministrator("Administrador Canário", BOOT_EMAIL, PROVISIONAL);

        // entrada errada, com e-mail conhecido e desconhecido
        MvcResult wrong = adminSignInRequest(BOOT_EMAIL, WRONG);
        assertThat(wrong.getResponse().getStatus()).isEqualTo(401);
        seen("entrada com senha errada", wrong);
        MvcResult unknown = adminSignInRequest("nobody@example.test", WRONG_UNKNOWN_EMAIL);
        assertThat(unknown.getResponse().getStatus()).isEqualTo(401);
        seen("entrada com e-mail desconhecido", unknown);

        // corpos malformados e longos demais que carregam a senha
        MvcResult truncated =
                call(
                        MockMvcRequestBuilders.post(ADMIN_BASE + "/session")
                                .with(SecurityMockMvcRequestPostProcessors.csrf().asHeader())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"email\":\""
                                                + BOOT_EMAIL
                                                + "\",\"password\":\""
                                                + TRUNCATED));
        assertThat(truncated.getResponse().getStatus()).isBetween(400, 499);
        seen("corpo truncado com senha", truncated);
        MvcResult tooLong = adminSignInRequest(BOOT_EMAIL, TOO_LONG);
        assertThat(tooLong.getResponse().getStatus()).isBetween(400, 499);
        seen("senha longa demais", tooLong);

        // entrada com a senha provisória e uso de rota restrita
        MvcResult provisional = adminSignInRequest(BOOT_EMAIL, PROVISIONAL);
        mvcStatus(provisional, 200);
        seen("entrada com senha provisória", provisional);
        Cookie provisionalSession = cookieOf(provisional, ADMIN_COOKIE);
        seen("rota restrita com senha provisória", adminGet(provisionalSession, ADMIN_BASE + "/teachers"));

        // troca recusada (curta; igual ao e-mail) e aceita
        MvcResult shortPassword =
                adminPut(provisionalSession, ADMIN_BASE + "/password", passwordJson(SHORT));
        assertThat(shortPassword.getResponse().getStatus()).isBetween(400, 499);
        seen("troca com senha curta", shortPassword);
        MvcResult sameAsEmail =
                adminPut(provisionalSession, ADMIN_BASE + "/password", passwordJson(BOOT_EMAIL));
        assertThat(sameAsEmail.getResponse().getStatus()).isBetween(400, 499);
        seen("troca com senha igual ao e-mail", sameAsEmail);
        MvcResult changed =
                adminPut(provisionalSession, ADMIN_BASE + "/password", passwordJson(NEW_PASSWORD));
        mvcStatus(changed, 200);
        seen("troca aceita", changed);
        Cookie session = cookieOf(changed, ADMIN_COOKIE);

        // a senha provisória e a nova valem ou não valem, sem aparecer
        MvcResult oldPassword = adminSignInRequest(BOOT_EMAIL, PROVISIONAL);
        assertThat(oldPassword.getResponse().getStatus()).isEqualTo(401);
        seen("entrada com a provisória depois da troca", oldPassword);
        MvcResult fresh = adminSignInRequest(BOOT_EMAIL, NEW_PASSWORD);
        mvcStatus(fresh, 200);
        seen("entrada com a nova senha", fresh);
        seen("sessão atual", adminGet(session, ADMIN_BASE + "/session"));

        // segundo administrador: criação, ativação recusada e aceita, entrada
        MvcResult created =
                adminPost(
                        session,
                        ADMIN_BASE + "/administrators",
                        "{\"name\":\"Segundo Administrador\",\"email\":\"" + SECOND_EMAIL + "\"}");
        mvcStatus(created, 201);
        String activationCode = body(created).path("activationCode").asString();
        assertThat(activationCode).isNotBlank();
        seen("criação do segundo administrador", created);
        MvcResult weak =
                call(
                        asJson(
                                MockMvcRequestBuilders.post(ADMIN_BASE + "/activation")
                                        .with(
                                                SecurityMockMvcRequestPostProcessors.csrf()
                                                        .asHeader()),
                                activationJson(activationCode, ACTIVATION_WEAK)));
        assertThat(weak.getResponse().getStatus()).isBetween(400, 499);
        seen("ativação com senha fraca", weak);
        MvcResult activated =
                call(
                        asJson(
                                MockMvcRequestBuilders.post(ADMIN_BASE + "/activation")
                                        .with(
                                                SecurityMockMvcRequestPostProcessors.csrf()
                                                        .asHeader()),
                                activationJson(activationCode, ACTIVATED)));
        assertThat(activated.getResponse().getStatus()).isEqualTo(204);
        seen("ativação aceita", activated);
        MvcResult second = adminSignInRequest(SECOND_EMAIL, ACTIVATED);
        mvcStatus(second, 200);
        seen("entrada do segundo administrador", second);
        seen("saída", adminDelete(session, ADMIN_BASE + "/session"));

        // o que ficou: logs, rastros, autoria e o banco inteiro
        audit.text("log", Place.LOG, logText());
        audit.text("rastros de exceção", Place.EXCEPTION_TRACE, traceText());
        audit.text("authorship_record", Place.AUTHORSHIP, authorshipText());
        for (String table : tables()) {
            audit.text("tabela " + table, Place.AUTHORSHIP, tableText(table));
        }
        assertThat(audit.sweeps()).isGreaterThan(20);
        audit.assertClean();
        assertHashesNeverLeave();
    }

    /** T203: o hash da senha, o resumo do código de ativação e os das sessões ficam só no banco. */
    private void assertHashesNeverLeave() throws Exception {
        Map<String, String> hashes = new LinkedHashMap<>();
        int index = 0;
        for (String hash :
                jdbc.queryForList(
                        "select password_hash from administrator where password_hash is not null",
                        String.class)) {
            hashes.put("hash da senha " + ++index, hash);
        }
        for (String hash :
                jdbc.queryForList(
                        "select token_hash from administrator_session", String.class)) {
            hashes.put("resumo da sessão " + ++index, hash);
        }
        assertThat(hashes).as("há hashes no banco para procurar").hasSizeGreaterThanOrEqualTo(3);
        assertThat(hashes.values()).anyMatch(hash -> hash.startsWith("$argon2id$"));
        LeakAudit leak = LeakAudit.searching(hashes);
        for (int i = 0; i < exchanges.size(); i++) {
            leak.exchange("resposta " + i, exchanges.get(i));
        }
        leak.text("log", Place.LOG, logText());
        leak.text("rastros de exceção", Place.EXCEPTION_TRACE, traceText());
        leak.text("authorship_record", Place.AUTHORSHIP, authorshipText());
        leak.assertClean();
    }

    private static String passwordJson(String password) {
        return "{\"newPassword\":\"" + password + "\"}";
    }

    private static String activationJson(String code, String password) {
        return "{\"email\":\""
                + SECOND_EMAIL
                + "\",\"activationCode\":\""
                + code
                + "\",\"newPassword\":\""
                + password
                + "\"}";
    }
}
