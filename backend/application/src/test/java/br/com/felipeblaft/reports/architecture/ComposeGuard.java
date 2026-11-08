package br.com.felipeblaft.reports.architecture;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.yaml.snakeyaml.Yaml;

/**
 * Regras estáticas do compose e do {@code Dockerfile} do n8n (feature 004, R-11, restrição de
 * contêiner da constituição): rotação de logs em todo serviço da base, imagem fixada, sem porta
 * pública, só na rede interna, reinício em produção, editor desligado fora do ambiente local e
 * nenhum segredo com valor literal.
 *
 * <p>Cada violação começa por um código estável entre colchetes; nunca traz o valor encontrado.
 */
final class ComposeGuard {

    static final String BASE = "compose.yaml";
    static final String DEV = "compose.dev.yaml";
    static final String PROD = "compose.prod.yaml";

    private static final Set<String> FLOATING_TAGS =
            Set.of("latest", "stable", "next", "edge", "nightly");
    private static final Pattern SECRET_NAME =
            Pattern.compile(".*(TOKEN|SECRET|KEY|PASSWORD|HASH).*");
    private static final Pattern EXACT_VERSION = Pattern.compile("\\d+\\.\\d+\\.\\d+");
    private static final Pattern FROM_LINE = Pattern.compile("^FROM\\s+(\\S+)", Pattern.MULTILINE);

    private ComposeGuard() {}

    /**
     * @param files conteúdo dos arquivos {@code compose*.yaml} pelo nome
     * @param dockerfile conteúdo de {@code infra/n8n/Dockerfile}, ou {@code null} para não conferir
     */
    static List<String> check(Map<String, String> files, String dockerfile) {
        List<String> violations = new ArrayList<>();
        Map<String, Map<String, Object>> services = new TreeMap<>();
        for (Map.Entry<String, String> file : files.entrySet()) {
            Object root = new Yaml().load(file.getValue());
            Map<String, Object> declared = map(map(root).get("services"));
            services.put(file.getKey(), declared);
            checkImages(file.getKey(), declared, violations);
        }
        checkLogRotation(services, violations);
        checkN8n(files, services, violations);
        if (dockerfile != null) {
            checkDockerfile(dockerfile, violations);
        }
        return violations;
    }

    private static void checkImages(
            String file, Map<String, Object> services, List<String> violations) {
        for (Map.Entry<String, Object> entry : services.entrySet()) {
            Map<String, Object> service = map(entry.getValue());
            Object image = service.get("image");
            if (image == null || service.containsKey("build")) {
                continue;
            }
            String reference = image.toString();
            int colon = reference.lastIndexOf(':');
            boolean hasTag = colon > reference.lastIndexOf('/');
            String where = file + " serviço '" + entry.getKey() + "'";
            if (!hasTag) {
                violations.add(message("IMAGE_UNTAGGED", where, "imagem sem tag de versão"));
            } else if (FLOATING_TAGS.contains(reference.substring(colon + 1))) {
                violations.add(
                        message("IMAGE_FLOATING", where, "tag flutuante (latest, stable, next)"));
            }
        }
    }

    /**
     * Todo serviço da base roda com rotação de logs (feature 007, R-08): driver, tamanho e
     * arquivos.
     */
    private static void checkLogRotation(
            Map<String, Map<String, Object>> services, List<String> violations) {
        for (Map.Entry<String, Object> entry : services.getOrDefault(BASE, Map.of()).entrySet()) {
            Map<String, Object> logging = map(map(entry.getValue()).get("logging"));
            Map<String, Object> options = map(logging.get("options"));
            boolean valid =
                    "json-file".equals(String.valueOf(logging.get("driver")))
                            && options.get("max-size") != null
                            && positive(options.get("max-file"));
            if (!valid) {
                violations.add(
                        message(
                                "LOG_ROTATION",
                                BASE + " serviço '" + entry.getKey() + "'",
                                "falta logging json-file com max-size e max-file maior que zero"));
            }
        }
    }

    private static boolean positive(Object value) {
        try {
            return value != null && Integer.parseInt(value.toString()) > 0;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static void checkN8n(
            Map<String, String> files,
            Map<String, Map<String, Object>> services,
            List<String> violations) {
        Map<String, Object> base = map(services.getOrDefault(BASE, Map.of()).get("n8n"));
        if (base.isEmpty()) {
            violations.add(
                    message("N8N_MISSING", BASE, "o serviço n8n não está na base do compose"));
            return;
        }
        for (Map.Entry<String, Map<String, Object>> file : services.entrySet()) {
            Map<String, Object> service = map(file.getValue().get("n8n"));
            if (service.isEmpty()) {
                continue;
            }
            boolean dev = file.getKey().equals(DEV);
            for (Object port : list(service.get("ports"))) {
                String text =
                        port instanceof Map<?, ?> m
                                ? String.valueOf(m.get("published"))
                                : port.toString();
                boolean loopback = text.startsWith("127.0.0.1:") || text.contains("host_ip");
                if (!(dev && loopback)) {
                    violations.add(
                            message(
                                    "N8N_PORTS",
                                    file.getKey() + " serviço 'n8n'",
                                    "porta publicada (só o editor, em 127.0.0.1, no compose.dev.yaml)"));
                }
            }
        }
        List<Object> networks = networks(base.get("networks"));
        if (!networks.contains("internal") || networks.size() != 1) {
            violations.add(
                    message(
                            "N8N_NETWORK",
                            BASE + " serviço 'n8n'",
                            "o n8n precisa estar só na rede internal"));
        }
        if (!"true".equals(env(base).get("N8N_DISABLE_UI"))) {
            violations.add(
                    message(
                            "N8N_UI_ENABLED",
                            BASE + " serviço 'n8n'",
                            "N8N_DISABLE_UI diferente de true na base"));
        }
        Map<String, Object> prod = map(services.getOrDefault(PROD, Map.of()).get("n8n"));
        if (!"unless-stopped".equals(String.valueOf(prod.get("restart")))) {
            violations.add(
                    message(
                            "N8N_NO_RESTART",
                            PROD + " serviço 'n8n'",
                            "falta restart: unless-stopped"));
        }
        if (prod.containsKey("environment") && !"true".equals(env(prod).get("N8N_DISABLE_UI"))) {
            violations.add(
                    message(
                            "N8N_UI_ENABLED",
                            PROD + " serviço 'n8n'",
                            "N8N_DISABLE_UI diferente de true em produção"));
        }
        for (String name : List.of(BASE, DEV, PROD)) {
            if (!files.containsKey(name)) {
                continue;
            }
            Map<String, Object> service = map(services.get(name).get("n8n"));
            for (Map.Entry<String, String> variable : env(service).entrySet()) {
                String value = variable.getValue();
                if (SECRET_NAME.matcher(variable.getKey()).matches()
                        && !variable.getKey().equals("N8N_BLOCK_ENV_ACCESS_IN_NODE")
                        && !value.isEmpty()
                        && !value.startsWith("${")) {
                    violations.add(
                            message(
                                    "LITERAL_SECRET",
                                    name + " serviço 'n8n' variável " + variable.getKey(),
                                    "variável secreta com valor literal (use ${...} do infra/.env)"));
                }
            }
        }
    }

    private static void checkDockerfile(String dockerfile, List<String> violations) {
        Matcher from = FROM_LINE.matcher(dockerfile);
        boolean any = false;
        while (from.find()) {
            any = true;
            String reference = from.group(1);
            int colon = reference.lastIndexOf(':');
            boolean exact =
                    colon > reference.lastIndexOf('/')
                            && EXACT_VERSION.matcher(reference.substring(colon + 1)).matches();
            if (!exact) {
                violations.add(
                        message(
                                "DOCKERFILE_FROM",
                                "infra/n8n/Dockerfile",
                                "FROM sem versão exata (X.Y.Z)"));
            }
        }
        if (!any) {
            violations.add(
                    message("DOCKERFILE_FROM", "infra/n8n/Dockerfile", "nenhuma linha FROM"));
        }
    }

    private static Map<String, String> env(Map<String, Object> service) {
        Map<String, String> result = new TreeMap<>();
        Object environment = service.get("environment");
        if (environment instanceof Map<?, ?> m) {
            m.forEach((k, v) -> result.put(String.valueOf(k), v == null ? "" : String.valueOf(v)));
        } else if (environment instanceof List<?> l) {
            for (Object item : l) {
                String text = String.valueOf(item);
                int equals = text.indexOf('=');
                result.put(
                        equals < 0 ? text : text.substring(0, equals),
                        equals < 0 ? "" : text.substring(equals + 1));
            }
        }
        return result;
    }

    private static List<Object> networks(Object value) {
        if (value instanceof Map<?, ?> m) {
            return new ArrayList<>(m.keySet());
        }
        return list(value);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value) {
        return value instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
    }

    @SuppressWarnings("unchecked")
    private static List<Object> list(Object value) {
        return value instanceof List<?> l ? (List<Object>) l : List.of();
    }

    private static String message(String code, String subject, String reason) {
        return "[" + code + "] " + subject + ": " + reason;
    }
}
