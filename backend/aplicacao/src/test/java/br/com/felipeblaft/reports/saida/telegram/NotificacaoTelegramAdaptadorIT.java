package br.com.felipeblaft.reports.saida.telegram;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.felipeblaft.reports.domain.common.Channel;
import br.com.felipeblaft.reports.domain.registry.Student;
import br.com.felipeblaft.reports.domain.registry.Teacher;
import br.com.felipeblaft.reports.port.TemporaryFile;
import br.com.felipeblaft.reports.saida.fila.AvisoDemoraTarefa;
import br.com.felipeblaft.reports.suporte.CenarioIT;
import br.com.felipeblaft.reports.suporte.NormalizacaoAudioTeste;
import br.com.felipeblaft.reports.suporte.TelegramConversaBase;
import br.com.felipeblaft.reports.usecase.lesson.AnswerQuestionsUseCase;
import br.com.felipeblaft.reports.usecase.lesson.RegisterLessonByAudioUseCase;
import br.com.felipeblaft.reports.usecase.lesson.RequestLessonReportChangeUseCase;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.JsonNode;

/**
 * Avisos do bot (FR-021, contracts/telegram-conversa.md §6) para trabalhos pedidos em cada canal.
 */
class NotificacaoTelegramAdaptadorIT extends TelegramConversaBase {

    @Autowired private RegisterLessonByAudioUseCase registrar;
    @Autowired private AnswerQuestionsUseCase responder;
    @Autowired private RequestLessonReportChangeUseCase alterar;
    @Autowired private AvisoDemoraTarefa avisoDemora;

    private Teacher clara;
    private Student matteo;

    @BeforeEach
    void preparar() {
        clara = cenario.clara();
        matteo = cenario.aluno(clara, "Matteo Ferreira", "Piano");
    }

    private TemporaryFile audioDaWeb() throws Exception {
        Path diretorio = propriedades.audio().tempDir();
        Files.createDirectories(diretorio);
        Path arquivo = diretorio.resolve("web-" + UUID.randomUUID() + ".webm");
        Files.write(arquivo, NormalizacaoAudioTeste.AUDIO_COM_FALA);
        return new TemporaryFile(arquivo, Files.size(arquivo), "audio/webm");
    }

    private UUID relatorioId() {
        return jdbc.queryForObject("select id from relatorio_aula", UUID.class);
    }

    @Test
    void trabalhoDaWebComVinculoGeraAvisoCurtoSemMudarOFoco() throws Exception {
        vincular(USUARIO_CLARA, CenarioIT.CODIGO_CLARA);

        registrar.run(clara.id(), matteo.id(), audioDaWeb(), Channel.WEB);
        cenario.processarFila();

        JsonNode aviso = ultimaMensagem();
        assertThat(aviso.get("text").asString())
                .isEqualTo("O relatório de Matteo Ferreira tem perguntas pendentes.");
        assertThat(rotulosBotoes(aviso)).containsExactly("Continuar no chat");
        assertThat(botoes(aviso)).containsExactly("v1:foco:a" + relatorioId());
        assertThat(textosEditados()).isEmpty();
        assertThat(
                        jdbc.queryForObject(
                                "select relatorio_foco_id from vinculo_telegram", UUID.class))
                .isNull();

        API_BOTS.resetRequests();
        responder.answerText(clara.id(), relatorioId(), "Estudou bem.", Channel.WEB);
        cenario.processarFila();
        assertThat(ultimaMensagem().get("text").asString())
                .isEqualTo("O relatório de Matteo Ferreira está pronto para conferência.");

        API_BOTS.resetRequests();
        alterar.changeByText(
                clara.id(), relatorioId(), "Diga que a aula foi ótima.", 1, Channel.WEB);
        cenario.processarFila();
        assertThat(ultimaMensagem().get("text").asString())
                .isEqualTo("O relatório de Matteo Ferreira tem uma nova versão (2).");
        assertThat(
                        jdbc.queryForObject(
                                "select relatorio_foco_id from vinculo_telegram", UUID.class))
                .isNull();
    }

    /**
     * V12, US5.3: no modo DIRETA (padrão) a mensagem sai só pela API de bots e nada entra na
     * outbox.
     */
    @Test
    void modoDiretoEnviaDiretoENadaEntraNaOutbox() throws Exception {
        vincular(USUARIO_CLARA, CenarioIT.CODIGO_CLARA);

        registrar.run(clara.id(), matteo.id(), audioDaWeb(), Channel.WEB);
        cenario.processarFila();

        assertThat(ultimaMensagem().get("text").asString())
                .isEqualTo("O relatório de Matteo Ferreira tem perguntas pendentes.");
        assertThat(jdbc.queryForObject("select count(*) from acao_canal", Integer.class)).isZero();
        assertThat(propriedades.canalTelegram().entregaProativa().name()).isEqualTo("DIRETA");
    }

    @Test
    void trabalhoDaWebSemVinculoNaoEnviaNada() throws Exception {
        registrar.run(clara.id(), matteo.id(), audioDaWeb(), Channel.WEB);

        cenario.processarFila();

        assertThat(chamadas("sendMessage")).isEmpty();
        assertThat(jdbc.queryForObject("select status from relatorio_aula", String.class))
                .isEqualTo("AGUARDANDO_RESPOSTAS");
    }

    @Test
    void trabalhoDoTelegramAvisaQueEstaDemorandoUmaVezAos3Minutos() throws Exception {
        vincular(USUARIO_CLARA, CenarioIT.CODIGO_CLARA);
        enviarTexto(USUARIO_CLARA, "Matteo");
        enviarVoz(USUARIO_CLARA, "lenta", 1000);
        enviarCallback(USUARIO_CLARA, "v1:confal:");
        API_BOTS.resetRequests();

        relogio.avancar(Duration.ofMinutes(2));
        avisoDemora.verificar();
        assertThat(chamadas("sendMessage")).isEmpty();

        relogio.avancar(Duration.ofMinutes(1));
        avisoDemora.verificar();
        avisoDemora.verificar();

        assertThat(textosEnviados())
                .containsExactly(
                        "Está demorando mais que o normal. O resultado será entregue assim que ficar pronto; você pode sair e voltar depois.");
        assertThat(
                        jdbc.queryForObject(
                                "select aviso_demora_enviado from trabalho_processamento",
                                Boolean.class))
                .isTrue();
    }

    @Test
    void trabalhoDaWebDemoradoNaoEnviaAvisoPeloBot() throws Exception {
        vincular(USUARIO_CLARA, CenarioIT.CODIGO_CLARA);
        registrar.run(clara.id(), matteo.id(), audioDaWeb(), Channel.WEB);

        relogio.avancar(Duration.ofMinutes(4));
        avisoDemora.verificar();

        assertThat(chamadas("sendMessage")).isEmpty();
        assertThat(
                        jdbc.queryForObject(
                                "select aviso_demora_enviado from trabalho_processamento",
                                Boolean.class))
                .isTrue();
        cenario.processarFila();
    }
}
