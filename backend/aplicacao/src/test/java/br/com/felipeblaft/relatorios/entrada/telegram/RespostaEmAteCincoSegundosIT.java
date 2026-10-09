package br.com.felipeblaft.relatorios.entrada.telegram;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import br.com.felipeblaft.relatorios.suporte.CenarioIT;
import br.com.felipeblaft.relatorios.suporte.EntregaOrquestradorBase;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import tools.jackson.databind.JsonNode;

/**
 * SC-007: a confirmação e a mensagem de erro de uma ação do professor chegam em até 5 s do
 * recebimento do update, e toda mensagem recebida gera resposta (FR-008). A resposta a um update
 * volta na própria chamada à API interna; o que nasce sem update (proativo) espera, no pior caso,
 * um intervalo do despachante da outbox (2 s por padrão) mais a entrega. Mede-se o tempo de relógio
 * do recebimento até a mensagem aparecer na API de bots, com o executor simulado no lugar do n8n.
 */
class RespostaEmAteCincoSegundosIT extends EntregaOrquestradorBase {

    private static final Duration LIMITE = Duration.ofSeconds(5);
    private static final String BASE = "/interno/v1/canal/telegram";

    @MockitoSpyBean InterpretadorConversaTelegram interpretador;

    private static long intervaloPadraoDoDespachanteMs() throws Exception {
        String yaml;
        try (var entrada = new ClassPathResource("application.yaml").getInputStream()) {
            yaml = new String(entrada.readAllBytes(), StandardCharsets.UTF_8);
        }
        Matcher achado =
                Pattern.compile("intervalo-despacho-ms:\\s*\\$\\{[A-Z_]+:(\\d+)\\}").matcher(yaml);
        assertThat(achado.find()).as("intervalo-despacho-ms no application.yaml").isTrue();
        return Long.parseLong(achado.group(1));
    }

    /** Envia o update à API interna e passa as ações ao executor; devolve o tempo gasto. */
    private Duration receberEExecutar(String json, List<JsonNode> acoesRecebidas) throws Exception {
        long inicio = System.nanoTime();
        String corpo =
                mvc.perform(
                                post(BASE + "/atualizacao")
                                        .header("X-N8N-Service-Token", TOKEN_SERVICO)
                                        .header("X-Telegram-Bot-Api-Secret-Token", SEGREDO)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(json))
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        JsonNode acoes = JSON.readTree(corpo).get("acoes");
        executorDoOrquestrador().executar(acoes);
        Duration gasto = Duration.ofNanos(System.nanoTime() - inicio);
        acoes.forEach(acoesRecebidas::add);
        return gasto;
    }

    private static String toque(long updateId, String dados) {
        return """
                {"update_id": %d, "callback_query": {"id": "cb%d", "data": "%s",
                 "from": {"id": %d, "is_bot": false, "first_name": "Teste"},
                 "message": {"message_id": 55, "date": 1,
                             "chat": {"id": %d, "type": "private"}}}}
                """
                .formatted(updateId, updateId, dados, USUARIO_CLARA, USUARIO_CLARA);
    }

    @Test
    void confirmacaoDeUmaAcaoDoProfessorApareceEmAteCincoSegundos() throws Exception {
        vincular(USUARIO_CLARA, CenarioIT.CODIGO_CLARA);
        API_BOTS.resetRequests();
        List<JsonNode> acoes = new ArrayList<>();

        Duration gasto = receberEExecutar(toque(proximoUpdate(), "v1:inicio:"), acoes);

        assertThat(gasto).isLessThanOrEqualTo(LIMITE);
        assertThat(acoes).isNotEmpty();
        assertThat(chamadas("answerCallbackQuery")).hasSize(1);
        assertThat(textosEnviados()).isNotEmpty();
    }

    @Test
    void mensagemDeErroApareceEmAteCincoSegundos() throws Exception {
        vincular(USUARIO_CLARA, CenarioIT.CODIGO_CLARA);
        API_BOTS.resetRequests();
        doThrow(new IllegalStateException("falha")).when(interpretador).interpretar(any());
        List<JsonNode> acoes = new ArrayList<>();

        Duration gasto =
                receberEExecutar(
                        mensagemJson(proximoUpdate(), USUARIO_CLARA, "\"text\": \"oi\""), acoes);

        assertThat(gasto).isLessThanOrEqualTo(LIMITE);
        assertThat(textosEnviados())
                .containsExactly("Ocorreu um erro inesperado. Tente novamente mais tarde.");
    }

    @Test
    void todaMensagemRecebidaGeraResposta() throws Exception {
        vincular(USUARIO_CLARA, CenarioIT.CODIGO_CLARA);
        List<String> mensagens =
                List.of(
                        "\"text\": \"oi\"",
                        "\"text\": \"/start\"",
                        "\"text\": \"texto qualquer sem aluno\"",
                        "\"sticker\": {\"file_id\": \"s1\", \"file_unique_id\": \"u-s1\","
                                + " \"width\": 1, \"height\": 1, \"type\": \"regular\","
                                + " \"is_animated\": false, \"is_video\": false}",
                        "\"location\": {\"latitude\": 1.0, \"longitude\": 2.0}");
        for (String conteudo : mensagens) {
            List<JsonNode> acoes = new ArrayList<>();
            Duration gasto =
                    receberEExecutar(mensagemJson(proximoUpdate(), USUARIO_CLARA, conteudo), acoes);
            assertThat(acoes).as("resposta a " + conteudo).isNotEmpty();
            assertThat(gasto).as("tempo de " + conteudo).isLessThanOrEqualTo(LIMITE);
        }
        List<JsonNode> doGrupo = new ArrayList<>();
        receberEExecutar(
                mensagemJson(proximoUpdate(), USUARIO_CLARA, "group", "\"text\": \"oi\""), doGrupo);
        assertThat(doGrupo).as("resposta no grupo").isNotEmpty();
    }

    @Test
    void acaoProativaChegaDentroDoIntervaloPadraoDoDespachantePorMaisAEntrega() throws Exception {
        vincular(USUARIO_CLARA, CenarioIT.CODIGO_CLARA);
        API_BOTS.resetRequests();
        // a base de testes desliga o despachante agendado; o padrão de produção vem do
        // application.yaml
        long intervalo = intervaloPadraoDoDespachanteMs();
        assertThat(intervalo).as("intervalo padrão do despachante").isEqualTo(2000L);

        long inicio = System.nanoTime();
        gerarAvisoDe(matteo);
        drenar(3);
        Duration entrega = Duration.ofNanos(System.nanoTime() - inicio);

        assertThat(textosEnviados()).isNotEmpty();
        // pior caso: o aviso nasce logo depois de um ciclo e espera um intervalo inteiro
        assertThat(entrega.plusMillis(intervalo)).isLessThanOrEqualTo(LIMITE);
    }
}
