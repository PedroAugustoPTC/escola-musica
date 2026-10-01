package br.com.felipeblaft.relatorios.entrada.telegram;

import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static org.assertj.core.api.Assertions.assertThat;

import br.com.felipeblaft.relatorios.dominio.cadastro.Aluno;
import br.com.felipeblaft.relatorios.dominio.cadastro.Professor;
import br.com.felipeblaft.relatorios.suporte.CenarioIT;
import br.com.felipeblaft.relatorios.suporte.NormalizacaoAudioTeste;
import br.com.felipeblaft.relatorios.suporte.TelegramConversaBase;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/** Registro de aula pelo Telegram (sequência 5.2, passos 1 a 5; estados #7 e #10). */
class RegistroAulaConversaIT extends TelegramConversaBase {

    private static final long LIMITE = 50L * 1024 * 1024;

    private Aluno matteo;

    @BeforeEach
    void preparar() throws Exception {
        Professor clara = cenario.clara();
        matteo = cenario.aluno(clara, "Matteo Ferreira", "Piano");
        cenario.aluno(clara, "Helena Prado", "Violino");
        vincular(USUARIO_CLARA, CenarioIT.CODIGO_CLARA);
    }

    private void escolherMatteo() throws Exception {
        enviarTexto(USUARIO_CLARA, "Matteo");
        API_BOTS.resetRequests();
    }

    private int trabalhos() {
        return jdbc.queryForObject("select count(*) from trabalho_processamento", Integer.class);
    }

    private Map<String, Object> conversa() {
        return jdbc.queryForMap("select * from vinculo_telegram");
    }

    @Test
    void registrarAulaPedeOAlunoEDepoisOAudio() throws Exception {
        enviarCallback(USUARIO_CLARA, "v1:reg:");
        assertThat(ultimaMensagem().get("text").asString()).isEqualTo("Para qual aluno?");
        assertThat(botoes(ultimaMensagem())).contains("v1:aluno:0", "v1:aluno:1");

        enviarCallback(USUARIO_CLARA, "v1:aluno:1");

        assertThat(ultimaMensagem().get("text").asString())
                .isEqualTo(
                        "Aula de hoje, 21/09/2026, com Matteo Ferreira. Envie o áudio da aula (mensagem de voz ou arquivo, até 50 MB).");
        assertThat(conversa().get("aluno_selecionado_id")).isEqualTo(matteo.id());
    }

    @Test
    void audioComAlunoSelecionadoPedeConfirmacaoSemBaixarNada() throws Exception {
        escolherMatteo();

        enviarVoz(USUARIO_CLARA, "voz-1", 120_000);

        JsonNode mensagem = ultimaMensagem();
        assertThat(mensagem.get("text").asString())
                .isEqualTo("Áudio recebido. Este áudio é da aula de Matteo Ferreira?");
        assertThat(botoes(mensagem)).containsExactly("v1:confal:", "v1:trocal:");
        assertThat(rotulosBotoes(mensagem))
                .containsExactly("Confirmar Matteo Ferreira", "Trocar aluno");
        assertThat(conversa().get("audio_pendente_file_id")).isEqualTo("voz-1");
        assertThat(trabalhos()).isZero();
        API_BOTS.verify(0, postRequestedFor(urlPathMatching(".*/getFile")));
    }

    @Test
    void aceitaAudioVideoNotaDeVideoEDocumentoDeAudio() throws Exception {
        escolherMatteo();
        List<String[]> tipos =
                List.of(
                        new String[] {"audio", "audio/mpeg"},
                        new String[] {"video", "video/mp4"},
                        new String[] {"video_note", "video/mp4"},
                        new String[] {"document", "audio/x-m4a"});
        for (String[] tipo : tipos) {
            API_BOTS.resetRequests();
            enviarArquivo(USUARIO_CLARA, tipo[0], "arq-" + tipo[0], tipo[1], 1000);
            assertThat(ultimaMensagem().get("text").asString()).startsWith("Áudio recebido.");
            assertThat(conversa().get("audio_pendente_file_id")).isEqualTo("arq-" + tipo[0]);
        }

        API_BOTS.resetRequests();
        enviarArquivo(USUARIO_CLARA, "document", "planilha", "application/pdf", 1000);
        assertThat(ultimaMensagem().get("text").asString())
                .startsWith("Não consigo usar este tipo de mensagem aqui.");
    }

    @Test
    void audioAcimaDoLimiteERecusadoSemBaixar() throws Exception {
        escolherMatteo();

        enviarVoz(USUARIO_CLARA, "grande", LIMITE + 1);

        assertThat(ultimaMensagem().get("text").asString())
                .isEqualTo(
                        "O áudio enviado é maior que o limite permitido. Grave um trecho mais curto.");
        assertThat(conversa().get("audio_pendente_file_id")).isNull();
        API_BOTS.verify(0, postRequestedFor(urlPathMatching(".*/getFile")));
    }

    @Test
    void novoAudioSubstituiOAnteriorEExpiraEm30Minutos() throws Exception {
        escolherMatteo();
        enviarVoz(USUARIO_CLARA, "primeiro", 1000);
        enviarVoz(USUARIO_CLARA, "segundo", 1000);
        assertThat(conversa().get("audio_pendente_file_id")).isEqualTo("segundo");

        relogio.avancar(Duration.ofMinutes(31));
        API_BOTS.resetRequests();
        enviarTexto(USUARIO_CLARA, "Helena");

        assertThat(textosEnviados().getFirst())
                .isEqualTo(
                        "O áudio enviado não foi confirmado a tempo e foi descartado. Envie o áudio de novo.");
        assertThat(conversa().get("audio_pendente_file_id")).isNull();
    }

    @Test
    void semAlunoSelecionadoOAudioPedeOAlunoEAEscolhaConfirma() throws Exception {
        prepararArquivoTelegram("sem-aluno", NormalizacaoAudioTeste.AUDIO_COM_FALA);

        enviarVoz(USUARIO_CLARA, "sem-aluno", 1000);
        assertThat(ultimaMensagem().get("text").asString())
                .isEqualTo("Áudio recebido. De qual aluno é esta aula?");

        enviarCallback(USUARIO_CLARA, "v1:aluno:1");

        assertThat(trabalhos()).isEqualTo(1);
        assertThat(
                        jdbc.queryForObject(
                                "select aluno_id from trabalho_processamento",
                                java.util.UUID.class))
                .isEqualTo(matteo.id());
        assertThat(conversa().get("audio_pendente_file_id")).isNull();
    }

    @Test
    void trocarAlunoMostraALista() throws Exception {
        escolherMatteo();
        enviarVoz(USUARIO_CLARA, "voz-troca", 1000);

        enviarCallback(USUARIO_CLARA, "v1:trocal:");

        assertThat(ultimaMensagem().get("text").asString())
                .isEqualTo("Áudio recebido. De qual aluno é esta aula?");
        assertThat(botoes(ultimaMensagem())).contains("v1:aluno:0", "v1:aluno:1");
    }

    @Test
    void confirmarProcessaEditandoAMesmaMensagemAteAsPerguntas() throws Exception {
        Path arquivo = prepararArquivoTelegram("voz-ok", NormalizacaoAudioTeste.AUDIO_COM_FALA);
        escolherMatteo();
        enviarVoz(USUARIO_CLARA, "voz-ok", 1000);
        API_BOTS.resetRequests();

        enviarCallback(USUARIO_CLARA, "v1:confal:");

        // a mensagem dos botões vira a mensagem de etapa, editada no lugar
        JsonNode edicao = chamadas("editMessageText").getFirst();
        assertThat(edicao.get("message_id").asLong()).isEqualTo(55);
        assertThat(edicao.get("text").asString()).isEqualTo("<i>Enviando o áudio…</i>");
        assertThat(
                        jdbc.queryForObject(
                                "select parametros->>'mensagemEtapa' from trabalho_processamento",
                                String.class))
                .isEqualTo("55");

        assertThat(cenario.processarFila()).isEqualTo(1);

        assertThat(textosEditados())
                .contains("<i>Transcrevendo…</i>", "<i>Estruturando o relatório…</i>");
        assertThat(chamadas("editMessageText"))
                .allSatisfy(e -> assertThat(e.get("message_id").asLong()).isEqualTo(55));
        String perguntas = ultimaMensagem().get("text").asString();
        assertThat(perguntas)
                .startsWith(
                        "Faltam algumas informações. Responda numa única mensagem, digitando ou gravando:")
                .contains("\n1. O aluno estudou em casa durante a semana?")
                .contains("\n2. Que repertório o aluno deve preparar para a próxima aula?")
                .contains("Texto ou áudio enviado aqui será aplicado a este relatório.");
        assertThat(botoes(ultimaMensagem())).containsExactly("v1:canc:", "v1:reg:");
        Map<String, Object> estado = conversa();
        assertThat(estado.get("relatorio_foco_tipo")).isEqualTo("AULA");
        assertThat(estado.get("relatorio_foco_id"))
                .isEqualTo(
                        jdbc.queryForObject("select id from relatorio_aula", java.util.UUID.class));
        // FR-018: o áudio não fica nem no volume do Telegram nem no diretório temporário
        assertThat(arquivo).doesNotExist();
        assertThat(arquivosTemporarios()).isEmpty();
    }

    @Test
    void audioSemFalaNaoCriaRelatorio() throws Exception {
        prepararArquivoTelegram(
                "silencio", NormalizacaoAudioTeste.SILENCIO.getBytes(StandardCharsets.UTF_8));
        escolherMatteo();
        enviarVoz(USUARIO_CLARA, "silencio", 1000);
        enviarCallback(USUARIO_CLARA, "v1:confal:");

        cenario.processarFila();

        assertThat(ultimaMensagem().get("text").asString())
                .startsWith("Não conseguimos entender este áudio");
        assertThat(jdbc.queryForObject("select count(*) from relatorio_aula", Integer.class))
                .isZero();
    }

    @Test
    void relatorioEmAbertoNaConfirmacaoAvisaEOfereceContinuarOuDescartar() throws Exception {
        prepararArquivoTelegram("primeiro", NormalizacaoAudioTeste.AUDIO_COM_FALA);
        escolherMatteo();
        enviarVoz(USUARIO_CLARA, "primeiro", 1000);
        enviarCallback(USUARIO_CLARA, "v1:confal:");
        cenario.processarFila();
        enviarTexto(USUARIO_CLARA, "registrar aula");
        escolherMatteo();
        enviarVoz(USUARIO_CLARA, "segundo", 1000);
        API_BOTS.resetRequests();

        enviarCallback(USUARIO_CLARA, "v1:confal:");

        assertThat(ultimaMensagem().get("text").asString())
                .startsWith("Você tem um relatório em aberto.")
                .contains("pelo Telegram");
        assertThat(botoes(ultimaMensagem())).containsExactly("v1:cont:", "v1:desc:");
        assertThat(trabalhos()).isEqualTo(1);
    }

    @Test
    void cancelarSemRelatorioEmFocoDescartaOAudioPendente() throws Exception {
        escolherMatteo();
        enviarVoz(USUARIO_CLARA, "descartavel", 1000);

        enviarTexto(USUARIO_CLARA, "cancelar");

        assertThat(ultimaMensagem().get("text").asString())
                .isEqualTo("O áudio enviado foi descartado.");
        assertThat(conversa().get("audio_pendente_file_id")).isNull();
    }

    private List<Path> arquivosTemporarios() throws Exception {
        Path diretorio = propriedades.audio().tempDir();
        if (!Files.isDirectory(diretorio)) {
            return List.of();
        }
        try (var arquivos = Files.list(diretorio)) {
            return arquivos.toList();
        }
    }
}
