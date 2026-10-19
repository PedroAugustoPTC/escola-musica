package br.com.felipeblaft.reports.transport.n8n;

import static br.com.felipeblaft.reports.support.N8nTestSupport.N8N_CONTAINER;
import static br.com.felipeblaft.reports.support.N8nTestSupport.N8N_URL;
import static br.com.felipeblaft.reports.support.N8nTestSupport.awaitHealthy;
import static br.com.felipeblaft.reports.support.N8nTestSupport.docker;
import static br.com.felipeblaft.reports.support.N8nTestSupport.execInN8n;
import static br.com.felipeblaft.reports.support.N8nTestSupport.post;
import static org.assertj.core.api.Assertions.assertThat;

import br.com.felipeblaft.reports.support.N8nBackendStub;
import br.com.felipeblaft.reports.support.N8nBotApiStub;
import br.com.felipeblaft.reports.support.N8nTestSupport.CommandResult;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Privacidade do n8n (feature 004, FR-031, SC-005, constituição IV): depois de tráfego com dados
 * sentinela (texto do professor, nome de aluno, texto de relatório e bytes de PDF), nada disso e
 * nenhum segredo aparece nos logs, na exportação dos workflows nem nos arquivos do volume do n8n;
 * nenhuma execução fica guardada e o binário do PDF some. O banco do n8n vive em memória (R-13,
 * F-1); a poda apaga as execuções em cerca de 2 min, que o teste aguarda.
 */
@Tag("n8n")
class N8nPrivacyIT {

    private static final String RUN = UUID.randomUUID().toString().substring(0, 8);
    private static final String TEACHER_TEXT = "SENTINEL-TEACHER-TEXT-" + RUN;
    private static final String STUDENT_NAME = "SENTINEL-STUDENT-NAME-" + RUN;
    private static final String REPORT_TEXT = "SENTINEL-REPORT-TEXT-" + RUN;
    private static final String PDF_MARK = "SENTINEL-PDF-BYTES-" + RUN;
    private static final String DOCUMENT_ID = "p".repeat(43);

    private static final List<String> SECRETS =
            List.of(
                    "123456:TEST",
                    N8nBackendStub.SERVICE_TOKEN,
                    N8nDeliveryWorkflowIT.DELIVERY_TOKEN,
                    "secret-webhook-test");

    /**
     * A chave de cifra do próprio n8n (N8N_ENCRYPTION_KEY): o n8n a grava no arquivo {@code config}
     * do volume por conta própria. Não protege nada de domínio (não há credencial no n8n) e fica
     * fora desta varredura; ver R-13.
     */
    private static final String ENCRYPTION_KEY = "test-encryption-key-0123456789abcdef";

    private static final String COUNT_EXECUTIONS =
            "const { DatabaseSync } = require('node:sqlite');"
                    + "const db = new DatabaseSync(process.env.DB_SQLITE_DATABASE);"
                    + "console.log(db.prepare('select count(*) c from execution_entity').get().c"
                    + " + ' ' + db.prepare('select count(*) c from execution_data').get().c);";

    @Test
    @DisplayName(
            "nada de aluno nem segredo em logs, exportação e volume; nenhuma execução nem PDF ficam guardados")
    void nothingSensitiveRemainsInTheOrchestrator() throws Exception {
        awaitHealthy(Duration.ofSeconds(180));
        try (N8nBackendStub backend = new N8nBackendStub();
                N8nBotApiStub botApi = new N8nBotApiStub()) {
            generateTraffic(backend, botApi);
            awaitPruning();

            assertNoSensitiveText("logs do contêiner", docker("logs", N8N_CONTAINER).output());
            assertThat(docker("logs", N8N_CONTAINER).output())
                    .as("logs do contêiner trazem a chave de cifra")
                    .doesNotContain(ENCRYPTION_KEY);
            assertNoSensitiveText(
                    "exportação dos workflows",
                    execInN8n(
                                    "sh",
                                    "-c",
                                    "rm -rf /tmp/exp && n8n export:workflow --all --output=/tmp/exp >/dev/null 2>&1; cat /tmp/exp/*")
                            .output());
            assertThat(
                            files(
                                    "/home/node/.n8n",
                                    List.of(TEACHER_TEXT, STUDENT_NAME, REPORT_TEXT, PDF_MARK)))
                    .as("arquivos do volume do n8n com dado de aluno")
                    .isEmpty();
            assertThat(files("/home/node/.n8n", SECRETS))
                    .as("arquivos do volume do n8n com segredo")
                    .isEmpty();
            assertThat(
                            execInN8n("sh", "-c", "find /home/node/.n8n/binaryData -type f | wc -l")
                                    .output()
                                    .trim())
                    .as("arquivos de binário do PDF")
                    .isEqualTo("0");
        }
    }

    // ---------------------------------------------------------------- apoio

    private static void generateTraffic(N8nBackendStub backend, N8nBotApiStub botApi) {
        backend.documentReturns(
                DOCUMENT_ID,
                ("%PDF-1.4\n" + PDF_MARK + "\n" + "x".repeat(500))
                        .getBytes(StandardCharsets.ISO_8859_1));
        backend.updateReturns(
                "{\"version\":\"v1\",\"updateId\":71,\"batchId\":\"u71\",\"actions\":["
                        + "{\"actionId\":\"u71-1\",\"type\":\"MESSAGE\",\"chatId\":7001,\"text\":\""
                        + STUDENT_NAME
                        + " "
                        + REPORT_TEXT
                        + "\"},"
                        + "{\"actionId\":\"u71-2\",\"type\":\"DOCUMENT\",\"chatId\":7001,\"documentId\":\""
                        + DOCUMENT_ID
                        + "\",\"fileName\":\"report.pdf\",\"contentType\":\"application/pdf\",\"caption\":\""
                        + STUDENT_NAME
                        + "\"},"
                        + "{\"actionId\":\"u71-3\",\"type\":\"MESSAGE\",\"chatId\":7001,\"text\":\"fails "
                        + REPORT_TEXT
                        + "\"}]}");
        botApi.failMethod("editMessageText", 403, "Forbidden: " + STUDENT_NAME);
        for (int i = 0; i < 3; i++) {
            post(
                    N8N_URL + "/webhook/telegram-update",
                    Map.of("X-Telegram-Bot-Api-Secret-Token", "secret-webhook-test"),
                    "application/json",
                    ("{\"update_id\":71,\"message\":{\"chat\":{\"id\":7001},\"text\":\""
                                    + TEACHER_TEXT
                                    + "\"}}")
                            .getBytes(StandardCharsets.UTF_8));
        }
        N8nDeliveryWorkflowIT.deliver(
                N8nDeliveryWorkflowIT.DELIVERY_TOKEN,
                "u72",
                N8nDeliveryWorkflowIT.batch("u72", STUDENT_NAME + " " + REPORT_TEXT));
    }

    /** Espera a poda do n8n (cerca de 2 min) apagar as execuções e os binários. */
    private static void awaitPruning() throws Exception {
        long deadline = System.nanoTime() + Duration.ofMinutes(4).toNanos();
        String counts = "";
        while (System.nanoTime() < deadline) {
            CommandResult result = execInN8n("node", "--no-warnings", "-e", COUNT_EXECUTIONS);
            counts = result.output().trim();
            if ("0 0".equals(counts)) {
                return;
            }
            Thread.sleep(5_000);
        }
        throw new AssertionError(
                "a poda não apagou as execuções a tempo (linhas de execução e de dados: "
                        + counts
                        + ")");
    }

    private static void assertNoSensitiveText(String where, String text) {
        for (String forbidden : List.of(TEACHER_TEXT, STUDENT_NAME, REPORT_TEXT, PDF_MARK)) {
            assertThat(text).as(where + " traz dado de aluno").doesNotContain(forbidden);
        }
        for (String secret : SECRETS) {
            assertThat(text).as(where + " traz um segredo").doesNotContain(secret);
        }
    }

    /**
     * Arquivos sob o diretório que contêm algum dos trechos (só os nomes voltam; nunca o conteúdo).
     */
    private static List<String> files(String directory, List<String> fragments) {
        StringBuilder pattern = new StringBuilder();
        for (String fragment : fragments) {
            pattern.append(" -e '").append(fragment).append("'");
        }
        CommandResult result =
                execInN8n(
                        "sh",
                        "-c",
                        "grep -rlF" + pattern + " " + directory + " 2>/dev/null || true");
        return result.output().lines().map(String::trim).filter(line -> !line.isEmpty()).toList();
    }
}
