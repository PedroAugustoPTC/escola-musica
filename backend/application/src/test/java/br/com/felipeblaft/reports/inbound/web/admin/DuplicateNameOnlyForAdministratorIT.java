package br.com.felipeblaft.reports.inbound.web.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import br.com.felipeblaft.reports.domain.common.DomainException;
import br.com.felipeblaft.reports.domain.common.ErrorCode;
import br.com.felipeblaft.reports.support.ContractApiBase;
import br.com.felipeblaft.reports.support.ScenarioIT;
import br.com.felipeblaft.reports.usecase.home.GetTeacherHomeUseCase;
import jakarta.servlet.http.Cookie;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MvcResult;

/**
 * O nome do registro existente em {@code DUPLICATE_CPF} e {@code DUPLICATE_EMAIL} só sai em rotas
 * do administrador (FR-024, R-13): se um erro de duplicidade com nome escapar numa rota do
 * professor, a resposta é a neutra, sem nome e sem detalhe: {@code CPF_UNAVAILABLE} para o CPF e o
 * texto neutro do e-mail (que não fala de CPF) para o e-mail.
 */
class DuplicateNameOnlyForAdministratorIT extends ContractApiBase {

    private static final String EXISTING_NAME = "Aluno Existente Canário";

    @MockitoSpyBean GetTeacherHomeUseCase getHome;

    @Test
    void duplicateCpfWithNameOnATeacherRouteBecomesTheNeutralAnswer() throws Exception {
        scenario.clara();
        Cookie session = signIn(ScenarioIT.CODE_CLARA);
        doThrow(new DomainException(ErrorCode.DUPLICATE_CPF, Map.of("name", EXISTING_NAME)))
                .when(getHome)
                .run(any());

        MvcResult result = mvc.perform(get("/api/v1/home").cookie(session)).andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(409);
        String body = result.getResponse().getContentAsString();
        assertThat(body)
                .contains("\"code\":\"CPF_UNAVAILABLE\"")
                .contains("Não foi possível usar este CPF. Procure o administrador.")
                .doesNotContain(EXISTING_NAME)
                .doesNotContain("details")
                .doesNotContain("DUPLICATE");
    }

    @Test
    void duplicateEmailWithNameOnATeacherRouteIsNeutralAndNeverTalksAboutCpf() throws Exception {
        scenario.clara();
        Cookie session = signIn(ScenarioIT.CODE_CLARA);
        doThrow(new DomainException(ErrorCode.DUPLICATE_EMAIL, Map.of("name", EXISTING_NAME)))
                .when(getHome)
                .run(any());

        MvcResult result = mvc.perform(get("/api/v1/home").cookie(session)).andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(409);
        assertThat(result.getResponse().getContentAsString())
                .contains("\"code\":\"DUPLICATE_EMAIL\"")
                .contains("Não foi possível usar este e-mail. Procure o administrador.")
                .doesNotContain(EXISTING_NAME)
                .doesNotContain("details")
                .doesNotContainIgnoringCase("CPF");
    }
}
