package br.com.felipeblaft.reports.privacy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.felipeblaft.reports.domain.privacy.ConsentTermVersion;
import br.com.felipeblaft.reports.domain.registry.Student;
import br.com.felipeblaft.reports.domain.registry.Teacher;
import br.com.felipeblaft.reports.port.ConsentTermRepository;
import br.com.felipeblaft.reports.port.LlmPort;
import br.com.felipeblaft.reports.port.TranscriptionPort;
import br.com.felipeblaft.reports.support.AudioNormalizationTest;
import br.com.felipeblaft.reports.support.CanaryStudent;
import br.com.felipeblaft.reports.support.TelegramConversationBase;
import br.com.felipeblaft.reports.support.LeakAudit;
import br.com.felipeblaft.reports.support.LogCapture;
import br.com.felipeblaft.reports.support.PersonalDataSweep;
import br.com.felipeblaft.reports.support.ScenarioIT;
import jakarta.servlet.http.Cookie;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MvcResult;

/**
 * SC-005: nenhuma chamada a provedor de IA (transcrição ou LLM) para aluno menor sem consentimento
 * válido, e zero linhas em {@code ai_call}. Percorre as entradas de IA da Web com os provedores
 * espionados; a varredura de dado pessoal acompanha o percurso. O aluno com consentimento válido
 * segue passando pela barreira (o teste do outro lado).
 */
class ConsentNoAiCallIT extends TelegramConversationBase {

    @Autowired ConsentTermRepository terms;

    @MockitoSpyBean LlmPort llm;
    @MockitoSpyBean TranscriptionPort transcription;

    private Cookie session;
    private Student minor;

    @BeforeEach
    void prepare() throws Exception {
        Teacher clara = scenario.clara();
        minor = scenario.studentWithoutConsent(clara, CanaryStudent.NAME, "Piano");
        terms.save(
                new ConsentTermVersion(
                        UUID.randomUUID(),
                        1,
                        LocalDate.of(2026, 1, 1),
                        "Institutional text",
                        false,
                        UUID.randomUUID(),
                        clock.now()));
        MvcResult input =
                mvc.perform(
                                post("/api/v1/session")
                                        .with(csrf().asHeader())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("{\"code\":\"" + ScenarioIT.CODE_CLARA + "\"}"))
                        .andReturn();
        Cookie cookie = input.getResponse().getCookie("session");
        session = new Cookie(cookie.getName(), cookie.getValue());
    }

    @Test
    void minorWithoutConsentNeverReachesAnyProviderNorLeavesAnAiCallRow() throws Exception {
        PersonalDataSweep sweep = PersonalDataSweep.of(jdbc, CanaryStudent.sweepValuesWithCpf());
        LeakAudit audit = sweep.newAudit();

        try (LogCapture logs = LogCapture.start()) {
            MockMultipartFile audio =
                    new MockMultipartFile(
                            "audio",
                            "lesson.webm",
                            "audio/webm",
                            AudioNormalizationTest.AUDIO_WITH_SPEECH);
            mvc.perform(
                            multipart("/api/v1/students/{id}/lesson-reports", minor.id())
                                    .file(audio)
                                    .cookie(session)
                                    .with(csrf().asHeader()))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("CONSENT_REQUIRED"));

            mvc.perform(
                            post("/api/v1/students/{id}/semester-reports", minor.id())
                                    .cookie(session)
                                    .with(csrf().asHeader())
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content("{\"periodKey\":\"2026-1\"}"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("CONSENT_REQUIRED"));

            sweep.sweep(audit, "entradas de IA sem consentimento", logs);
        }

        verifyNoInteractions(llm, transcription);
        assertThat(jdbc.queryForObject("select count(*) from ai_call", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from processing_job", Integer.class))
                .isZero();
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from authorship_record"
                                        + " where operation = 'CONSENT_BLOCKED_AI'"
                                        + " and result = 'BLOCKED' and error_code = 'CONSENT_REQUIRED'",
                                Integer.class))
                .isEqualTo(2);
        audit.assertClean();
    }
}
