package br.com.felipeblaft.reports.inbound.web;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.felipeblaft.reports.domain.registry.Student;
import br.com.felipeblaft.reports.domain.registry.Teacher;
import br.com.felipeblaft.reports.support.AudioNormalizationTest;
import br.com.felipeblaft.reports.support.ContractApiBase;
import br.com.felipeblaft.reports.support.ScenarioIT;
import jakarta.servlet.http.Cookie;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;

/** Contrato do relatório semestral (FR-041 a FR-046): semestres, prévia, geração e o ciclo. */
class SemesterReportContractIT extends ContractApiBase {

    private Teacher clara;
    private Student matteo;
    private Student helena;
    private Cookie session;
    private Cookie sessionBruno;

    @BeforeEach
    void load() throws Exception {
        clara = scenario.clara();
        Teacher bruno = scenario.bruno();
        matteo = scenario.student(clara, "Matteo Ferreira", "Piano");
        helena = scenario.student(bruno, "Helena Prado", "Flauta");
        session = signIn(ScenarioIT.CODE_CLARA);
        sessionBruno = signIn(ScenarioIT.CODE_BRUNO);
    }

    private void lessonsInSemester1() {
        scenario.approvedLesson(clara, matteo, LocalDate.of(2026, 3, 10), "Escala de Dó");
        scenario.approvedLesson(clara, matteo, LocalDate.of(2026, 5, 20), "Estudo nº 5");
    }

    private void generate(String body) throws Exception {
        mvc.perform(
                        authenticated(
                                        post(
                                                "/api/v1/alunos/{id}/relatorios-semestrais",
                                                matteo.id()),
                                        session)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                .andExpect(status().isAccepted())
                .andExpect(followsContract())
                .andExpect(jsonPath("$.trabalhoId").isString());
    }

    private UUID semesterInReview() throws Exception {
        lessonsInSemester1();
        generate("{\"periodoChave\":\"2026-1\"}");
        scenario.processQueue();
        return jdbc.queryForObject("select id from semester_report", UUID.class);
    }

    @Test
    void semestersAndPreview() throws Exception {
        lessonsInSemester1();

        mvc.perform(get("/api/v1/alunos/{id}/semestres", matteo.id()).cookie(session))
                .andExpect(status().isOk())
                .andExpect(followsContract())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].chave").value("2026-2"))
                .andExpect(jsonPath("$[0].rotulo").value("2º semestre 2026"))
                .andExpect(jsonPath("$[0].emAndamento").value(true))
                .andExpect(jsonPath("$[0].preSelecionado").value(true))
                .andExpect(jsonPath("$[1].inicio").value("2026-01-01"));
        mvc.perform(get("/api/v1/alunos/{id}/semestres/2026-1/previa", matteo.id()).cookie(session))
                .andExpect(status().isOk())
                .andExpect(followsContract())
                .andExpect(jsonPath("$.aulasAprovadas").value(2))
                .andExpect(jsonPath("$.relatoriosAulaEmAberto").value(0))
                .andExpect(jsonPath("$.semestralAprovadoId").isEmpty());
        mvc.perform(get("/api/v1/alunos/{id}/semestres", helena.id()).cookie(session))
                .andExpect(status().isForbidden())
                .andExpect(followsContract());
        mvc.perform(get("/api/v1/alunos/{id}/semestres/2026-9/previa", matteo.id()).cookie(session))
                .andExpect(status().isBadRequest());
    }

    @Test
    void generationRefusedWithoutLessonsAndWithOpenSemester() throws Exception {
        mvc.perform(
                        authenticated(
                                        post(
                                                "/api/v1/alunos/{id}/relatorios-semestrais",
                                                matteo.id()),
                                        session)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"periodoChave\":\"2026-1\"}"))
                .andExpect(status().isConflict())
                .andExpect(followsContract())
                .andExpect(jsonPath("$.codigo").value("SEM_AULAS_APROVADAS_NO_PERIODO"));

        lessonsInSemester1();
        generate("{\"periodoChave\":\"2026-1\"}");
        mvc.perform(
                        authenticated(
                                        post(
                                                "/api/v1/alunos/{id}/relatorios-semestrais",
                                                matteo.id()),
                                        session)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"periodoChave\":\"2026-1\"}"))
                .andExpect(status().isConflict())
                .andExpect(followsContract())
                .andExpect(jsonPath("$.codigo").value("RELATORIO_EM_ABERTO"))
                .andExpect(jsonPath("$.detalhes.relatorioEmAberto.tipo").value("SEMESTRAL"));
        scenario.processQueue();
    }

    @Test
    void reviewChangeApprovalAndPdf() throws Exception {
        UUID id = semesterInReview();

        mvc.perform(get("/api/v1/relatorios-semestrais/{id}", id).cookie(session))
                .andExpect(status().isOk())
                .andExpect(followsContract())
                .andExpect(jsonPath("$.status").value("AGUARDANDO_CONFERENCIA"))
                .andExpect(jsonPath("$.versaoVigente").value(1))
                .andExpect(jsonPath("$.periodoChave").value("2026-1"))
                .andExpect(jsonPath("$.informacoesGerais.nome").value("Matteo Ferreira"))
                .andExpect(jsonPath("$.informacoesGerais.quantidadeAulasConsideradas").value(2))
                .andExpect(jsonPath("$.informacoesGerais.periodoRotulo").value("1º semestre 2026"))
                .andExpect(jsonPath("$.conteudo.parecerFinal").isString())
                .andExpect(jsonPath("$.quantidadeRelatoriosEmAberto").value(0));
        mvc.perform(get("/api/v1/relatorios-semestrais/{id}", id).cookie(sessionBruno))
                .andExpect(status().isNotFound())
                .andExpect(followsContract());
        mvc.perform(get("/api/v1/relatorios-semestrais/{id}/versoes/1/pdf", id).cookie(session))
                .andExpect(status().isOk())
                .andExpect(followsContract())
                .andExpect(
                        header().string(
                                        "Content-Disposition",
                                        "inline; filename=\"relatorio-semestral-2026-1-v1.pdf\""));

        mvc.perform(
                        authenticated(
                                        post("/api/v1/relatorios-semestrais/{id}/alteracoes", id),
                                        session)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"texto\":\"Parecer: excelente semestre.\",\"versaoBase\":1}"))
                .andExpect(status().isAccepted())
                .andExpect(followsContract());
        scenario.processQueue();
        mvc.perform(
                        authenticated(
                                multipart("/api/v1/relatorios-semestrais/{id}/alteracoes", id)
                                        .file(
                                                new MockMultipartFile(
                                                        "audio",
                                                        "a.webm",
                                                        "audio/webm",
                                                        AudioNormalizationTest.AUDIO_WITH_SPEECH))
                                        .param("versaoBase", "2"),
                                session))
                .andExpect(status().isAccepted())
                .andExpect(replyFollowsContract());
        scenario.processQueue();
        mvc.perform(get("/api/v1/relatorios-semestrais/{id}", id).cookie(session))
                .andExpect(jsonPath("$.versaoVigente").value(3))
                .andExpect(jsonPath("$.ultimaInteracao.meio").value("AUDIO"));

        mvc.perform(
                        authenticated(
                                        post("/api/v1/relatorios-semestrais/{id}/aprovacao", id),
                                        session)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"versaoConfirmada\":2}"))
                .andExpect(status().isConflict())
                .andExpect(followsContract())
                .andExpect(jsonPath("$.codigo").value("VERSAO_DESATUALIZADA"));
        mvc.perform(
                        authenticated(
                                        post("/api/v1/relatorios-semestrais/{id}/aprovacao", id),
                                        session)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"versaoConfirmada\":3}"))
                .andExpect(status().isOk())
                .andExpect(followsContract())
                .andExpect(jsonPath("$.versaoAprovada").value(3));
        mvc.perform(get("/api/v1/relatorios-semestrais/{id}/pdf-oficial", id).cookie(session))
                .andExpect(status().isOk())
                .andExpect(followsContract());
        mvc.perform(
                        authenticated(
                                        post(
                                                "/api/v1/alunos/{id}/relatorios-semestrais",
                                                matteo.id()),
                                        session)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"periodoChave\":\"2026-1\"}"))
                .andExpect(status().isConflict())
                .andExpect(followsContract())
                .andExpect(jsonPath("$.codigo").value("SEMESTRAL_JA_APROVADO"));
    }

    @Test
    void openLessonReportsAskConfirmationAndCancellation() throws Exception {
        lessonsInSemester1();
        jdbc.update(
                "update lesson_report set status = 'AGUARDANDO_CONFERENCIA', approved_at = null,"
                        + " approved_version = null, approval_channel = null"
                        + " where id = (select r.id from lesson_report r join lesson a on a.id = r.lesson_id"
                        + " where a.lesson_date = '2026-05-20')");
        scenario.approvedLesson(clara, matteo, LocalDate.of(2026, 4, 2), "Leitura");

        mvc.perform(
                        authenticated(
                                        post(
                                                "/api/v1/alunos/{id}/relatorios-semestrais",
                                                matteo.id()),
                                        session)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"periodoChave\":\"2026-1\"}"))
                .andExpect(status().isConflict())
                .andExpect(followsContract())
                .andExpect(jsonPath("$.codigo").value("HA_RELATORIOS_EM_ABERTO"))
                .andExpect(jsonPath("$.detalhes.quantidade").value(1));
        generate("{\"periodoChave\":\"2026-1\",\"confirmarMesmoComEmAberto\":true}");
        scenario.processQueue();
        UUID id = jdbc.queryForObject("select id from semester_report", UUID.class);
        mvc.perform(get("/api/v1/relatorios-semestrais/{id}", id).cookie(session))
                .andExpect(jsonPath("$.quantidadeRelatoriosEmAberto").value(1))
                .andExpect(jsonPath("$.informacoesGerais.quantidadeAulasConsideradas").value(2));

        mvc.perform(
                        authenticated(
                                post("/api/v1/relatorios-semestrais/{id}/cancelamento", id),
                                session))
                .andExpect(status().isNoContent())
                .andExpect(followsContract());
        mvc.perform(
                        authenticated(
                                post("/api/v1/relatorios-semestrais/{id}/cancelamento", id),
                                session))
                .andExpect(status().isConflict())
                .andExpect(followsContract());
    }
}
