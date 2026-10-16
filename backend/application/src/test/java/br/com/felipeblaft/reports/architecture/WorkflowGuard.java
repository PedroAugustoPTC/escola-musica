package br.com.felipeblaft.reports.architecture;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Regras estáticas dos workflows do n8n (feature 004, research R-05): o n8n só transporta. Lê os
 * JSON de uma pasta e devolve as violações; a mensagem cita o arquivo, o nó e o caminho JSON, e
 * nunca o valor encontrado (o valor pode ser um segredo).
 *
 * <p>Cada violação começa por um código estável entre colchetes, que o autoteste confere.
 */
public final class WorkflowGuard {

    public static final Set<String> INVENTORY =
            Set.of("telegram-inbound", "channel-delivery", "execute-actions", "transport-health");

    static final Set<String> ALLOWED_NODES =
            Set.of(
                    "webhook",
                    "respondToWebhook",
                    "httpRequest",
                    "code",
                    "if",
                    "switch",
                    "splitInBatches",
                    "set",
                    "noOp",
                    "executeWorkflow",
                    "executeWorkflowTrigger");

    static final Set<String> ALLOWED_ENV =
            Set.of(
                    "BACKEND_INTERNAL_URL",
                    "TELEGRAM_API_URL",
                    "TELEGRAM_BOT_TOKEN",
                    "BACKEND_SERVICE_TOKEN",
                    "CHANNEL_DELIVERY_TOKEN");

    /** Último segmento permitido nas referências lidas por {@code If} e {@code Switch} (W4). */
    static final Set<String> BRANCH_FIELDS =
            Set.of(
                    "statusCode",
                    "type",
                    "state",
                    "outcome",
                    "ok",
                    "status",
                    "backend",
                    "botApi",
                    "error",
                    "reasonCode",
                    "x-channel-delivery-token",
                    "CHANNEL_DELIVERY_TOKEN");

    private static final Pattern ENV_REFERENCE =
            Pattern.compile(
                    "\\$env(?:\\.([A-Za-z_][A-Za-z0-9_]*)|\\['([^']*)'\\]|\\[\"([^\"]*)\"\\])");
    private static final Pattern PROPERTY_CHAIN =
            Pattern.compile(
                    "(?:\\$json|\\.json|\\$env)((?:\\.[A-Za-z_][A-Za-z0-9_]*|\\['[^']*'\\]|\\[\"[^\"]*\"\\])+)");
    private static final Pattern LAST_SEGMENT =
            Pattern.compile("(?:\\.([A-Za-z_][A-Za-z0-9_]*)|\\['([^']*)'\\]|\\[\"([^\"]*)\"\\])$");
    private static final Pattern ALLOWED_DESTINATION =
            Pattern.compile("^=?\\{\\{ \\$env\\.(BACKEND_INTERNAL_URL|TELEGRAM_API_URL) \\}\\}.*");
    private static final Pattern CODE_FORBIDDEN =
            Pattern.compile(
                    "\\$env|\\bprocess\\b|\\brequire\\b|\\bimport\\b|\\bfetch\\b|\\beval\\b|\\bFunction\\s*\\(|\\bglobalThis\\b|\\bchild_process\\b");
    private static final Pattern CODE_COMMENT = Pattern.compile("(^|[\\s;])//|/\\*");
    private static final Pattern ACCENTED =
            Pattern.compile(".*[\\u00C0-\\u00FF].*", Pattern.DOTALL);

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private WorkflowGuard() {}

    /**
     * Verifica todos os {@code *.json} da pasta. {@code required} são os arquivos (sem extensão)
     * que precisam existir; as fases da feature vão ampliando essa lista.
     */
    public static List<String> check(Path directory, Set<String> required) {
        List<String> violations = new ArrayList<>();
        Set<String> found = new TreeSet<>();
        int updateReceivers = 0;
        List<Path> files;
        try (Stream<Path> stream = Files.list(directory)) {
            files =
                    stream.filter(p -> p.getFileName().toString().endsWith(".json"))
                            .sorted()
                            .toList();
        } catch (IOException e) {
            throw new IllegalStateException("Falha ao listar " + directory, e);
        }
        for (Path file : files) {
            String fileName = file.getFileName().toString();
            String stem = fileName.substring(0, fileName.length() - ".json".length());
            found.add(stem);
            JsonNode root;
            try {
                root = JSON.readTree(Files.readString(file));
            } catch (Exception e) {
                violations.add(
                        message("INVALID_JSON", fileName, "$", "o arquivo não é um JSON válido"));
                continue;
            }
            if (!INVENTORY.contains(stem)) {
                violations.add(
                        message(
                                "UNKNOWN_FILE",
                                fileName,
                                "$",
                                "workflow fora do inventário permitido (quatro arquivos)"));
            }
            if (!stem.equals(root.path("id").asString())) {
                violations.add(
                        message(
                                "ID_MISMATCH",
                                fileName,
                                "$.id",
                                "o id precisa ser igual ao nome do arquivo"));
            }
            scanForbiddenKeys(root, fileName, "$", violations);
            JsonNode nodes = root.path("nodes");
            for (int i = 0; i < nodes.size(); i++) {
                JsonNode node = nodes.get(i);
                String where = "$.nodes[" + i + "]";
                String name = node.path("name").asString();
                String type = node.path("type").asString();
                String kind = type.startsWith("n8n-nodes-base.") ? type.substring(15) : type;
                String label = fileName + " nó '" + name + "'";
                if (!type.startsWith("n8n-nodes-base.") || !ALLOWED_NODES.contains(kind)) {
                    String code =
                            kind.equals("telegramTrigger") ? "UPDATE_RECEIVER" : "NODE_NOT_ALLOWED";
                    violations.add(
                            message(code, label, where + ".type", "tipo de nó não permitido"));
                    continue;
                }
                if (kind.equals("webhook")
                        && "telegram-update"
                                .equals(node.path("parameters").path("path").asString())) {
                    updateReceivers++;
                    if (!stem.equals("telegram-inbound")) {
                        violations.add(
                                message(
                                        "UPDATE_RECEIVER",
                                        label,
                                        where + ".parameters.path",
                                        "receptor de update fora do telegram-inbound"));
                    }
                }
                checkStrings(
                        node.path("parameters"), kind, label, where + ".parameters", violations);
                if (kind.equals("httpRequest")) {
                    String url = node.path("parameters").path("url").asString();
                    if (!ALLOWED_DESTINATION.matcher(url).matches()) {
                        violations.add(
                                message(
                                        "DESTINATION",
                                        label,
                                        where + ".parameters.url",
                                        "destino diferente do backend e do servidor de bots"));
                    }
                }
                if (kind.equals("code")) {
                    String js = node.path("parameters").path("jsCode").asString();
                    if (CODE_FORBIDDEN.matcher(js).find()) {
                        violations.add(
                                message(
                                        "CODE_FORBIDDEN",
                                        label,
                                        where + ".parameters.jsCode",
                                        "código com acesso a ambiente, módulo ou rede"));
                    }
                    if (CODE_COMMENT.matcher(js).find()) {
                        violations.add(
                                message(
                                        "FREE_TEXT",
                                        label,
                                        where + ".parameters.jsCode",
                                        "comentário no código (a documentação mora no contrato)"));
                    }
                }
                if (kind.equals("if") || kind.equals("switch")) {
                    checkBranching(
                            node.path("parameters"), label, where + ".parameters", violations);
                }
            }
        }
        if (updateReceivers > 1) {
            violations.add(
                    message(
                            "UPDATE_RECEIVER",
                            "conjunto de workflows",
                            "$",
                            "mais de um receptor de update do Telegram"));
        }
        for (String name : required) {
            if (!found.contains(name)) {
                violations.add(
                        message(
                                "MISSING_FILE",
                                name + ".json",
                                "$",
                                "workflow obrigatório ausente"));
            }
        }
        return violations;
    }

    private static void scanForbiddenKeys(
            JsonNode node, String fileName, String path, List<String> violations) {
        if (node.isObject()) {
            for (Map.Entry<String, JsonNode> entry : node.properties()) {
                String key = entry.getKey();
                String child = path + "." + key;
                if (key.equals("credentials") || key.equals("credentialId")) {
                    violations.add(
                            message(
                                    "CREDENTIALS",
                                    fileName,
                                    child,
                                    "chave de credencial é proibida"));
                } else if (key.equals("notes") && !entry.getValue().asString().isEmpty()) {
                    violations.add(message("FREE_TEXT", fileName, child, "nota com texto livre"));
                }
                scanForbiddenKeys(entry.getValue(), fileName, child, violations);
            }
        } else if (node.isArray()) {
            for (int i = 0; i < node.size(); i++) {
                scanForbiddenKeys(node.get(i), fileName, path + "[" + i + "]", violations);
            }
        }
    }

    /** Textos livres, {@code getFile} e {@code $env} fora da lista, em qualquer valor do nó. */
    private static void checkStrings(
            JsonNode node, String kind, String label, String path, List<String> violations) {
        if (node.isString()) {
            String text = node.asString();
            if (text.contains("getFile") || text.contains("/file/bot")) {
                violations.add(
                        message("GET_FILE", label, path, "o áudio nunca passa pelo n8n (getFile)"));
            }
            if (ACCENTED.matcher(text).matches()) {
                violations.add(message("FREE_TEXT", label, path, "texto livre com acentuação"));
            }
            if (!kind.equals("code")) {
                Matcher matcher = ENV_REFERENCE.matcher(text);
                while (matcher.find()) {
                    String variable =
                            matcher.group(1) != null
                                    ? matcher.group(1)
                                    : matcher.group(2) != null
                                            ? matcher.group(2)
                                            : matcher.group(3);
                    if (!ALLOWED_ENV.contains(variable)) {
                        violations.add(
                                message(
                                        "ENV_NOT_ALLOWED",
                                        label,
                                        path,
                                        "variável de ambiente fora da lista"));
                    }
                }
            }
        } else if (node.isObject()) {
            for (Map.Entry<String, JsonNode> entry : node.properties()) {
                checkStrings(
                        entry.getValue(), kind, label, path + "." + entry.getKey(), violations);
            }
        } else if (node.isArray()) {
            for (int i = 0; i < node.size(); i++) {
                checkStrings(node.get(i), kind, label, path + "[" + i + "]", violations);
            }
        }
    }

    /** {@code If} e {@code Switch} só leem tipo, estado, status, resultado e o token de entrega. */
    private static void checkBranching(
            JsonNode node, String label, String path, List<String> violations) {
        if (node.isString()) {
            Matcher chain = PROPERTY_CHAIN.matcher(node.asString());
            while (chain.find()) {
                Matcher last = LAST_SEGMENT.matcher(chain.group(1));
                if (last.find()) {
                    String field =
                            last.group(1) != null
                                    ? last.group(1)
                                    : last.group(2) != null ? last.group(2) : last.group(3);
                    if (!BRANCH_FIELDS.contains(field)) {
                        violations.add(
                                message(
                                        "BRANCH_ON_CONTENT",
                                        label,
                                        path,
                                        "ramificação sobre campo que não é tipo, estado, status, resultado nem token"));
                    }
                }
            }
        } else if (node.isObject()) {
            for (Map.Entry<String, JsonNode> entry : node.properties()) {
                checkBranching(entry.getValue(), label, path + "." + entry.getKey(), violations);
            }
        } else if (node.isArray()) {
            for (int i = 0; i < node.size(); i++) {
                checkBranching(node.get(i), label, path + "[" + i + "]", violations);
            }
        }
    }

    private static String message(String code, String subject, String path, String reason) {
        return "[" + code + "] " + subject + " (" + path + "): " + reason;
    }
}
