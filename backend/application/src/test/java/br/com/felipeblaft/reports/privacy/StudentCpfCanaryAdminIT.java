package br.com.felipeblaft.reports.privacy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

import br.com.felipeblaft.reports.support.CanaryCpf;
import br.com.felipeblaft.reports.support.CanaryStudent;
import br.com.felipeblaft.reports.support.LeakAudit;
import br.com.felipeblaft.reports.support.RealStackBase;
import br.com.felipeblaft.reports.support.ResponseSweeper;
import br.com.felipeblaft.reports.support.ResponseSweeper.Place;
import jakarta.servlet.http.Cookie;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

/**
 * SC-004 (administrador): o CPF canário nunca aparece em log, resposta, cabeçalho, URL, mensagem de
 * erro, rastro de exceção nem em {@code authorship_record} (e, cifrado, em nenhuma tabela), nos
 * fluxos de cadastro, edição, duplicidade e validação. O aluno canário (nome, nascimento e dados
 * do responsável) só aparece no corpo das respostas de sucesso ao administrador, nunca em log,
 * rastro, URL, autoria nem fora da tabela do aluno. Pilha real, nenhum dublê.
 */
class StudentCpfCanaryAdminIT extends RealStackBase {

    private static final String OTHER_CPF = "529.982.247-25";
    private static final String THIRD_CPF = "123.456.789-09";
    private static final String STUDENTS = ADMIN_BASE + "/students";

    private Cookie admin;
    private UUID teacherA;
    private UUID teacherB;
    private LeakAudit cpf;
    private LeakAudit data;
    private ResponseSweeper successSweeper;
    private ResponseSweeper failureSweeper;

    @BeforeEach
    void prepare() throws Exception {
        admin = insertAndSignInAsAdministrator();
        teacherA = createTeacher(admin, "Professora Alfa", "alfa@example.test").id();
        teacherB = createTeacher(admin, "Professor Beta", "beta@example.test").id();
        cpf = LeakAudit.searching(CanaryCpf.sweepValues());
        data = LeakAudit.searching(CanaryStudent.sweepValues());
        successSweeper = ResponseSweeper.searching(CanaryStudent.sweepValues()).allowing(Place.BODY);
        // a recusa por duplicidade pode nomear o aluno existente ao administrador (FR-021)
        Map<String, String> withoutName = new LinkedHashMap<>(CanaryStudent.sweepValues());
        withoutName.remove("nome do aluno canário");
        failureSweeper = ResponseSweeper.searching(withoutName).allowing(Place.BODY);
        logs.clear();
    }

    /** Registra o resultado: CPF nunca; dados do aluno só em corpo de sucesso. */
    private MvcResult seen(String step, MvcResult result) {
        cpf.exchange(step, result);
        boolean success = result.getResponse().getStatus() / 100 == 2;
        data.exchange(step, result, success ? successSweeper : failureSweeper);
        return result;
    }

    /** Como {@link #seen}, mas a requisição leva o CPF na URL de propósito: só ela é dispensada. */
    private MvcResult seenWithCpfInRequestUrl(String step, MvcResult result) {
        cpf.exchange(step, result, ResponseSweeper.searching(CanaryCpf.sweepValues()).allowing(Place.URL));
        data.exchange(step, result, failureSweeper);
        return result;
    }

    private String register(String json) throws Exception {
        MvcResult result = seen("cadastro do aluno", adminPost(admin, STUDENTS, json));
        assertThat(result.getResponse().getStatus()).as("cadastro do aluno").isEqualTo(201);
        return body(result).path("id").asString();
    }

    @Test
    void cpfNeverLeaksAndStudentDataStaysOnTheAuthorizedResponse() throws Exception {
        // cadastro com o CPF canário e com os dois professores
        MvcResult created =
                seen("cadastro canário", adminPost(admin, STUDENTS, canaryStudentJson(List.of(teacherA, teacherB))));
        assertThat(created.getResponse().getStatus()).isEqualTo(201);
        assertThat(text(created)).contains(CanaryStudent.NAME).contains(CanaryStudent.GUARDIAN_NAME);
        String studentId = body(created).path("id").asString();
        String other =
                register(
                        studentJson(
                                "Aluno Adulto Fictício",
                                "1990-05-04",
                                OTHER_CPF,
                                null,
                                null,
                                null,
                                "Piano",
                                List.of(teacherA)));

        // leitura: lista, busca por nome e por instrumento, abertura
        seen("lista de alunos", adminGet(admin, STUDENTS));
        seen("busca por nome", adminGet(admin, STUDENTS + "?search=pietra"));
        seen("busca por instrumento", adminGet(admin, STUDENTS + "?search=viol%C3%A3o&status=ALL"));
        seen("abertura do aluno", adminGet(admin, STUDENTS + "/" + studentId));

        // duplicidade no cadastro (CPF canário de novo, outro nome) e na edição
        MvcResult duplicate =
                seen(
                        "cadastro duplicado",
                        adminPost(
                                admin,
                                STUDENTS,
                                studentJson(
                                        "Outro Nome Fictício",
                                        "1991-02-03",
                                        CanaryCpf.DIGITS,
                                        null,
                                        null,
                                        null,
                                        null,
                                        List.of(teacherB))));
        assertThat(duplicate.getResponse().getStatus()).isEqualTo(409);
        MvcResult duplicateEdit =
                seen(
                        "edição duplicada",
                        adminPatch(
                                admin,
                                STUDENTS + "/" + other,
                                "{\"cpf\":\"" + CanaryCpf.MASKED + "\"}"));
        assertThat(duplicateEdit.getResponse().getStatus()).isEqualTo(409);

        // edição bem-sucedida: libera o CPF canário e o devolve a outro aluno
        mvcStatus(
                seen(
                        "troca do CPF do canário",
                        adminPatch(admin, STUDENTS + "/" + studentId, "{\"cpf\":\"" + THIRD_CPF + "\"}")),
                200);
        mvcStatus(
                seen(
                        "CPF canário para o outro aluno",
                        adminPatch(admin, STUDENTS + "/" + other, "{\"cpf\":\"" + CanaryCpf.DIGITS + "\"}")),
                200);
        mvcStatus(
                seen(
                        "edição de nome e responsável",
                        adminPatch(
                                admin,
                                STUDENTS + "/" + studentId,
                                studentJson(
                                        CanaryStudent.NAME,
                                        null,
                                        null,
                                        CanaryStudent.GUARDIAN_NAME,
                                        CanaryStudent.GUARDIAN_PHONE_DIGITS,
                                        CanaryStudent.GUARDIAN_EMAIL,
                                        "Violino",
                                        null))),
                200);
        mvcStatus(
                seen("inativação", adminPost(admin, STUDENTS + "/" + studentId + "/inactivation", null)),
                200);
        mvcStatus(
                seen("reativação", adminPost(admin, STUDENTS + "/" + studentId + "/reactivation", null)),
                200);

        // validação: o CPF canário digitado no campo errado ou em corpo malformado
        invalid("nome em branco", studentJson(" ", "2000-01-01", CanaryCpf.MASKED, null, null, null, null, List.of(teacherA)));
        invalid("nascimento futuro", studentJson("Nome Válido", "2999-01-01", CanaryCpf.MASKED, null, null, null, null, List.of(teacherA)));
        invalid("CPF no campo de nascimento", studentJson("Nome Válido", CanaryCpf.DIGITS, "529.982.247-25", null, null, null, null, List.of(teacherA)));
        invalid("CPF no telefone do responsável", studentJson("Menor Válido", "2016-03-14", "987.654.321-00", "Responsável Válido", CanaryCpf.MASKED, "g@example.test", null, List.of(teacherA)));
        invalid("CPF no e-mail do responsável", studentJson("Menor Válido", "2016-03-14", "987.654.321-00", "Responsável Válido", "(11) 98888-7777", CanaryCpf.MASKED, null, List.of(teacherA)));
        invalid("CPF com dígito verificador errado", studentJson("Nome Válido", "2000-01-01", "111.444.777-36", null, null, null, null, List.of(teacherA)));
        invalid("menor sem responsável", studentJson("Menor Sem Responsável", "2016-03-14", CanaryCpf.MASKED, null, null, null, null, List.of(teacherA)));
        invalid("sem professor", studentJson("Nome Válido", "2000-01-01", CanaryCpf.MASKED, null, null, null, null, List.of()));
        invalid("corpo truncado", "{\"name\":\"Nome Válido\",\"cpf\":\"" + CanaryCpf.MASKED + "\",\"birthDate\":");
        invalid("CPF como número", "{\"name\":\"Nome Válido\",\"cpf\":" + CanaryCpf.DIGITS + "}");
        invalid("campo desconhecido com CPF", "{\"oldCpf\":\"" + CanaryCpf.MASKED + "\",\"name\":\"X\"}");
        invalid("lista no lugar do texto", "{\"name\":[\"" + CanaryCpf.MASKED + "\"]}");
        invalid("CPF no corpo de outro tipo", "\"" + CanaryCpf.MASKED + "\"");
        MvcResult wrongType =
                seen(
                        "tipo de conteúdo errado",
                        call(
                                post(STUDENTS)
                                        .cookie(admin)
                                        .with(csrf().asHeader())
                                        .contentType(MediaType.TEXT_PLAIN)
                                        .content(CanaryCpf.MASKED)));
        assertThat(wrongType.getResponse().getStatus()).isBetween(400, 499);

        // sondagem pela busca: o CPF na consulta é recusado sem ser repetido
        MvcResult probe =
                seenWithCpfInRequestUrl(
                        "busca com forma de CPF",
                        call(get(STUDENTS).cookie(admin).param("search", CanaryCpf.DIGITS)));
        assertThat(probe.getResponse().getStatus()).isEqualTo(400);
        seenWithCpfInRequestUrl(
                "busca com CPF mascarado",
                call(get(STUDENTS).cookie(admin).param("search", CanaryCpf.MASKED)));

        // o que ficou depois de tudo
        cpf.text("log", Place.LOG, logText());
        cpf.text("rastros de exceção", Place.EXCEPTION_TRACE, traceText());
        cpf.text("authorship_record", Place.AUTHORSHIP, authorshipText());
        data.text("log", Place.LOG, logText());
        data.text("rastros de exceção", Place.EXCEPTION_TRACE, traceText());
        data.text("authorship_record", Place.AUTHORSHIP, authorshipText());
        for (String table : tables()) {
            // o CPF está cifrado em toda tabela; os dados do aluno só podem estar na tabela dele
            cpf.text("tabela " + table, Place.AUTHORSHIP, tableText(table));
            if (!table.equals("student")) {
                data.text("tabela " + table, Place.AUTHORSHIP, tableText(table));
            }
        }
        assertThat(cpf.sweeps()).isGreaterThan(30);
        cpf.assertClean();
        data.assertClean();
    }

    /** A requisição inválida responde 4xx e a resposta não repete o CPF nem os dados do aluno. */
    private void invalid(String step, String json) throws Exception {
        MvcResult result = seen(step, adminPost(admin, STUDENTS, json));
        assertThat(result.getResponse().getStatus()).as(step).isBetween(400, 499);
    }
}
