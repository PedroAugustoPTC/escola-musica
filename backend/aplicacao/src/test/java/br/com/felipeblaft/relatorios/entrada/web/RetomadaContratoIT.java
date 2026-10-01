package br.com.felipeblaft.relatorios.entrada.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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
import org.springframework.mock.web.MockMultipartFile;

/**
 * Contrato da retomada (US3): relatório em aberto do aluno e descarte de registro em transcrição.
 */
class RetomadaContratoIT extends ContratoApiBase {

    private Aluno matteo;
    private Aluno helena;
    private Cookie sessao;
    private Cookie sessaoBruno;

    @BeforeEach
    void carregar() throws Exception {
        Professor clara = cenario.clara();
        Professor bruno = cenario.bruno();
        matteo = cenario.aluno(clara, "Matteo Ferreira", "Piano");
        helena = cenario.aluno(bruno, "Helena Prado", "Flauta");
        sessao = entrar(CenarioIT.CODIGO_CLARA);
        sessaoBruno = entrar(CenarioIT.CODIGO_BRUNO);
    }

    private UUID registrar() throws Exception {
        mvc.perform(
                        autenticada(
                                multipart("/api/v1/alunos/{id}/relatorios-aula", matteo.id())
                                        .file(
                                                new MockMultipartFile(
                                                        "audio",
                                                        "aula.webm",
                                                        "audio/webm",
                                                        NormalizacaoAudioTeste.AUDIO_COM_FALA)),
                                sessao))
                .andExpect(status().isAccepted());
        return jdbc.queryForObject("select id from trabalho_processamento", UUID.class);
    }

    @Test
    void semRelatorioEmAbertoDevolve204EAlunoDeOutroProfessor403() throws Exception {
        mvc.perform(get("/api/v1/alunos/{id}/relatorio-em-aberto", matteo.id()).cookie(sessao))
                .andExpect(status().isNoContent())
                .andExpect(segueContrato());
        mvc.perform(get("/api/v1/alunos/{id}/relatorio-em-aberto", helena.id()).cookie(sessao))
                .andExpect(status().isForbidden())
                .andExpect(segueContrato())
                .andExpect(jsonPath("$.codigo").value("ALUNO_NAO_ASSOCIADO"));
    }

    @Test
    void registroEmTranscricaoEDepoisRelatorioEmAberto() throws Exception {
        UUID trabalhoId = registrar();

        mvc.perform(get("/api/v1/alunos/{id}/relatorio-em-aberto", matteo.id()).cookie(sessao))
                .andExpect(status().isOk())
                .andExpect(segueContrato())
                .andExpect(jsonPath("$.tipo").value("AULA"))
                .andExpect(jsonPath("$.relatorioId").isEmpty())
                .andExpect(jsonPath("$.trabalhoId").value(trabalhoId.toString()))
                .andExpect(jsonPath("$.canalOrigem").value("WEB"));

        cenario.processarFila();

        mvc.perform(get("/api/v1/alunos/{id}/relatorio-em-aberto", matteo.id()).cookie(sessao))
                .andExpect(status().isOk())
                .andExpect(segueContrato())
                .andExpect(jsonPath("$.relatorioId").isString())
                .andExpect(jsonPath("$.status").value("AGUARDANDO_RESPOSTAS"));
    }

    @Test
    void descartarRegistroEmTranscricao() throws Exception {
        UUID trabalhoId = registrar();

        mvc.perform(autenticada(post("/api/v1/trabalhos/{id}/descarte", trabalhoId), sessaoBruno))
                .andExpect(status().isNotFound())
                .andExpect(segueContrato());
        mvc.perform(autenticada(post("/api/v1/trabalhos/{id}/descarte", trabalhoId), sessao))
                .andExpect(status().isNoContent())
                .andExpect(segueContrato());
        mvc.perform(autenticada(post("/api/v1/trabalhos/{id}/descarte", trabalhoId), sessao))
                .andExpect(status().isConflict())
                .andExpect(segueContrato())
                .andExpect(jsonPath("$.codigo").value("ACAO_NAO_PERMITIDA_NA_ETAPA"));
        mvc.perform(get("/api/v1/alunos/{id}/relatorio-em-aberto", matteo.id()).cookie(sessao))
                .andExpect(status().isNoContent());
        cenario.processarFila();
        mvc.perform(get("/api/v1/alunos/{id}/relatorio-em-aberto", matteo.id()).cookie(sessao))
                .andExpect(status().isNoContent());
    }
}
