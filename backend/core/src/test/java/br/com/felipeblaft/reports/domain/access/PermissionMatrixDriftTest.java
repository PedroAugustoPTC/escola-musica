package br.com.felipeblaft.reports.domain.access;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * Compara a {@link PermissionMatrix} com {@code contracts/permission-matrix.json}, linha a linha,
 * nos quatro atores: administrador na Web, professor na Web, professor no Telegram e sem credencial
 * (SC-005); inclui as origens de sistema. A leitura é por expressão regular porque o núcleo não tem
 * biblioteca de JSON; cada operação do arquivo é um objeto plano e o campo {@code system} é o
 * último que o teste lê (as rotas trazem chaves, como {@code {teacherId}}).
 */
class PermissionMatrixDriftTest {

    private static final Path CONTRACT =
            Path.of("../../specs/005-admin-registrations-access/contracts/permission-matrix.json");
    private static final Pattern OPERATION_OBJECT =
            Pattern.compile(
                    "\\{\\s*\"operation\"\\s*:\\s*\"(\\w+)\"(.*?\"system\"\\s*:\\s*\\[[^\\]]*\\])",
                    Pattern.DOTALL);

    private record Line(
            Operation operation,
            Permission administratorWeb,
            Permission teacherWeb,
            Permission teacherTelegram,
            Set<ActionOrigin> system) {}

    private final PermissionMatrix matrix = PermissionMatrix.standard();
    private final UUID id = UUID.randomUUID();

    @Test
    void contractListsExactlyTheOperationsOfTheCore() throws IOException {
        List<Operation> operations = read().stream().map(Line::operation).toList();
        assertThat(operations)
                .doesNotHaveDuplicates()
                .containsExactlyInAnyOrder(Operation.values());
    }

    @Test
    void administratorWebMatchesTheContract() throws IOException {
        Actor actor = Actor.administrator(id, false);
        for (Line line : read()) {
            assertThat(matrix.decide(line.operation(), actor))
                    .as("administratorWeb de %s", line.operation())
                    .isEqualTo(line.administratorWeb());
        }
    }

    @Test
    void teacherWebMatchesTheContract() throws IOException {
        Actor actor = Actor.teacher(id, ActionOrigin.WEB);
        for (Line line : read()) {
            assertThat(matrix.decide(line.operation(), actor))
                    .as("teacherWeb de %s", line.operation())
                    .isEqualTo(line.teacherWeb());
        }
    }

    @Test
    void teacherTelegramMatchesTheContract() throws IOException {
        Actor actor = Actor.teacher(id, ActionOrigin.TELEGRAM);
        for (Line line : read()) {
            assertThat(matrix.decide(line.operation(), actor))
                    .as("teacherTelegram de %s", line.operation())
                    .isEqualTo(line.teacherTelegram());
        }
    }

    @Test
    void callerWithoutCredentialOnlyReachesPublicOperations() throws IOException {
        for (Line line : read()) {
            boolean publicOperation =
                    line.administratorWeb() == Permission.PUBLIC
                            || line.teacherWeb() == Permission.PUBLIC
                            || line.teacherTelegram() == Permission.PUBLIC;
            assertThat(matrix.decideAnonymous(line.operation()))
                    .as("sem credencial em %s", line.operation())
                    .isEqualTo(publicOperation ? Permission.PUBLIC : Permission.DENY);
        }
    }

    @Test
    void systemActorMatchesTheContractForEachSystemOrigin() throws IOException {
        for (Line line : read()) {
            for (ActionOrigin origin :
                    EnumSet.of(ActionOrigin.SYSTEM_STARTUP, ActionOrigin.INITIAL_LOAD)) {
                assertThat(matrix.decide(line.operation(), Actor.system(origin)))
                        .as("sistema %s em %s", origin, line.operation())
                        .isEqualTo(
                                line.system().contains(origin)
                                        ? Permission.ALLOW
                                        : Permission.DENY);
            }
        }
    }

    private static List<Line> read() throws IOException {
        String json = Files.readString(CONTRACT, StandardCharsets.UTF_8);
        List<Line> lines = new ArrayList<>();
        Matcher matcher = OPERATION_OBJECT.matcher(json);
        while (matcher.find()) {
            String body = matcher.group(2);
            lines.add(
                    new Line(
                            Operation.valueOf(matcher.group(1)),
                            Permission.valueOf(text(body, "administratorWeb")),
                            Permission.valueOf(text(body, "teacherWeb")),
                            Permission.valueOf(text(body, "teacherTelegram")),
                            origins(body)));
        }
        assertThat(lines).as("operações lidas de %s", CONTRACT).isNotEmpty();
        return lines;
    }

    private static String text(String body, String field) {
        Matcher matcher = Pattern.compile("\"" + field + "\"\\s*:\\s*\"(\\w+)\"").matcher(body);
        assertThat(matcher.find()).as("campo %s", field).isTrue();
        return matcher.group(1);
    }

    private static Set<ActionOrigin> origins(String body) {
        Matcher matcher = Pattern.compile("\"system\"\\s*:\\s*\\[([^\\]]*)\\]").matcher(body);
        assertThat(matcher.find()).as("campo system").isTrue();
        Set<ActionOrigin> origins = EnumSet.noneOf(ActionOrigin.class);
        Matcher name = Pattern.compile("\"(\\w+)\"").matcher(matcher.group(1));
        while (name.find()) {
            origins.add(ActionOrigin.valueOf(name.group(1)));
        }
        return origins;
    }
}
