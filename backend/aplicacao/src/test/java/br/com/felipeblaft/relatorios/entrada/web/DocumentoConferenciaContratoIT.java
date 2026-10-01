package br.com.felipeblaft.relatorios.entrada.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.felipeblaft.relatorios.dominio.cadastro.Aluno;
import br.com.felipeblaft.relatorios.dominio.cadastro.Professor;
import br.com.felipeblaft.relatorios.suporte.CenarioIT;
import br.com.felipeblaft.relatorios.suporte.ContratoApiBase;
import br.com.felipeblaft.relatorios.suporte.NormalizacaoAudioTeste;
import jakarta.servlet.http.Cookie;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;

/** Entrega autorizada dos PDFs (R-11, FR-048): inline, sem cache e sem identificador interno no nome. */
class DocumentoConferenciaContratoIT extends ContratoApiBase {

    private Cookie sessao;
    private Cookie sessaoBruno;
    private UUID relatorioId;

    @BeforeEach
    void chegarAConferencia() throws Exception {
        Professor clara = cenario.clara();
        cenario.bruno();
        Aluno matteo = cenario.aluno(clara, "Matteo Ferreira", "Piano");
        sessao = entrar(CenarioIT.CODIGO_CLARA);
        sessaoBruno = entrar(CenarioIT.CODIGO_BRUNO);
        mvc.perform(
                        autenticada(
                                multipart("/api/v1/alunos/{id}/relatorios-aula", matteo.id())
                                        .file(
                                                new MockMultipartFile(
                                                        "audio", "aula.m4a", "audio/mp4", NormalizacaoAudioTeste.AUDIO_COM_FALA)),
                                sessao))
                .andExpect(status().isAccepted());
        cenario.processarFila();
        relatorioId = jdbc.queryForObject("select id from relatorio_aula", UUID.class);
        mvc.perform(
                        autenticada(post("/api/v1/relatorios-aula/{id}/respostas", relatorioId), sessao)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"texto\":\"Estudou bem.\"}"))
                .andExpect(status().isAccepted());
        cenario.processarFila();
    }

    @Test
    void pdfDeConferenciaInlineSemCacheENomeSemIdentificador() throws Exception {
        MvcResult resultado =
                mvc.perform(get("/api/v1/relatorios-aula/{id}/versoes/1/pdf", relatorioId).cookie(sessao))
                        .andExpect(status().isOk())
                        .andExpect(segueContrato())
                        .andExpect(header().string("Content-Type", "application/pdf"))
                        .andExpect(header().string("Content-Disposition", "inline; filename=\"relatorio-aula-2026-09-21-v1.pdf\""))
                        .andReturn();

        assertThat(resultado.getResponse().getHeader("Cache-Control")).contains("no-store").contains("private");
        assertThat(resultado.getResponse().getHeader("Content-Disposition")).doesNotContain(relatorioId.toString());
        byte[] pdf = resultado.getResponse().getContentAsByteArray();
        assertThat(new String(pdf, 0, 5, java.nio.charset.StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
    }

    @Test
    void outroProfessorEVersaoInexistenteRecebem404() throws Exception {
        mvc.perform(get("/api/v1/relatorios-aula/{id}/versoes/1/pdf", relatorioId).cookie(sessaoBruno))
                .andExpect(status().isNotFound())
                .andExpect(segueContrato());
        mvc.perform(get("/api/v1/relatorios-aula/{id}/versoes/7/pdf", relatorioId).cookie(sessao))
                .andExpect(status().isNotFound())
                .andExpect(segueContrato());
        mvc.perform(get("/api/v1/relatorios-aula/{id}/versoes/1/pdf", relatorioId))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void pdfOficialSoDepoisDaAprovacao() throws Exception {
        mvc.perform(get("/api/v1/relatorios-aula/{id}/pdf-oficial", relatorioId).cookie(sessao))
                .andExpect(status().isNotFound())
                .andExpect(segueContrato());
        mvc.perform(
                        autenticada(post("/api/v1/relatorios-aula/{id}/aprovacao", relatorioId), sessao)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"versaoConfirmada\":1}"))
                .andExpect(status().isOk());

        mvc.perform(get("/api/v1/relatorios-aula/{id}/pdf-oficial", relatorioId).cookie(sessao))
                .andExpect(status().isOk())
                .andExpect(segueContrato())
                .andExpect(header().string("Content-Disposition", "inline; filename=\"relatorio-aula-2026-09-21.pdf\""));
        mvc.perform(get("/api/v1/relatorios-aula/{id}/pdf-oficial", relatorioId).cookie(sessaoBruno))
                .andExpect(status().isNotFound());
    }
}
