package br.com.felipeblaft.reports.inbound.telegram.channel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.felipeblaft.reports.domain.channel.ChannelActionType;
import br.com.felipeblaft.reports.domain.channel.ChannelDocument;
import br.com.felipeblaft.reports.domain.channel.RetryPolicy;
import br.com.felipeblaft.reports.domain.common.ReportType;
import br.com.felipeblaft.reports.inbound.telegram.DocumentReference;
import br.com.felipeblaft.reports.inbound.telegram.TelegramOutput;
import br.com.felipeblaft.reports.inbound.telegram.TelegramOutput.Button;
import br.com.felipeblaft.reports.inbound.telegram.TelegramOutput.Keyboard;
import br.com.felipeblaft.reports.inbound.telegram.channel.ChannelActionMapper.MappedAction;
import br.com.felipeblaft.reports.port.ChannelDocumentRepository;
import br.com.felipeblaft.reports.support.ActionsSchemaV1;
import br.com.felipeblaft.reports.support.AdjustableClock;
import br.com.felipeblaft.reports.usecase.channel.EmitChannelDocumentUseCase;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class ChannelActionMapperTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final Map<String, ChannelDocument> documents = new HashMap<>();
    private final ChannelDocumentRepository repository =
            new ChannelDocumentRepository() {
                @Override
                public void emit(ChannelDocument document) {
                    documents.put(document.tokenHash(), document);
                }

                @Override
                public Optional<ChannelDocument> searchByHash(String tokenHash) {
                    return Optional.ofNullable(documents.get(tokenHash));
                }

                @Override
                public int purgeExpired(Instant now) {
                    return 0;
                }
            };
    private final ChannelActionMapper mapper =
            new ChannelActionMapper(
                    new EmitChannelDocumentUseCase(
                            repository,
                            new AdjustableClock(),
                            new RetryPolicy(
                                    Duration.ofSeconds(30),
                                    Duration.ofMinutes(15),
                                    Duration.ofHours(24))));

    private List<MappedAction> map(TelegramOutput... outputs) {
        List<MappedAction> actions = mapper.map(List.of(outputs), 4242L, pos -> "u90211-" + pos);
        actions.forEach(a -> ActionsSchemaV1.require("Acao", a.replyNode()));
        return actions;
    }

    // ---- mensagens

    @Test
    void simpleMessageWithoutKeyboardDoesNotEmitField() {
        MappedAction action =
                map(new TelegramOutput.Message(7L, "Olá <b>Clara</b>", Keyboard.NONE)).get(0);
        assertThat(action.actionId()).isEqualTo("u90211-0");
        assertThat(action.type()).isEqualTo(ChannelActionType.MENSAGEM);
        assertThat(action.chatId()).isEqualTo(7L);
        assertThat(action.replyNode().toString())
                .isEqualTo(
                        "{\"acaoId\":\"u90211-0\",\"tipo\":\"MENSAGEM\",\"chatId\":7,"
                                + "\"texto\":\"Olá <b>Clara</b>\"}");
        // o payload guardado não leva o acaoId (ele é do envelope e é o id da linha)
        assertThat(action.payload()).doesNotContain("acaoId").contains("\"texto\"");
        assertThat(action.compensation()).isNull();
    }

    @Test
    void keyboardGoesWithLabelAndDataAndStaysInRows() {
        MappedAction action =
                map(new TelegramOutput.Message(
                                7L,
                                "Escolha",
                                new Keyboard(
                                        List.of(
                                                List.of(new Button("Aprovar", "v1:aprov:2")),
                                                List.of(
                                                        new Button("PDF", "v1:pdf:"),
                                                        new Button("Cancelar", "v1:canc:"))))))
                        .get(0);
        JsonNode rows = action.replyNode().path("teclado").path("linhas");
        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).get(0).path("rotulo").asString()).isEqualTo("Aprovar");
        assertThat(rows.get(0).get(0).path("dados").asString()).isEqualTo("v1:aprov:2");
        assertThat(rows.get(1)).hasSize(2);
    }

    @Test
    void longTextIsSplitWithKeyboardOnlyOnLastAndIdsByPositionAfterExpansion() {
        String line = "x".repeat(99) + "\n";
        String longText = line.repeat(100); // 10.000 caracteres
        List<MappedAction> actions =
                map(
                        new TelegramOutput.CallbackReply("cb1"),
                        new TelegramOutput.Message(
                                7L, longText, Keyboard.of(new Button("OK", "v1:ok:"))),
                        new TelegramOutput.Message(7L, "fim", Keyboard.NONE));

        assertThat(actions).hasSizeGreaterThanOrEqualTo(5);
        assertThat(actions).extracting(MappedAction::actionId).first().isEqualTo("u90211-0");
        for (int i = 0; i < actions.size(); i++) {
            assertThat(actions.get(i).actionId()).isEqualTo("u90211-" + i);
        }
        List<MappedAction> parts = actions.subList(1, actions.size() - 1);
        for (int i = 0; i < parts.size(); i++) {
            JsonNode no = parts.get(i).replyNode();
            assertThat(no.path("texto").asString().length()).isLessThanOrEqualTo(4096);
            assertThat(no.has("teclado")).isEqualTo(i == parts.size() - 1);
        }
        assertThat(
                        parts.stream()
                                .map(p -> p.replyNode().path("texto").asString())
                                .reduce("", (a, b) -> a + b)
                                .replace("\n", ""))
                .isEqualTo(longText.replace("\n", ""));
        assertThat(actions.getLast().replyNode().path("texto").asString()).isEqualTo("fim");
    }

    // ---- edição e compensação

    @Test
    void editWithEmptyKeyboardIsExplicitAndGeneratesCompensationWithSameContentWithoutActionId() {
        MappedAction action =
                map(new TelegramOutput.Edit(7L, 900L, "<i>Pronto</i>", Keyboard.NONE)).get(0);
        assertThat(action.type()).isEqualTo(ChannelActionType.EDICAO);
        assertThat(action.replyNode().path("mensagemId").asLong()).isEqualTo(900L);
        assertThat(action.replyNode().path("teclado").path("linhas")).isEmpty();
        assertThat(action.replyNode().path("teclado").isObject()).isTrue();

        JsonNode compensation = JSON.readTree(action.compensation());
        assertThat(compensation.path("tipo").asString()).isEqualTo("MENSAGEM");
        assertThat(compensation.path("texto").asString()).isEqualTo("<i>Pronto</i>");
        assertThat(compensation.path("chatId").asLong()).isEqualTo(7L);
        assertThat(compensation.has("acaoId")).isFalse();
        assertThat(compensation.has("teclado")).isFalse();
    }

    @Test
    void editCompensationWithKeyboardCarriesKeyboard() {
        MappedAction action =
                map(new TelegramOutput.Edit(7L, 900L, "t", Keyboard.of(new Button("A", "v1:a:"))))
                        .get(0);
        assertThat(JSON.readTree(action.compensation()).path("teclado").path("linhas")).hasSize(1);
    }

    // ---- documento

    @Test
    void documentBecomesOpaqueIdWithoutBytesPathNorUrl() {
        UUID teacher = UUID.randomUUID();
        UUID report = UUID.randomUUID();
        byte[] pdf = "%PDF-conteudo-do-relatorio".getBytes();
        MappedAction action =
                map(new TelegramOutput.Document(
                                7L,
                                pdf,
                                "relatorio-aula-2026-03-10-v2.pdf",
                                "Segue o relatório",
                                DocumentReference.review(teacher, ReportType.AULA, report, 2)))
                        .get(0);
        JsonNode no = action.replyNode();
        assertThat(action.type()).isEqualTo(ChannelActionType.DOCUMENTO);
        assertThat(no.path("documentoId").asString()).matches("^[A-Za-z0-9_-]{43}$");
        assertThat(no.path("nomeArquivo").asString()).isEqualTo("relatorio-aula-2026-03-10-v2.pdf");
        assertThat(no.path("tipoConteudo").asString()).isEqualTo("application/pdf");
        assertThat(no.path("legenda").asString()).isEqualTo("Segue o relatório");
        assertThat(no.propertyNames())
                .containsExactlyInAnyOrder(
                        "acaoId",
                        "tipo",
                        "chatId",
                        "documentoId",
                        "nomeArquivo",
                        "tipoConteudo",
                        "legenda");
        assertThat(no.toString()).doesNotContain("PDF-conteudo");
        // só o hash do identificador foi guardado, com a referência e o dono
        ChannelDocument stored =
                documents.get(ChannelDocument.hashDoToken(no.path("documentoId").asString()));
        assertThat(stored.teacherId()).isEqualTo(teacher);
        assertThat(stored.reportId()).isEqualTo(report);
        assertThat(stored.version()).isEqualTo(2);
        assertThat(stored.fileName()).isEqualTo("relatorio-aula-2026-03-10-v2.pdf");
        assertThat(action.compensation()).isNull();
    }

    @Test
    void documentKeepsCompensationWithNoticeAndButtonWithoutDocumentNorReportText()
            throws Exception {
        MappedAction action =
                map(new TelegramOutput.Document(
                                7L,
                                "%PDF-conteudo-do-relatorio".getBytes(),
                                "relatorio-aula.pdf",
                                "Segue o relatório do Matteo",
                                DocumentReference.review(
                                        UUID.randomUUID(), ReportType.AULA, UUID.randomUUID(), 2),
                                new TelegramOutput.Message(
                                        7L,
                                        "Não consegui enviar o PDF agora.",
                                        Keyboard.of(new Button("Ver PDF", "v1:pdf:")))))
                        .get(0);

        JsonNode compensation = JSON.readTree(action.compensation());
        assertThat(compensation.path("tipo").asString()).isEqualTo("MENSAGEM");
        assertThat(compensation.path("chatId").asLong()).isEqualTo(7L);
        assertThat(compensation.path("texto").asString())
                .isEqualTo("Não consegui enviar o PDF agora.");
        assertThat(
                        compensation
                                .path("teclado")
                                .path("linhas")
                                .get(0)
                                .get(0)
                                .path("dados")
                                .asString())
                .isEqualTo("v1:pdf:");
        assertThat(
                        compensation
                                .path("teclado")
                                .path("linhas")
                                .get(0)
                                .get(0)
                                .path("rotulo")
                                .asString())
                .isEqualTo("Ver PDF");
        assertThat(compensation.has("acaoId")).isFalse();
        assertThat(action.compensation())
                .doesNotContain("documentoId", "nomeArquivo", "legenda", "Matteo", "PDF-conteudo");
        // a resposta ao orquestrador continua sendo só o documento
        assertThat(action.replyNode().has("compensacao")).isFalse();
        assertThat(action.replyNode().path("tipo").asString()).isEqualTo("DOCUMENTO");
    }

    @Test
    void officialDocumentCarriesNoVersionAndHasNoCaptionWhenNull() {
        MappedAction action =
                map(new TelegramOutput.Document(
                                7L,
                                new byte[] {1},
                                "oficial.pdf",
                                null,
                                DocumentReference.official(
                                        UUID.randomUUID(),
                                        ReportType.SEMESTRAL,
                                        UUID.randomUUID())))
                        .get(0);
        assertThat(action.replyNode().has("legenda")).isFalse();
        assertThat(documents.values())
                .singleElement()
                .satisfies(d -> assertThat(d.official()).isTrue());
    }

    @Test
    void documentWithoutReferenceIsProgrammingError() {
        assertThatThrownBy(
                        () ->
                                mapper.map(
                                        List.of(
                                                new TelegramOutput.Document(
                                                        7L, new byte[] {1}, "x.pdf", null, null)),
                                        7L,
                                        pos -> "u1-" + pos))
                .isInstanceOf(IllegalStateException.class);
    }

    // ---- callback e etapa

    @Test
    void callbackReplyUsesUpdateChatAndGoesOutFirstInReceivedOrder() {
        List<MappedAction> actions =
                map(
                        new TelegramOutput.CallbackReply("cb-77"),
                        new TelegramOutput.Message(7L, "Confirma?", Keyboard.NONE));
        assertThat(actions.get(0).type()).isEqualTo(ChannelActionType.RESPOSTA_CALLBACK);
        assertThat(actions.get(0).chatId()).isEqualTo(4242L);
        assertThat(actions.get(0).replyNode().path("callbackId").asString()).isEqualTo("cb-77");
        assertThat(actions.get(0).replyNode().has("chatId")).isFalse();
        assertThat(actions.get(1).actionId()).isEqualTo("u90211-1");
    }

    @Test
    void stepCarriesJobAndMessageIdWhenInterpreterKnowsItAndCompensationDoesNot() {
        UUID job = UUID.randomUUID();
        MappedAction editing =
                map(new TelegramOutput.StepMessage(7L, 555L, "<i>Transcrevendo…</i>", job)).get(0);
        assertThat(editing.type()).isEqualTo(ChannelActionType.MENSAGEM_ETAPA);
        assertThat(editing.jobId()).isEqualTo(job);
        assertThat(editing.replyNode().path("trabalhoId").asString()).isEqualTo(job.toString());
        assertThat(editing.replyNode().path("editarMensagemId").asLong()).isEqualTo(555L);
        assertThat(editing.payload()).contains("\"editarMensagemId\":555");
        // a compensação (mensagem nova) é a mesma ação sem editarMensagemId e sem acaoId
        JsonNode compensation = JSON.readTree(editing.compensation());
        assertThat(compensation.has("editarMensagemId")).isFalse();
        assertThat(compensation.has("acaoId")).isFalse();
        assertThat(compensation.path("texto").asString()).isEqualTo("<i>Transcrevendo…</i>");
        assertThat(compensation.path("trabalhoId").asString()).isEqualTo(job.toString());

        MappedAction created =
                map(new TelegramOutput.StepMessage(7L, null, "<i>Enviando…</i>", job)).get(0);
        assertThat(created.replyNode().has("editarMensagemId")).isFalse();
        assertThat(JSON.readTree(created.compensation()))
                .isEqualTo(JSON.readTree(created.payload()));
    }

    // ---- reconstituição para reentrega

    @Test
    void withActionIdRebuildsFullActionWithIdFirst() {
        MappedAction action = map(new TelegramOutput.Message(7L, "oi", Keyboard.NONE)).get(0);
        assertThat(ChannelActionMapper.withActionId("u90211-0", action.payload()).toString())
                .isEqualTo(action.replyNode().toString());
        assertThat(ChannelActionMapper.withActionId("u90211-0", "{\"a\":1}").toString())
                .isEqualTo("{\"acaoId\":\"u90211-0\",\"a\":1}");
    }

    @Test
    void absenceOfAudioInGeneratedNodes() {
        List<MappedAction> actions =
                map(
                        new TelegramOutput.Message(
                                7L, "Recebi o áudio de Matteo. É dele mesmo?", Keyboard.NONE),
                        new TelegramOutput.StepMessage(7L, null, "etapa", UUID.randomUUID()));
        for (MappedAction action : actions) {
            assertThat(action.replyNode().toString())
                    .doesNotContain("file_id", "file_path", ".oga", ".ogg", "bytes", "audio");
        }
    }
}
