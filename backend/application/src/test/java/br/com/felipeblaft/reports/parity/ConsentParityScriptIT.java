package br.com.felipeblaft.reports.parity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import br.com.felipeblaft.reports.domain.access.Actor;
import br.com.felipeblaft.reports.domain.privacy.ConsentTermVersion;
import br.com.felipeblaft.reports.domain.registry.Student;
import br.com.felipeblaft.reports.domain.registry.Teacher;
import br.com.felipeblaft.reports.port.ConsentTermRepository;
import br.com.felipeblaft.reports.support.AudioNormalizationTest;
import br.com.felipeblaft.reports.support.ScenarioIT;
import br.com.felipeblaft.reports.support.TelegramConversationBase;
import br.com.felipeblaft.reports.usecase.privacy.RevokeConsentUseCase;
import jakarta.servlet.http.Cookie;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Paridade Web ≡ Telegram do consentimento do responsável (SC-005, R-16): cadastro de menor sem
 * consentimento recusado e com consentimento aceito, aluno adulto sem bloqueio, bloqueio da IA com
 * o mesmo código e o mesmo evento, registro pelo botão do aviso e novo bloqueio depois da revogação.
 * Pilha real, Postgres real; só o canal registrado muda.
 */
class ConsentParityScriptIT extends TelegramConversationBase {

    private static final String BLOCK_TEXT = "o consentimento do responsável ainda não foi registrado";

    @Autowired ConsentTermRepository terms;
    @Autowired RevokeConsentUseCase revoke;

    private Student adultWeb;
    private Student adultBot;
    private Student minorWeb;
    private Student minorBot;

    /** Operações do roteiro num canal; cada uma devolve um resultado neutro. */
    interface ConsentChannel {
        /** Cadastra um menor sem consentimento (a recusa deixa zero alunos). */
        String registerMinorWithoutConsent() throws Exception;

        /** Cadastra um menor com o consentimento do responsável. */
        String registerMinorWithConsent() throws Exception;

        /** Envia áudio para o aluno; devolve {@code ACCEPTED} ou o código do bloqueio. */
        String sendAudio(Student student) throws Exception;

        /** Registra o consentimento a partir do aviso de bloqueio. */
        String recordFromNotice(Student student) throws Exception;
    }

    @BeforeEach
    void prepare() {
        Teacher clara = scenario.clara();
        terms.save(
                new ConsentTermVersion(
                        UUID.randomUUID(),
                        1,
                        LocalDate.of(2026, 1, 1),
                        "Institutional text",
                        false,
                        UUID.randomUUID(),
                        clock.now()));
        adultWeb = adult(clara, "Ana Web");
        adultBot = adult(clara, "Ana Bot");
        minorWeb = scenario.studentWithoutConsent(clara, "Davi Web", "Piano");
        minorBot = scenario.studentWithoutConsent(clara, "Davi Bot", "Violão");
    }

    private Student adult(Teacher teacher, String name) {
        Student student = scenario.studentWithoutConsent(teacher, name, "Piano");
        jdbc.update("update student set birth_date = date '1990-03-05' where id = ?", student.id());
        return student;
    }

    // ---------------------------------------------------------------- consultas neutras

    private int studentsNamed(String name) {
        return jdbc.queryForObject(
                "select count(*) from student where name = ?", Integer.class, name);
    }

    /** Consentimentos do aluno pelo nome, com a origem trocada por um marcador. */
    private String consentsOf(String name, String origin) {
        return jdbc.queryForList(
                        "select c.relationship || ' v' || t.number || ' ' || c.recorded_origin"
                                + " from student_consent c join student s on s.id = c.student_id"
                                + " join consent_term_version t on t.id = c.term_version_id"
                                + " where s.name = ? order by c.recorded_at",
                        String.class,
                        name)
                .toString()
                .replace(origin, "CHANNEL");
    }

    private String consentsOf(Student student, String origin) {
        return jdbc.queryForList(
                        "select c.relationship || ' v' || t.number || ' ' || c.recorded_origin"
                                + " from student_consent c"
                                + " join consent_term_version t on t.id = c.term_version_id"
                                + " where c.student_id = ? order by c.recorded_at",
                        String.class,
                        student.id())
                .toString()
                .replace(origin, "CHANNEL");
    }

    /** Eventos de bloqueio do aluno, sem identificadores e com a origem trocada por um marcador. */
    private List<String> blockEvents(Student student, String origin) {
        return jdbc.queryForList(
                "select operation || ' ' || result || ' ' || error_code || ' ' || origin"
                        + " from authorship_record where target_id = ?"
                        + " and operation = 'CONSENT_BLOCKED_AI' order by sequence",
                String.class,
                student.id())
                .stream()
                .map(line -> line.replace(origin, "CHANNEL"))
                .toList();
    }

    private int aiCalls() {
        return jdbc.queryForObject("select count(*) from ai_call", Integer.class);
    }

    private void revokeAsAdministrator(Student student) {
        UUID consent =
                jdbc.queryForObject(
                        "select id from student_consent where student_id = ?"
                                + " order by recorded_at desc limit 1",
                        UUID.class,
                        student.id());
        revoke.run(
                Actor.administrator(UUID.randomUUID(), false),
                new RevokeConsentUseCase.Command(consent, clock.todayInSchoolTimezone(), "MOTHER", "77"));
    }

    private static ParityScript script(
            ConsentChannel channel, Student adult, Student minor, String origin, ParityHooks hooks) {
        return new ParityScript()
                .step("menor sem consentimento recusado", c -> channel.registerMinorWithoutConsent())
                .step("menor com consentimento aceito", c -> channel.registerMinorWithConsent())
                .step("adulto sem bloqueio", c -> channel.sendAudio(adult))
                .step("menor sem consentimento bloqueado", c -> channel.sendAudio(minor))
                .step("evento do bloqueio", c -> hooks.blockEvents(minor, origin))
                .step("registro pelo botão do aviso", c -> channel.recordFromNotice(minor))
                .step("depois do registro", c -> channel.sendAudio(minor))
                .step(
                        "revogação pelo administrador",
                        c -> {
                            hooks.revoke(minor);
                            return "REVOKED";
                        })
                .step("bloqueio depois da revogação", c -> channel.sendAudio(minor));
    }

    /** Ganchos do teste usados pelo roteiro (consultas e a revogação, iguais nos dois canais). */
    interface ParityHooks {
        List<String> blockEvents(Student student, String origin);

        void revoke(Student student);
    }

    private ParityHooks hooks() {
        return new ParityHooks() {
            @Override
            public List<String> blockEvents(Student student, String origin) {
                return ConsentParityScriptIT.this.blockEvents(student, origin);
            }

            @Override
            public void revoke(Student student) {
                revokeAsAdministrator(student);
            }
        };
    }

    // ---------------------------------------------------------------- Web

    private Map<String, Object> studentBody(String name, String cpf, boolean withConsent) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", name);
        body.put("birthDate", "2016-03-14");
        body.put("cpf", cpf);
        body.put("instrument", "Violão");
        body.put(
                "guardian",
                Map.of(
                        "name", "Marta Lima",
                        "phone", "(11) 98888-7777",
                        "email", "marta.lima@example.test"));
        if (withConsent) {
            body.put("consent", consentBody());
        }
        return body;
    }

    private static Map<String, Object> consentBody() {
        return Map.of(
                "consentingName", "Maria Responsável",
                "relationship", "MOTHER",
                "consentedOn", "2026-09-01",
                "termVersion", 1,
                "collectionMethod", "DIGITAL");
    }

    private ConsentChannel web(Cookie session) {
        return new ConsentChannel() {
            private int send(String path, Object body) throws Exception {
                return mvc.perform(
                                post(path)
                                        .cookie(session)
                                        .with(csrf().asHeader())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(JSON.writeValueAsString(body)))
                        .andReturn()
                        .getResponse()
                        .getStatus();
            }

            @Override
            public String registerMinorWithoutConsent() throws Exception {
                send("/api/v1/my-students", studentBody("Bia Web", "390.533.447-05", false));
                return "alunos=" + studentsNamed("Bia Web");
            }

            @Override
            public String registerMinorWithConsent() throws Exception {
                send("/api/v1/my-students", studentBody("Bia Web", "390.533.447-05", true));
                return "alunos="
                        + studentsNamed("Bia Web")
                        + " "
                        + consentsOf("Bia Web", "WEB");
            }

            @Override
            public String sendAudio(Student student) throws Exception {
                MvcResult result =
                        mvc.perform(
                                        multipart(
                                                        "/api/v1/students/{id}/lesson-reports",
                                                        student.id())
                                                .file(
                                                        new MockMultipartFile(
                                                                "audio",
                                                                "lesson.webm",
                                                                "audio/webm",
                                                                AudioNormalizationTest
                                                                        .AUDIO_WITH_SPEECH))
                                                .cookie(session)
                                                .with(csrf().asHeader()))
                                .andReturn();
                int status = result.getResponse().getStatus();
                if (status / 100 == 2) {
                    scenario.processQueue();
                    return "ACCEPTED";
                }
                return JSON.readTree(result.getResponse().getContentAsString())
                        .path("code")
                        .asString();
            }

            @Override
            public String recordFromNotice(Student student) throws Exception {
                int status =
                        send("/api/v1/my-students/" + student.id() + "/consents", consentBody());
                return (status / 100 == 2 ? "SUCCESS " : "FAILED ")
                        + consentsOf(student, "WEB");
            }
        };
    }

    // ---------------------------------------------------------------- Telegram

    private void say(String text) throws Exception {
        sendText(USER_CLARA, text);
    }

    private void tap(String data) throws Exception {
        sendCallback(USER_CLARA, data);
    }

    private void consentSteps() throws Exception {
        tap("v1:consent-yes:");
        say("Maria Responsável");
        tap("v1:consent-rel:MOTHER");
        tap("v1:consent-date:today");
        tap("v1:consent-method:DIGITAL");
    }

    private void guardianSteps(String name, String cpf) throws Exception {
        say("cadastrar aluno");
        say(name);
        say("14/03/2016");
        say(cpf);
        say("Marta Lima");
        say("(11) 98888-7777");
        say("marta.lima@example.test");
    }

    private ConsentChannel telegram() {
        return new ConsentChannel() {
            @Override
            public String registerMinorWithoutConsent() throws Exception {
                guardianSteps("Bia Bot", "111.444.777-35");
                tap("v1:consent-no:");
                return "alunos=" + studentsNamed("Bia Bot");
            }

            @Override
            public String registerMinorWithConsent() throws Exception {
                guardianSteps("Bia Bot", "111.444.777-35");
                consentSteps();
                say("pular");
                say("sim");
                return "alunos="
                        + studentsNamed("Bia Bot")
                        + " "
                        + consentsOf("Bia Bot", "TELEGRAM");
            }

            @Override
            public String sendAudio(Student student) throws Exception {
                API_BOTS.resetRequests();
                say("registrar aula");
                say(student.name());
                String file = "lesson-" + student.id() + "-" + System.nanoTime();
                prepareTelegramFile(file, AudioNormalizationTest.AUDIO_WITH_SPEECH);
                sendVoice(USER_CLARA, file, 1000);
                tap("v1:confirm-student:");
                boolean blocked = sentTexts().stream().anyMatch(t -> t.contains(BLOCK_TEXT));
                if (blocked) {
                    return "CONSENT_REQUIRED";
                }
                scenario.processQueue();
                return "ACCEPTED";
            }

            @Override
            public String recordFromNotice(Student student) throws Exception {
                tap("v1:consent-record:" + student.id());
                consentSteps();
                say("sim");
                return "SUCCESS " + consentsOf(student, "TELEGRAM");
            }
        };
    }

    @Test
    void sameScriptSameConsentResultInBothChannels() throws Exception {
        Cookie session = signInOnWeb();
        link(USER_CLARA, ScenarioIT.CODE_CLARA);

        List<String> web =
                script(web(session), adultWeb, minorWeb, "WEB", hooks()).run(null);
        List<String> bot =
                script(telegram(), adultBot, minorBot, "TELEGRAM", hooks()).run(null);

        assertThat(bot).containsExactlyElementsOf(web);
        assertThat(web)
                .containsExactly(
                        "menor sem consentimento recusado = alunos=0",
                        "menor com consentimento aceito = alunos=1 [MOTHER v1 CHANNEL]",
                        "adulto sem bloqueio = ACCEPTED",
                        "menor sem consentimento bloqueado = CONSENT_REQUIRED",
                        "evento do bloqueio = [CONSENT_BLOCKED_AI BLOCKED CONSENT_REQUIRED CHANNEL]",
                        "registro pelo botão do aviso = SUCCESS [MOTHER v1 CHANNEL]",
                        "depois do registro = ACCEPTED",
                        "revogação pelo administrador = REVOKED",
                        "bloqueio depois da revogação = CONSENT_REQUIRED");
        // SC-005: o bloqueio nunca chega ao provedor: só os dois relatórios aceitos geraram chamadas
        assertThat(aiCalls()).isPositive();
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from ai_call where report_id is null", Integer.class))
                .isZero();
    }

    private Cookie signInOnWeb() throws Exception {
        MvcResult result =
                mvc.perform(
                                post("/api/v1/session")
                                        .with(csrf().asHeader())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                "{\"code\":\"" + ScenarioIT.CODE_CLARA + "\"}"))
                        .andReturn();
        Cookie cookie = result.getResponse().getCookie("session");
        return new Cookie(cookie.getName(), cookie.getValue());
    }
}
