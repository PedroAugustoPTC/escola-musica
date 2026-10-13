package br.com.felipeblaft.reports.saida.telegram;

import br.com.felipeblaft.reports.configuracao.PropriedadesAplicacao;
import br.com.felipeblaft.reports.entrada.telegram.ApresentadorTelegram;
import br.com.felipeblaft.reports.entrada.telegram.EnvioTelegram;
import br.com.felipeblaft.reports.entrada.telegram.SaidaTelegram;
import br.com.felipeblaft.reports.saida.persistencia.FilaTrabalhoPostgresAdaptador;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Chamadas à API de bots no servidor local {@code telegram-bot-api} (R-05): {@code sendMessage}
 * (HTML, texto escapado), {@code editMessageText}, {@code answerCallbackQuery}, {@code
 * sendDocument} e {@code getFile}. Falhas são registradas sem o conteúdo das mensagens (FR-055).
 */
@Component
public class MensageriaTelegramAdaptador implements EnvioTelegram {

    private static final Logger LOG = LoggerFactory.getLogger(MensageriaTelegramAdaptador.class);
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final boolean habilitado;
    private final String baseUrl;
    private final HttpClient cliente =
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    private final FilaTrabalhoPostgresAdaptador fila;

    public MensageriaTelegramAdaptador(
            PropriedadesAplicacao propriedades, FilaTrabalhoPostgresAdaptador fila) {
        this.fila = fila;
        PropriedadesAplicacao.Telegram telegram = propriedades.telegram();
        this.habilitado = telegram.token() != null && !telegram.token().isBlank();
        this.baseUrl = telegram.apiUrl() + "/bot" + telegram.token() + "/";
    }

    @Override
    public void enviar(SaidaTelegram saida) {
        switch (saida) {
            case SaidaTelegram.Mensagem m -> enviarMensagem(m.chatId(), m.texto(), m.teclado());
            case SaidaTelegram.Edicao e -> editar(e);
            case SaidaTelegram.MensagemEtapa m -> enviarEtapa(m);
            case SaidaTelegram.Documento d -> enviarDocumento(d);
            case SaidaTelegram.RespostaCallback r ->
                    chamar("answerCallbackQuery", Map.of("callback_query_id", r.callbackId()));
        }
    }

    /**
     * Envia (ou edita no lugar) a mensagem de etapa e grava o seu id no trabalho, para que o worker
     * edite a mesma mensagem a cada etapa (sequência 5.2, passo 4).
     */
    private void enviarEtapa(SaidaTelegram.MensagemEtapa etapa) {
        Optional<Long> mensagemId;
        if (etapa.editarMensagemId() != null) {
            editar(
                    new SaidaTelegram.Edicao(
                            etapa.chatId(),
                            etapa.editarMensagemId(),
                            etapa.texto(),
                            SaidaTelegram.Teclado.NENHUM));
            mensagemId = Optional.of(etapa.editarMensagemId());
        } else {
            mensagemId =
                    enviarMensagem(etapa.chatId(), etapa.texto(), SaidaTelegram.Teclado.NENHUM);
        }
        mensagemId.ifPresent(id -> fila.registerStepMessage(etapa.trabalhoId(), id));
    }

    /** Envia uma mensagem nova; devolve o id da última parte enviada, se a API respondeu. */
    public Optional<Long> enviarMensagem(long chatId, String texto, SaidaTelegram.Teclado teclado) {
        Optional<Long> ultimaId = Optional.empty();
        List<String> partes = ApresentadorTelegram.dividir(texto);
        for (int i = 0; i < partes.size(); i++) {
            Map<String, Object> corpo = new LinkedHashMap<>();
            corpo.put("chat_id", chatId);
            corpo.put("text", partes.get(i));
            corpo.put("parse_mode", "HTML");
            corpo.put("link_preview_options", Map.of("is_disabled", true));
            if (i == partes.size() - 1 && teclado != null && !teclado.vazio()) {
                corpo.put("reply_markup", tecladoJson(teclado));
            }
            ultimaId =
                    chamar("sendMessage", corpo)
                            .map(resultado -> resultado.path("message_id"))
                            .filter(JsonNode::isNumber)
                            .map(JsonNode::asLong);
        }
        return ultimaId;
    }

    public void editar(SaidaTelegram.Edicao edicao) {
        Map<String, Object> corpo = new LinkedHashMap<>();
        corpo.put("chat_id", edicao.chatId());
        corpo.put("message_id", edicao.mensagemId());
        corpo.put("text", edicao.texto());
        corpo.put("parse_mode", "HTML");
        corpo.put(
                "reply_markup",
                tecladoJson(
                        edicao.teclado() == null
                                ? SaidaTelegram.Teclado.NENHUM
                                : edicao.teclado()));
        chamar("editMessageText", corpo);
    }

    static Map<String, Object> tecladoJson(SaidaTelegram.Teclado teclado) {
        return Map.of(
                "inline_keyboard",
                teclado.linhas().stream()
                        .map(
                                linha ->
                                        linha.stream()
                                                .map(
                                                        botao ->
                                                                Map.of(
                                                                        "text",
                                                                        botao.rotulo(),
                                                                        "callback_data",
                                                                        botao.dados()))
                                                .toList())
                        .toList());
    }

    private void enviarDocumento(SaidaTelegram.Documento documento) {
        if (!habilitado) {
            return;
        }
        String fronteira = "----relatorios" + UUID.randomUUID();
        ByteArrayOutputStream corpo = new ByteArrayOutputStream();
        campo(corpo, fronteira, "chat_id", Long.toString(documento.chatId()));
        if (documento.legenda() != null) {
            campo(corpo, fronteira, "caption", documento.legenda());
            campo(corpo, fronteira, "parse_mode", "HTML");
        }
        escrever(
                corpo,
                "--"
                        + fronteira
                        + "\r\nContent-Disposition: form-data; name=\"document\"; filename=\""
                        + documento.nomeArquivo()
                        + "\"\r\nContent-Type: application/pdf\r\n\r\n");
        corpo.writeBytes(documento.conteudo());
        escrever(corpo, "\r\n--" + fronteira + "--\r\n");
        HttpRequest requisicao =
                HttpRequest.newBuilder(URI.create(baseUrl + "sendDocument"))
                        .timeout(Duration.ofSeconds(30))
                        .header("Content-Type", "multipart/form-data; boundary=" + fronteira)
                        .POST(HttpRequest.BodyPublishers.ofByteArray(corpo.toByteArray()))
                        .build();
        executar("sendDocument", requisicao);
    }

    private static void campo(
            ByteArrayOutputStream corpo, String fronteira, String nome, String valor) {
        escrever(
                corpo,
                "--"
                        + fronteira
                        + "\r\nContent-Disposition: form-data; name=\""
                        + nome
                        + "\"\r\n\r\n"
                        + valor
                        + "\r\n");
    }

    private static void escrever(ByteArrayOutputStream saida, String texto) {
        saida.writeBytes(texto.getBytes(StandardCharsets.UTF_8));
    }

    /** Chama um método da API; devolve o campo {@code result} em caso de sucesso. */
    public Optional<JsonNode> chamar(String metodo, Map<String, ?> parametros) {
        if (!habilitado) {
            return Optional.empty();
        }
        HttpRequest requisicao =
                HttpRequest.newBuilder(URI.create(baseUrl + metodo))
                        .timeout(Duration.ofSeconds(15))
                        .header("Content-Type", "application/json")
                        .POST(
                                HttpRequest.BodyPublishers.ofString(
                                        JSON.writeValueAsString(parametros)))
                        .build();
        return executar(metodo, requisicao);
    }

    private Optional<JsonNode> executar(String metodo, HttpRequest requisicao) {
        try {
            HttpResponse<String> resposta =
                    cliente.send(
                            requisicao, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resposta.statusCode() / 100 != 2) {
                LOG.warn("telegram-falha metodo={} status={}", metodo, resposta.statusCode());
                return Optional.empty();
            }
            JsonNode no = JSON.readTree(resposta.body());
            return Optional.ofNullable(no.get("result"));
        } catch (InterruptedException erro) {
            Thread.currentThread().interrupt();
            LOG.warn("telegram-interrompido metodo={}", metodo);
            return Optional.empty();
        } catch (Exception erro) {
            LOG.warn("telegram-erro metodo={} tipo={}", metodo, erro.getClass().getSimpleName());
            return Optional.empty();
        }
    }
}
