package br.com.felipeblaft.reports.security;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.felipeblaft.reports.support.AdministrationStackBase;
import jakarta.servlet.http.Cookie;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * Registro de autoria da entrada do professor (FR-040, parte do SC-014): o código na Web ({@code
 * TEACHER_SIGN_IN}) e o vínculo no simulador do Telegram ({@code TEACHER_LINK_TELEGRAM}), com
 * sucesso, falha e bloqueio. O registro leva o papel, o canal, o resultado e o resumo da origem;
 * nunca o código de acesso, nem o certo nem o errado.
 */
class AuthorshipTeacherAuthenticationIT extends AdministrationStackBase {

    private static final String WRONG_CODE = "ZZZZZZZZ";
    private static final String ORIGIN = "203.0.113.50";
    private static final String HEX64 = "[0-9a-f]{64}";

    private TeacherAccount teacher;

    @BeforeEach
    void prepare() throws Exception {
        Cookie admin = signInAdministrator(insertAdministrator());
        teacher = createTeacher(admin, "Clara Souza", "clara@example.test");
        jdbc.update("delete from access_attempt");
        clearAuthorship();
    }

    @Test
    void webSignInSuccessLeavesARecordOfTheTeacherOnTheWebChannel() throws Exception {
        MockHttpServletResponse response = signInTeacherRaw(teacher.accessCode(), ORIGIN);

        assertThat(response.getStatus()).isEqualTo(200);
        Map<String, Object> record = onlyAuthorship("TEACHER_SIGN_IN");
        assertRecord(record, "TEACHER", "WEB", "SUCCESS");
        assertThat(record.get("author_id")).isEqualTo(teacher.id());
        assertThat((String) record.get("origin_hash")).matches(HEX64);
        assertThat(authorshipDump()).doesNotContain(teacher.accessCode()).doesNotContain(ORIGIN);
    }

    @Test
    void webWrongCodeIsAFailureWithoutAuthorAndWithoutTheTypedCode() throws Exception {
        MockHttpServletResponse response = signInTeacherRaw(WRONG_CODE, ORIGIN);

        assertThat(response.getStatus()).isEqualTo(401);
        Map<String, Object> record = onlyAuthorship("TEACHER_SIGN_IN");
        assertRecord(record, null, "WEB", "FAILURE");
        assertThat(record.get("author_id")).isNull();
        assertThat(record.get("error_code")).isEqualTo("UNKNOWN_CODE");
        assertThat(authorshipDump()).doesNotContain(WRONG_CODE);
    }

    @Test
    void webRepeatedWrongCodesEndInABlockedRecord() throws Exception {
        for (int i = 0; i < 5; i++) {
            signInTeacherRaw(WRONG_CODE, ORIGIN);
        }

        MockHttpServletResponse blocked = signInTeacherRaw(teacher.accessCode(), ORIGIN);

        assertThat(blocked.getStatus()).isEqualTo(429);
        List<Map<String, Object>> blockedRecords = authorship("ATTEMPT_BLOCKED");
        assertThat(blockedRecords).hasSize(1);
        assertRecord(blockedRecords.getFirst(), null, "WEB", "BLOCKED");
        assertThat(blockedRecords.getFirst().get("error_code")).isEqualTo("TEMPORARILY_BLOCKED");
        assertThat(authorship("TEACHER_SIGN_IN")).hasSize(5);
        assertThat(authorshipDump())
                .doesNotContain(WRONG_CODE)
                .doesNotContain(teacher.accessCode());
    }

    @Test
    void telegramLinkSuccessLeavesARecordOfTheTeacherOnTheTelegramChannel() throws Exception {
        sendText(USER_CLARA, teacher.accessCode());

        assertThat(jdbc.queryForObject("select count(*) from telegram_link", Integer.class))
                .isEqualTo(1);
        Map<String, Object> record = onlyAuthorship("TEACHER_LINK_TELEGRAM");
        assertRecord(record, "TEACHER", "TELEGRAM", "SUCCESS");
        assertThat(record.get("author_id")).isEqualTo(teacher.id());
        assertThat((String) record.get("origin_hash")).matches(HEX64);
        // no Telegram a correlação é o update_id (1 a 10 dígitos)
        assertThat((String) record.get("correlation_id")).matches("[0-9]{1,10}");
        assertThat(authorshipDump()).doesNotContain(teacher.accessCode());
    }

    @Test
    void telegramWrongCodeIsAFailureAndTheCodeIsNeverStored() throws Exception {
        sendText(USER_CLARA, WRONG_CODE);

        assertThat(jdbc.queryForObject("select count(*) from telegram_link", Integer.class))
                .isZero();
        Map<String, Object> record = onlyAuthorship("TEACHER_LINK_TELEGRAM");
        assertRecord(record, null, "TELEGRAM", "FAILURE");
        assertThat(record.get("author_id")).isNull();
        assertThat(record.get("error_code")).isEqualTo("UNKNOWN_CODE");
        assertThat(authorshipDump()).doesNotContain(WRONG_CODE);
    }

    @Test
    void telegramRepeatedWrongCodesEndInABlockedRecord() throws Exception {
        for (int i = 0; i < 5; i++) {
            sendText(USER_CLARA, WRONG_CODE);
        }
        sendText(USER_CLARA, teacher.accessCode());

        List<Map<String, Object>> blockedRecords = authorship("ATTEMPT_BLOCKED");
        assertThat(blockedRecords).hasSize(1);
        assertRecord(blockedRecords.getFirst(), null, "TELEGRAM", "BLOCKED");
        assertThat(jdbc.queryForObject("select count(*) from telegram_link", Integer.class))
                .isZero();
        assertThat(authorshipDump()).doesNotContain(teacher.accessCode());
    }
}
