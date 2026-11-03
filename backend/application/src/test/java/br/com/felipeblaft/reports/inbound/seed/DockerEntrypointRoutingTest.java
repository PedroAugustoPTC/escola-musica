package br.com.felipeblaft.reports.inbound.seed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * O {@code docker-entrypoint.sh} é a única porta dos comandos operacionais no contêiner: todo
 * comando que o {@code SeedCommandsExecutor} entende precisa estar roteado para o perfil {@code
 * seed}; um comando fora do {@code case} cairia em {@code exec "$@"} e falharia no contêiner
 * (defeito do {@code rotate-cpf-key}, que só aparecia fora dos testes de integração).
 */
class DockerEntrypointRoutingTest {

    private static final Path ENTRYPOINT = Path.of("docker-entrypoint.sh");
    private static final List<String> COMMANDS =
            List.of(
                    SeedCommand.NAME,
                    IssueCodeCommand.NAME,
                    DemoDataCommand.NAME,
                    RotateCpfKeyCommand.NAME);

    private static String script() throws IOException {
        return Files.readString(ENTRYPOINT);
    }

    /** Alternativas da primeira cláusula do {@code case} (as que ativam o perfil operacional). */
    private static List<String> routedCommands(String script) {
        Matcher clause =
                Pattern.compile("(?m)^\\s*([a-z-]+(?:\\|[a-z-]+)*)\\)\\s*$").matcher(script);
        assertThat(clause.find()).as("cláusula de comandos no case").isTrue();
        return new ArrayList<>(List.of(clause.group(1).split("\\|")));
    }

    @Test
    void everyCommandOfTheExecutorIsRoutedToTheSeedProfile() throws IOException {
        assertThat(routedCommands(script())).containsExactlyInAnyOrderElementsOf(COMMANDS);
    }

    @Test
    void theHeaderCommentNamesEveryCommand() throws IOException {
        String comment =
                script().lines().filter(line -> line.startsWith("#")).reduce("", String::concat);

        for (String command : COMMANDS) {
            assertThat(comment).as("comentário cita " + command).contains(command);
        }
    }

    @Test
    void runningEachCommandStartsTheApplicationInTheSeedProfileWithoutAWebServer(
            @TempDir Path directory) throws Exception {
        assumeTrue(Files.exists(Path.of("/bin/sh")), "precisa de um sh POSIX");
        Path fakeJava = directory.resolve("java");
        Files.writeString(fakeJava, "#!/bin/sh\necho \"JAVA:$*\"\n");
        Files.setPosixFilePermissions(fakeJava, PosixFilePermissions.fromString("rwxr-xr-x"));

        for (String command : COMMANDS) {
            ProcessBuilder builder =
                    new ProcessBuilder(
                                    "/bin/sh",
                                    ENTRYPOINT.toAbsolutePath().toString(),
                                    command,
                                    "--x")
                            .redirectErrorStream(true);
            builder.environment().put("PATH", directory + ":" + System.getenv("PATH"));
            Process process = builder.start();
            String output = new String(process.getInputStream().readAllBytes());
            assertThat(process.waitFor(30, TimeUnit.SECONDS)).isTrue();

            assertThat(output)
                    .as(command)
                    .contains("JAVA:-jar /app/application.jar")
                    .contains("--spring.profiles.active=seed")
                    .contains("--spring.main.web-application-type=none")
                    .contains(command + " --x");
        }
    }
}
