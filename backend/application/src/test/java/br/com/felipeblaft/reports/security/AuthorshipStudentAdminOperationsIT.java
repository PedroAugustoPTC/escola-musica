package br.com.felipeblaft.reports.security;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.felipeblaft.reports.support.AdministrationStackBase;
import br.com.felipeblaft.reports.support.AdministratorFixture;
import br.com.felipeblaft.reports.support.CanaryCpf;
import br.com.felipeblaft.reports.support.CanaryStudent;
import br.com.felipeblaft.reports.support.ResponseSweeper;
import br.com.felipeblaft.reports.support.ResponseSweeper.Place;
import jakarta.servlet.http.Cookie;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * Registro de autoria das operações de aluno do administrador (FR-040, parte do SC-014): cadastrar,
 * editar, inativar e reativar deixam registro com papel, canal, alvo e resultado, sem CPF e sem
 * nenhum dado do aluno ou do responsável (aluno canário menor de idade, com os três dados do
 * responsável).
 */
class AuthorshipStudentAdminOperationsIT extends AdministrationStackBase {

    private AdministratorFixture administrator;
    private Cookie session;

    @BeforeEach
    void prepare() throws Exception {
        administrator = insertAdministrator();
        session = signInAdministrator(administrator);
        clearAuthorship();
    }

    private Map<String, Object> canary() {
        return minorStudent(
                CanaryStudent.NAME,
                CanaryStudent.BIRTH_DATE_ISO,
                CanaryCpf.MASKED,
                CanaryStudent.GUARDIAN_NAME,
                CanaryStudent.GUARDIAN_PHONE_MASKED,
                CanaryStudent.GUARDIAN_EMAIL);
    }

    private void assertStudentRecord(Map<String, Object> record, UUID student) {
        assertRecord(record, "ADMINISTRATOR", "WEB", "SUCCESS");
        assertThat(record.get("author_id")).isEqualTo(administrator.administrator().id());
        assertThat(record.get("target_type")).isEqualTo("STUDENT");
        assertThat(record.get("target_id")).isEqualTo(student);
        assertThat(record.get("error_code")).isNull();
        assertThat(record.get("origin_hash")).isNull();
    }

    @Test
    void registerUpdateInactivateAndReactivateLeaveRecordsWithoutPersonalData() throws Exception {
        UUID student = registerStudentAsAdministrator(session, canary());
        MockHttpServletResponse update =
                call(
                        HttpMethod.PATCH,
                        ADMIN + "/students/" + student,
                        session,
                        Map.of(
                                "instrument",
                                "Flauta",
                                "guardian",
                                Map.of(
                                        "name",
                                        "Outro Responsável Fictício",
                                        "phone",
                                        "(21) 97654-3210",
                                        "email",
                                        "other.guardian@example.test")));
        assertThat(update.getStatus()).isEqualTo(200);
        assertThat(
                        call(
                                        HttpMethod.POST,
                                        ADMIN + "/students/" + student + "/inactivation",
                                        session)
                                .getStatus())
                .isEqualTo(200);
        assertThat(
                        call(
                                        HttpMethod.POST,
                                        ADMIN + "/students/" + student + "/reactivation",
                                        session)
                                .getStatus())
                .isEqualTo(200);

        for (String operation :
                new String[] {
                    "STUDENT_REGISTER", "STUDENT_UPDATE", "STUDENT_INACTIVATE", "STUDENT_REACTIVATE"
                }) {
            assertStudentRecord(onlyAuthorship(operation), student);
        }
        assertThat(authorship()).hasSize(4);
        ResponseSweeper.searching(CanaryStudent.sweepValuesWithCpf())
                .assertClean(Place.AUTHORSHIP, authorshipDump());
        assertThat(authorshipDump())
                .doesNotContain("Responsável Fictício")
                .doesNotContain("97654")
                .doesNotContain("other.guardian");
    }

    @Test
    void duplicateCpfAndInvalidDataLeaveNoStudentRecord() throws Exception {
        registerStudentAsAdministrator(session, canary());
        clearAuthorship();

        MockHttpServletResponse duplicate =
                call(HttpMethod.POST, ADMIN + "/students", session, canary());
        MockHttpServletResponse minorWithoutGuardian =
                call(
                        HttpMethod.POST,
                        ADMIN + "/students",
                        session,
                        Map.of(
                                "name",
                                "Sem Responsável",
                                "birthDate",
                                "2016-01-01",
                                "cpf",
                                "529.982.247-25"));

        assertThat(duplicate.getStatus()).isEqualTo(409);
        assertThat(errorCode(duplicate)).isEqualTo("DUPLICATE_CPF");
        assertThat(minorWithoutGuardian.getStatus()).isEqualTo(400);
        assertThat(authorship()).isEmpty();
    }
}
