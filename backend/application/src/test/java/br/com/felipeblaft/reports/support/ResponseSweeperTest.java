package br.com.felipeblaft.reports.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.felipeblaft.reports.support.ResponseSweeper.Finding;
import br.com.felipeblaft.reports.support.ResponseSweeper.Place;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class ResponseSweeperTest {

    private final ResponseSweeper sweeper =
            ResponseSweeper.searching(CanaryStudent.sweepValuesWithCpf());

    private static Finding finding(String label, Place place) {
        return new Finding(label, place);
    }

    @Test
    void cleanResponseHasNoFinding() {
        List<Finding> findings =
                sweeper.sweep(
                        "/admin/students",
                        Map.of("Content-Type", List.of("application/json")),
                        "{\"items\":[],\"total\":0}");

        assertThat(findings).isEmpty();
    }

    @Test
    void findsTheValueInTheBody() {
        List<Finding> findings =
                sweeper.sweep("/admin/students", Map.of(), "{\"cpf\":\"11144477735\"}");

        assertThat(findings).containsExactly(finding("CPF canário", Place.BODY));
    }

    @Test
    void findsTheValueInAHeader() {
        List<Finding> findings =
                sweeper.sweep(
                        "/admin/students",
                        Map.of("X-Debug", List.of("aluno " + CanaryStudent.NAME)),
                        "");

        assertThat(findings).containsExactly(finding("nome do aluno canário", Place.HEADERS));
    }

    @Test
    void findsTheValueInTheUrl() {
        List<Finding> findings =
                sweeper.sweep("/admin/students?cpf=" + CanaryCpf.DIGITS, Map.of(), "");

        assertThat(findings).containsExactly(finding("CPF canário", Place.URL));
    }

    @Test
    void treatsTheLocationHeaderAsUrl() {
        List<Finding> findings =
                sweeper.sweep(
                        "/admin/students",
                        Map.of("Location", List.of("/admin/students/" + CanaryCpf.DIGITS)),
                        "");

        assertThat(findings).containsExactly(finding("CPF canário", Place.URL));
    }

    @Test
    void allowedPlaceIsSkippedButTheOthersStillCount() {
        ResponseSweeper allowingBody = sweeper.allowing(Place.BODY);

        List<Finding> findings =
                allowingBody.sweep(
                        "/admin/students?name=" + "Pietra%20Zanzibar%20Quimera",
                        Map.of(), "{\"name\":\"" + CanaryStudent.NAME + "\"}");

        assertThat(findings).containsExactly(finding("nome do aluno canário", Place.URL));
    }

    @Test
    void allowingDoesNotChangeTheOriginalSweeper() {
        sweeper.allowing(Place.BODY);

        assertThat(sweeper.sweep("/x", Map.of(), CanaryStudent.NAME)).isNotEmpty();
    }

    @Test
    void findsTheCpfInAnyMaskOrSeparator() {
        for (String text :
                List.of(
                        "111.444.777-35",
                        "111 444 777 35",
                        "111-444-777-35",
                        "cpf=11144477735;",
                        "111.444.777/35")) {
            assertThat(sweeper.sweepText(Place.LOG, text))
                    .as(text)
                    .extracting(Finding::label)
                    .contains("CPF canário");
        }
    }

    @Test
    void findsThePhoneInAnyMask() {
        for (String text :
                List.of("(11) 94321-8765", "11 94321 8765", "11943218765", "11-94321-8765")) {
            assertThat(sweeper.sweepText(Place.LOG, text))
                    .as(text)
                    .extracting(Finding::label)
                    .contains("telefone do responsável canário");
        }
    }

    @Test
    void findsTheBirthDateInEitherForm() {
        assertThat(sweeper.sweepText(Place.LOG, "nasceu em 2016-03-14"))
                .extracting(Finding::label)
                .contains("nascimento do aluno canário (ISO)");
        assertThat(sweeper.sweepText(Place.LOG, "nasceu em 14/03/2016"))
                .extracting(Finding::label)
                .contains("nascimento do aluno canário (BR)");
    }

    @Test
    void ignoresCaseAndAccents() {
        assertThat(sweeper.sweepText(Place.LOG, "HORTENSIO BALBURDIA CANTARELLI"))
                .extracting(Finding::label)
                .containsExactly("nome do responsável canário");
        assertThat(sweeper.sweepText(Place.LOG, "pietra zanzibar quimera"))
                .extracting(Finding::label)
                .containsExactly("nome do aluno canário");
    }

    @Test
    void findsUrlEncodedAndPlusEncodedValues() {
        assertThat(sweeper.sweepText(Place.URL, "q=Pietra%20Zanzibar%20Quimera"))
                .extracting(Finding::label)
                .containsExactly("nome do aluno canário");
        assertThat(sweeper.sweepText(Place.URL, "q=Pietra+Zanzibar+Quimera"))
                .extracting(Finding::label)
                .containsExactly("nome do aluno canário");
        assertThat(sweeper.sweepText(Place.URL, "mail=balburdia.cantarelli%40example.test"))
                .extracting(Finding::label)
                .containsExactly("e-mail do responsável canário");
    }

    @Test
    void findsUnicodeEscapedValues() {
        assertThat(sweeper.sweepText(Place.BODY, "Hort\\u00eansio Balb\\u00fardia Cantarelli"))
                .extracting(Finding::label)
                .containsExactly("nome do responsável canário");
    }

    @Test
    void doesNotFlagNearbyNumbers() {
        assertThat(sweeper.sweepText(Place.LOG, "11144477736 e 1114447773")).isEmpty();
    }

    @Test
    void doesNotJoinDigitsOfSeparateJsonFields() {
        assertThat(sweeper.sweepText(Place.BODY, "{\"a\":\"111444\",\"b\":\"77735\"}")).isEmpty();
    }

    @Test
    void reportsEachLabelOnceWithAllItsPlaces() {
        List<Finding> findings =
                sweeper.sweep(
                        "/x?c=" + CanaryCpf.DIGITS,
                        Map.of("X-C", List.of(CanaryCpf.DIGITS)),
                        CanaryCpf.DIGITS + " " + CanaryCpf.DIGITS);

        assertThat(findings)
                .containsExactlyInAnyOrder(
                        finding("CPF canário", Place.URL),
                        finding("CPF canário", Place.HEADERS),
                        finding("CPF canário", Place.BODY));
    }

    @Test
    void sweepTextOfAnAllowedPlaceIsEmpty() {
        ResponseSweeper allowing = sweeper.allowing(Place.BOT_CARD);

        assertThat(allowing.sweepText(Place.BOT_CARD, CanaryStudent.NAME)).isEmpty();
        assertThat(allowing.sweepText(Place.ACTION_PAYLOAD, CanaryStudent.NAME)).isNotEmpty();
    }

    @Test
    void assertCleanPassesWhenThereIsNoFinding() {
        assertThatCode(() -> sweeper.assertClean(Place.LOG, "tudo em ordem"))
                .doesNotThrowAnyException();
    }

    @Test
    void assertCleanFailsNamingLabelAndPlaceButNeverTheValue() {
        assertThatThrownBy(
                        () ->
                                sweeper.assertClean(
                                        Place.LOG, "falhou para " + CanaryStudent.GUARDIAN_NAME))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("nome do responsável canário")
                .hasMessageContaining("LOG")
                .satisfies(
                        failure ->
                                assertThat(failure.getMessage())
                                        .doesNotContain(CanaryStudent.GUARDIAN_NAME));
    }

    @Test
    void sweepsRequestAndResponseOfTheServletLayer() throws IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/admin/students");
        request.setQueryString("cpf=" + CanaryCpf.DIGITS);
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setCharacterEncoding("UTF-8");
        response.setHeader("X-Trace", CanaryStudent.GUARDIAN_EMAIL);
        response.setHeader("Location", "/admin/students/" + CanaryStudent.NAME);
        response.getWriter().write("{\"name\":\"" + CanaryStudent.NAME + "\"}");
        response.getWriter().flush();

        List<Finding> findings = sweeper.sweep(request, response);

        assertThat(findings)
                .containsExactlyInAnyOrder(
                        finding("CPF canário", Place.URL),
                        finding("e-mail do responsável canário", Place.HEADERS),
                        finding("nome do aluno canário", Place.URL),
                        finding("nome do aluno canário", Place.BODY));
    }

    @Test
    void sweepsTheErrorMessageOfTheResponse() throws IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/x");
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.sendError(400, "CPF " + CanaryCpf.MASKED + " inválido");

        assertThat(sweeper.sweep(request, response))
                .contains(finding("CPF canário", Place.ERROR_MESSAGE));
    }

    @Test
    void assertCleanOfTheServletLayerAllowsTheAuthorizedBody() throws IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/admin/students/1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("{\"name\":\"" + CanaryStudent.NAME + "\"}");
        response.getWriter().flush();

        assertThatCode(() -> sweeper.allowing(Place.BODY).assertClean(request, response))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> sweeper.assertClean(request, response))
                .isInstanceOf(AssertionError.class);
    }

    @Test
    void noValueMeansNothingToFind() {
        ResponseSweeper empty = ResponseSweeper.searching(Map.of());

        assertThat(empty.sweepText(Place.LOG, CanaryStudent.NAME)).isEmpty();
    }
}
