package br.com.felipeblaft.reports.entrada.telegram;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.felipeblaft.reports.domain.common.Channel;
import br.com.felipeblaft.reports.domain.registry.Student;
import br.com.felipeblaft.reports.domain.registry.Teacher;
import br.com.felipeblaft.reports.port.TemporaryFile;
import br.com.felipeblaft.reports.suporte.CenarioIT;
import br.com.felipeblaft.reports.suporte.NormalizacaoAudioTeste;
import br.com.felipeblaft.reports.suporte.TelegramConversaBase;
import br.com.felipeblaft.reports.usecase.lesson.AnswerQuestionsUseCase;
import br.com.felipeblaft.reports.usecase.lesson.RegisterLessonByAudioUseCase;
import br.com.felipeblaft.reports.usecase.lesson.RequestLessonReportChangeUseCase;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Retomada entre canais pelo bot (sequência 5.3, US3): relatório iniciado na Web. */
class RetomadaConversaIT extends TelegramConversaBase {

    @Autowired private RegisterLessonByAudioUseCase registrar;
    @Autowired private AnswerQuestionsUseCase responder;
    @Autowired private RequestLessonReportChangeUseCase alterar;

    private Teacher clara;
    private Student matteo;

    @BeforeEach
    void preparar() throws Exception {
        clara = cenario.clara();
        matteo = cenario.aluno(clara, "Matteo Ferreira", "Piano");
        vincular(USUARIO_CLARA, CenarioIT.CODIGO_CLARA);
    }

    private TemporaryFile audioDaWeb() throws Exception {
        Path diretorio = propriedades.audio().tempDir();
        Files.createDirectories(diretorio);
        Path arquivo = diretorio.resolve("web-" + UUID.randomUUID() + ".webm");
        Files.write(arquivo, NormalizacaoAudioTeste.AUDIO_COM_FALA);
        return new TemporaryFile(arquivo, Files.size(arquivo), "audio/webm");
    }

    private UUID relatorioId() {
        return jdbc.queryForObject(
                "select id from relatorio_aula where status <> 'CANCELADO'", UUID.class);
    }

    /** Relatório iniciado na Web e deixado na conferência (versão 1). */
    private UUID relatorioNaConferenciaPelaWeb() throws Exception {
        registrar.run(clara.id(), matteo.id(), audioDaWeb(), Channel.WEB);
        cenario.processarFila();
        responder.answerText(clara.id(), relatorioId(), "Estudou bem.", Channel.WEB);
        cenario.processarFila();
        API_BOTS.resetRequests();
        return relatorioId();
    }

    @Test
    void escolherOAlunoAvisaDoRelatorioEmAbertoComCanalEData() throws Exception {
        relatorioNaConferenciaPelaWeb();

        enviarTexto(USUARIO_CLARA, "registrar aula");
        enviarTexto(USUARIO_CLARA, "Matteo");

        assertThat(ultimaMensagem().get("text").asString())
                .isEqualTo(
                        "Você tem um relatório em aberto. Este aluno já tem um relatório iniciado pelo portal e ainda não concluído (última alteração em 21/09/2026 às 10:00).");
        assertThat(botoes(ultimaMensagem())).containsExactly("v1:cont:", "v1:desc:");
    }

    @Test
    void continuarReapresentaAConferenciaNaVersaoVigente() throws Exception {
        UUID id = relatorioNaConferenciaPelaWeb();
        alterar.changeByText(clara.id(), id, "Diga que a aula foi ótima.", 1, Channel.WEB);
        cenario.processarFila();
        enviarTexto(USUARIO_CLARA, "registrar aula");
        enviarTexto(USUARIO_CLARA, "Matteo");
        API_BOTS.resetRequests();

        enviarCallback(USUARIO_CLARA, "v1:cont:");

        assertThat(String.join("\n", textosEnviados()))
                .contains("Confira o relatório antes de aprovar")
                .contains("Versão 2");
        assertThat(botoes(ultimaMensagem())).contains("v1:aprov:2");
        assertThat(
                        jdbc.queryForObject(
                                "select relatorio_foco_id from vinculo_telegram", UUID.class))
                .isEqualTo(id);
        assertThat(
                        jdbc.queryForObject(
                                "select versao_apresentada from vinculo_telegram", Integer.class))
                .isEqualTo(2);
    }

    @Test
    void continuarComPerguntasPendentesMostraAsPerguntas() throws Exception {
        registrar.run(clara.id(), matteo.id(), audioDaWeb(), Channel.WEB);
        cenario.processarFila();
        enviarTexto(USUARIO_CLARA, "registrar aula");
        enviarTexto(USUARIO_CLARA, "Matteo");
        API_BOTS.resetRequests();

        enviarTexto(USUARIO_CLARA, "continuar");

        assertThat(ultimaMensagem().get("text").asString())
                .startsWith("Faltam algumas informações.");
    }

    @Test
    void registroAindaEmTranscricaoInformaAEtapa() throws Exception {
        registrar.run(clara.id(), matteo.id(), audioDaWeb(), Channel.WEB);
        enviarTexto(USUARIO_CLARA, "registrar aula");
        enviarTexto(USUARIO_CLARA, "Matteo");
        API_BOTS.resetRequests();

        enviarCallback(USUARIO_CLARA, "v1:cont:");

        assertThat(ultimaMensagem().get("text").asString())
                .startsWith("Este relatório ainda está sendo processado");
        cenario.processarFila();
    }

    @Test
    void descartarPedeConfirmacaoCancelaEPedeONovoAudio() throws Exception {
        relatorioNaConferenciaPelaWeb();
        enviarTexto(USUARIO_CLARA, "registrar aula");
        enviarTexto(USUARIO_CLARA, "Matteo");

        enviarCallback(USUARIO_CLARA, "v1:desc:");
        assertThat(ultimaMensagem().get("text").asString())
                .isEqualTo(
                        "Descartar o relatório em aberto? Ele deixará de aparecer para você e não poderá ser recuperado.");
        assertThat(botoes(ultimaMensagem())).containsExactly("v1:descok:", "v1:inicio:");
        API_BOTS.resetRequests();

        enviarCallback(USUARIO_CLARA, "v1:descok:");

        assertThat(textosEnviados()).contains("Relatório cancelado.");
        assertThat(ultimaMensagem().get("text").asString())
                .startsWith("Aula de hoje, 21/09/2026, com Matteo Ferreira.");
        assertThat(jdbc.queryForObject("select status from relatorio_aula", String.class))
                .isEqualTo("CANCELADO");
    }

    @Test
    void descartarComAudioPendenteValidoReaproveitaOAudio() throws Exception {
        registrar.run(clara.id(), matteo.id(), audioDaWeb(), Channel.WEB);
        cenario.processarFila();
        prepararArquivoTelegram("reaproveitado", NormalizacaoAudioTeste.AUDIO_COM_FALA);
        enviarVoz(USUARIO_CLARA, "reaproveitado", 1000);
        enviarCallback(USUARIO_CLARA, "v1:aluno:0");
        assertThat(ultimaMensagem().get("text").asString())
                .startsWith("Você tem um relatório em aberto.");

        enviarCallback(USUARIO_CLARA, "v1:desc:");
        enviarTexto(USUARIO_CLARA, "sim");

        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from trabalho_processamento where tipo = 'REGISTRO_AULA' and status = 'PENDENTE'",
                                Integer.class))
                .isEqualTo(1);
        assertThat(
                        jdbc.queryForObject(
                                "select parametros->>'arquivoCanal' from trabalho_processamento where status = 'PENDENTE'",
                                String.class))
                .isEqualTo("reaproveitado");
        cenario.processarFila();
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from relatorio_aula where status = 'AGUARDANDO_RESPOSTAS'",
                                Integer.class))
                .isEqualTo(1);
    }

    @Test
    void inicioOfereceContinuarORelatorioEmAberto() throws Exception {
        UUID id = relatorioNaConferenciaPelaWeb();

        enviarTexto(USUARIO_CLARA, "início");

        assertThat(botoes(ultimaMensagem())).contains("v1:foco:a" + id);
        assertThat(rotulosBotoes(ultimaMensagem()))
                .contains("Continuar: Matteo Ferreira (21/09/2026)");
    }
}
