package br.com.felipeblaft.reports.security;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.felipeblaft.reports.support.CanaryCpf;
import br.com.felipeblaft.reports.support.ScenarioIT;
import jakarta.servlet.http.Cookie;
import java.util.List;
import java.util.concurrent.Callable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;

/**
 * Concorrência no cadastro de alunos, com Postgres real (T143, FR-024): dois professores
 * cadastrando o mesmo CPF ao mesmo tempo resultam em um aluno aceito e em uma recusa neutra para o
 * outro (a corrida é decidida pelo índice único, nunca por duas gravações); a confirmação do bot
 * tocada duas vezes ao mesmo tempo grava uma só vez.
 */
class StudentConcurrencyIT extends SecurityBase {

    private static final int ROUNDS = 4; // 5 recusas bloqueariam o perdedor

    private Cookie claraSession;
    private Cookie brunoSession;

    @BeforeEach
    void prepare() throws Exception {
        scenario.clara();
        scenario.bruno();
        claraSession = signInTeacher(ScenarioIT.CODE_CLARA);
        brunoSession = signInTeacher(ScenarioIT.CODE_BRUNO);
        link(USER_CLARA, ScenarioIT.CODE_CLARA);
    }

    private Callable<MvcResult> registration(Cookie session, String name, String cpf) {
        return () ->
                call(
                        HttpMethod.POST,
                        API + "/my-students",
                        "{\"name\":\""
                                + name
                                + "\",\"birthDate\":\"1990-03-04\",\"cpf\":\""
                                + cpf
                                + "\",\"instrument\":\"Piano\"}",
                        session);
    }

    @Test
    void twoTeachersRegisteringTheSameCpfAtTheSameTimeLeaveOneStudentAndOneNeutralRefusal()
            throws Exception {
        for (int round = 1; round <= ROUNDS; round++) {
            String cpf = validCpf(500 + round);
            List<MvcResult> results =
                    runTogether(
                            List.of(
                                    registration(claraSession, "Aluno da Clara " + round, cpf),
                                    registration(brunoSession, "Aluno do Bruno " + round, cpf)));

            List<Integer> statuses =
                    results.stream()
                            .map(result -> result.getResponse().getStatus())
                            .sorted()
                            .toList();
            assertThat(statuses).as("rodada %d", round).containsExactly(201, 409);
            MvcResult refused =
                    results.stream()
                            .filter(result -> result.getResponse().getStatus() == 409)
                            .findFirst()
                            .orElseThrow();
            assertThat(errorCode(refused)).as("rodada %d", round).isEqualTo("CPF_UNAVAILABLE");
            assertThat(bodyOf(refused)).doesNotContain("Aluno da", "Aluno do", "Clara", "Bruno");

            // um só aluno com esse CPF, associado só a quem ganhou
            JsonNode winner =
                    jsonOf(
                            results.stream()
                                    .filter(result -> result.getResponse().getStatus() == 201)
                                    .findFirst()
                                    .orElseThrow());
            assertThat(
                            jdbc.queryForObject(
                                    "select count(*) from student where name like ?",
                                    Integer.class,
                                    "%" + round))
                    .as("rodada %d", round)
                    .isEqualTo(1);
            assertThat(
                            jdbc.queryForObject(
                                    "select count(*) from teacher_student_assignment where student_id = ?::uuid",
                                    Integer.class,
                                    winner.path("id").asString()))
                    .isEqualTo(1);
        }
        assertThat(count("student")).isEqualTo(ROUNDS);
        assertThat(count("teacher_student_assignment")).isEqualTo(ROUNDS);
    }

    @Test
    void theBotConfirmationTouchedTwiceAtTheSameTimeSavesOnce() throws Exception {
        say(USER_CLARA, "cadastrar aluno");
        say(USER_CLARA, "Ana Souza");
        say(USER_CLARA, "05/03/1990");
        say(USER_CLARA, CanaryCpf.MASKED);
        Reply summary = say(USER_CLARA, "pular");
        String confirmation =
                summary.buttonData().stream()
                        .filter(data -> data.startsWith("v1:reg-confirm:"))
                        .findFirst()
                        .orElseThrow(() -> new AssertionError("sem resumo: " + summary.plain()));
        assertThat(count("student")).isZero();

        API_BOTS.resetRequests();
        runTogether(
                List.of(
                        () -> {
                            sendCallback(USER_CLARA, confirmation);
                            return null;
                        },
                        () -> {
                            sendCallback(USER_CLARA, confirmation);
                            return null;
                        }));

        assertThat(count("student")).as("uma só gravação").isEqualTo(1);
        assertThat(count("teacher_student_assignment")).isEqualTo(1);
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from authorship_record where operation = 'STUDENT_REGISTER'",
                                Integer.class))
                .isEqualTo(1);
        List<String> sent = reply().texts();
        assertThat(sent.stream().filter(text -> text.startsWith("Pronto!")).count())
                .as("a confirmação responde sucesso uma só vez")
                .isEqualTo(1);
        assertThat(String.join("\n", sent)).doesNotContain(CanaryCpf.DIGITS);
    }
}
