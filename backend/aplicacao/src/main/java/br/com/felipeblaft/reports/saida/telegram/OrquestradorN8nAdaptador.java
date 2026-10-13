package br.com.felipeblaft.reports.saida.telegram;

import br.com.felipeblaft.reports.configuracao.PropriedadesAplicacao;
import br.com.felipeblaft.reports.entrada.telegram.canal.AcaoCanalMapeador;
import br.com.felipeblaft.reports.entrada.telegram.canal.LoteEntregaDto;
import br.com.felipeblaft.reports.port.ChannelOrchestratorPort;
import br.com.felipeblaft.reports.port.DeliveryBatch;
import br.com.felipeblaft.reports.port.DeliveryResult;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Entrega de lotes proativos ao webhook do orquestrador (n8n) pelo cliente HTTP do JDK: timeouts de
 * conexão e de resposta configuráveis, sem seguir redirecionamentos, {@code Idempotency-Key} igual
 * ao {@code loteId} e o segredo do destino em {@code X-Canal-Entrega-Token}. Qualquer {@code 2xx} é
 * aceite; qualquer outra coisa (timeout, falha de conexão, 4xx, 5xx) é recusa, sem encerrar as
 * tentativas: quem decide reenviar é o núcleo. O nome do fornecedor só existe nesta camada.
 *
 * <p>Nenhum endereço vem do conteúdo das ações. Em log vão só o {@code loteId}, o status HTTP, o
 * código de situação e o tipo da exceção: nunca corpo, mensagem de exceção, URL (um webhook do n8n
 * pode trazer segredo no caminho) nem token.
 */
@Component
public class OrquestradorN8nAdaptador implements ChannelOrchestratorPort {

    public static final String CABECALHO_TOKEN = "X-Canal-Entrega-Token";
    public static final String CABECALHO_IDEMPOTENCIA = "Idempotency-Key";

    private static final Logger LOG = LoggerFactory.getLogger(OrquestradorN8nAdaptador.class);
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final URI destino;
    private final String token;
    private final Duration respostaTimeout;
    private final HttpClient cliente;

    public OrquestradorN8nAdaptador(PropriedadesAplicacao propriedades) {
        PropriedadesAplicacao.CanalTelegram canal = propriedades.canalTelegram();
        this.destino = canal.orquestradorConfigurado() ? URI.create(canal.orquestradorUrl()) : null;
        this.token = canal.orquestradorToken();
        this.respostaTimeout = Duration.ofMillis(canal.respostaTimeoutMs());
        this.cliente =
                HttpClient.newBuilder()
                        .connectTimeout(Duration.ofMillis(canal.conexaoTimeoutMs()))
                        .followRedirects(HttpClient.Redirect.NEVER)
                        .build();
    }

    @Override
    public DeliveryResult deliver(DeliveryBatch lote) {
        if (destino == null) {
            LOG.warn("canal-entrega-sem-destino loteId={}", lote.batchId());
            return DeliveryResult.refused("SEM_DESTINO");
        }
        HttpRequest.Builder requisicao =
                HttpRequest.newBuilder(destino)
                        .timeout(respostaTimeout)
                        .header("Content-Type", "application/json; charset=utf-8")
                        .header(CABECALHO_IDEMPOTENCIA, lote.batchId())
                        .POST(
                                HttpRequest.BodyPublishers.ofString(
                                        JSON.writeValueAsString(corpo(lote)),
                                        StandardCharsets.UTF_8));
        if (token != null && !token.isBlank()) {
            requisicao.header(CABECALHO_TOKEN, token);
        }
        try {
            HttpResponse<Void> resposta =
                    cliente.send(requisicao.build(), HttpResponse.BodyHandlers.discarding());
            int status = resposta.statusCode();
            if (status / 100 == 2) {
                LOG.info("canal-entrega-aceita loteId={} status={}", lote.batchId(), status);
                return DeliveryResult.accept();
            }
            LOG.warn("canal-entrega-recusada loteId={} status={}", lote.batchId(), status);
            return DeliveryResult.refused("HTTP_" + status);
        } catch (HttpTimeoutException timeout) { // inclui o timeout de conexão
            LOG.warn("canal-entrega-recusada loteId={} situacao=TIMEOUT", lote.batchId());
            return DeliveryResult.refused("TIMEOUT");
        } catch (InterruptedException interrompido) {
            Thread.currentThread().interrupt();
            LOG.warn("canal-entrega-recusada loteId={} situacao=INTERROMPIDO", lote.batchId());
            return DeliveryResult.refused("INTERROMPIDO");
        } catch (IOException | RuntimeException erro) {
            LOG.warn(
                    "canal-entrega-recusada loteId={} situacao=CONEXAO tipo={}",
                    lote.batchId(),
                    erro.getClass().getSimpleName());
            return DeliveryResult.refused("CONEXAO");
        }
    }

    /** Monta o {@code LoteEntrega} v1: as ações com {@code acaoId} e, nas etapas, o id a editar. */
    static LoteEntregaDto corpo(DeliveryBatch lote) {
        List<JsonNode> acoes = new ArrayList<>();
        for (DeliveryBatch.Action acao : lote.actions()) {
            ObjectNode no = AcaoCanalMapeador.comAcaoId(acao.actionId(), acao.payload());
            if (acao.editMessageId() != null) {
                // o id conhecido do trabalho no momento do despacho vale mais que o da publicação
                no.put("editarMensagemId", acao.editMessageId());
            }
            acoes.add(no);
        }
        return new LoteEntregaDto(
                "v1",
                lote.batchId(),
                lote.origin().name(),
                lote.createdAt().toString(),
                lote.attempt(),
                acoes);
    }
}
