package br.com.felipeblaft.reports.inbound.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.felipeblaft.reports.domain.registry.Student;
import br.com.felipeblaft.reports.domain.registry.Teacher;
import br.com.felipeblaft.reports.support.AudioNormalizationTest;
import br.com.felipeblaft.reports.support.ContractApiBase;
import br.com.felipeblaft.reports.support.ScenarioIT;
import jakarta.servlet.http.Cookie;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

/**
 * Contrato da retomada (US3): relatório em aberto do aluno e descarte de registro em transcrição.
 */
class ResumptionContractIT extends ContractApiBase {

    private Student matteo;
    private Student helena;
    private Cookie session;
    private Cookie sessionBruno;

    @BeforeEach
    void load() throws Exception {
        Teacher clara = scenario.clara();
        Teacher bruno = scenario.bruno();
        matteo = scenario.student(clara, "Matteo Ferreira", "Piano");
        helena = scenario.student(bruno, "Helena Prado", "Flauta");
        session = signIn(ScenarioIT.CODE_CLARA);
        sessionBruno = signIn(ScenarioIT.CODE_BRUNO);
    }

    private UUID register() throws Exception {
        mvc.perform(
                        authenticated(
                                multipart("/api/v1/alunos/{id}/relatorios-aula", matteo.id())
                                        .file(
                                                new MockMultipartFile(
                                                        "audio",
                                                        "aula.webm",
                                                        "audio/webm",
                                                        AudioNormalizationTest.AUDIO_WITH_SPEECH)),
                                session))
                .andExpect(status().isAccepted());
        return jdbc.queryForObject("select id from processing_job", UUID.class);
    }

    @Test
    void withoutOpenReportReturns204AndOtherTeachersStudent403() throws Exception {
        mvc.perform(get("/api/v1/alunos/{id}/relatorio-em-aberto", matteo.id()).cookie(session))
                .andExpect(status().isNoContent())
                .andExpect(followsContract());
        mvc.perform(get("/api/v1/alunos/{id}/relatorio-em-aberto", helena.id()).cookie(session))
                .andExpect(status().isForbidden())
                .andExpect(followsContract())
                .andExpect(jsonPath("$.codigo").value("ALUNO_NAO_ASSOCIADO"));
    }

    @Test
    void registrationInTranscriptionAndThenOpenReport() throws Exception {
        UUID jobId = register();

        mvc.perform(get("/api/v1/alunos/{id}/relatorio-em-aberto", matteo.id()).cookie(session))
                .andExpect(status().isOk())
                .andExpect(followsContract())
                .andExpect(jsonPath("$.tipo").value("AULA"))
                .andExpect(jsonPath("$.relatorioId").isEmpty())
                .andExpect(jsonPath("$.trabalhoId").value(jobId.toString()))
                .andExpect(jsonPath("$.canalOrigem").value("WEB"));

        scenario.processQueue();

        mvc.perform(get("/api/v1/alunos/{id}/relatorio-em-aberto", matteo.id()).cookie(session))
                .andExpect(status().isOk())
                .andExpect(followsContract())
                .andExpect(jsonPath("$.relatorioId").isString())
                .andExpect(jsonPath("$.status").value("AGUARDANDO_RESPOSTAS"));
    }

    @Test
    void discardRegistrationInTranscription() throws Exception {
        UUID jobId = register();

        mvc.perform(authenticated(post("/api/v1/trabalhos/{id}/descarte", jobId), sessionBruno))
                .andExpect(status().isNotFound())
                .andExpect(followsContract());
        mvc.perform(authenticated(post("/api/v1/trabalhos/{id}/descarte", jobId), session))
                .andExpect(status().isNoContent())
                .andExpect(followsContract());
        mvc.perform(authenticated(post("/api/v1/trabalhos/{id}/descarte", jobId), session))
                .andExpect(status().isConflict())
                .andExpect(followsContract())
                .andExpect(jsonPath("$.codigo").value("ACAO_NAO_PERMITIDA_NA_ETAPA"));
        mvc.perform(get("/api/v1/alunos/{id}/relatorio-em-aberto", matteo.id()).cookie(session))
                .andExpect(status().isNoContent());
        scenario.processQueue();
        mvc.perform(get("/api/v1/alunos/{id}/relatorio-em-aberto", matteo.id()).cookie(session))
                .andExpect(status().isNoContent());
    }
}
