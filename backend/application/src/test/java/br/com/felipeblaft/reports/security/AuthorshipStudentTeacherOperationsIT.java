package br.com.felipeblaft.reports.security;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.felipeblaft.reports.support.AdministrationStackBase;
import br.com.felipeblaft.reports.support.CanaryCpf;
import jakarta.servlet.http.Cookie;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.JsonNode;

/**
 * Registro de autoria das operações de aluno do professor, na Web e no Telegram (FR-040, parte do
 * SC-014): cadastrar, editar, inativar e reativar deixam registro de papel {@code TEACHER}, com o
 * canal certo, o aluno como alvo e o resultado; a recusa de CPF de aluno alheio deixa {@code
 * CPF_REFUSED_OTHER_STUDENT} (negado). Nenhum registro leva CPF, nome do aluno ou dado do
 * responsável.
 */
class AuthorshipStudentTeacherOperationsIT extends AdministrationStackBase {

    private static final String OTHER_CPF = "529.982.247-25";
    private static final String OTHER_CPF_DIGITS = "52998224725";

    private Cookie claraSession;
    private TeacherAccount clara;
    private UUID brunoStudent;

    @BeforeEach
    void prepare() throws Exception {
        Cookie admin = signInAdministrator(insertAdministrator());
        clara = createTeacher(admin, "Clara Souza", "clara@example.test");
        TeacherAccount bruno = createTeacher(admin, "Bruno Lima", "bruno@example.test");
        claraSession = signInTeacher(clara.accessCode());
        brunoStudent =
                registerMyStudent(
                        signInTeacher(bruno.accessCode()), adultStudent("Helena Prado", OTHER_CPF));
        sendText(USER_CLARA, clara.accessCode());
        clearAuthorship();
    }

    private void assertTeacherRecord(
            Map<String, Object> record, String channel, String result, UUID student) {
        assertRecord(record, "TEACHER", channel, result);
        assertThat(record.get("author_id")).isEqualTo(clara.id());
        assertThat(record.get("target_type")).isEqualTo("STUDENT");
        assertThat(record.get("target_id")).isEqualTo(student);
        assertThat(record.get("origin_hash")).isNull();
    }

    private void assertNoPersonalData() {
        assertThat(authorshipDump())
                .doesNotContain(CanaryCpf.DIGITS)
                .doesNotContain(OTHER_CPF_DIGITS)
                .doesNotContain("Helena")
                .doesNotContain("Ana Souza")
                .doesNotContain("Davi Lima")
                .doesNotContain("Marta");
    }

    // ------------------------------------------------------------------ Web

    @Test
    void webOperationsLeaveRecordsOfTheTeacherOnTheWebChannel() throws Exception {
        Map<String, Object> minor =
                minorStudent(
                        "Davi Lima",
                        "2016-03-14",
                        CanaryCpf.MASKED,
                        "Marta Lima",
                        "(11) 98888-7777",
                        "marta.lima@example.test");
        UUID student = registerMyStudent(claraSession, minor);
        String path = "/api/v1/my-students/" + student;
        assertThat(
                        call(HttpMethod.PATCH, path, claraSession, Map.of("instrument", "Flauta"))
                                .getStatus())
                .isEqualTo(200);
        assertThat(call(HttpMethod.POST, path + "/inactivation", claraSession).getStatus())
                .isEqualTo(200);
        assertThat(call(HttpMethod.POST, path + "/reactivation", claraSession).getStatus())
                .isEqualTo(200);

        for (String operation :
                List.of(
                        "STUDENT_REGISTER",
                        "STUDENT_UPDATE",
                        "STUDENT_INACTIVATE",
                        "STUDENT_REACTIVATE")) {
            assertTeacherRecord(onlyAuthorship(operation), "WEB", "SUCCESS", student);
        }
        assertThat(authorshipDump()).doesNotContain("98888").doesNotContain("marta.lima");
        assertNoPersonalData();
    }

    @Test
    void webRegisteringOrEditingToAnotherTeachersCpfLeavesTheDenialRecord() throws Exception {
        UUID own = registerMyStudent(claraSession, adultStudent("Ana Souza", CanaryCpf.MASKED));
        clearAuthorship();

        MockHttpServletResponse register =
                call(
                        HttpMethod.POST,
                        "/api/v1/my-students",
                        claraSession,
                        adultStudent("Novo Aluno", OTHER_CPF));
        MockHttpServletResponse edit =
                call(
                        HttpMethod.PATCH,
                        "/api/v1/my-students/" + own,
                        claraSession,
                        Map.of("cpf", OTHER_CPF));

        for (MockHttpServletResponse refusal : List.of(register, edit)) {
            assertThat(refusal.getStatus()).isEqualTo(409);
            assertThat(errorCode(refusal)).isEqualTo("CPF_UNAVAILABLE");
            assertThat(refusal.getContentAsString())
                    .doesNotContain("Helena")
                    .doesNotContain("Bruno");
        }
        List<Map<String, Object>> denials = authorship("CPF_REFUSED_OTHER_STUDENT");
        assertThat(denials).hasSize(2);
        for (Map<String, Object> denial : denials) {
            assertTeacherRecord(denial, "WEB", "DENIED", brunoStudent);
            assertThat(denial.get("error_code")).isEqualTo("CPF_UNAVAILABLE");
        }
        assertThat(authorship("STUDENT_REGISTER")).isEmpty();
        assertThat(authorship("STUDENT_UPDATE")).isEmpty();
        assertNoPersonalData();
    }

    @Test
    void webRegisteringOneOfTheTeachersOwnCpfIsNotADenial() throws Exception {
        registerMyStudent(claraSession, adultStudent("Ana Souza", CanaryCpf.MASKED));
        clearAuthorship();

        MockHttpServletResponse again =
                call(
                        HttpMethod.POST,
                        "/api/v1/my-students",
                        claraSession,
                        adultStudent("Ana Souza", CanaryCpf.MASKED));

        assertThat(again.getStatus()).isEqualTo(409);
        assertThat(errorCode(again)).isEqualTo("STUDENT_ALREADY_YOURS");
        assertThat(authorship()).isEmpty();
    }

    // ------------------------------------------------------------------ Telegram

    private JsonNode say(String text) throws Exception {
        API_BOTS.resetRequests();
        sendText(USER_CLARA, text);
        return lastMessage();
    }

    private JsonNode tapButton(String data) throws Exception {
        API_BOTS.resetRequests();
        sendCallback(USER_CLARA, data);
        return lastMessage();
    }

    private String confirmData(JsonNode message) {
        return buttons(message).stream()
                .filter(data -> data.startsWith("v1:reg-confirm:"))
                .findFirst()
                .orElseThrow();
    }

    private UUID onlyStudentOf(String name) {
        return jdbc.queryForObject("select id from student where name = ?", UUID.class, name);
    }

    @Test
    void telegramOperationsLeaveRecordsOfTheTeacherOnTheTelegramChannel() throws Exception {
        say("cadastrar aluno");
        say("Ana Souza");
        say("05/03/1990");
        say(CanaryCpf.MASKED);
        assertThat(calls("deleteMessage")).as("a mensagem com o CPF é apagada").hasSize(1);
        say("pular");
        assertThat(say("sim").path("text").asString()).startsWith("Pronto! Ana Souza");
        UUID student = onlyStudentOf("Ana Souza");
        assertTeacherRecord(onlyAuthorship("STUDENT_REGISTER"), "TELEGRAM", "SUCCESS", student);

        // editar o nome
        say("meus alunos");
        tapButton("v1:reg-open:0");
        tapButton("v1:reg-edit:");
        tapButton("v1:reg-edit:name");
        say("Ana Souza Lima");
        assertThat(say("sim").path("text").asString()).startsWith("Pronto! Os dados de");
        assertTeacherRecord(onlyAuthorship("STUDENT_UPDATE"), "TELEGRAM", "SUCCESS", student);

        // inativar
        say("meus alunos");
        tapButton("v1:reg-open:0");
        JsonNode inactivation = tapButton("v1:reg-inactivate:");
        assertThat(tapButton(confirmData(inactivation)).path("text").asString())
                .contains("foi inativado");
        assertTeacherRecord(onlyAuthorship("STUDENT_INACTIVATE"), "TELEGRAM", "SUCCESS", student);

        // reativar
        say("meus alunos");
        tapButton("v1:reg-filter:INACTIVE");
        tapButton("v1:reg-open:0");
        JsonNode reactivation = tapButton("v1:reg-reactivate:");
        assertThat(tapButton(confirmData(reactivation)).path("text").asString())
                .contains("foi reativado");
        assertTeacherRecord(onlyAuthorship("STUDENT_REACTIVATE"), "TELEGRAM", "SUCCESS", student);

        assertThat(authorship()).hasSize(4);
        authorship()
                .forEach(
                        row ->
                                assertThat((String) row.get("correlation_id"))
                                        .matches("[0-9]{1,10}"));
        assertNoPersonalData();
    }

    @Test
    void telegramAnotherTeachersCpfIsRefusedNeutrallyAndLeavesTheDenialRecord() throws Exception {
        say("cadastrar aluno");
        say("Pedro Alves");
        say("05/03/1990");
        String reply = say(OTHER_CPF).path("text").asString();

        assertThat(reply).startsWith("Não foi possível usar este CPF. Procure o administrador.");
        assertThat(reply).doesNotContain("Helena").doesNotContain("Bruno");
        Map<String, Object> denial = onlyAuthorship("CPF_REFUSED_OTHER_STUDENT");
        assertTeacherRecord(denial, "TELEGRAM", "DENIED", brunoStudent);
        assertThat(denial.get("error_code")).isEqualTo("CPF_UNAVAILABLE");
        assertThat(calls("deleteMessage")).hasSize(1);
        assertThat(jdbc.queryForObject("select count(*) from student", Integer.class)).isEqualTo(1);
        assertNoPersonalData();
    }
}
