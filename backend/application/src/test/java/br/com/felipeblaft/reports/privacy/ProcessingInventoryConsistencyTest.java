package br.com.felipeblaft.reports.privacy;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.felipeblaft.reports.architecture.GlossaryFile;
import br.com.felipeblaft.reports.domain.privacy.RetentionPolicy;
import br.com.felipeblaft.reports.port.SemesterConsolidationInput;
import br.com.felipeblaft.reports.port.SemesterRevisionInput;
import br.com.felipeblaft.reports.port.TaskInput;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * FR-070, FR-071: o inventário de tratamento ({@code privacy/processing-inventory.md}) precisa
 * acompanhar o código. Os pontos checáveis por máquina ficam em marcadores de comentário em inglês;
 * o restante do documento é texto em português para as pessoas.
 */
class ProcessingInventoryConsistencyTest {

    private static final Path INVENTORY =
            GlossaryFile.findRoot().resolve("privacy/processing-inventory.md");

    private static final List<String> PORT_SECTIONS =
            List.of(
                    "TranscriptionPort",
                    "LlmPort",
                    "ChannelOrchestratorPort",
                    "DocumentStoragePort");

    private static final List<String> NEVER_SENT = List.of("name", "cpf", "birth-date", "guardian");

    private static String inventory() throws IOException {
        assertThat(INVENTORY).as("privacy/processing-inventory.md precisa existir").exists();
        return Files.readString(INVENTORY);
    }

    private static String marker(String text, String key) {
        Matcher matcher =
                Pattern.compile("<!--\\s*" + Pattern.quote(key) + ":\\s*(.*?)\\s*-->")
                        .matcher(text);
        assertThat(matcher.find()).as("marcador %s no inventário", key).isTrue();
        return matcher.group(1);
    }

    @Test
    @DisplayName("há uma seção para cada porta de provedor configurada")
    void hasOneSectionPerProviderPort() throws IOException {
        String text = inventory();

        List<String> missing = new ArrayList<>();
        for (String port : PORT_SECTIONS) {
            if (!Pattern.compile("(?m)^## .*\\b" + port + "\\b").matcher(text).find()) {
                missing.add(port);
            }
        }

        assertThat(missing).as("portas sem seção").isEmpty();
    }

    @Test
    @DisplayName("os prazos padrão do inventário são os de RetentionPolicy")
    void defaultRetentionMatchesPolicy() throws IOException {
        String declared = marker(inventory(), "retention-days");

        String expected =
                "transcription=%d unapproved-pdf=%d cancelled-report=%d audit-trail=%d"
                        .formatted(
                                RetentionPolicy.DEFAULT_TRANSCRIPTION_DAYS,
                                RetentionPolicy.DEFAULT_UNAPPROVED_PDF_DAYS,
                                RetentionPolicy.DEFAULT_CANCELLED_REPORT_DAYS,
                                RetentionPolicy.DEFAULT_AUDIT_TRAIL_DAYS);
        assertThat(declared).isEqualTo(expected);
    }

    @Test
    @DisplayName("declara que nome, CPF, nascimento e responsável não vão à IA")
    void declaresPersonalDataNeverSent() throws IOException {
        String declared = marker(inventory(), "never-sent");

        assertThat(List.of(declared.split("\\s+"))).containsExactlyInAnyOrderElementsOf(NEVER_SENT);
    }

    @Test
    @DisplayName("as entradas enviadas ao LLM listadas são exatamente as que a porta aceita")
    void llmInputsMatchThePort() throws IOException {
        List<String> declared = List.of(marker(inventory(), "llm-inputs").split("\\s+"));

        TreeSet<String> actual = new TreeSet<>();
        for (Class<?> permitted : TaskInput.class.getPermittedSubclasses()) {
            if (permitted.isInterface()) {
                continue;
            }
            actual.add(permitted.getSimpleName());
        }
        actual.add(SemesterConsolidationInput.class.getSimpleName());
        actual.add(SemesterRevisionInput.class.getSimpleName());

        assertThat(declared).containsExactlyInAnyOrderElementsOf(actual);
    }
}
