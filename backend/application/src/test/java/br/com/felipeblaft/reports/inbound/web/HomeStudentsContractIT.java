package br.com.felipeblaft.reports.inbound.web;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.felipeblaft.reports.domain.registry.Student;
import br.com.felipeblaft.reports.domain.registry.Teacher;
import br.com.felipeblaft.reports.support.ContractApiBase;
import br.com.felipeblaft.reports.support.ScenarioIT;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Contrato de /inicio, /alunos e /alunos/{id} (FR-012, FR-013, FR-009). */
class HomeStudentsContractIT extends ContractApiBase {

    private Student matteo;
    private Student helena;
    private Cookie session;

    @BeforeEach
    void load() throws Exception {
        Teacher clara = scenario.clara();
        Teacher bruno = scenario.bruno();
        matteo = scenario.student(clara, "Matteo Mafra", "Piano");
        scenario.student(clara, "Olavo Torrente", null);
        scenario.student(clara, "Miguel de Castro Torrente", "Violino");
        helena = scenario.student(bruno, "Helena Prado", "Flauta");
        session = signIn(ScenarioIT.CODE_CLARA);
    }

    @Test
    void homeWithTotalStudentsAndLimit() throws Exception {
        mvc.perform(get("/api/v1/home").cookie(session))
                .andExpect(status().isOk())
                .andExpect(followsContract())
                .andExpect(jsonPath("$.teacher.name").value("Clara Souza"))
                .andExpect(jsonPath("$.totalStudents").value(3))
                .andExpect(jsonPath("$.students", hasSize(3)))
                .andExpect(jsonPath("$.students[0].name").value("Matteo Mafra"))
                .andExpect(jsonPath("$.openReports", hasSize(0)))
                .andExpect(jsonPath("$.audioLimitMb").value(50));
    }

    @Test
    void searchAllFoundAndNone() throws Exception {
        mvc.perform(get("/api/v1/students").cookie(session))
                .andExpect(status().isOk())
                .andExpect(followsContract())
                .andExpect(jsonPath("$.result").value("ALL"))
                .andExpect(jsonPath("$.students", hasSize(3)));
        mvc.perform(get("/api/v1/students").param("search", "TÓRR").cookie(session))
                .andExpect(status().isOk())
                .andExpect(followsContract())
                .andExpect(jsonPath("$.result").value("FOUND"))
                .andExpect(jsonPath("$.students", hasSize(2)));
        mvc.perform(get("/api/v1/students").param("search", "Helena").cookie(session))
                .andExpect(status().isOk())
                .andExpect(followsContract())
                .andExpect(jsonPath("$.result").value("NONE"))
                .andExpect(jsonPath("$.students", hasSize(3)));
    }

    @Test
    void studentSummaryAndCrossAccess() throws Exception {
        mvc.perform(get("/api/v1/students/{id}", matteo.id()).cookie(session))
                .andExpect(status().isOk())
                .andExpect(followsContract())
                .andExpect(jsonPath("$.name").value("Matteo Mafra"))
                .andExpect(jsonPath("$.hasOpenReport").value(false));
        mvc.perform(get("/api/v1/students/{id}", helena.id()).cookie(session))
                .andExpect(status().isNotFound())
                .andExpect(followsContract())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Não encontramos o que você procurou."));
        mvc.perform(get("/api/v1/students/{id}", "not-a-uuid").cookie(session))
                .andExpect(status().isBadRequest());
    }

    @Test
    void withoutSessionEverythingIs401() throws Exception {
        mvc.perform(get("/api/v1/home"))
                .andExpect(status().isUnauthorized())
                .andExpect(followsContract());
        mvc.perform(get("/api/v1/students")).andExpect(status().isUnauthorized());
    }
}
