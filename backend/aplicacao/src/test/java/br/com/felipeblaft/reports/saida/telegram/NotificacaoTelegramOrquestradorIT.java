package br.com.felipeblaft.reports.saida.telegram;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.felipeblaft.reports.domain.common.Channel;
import br.com.felipeblaft.reports.saida.fila.AvisoDemoraTarefa;
import br.com.felipeblaft.reports.suporte.CenarioIT;
import br.com.felipeblaft.reports.suporte.EntregaOrquestradorBase;
import br.com.felipeblaft.reports.suporte.NormalizacaoAudioTeste;
import br.com.felipeblaft.reports.usecase.lesson.AnswerQuestionsUseCase;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.JsonNode;

/**
 * V13, US3.1, US3.2, V12: com {@code entrega-proativa=ORQUESTRADOR} os avisos e resultados do
 * processamento saem pela outbox, com as mesmas mensagens e a mesma ordem do envio direto, o
 * relatório em foco quando pedido no Telegram e sem nenhuma chamada direta do backend à API de
 * bots. Os casos do modo {@code DIRETA} estão em {@link NotificacaoTelegramAdaptadorIT}, intactos.
 */
class NotificacaoTelegramOrquestradorIT extends EntregaOrquestradorBase {

    @Autowired AvisoDemoraTarefa avisoDemora;
    @Autowired AnswerQuestionsUseCase responder;

    private UUID relatorioId() {
        return jdbc.queryForObject("select id from relatorio_aula", UUID.class);
    }

    private void semChamadaDiretaDoBackend() {
        for (String metodo :
                List.of("sendMessage", "editMessageText", "sendDocument", "answerCallbackQuery")) {
            assertThat(chamadas(metodo)).as(metodo).isEmpty();
        }
    }

    private void chegarAsPerguntasPeloTelegram() throws Exception {
        vincular(USUARIO_CLARA, CenarioIT.CODIGO_CLARA);
        prepararArquivoTelegram("aula", NormalizacaoAudioTeste.AUDIO_COM_FALA);
        enviarTexto(USUARIO_CLARA, "Matteo");
        enviarVoz(USUARIO_CLARA, "aula", 1000);
        enviarCallback(USUARIO_CLARA, "v1:confal:");
        API_BOTS.resetRequests();
        cenario.processarFila();
    }

    /** Métodos chamados à API de bots, na ordem cronológica. */
    private List<String> ordemDasChamadas() {
        List<ServeEvent> eventos = new ArrayList<>(API_BOTS.getAllServeEvents());
        Collections.reverse(eventos);
        List<String> metodos = new ArrayList<>();
        for (ServeEvent evento : eventos) {
            String url = evento.getRequest().getUrl();
            metodos.add(url.substring(url.lastIndexOf('/') + 1));
        }
        return metodos;
    }

    // ---- aviso curto de trabalho da Web (US3.2)

    @Test
    void trabalhoDaWebComVinculoGeraAvisoCurtoComContinuarNoChatSemMudarOFoco() throws Exception {
        vincular(USUARIO_CLARA, CenarioIT.CODIGO_CLARA);

        registrar.run(clara.id(), matteo.id(), audioDaWeb(), Channel.WEB);
        cenario.processarFila();

        // o backend só gravou na outbox: nada foi enviado ao Telegram por ele
        semChamadaDiretaDoBackend();
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from acao_canal where estado = 'PENDENTE'",
                                Integer.class))
                .isEqualTo(1);

        drenar(5);

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

        // resultado seguinte do mesmo relatório: "pronto para conferência"
        API_BOTS.resetRequests();
        responder.answerText(clara.id(), relatorioId(), "Estudou bem.", Channel.WEB);
        cenario.processarFila();
        drenar(5);
        assertThat(ultimaMensagem().get("text").asString())
                .isEqualTo("O relatório de Matteo Ferreira está pronto para conferência.");
        assertThat(
                        jdbc.queryForObject(
                                "select relatorio_foco_id from vinculo_telegram", UUID.class))
                .isNull();
    }

    @Test
    void trabalhoDaWebSemVinculoNaoPublicaNada() throws Exception {
        registrar.run(clara.id(), matteo.id(), audioDaWeb(), Channel.WEB);
        cenario.processarFila();
        drenar(3);
        assertThat(jdbc.queryForObject("select count(*) from acao_canal", Integer.class)).isZero();
        assertThat(entregas()).isEmpty();
    }

    // ---- resultado de áudio do Telegram (US3.1)

    @Test
    void perguntasDoTelegramSaemPelaOutboxComAsMesmasEdicoesDeEtapaMensagemEFoco()
            throws Exception {
        chegarAsPerguntasPeloTelegram();

        semChamadaDiretaDoBackend();
        drenar(10);

        assertThat(textosEditados())
                .contains("<i>Transcrevendo…</i>", "<i>Estruturando o relatório…</i>");
        assertThat(chamadas("editMessageText"))
                .allSatisfy(e -> assertThat(e.get("message_id").asLong()).isEqualTo(55));
        String perguntas = ultimaMensagem().get("text").asString();
        assertThat(perguntas)
                .startsWith(
                        "Faltam algumas informações. Responda numa única mensagem, digitando ou gravando:")
                .contains("\n1. O aluno estudou em casa durante a semana?")
                .contains("Texto ou áudio enviado aqui será aplicado a este relatório.");
        assertThat(botoes(ultimaMensagem())).containsExactly("v1:canc:", "v1:reg:");
        // relatório em foco, como no envio direto
        assertThat(
                        jdbc.queryForObject(
                                "select relatorio_foco_id from vinculo_telegram", UUID.class))
                .isEqualTo(relatorioId());
        assertThat(
                        jdbc.queryForObject(
                                "select parametros->>'mensagemEtapa' from trabalho_processamento",
                                String.class))
                .isEqualTo("55");
        // tudo foi confirmado pelo orquestrador
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from acao_canal where estado <> 'EXECUTADA'",
                                Integer.class))
                .isZero();
    }

    @Test
    void conferenciaComPdfSaiNaOrdemDeHojeTextoDocumentoBotoes() throws Exception {
        chegarAsPerguntasPeloTelegram();
        drenar(10);
        enviarTexto(USUARIO_CLARA, "Estudou bem; vai preparar a valsa.");
        API_BOTS.resetRequests();
        cenario.processarFila();

        semChamadaDiretaDoBackend();
        drenar(10);

        List<String> ordem = ordemDasChamadas();
        int documento = ordem.indexOf("sendDocument");
        assertThat(documento).as("ordem %s", ordem).isGreaterThan(0);
        assertThat(ordem.stream().filter("sendDocument"::equals).count()).isEqualTo(1);
        // depois do documento só a mensagem com os botões
        assertThat(ordem.subList(documento + 1, ordem.size())).containsExactly("sendMessage");
        JsonNode acoes = ultimaMensagem();
        assertThat(acoes.get("text").asString())
                .contains("Texto ou áudio enviado aqui será aplicado a este relatório.");
        assertThat(botoes(acoes))
                .containsExactly("v1:aprov:1", "v1:alt:", "v1:pdf:", "v1:canc:", "v1:reg:");
        assertThat(String.join("\n", textosEnviados()))
                .contains("Respostas incorporadas ao relatório. Entendi:")
                .contains("Aluno: Matteo Ferreira");
        assertThat(jdbc.queryForObject("select status from relatorio_aula", String.class))
                .isEqualTo("AGUARDANDO_CONFERENCIA");
    }

    @Test
    void falhaDeProcessamentoDoTelegramTrazAsOpcoesDeTentarDeNovo() throws Exception {
        vincular(USUARIO_CLARA, CenarioIT.CODIGO_CLARA);
        enviarTexto(USUARIO_CLARA, "Matteo");
        enviarVoz(USUARIO_CLARA, "lenta", 1000); // sem arquivo na API: o áudio não é processável
        enviarCallback(USUARIO_CLARA, "v1:confal:");
        API_BOTS.resetRequests();
        cenario.processarFila();

        semChamadaDiretaDoBackend();
        drenar(10);

        assertThat(ultimaMensagem().get("text").asString()).isNotBlank();
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from acao_canal where estado <> 'EXECUTADA'",
                                Integer.class))
                .isZero();
    }

    // ---- demora

    @Test
    void avisoDeDemoraDoTelegramSaiUmaVezPelaOutbox() throws Exception {
        vincular(USUARIO_CLARA, CenarioIT.CODIGO_CLARA);
        enviarTexto(USUARIO_CLARA, "Matteo");
        enviarVoz(USUARIO_CLARA, "lenta", 1000);
        enviarCallback(USUARIO_CLARA, "v1:confal:");
        API_BOTS.resetRequests();

        relogio.avancar(Duration.ofMinutes(3));
        avisoDemora.verificar();
        avisoDemora.verificar();

        semChamadaDiretaDoBackend();
        drenar(5);
        assertThat(textosEnviados())
                .containsExactly(
                        "Está demorando mais que o normal. O resultado será entregue assim que ficar pronto; você pode sair e voltar depois.");
    }
}
