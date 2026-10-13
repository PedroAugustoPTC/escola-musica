package br.com.felipeblaft.reports.suporte;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Especificação executável do workflow do orquestrador (feature 003): faz o que o n8n fará, e só
 * isso. Percorre as ações em ordem <strong>sem parar na primeira falha</strong>, traduz cada uma
 * para a API de bots (aqui, o WireMock) conforme a tabela de contracts/canal-telegram-interno.md §4
 * e informa o resultado por {@code POST /confirmacoes}. Não decide nada: não compensa, não reenvia,
 * não interpreta texto. Sem estado: a idempotência vem de {@code GET /acoes/{id}}.
 */
public class ExecutorAcoesSimuladoTeste {

    /**
     * Chamadas à API interna do backend (o teste usa MockMvc; a verificação do executor, um dublê).
     */
    public interface ClienteInterno {

        record Resposta(int status, byte[] corpo) {

            public String texto() {
                return new String(corpo, StandardCharsets.UTF_8);
            }
        }

        Resposta get(String caminho) throws Exception;

        Resposta post(String caminho, String corpoJson) throws Exception;
    }

    /** Resultado informado ao backend para uma ação. */
    public record Resultado(String acaoId, String situacao, String codigoMotivo, Long mensagemId) {}

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String BASE = "/interno/v1/canal/telegram";

    private final ClienteInterno interno;
    private final String urlApiBots;
    private final HttpClient http = HttpClient.newHttpClient();

    /** Resultados já informados por este executor, na ordem (para asserções dos testes). */
    public final List<Resultado> informados = new ArrayList<>();

    public ExecutorAcoesSimuladoTeste(ClienteInterno interno, String urlApiBotsComToken) {
        this.interno = interno;
        this.urlApiBots =
                urlApiBotsComToken.endsWith("/") ? urlApiBotsComToken : urlApiBotsComToken + "/";
    }

    /** Executa as ações da lista e confirma os resultados num único lote. */
    public void executar(JsonNode acoes) throws Exception {
        List<Resultado> resultados = new ArrayList<>();
        for (JsonNode acao : acoes) {
            String acaoId = acao.path("acaoId").asString();
            if (jaTratada(acaoId)) {
                continue;
            }
            resultados.add(executarUma(acaoId, acao));
        }
        confirmar(resultados);
    }

    // ------------------------------------------------------------------ idempotência

    /** O receptor não guarda estado: consulta o backend e pula o que já está tratado. */
    private boolean jaTratada(String acaoId) throws Exception {
        ClienteInterno.Resposta resposta = interno.get(BASE + "/acoes/" + acaoId);
        if (resposta.status() != 200) {
            return false;
        }
        String estado = JSON.readTree(resposta.corpo()).path("estado").asString();
        return estado.equals("EXECUTADA") || estado.equals("FALHOU");
    }

    // ------------------------------------------------------------------ tradução ação -> API de
    // bots

    private Resultado executarUma(String acaoId, JsonNode acao) throws Exception {
        String tipo = acao.path("tipo").asString();
        return switch (tipo) {
            case "MENSAGEM" -> enviarMensagem(acaoId, acao);
            case "EDICAO" -> editar(acaoId, acao, acao.path("mensagemId").asLong());
            case "DOCUMENTO" -> enviarDocumento(acaoId, acao);
            case "RESPOSTA_CALLBACK" ->
                    resultadoDe(
                            acaoId,
                            chamarJson(
                                    "answerCallbackQuery",
                                    JSON.createObjectNode()
                                            .put(
                                                    "callback_query_id",
                                                    acao.path("callbackId").asString())),
                            null);
            case "MENSAGEM_ETAPA" -> etapa(acaoId, acao);
            // tipo novo: pula, informa e segue com as demais (FR-003a)
            default -> new Resultado(acaoId, "FALHOU", "ACAO_NAO_SUPORTADA", null);
        };
    }

    private Resultado enviarMensagem(String acaoId, JsonNode acao) throws Exception {
        ObjectNode corpo = JSON.createObjectNode();
        corpo.put("chat_id", acao.path("chatId").asLong());
        corpo.put("text", acao.path("texto").asString());
        corpo.put("parse_mode", "HTML");
        corpo.set("link_preview_options", JSON.createObjectNode().put("is_disabled", true));
        JsonNode teclado = acao.path("teclado");
        if (teclado.path("linhas").size() > 0) {
            corpo.set("reply_markup", tecladoTelegram(teclado));
        }
        return resultadoDe(acaoId, chamarJson("sendMessage", corpo), null);
    }

    private Resultado editar(String acaoId, JsonNode acao, long mensagemId) throws Exception {
        ObjectNode corpo = JSON.createObjectNode();
        corpo.put("chat_id", acao.path("chatId").asLong());
        corpo.put("message_id", mensagemId);
        corpo.put("text", acao.path("texto").asString());
        corpo.put("parse_mode", "HTML");
        // teclado vazio remove os botões
        corpo.set("reply_markup", tecladoTelegram(acao.path("teclado")));
        return resultadoDe(acaoId, chamarJson("editMessageText", corpo), mensagemId);
    }

    private Resultado etapa(String acaoId, JsonNode acao) throws Exception {
        if (acao.hasNonNull("editarMensagemId")) {
            return editar(acaoId, acao, acao.path("editarMensagemId").asLong());
        }
        return enviarMensagem(acaoId, acao);
    }

    /** Uma única tentativa de GET; 404 ou 5xx viram DOCUMENTO_INDISPONIVEL, sem repetir. */
    private Resultado enviarDocumento(String acaoId, JsonNode acao) throws Exception {
        ClienteInterno.Resposta pdf =
                interno.get(BASE + "/documentos/" + acao.path("documentoId").asString());
        if (pdf.status() != 200) {
            return new Resultado(acaoId, "FALHOU", "DOCUMENTO_INDISPONIVEL", null);
        }
        String fronteira = "----executor" + UUID.randomUUID();
        ByteArrayOutputStream corpo = new ByteArrayOutputStream();
        campo(corpo, fronteira, "chat_id", Long.toString(acao.path("chatId").asLong()));
        if (acao.hasNonNull("legenda")) {
            campo(corpo, fronteira, "caption", acao.path("legenda").asString());
            campo(corpo, fronteira, "parse_mode", "HTML");
        }
        escrever(
                corpo,
                "--"
                        + fronteira
                        + "\r\nContent-Disposition: form-data; name=\"document\"; filename=\""
                        + acao.path("nomeArquivo").asString()
                        + "\"\r\nContent-Type: "
                        + acao.path("tipoConteudo").asString("application/pdf")
                        + "\r\n\r\n");
        corpo.writeBytes(pdf.corpo());
        escrever(corpo, "\r\n--" + fronteira + "--\r\n");
        HttpRequest requisicao =
                HttpRequest.newBuilder(URI.create(urlApiBots + "sendDocument"))
                        .header("Content-Type", "multipart/form-data; boundary=" + fronteira)
                        .POST(HttpRequest.BodyPublishers.ofByteArray(corpo.toByteArray()))
                        .build();
        return resultadoDe(
                acaoId, http.send(requisicao, HttpResponse.BodyHandlers.ofString()), null);
    }

    private HttpResponse<String> chamarJson(String metodo, ObjectNode corpo) throws Exception {
        HttpRequest requisicao =
                HttpRequest.newBuilder(URI.create(urlApiBots + metodo))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(corpo.toString()))
                        .build();
        return http.send(requisicao, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private static ObjectNode tecladoTelegram(JsonNode teclado) {
        ArrayNode linhas = JSON.createArrayNode();
        for (JsonNode linha : teclado.path("linhas")) {
            ArrayNode botoes = JSON.createArrayNode();
            for (JsonNode botao : linha) {
                botoes.add(
                        JSON.createObjectNode()
                                .put("text", botao.path("rotulo").asString())
                                .put("callback_data", botao.path("dados").asString()));
            }
            linhas.add(botoes);
        }
        ObjectNode no = JSON.createObjectNode();
        no.set("inline_keyboard", linhas);
        return no;
    }

    // ------------------------------------------------------------------ erro do Telegram ->
    // resultado

    /**
     * @param mensagemIdConhecido id já conhecido (edição); se nulo, lido de {@code
     *     result.message_id}
     */
    private Resultado resultadoDe(
            String acaoId, HttpResponse<String> resposta, Long mensagemIdConhecido) {
        int status = resposta.statusCode();
        String descricao = descricaoDe(resposta.body());
        if (status / 100 == 2) {
            Long mensagemId = mensagemIdConhecido;
            if (mensagemId == null) {
                JsonNode id = lerResultado(resposta.body()).path("message_id");
                mensagemId = id.isNumber() ? id.asLong() : null;
            }
            return new Resultado(acaoId, "EXECUTADA", null, mensagemId);
        }
        String codigo = classificar(status, descricao);
        return codigo == null
                ? new Resultado(acaoId, "EXECUTADA", null, mensagemIdConhecido)
                : new Resultado(acaoId, "FALHOU", codigo, null);
    }

    /** Tabela "erro do Telegram -> situação" (contracts/canal-telegram-interno.md §4). */
    public static String classificar(int status, String descricao) {
        String texto = descricao == null ? "" : descricao.toLowerCase();
        if (texto.contains("message is not modified")) {
            return null; // execução bem-sucedida: nada mudou
        }
        if (texto.contains("message to edit not found")
                || texto.contains("message can't be edited")
                || texto.contains("message_id_invalid")) {
            return "MENSAGEM_NAO_EDITAVEL";
        }
        if (status == 403 || texto.contains("chat not found")) {
            return "CHAT_INDISPONIVEL";
        }
        if (status == 429) {
            return "LIMITE_DE_TAXA";
        }
        return "ERRO_TELEGRAM";
    }

    private static String descricaoDe(String corpo) {
        try {
            return JSON.readTree(corpo).path("description").asString("");
        } catch (RuntimeException ilegivel) {
            return "";
        }
    }

    private static JsonNode lerResultado(String corpo) {
        try {
            return JSON.readTree(corpo).path("result");
        } catch (RuntimeException ilegivel) {
            return JSON.createObjectNode();
        }
    }

    // ------------------------------------------------------------------ confirmação

    private void confirmar(List<Resultado> resultados) throws Exception {
        if (resultados.isEmpty()) {
            return;
        }
        ArrayNode itens = JSON.createArrayNode();
        for (Resultado resultado : resultados) {
            informados.add(resultado);
            ObjectNode item =
                    JSON.createObjectNode()
                            .put("acaoId", resultado.acaoId())
                            .put("situacao", resultado.situacao());
            if (resultado.codigoMotivo() != null) {
                item.put("codigoMotivo", resultado.codigoMotivo());
            }
            if (resultado.mensagemId() != null) {
                item.put("mensagemId", resultado.mensagemId());
            }
            itens.add(item);
        }
        ObjectNode corpo = JSON.createObjectNode().put("versao", "v1");
        corpo.set("resultados", itens);
        ClienteInterno.Resposta resposta = interno.post(BASE + "/confirmacoes", corpo.toString());
        if (resposta.status() != 200) {
            throw new AssertionError(
                    "confirmação recusada: " + resposta.status() + " " + resposta.texto());
        }
        EsquemaAcoesV1.exigir("RespostaConfirmacao", resposta.texto());
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
}
