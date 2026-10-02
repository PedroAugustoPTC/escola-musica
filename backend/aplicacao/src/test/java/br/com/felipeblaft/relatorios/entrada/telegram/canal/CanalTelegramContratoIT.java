package br.com.felipeblaft.relatorios.entrada.telegram.canal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import br.com.felipeblaft.relatorios.dominio.cadastro.Professor;
import br.com.felipeblaft.relatorios.suporte.CenarioIT;
import br.com.felipeblaft.relatorios.suporte.ContratoCanalOpenApi;
import br.com.felipeblaft.relatorios.suporte.EsquemaAcoesV1;
import br.com.felipeblaft.relatorios.suporte.NormalizacaoAudioTeste;
import br.com.felipeblaft.relatorios.suporte.TelegramConversaBase;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;

/**
 * V2, C1 a C3 e C13 a C14: toda resposta da API interna segue o formato v1 normativo ({@code
 * acoes-v1.schema.json}) e as rotas e códigos HTTP seguem o OpenAPI. O estado da conversa é montado
 * pelo webhook; o que se verifica aqui é a nova porta.
 */
class CanalTelegramContratoIT extends TelegramConversaBase {

    private static final String BASE = "/interno/v1/canal/telegram";

    private MvcResult atualizacao(String json) throws Exception {
        MvcResult resultado =
                mvc.perform(
                                post(BASE + "/atualizacao")
                                        .header("X-N8N-Service-Token", TOKEN_SERVICO)
                                        .header("X-Telegram-Bot-Api-Secret-Token", SEGREDO)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(json))
                        .andReturn();
        assertThat(resultado.getResponse().getStatus()).isEqualTo(200);
        EsquemaAcoesV1.exigir("RespostaAtualizacao", resultado.getResponse().getContentAsString());
        ContratoCanalOpenApi.exigirResposta(resultado);
        return resultado;
    }

    private JsonNode acoes(MvcResult resultado) throws Exception {
        return JSON.readTree(resultado.getResponse().getContentAsString()).get("acoes");
    }

    private MvcResult callback(long usuario, String dados) throws Exception {
        return atualizacao(
                """
                {"update_id": %d, "callback_query": {"id": "cb%d", "data": %s,
                 "from": {"id": %d, "is_bot": false, "first_name": "Teste"},
                 "message": {"message_id": 55, "date": 1, "chat": {"id": %d, "type": "private"}}}}
                """
                        .formatted(
                                proximoUpdate(),
                                proximoUpdate(),
                                JSON.writeValueAsString(dados),
                                usuario,
                                usuario));
    }

    private UUID prepararConferencia() throws Exception {
        Professor clara = cenario.clara();
        cenario.aluno(clara, "Matteo Ferreira", "Piano");
        vincular(USUARIO_CLARA, CenarioIT.CODIGO_CLARA);
        prepararArquivoTelegram("aula", NormalizacaoAudioTeste.AUDIO_COM_FALA);
        enviarTexto(USUARIO_CLARA, "Matteo");
        enviarVoz(USUARIO_CLARA, "aula", 1000);
        enviarCallback(USUARIO_CLARA, "v1:confal:");
        cenario.processarFila();
        enviarTexto(USUARIO_CLARA, "Estudou bem; vai preparar a valsa.");
        cenario.processarFila();
        API_BOTS.resetRequests();
        return jdbc.queryForObject("select id from relatorio_aula", UUID.class);
    }

    // ---- C1

    @Test
    void professorNaoVinculadoRecebeOTextoDeCodigoNecessarioComOAcaoIdPorPosicao()
            throws Exception {
        long update = proximoUpdate();
        MvcResult resultado = atualizacao(mensagemJson(update, USUARIO_CLARA, "\"text\": \"oi\""));

        JsonNode corpo = JSON.readTree(resultado.getResponse().getContentAsString());
        assertThat(corpo.path("versao").asString()).isEqualTo("v1");
        assertThat(corpo.path("updateId").asLong()).isEqualTo(update);
        assertThat(corpo.path("loteId").asString()).isEqualTo("u" + update);
        JsonNode acao = corpo.get("acoes").get(0);
        assertThat(acao.path("acaoId").asString()).isEqualTo("u" + update + "-0");
        assertThat(acao.path("tipo").asString()).isEqualTo("MENSAGEM");
        assertThat(acao.path("texto").asString()).startsWith("Olá! Para usar o bot");
        assertThat(acao.path("chatId").asLong()).isEqualTo(USUARIO_CLARA);
        // nada foi enviado ao Telegram: o envio é do orquestrador (FR-010)
        assertThat(textosEnviados()).isEmpty();
    }

    @Test
    void grupoRecebeSomenteConversaPrivada() throws Exception {
        MvcResult resultado =
                atualizacao(
                        mensagemJson(
                                proximoUpdate(), USUARIO_CLARA, "group", "\"text\": \"K7QM2XPA\""));
        assertThat(acoes(resultado).get(0).path("texto").asString())
                .isEqualTo("Eu só atendo em conversa privada. Fale comigo diretamente.");
    }

    @Test
    void corpoIlegivelOuTipoNaoSuportadoVoltaComListaVazia() throws Exception {
        for (String corpo :
                List.of(
                        "{ nao é json",
                        "",
                        "{}",
                        "{\"update_id\": " + proximoUpdate() + ", \"edited_message\": {}}")) {
            MvcResult resultado = atualizacao(corpo);
            assertThat(acoes(resultado)).isEmpty();
        }
    }

    // ---- C2

    @Test
    void toqueEmAprovarTrazRespostaDeCallbackDepoisAConfirmacaoComBotoes() throws Exception {
        prepararConferencia();

        MvcResult resultado = callback(USUARIO_CLARA, "v1:aprov:1");

        JsonNode acoes = acoes(resultado);
        assertThat(acoes.get(0).path("tipo").asString()).isEqualTo("RESPOSTA_CALLBACK");
        assertThat(acoes.get(0).path("callbackId").asString()).startsWith("cb");
        JsonNode confirmacao = acoes.get(1);
        assertThat(confirmacao.path("tipo").asString()).isEqualTo("MENSAGEM");
        assertThat(confirmacao.path("teclado").path("linhas")).isNotEmpty();
        assertThat(confirmacao.path("teclado").toString()).contains("v1:");
    }

    // ---- C3

    @Test
    void relatorioProntoTrazMensagensDivididasDocumentoEBotoesNaOrdemDeHoje() throws Exception {
        UUID relatorio = prepararConferencia();
        jdbc.update(
                "update relatorio_aula set conteudo = jsonb_set(conteudo, '{observacoes}',"
                        + " to_jsonb(repeat('Observação importante da aula. ', 200)))");

        MvcResult resultado = callback(USUARIO_CLARA, "v1:foco:a" + relatorio);

        JsonNode acoes = acoes(resultado);
        List<String> tipos = new ArrayList<>();
        acoes.forEach(a -> tipos.add(a.path("tipo").asString()));
        int documento = tipos.indexOf("DOCUMENTO");
        assertThat(documento).as("tipos %s", tipos).isGreaterThan(1);
        assertThat(tipos.subList(1, documento)).containsOnly("MENSAGEM");
        assertThat(tipos.subList(1, documento).size())
                .as("texto longo dividido em mais de uma mensagem")
                .isGreaterThanOrEqualTo(2);
        for (int i = 1; i < documento; i++) {
            assertThat(acoes.get(i).path("texto").asString().length()).isLessThanOrEqualTo(4096);
        }
        JsonNode pdf = acoes.get(documento);
        assertThat(pdf.path("documentoId").asString()).matches("^[A-Za-z0-9_-]{43}$");
        assertThat(pdf.path("nomeArquivo").asString()).endsWith(".pdf");
        assertThat(pdf.path("tipoConteudo").asString()).isEqualTo("application/pdf");
        assertThat(pdf.has("conteudo")).isFalse();
        JsonNode ultima = acoes.get(acoes.size() - 1);
        assertThat(documento).isEqualTo(acoes.size() - 2);
        assertThat(ultima.path("tipo").asString()).isEqualTo("MENSAGEM");
        assertThat(ultima.path("teclado").toString())
                .contains("v1:aprov:1", "v1:alt:", "v1:pdf:", "v1:canc:", "v1:reg:");
        // ids únicos e por posição, depois da divisão
        List<String> ids = new ArrayList<>();
        acoes.forEach(a -> ids.add(a.path("acaoId").asString()));
        assertThat(ids).doesNotHaveDuplicates();
    }

    @Test
    void documentoDaAcaoTemOMesmoNomeEOMesmoPdfQueOEnvioDireto() throws Exception {
        prepararConferencia();
        enviarCallback(USUARIO_CLARA, "v1:pdf:"); // envio direto, pelo webhook
        var direto =
                API_BOTS.findAll(
                        com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor(
                                com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching(
                                        ".*/sendDocument")));
        assertThat(direto).hasSize(1);
        String multipart = direto.get(0).getBodyAsString();
        String nomeDireto = multipart.replaceAll("(?s).*filename=\"([^\"]+)\".*", "$1");

        JsonNode pdf = null;
        for (JsonNode a : acoes(callback(USUARIO_CLARA, "v1:pdf:"))) {
            if (a.path("tipo").asString().equals("DOCUMENTO")) {
                pdf = a;
            }
        }
        assertThat(pdf).isNotNull();
        assertThat(pdf.path("nomeArquivo").asString()).isEqualTo(nomeDireto);
        assertThat(pdf.has("legenda")).isFalse();
    }

    // ---- confirmações e estado

    @Test
    void confirmacoesEEstadoDaAcaoSeguemOContrato() throws Exception {
        MvcResult resultado =
                atualizacao(mensagemJson(proximoUpdate(), USUARIO_CLARA, "\"text\": \"oi\""));
        String acaoId = acoes(resultado).get(0).path("acaoId").asString();

        MvcResult antes =
                mvc.perform(
                                get(BASE + "/acoes/" + acaoId)
                                        .header("X-N8N-Service-Token", TOKEN_SERVICO))
                        .andReturn();
        assertThat(antes.getResponse().getStatus()).isEqualTo(200);
        EsquemaAcoesV1.exigir("RespostaEstadoAcao", antes.getResponse().getContentAsString());
        ContratoCanalOpenApi.exigirResposta(antes);
        assertThat(
                        JSON.readTree(antes.getResponse().getContentAsString())
                                .path("estado")
                                .asString())
                .isEqualTo("ENTREGUE");

        MvcResult confirmacao =
                mvc.perform(
                                post(BASE + "/confirmacoes")
                                        .header("X-N8N-Service-Token", TOKEN_SERVICO)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                "{\"versao\":\"v1\",\"campoNovo\":true,\"resultados\":[{\"acaoId\":\""
                                                        + acaoId
                                                        + "\",\"situacao\":\"EXECUTADA\",\"extra\":1},"
                                                        + "{\"acaoId\":\"u1-9\",\"situacao\":\"EXECUTADA\"}]}"))
                        .andReturn();
        assertThat(confirmacao.getResponse().getStatus()).isEqualTo(200);
        EsquemaAcoesV1.exigir(
                "RespostaConfirmacao", confirmacao.getResponse().getContentAsString());
        ContratoCanalOpenApi.exigirResposta(confirmacao);
        JsonNode corpo = JSON.readTree(confirmacao.getResponse().getContentAsString());
        assertThat(corpo.path("aceitas").asInt()).isEqualTo(1);
        assertThat(corpo.path("ignoradas")).hasSize(1);

        MvcResult depois =
                mvc.perform(
                                get(BASE + "/acoes/" + acaoId)
                                        .header("X-N8N-Service-Token", TOKEN_SERVICO))
                        .andReturn();
        assertThat(
                        JSON.readTree(depois.getResponse().getContentAsString())
                                .path("estado")
                                .asString())
                .isEqualTo("EXECUTADA");

        MvcResult desconhecida =
                mvc.perform(
                                get(BASE + "/acoes/u1-999999")
                                        .header("X-N8N-Service-Token", TOKEN_SERVICO))
                        .andReturn();
        assertThat(desconhecida.getResponse().getStatus()).isEqualTo(404);
        assertThat(desconhecida.getResponse().getContentAsByteArray()).isEmpty();
        ContratoCanalOpenApi.exigirResposta(desconhecida);
    }

    @Test
    void falhaComCodigoRegistraOMotivoEVersaoDesconhecidaERecusada() throws Exception {
        String acaoId =
                acoes(atualizacao(mensagemJson(proximoUpdate(), USUARIO_CLARA, "\"text\": \"oi\"")))
                        .get(0)
                        .path("acaoId")
                        .asString();
        MvcResult falha =
                mvc.perform(
                                post(BASE + "/confirmacoes")
                                        .header("X-N8N-Service-Token", TOKEN_SERVICO)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                "{\"versao\":\"v1\",\"resultados\":[{\"acaoId\":\""
                                                        + acaoId
                                                        + "\",\"situacao\":\"FALHOU\",\"codigoMotivo\":\"CHAT_INDISPONIVEL\"}]}"))
                        .andReturn();
        assertThat(falha.getResponse().getStatus()).isEqualTo(200);
        var estado =
                JSON.readTree(
                        mvc.perform(
                                        get(BASE + "/acoes/" + acaoId)
                                                .header("X-N8N-Service-Token", TOKEN_SERVICO))
                                .andReturn()
                                .getResponse()
                                .getContentAsString());
        assertThat(estado.path("estado").asString()).isEqualTo("FALHOU");
        assertThat(estado.path("codigoMotivo").asString()).isEqualTo("CHAT_INDISPONIVEL");

        for (String corpo :
                List.of(
                        "{\"versao\":\"v2\",\"resultados\":[]}",
                        "{\"resultados\":[]}",
                        "não é json",
                        "{\"versao\":\"v1\"}",
                        "{\"versao\":\"v1\",\"resultados\":[]}")) {
            MvcResult recusada =
                    mvc.perform(
                                    post(BASE + "/confirmacoes")
                                            .header("X-N8N-Service-Token", TOKEN_SERVICO)
                                            .contentType(MediaType.APPLICATION_JSON)
                                            .content(corpo))
                            .andReturn();
            assertThat(recusada.getResponse().getStatus()).as(corpo).isEqualTo(400);
            EsquemaAcoesV1.exigir("Erro", recusada.getResponse().getContentAsString());
            ContratoCanalOpenApi.exigirResposta(recusada);
        }
        MvcResult versao =
                mvc.perform(
                                post(BASE + "/confirmacoes")
                                        .header("X-N8N-Service-Token", TOKEN_SERVICO)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("{\"versao\":\"v2\",\"resultados\":[]}"))
                        .andReturn();
        assertThat(
                        JSON.readTree(versao.getResponse().getContentAsString())
                                .path("codigo")
                                .asString())
                .isEqualTo("VERSAO_NAO_SUPORTADA");
    }

    @Test
    void erroDe401TemCodigoDocumentadoENaoTemCorpo() throws Exception {
        MvcResult semToken =
                mvc.perform(
                                post(BASE + "/atualizacao")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("{}"))
                        .andReturn();
        assertThat(semToken.getResponse().getStatus()).isEqualTo(401);
        ContratoCanalOpenApi.exigirResposta(semToken);
    }

    // ---- latência (SC-003): mede e imprime; não bloqueia o build

    @Test
    @Tag("latencia")
    void latenciaDoP95DaAtualizacaoComConferenciaEPdf() throws Exception {
        UUID relatorio = prepararConferencia();
        List<Long> duracoesMs = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            long inicio = System.nanoTime();
            callback(USUARIO_CLARA, "v1:foco:a" + relatorio);
            duracoesMs.add((System.nanoTime() - inicio) / 1_000_000);
        }
        Collections.sort(duracoesMs);
        long p95 = duracoesMs.get((int) Math.ceil(duracoesMs.size() * 0.95) - 1);
        System.out.printf(
                "LATENCIA /atualizacao (conferencia com PDF, 50 chamadas): p50=%d ms p95=%d ms max=%d ms (alvo SC-003: p95 <= 2000 ms)%n",
                duracoesMs.get(duracoesMs.size() / 2), p95, duracoesMs.getLast());
    }
}
