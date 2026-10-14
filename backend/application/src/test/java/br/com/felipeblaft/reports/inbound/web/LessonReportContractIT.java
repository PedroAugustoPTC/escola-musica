package br.com.felipeblaft.reports.inbound.web;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
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
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;

/**
 * Contrato dos endpoints de relatório de aula (registro, respostas, alteração, aprovação,
 * cancelamento).
 */
class LessonReportContractIT extends ContractApiBase {

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

    private static MockMultipartFile audio(String type, byte[] content) {
        return new MockMultipartFile("audio", "aula.webm", type, content);
    }

    private static MockMultipartFile audio() {
        return audio("audio/webm;codecs=opus", AudioNormalizationTest.AUDIO_WITH_SPEECH);
    }

    private UUID register() throws Exception {
        String body =
                mvc.perform(
                                authenticated(
                                        multipart(
                                                        "/api/v1/alunos/{id}/relatorios-aula",
                                                        matteo.id())
                                                .file(audio()),
                                        session))
                        .andExpect(status().isAccepted())
                        .andExpect(replyFollowsContract())
                        .andExpect(jsonPath("$.trabalhoId").isString())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        return UUID.fromString(body.replaceAll(".*\"trabalhoId\":\"([^\"]+)\".*", "$1"));
    }

    private UUID reportWithQuestions() throws Exception {
        register();
        scenario.processQueue();
        return jdbc.queryForObject("select id from lesson_report", UUID.class);
    }

    private UUID reportInReview() throws Exception {
        UUID id = reportWithQuestions();
        mvc.perform(
                        authenticated(post("/api/v1/relatorios-aula/{id}/respostas", id), session)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"texto\":\"Estudou bem.\"}"))
                .andExpect(status().isAccepted());
        scenario.processQueue();
        return id;
    }

    @Test
    void registerAcceptsAudioAndReturnsJob() throws Exception {
        UUID jobId = register();

        mvc.perform(get("/api/v1/trabalhos/{id}", jobId).cookie(session))
                .andExpect(status().isOk())
                .andExpect(followsContract())
                .andExpect(jsonPath("$.tipo").value("REGISTRO_AULA"))
                .andExpect(jsonPath("$.status").value("PENDENTE"))
                .andExpect(jsonPath("$.etapa").value("ENVIANDO"))
                .andExpect(jsonPath("$.demorando").value(false));
    }

    @Test
    void registerRefusalUnassignedStudentFormatSizeAndOpenReport() throws Exception {
        mvc.perform(
                        authenticated(
                                multipart("/api/v1/alunos/{id}/relatorios-aula", helena.id())
                                        .file(audio()),
                                session))
                .andExpect(status().isForbidden())
                .andExpect(replyFollowsContract())
                .andExpect(jsonPath("$.codigo").value("ALUNO_NAO_ASSOCIADO"));
        mvc.perform(
                        authenticated(
                                multipart("/api/v1/alunos/{id}/relatorios-aula", matteo.id())
                                        .file(
                                                new MockMultipartFile(
                                                        "audio",
                                                        "notas.txt",
                                                        "text/plain",
                                                        new byte[] {1})),
                                session))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(replyFollowsContract())
                .andExpect(jsonPath("$.codigo").value("FORMATO_NAO_SUPORTADO"));
        mvc.perform(
                        authenticated(
                                multipart("/api/v1/alunos/{id}/relatorios-aula", matteo.id())
                                        .file(audio("audio/mpeg", new byte[50 * 1024 * 1024 + 1])),
                                session))
                .andExpect(status().isContentTooLarge())
                .andExpect(replyFollowsContract())
                .andExpect(jsonPath("$.codigo").value("AUDIO_MUITO_GRANDE"))
                .andExpect(jsonPath("$.detalhes.limiteMb").value(50));

        register();
        mvc.perform(
                        authenticated(
                                multipart("/api/v1/alunos/{id}/relatorios-aula", matteo.id())
                                        .file(audio()),
                                session))
                .andExpect(status().isConflict())
                .andExpect(replyFollowsContract())
                .andExpect(jsonPath("$.codigo").value("RELATORIO_EM_ABERTO"))
                .andExpect(jsonPath("$.mensagem", startsWith("Você tem um relatório em aberto.")))
                .andExpect(jsonPath("$.detalhes.relatorioEmAberto.canalOrigem").value("WEB"));
    }

    @Test
    void getReportWithQuestionsAndOtherTeacherGets404() throws Exception {
        UUID id = reportWithQuestions();

        mvc.perform(get("/api/v1/relatorios-aula/{id}", id).cookie(session))
                .andExpect(status().isOk())
                .andExpect(followsContract())
                .andExpect(jsonPath("$.status").value("AGUARDANDO_RESPOSTAS"))
                .andExpect(jsonPath("$.alunoNome").value("Matteo Ferreira"))
                .andExpect(jsonPath("$.professorNome").value("Clara Souza"))
                .andExpect(jsonPath("$.dataAula").value("2026-09-21"))
                .andExpect(jsonPath("$.perguntasPendentes", hasSize(2)))
                .andExpect(jsonPath("$.versaoVigente").value(0))
                .andExpect(jsonPath("$.pdfDisponivel").value(false))
                .andExpect(jsonPath("$.canalOrigem").value("WEB"));
        mvc.perform(get("/api/v1/relatorios-aula/{id}", id).cookie(sessionBruno))
                .andExpect(status().isNotFound())
                .andExpect(followsContract())
                .andExpect(jsonPath("$.codigo").value("RECURSO_NAO_ENCONTRADO"));
    }

    @Test
    void answersByTextAndAudioAndEmptyAnswer() throws Exception {
        UUID id = reportWithQuestions();

        mvc.perform(
                        authenticated(post("/api/v1/relatorios-aula/{id}/respostas", id), session)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"texto\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(followsContract())
                .andExpect(jsonPath("$.codigo").value("RESPOSTA_VAZIA"));
        mvc.perform(
                        authenticated(
                                multipart("/api/v1/relatorios-aula/{id}/respostas", id)
                                        .file(audio()),
                                session))
                .andExpect(status().isAccepted())
                .andExpect(replyFollowsContract());
        mvc.perform(
                        authenticated(post("/api/v1/relatorios-aula/{id}/respostas", id), session)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"texto\":\"Estudou bem.\"}"))
                .andExpect(status().isConflict())
                .andExpect(followsContract())
                .andExpect(jsonPath("$.codigo").value("PROCESSAMENTO_EM_ANDAMENTO"));
    }

    @Test
    void changesWithBaseVersionAndNewAttemptOutsideStep() throws Exception {
        UUID id = reportInReview();

        mvc.perform(get("/api/v1/relatorios-aula/{id}", id).cookie(session))
                .andExpect(status().isOk())
                .andExpect(followsContract())
                .andExpect(jsonPath("$.status").value("AGUARDANDO_CONFERENCIA"))
                .andExpect(jsonPath("$.versaoVigente").value(1))
                .andExpect(jsonPath("$.pdfDisponivel").value(true))
                .andExpect(jsonPath("$.ultimaInteracao.tipo").value("RESPOSTA_PERGUNTAS"))
                .andExpect(jsonPath("$.ultimaInteracao.texto").value("Estudou bem."));
        mvc.perform(
                        authenticated(post("/api/v1/relatorios-aula/{id}/alteracoes", id), session)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"texto\":\"Diga que foi ótima.\",\"versaoBase\":3}"))
                .andExpect(status().isConflict())
                .andExpect(followsContract())
                .andExpect(jsonPath("$.codigo").value("VERSAO_DESATUALIZADA"))
                .andExpect(jsonPath("$.detalhes.versaoVigente").value(1));
        mvc.perform(
                        authenticated(post("/api/v1/relatorios-aula/{id}/alteracoes", id), session)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"texto\":\"   \",\"versaoBase\":1}"))
                .andExpect(status().isBadRequest())
                .andExpect(followsContract())
                .andExpect(jsonPath("$.codigo").value("ALTERACAO_VAZIA"));
        mvc.perform(authenticated(post("/api/v1/relatorios-aula/{id}/nova-tentativa", id), session))
                .andExpect(status().isConflict())
                .andExpect(followsContract());
        mvc.perform(
                        authenticated(
                                multipart("/api/v1/relatorios-aula/{id}/alteracoes", id)
                                        .file(audio())
                                        .param("versaoBase", "1"),
                                session))
                .andExpect(status().isAccepted())
                .andExpect(replyFollowsContract());
        scenario.processQueue();

        mvc.perform(get("/api/v1/relatorios-aula/{id}", id).cookie(session))
                .andExpect(jsonPath("$.versaoVigente").value(2))
                .andExpect(jsonPath("$.ultimaInteracao.meio").value("AUDIO"));
    }

    @Test
    void approveCitingVersionAndSecondApprovalRefused() throws Exception {
        UUID id = reportInReview();

        mvc.perform(
                        authenticated(post("/api/v1/relatorios-aula/{id}/aprovacao", id), session)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"versaoConfirmada\":1}"))
                .andExpect(status().isOk())
                .andExpect(followsContract())
                .andExpect(jsonPath("$.status").value("APROVADO"))
                .andExpect(jsonPath("$.versaoAprovada").value(1))
                .andExpect(jsonPath("$.aprovadoEm").value("2026-09-21T13:00:00Z"));
        mvc.perform(
                        authenticated(post("/api/v1/relatorios-aula/{id}/aprovacao", id), session)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"versaoConfirmada\":1}"))
                .andExpect(status().isConflict())
                .andExpect(followsContract())
                .andExpect(jsonPath("$.codigo").value("RELATORIO_JA_APROVADO"));
    }

    @Test
    void cancelAndAfterwardsAcceptsNoMoreActions() throws Exception {
        UUID id = reportInReview();

        mvc.perform(
                        authenticated(
                                post("/api/v1/relatorios-aula/{id}/cancelamento", id),
                                sessionBruno))
                .andExpect(status().isNotFound())
                .andExpect(followsContract());
        mvc.perform(authenticated(post("/api/v1/relatorios-aula/{id}/cancelamento", id), session))
                .andExpect(status().isNoContent())
                .andExpect(followsContract());
        mvc.perform(
                        authenticated(post("/api/v1/relatorios-aula/{id}/aprovacao", id), session)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"versaoConfirmada\":1}"))
                .andExpect(status().isConflict())
                .andExpect(followsContract())
                .andExpect(jsonPath("$.codigo").value("RELATORIO_CANCELADO"));
    }

    @Test
    void otherTeachersJobDoesNotExist() throws Exception {
        UUID jobId = register();

        mvc.perform(get("/api/v1/trabalhos/{id}", jobId).cookie(sessionBruno))
                .andExpect(status().isNotFound())
                .andExpect(followsContract());
        scenario.processQueue();
        mvc.perform(get("/api/v1/trabalhos/{id}", jobId).cookie(session))
                .andExpect(status().isOk())
                .andExpect(followsContract())
                .andExpect(jsonPath("$.status").value("CONCLUIDO"))
                .andExpect(jsonPath("$.resultadoCodigo").value("PERGUNTAS_PENDENTES"))
                .andExpect(jsonPath("$.relatorioTipo").value("AULA"));
    }
}
