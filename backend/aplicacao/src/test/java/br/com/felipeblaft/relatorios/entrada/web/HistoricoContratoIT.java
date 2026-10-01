package br.com.felipeblaft.relatorios.entrada.web;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.felipeblaft.relatorios.dominio.cadastro.Aluno;
import br.com.felipeblaft.relatorios.dominio.cadastro.Professor;
import br.com.felipeblaft.relatorios.dominio.relatorio.RelatorioAula;
import br.com.felipeblaft.relatorios.suporte.CenarioIT;
import br.com.felipeblaft.relatorios.suporte.ContratoApiBase;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/** Contrato do histórico e dos PDFs oficiais (FR-047, FR-048). */
class HistoricoContratoIT extends ContratoApiBase {

    private Professor clara;
    private Aluno matteo;
    private Aluno helena;
    private Cookie sessao;
    private Cookie sessaoBruno;

    @BeforeEach
    void carregar() throws Exception {
        clara = cenario.clara();
        Professor bruno = cenario.bruno();
        matteo = cenario.aluno(clara, "Matteo Ferreira", "Piano");
        helena = cenario.aluno(bruno, "Helena Prado", "Flauta");
        sessao = entrar(CenarioIT.CODIGO_CLARA);
        sessaoBruno = entrar(CenarioIT.CODIGO_BRUNO);
    }

    @Test
    void historicoVazioEAlunoDeOutroProfessor() throws Exception {
        mvc.perform(get("/api/v1/alunos/{id}/historico", matteo.id()).cookie(sessao))
                .andExpect(status().isOk())
                .andExpect(segueContrato())
                .andExpect(jsonPath("$.aluno.nome").value("Matteo Ferreira"))
                .andExpect(jsonPath("$.itens", hasSize(0)));
        mvc.perform(get("/api/v1/alunos/{id}/historico", helena.id()).cookie(sessao))
                .andExpect(status().isForbidden())
                .andExpect(segueContrato());
    }

    @Test
    void aulasESemestralDoMaisRecenteAoMaisAntigoComPdfOficial() throws Exception {
        RelatorioAula marco =
                cenario.aulaAprovada(clara, matteo, LocalDate.of(2026, 3, 10), "Escala");
        relogio.avancar(Duration.ofMinutes(1));
        RelatorioAula setembro =
                cenario.aulaAprovada(clara, matteo, LocalDate.of(2026, 9, 1), "Estudo");
        mvc.perform(
                        autenticada(
                                        post(
                                                "/api/v1/alunos/{id}/relatorios-semestrais",
                                                matteo.id()),
                                        sessao)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"periodoChave\":\"2026-1\"}"))
                .andExpect(status().isAccepted());
        cenario.processarFila();
        UUID semestral = jdbc.queryForObject("select id from relatorio_semestral", UUID.class);
        mvc.perform(
                        autenticada(
                                        post(
                                                "/api/v1/relatorios-semestrais/{id}/aprovacao",
                                                semestral),
                                        sessao)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"versaoConfirmada\":1}"))
                .andExpect(status().isOk());

        mvc.perform(get("/api/v1/alunos/{id}/historico", matteo.id()).cookie(sessao))
                .andExpect(status().isOk())
                .andExpect(segueContrato())
                .andExpect(jsonPath("$.itens", hasSize(3)))
                .andExpect(jsonPath("$.itens[0].relatorioId").value(setembro.id().toString()))
                .andExpect(jsonPath("$.itens[0].rotulo").value("Aula de 01/09/2026"))
                .andExpect(jsonPath("$.itens[0].dataAula").value("2026-09-01"))
                .andExpect(jsonPath("$.itens[1].relatorioId").value(marco.id().toString()))
                .andExpect(jsonPath("$.itens[2].tipo").value("SEMESTRAL"))
                .andExpect(
                        jsonPath("$.itens[2].rotulo").value("Semestral (01/01/2026 a 30/06/2026)"))
                .andExpect(jsonPath("$.itens[2].periodoInicio").value("2026-01-01"));

        mvc.perform(get("/api/v1/relatorios-aula/{id}/pdf-oficial", setembro.id()).cookie(sessao))
                .andExpect(status().isOk())
                .andExpect(segueContrato())
                .andExpect(
                        header().string(
                                        "Content-Disposition",
                                        "inline; filename=\"relatorio-aula-2026-09-01.pdf\""));
        mvc.perform(get("/api/v1/relatorios-semestrais/{id}/pdf-oficial", semestral).cookie(sessao))
                .andExpect(status().isOk())
                .andExpect(segueContrato())
                .andExpect(
                        header().string(
                                        "Content-Disposition",
                                        "inline; filename=\"relatorio-semestral-2026-1.pdf\""));
        mvc.perform(
                        get("/api/v1/relatorios-aula/{id}/pdf-oficial", setembro.id())
                                .cookie(sessaoBruno))
                .andExpect(status().isNotFound())
                .andExpect(segueContrato());
        mvc.perform(
                        get("/api/v1/relatorios-semestrais/{id}/pdf-oficial", semestral)
                                .cookie(sessaoBruno))
                .andExpect(status().isNotFound())
                .andExpect(segueContrato());
    }
}
