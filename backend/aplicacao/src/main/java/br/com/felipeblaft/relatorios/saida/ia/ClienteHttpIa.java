package br.com.felipeblaft.relatorios.saida.ia;

import br.com.felipeblaft.relatorios.porta.ProvedorIndisponivelExcecao;
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
class ClienteHttpIa {

    static final int FALHAS_PARA_ABRIR = 5;
    static final Duration DISJUNTOR_ABERTO = Duration.ofMinutes(1);

    /** Resposta HTTP bruta de um provedor. */
    record Resposta(int status, String corpo) {

        boolean sucesso() {
            return status / 100 == 2;
        }

        boolean transitoria() {
            return status == 429 || status >= 500;
        }
    }

    private final HttpClient cliente;
    private final Duration esperaNovaTentativa;
    private final Clock relogio;
    private final AtomicInteger falhasSeguidas = new AtomicInteger();
    private final AtomicReference<Instant> abertoAte = new AtomicReference<>(Instant.MIN);

    ClienteHttpIa(Duration esperaNovaTentativa) {
        this(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build(),
                esperaNovaTentativa,
                Clock.systemUTC());
    }

    ClienteHttpIa(HttpClient cliente, Duration esperaNovaTentativa, Clock relogio) {
        this.cliente = cliente;
        this.esperaNovaTentativa = esperaNovaTentativa;
        this.relogio = relogio;
    }

    /**
     * Executa a requisição com uma nova tentativa em erro transitório. Devolve respostas 4xx
     * (exceto 429) para quem chamou decidir; erros transitórios persistentes viram
     * indisponibilidade.
     */
    Resposta executar(Supplier<HttpRequest> requisicao) {
        if (relogio.instant().isBefore(abertoAte.get())) {
            throw new ProvedorIndisponivelExcecao("disjuntor-aberto");
        }
        for (int tentativa = 1; ; tentativa++) {
            Resposta resposta;
            try {
                HttpResponse<String> http =
                        cliente.send(
                                requisicao.get(),
                                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                resposta = new Resposta(http.statusCode(), http.body());
            } catch (IOException erro) {
                resposta = null;
            } catch (InterruptedException erro) {
                Thread.currentThread().interrupt();
                throw new ProvedorIndisponivelExcecao("interrompido");
            }
            if (resposta != null && !resposta.transitoria()) {
                falhasSeguidas.set(0);
                return resposta;
            }
            if (tentativa >= 2) {
                if (falhasSeguidas.incrementAndGet() >= FALHAS_PARA_ABRIR) {
                    abertoAte.set(relogio.instant().plus(DISJUNTOR_ABERTO));
                    falhasSeguidas.set(0);
                }
                throw new ProvedorIndisponivelExcecao(
                        resposta == null ? "rede" : "status-" + resposta.status());
            }
            esperar(esperaNovaTentativa);
        }
    }

    private static void esperar(Duration duracao) {
        try {
            Thread.sleep(duracao);
        } catch (InterruptedException erro) {
            Thread.currentThread().interrupt();
        }
    }

    static HttpRequest.Builder base(String url, String chave, Duration timeout) {
        HttpRequest.Builder construtor = HttpRequest.newBuilder(URI.create(url)).timeout(timeout);
        if (chave != null && !chave.isBlank()) {
            construtor.header("Authorization", "Bearer " + chave);
        }
        return construtor;
    }

    static String provedorModelo(String url, String modelo) {
        String host = URI.create(url).getHost();
        return (host == null ? "local" : host) + "/" + modelo;
    }
}
