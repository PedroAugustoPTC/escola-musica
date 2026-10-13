package br.com.felipeblaft.reports.entrada.web;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.felipeblaft.reports.domain.registry.Student;
import br.com.felipeblaft.reports.domain.registry.Teacher;
import br.com.felipeblaft.reports.suporte.CenarioIT;
import br.com.felipeblaft.reports.suporte.ContratoApiBase;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Contrato de /inicio, /alunos e /alunos/{id} (FR-012, FR-013, FR-009). */
class InicioAlunosContratoIT extends ContratoApiBase {

    private Student matteo;
    private Student helena;
    private Cookie sessao;

    @BeforeEach
    void carregar() throws Exception {
        Teacher clara = cenario.clara();
        Teacher bruno = cenario.bruno();
        matteo = cenario.aluno(clara, "Matteo Mafra", "Piano");
        cenario.aluno(clara, "Olavo Torrente", null);
        cenario.aluno(clara, "Miguel de Castro Torrente", "Violino");
        helena = cenario.aluno(bruno, "Helena Prado", "Flauta");
        sessao = entrar(CenarioIT.CODIGO_CLARA);
    }

    @Test
    void inicioComTotalAlunosELimite() throws Exception {
        mvc.perform(get("/api/v1/inicio").cookie(sessao))
                .andExpect(status().isOk())
                .andExpect(segueContrato())
                .andExpect(jsonPath("$.professor.nome").value("Clara Souza"))
                .andExpect(jsonPath("$.totalAlunos").value(3))
                .andExpect(jsonPath("$.alunos", hasSize(3)))
                .andExpect(jsonPath("$.alunos[0].nome").value("Matteo Mafra"))
                .andExpect(jsonPath("$.relatoriosEmAberto", hasSize(0)))
                .andExpect(jsonPath("$.limiteAudioMb").value(50));
    }

    @Test
    void buscaTodosEncontradosENenhum() throws Exception {
        mvc.perform(get("/api/v1/alunos").cookie(sessao))
                .andExpect(status().isOk())
                .andExpect(segueContrato())
                .andExpect(jsonPath("$.resultado").value("TODOS"))
                .andExpect(jsonPath("$.alunos", hasSize(3)));
        mvc.perform(get("/api/v1/alunos").param("busca", "TÓRR").cookie(sessao))
                .andExpect(status().isOk())
                .andExpect(segueContrato())
                .andExpect(jsonPath("$.resultado").value("ENCONTRADOS"))
                .andExpect(jsonPath("$.alunos", hasSize(2)));
        mvc.perform(get("/api/v1/alunos").param("busca", "Helena").cookie(sessao))
                .andExpect(status().isOk())
                .andExpect(segueContrato())
                .andExpect(jsonPath("$.resultado").value("NENHUM"))
                .andExpect(jsonPath("$.alunos", hasSize(3)));
    }

    @Test
    void resumoDoAlunoEAcessoCruzado() throws Exception {
        mvc.perform(get("/api/v1/alunos/{id}", matteo.id()).cookie(sessao))
                .andExpect(status().isOk())
                .andExpect(segueContrato())
                .andExpect(jsonPath("$.nome").value("Matteo Mafra"))
                .andExpect(jsonPath("$.possuiRelatorioEmAberto").value(false));
        mvc.perform(get("/api/v1/alunos/{id}", helena.id()).cookie(sessao))
                .andExpect(status().isForbidden())
                .andExpect(segueContrato())
                .andExpect(jsonPath("$.codigo").value("ALUNO_NAO_ASSOCIADO"))
                .andExpect(jsonPath("$.mensagem").value("Este aluno não está associado a você."));
        mvc.perform(get("/api/v1/alunos/{id}", "nao-e-uuid").cookie(sessao))
                .andExpect(status().isBadRequest());
    }

    @Test
    void semSessaoTudoE401() throws Exception {
        mvc.perform(get("/api/v1/inicio"))
                .andExpect(status().isUnauthorized())
                .andExpect(segueContrato());
        mvc.perform(get("/api/v1/alunos")).andExpect(status().isUnauthorized());
    }
}
