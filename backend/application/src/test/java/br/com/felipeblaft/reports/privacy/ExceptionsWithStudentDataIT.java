package br.com.felipeblaft.reports.privacy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

import br.com.felipeblaft.reports.port.StudentRepository;
import br.com.felipeblaft.reports.support.CanaryCpf;
import br.com.felipeblaft.reports.support.CanaryStudent;
import br.com.felipeblaft.reports.support.LeakAudit;
import br.com.felipeblaft.reports.support.RealStackBase;
import br.com.felipeblaft.reports.support.ResponseSweeper.Place;
import br.com.felipeblaft.reports.support.ScenarioIT;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import jakarta.servlet.http.Cookie;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MvcResult;

/**
 * SC-004 (mensagens de exceção e rastros de pilha): quando a gravação do aluno falha com uma
 * exceção cuja mensagem e cuja causa trazem o CPF e os dados do aluno canário, nada disso chega à
 * resposta (Web do administrador e do professor), ao bot, ao log, ao rastro de pilha nem à autoria.
 * O log registra só o tipo e uma referência. Pilha real, com a gravação do aluno forçada a falhar.
 */
class ExceptionsWithStudentDataIT extends RealStackBase {

    private static final String POISON =
            "falha ao gravar "
                    + CanaryStudent.NAME
                    + " CPF "
                    + CanaryCpf.MASKED
                    + " nasc. "
                    + CanaryStudent.BIRTH_DATE_BR
                    + " resp. "
                    + CanaryStudent.GUARDIAN_NAME
                    + " "
                    + CanaryStudent.GUARDIAN_PHONE_MASKED
                    + " "
                    + CanaryStudent.GUARDIAN_EMAIL;

    @MockitoSpyBean StudentRepository students;

    private LeakAudit audit;
    private LeakAudit cpfOnly;
    private LeakAudit outbox;

    @BeforeEach
    void makeStudentWritesFail() {
        audit = LeakAudit.searching(CanaryStudent.sweepValuesWithCpf());
        cpfOnly = LeakAudit.searching(CanaryCpf.sweepValues());
        // a caixa de saída do canal guarda o resumo do cadastro (nome, nascimento e responsável)
        Map<String, String> notInSummary = new LinkedHashMap<>(CanaryStudent.sweepValuesWithCpf());
        notInSummary.remove("nome do aluno canário");
        notInSummary.remove("nome do responsável canário");
        notInSummary.remove("nascimento do aluno canário (BR)");
        outbox = LeakAudit.searching(notInSummary);
        RuntimeException failure =
                new IllegalStateException(POISON, new IllegalArgumentException("causa: " + POISON));
        doThrow(failure).when(students).insertIfAbsent(any());
        doThrow(failure).when(students).save(any());
    }

    @Test
    void webAdministratorAndTeacherAnswerWithoutTheExceptionContent() throws Exception {
        Cookie admin = insertAndSignInAsAdministrator();
        UUID teacher = createTeacher(admin, "Professora Alfa", "alfa@example.test").id();
        scenario.clara();
        Cookie clara = teacherSignIn(ScenarioIT.CODE_CLARA);
        logs.clear();

        MvcResult byAdmin =
                adminPost(admin, ADMIN_BASE + "/students", canaryStudentJson(List.of(teacher)));
        assertThat(byAdmin.getResponse().getStatus()).isEqualTo(500);
        assertThat(body(byAdmin).path("code").asString()).isEqualTo("INTERNAL_ERROR");
        audit.exchange("resposta ao administrador", byAdmin);

        MvcResult byTeacher =
                call(
                        asJson(
                                withCookie(
                                        org.springframework.test.web.servlet.request
                                                .MockMvcRequestBuilders.post("/api/v1/my-students"),
                                        clara),
                                canaryStudentJson(null)));
        assertThat(byTeacher.getResponse().getStatus()).isEqualTo(500);
        audit.exchange("resposta ao professor", byTeacher);

        assertErrorsLoggedWithoutContent();
        finish();
    }

    @Test
    void telegramConfirmationAnswersAndLogsWithoutTheExceptionContent() throws Exception {
        scenario.clara();
        link(USER_CLARA, ScenarioIT.CODE_CLARA);
        logs.clear();

        sendText(USER_CLARA, "cadastrar aluno");
        sendText(USER_CLARA, CanaryStudent.NAME);
        sendText(USER_CLARA, CanaryStudent.BIRTH_DATE_BR);
        sendText(USER_CLARA, CanaryCpf.MASKED);
        sendText(USER_CLARA, CanaryStudent.GUARDIAN_NAME);
        sendText(USER_CLARA, CanaryStudent.GUARDIAN_PHONE_MASKED);
        sendText(USER_CLARA, CanaryStudent.GUARDIAN_EMAIL);
        sendText(USER_CLARA, CanaryStudent.INSTRUMENT);
        List<String> labels = buttonLabels(lastMessage());
        String confirm = buttons(lastMessage()).get(labels.indexOf("Confirmar"));

        TelegramStep failed = telegramStep(() -> sendCallback(USER_CLARA, confirm));
        // a falha na gravação responde com a mensagem genérica; nada do aluno vai ao chat
        audit.text("ações do canal na falha", Place.ACTION_PAYLOAD, failed.channelActions());
        audit.text("API de bots na falha", Place.ACTION_PAYLOAD, failed.botCalls());
        assertThat(failed.botCalls()).contains("sendMessage");

        assertErrorsLoggedWithoutContent();
        finish();
    }

    /** O log registrou o erro (só tipo e referência) e nenhum evento carrega a exceção anexada. */
    private void assertErrorsLoggedWithoutContent() {
        List<ILoggingEvent> errors =
                logs.events().stream()
                        .filter(event -> event.getLevel().isGreaterOrEqual(Level.ERROR))
                        .toList();
        assertThat(errors).as("a falha foi registrada").isNotEmpty();
        assertThat(errors)
                .as("o evento de erro não anexa a exceção (mensagem, causa e pilha)")
                .allMatch(event -> event.getThrowableProxy() == null);
        assertThat(logs.messages())
                .as("o log traz só o tipo e uma referência para achar o erro")
                .anyMatch(message -> message.contains("reference=") && message.contains("type="));
    }

    private void finish() {
        audit.text("log", Place.LOG, logText());
        audit.text("rastros de exceção", Place.EXCEPTION_TRACE, traceText());
        audit.text("authorship_record", Place.AUTHORSHIP, authorshipText());
        for (String table : tables()) {
            // o aluno e o cadastro em andamento guardam o cadastro; neles o CPF só existe cifrado
            if (table.equals("student") || table.equals("student_registration_draft")) {
                cpfOnly.text("tabela " + table, Place.AUTHORSHIP, tableText(table));
            } else if (table.equals("channel_action")) {
                outbox.text("tabela " + table, Place.BOT_CARD, tableText(table));
            } else {
                audit.text("tabela " + table, Place.AUTHORSHIP, tableText(table));
            }
        }
        assertThat(audit.sweeps()).isGreaterThan(5);
        audit.assertClean();
        cpfOnly.assertClean();
        outbox.assertClean();
    }
}
