package br.com.felipeblaft.reports.outbound.ai;

import br.com.felipeblaft.reports.port.ProviderUnavailableException;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * Chamadas HTTP aos provedores de IA (R-08): uma nova tentativa com espera em erro transitório
 * (429, 5xx, rede, timeout) e disjuntor simples — após falhas seguidas, recusa por um minuto.
 */
class AiHttpClient {

    static final int FAILURES_TO_OPEN = 5;
    static final Duration BREAKER_OPEN = Duration.ofMinutes(1);

    /** Resposta HTTP bruta de um provedor. */
    record Response(int status, String body) {

        boolean success() {
            return status / 100 == 2;
        }

        boolean isTransient() {
            return status == 429 || status >= 500;
        }
    }

    private final HttpClient client;
    private final Duration newAttemptWait;
    private final Clock clock;
    private final AtomicInteger consecutiveFailures = new AtomicInteger();
    private final AtomicReference<Instant> openUntil = new AtomicReference<>(Instant.MIN);

    AiHttpClient(Duration newAttemptWait) {
        this(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build(),
                newAttemptWait,
                Clock.systemUTC());
    }

    AiHttpClient(HttpClient client, Duration newAttemptWait, Clock clock) {
        this.client = client;
        this.newAttemptWait = newAttemptWait;
        this.clock = clock;
    }

    /**
     * Executa a requisição com uma nova tentativa em erro transitório. Devolve respostas 4xx
     * (exceto 429) para quem chamou decidir; erros transitórios persistentes viram
     * indisponibilidade.
     */
    Response run(Supplier<HttpRequest> request) {
        if (clock.instant().isBefore(openUntil.get())) {
            throw new ProviderUnavailableException("disjuntor-aberto");
        }
        for (int attempt = 1; ; attempt++) {
            Response response;
            try {
                HttpResponse<String> http =
                        client.send(
                                request.get(),
                                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                response = new Response(http.statusCode(), http.body());
            } catch (IOException error) {
                response = null;
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                throw new ProviderUnavailableException("interrompido");
            }
            if (response != null && !response.isTransient()) {
                consecutiveFailures.set(0);
                return response;
            }
            if (attempt >= 2) {
                if (consecutiveFailures.incrementAndGet() >= FAILURES_TO_OPEN) {
                    openUntil.set(clock.instant().plus(BREAKER_OPEN));
                    consecutiveFailures.set(0);
                }
                throw new ProviderUnavailableException(
                        response == null ? "rede" : "status-" + response.status());
            }
            wait(newAttemptWait);
        }
    }

    private static void wait(Duration duration) {
        try {
            Thread.sleep(duration);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
        }
    }

    static HttpRequest.Builder base(String url, String key, Duration timeout) {
        HttpRequest.Builder constructor = HttpRequest.newBuilder(URI.create(url)).timeout(timeout);
        if (key != null && !key.isBlank()) {
            constructor.header("Authorization", "Bearer " + key);
        }
        return constructor;
    }

    static String modelProvider(String url, String model) {
        String host = URI.create(url).getHost();
        return (host == null ? "local" : host) + "/" + model;
    }
}
