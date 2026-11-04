package br.com.felipeblaft.reports.privacy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

import br.com.felipeblaft.reports.domain.registry.Student;
import br.com.felipeblaft.reports.domain.registry.Teacher;
import br.com.felipeblaft.reports.port.AuthorshipRecorderPort;
import br.com.felipeblaft.reports.port.StudentRepository;
import br.com.felipeblaft.reports.support.CanaryCpf;
import br.com.felipeblaft.reports.support.CanaryStudent;
import br.com.felipeblaft.reports.support.LogCapture;
import br.com.felipeblaft.reports.support.ResponseSweeper;
import br.com.felipeblaft.reports.support.ResponseSweeper.Place;
import br.com.felipeblaft.reports.support.SchoolHistoryBase;
import jakarta.servlet.http.Cookie;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/**
 * Privacidade da visão da escola (SC-005): depois de todas as requisições da feature (sucesso,
 * validação, negativas e falha forçada), nenhum valor canário (nome e CPF do aluno, dados do
 * responsável e um trecho de conteúdo de relatório) aparece em {@code authorship_record}, nos logs
 * capturados nem nos corpos de erro. Só a resposta autorizada da lista e do PDF leva o nome.
 */
class SchoolHistoryCanaryIT extends SchoolHistoryBase {

    private static final String CONTENT_CANARY = "Trecho canário Zimbório Quixotesco";

    @Autowired StudentRepository students;
    @MockitoSpyBean AuthorshipRecorderPort recorder;

    private static Map<String, String> canaries() {
        Map<String, String> values = new LinkedHashMap<>(CanaryStudent.sweepValuesWithCpf());
        values.put("trecho canário de conteúdo", CONTENT_CANARY);
        return values;
    }

    @Test
    void noCanaryOutsideTheAuthorizedListAndPdfResponses() throws Exception {
        Teacher clara = scenario.clara();
        Cookie administrator = signInNewAdministrator();
        UUID studentId =
                registerStudentAsAdministrator(
                        administrator,
                        minorStudent(
                                CanaryStudent.NAME,
                                CanaryStudent.BIRTH_DATE_ISO,
                                CanaryCpf.MASKED,
                                CanaryStudent.GUARDIAN_NAME,
                                CanaryStudent.GUARDIAN_PHONE_MASKED,
                                CanaryStudent.GUARDIAN_EMAIL),
                        clara.id());
        Student student = students.searchById(studentId).orElseThrow();
        UUID lesson =
                scenario.approvedLesson(clara, student, LocalDate.of(2026, 3, 10), CONTENT_CANARY)
                        .id();
        UUID absent = UUID.fromString("00000000-0000-4000-8000-000000000999");
        clearAuthorship();
        ResponseSweeper sweeper = ResponseSweeper.searching(canaries());

        try (LogCapture logs = LogCapture.start()) {
            // sucesso: o nome pode aparecer no corpo da lista, nunca o resto
            MockHttpServletResponse list =
                    call(HttpMethod.GET, SCHOOL_HISTORY + "?studentId=" + studentId, administrator);
            assertThat(list.getStatus()).isEqualTo(200);
            Map<String, String> forbiddenInList = new LinkedHashMap<>(canaries());
            forbiddenInList.remove("nome do aluno canário");
            ResponseSweeper.searching(forbiddenInList)
                    .assertClean(Place.BODY, list.getContentAsString());
            assertThat(call(HttpMethod.GET, lessonPdf(lesson), administrator).getStatus())
                    .isEqualTo(200);
            assertThat(call(HttpMethod.GET, lessonDownload(lesson), administrator).getStatus())
                    .isEqualTo(200);

            // validação, negativa e falha forçada: corpos de erro sem canário
            MockHttpServletResponse validation =
                    call(
                            HttpMethod.GET,
                            SCHOOL_HISTORY
                                    + "?studentId="
                                    + studentId
                                    + "&from=2026-05-02&to=2026-05-01",
                            administrator);
            assertThat(validation.getStatus()).isEqualTo(400);
            sweeper.assertClean(Place.BODY, validation.getContentAsString());
            MockHttpServletResponse notFound =
                    call(HttpMethod.GET, lessonPdf(absent), administrator);
            assertThat(notFound.getStatus()).isEqualTo(404);
            sweeper.assertClean(Place.BODY, notFound.getContentAsString());
            doThrow(new IllegalStateException("falha simulada")).when(recorder).record(any());
            MockHttpServletResponse forced =
                    call(HttpMethod.GET, SCHOOL_HISTORY + "?studentId=" + studentId, administrator);
            assertThat(forced.getStatus()).isEqualTo(500);
            sweeper.assertClean(Place.BODY, forced.getContentAsString());
            MockHttpServletResponse forcedPdf =
                    call(HttpMethod.GET, lessonPdf(lesson), administrator);
            assertThat(forcedPdf.getStatus()).isEqualTo(500);
            sweeper.assertClean(Place.BODY, forcedPdf.getContentAsString());

            sweeper.assertClean(Place.LOG, logs.text());
        }

        sweeper.assertClean(Place.AUTHORSHIP, authorshipDump());
    }
}
