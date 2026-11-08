package br.com.felipeblaft.reports.privacy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import br.com.felipeblaft.reports.support.CanaryCpf;
import br.com.felipeblaft.reports.support.CanaryStudent;
import br.com.felipeblaft.reports.support.LeakAudit;
import br.com.felipeblaft.reports.support.RealStackBase;
import br.com.felipeblaft.reports.support.ResponseSweeper;
import br.com.felipeblaft.reports.support.ResponseSweeper.Place;
import br.com.felipeblaft.reports.support.ScenarioIT;
import jakarta.servlet.http.Cookie;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

/**
 * SC-004 (professor, Web e Telegram): o CPF canário nunca aparece em log, resposta, cabeçalho, URL,
 * mensagem do bot, resumo de confirmação, ação de saída, rastro de exceção, autoria nem (em claro)
 * no banco. O aluno canário só aparece na resposta ao professor autorizado e no cartão do bot
 * (contracts/telegram-student-registry.md §3.1 e §3.2: nome e nome do responsável); data de
 * nascimento, telefone e e-mail do responsável nunca saem pelo bot. Pilha real, nenhum dublê.
 */
class StudentCpfCanaryTeacherIT extends RealStackBase {

    private static final long USER_UNLINKED = 7003L;
    private static final String OTHER_CPF = "123.456.789-09";
    private static final String MY_STUDENTS = "/api/v1/my-students";

    private LeakAudit cpf;
    private LeakAudit data;
    private LeakAudit cardData;
    private ResponseSweeper successSweeper;

    @BeforeEach
    void prepare() throws Exception {
        scenario.clara();
        scenario.bruno();
        cpf = LeakAudit.searching(CanaryCpf.sweepValues());
        data = LeakAudit.searching(CanaryStudent.sweepValues());
        // o cartão (§3.2) e o resumo (§3.1, item 5) mostram o nome do aluno, o nome do responsável
        // e,
        // no resumo, o nascimento como dd/mm/aaaa; telefone, e-mail e o CPF nunca saem pelo bot
        Map<String, String> notInCard = new LinkedHashMap<>(CanaryStudent.sweepValues());
        notInCard.remove("nome do aluno canário");
        notInCard.remove("nome do responsável canário");
        notInCard.remove("nascimento do aluno canário (BR)");
        cardData = LeakAudit.searching(notInCard);
        successSweeper =
                ResponseSweeper.searching(CanaryStudent.sweepValues()).allowing(Place.BODY);
    }

    // ---------------------------------------------------------------- Web

    private MvcResult seen(String step, MvcResult result) {
        cpf.exchange(step, result);
        boolean success = result.getResponse().getStatus() / 100 == 2;
        // fora do sucesso a resposta é neutra: nenhum dado do aluno, nem o nome
        data.exchange(
                step,
                result,
                success ? successSweeper : ResponseSweeper.searching(CanaryStudent.sweepValues()));
        return result;
    }

    private MvcResult seenWithCpfInRequestUrl(String step, MvcResult result) {
        cpf.exchange(
                step,
                result,
                ResponseSweeper.searching(CanaryCpf.sweepValues()).allowing(Place.URL));
        data.exchange(step, result);
        return result;
    }

    private MvcResult teacherPost(Cookie session, String path, String json) throws Exception {
        var request = withCookie(post(path), session);
        return call(json == null ? request : asJson(request, json));
    }

    @Test
    void webFlowsKeepTheCpfAndTheStudentOutOfEverythingButTheAuthorizedResponse() throws Exception {
        Cookie clara = teacherSignIn(ScenarioIT.CODE_CLARA);
        Cookie bruno = teacherSignIn(ScenarioIT.CODE_BRUNO);
        logs.clear();

        MvcResult created =
                seen("cadastro canário", teacherPost(clara, MY_STUDENTS, canaryStudentJson(null)));
        assertThat(created.getResponse().getStatus()).isEqualTo(201);
        assertThat(text(created)).contains(CanaryStudent.NAME);
        String id = body(created).path("id").asString();

        seen("lista de alunos", call(get(MY_STUDENTS).cookie(clara)));
        seen("busca por nome", call(get(MY_STUDENTS).cookie(clara).param("search", "pietra")));
        seen("abertura do aluno", call(get(MY_STUDENTS + "/" + id).cookie(clara)));

        // duplicidade: o professor do aluno e outro professor recebem recusa neutra
        MvcResult own =
                seen(
                        "duplicidade com aluno próprio",
                        teacherPost(clara, MY_STUDENTS, duplicateJson()));
        assertThat(own.getResponse().getStatus()).isEqualTo(409);
        MvcResult foreign =
                seen(
                        "duplicidade com aluno alheio",
                        teacherPost(bruno, MY_STUDENTS, duplicateJson()));
        assertThat(foreign.getResponse().getStatus()).isEqualTo(409);
        assertThat(text(foreign)).doesNotContain("Clara").doesNotContain("Souza");
        MvcResult foreignRead =
                seen(
                        "abertura por outro professor",
                        call(get(MY_STUDENTS + "/" + id).cookie(bruno)));
        assertThat(foreignRead.getResponse().getStatus()).isEqualTo(404);

        // edição, inativação e reativação
        mvcStatus(
                seen(
                        "edição do responsável",
                        call(
                                asJson(
                                        withCookie(patch(MY_STUDENTS + "/" + id), clara),
                                        studentJson(
                                                null,
                                                null,
                                                OTHER_CPF,
                                                CanaryStudent.GUARDIAN_NAME,
                                                CanaryStudent.GUARDIAN_PHONE_DIGITS,
                                                CanaryStudent.GUARDIAN_EMAIL,
                                                "Violino",
                                                null)))),
                200);
        mvcStatus(
                seen(
                        "inativação",
                        teacherPost(clara, MY_STUDENTS + "/" + id + "/inactivation", null)),
                200);
        mvcStatus(
                seen(
                        "reativação",
                        teacherPost(clara, MY_STUDENTS + "/" + id + "/reactivation", null)),
                200);

        // validação com o CPF canário em lugares errados e corpos malformados
        invalid(
                clara,
                "nome em branco",
                studentJson(" ", "2000-01-01", CanaryCpf.MASKED, null, null, null, null, null));
        invalid(
                clara,
                "CPF no campo de nascimento",
                studentJson(
                        "Nome Válido",
                        CanaryCpf.DIGITS,
                        "529.982.247-25",
                        null,
                        null,
                        null,
                        null,
                        null));
        invalid(
                clara,
                "CPF no telefone do responsável",
                studentJson(
                        "Menor Válido",
                        "2016-03-14",
                        "987.654.321-00",
                        "Responsável Válido",
                        CanaryCpf.MASKED,
                        "g@example.test",
                        null,
                        null));
        invalid(
                clara,
                "CPF no e-mail do responsável",
                studentJson(
                        "Menor Válido",
                        "2016-03-14",
                        "987.654.321-00",
                        "Responsável Válido",
                        "(11) 98888-7777",
                        CanaryCpf.MASKED,
                        null,
                        null));
        invalid(
                clara,
                "CPF com dígito verificador errado",
                studentJson(
                        "Nome Válido",
                        "2000-01-01",
                        "111.444.777-36",
                        null,
                        null,
                        null,
                        null,
                        null));
        invalid(
                clara,
                "menor sem responsável",
                studentJson(
                        "Menor Sem Responsável",
                        "2016-03-14",
                        "987.654.321-00",
                        null,
                        null,
                        null,
                        null,
                        null));
        invalid(
                clara,
                "corpo truncado",
                "{\"name\":\"Nome Válido\",\"cpf\":\"" + CanaryCpf.MASKED + "\",\"birthDate\":");
        invalid(
                clara,
                "CPF como número",
                "{\"name\":\"Nome Válido\",\"cpf\":" + CanaryCpf.DIGITS + "}");
        invalid(clara, "lista no lugar do texto", "{\"name\":[\"" + CanaryCpf.MASKED + "\"]}");

        // a busca com forma de CPF é recusada sem ser repetida
        MvcResult probe =
                seenWithCpfInRequestUrl(
                        "busca com forma de CPF",
                        call(get(MY_STUDENTS).cookie(clara).param("search", CanaryCpf.DIGITS)));
        assertThat(probe.getResponse().getStatus()).isEqualTo(400);

        finish("Web");
    }

    private void invalid(Cookie session, String step, String json) throws Exception {
        MvcResult result = seen(step, teacherPost(session, MY_STUDENTS, json));
        assertThat(result.getResponse().getStatus()).as(step).isBetween(400, 499);
    }

    private static String duplicateJson() {
        return studentJson(
                "Outro Nome Fictício",
                "1991-02-03",
                CanaryCpf.DIGITS,
                null,
                null,
                null,
                null,
                null);
    }

    // ---------------------------------------------------------------- Telegram

    /** Um passo que o bot só pode responder sem nenhum dado do aluno. */
    private TelegramStep strict(String step, TelegramAction action) throws Exception {
        TelegramStep result = telegramStep(action);
        cpf.text(step + " (API de bots)", Place.BOT_CARD, result.botCalls());
        cpf.text(step + " (ações do canal)", Place.ACTION_PAYLOAD, result.channelActions());
        data.text(step + " (API de bots)", Place.ACTION_PAYLOAD, result.botCalls());
        data.text(step + " (ações do canal)", Place.ACTION_PAYLOAD, result.channelActions());
        return result;
    }

    /** Um passo de cartão ou resumo: pode mostrar o nome do aluno e o do responsável, nada mais. */
    private TelegramStep card(String step, TelegramAction action) throws Exception {
        TelegramStep result = telegramStep(action);
        cpf.text(step + " (API de bots)", Place.BOT_CARD, result.botCalls());
        cpf.text(step + " (ações do canal)", Place.ACTION_PAYLOAD, result.channelActions());
        cardData.text(step + " (API de bots)", Place.BOT_CARD, result.botCalls());
        cardData.text(step + " (ações do canal)", Place.ACTION_PAYLOAD, result.channelActions());
        return result;
    }

    private String lastButtonWith(String label) {
        List<String> labels = buttonLabels(lastMessage());
        List<String> codes = buttons(lastMessage());
        for (int i = 0; i < labels.size(); i++) {
            if (labels.get(i).contains(label)) {
                return codes.get(i);
            }
        }
        throw new AssertionError("botão \"" + label + "\" não encontrado: " + labels);
    }

    @Test
    void telegramFlowsKeepTheCpfAndTheStudentOutOfEverythingButTheCard() throws Exception {
        link(USER_CLARA, ScenarioIT.CODE_CLARA);
        link(USER_BRUNO, ScenarioIT.CODE_BRUNO);
        logs.clear();

        // cadastro guiado de um menor, com o CPF canário
        strict("menu de cadastro", () -> sendText(USER_CLARA, "cadastrar aluno"));
        strict("pergunta do nome", () -> sendText(USER_CLARA, CanaryStudent.NAME));
        strict("pergunta do nascimento", () -> sendText(USER_CLARA, CanaryStudent.BIRTH_DATE_BR));
        TelegramStep cpfStep = strict("CPF", () -> sendText(USER_CLARA, CanaryCpf.MASKED));
        assertThat(cpfStep.botCalls())
                .as("a mensagem com o CPF é apagada")
                .contains("deleteMessage");
        strict("nome do responsável", () -> sendText(USER_CLARA, CanaryStudent.GUARDIAN_NAME));
        strict(
                "telefone do responsável",
                () -> sendText(USER_CLARA, CanaryStudent.GUARDIAN_PHONE_MASKED));
        strict("e-mail do responsável", () -> sendText(USER_CLARA, CanaryStudent.GUARDIAN_EMAIL));
        // consentimento do responsável (C1 a C5), com um nome que não é o do canário
        strict("consentimento C1", () -> sendCallback(USER_CLARA, "v1:consent-yes:"));
        strict("consentimento nome", () -> sendText(USER_CLARA, "Responsável Fictício"));
        strict("consentimento relação", () -> sendCallback(USER_CLARA, "v1:consent-rel:MOTHER"));
        strict("consentimento data", () -> sendCallback(USER_CLARA, "v1:consent-date:today"));
        strict("consentimento forma", () -> sendCallback(USER_CLARA, "v1:consent-method:DIGITAL"));
        card("resumo do cadastro", () -> sendText(USER_CLARA, CanaryStudent.INSTRUMENT));
        String confirm = lastButtonWith("Confirmar");
        TelegramStep saved =
                card("confirmação do cadastro", () -> sendCallback(USER_CLARA, confirm));
        assertThat(saved.botCalls()).as("o cadastro foi gravado").contains("foi cadastrado(a)");
        assertThat(jdbc.queryForObject("select count(*) from student", Integer.class)).isEqualTo(1);

        // lista, cartão, edição e inativação (cada uma confirmada ou cancelada)
        card("lista de alunos", () -> sendText(USER_CLARA, "meus alunos"));
        String open =
                buttons(lastMessage()).stream()
                        .filter(c -> c.contains("reg-open"))
                        .findFirst()
                        .orElseThrow();
        card("cartão", () -> sendCallback(USER_CLARA, open));
        String edit = lastButtonWith("Editar");
        card("escolha do campo", () -> sendCallback(USER_CLARA, edit));
        String editCpf = lastButtonWith("CPF");
        card("editar CPF", () -> sendCallback(USER_CLARA, editCpf));
        TelegramStep newCpf = card("novo CPF", () -> sendText(USER_CLARA, OTHER_CPF));
        assertThat(newCpf.botCalls())
                .as("a mensagem com o novo CPF é apagada")
                .contains("deleteMessage");
        String cancelEdit = lastButtonWith("Cancelar");
        card("cancelar edição", () -> sendCallback(USER_CLARA, cancelEdit));
        card("lista de novo", () -> sendText(USER_CLARA, "meus alunos"));
        String openAgain =
                buttons(lastMessage()).stream()
                        .filter(c -> c.contains("reg-open"))
                        .findFirst()
                        .orElseThrow();
        card("cartão de novo", () -> sendCallback(USER_CLARA, openAgain));
        String inactivate = lastButtonWith("Inativar");
        card("pedido de inativação", () -> sendCallback(USER_CLARA, inactivate));
        String cancelInactivation = lastButtonWith("Cancelar");
        card("cancelar inativação", () -> sendCallback(USER_CLARA, cancelInactivation));

        // duplicidade: outro professor e o próprio dono recebem recusa neutra
        registerWithCanaryCpf(USER_BRUNO, "Outro Nome Fictício");
        registerWithCanaryCpf(USER_CLARA, "Outro Nome Fictício");

        // o CPF canário digitado onde não se espera: etapas erradas, busca e conta sem vínculo
        strict("CPF solto no menu", () -> sendText(USER_CLARA, CanaryCpf.DIGITS));
        card("lista para buscar", () -> sendText(USER_CLARA, "meus alunos"));
        TelegramStep search =
                strict("CPF na busca da lista", () -> sendText(USER_CLARA, CanaryCpf.MASKED));
        assertThat(search.botCalls())
                .as("a mensagem com o CPF é apagada")
                .contains("deleteMessage");
        card("registrar aula", () -> sendText(USER_CLARA, "registrar aula"));
        strict("CPF na escolha do aluno da aula", () -> sendText(USER_CLARA, CanaryCpf.DIGITS));
        strict("cancelar", () -> sendText(USER_CLARA, "cancelar"));
        strict("CPF com conta sem vínculo", () -> sendText(USER_UNLINKED, CanaryCpf.MASKED));
        strict(
                "CPF em conversa de grupo",
                () ->
                        sendUpdate(
                                messageJson(
                                        nextUpdate(),
                                        USER_CLARA,
                                        "group",
                                        "\"text\": \"" + CanaryCpf.DIGITS + "\"")));
        strict("cadastro para o CPF errado", () -> sendText(USER_CLARA, "cadastrar aluno"));
        strict(
                "nome do cadastro do CPF errado",
                () -> sendText(USER_CLARA, "Aluno Adulto Fictício"));
        strict("nascimento do cadastro do CPF errado", () -> sendText(USER_CLARA, "04/05/1990"));
        strict("CPF com dígito verificador errado", () -> sendText(USER_CLARA, "111.444.777-36"));
        strict("CPF curto demais", () -> sendText(USER_CLARA, "111.444.777"));
        strict("cancelar cadastro", () -> sendText(USER_CLARA, "cancelar"));

        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from student_registration_draft", Integer.class))
                .as("nenhum cadastro em andamento sobra")
                .isZero();
        finish("Telegram");
    }

    /** Começa um cadastro com o CPF canário e o cancela; a resposta tem de ser neutra. */
    private void registerWithCanaryCpf(long user, String name) throws Exception {
        strict("cadastro duplicado (início)", () -> sendText(user, "cadastrar aluno"));
        strict("cadastro duplicado (nome)", () -> sendText(user, name));
        strict("cadastro duplicado (nascimento)", () -> sendText(user, "03/02/1991"));
        TelegramStep step =
                strict("cadastro duplicado (CPF)", () -> sendText(user, CanaryCpf.MASKED));
        assertThat(step.botCalls()).contains("deleteMessage");
        strict("cadastro duplicado (cancelar)", () -> sendText(user, "cancelar"));
    }

    // ---------------------------------------------------------------- o que ficou

    private void finish(String channel) {
        cpf.text("log (" + channel + ")", Place.LOG, logText());
        cpf.text("rastros de exceção (" + channel + ")", Place.EXCEPTION_TRACE, traceText());
        cpf.text("authorship_record (" + channel + ")", Place.AUTHORSHIP, authorshipText());
        data.text("log (" + channel + ")", Place.LOG, logText());
        data.text("rastros de exceção (" + channel + ")", Place.EXCEPTION_TRACE, traceText());
        data.text("authorship_record (" + channel + ")", Place.AUTHORSHIP, authorshipText());
        for (String table : tables()) {
            // o CPF só existe cifrado; os dados do aluno só podem estar na tabela dele e, como
            // cartão
            // ou resumo, na caixa de saída do canal
            cpf.text("tabela " + table, Place.AUTHORSHIP, tableText(table));
            if (table.equals("channel_action")) {
                cardData.text("tabela " + table, Place.BOT_CARD, tableText(table));
            } else if (!table.equals("student")) {
                data.text("tabela " + table, Place.AUTHORSHIP, tableText(table));
            }
        }
        assertThat(cpf.sweeps()).isGreaterThan(20);
        cpf.assertClean();
        data.assertClean();
        cardData.assertClean();
    }
}
