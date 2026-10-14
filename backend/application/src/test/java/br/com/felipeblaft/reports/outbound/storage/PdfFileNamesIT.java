package br.com.felipeblaft.reports.outbound.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.felipeblaft.reports.domain.registry.Student;
import br.com.felipeblaft.reports.domain.registry.Teacher;
import br.com.felipeblaft.reports.port.DocumentStoragePort;
import br.com.felipeblaft.reports.support.AudioNormalizationTest;
import br.com.felipeblaft.reports.support.ContractApiBase;
import br.com.felipeblaft.reports.support.ScenarioIT;
import jakarta.servlet.http.Cookie;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;

/**
 * Feature 003, FR-026: o nome do PDF MOSTRADO ao usuário não muda (exceção permanente {@code
 * download-file-name}: {@code relatorio-aula-<data>[-v<n>].pdf}), enquanto a chave INTERNA de
 * armazenamento de um documento novo passa a usar nomes em inglês ({@code official.pdf}, {@code
 * v<n>.pdf}). Um documento gravado antes da mudança, com a chave antiga ({@code oficial.pdf}),
 * continua abrindo: a chave é um dado opaco lido do próprio registro.
 */
class PdfFileNamesIT extends ContractApiBase {

    @Autowired private DocumentStoragePort storage;

    private Cookie session;
    private UUID reportId;

    @BeforeEach
    void reachReview() throws Exception {
        Teacher clara = scenario.clara();
        Student matteo = scenario.student(clara, "Matteo Ferreira", "Piano");
        session = signIn(ScenarioIT.CODE_CLARA);
        mvc.perform(
                        authenticated(
                                multipart("/api/v1/alunos/{id}/relatorios-aula", matteo.id())
                                        .file(
                                                new MockMultipartFile(
                                                        "audio",
                                                        "aula.m4a",
                                                        "audio/mp4",
                                                        AudioNormalizationTest.AUDIO_WITH_SPEECH)),
                                session))
                .andExpect(status().isAccepted());
        scenario.processQueue();
        reportId = jdbc.queryForObject("select id from lesson_report", UUID.class);
        mvc.perform(
                        authenticated(
                                        post("/api/v1/relatorios-aula/{id}/respostas", reportId),
                                        session)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"texto\":\"Estudou bem.\"}"))
                .andExpect(status().isAccepted());
        scenario.processQueue();
        mvc.perform(
                        authenticated(
                                        post("/api/v1/relatorios-aula/{id}/aprovacao", reportId),
                                        session)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"versaoConfirmada\":1}"))
                .andExpect(status().isOk());
    }

    @Test
    void displayedNamesStayInPortugueseAndNewStorageKeysAreInEnglish() throws Exception {
        mvc.perform(get("/api/v1/relatorios-aula/{id}/versoes/1/pdf", reportId).cookie(session))
                .andExpect(status().isOk())
                .andExpect(
                        header().string(
                                        "Content-Disposition",
                                        "inline; filename=\"relatorio-aula-2026-09-21-v1.pdf\""));
        mvc.perform(get("/api/v1/relatorios-aula/{id}/pdf-oficial", reportId).cookie(session))
                .andExpect(status().isOk())
                .andExpect(
                        header().string(
                                        "Content-Disposition",
                                        "inline; filename=\"relatorio-aula-2026-09-21.pdf\""));

        List<String> keys = jdbc.queryForList("select storage_key from pdf_document", String.class);
        assertThat(keys).isNotEmpty();
        assertThat(keys).allSatisfy(key -> assertThat(key).matches(".*/(official|v[0-9]+)\\.pdf"));
        assertThat(keys).anySatisfy(key -> assertThat(key).endsWith("/official.pdf"));
        assertThat(keys).noneSatisfy(key -> assertThat(key).endsWith("oficial.pdf"));
    }

    @Test
    void documentStoredBeforeTheRenameKeepsOpeningWithItsOldKey() throws Exception {
        String newKey =
                jdbc.queryForObject(
                        "select storage_key from pdf_document where storage_key like '%/official.pdf'",
                        String.class);
        byte[] bytes = storage.open(newKey).orElseThrow();
        String oldKey = newKey.replace("official.pdf", "oficial.pdf");
        storage.save(oldKey, bytes);
        jdbc.update(
                "update pdf_document set storage_key = ? where storage_key = ?", oldKey, newKey);

        mvc.perform(get("/api/v1/relatorios-aula/{id}/pdf-oficial", reportId).cookie(session))
                .andExpect(status().isOk())
                .andExpect(
                        header().string(
                                        "Content-Disposition",
                                        "inline; filename=\"relatorio-aula-2026-09-21.pdf\""));
    }
}
