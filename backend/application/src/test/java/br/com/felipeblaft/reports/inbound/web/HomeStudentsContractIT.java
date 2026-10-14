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
        mvc.perform(get("/api/v1/inicio").cookie(session))
                .andExpect(status().isOk())
                .andExpect(followsContract())
                .andExpect(jsonPath("$.professor.nome").value("Clara Souza"))
                .andExpect(jsonPath("$.totalAlunos").value(3))
                .andExpect(jsonPath("$.alunos", hasSize(3)))
                .andExpect(jsonPath("$.alunos[0].nome").value("Matteo Mafra"))
                .andExpect(jsonPath("$.relatoriosEmAberto", hasSize(0)))
                .andExpect(jsonPath("$.limiteAudioMb").value(50));
    }

    @Test
    void searchAllFoundAndNone() throws Exception {
        mvc.perform(get("/api/v1/alunos").cookie(session))
                .andExpect(status().isOk())
                .andExpect(followsContract())
                .andExpect(jsonPath("$.resultado").value("TODOS"))
                .andExpect(jsonPath("$.alunos", hasSize(3)));
        mvc.perform(get("/api/v1/alunos").param("busca", "TÓRR").cookie(session))
                .andExpect(status().isOk())
                .andExpect(followsContract())
                .andExpect(jsonPath("$.resultado").value("ENCONTRADOS"))
                .andExpect(jsonPath("$.alunos", hasSize(2)));
        mvc.perform(get("/api/v1/alunos").param("busca", "Helena").cookie(session))
                .andExpect(status().isOk())
                .andExpect(followsContract())
                .andExpect(jsonPath("$.resultado").value("NENHUM"))
                .andExpect(jsonPath("$.alunos", hasSize(3)));
    }

    @Test
    void studentSummaryAndCrossAccess() throws Exception {
        mvc.perform(get("/api/v1/alunos/{id}", matteo.id()).cookie(session))
                .andExpect(status().isOk())
                .andExpect(followsContract())
                .andExpect(jsonPath("$.nome").value("Matteo Mafra"))
                .andExpect(jsonPath("$.possuiRelatorioEmAberto").value(false));
        mvc.perform(get("/api/v1/alunos/{id}", helena.id()).cookie(session))
                .andExpect(status().isForbidden())
                .andExpect(followsContract())
                .andExpect(jsonPath("$.codigo").value("ALUNO_NAO_ASSOCIADO"))
                .andExpect(jsonPath("$.mensagem").value("Este aluno não está associado a você."));
        mvc.perform(get("/api/v1/alunos/{id}", "nao-e-uuid").cookie(session))
                .andExpect(status().isBadRequest());
    }

    @Test
    void withoutSessionEverythingIs401() throws Exception {
        mvc.perform(get("/api/v1/inicio"))
                .andExpect(status().isUnauthorized())
                .andExpect(followsContract());
        mvc.perform(get("/api/v1/alunos")).andExpect(status().isUnauthorized());
    }
}
