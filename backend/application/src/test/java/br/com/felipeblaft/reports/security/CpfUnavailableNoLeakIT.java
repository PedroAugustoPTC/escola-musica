package br.com.felipeblaft.reports.security;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.felipeblaft.reports.domain.registry.Teacher;
import br.com.felipeblaft.reports.support.CanaryCpf;
import br.com.felipeblaft.reports.support.LogCapture;
import br.com.felipeblaft.reports.support.ResponseSweeper;
import br.com.felipeblaft.reports.support.ResponseSweeper.Place;
import br.com.felipeblaft.reports.support.ScenarioIT;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.MvcResult;

/**
 * CPF indisponível sem vazamento (T139, FR-024): o CPF de um aluno de outro professor é recusado
 * no cadastro e na edição, na Web e no Telegram, sem nome do aluno, nome ou e-mail do professor
 * dono, nem dado algum na resposta, no texto do bot, no log e na autoria; o CPF de um aluno do
 * próprio professor responde "já está entre os seus". Os limites contra sondagem (5 recusas em 15
 * minutos; 30 consultas em 60 minutos) bloqueiam só as operações que informam CPF e dizem o tempo
 * restante sem dizer o motivo.
 */
class CpfUnavailableNoLeakIT extends SecurityBase {

    private static final String OWN_CPF = "529.982.247-25";
    private static final String UNAVAILABLE =
            "Não foi possível usar este CPF. Procure o administrador.";
    private static final String OWN_STUDENT_TEXT = "Este aluno já está entre os seus.";

    private static final String BLOCKED_TEXT =
            "Esta operação está indisponível no momento[.] Tente novamente em [0-9]+ minuto[(]s[)][.]";
    private static final String OTHER_STUDENT_NAME = "Joana Cantora";
    private static final String OTHER_TEACHER_NAME = "Bruno Lima";
    private static final String OTHER_TEACHER_EMAIL = "bruno@example.test";

    private Teacher clara;
    private Cookie claraSession;
    private Cookie brunoSession;
    private UUID claraStudentId;
    private UUID otherStudentId;

    /** Dados que o professor A nunca pode ver: o aluno, o professor dono e o CPF canário. */
    private static ResponseSweeper sweeper() {
        Map<String, String> values = new LinkedHashMap<>(CanaryCpf.sweepValues());
        values.put("nome do aluno do outro professor", OTHER_STUDENT_NAME);
        values.put("sobrenome do aluno do outro professor", "Cantora");
        values.put("nome do outro professor", OTHER_TEACHER_NAME);
        values.put("e-mail do outro professor", OTHER_TEACHER_EMAIL);
        return ResponseSweeper.searching(values);
    }

    @BeforeEach
    void prepare() throws Exception {
        clara = scenario.clara();
        scenario.bruno();
        claraSession = signInTeacher(ScenarioIT.CODE_CLARA);
        brunoSession = signInTeacher(ScenarioIT.CODE_BRUNO);
        // o aluno de Bruno, com o CPF canário
        MvcResult other =
                register(brunoSession, OTHER_STUDENT_NAME, CanaryCpf.MASKED, "1990-03-04");
        assertThat(other.getResponse().getStatus()).isEqualTo(201);
        otherStudentId = UUID.fromString(jsonOf(other).path("id").asString());
        // o aluno de Clara, com o CPF dela
        MvcResult own = register(claraSession, "Matteo Mafra", OWN_CPF, "1992-07-01");
        assertThat(own.getResponse().getStatus()).isEqualTo(201);
        claraStudentId = UUID.fromString(jsonOf(own).path("id").asString());
        link(USER_CLARA, ScenarioIT.CODE_CLARA);
    }

    private MvcResult register(Cookie session, String name, String cpf, String birthDate)
            throws Exception {
        return call(
                HttpMethod.POST,
                API + "/my-students",
                "{\"name\":\""
                        + name
                        + "\",\"birthDate\":\""
                        + birthDate
                        + "\",\"cpf\":\""
                        + cpf
                        + "\",\"instrument\":\"Piano\"}",
                session);
    }

    private MvcResult editCpf(Cookie session, UUID student, String cpf) throws Exception {
        return call(
                HttpMethod.PATCH,
                API + "/my-students/" + student,
                "{\"cpf\":\"" + cpf + "\"}",
                session);
    }

    private String cpfHashOf(UUID student) {
        return jdbc.queryForObject("select cpf_hash from student where id = ?", String.class, student);
    }

    // ------------------------------------------------------------------ recusa sem vazamento

    @Test
    void duplicateCpfOfAnotherTeachersStudentIsRefusedOnTheWebWithoutAnyDataOnResponseLogOrAuthorship()
            throws Exception {
        String hashBefore = cpfHashOf(claraStudentId);
        int studentsBefore = count("student");
        try (LogCapture log = LogCapture.start()) {
            MvcResult registration = register(claraSession, "Outra Pessoa", CanaryCpf.MASKED, "1985-01-02");
            MvcResult edition = editCpf(claraSession, claraStudentId, CanaryCpf.DIGITS);

            for (MvcResult refused : List.of(registration, edition)) {
                assertThat(errorCode(refused)).isEqualTo("CPF_UNAVAILABLE");
                assertThat(refused.getResponse().getStatus()).isEqualTo(409);
                assertThat(jsonOf(refused).path("message").asString()).isEqualTo(UNAVAILABLE);
                sweeper().assertClean(refused);
            }
            sweeper().assertClean(Place.LOG, log.text());
        }
        assertThat(count("student")).as("nada foi cadastrado").isEqualTo(studentsBefore);
        assertThat(cpfHashOf(claraStudentId)).as("o CPF do aluno não mudou").isEqualTo(hashBefore);
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from teacher_student_assignment where student_id = ?",
                                Integer.class,
                                otherStudentId))
                .as("o aluno de Bruno segue só dele")
                .isEqualTo(1);

        // a autoria registra a recusa do professor, sem CPF, nome ou e-mail
        List<Map<String, Object>> refusals =
                jdbc.queryForList(
                        "select author_id, author_role, origin, result, error_code from authorship_record"
                                + " where operation = 'CPF_REFUSED_OTHER_STUDENT'");
        assertThat(refusals).hasSize(2);
        assertThat(refusals)
                .allSatisfy(
                        row -> {
                            assertThat(row.get("author_id")).isEqualTo(clara.id());
                            assertThat(row.get("author_role")).isEqualTo("TEACHER");
                            assertThat(row.get("origin")).isEqualTo("WEB");
                            assertThat(row.get("result")).isEqualTo("DENIED");
                            assertThat(row.get("error_code")).isEqualTo("CPF_UNAVAILABLE");
                        });
        sweeper().assertClean(Place.AUTHORSHIP, authorshipText());
        assertThat(authorshipText()).doesNotContain(OTHER_TEACHER_NAME, OTHER_STUDENT_NAME);
    }

    private String authorshipText() {
        return String.join(
                "\n",
                jdbc.query(
                        "select t::text from authorship_record t",
                        (rs, row) -> rs.getString(1)));
    }

    @Test
    void cpfOfTheTeachersOwnStudentSaysItIsAlreadyAmongTheirs() throws Exception {
        MvcResult second = register(claraSession, "Segunda Aluna", validCpf(7), "1991-05-05");
        UUID secondId = UUID.fromString(jsonOf(second).path("id").asString());

        MvcResult registration = register(claraSession, "Matteo De Novo", OWN_CPF, "1992-07-01");
        MvcResult edition = editCpf(claraSession, secondId, OWN_CPF);

        for (MvcResult answer : List.of(registration, edition)) {
            assertThat(errorCode(answer)).isEqualTo("STUDENT_ALREADY_YOURS");
            assertThat(jsonOf(answer).path("message").asString()).isEqualTo(OWN_STUDENT_TEXT);
            sweeper().assertClean(answer);
        }
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from authorship_record where operation = 'CPF_REFUSED_OTHER_STUDENT'",
                                Integer.class))
                .as("o CPF do próprio aluno não é recusa de CPF alheio")
                .isZero();
    }

    @Test
    void duplicateCpfIsRefusedOnTheBotWithoutAnyDataOnTheTextTheLogOrTheAuthorship() throws Exception {
        try (LogCapture log = LogCapture.start()) {
            // cadastro com o CPF do aluno de Bruno
            Reply registration = startRegistrationAndTypeCpf(CanaryCpf.MASKED);
            assertThat(registration.plain()).contains(UNAVAILABLE);
            assertThat(sweeper().sweepText(Place.BODY, registration.plain() + registration.buttonData()))
                    .isEmpty();
            say(USER_CLARA, "cancelar");

            // edição do CPF do aluno de Clara para o do aluno de Bruno
            say(USER_CLARA, "meus alunos");
            tap(USER_CLARA, "v1:reg-open:0");
            tap(USER_CLARA, "v1:reg-edit:");
            tap(USER_CLARA, "v1:reg-edit:cpf");
            Reply edition = say(USER_CLARA, CanaryCpf.MASKED);
            assertThat(edition.plain()).contains(UNAVAILABLE);
            assertThat(sweeper().sweepText(Place.BODY, edition.plain() + edition.buttonData()))
                    .isEmpty();
            say(USER_CLARA, "cancelar");

            sweeper().assertClean(Place.LOG, log.text());
        }
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from authorship_record where operation = 'CPF_REFUSED_OTHER_STUDENT'"
                                        + " and origin = 'TELEGRAM' and author_id = ?",
                                Integer.class,
                                clara.id()))
                .isEqualTo(2);
        sweeper().assertClean(Place.AUTHORSHIP, authorshipText());
    }

    @Test
    void ownCpfOnTheBotSaysTheStudentIsAlreadyAmongTheirs() throws Exception {
        Reply registration = startRegistrationAndTypeCpf(OWN_CPF);

        assertThat(registration.plain()).contains(OWN_STUDENT_TEXT).doesNotContain(UNAVAILABLE);
        assertThat(sweeper().sweepText(Place.BODY, registration.plain())).isEmpty();
    }

    private Reply startRegistrationAndTypeCpf(String cpf) throws Exception {
        say(USER_CLARA, "cadastrar aluno");
        say(USER_CLARA, "Ana Souza");
        say(USER_CLARA, "05/03/1990");
        return say(USER_CLARA, cpf);
    }

    // ------------------------------------------------------------------ limites contra sondagem

    @Test
    void fiveRefusalsBlockOnlyTheOperationsWithCpfAndTellTheRemainingTimeWithoutTheReason()
            throws Exception {
        for (int attempt = 1; attempt <= 5; attempt++) {
            MvcResult refused = register(claraSession, "Tentativa " + attempt, CanaryCpf.MASKED, "1985-01-02");
            assertThat(errorCode(refused)).as("tentativa %d", attempt).isEqualTo("CPF_UNAVAILABLE");
        }

        // a sexta, mesmo com um CPF livre, está bloqueada, com o tempo restante e sem o motivo
        MvcResult blocked = register(claraSession, "Sexta Tentativa", validCpf(11), "1985-01-02");
        assertBlockedWithoutReason(blocked, 15);
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from student where name = 'Sexta Tentativa'",
                                Integer.class))
                .isZero();
        // a edição com CPF também
        assertBlockedWithoutReason(editCpf(claraSession, claraStudentId, validCpf(12)), 15);

        // o que não informa CPF segue funcionando, na Web e no Telegram
        assertThat(
                        call(
                                        HttpMethod.PATCH,
                                        API + "/my-students/" + claraStudentId,
                                        "{\"instrument\":\"Violão\"}",
                                        claraSession)
                                .getResponse()
                                .getStatus())
                .isEqualTo(200);
        assertThat(
                        call(HttpMethod.GET, API + "/my-students", null, claraSession)
                                .getResponse()
                                .getStatus())
                .isEqualTo(200);
        assertThat(say(USER_CLARA, "meus alunos").plain()).contains("Matteo Mafra");
        say(USER_CLARA, "cadastrar aluno");
        say(USER_CLARA, "Ana Souza");
        say(USER_CLARA, "05/03/1990");
        Reply botBlocked = say(USER_CLARA, validCpf(13));
        assertThat(botBlocked.plain()).containsPattern(BLOCKED_TEXT);
        assertThat(botBlocked.plain().toLowerCase()).doesNotContain("cpf");
        say(USER_CLARA, "cancelar");

        // passado o bloqueio, volta a valer
        clock.advance(Duration.ofMinutes(16));
        MvcResult free = register(claraSession, "Depois Do Bloqueio", validCpf(14), "1985-01-02");
        assertThat(free.getResponse().getStatus()).isEqualTo(201);
    }

    @Test
    void thirtyLookupsInAnHourBlockEvenWithFreeCpfsAndTheLockLastsAnHour() throws Exception {
        // a primeira consulta foi o cadastro de Clara no preparo e a de Bruno é de outro professor
        int accepted = 0;
        MvcResult last = null;
        for (int i = 1; i <= 40; i++) {
            last = register(claraSession, "Aluno " + i, validCpf(1000 + i), "1990-01-01");
            if (last.getResponse().getStatus() != 201) {
                break;
            }
            accepted++;
        }
        assertThat(last.getResponse().getStatus()).isEqualTo(429);
        assertThat(errorCode(last)).isEqualTo("OPERATION_TEMPORARILY_BLOCKED");
        // 30 consultas por hora: a do preparo (1) mais as aceitas somam 30
        assertThat(accepted + 1).as("consultas aceitas antes do bloqueio").isEqualTo(30);
        assertBlockedWithoutReason(last, 60);

        // sem CPF, tudo segue
        assertThat(
                        call(
                                        HttpMethod.PATCH,
                                        API + "/my-students/" + claraStudentId,
                                        "{\"instrument\":\"Violão\"}",
                                        claraSession)
                                .getResponse()
                                .getStatus())
                .isEqualTo(200);

        clock.advance(Duration.ofMinutes(30));
        assertThat(errorCode(register(claraSession, "Ainda Bloqueado", validCpf(2001), "1990-01-01")))
                .isEqualTo("OPERATION_TEMPORARILY_BLOCKED");
        clock.advance(Duration.ofMinutes(31));
        assertThat(
                        register(claraSession, "Liberado", validCpf(2002), "1990-01-01")
                                .getResponse()
                                .getStatus())
                .isEqualTo(201);
    }

    private void assertBlockedWithoutReason(MvcResult blocked, int minutes) {
        assertThat(blocked.getResponse().getStatus()).isEqualTo(429);
        assertThat(errorCode(blocked)).isEqualTo("OPERATION_TEMPORARILY_BLOCKED");
        String message = jsonOf(blocked).path("message").asString();
        // o texto é só a indisponibilidade e o tempo: nenhuma palavra sobre o motivo
        assertThat(message).matches(BLOCKED_TEXT);
        int retryAfter = jsonOf(blocked).path("retryAfterSeconds").asInt();
        assertThat(retryAfter).isBetween(1, minutes * 60);
        assertThat(blocked.getResponse().getHeader("Retry-After")).isEqualTo(String.valueOf(retryAfter));
        sweeper().assertClean(blocked);
    }

}
