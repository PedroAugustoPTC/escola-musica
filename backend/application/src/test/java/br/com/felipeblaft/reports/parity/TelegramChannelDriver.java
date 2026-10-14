package br.com.felipeblaft.reports.parity;

import br.com.felipeblaft.reports.config.MessageCatalog;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import tools.jackson.databind.JsonNode;

/**
 * O roteiro de paridade pelo bot: updates enviados ao webhook e leitura das mensagens enviadas à
 * API de bots simulada. Os textos são traduzidos de volta para os códigos do catálogo.
 */
public class TelegramChannelDriver implements ChannelDriver {

    /** Envia um texto como o professor e devolve as mensagens do bot produzidas por ele. */
    public interface Conversation {
        List<JsonNode> send(String text) throws Exception;
    }

    private static final Pattern TOTAL = Pattern.compile("(\\d+) aluno\\(s\\) associado\\(s\\)");
    private static final Pattern STUDENT =
            Pattern.compile("^• ([^·\\n]+?)(?: ·.*)?$", Pattern.MULTILINE);

    private final Conversation conversation;
    private final MessageCatalog catalog;
    private final Supplier<Long> links;

    public TelegramChannelDriver(
            Conversation conversation, MessageCatalog catalog, Supplier<Long> links) {
        this.conversation = conversation;
        this.catalog = catalog;
        this.links = links;
    }

    @Override
    public String name() {
        return "Telegram";
    }

    private String lastText(List<JsonNode> messages) {
        return messages.getLast().get("text").asString();
    }

    /** Código do catálogo cujo texto (até o primeiro parâmetro) inicia a mensagem. */
    String textCode(String text) {
        String better = null;
        int larger = 0;
        for (String key : catalog.keys()) {
            String model = catalog.model(key);
            int parameter = model.indexOf('{');
            String prefix = parameter >= 0 ? model.substring(0, parameter) : model;
            if (prefix.length() > larger && !prefix.isBlank() && text.startsWith(prefix)) {
                better = key;
                larger = prefix.length();
            }
        }
        return better;
    }

    @Override
    public String signIn(String code) throws Exception {
        String text = lastText(conversation.send(code));
        if (text.startsWith(catalog.text("VINCULO_CRIADO").substring(0, 20))) {
            return "SUCESSO";
        }
        return textCode(text);
    }

    @Override
    public HomeSummary start() throws Exception {
        String text = lastText(conversation.send("início"));
        Matcher total = TOTAL.matcher(text);
        int quantity = total.find() ? Integer.parseInt(total.group(1)) : 0;
        List<String> names = new ArrayList<>();
        String students = text.substring(text.indexOf("aluno(s) associado(s)"));
        Matcher student = STUDENT.matcher(students);
        while (student.find()) {
            names.add(student.group(1).strip());
        }
        return new HomeSummary(quantity, names);
    }

    @Override
    public SearchResult search(String text) throws Exception {
        JsonNode message = conversation.send(text).getLast();
        String response = message.get("text").asString();
        String code = textCode(response);
        List<String> names = new ArrayList<>();
        if ("ALUNO_SELECIONADO".equals(code)) {
            Matcher m = Pattern.compile(", com (.+?)\\. Envie").matcher(response);
            if (m.find()) {
                names.add(m.group(1));
            }
            return new SearchResult("ENCONTRADOS", names);
        }
        for (JsonNode line : message.path("reply_markup").path("inline_keyboard")) {
            for (JsonNode button : line) {
                if (button.get("callback_data").asString().startsWith("v1:aluno:")) {
                    names.add(button.get("text").asString().replace(" · Em aberto", ""));
                }
            }
        }
        String type = "NENHUM_ALUNO_ENCONTRADO".equals(code) ? "NENHUM" : "ENCONTRADOS";
        return new SearchResult(type, names);
    }

    @Override
    public String end() throws Exception {
        conversation.send("sair");
        conversation.send("sim");
        return links.get() == 0 ? "ENCERRADO" : "ATIVO";
    }
}
