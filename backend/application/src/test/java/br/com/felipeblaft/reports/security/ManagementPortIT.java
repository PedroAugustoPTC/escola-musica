package br.com.felipeblaft.reports.security;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.felipeblaft.reports.support.IntegrationBase;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;

/**
 * SEC-002: as métricas e a saúde saem só pela porta de gerenciamento, presa ao loopback do
 * contêiner; a porta da API (a que a rede interna alcança) não as serve.
 */
class ManagementPortIT extends IntegrationBase {

    @Value("${local.management.port}")
    int managementPort;

    private static int get(int port, String path) throws Exception {
        return HttpClient.newHttpClient()
                .send(
                        HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                                .build(),
                        HttpResponse.BodyHandlers.discarding())
                .statusCode();
    }

    @Test
    void apiPortDoesNotServeActuator() throws Exception {
        assertThat(get(serverPort, "/actuator/prometheus")).isNotEqualTo(200);
        assertThat(get(serverPort, "/actuator/health")).isNotEqualTo(200);
    }

    @Test
    void managementPortServesHealthAndMetrics() throws Exception {
        assertThat(managementPort).isNotEqualTo(serverPort);
        assertThat(get(managementPort, "/actuator/health")).isEqualTo(200);
        assertThat(get(managementPort, "/actuator/prometheus")).isEqualTo(200);
    }
}
