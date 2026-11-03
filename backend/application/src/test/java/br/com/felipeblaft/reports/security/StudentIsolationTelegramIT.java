package br.com.felipeblaft.reports.security;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.felipeblaft.reports.domain.registry.Student;
import br.com.felipeblaft.reports.domain.registry.Teacher;
import br.com.felipeblaft.reports.support.ScenarioIT;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Isolamento entre professores no Telegram (T137, SC-006): a professora A digita o nome do aluno do
 * professor B, usa a busca da lista e toca botões com {@code callback_data} adulterado (posição
 * fora da lista persistida, identificador do aluno de B no lugar da posição, token de confirmação
 * de B). A resposta é sempre a de "não encontramos" do aluno que não existe e nada de B muda. O
 * {@code callback_data} nunca leva identificador de aluno: leva a posição na lista persistida.
 */
class StudentIsolationTelegramIT extends SecurityBase {

    private static final String NONEXISTENT = "Zacarias Inexistente";

    private Teacher clara;
    private Teacher bruno;
    private Student claraStudent;
    private Student brunoStudent;
    private Student brunoInactive;

    @BeforeEach
    void prepare() throws Exception {
        clara = scenario.clara();
        bruno = scenario.bruno();
        claraStudent = scenario.student(clara, "Matteo Mafra", "Piano");
        brunoStudent = scenario.student(bruno, "Helena Prado", "Flauta");
        brunoInactive = scenario.student(bruno, "Ivo Bastos", "Violão");
        jdbc.update("update student set active = false where id = ?", brunoInactive.id());
        link(USER_CLARA, ScenarioIT.CODE_CLARA);
        link(USER_BRUNO, ScenarioIT.CODE_BRUNO);
    }

    /** A impressão da resposta, com o eco do texto digitado trocado por §. */
    private static String withoutEcho(Reply reply, String typed) {
        return reply.fingerprint().replace(typed, "§");
    }

    @Test
    void aTypedNameOfAnotherTeachersStudentAnswersLikeANameThatDoesNotExist() throws Exception {
        String before = businessDataDigest();
        String absent = withoutEcho(say(USER_CLARA, NONEXISTENT), NONEXISTENT);
        assertThat(absent).contains("Nenhum aluno encontrado para \"§\".");

        for (String typed :
                List.of("Helena Prado", "Helena", "Prado", "Ivo Bastos", "helena prado")) {
            Reply reply = say(USER_CLARA, typed);

            assertThat(withoutEcho(reply, typed)).as(typed).isEqualTo(absent);
            assertThat(reply.plain().replace(typed, "")).doesNotContain("Helena", "Prado", "Ivo");
        }
        assertThat(businessDataDigest()).isEqualTo(before);
    }

    @Test
    void theSearchInsideTheListAnswersLikeANameThatDoesNotExist() throws Exception {
        String before = businessDataDigest();
        say(USER_CLARA, "meus alunos");
        String absent = withoutEcho(say(USER_CLARA, NONEXISTENT), NONEXISTENT);

        for (String filter : List.of("ACTIVE", "INACTIVE")) {
            say(USER_CLARA, "meus alunos");
            tap(USER_CLARA, "v1:reg-filter:" + filter);
            String absentInFilter = withoutEcho(say(USER_CLARA, NONEXISTENT), NONEXISTENT);
            for (String typed : List.of("Helena", "Prado", "Ivo", "Bastos")) {
                say(USER_CLARA, "meus alunos");
                tap(USER_CLARA, "v1:reg-filter:" + filter);
                Reply reply = say(USER_CLARA, typed);

                assertThat(withoutEcho(reply, typed))
                        .as("%s em %s", typed, filter)
                        .isEqualTo(absentInFilter);
                assertThat(reply.plain()).doesNotContain("Helena", "Ivo");
            }
        }
        assertThat(absent).isNotBlank();
        assertThat(businessDataDigest()).isEqualTo(before);
    }

    @Test
    void forgedButtonsAnswerLikeAStudentThatNoLongerExists() throws Exception {
        String before = businessDataDigest();
        List<String> forged =
                List.of(
                        "v1:reg-open:1",
                        "v1:reg-open:2",
                        "v1:reg-open:99",
                        "v1:reg-open:-1",
                        "v1:reg-open:abc",
                        "v1:reg-open:",
                        "v1:reg-open:" + brunoStudent.id(),
                        "v1:reg-open:" + brunoInactive.id(),
                        "v1:reg-open:" + UUID.randomUUID());
        List<String> fingerprints = new java.util.ArrayList<>();
        for (String data : forged) {
            say(USER_CLARA, "meus alunos");
            Reply reply = tap(USER_CLARA, data);
            assertThat(reply.plain()).as(data).doesNotContain("Helena", "Ivo", "Bruno", "Prado");
            fingerprints.add(reply.fingerprint());
        }
        assertThat(new java.util.HashSet<>(fingerprints))
                .as("toda adulteração responde igual")
                .hasSize(1);
        assertThat(businessDataDigest()).isEqualTo(before);

        // a mesma resposta de um aluno que existia na lista e deixou de existir
        say(USER_CLARA, "meus alunos");
        jdbc.update(
                "delete from teacher_student_assignment where student_id = ?", claraStudent.id());
        jdbc.update("delete from student where id = ?", claraStudent.id());
        Reply stale = tap(USER_CLARA, "v1:reg-open:0");
        assertThat(fingerprints.getFirst()).isEqualTo(stale.fingerprint());
    }

    @Test
    void anotherTeachersConfirmationTokenDoesNothing() throws Exception {
        // B começa a inativar o aluno dele e fica com o token no botão
        say(USER_BRUNO, "meus alunos");
        tap(USER_BRUNO, "v1:reg-open:0");
        Reply brunoConfirmation = tap(USER_BRUNO, "v1:reg-inactivate:");
        String tokenOfBruno = tokenOf(brunoConfirmation);

        // A tenta usar o token de B, antes e depois de começar a própria inativação
        String before = businessDataDigest();
        Reply idle = tap(USER_CLARA, "v1:reg-confirm:" + tokenOfBruno);
        assertThat(idle.plain()).doesNotContain("Helena", "Matteo");

        say(USER_CLARA, "meus alunos");
        tap(USER_CLARA, "v1:reg-open:0");
        Reply claraConfirmation = tap(USER_CLARA, "v1:reg-inactivate:");
        assertThat(tokenOf(claraConfirmation)).isNotEqualTo(tokenOfBruno);
        Reply forged = tap(USER_CLARA, "v1:reg-confirm:" + tokenOfBruno);
        assertThat(forged.plain()).doesNotContain("Helena", "inativado", "Pronto");

        assertThat(
                        jdbc.queryForObject(
                                "select active from student where id = ?",
                                Boolean.class,
                                brunoStudent.id()))
                .isTrue();
        assertThat(
                        jdbc.queryForObject(
                                "select active from student where id = ?",
                                Boolean.class,
                                claraStudent.id()))
                .as("a confirmação adulterada também não executa a inativação da própria A")
                .isTrue();
        assertThat(businessDataDigest()).as("nada mudou além do rascunho de A").isEqualTo(before);

        // o rascunho de B segue intacto: o token dele ainda vale
        Reply done = tap(USER_BRUNO, "v1:reg-confirm:" + tokenOfBruno);
        assertThat(done.plain()).contains("Helena Prado");
        assertThat(
                        jdbc.queryForObject(
                                "select active from student where id = ?",
                                Boolean.class,
                                brunoStudent.id()))
                .isFalse();
    }

    private static String tokenOf(Reply reply) {
        return reply.buttonData().stream()
                .filter(data -> data.startsWith("v1:reg-confirm:"))
                .map(data -> data.substring("v1:reg-confirm:".length()))
                .findFirst()
                .orElseThrow(
                        () -> new AssertionError("sem botão de confirmação: " + reply.plain()));
    }
}
