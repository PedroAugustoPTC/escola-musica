package br.com.felipeblaft.reports.support;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

/**
 * Apoio dos testes que exercitam o n8n real em contêiner (feature 004): endereços, chamadas HTTP e
 * controle do contêiner pelo {@code docker} CLI (o socket do Docker só é montado no {@code
 * tests-n8n}). Os testes ficam marcados com {@code @Tag("n8n")} e rodam só no perfil {@code n8n}.
 */
public final class N8nTestSupport {

    /** O n8n sob teste (rede do {@code compose.test.yaml}). */
    public static final String N8N_URL = environment("N8N_URL", "http://n8n-test:5678");

    /**
     * Entrada pública simulada (Caddy com o Caddyfile real), para provar que o n8n não é roteado.
     */
    public static final String WEB_URL = environment("WEB_URL", "https://web-n8n-test");

    public static final String N8N_CONTAINER = environment("N8N_CONTAINER", "reports-n8n-test");

    /** Porta fixa do backend simulado nos testes de contrato dos workflows. */
    public static final int BACKEND_STUB_PORT = 18080;

    /** Porta fixa da API de bots simulada. */
    public static final int BOT_API_STUB_PORT = 18081;

    private static final HttpClient PLAIN =
            HttpClient.newBuilder()
                    .version(HttpClient.Version.HTTP_1_1)
                    .connectTimeout(Duration.ofSeconds(5))
                    .build();

    private N8nTestSupport() {}

    public record Reply(int status, String body, String contentType) {}

    public record CommandResult(int exitCode, String output) {}

    public static String environment(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }

    // ---------------------------------------------------------------------------------------------
    // HTTP
    // ---------------------------------------------------------------------------------------------

    public static Reply get(String url) {
        return send(PLAIN, HttpRequest.newBuilder(URI.create(url)).GET());
    }

    public static Reply post(String url, String contentType, String body) {
        return send(
                PLAIN,
                HttpRequest.newBuilder(URI.create(url))
                        .header("Content-Type", contentType)
                        .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8)));
    }

    /** POST com cabeçalhos extras (o segredo do webhook do Telegram, por exemplo). */
    public static Reply post(
            String url, java.util.Map<String, String> headers, String contentType, byte[] body) {
        HttpRequest.Builder request =
                HttpRequest.newBuilder(URI.create(url)).header("Content-Type", contentType);
        headers.forEach(request::header);
        return send(PLAIN, request.POST(HttpRequest.BodyPublishers.ofByteArray(body)));
    }

    /** Aceita o certificado local do Caddy (a CA interna não é conhecida pela JVM). */
    public static Reply insecureGet(String url) {
        return send(insecureClient(), HttpRequest.newBuilder(URI.create(url)).GET());
    }

    public static Reply insecurePost(String url, String contentType, String body) {
        return send(
                insecureClient(),
                HttpRequest.newBuilder(URI.create(url))
                        .header("Content-Type", contentType)
                        .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8)));
    }

    private static Reply send(HttpClient client, HttpRequest.Builder request) {
        try {
            HttpResponse<String> response =
                    client.send(
                            request.timeout(Duration.ofSeconds(20)).build(),
                            HttpResponse.BodyHandlers.ofString());
            return new Reply(
                    response.statusCode(),
                    response.body(),
                    response.headers().firstValue("content-type").orElse(""));
        } catch (IOException e) {
            throw new IllegalStateException("Falha de rede na chamada de teste", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Chamada de teste interrompida", e);
        }
    }

    private static HttpClient insecureClient() {
        try {
            TrustManager trustAll =
                    new X509TrustManager() {
                        @Override
                        public void checkClientTrusted(X509Certificate[] chain, String authType) {}

                        @Override
                        public void checkServerTrusted(X509Certificate[] chain, String authType) {}

                        @Override
                        public X509Certificate[] getAcceptedIssuers() {
                            return new X509Certificate[0];
                        }
                    };
            SSLContext context = SSLContext.getInstance("TLS");
            context.init(null, new TrustManager[] {trustAll}, new SecureRandom());
            return HttpClient.newBuilder()
                    .version(HttpClient.Version.HTTP_1_1)
                    .sslContext(context)
                    .connectTimeout(Duration.ofSeconds(5))
                    .build();
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao preparar o cliente HTTPS de teste", e);
        }
    }

    /** Espera o endereço responder (qualquer status); falha de conexão conta como "ainda não". */
    public static void awaitUrl(String url, boolean insecure, Duration timeout) {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            try {
                insecureOrPlain(url, insecure);
                return;
            } catch (IllegalStateException notYet) {
                sleep(1000);
            }
        }
        throw new IllegalStateException("O endereço não respondeu a tempo: " + url);
    }

    private static Reply insecureOrPlain(String url, boolean insecure) {
        return insecure ? insecureGet(url) : get(url);
    }

    // ---------------------------------------------------------------------------------------------
    // Contêiner (docker CLI)
    // ---------------------------------------------------------------------------------------------

    public static CommandResult docker(String... arguments) {
        List<String> command = new ArrayList<>();
        command.add("docker");
        command.addAll(List.of(arguments));
        try {
            Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
            String output =
                    new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            return new CommandResult(process.waitFor(), output);
        } catch (IOException e) {
            throw new IllegalStateException("Falha ao executar o docker CLI", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Comando docker interrompido", e);
        }
    }

    /** Executa um comando dentro do contêiner do n8n. */
    public static CommandResult execInN8n(String... command) {
        List<String> arguments = new ArrayList<>(List.of("exec", N8N_CONTAINER));
        arguments.addAll(List.of(command));
        return docker(arguments.toArray(new String[0]));
    }

    public static String healthStatus() {
        return docker("inspect", "--format", "{{.State.Health.Status}}", N8N_CONTAINER)
                .output()
                .trim();
    }

    public static void awaitHealthy(Duration timeout) {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            if ("healthy".equals(healthStatus())) {
                return;
            }
            sleep(1000);
        }
        throw new IllegalStateException(
                "O n8n não ficou saudável a tempo (estado: " + healthStatus() + ")");
    }

    public static void restartN8n() {
        docker("restart", N8N_CONTAINER);
    }

    /** Derruba o contêiner sem parada limpa (queda), como falta de energia ou {@code kill -9}. */
    public static void killN8n() {
        docker("kill", N8N_CONTAINER);
    }

    public static void startN8n() {
        docker("start", N8N_CONTAINER);
    }

    /** O contêiner do n8n está em execução? */
    public static boolean isRunning() {
        return "true"
                .equals(
                        docker("inspect", "--format", "{{.State.Running}}", N8N_CONTAINER)
                                .output()
                                .trim());
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Espera interrompida", e);
        }
    }
}
