package br.com.felipeblaft.relatorios.entrada.web;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.felipeblaft.relatorios.dominio.cadastro.Aluno;
import br.com.felipeblaft.relatorios.dominio.cadastro.Professor;
import br.com.felipeblaft.relatorios.suporte.CenarioIT;
import br.com.felipeblaft.relatorios.suporte.ContratoApiBase;
import br.com.felipeblaft.relatorios.suporte.NormalizacaoAudioTeste;
import jakarta.servlet.http.Cookie;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;

/** Contrato do relatório semestral (FR-041 a FR-046): semestres, prévia, geração e o ciclo. */
class RelatorioSemestralContratoIT extends ContratoApiBase {

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

    private void aulasNo1Semestre() {
        cenario.aulaAprovada(clara, matteo, LocalDate.of(2026, 3, 10), "Escala de Dó");
        cenario.aulaAprovada(clara, matteo, LocalDate.of(2026, 5, 20), "Estudo nº 5");
    }

    private void gerar(String corpo) throws Exception {
        mvc.perform(
                        autenticada(
                                        post(
                                                "/api/v1/alunos/{id}/relatorios-semestrais",
                                                matteo.id()),
                                        sessao)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(corpo))
                .andExpect(status().isAccepted())
                .andExpect(segueContrato())
                .andExpect(jsonPath("$.trabalhoId").isString());
    }

    private UUID semestralEmConferencia() throws Exception {
        aulasNo1Semestre();
        gerar("{\"periodoChave\":\"2026-1\"}");
        cenario.processarFila();
        return jdbc.queryForObject("select id from relatorio_semestral", UUID.class);
    }

    @Test
    void semestresEPrevia() throws Exception {
        aulasNo1Semestre();

        mvc.perform(get("/api/v1/alunos/{id}/semestres", matteo.id()).cookie(sessao))
                .andExpect(status().isOk())
                .andExpect(segueContrato())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].chave").value("2026-2"))
                .andExpect(jsonPath("$[0].rotulo").value("2º semestre 2026"))
                .andExpect(jsonPath("$[0].emAndamento").value(true))
                .andExpect(jsonPath("$[0].preSelecionado").value(true))
                .andExpect(jsonPath("$[1].inicio").value("2026-01-01"));
        mvc.perform(get("/api/v1/alunos/{id}/semestres/2026-1/previa", matteo.id()).cookie(sessao))
                .andExpect(status().isOk())
                .andExpect(segueContrato())
                .andExpect(jsonPath("$.aulasAprovadas").value(2))
                .andExpect(jsonPath("$.relatoriosAulaEmAberto").value(0))
                .andExpect(jsonPath("$.semestralAprovadoId").isEmpty());
        mvc.perform(get("/api/v1/alunos/{id}/semestres", helena.id()).cookie(sessao))
                .andExpect(status().isForbidden())
                .andExpect(segueContrato());
        mvc.perform(get("/api/v1/alunos/{id}/semestres/2026-9/previa", matteo.id()).cookie(sessao))
                .andExpect(status().isBadRequest());
    }

    @Test
    void geracaoRecusadaSemAulasEComSemestralEmAberto() throws Exception {
        mvc.perform(
                        autenticada(
                                        post(
                                                "/api/v1/alunos/{id}/relatorios-semestrais",
                                                matteo.id()),
                                        sessao)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"periodoChave\":\"2026-1\"}"))
                .andExpect(status().isConflict())
                .andExpect(segueContrato())
                .andExpect(jsonPath("$.codigo").value("SEM_AULAS_APROVADAS_NO_PERIODO"));

        aulasNo1Semestre();
        gerar("{\"periodoChave\":\"2026-1\"}");
        mvc.perform(
                        autenticada(
                                        post(
                                                "/api/v1/alunos/{id}/relatorios-semestrais",
                                                matteo.id()),
                                        sessao)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"periodoChave\":\"2026-1\"}"))
                .andExpect(status().isConflict())
                .andExpect(segueContrato())
                .andExpect(jsonPath("$.codigo").value("RELATORIO_EM_ABERTO"))
                .andExpect(jsonPath("$.detalhes.relatorioEmAberto.tipo").value("SEMESTRAL"));
        cenario.processarFila();
    }

    @Test
    void conferenciaAlteracaoAprovacaoEPdf() throws Exception {
        UUID id = semestralEmConferencia();

        mvc.perform(get("/api/v1/relatorios-semestrais/{id}", id).cookie(sessao))
                .andExpect(status().isOk())
                .andExpect(segueContrato())
                .andExpect(jsonPath("$.status").value("AGUARDANDO_CONFERENCIA"))
                .andExpect(jsonPath("$.versaoVigente").value(1))
                .andExpect(jsonPath("$.periodoChave").value("2026-1"))
                .andExpect(jsonPath("$.informacoesGerais.nome").value("Matteo Ferreira"))
                .andExpect(jsonPath("$.informacoesGerais.quantidadeAulasConsideradas").value(2))
                .andExpect(jsonPath("$.informacoesGerais.periodoRotulo").value("1º semestre 2026"))
                .andExpect(jsonPath("$.conteudo.parecerFinal").isString())
                .andExpect(jsonPath("$.quantidadeRelatoriosEmAberto").value(0));
        mvc.perform(get("/api/v1/relatorios-semestrais/{id}", id).cookie(sessaoBruno))
                .andExpect(status().isNotFound())
                .andExpect(segueContrato());
        mvc.perform(get("/api/v1/relatorios-semestrais/{id}/versoes/1/pdf", id).cookie(sessao))
                .andExpect(status().isOk())
                .andExpect(segueContrato())
                .andExpect(
                        header().string(
                                        "Content-Disposition",
                                        "inline; filename=\"relatorio-semestral-2026-1-v1.pdf\""));

        mvc.perform(
                        autenticada(
                                        post("/api/v1/relatorios-semestrais/{id}/alteracoes", id),
                                        sessao)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"texto\":\"Parecer: excelente semestre.\",\"versaoBase\":1}"))
                .andExpect(status().isAccepted())
                .andExpect(segueContrato());
        cenario.processarFila();
        mvc.perform(
                        autenticada(
                                multipart("/api/v1/relatorios-semestrais/{id}/alteracoes", id)
                                        .file(
                                                new MockMultipartFile(
                                                        "audio",
                                                        "a.webm",
                                                        "audio/webm",
                                                        NormalizacaoAudioTeste.AUDIO_COM_FALA))
                                        .param("versaoBase", "2"),
                                sessao))
                .andExpect(status().isAccepted())
                .andExpect(respostaSegueContrato());
        cenario.processarFila();
        mvc.perform(get("/api/v1/relatorios-semestrais/{id}", id).cookie(sessao))
                .andExpect(jsonPath("$.versaoVigente").value(3))
                .andExpect(jsonPath("$.ultimaInteracao.meio").value("AUDIO"));

        mvc.perform(
                        autenticada(
                                        post("/api/v1/relatorios-semestrais/{id}/aprovacao", id),
                                        sessao)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"versaoConfirmada\":2}"))
                .andExpect(status().isConflict())
                .andExpect(segueContrato())
                .andExpect(jsonPath("$.codigo").value("VERSAO_DESATUALIZADA"));
        mvc.perform(
                        autenticada(
                                        post("/api/v1/relatorios-semestrais/{id}/aprovacao", id),
                                        sessao)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"versaoConfirmada\":3}"))
                .andExpect(status().isOk())
                .andExpect(segueContrato())
                .andExpect(jsonPath("$.versaoAprovada").value(3));
        mvc.perform(get("/api/v1/relatorios-semestrais/{id}/pdf-oficial", id).cookie(sessao))
                .andExpect(status().isOk())
                .andExpect(segueContrato());
        mvc.perform(
                        autenticada(
                                        post(
                                                "/api/v1/alunos/{id}/relatorios-semestrais",
                                                matteo.id()),
                                        sessao)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"periodoChave\":\"2026-1\"}"))
                .andExpect(status().isConflict())
                .andExpect(segueContrato())
                .andExpect(jsonPath("$.codigo").value("SEMESTRAL_JA_APROVADO"));
    }

    @Test
    void relatoriosDeAulaEmAbertoPedemConfirmacaoECancelamento() throws Exception {
        aulasNo1Semestre();
        jdbc.update(
                "update relatorio_aula set status = 'AGUARDANDO_CONFERENCIA', aprovado_em = null,"
                        + " versao_aprovada = null, canal_aprovacao = null"
                        + " where id = (select r.id from relatorio_aula r join aula a on a.id = r.aula_id"
                        + " where a.data_aula = '2026-05-20')");
        cenario.aulaAprovada(clara, matteo, LocalDate.of(2026, 4, 2), "Leitura");

        mvc.perform(
                        autenticada(
                                        post(
                                                "/api/v1/alunos/{id}/relatorios-semestrais",
                                                matteo.id()),
                                        sessao)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"periodoChave\":\"2026-1\"}"))
                .andExpect(status().isConflict())
                .andExpect(segueContrato())
                .andExpect(jsonPath("$.codigo").value("HA_RELATORIOS_EM_ABERTO"))
                .andExpect(jsonPath("$.detalhes.quantidade").value(1));
        gerar("{\"periodoChave\":\"2026-1\",\"confirmarMesmoComEmAberto\":true}");
        cenario.processarFila();
        UUID id = jdbc.queryForObject("select id from relatorio_semestral", UUID.class);
        mvc.perform(get("/api/v1/relatorios-semestrais/{id}", id).cookie(sessao))
                .andExpect(jsonPath("$.quantidadeRelatoriosEmAberto").value(1))
                .andExpect(jsonPath("$.informacoesGerais.quantidadeAulasConsideradas").value(2));

        mvc.perform(
                        autenticada(
                                post("/api/v1/relatorios-semestrais/{id}/cancelamento", id),
                                sessao))
                .andExpect(status().isNoContent())
                .andExpect(segueContrato());
        mvc.perform(
                        autenticada(
                                post("/api/v1/relatorios-semestrais/{id}/cancelamento", id),
                                sessao))
                .andExpect(status().isConflict())
                .andExpect(segueContrato());
    }
}
