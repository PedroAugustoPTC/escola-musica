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

    /** Toca num botão como o professor e devolve as mensagens do bot produzidas por ele. */
    public interface Taps {
        List<JsonNode> tap(String data) throws Exception;
    }

    private final Conversation conversation;
    private Taps taps;
    private final MessageCatalog catalog;
    private final Supplier<Long> links;

    public TelegramChannelDriver(
            Conversation conversation, MessageCatalog catalog, Supplier<Long> links) {
        this.conversation = conversation;
        this.catalog = catalog;
        this.links = links;
    }

    /** Habilita as operações de "Meus alunos", que usam botões. */
    public TelegramChannelDriver withTaps(Taps taps) {
        this.taps = taps;
        return this;
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
        if (text.startsWith(catalog.text("LINK_CREATED").substring(0, 20))) {
            return "SUCCESS";
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
        if ("SELECTED_STUDENT".equals(code)) {
            Matcher m = Pattern.compile(", com (.+?)\\. Envie").matcher(response);
            if (m.find()) {
                names.add(m.group(1));
            }
            return new SearchResult("FOUND", names);
        }
        for (JsonNode line : message.path("reply_markup").path("inline_keyboard")) {
            for (JsonNode button : line) {
                if (button.get("callback_data").asString().startsWith("v1:student:")) {
                    names.add(button.get("text").asString().replace(" · Em aberto", ""));
                }
            }
        }
        String type = "NO_STUDENT_FOUND".equals(code) ? "NONE" : "FOUND";
        return new SearchResult(type, names);
    }

    @Override
    public String end() throws Exception {
        conversation.send("sair");
        conversation.send("sim");
        return links.get() == 0 ? "ENDED" : "ACTIVE";
    }

    // ---- "Meus alunos"

    private JsonNode say(String text) throws Exception {
        return conversation.send(text).getLast();
    }

    private JsonNode press(String data) throws Exception {
        return taps.tap(data).getLast();
    }

    private static List<JsonNode> buttonsOf(JsonNode message) {
        List<JsonNode> buttons = new ArrayList<>();
        message.path("reply_markup")
                .path("inline_keyboard")
                .forEach(line -> line.forEach(buttons::add));
        return buttons;
    }

    private static String buttonDataNamed(JsonNode message, String name) {
        for (JsonNode button : buttonsOf(message)) {
            String data = button.get("callback_data").asString();
            if (data.startsWith("v1:reg-open:") && button.get("text").asString().equals(name)) {
                return data;
            }
        }
        return null;
    }

    /**
     * Código do erro do catálogo que o bot mostrou, ou {@code null} se é uma pergunta/etapa normal.
     */
    private String errorCodeOf(JsonNode message) {
        String code = textCode(message.get("text").asString());
        return code == null || code.startsWith("REGISTRY_") || code.startsWith("CONFIRM_")
                ? null
                : code;
    }

    @Override
    public String registerStudent(NewStudent student) throws Exception {
        say("cadastrar aluno");
        say(student.name());
        say(student.birthDate());
        JsonNode reply = say(student.cpf());
        String error = errorCodeOf(reply);
        if (error != null) {
            say("cancelar");
            return error;
        }
        if (student.guardian() != null) {
            say(student.guardian().name());
            say(student.guardian().phone());
            reply = say(student.guardian().email());
            // consentimento do responsável (C1 a C5): o mesmo do corpo da Web
            press("v1:consent-yes:");
            say("Responsável Fictício");
            press("v1:consent-rel:MOTHER");
            press("v1:consent-date:today");
            reply = press("v1:consent-method:DIGITAL");
        }
        say(student.instrument() == null ? "pular" : student.instrument());
        reply = say("sim");
        return "REGISTRY_SAVED".equals(textCode(reply.get("text").asString()))
                ? "SUCCESS"
                : String.valueOf(textCode(reply.get("text").asString()));
    }

    private List<String> openButtonNames(JsonNode message) {
        List<String> names = new ArrayList<>();
        for (JsonNode button : buttonsOf(message)) {
            if (button.get("callback_data").asString().startsWith("v1:reg-open:")) {
                names.add(button.get("text").asString());
            }
        }
        return names;
    }

    @Override
    public List<String> listStudents(String status) throws Exception {
        JsonNode list = say("meus alunos");
        if ("INACTIVE".equals(status)) {
            list = press("v1:reg-filter:INACTIVE");
        }
        return openButtonNames(list);
    }

    @Override
    public List<String> searchStudents(String text) throws Exception {
        say("meus alunos");
        return openButtonNames(say(text));
    }

    /** Abre o cartão do aluno, procurando entre os ativos e depois entre os inativos. */
    private JsonNode card(String name) throws Exception {
        JsonNode list = say("meus alunos");
        String data = buttonDataNamed(list, name);
        if (data == null) {
            list = press("v1:reg-filter:INACTIVE");
            data = buttonDataNamed(list, name);
        }
        if (data == null) {
            throw new IllegalStateException("aluno fora da lista: " + name);
        }
        return press(data);
    }

    @Override
    public String openStudent(String name) throws Exception {
        String[] lines = card(name).get("text").asString().split("\n");
        String[] age = lines[1].split(" anos · ", 2);
        return lines[0]
                + "|"
                + age[0]
                + "|"
                + age[1]
                + "|"
                + (lines[2].contains("Inativ") ? "INACTIVE" : "ACTIVE");
    }

    @Override
    public String changeInstrument(String name, String instrument) throws Exception {
        card(name);
        press("v1:reg-edit:");
        press("v1:reg-edit:instrument");
        say(instrument);
        JsonNode reply = say("sim");
        return "REGISTRY_UPDATED".equals(textCode(reply.get("text").asString()))
                ? "SUCCESS"
                : String.valueOf(textCode(reply.get("text").asString()));
    }

    private String changeStatus(String name, String button, String doneText) throws Exception {
        card(name);
        JsonNode confirmation = press(button);
        for (JsonNode candidate : buttonsOf(confirmation)) {
            String data = candidate.get("callback_data").asString();
            if (data.startsWith("v1:reg-confirm:")) {
                JsonNode reply = press(data);
                return reply.get("text").asString().contains(doneText)
                        ? "SUCCESS"
                        : String.valueOf(textCode(reply.get("text").asString()));
            }
        }
        return String.valueOf(textCode(confirmation.get("text").asString()));
    }

    @Override
    public String inactivateStudent(String name) throws Exception {
        return changeStatus(name, "v1:reg-inactivate:", "foi inativado");
    }

    @Override
    public String reactivateStudent(String name) throws Exception {
        return changeStatus(name, "v1:reg-reactivate:", "foi reativado");
    }
}
