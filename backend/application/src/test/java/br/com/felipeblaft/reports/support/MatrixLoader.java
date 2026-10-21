package br.com.felipeblaft.reports.support;

import br.com.felipeblaft.reports.domain.access.ActionOrigin;
import br.com.felipeblaft.reports.domain.access.Operation;
import br.com.felipeblaft.reports.domain.access.Permission;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Lê a matriz de permissões da feature 005 ({@code contracts/permission-matrix.json}), a fonte
 * normativa dos testes de acesso cruzado e das regras de arquitetura, para que nenhum teste
 * mantenha lista escrita à mão. A localização sobe a partir do diretório de trabalho até achar o
 * arquivo; a variável de ambiente {@code PERMISSION_MATRIX} aponta outro caminho.
 */
public final class MatrixLoader {

    private static final String CONTRACT_RELATIVE =
            "specs/005-admin-registrations-access/contracts/permission-matrix.json";
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final Pattern VARIABLE = Pattern.compile("\\{(\\w+)}");

    private MatrixLoader() {}

    /** Rota Web da matriz: {@code "POST /admin/session"} ou só o caminho (qualquer método). */
    public record Route(String method, String path) {

        public static Route parse(String text) {
            String trimmed = text.strip();
            int space = trimmed.indexOf(' ');
            if (space < 0) {
                return new Route(null, trimmed);
            }
            return new Route(trimmed.substring(0, space), trimmed.substring(space + 1).strip());
        }

        public boolean anyMethod() {
            return method == null;
        }

        public boolean hasVariables() {
            return VARIABLE.matcher(path).find();
        }

        /** Caminho com as variáveis ({@code {studentId}}) trocadas pelos valores informados. */
        public String pathWith(Map<String, String> values) {
            Matcher matcher = VARIABLE.matcher(path);
            StringBuilder result = new StringBuilder();
            while (matcher.find()) {
                String value = values.get(matcher.group(1));
                if (value == null) {
                    throw new IllegalArgumentException(
                            "sem valor para a variável " + matcher.group(1) + " da rota " + path);
                }
                matcher.appendReplacement(result, Matcher.quoteReplacement(value));
            }
            matcher.appendTail(result);
            return result.toString();
        }
    }

    /** Linha da matriz: a permissão de cada ator na operação. */
    public record Row(
            Operation operation,
            Permission administratorWeb,
            Permission teacherWeb,
            Permission teacherTelegram,
            List<Route> webRoutes,
            Set<ActionOrigin> system,
            String note) {

        public Row {
            webRoutes = List.copyOf(webRoutes);
            system = system.isEmpty() ? Set.of() : Set.copyOf(EnumSet.copyOf(system));
        }
    }

    /** A matriz inteira. */
    public record Matrix(List<Row> rows) {

        public Matrix {
            rows = List.copyOf(rows);
        }

        public Row row(Operation operation) {
            return rows.stream()
                    .filter(row -> row.operation() == operation)
                    .findFirst()
                    .orElseThrow(
                            () ->
                                    new IllegalArgumentException(
                                            "operação ausente da matriz: " + operation));
        }

        public List<Operation> operationsWhere(Predicate<Row> filter) {
            return rows.stream().filter(filter).map(Row::operation).toList();
        }

        /** Operações negadas ao professor na Web ou no Telegram (fora do alcance do canal dele). */
        public List<Operation> operationsDeniedToAnyTeacherChannel() {
            return operationsWhere(
                    row ->
                            row.teacherWeb() == Permission.DENY
                                    || row.teacherTelegram() == Permission.DENY);
        }
    }

    /** Carrega o contrato da feature, procurando-o a partir do diretório de trabalho. */
    public static Matrix load() {
        return load(locate());
    }

    public static Matrix load(Path file) {
        if (!Files.isRegularFile(file)) {
            throw new IllegalStateException(
                    "matriz de permissões não encontrada em " + file.toAbsolutePath());
        }
        try {
            return parse(Files.readString(file, StandardCharsets.UTF_8));
        } catch (IOException failure) {
            throw new IllegalStateException(
                    "não foi possível ler a matriz de permissões em " + file.toAbsolutePath(),
                    failure);
        }
    }

    public static Matrix parse(String json) {
        JsonNode operations = JSON.readTree(json).path("operations");
        EnumMap<Operation, Row> rows = new EnumMap<>(Operation.class);
        for (JsonNode node : operations) {
            Row row = toRow(node);
            if (rows.putIfAbsent(row.operation(), row) != null) {
                throw new IllegalArgumentException(
                        "operação repetida na matriz: " + row.operation());
            }
        }
        return new Matrix(new ArrayList<>(rows.values()));
    }

    private static Row toRow(JsonNode node) {
        String name = text(node, "operation", "?");
        Operation operation;
        try {
            operation = Operation.valueOf(name);
        } catch (IllegalArgumentException unknown) {
            throw new IllegalArgumentException("operação desconhecida na matriz: " + name, unknown);
        }
        List<Route> routes = new ArrayList<>();
        for (JsonNode route : node.path("webRoutes")) {
            routes.add(Route.parse(route.asString()));
        }
        Set<ActionOrigin> system = EnumSet.noneOf(ActionOrigin.class);
        for (JsonNode origin : node.path("system")) {
            system.add(ActionOrigin.valueOf(origin.asString()));
        }
        JsonNode note = node.get("note");
        return new Row(
                operation,
                permission(node, "administratorWeb", name),
                permission(node, "teacherWeb", name),
                permission(node, "teacherTelegram", name),
                routes,
                system,
                note == null ? null : note.asString());
    }

    private static Permission permission(JsonNode node, String field, String operation) {
        String value = text(node, field, operation);
        try {
            return Permission.valueOf(value);
        } catch (IllegalArgumentException unknown) {
            throw new IllegalArgumentException(
                    "permissão desconhecida na matriz: " + value + " (" + operation + ")", unknown);
        }
    }

    private static String text(JsonNode node, String field, String operation) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            throw new IllegalArgumentException(
                    "campo ausente na matriz: " + field + " (" + operation + ")");
        }
        return value.asString();
    }

    private static Path locate() {
        String configured = System.getenv("PERMISSION_MATRIX");
        if (configured != null && !configured.isBlank()) {
            return Path.of(configured);
        }
        Path directory = Path.of("").toAbsolutePath();
        while (directory != null) {
            Path candidate = directory.resolve(CONTRACT_RELATIVE);
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
            directory = directory.getParent();
        }
        return Path.of("..", "..", CONTRACT_RELATIVE);
    }
}
