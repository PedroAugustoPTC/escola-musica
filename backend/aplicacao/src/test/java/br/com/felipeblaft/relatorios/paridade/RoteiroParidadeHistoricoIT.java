package br.com.felipeblaft.relatorios.paridade;

import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import br.com.felipeblaft.relatorios.configuracao.FormatacaoMensagens;
import br.com.felipeblaft.relatorios.dominio.cadastro.Aluno;
import br.com.felipeblaft.relatorios.dominio.cadastro.Professor;
import br.com.felipeblaft.relatorios.suporte.CenarioIT;
import br.com.felipeblaft.relatorios.suporte.TelegramConversaBase;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;

/**
 * Histórico nos dois canais (FR-047, FR-048): a mesma lista, na mesma ordem, com os mesmos rótulos
 * e datas, e o PDF oficial igual byte a byte na Web e no bot.
 */
class RoteiroParidadeHistoricoIT extends TelegramConversaBase {

    @Autowired private FormatacaoMensagens formatacao;

    private Cookie sessao;
    private Aluno matteo;

    @BeforeEach
    void carregar() throws Exception {
        Professor clara = cenario.clara();
        matteo = cenario.aluno(clara, "Matteo Ferreira", "Piano");
        vincular(USUARIO_CLARA, CenarioIT.CODIGO_CLARA);
        MvcResult entrada =
                mvc.perform(
                                post("/api/v1/sessao")
                                        .with(csrf().asHeader())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("{\"codigo\":\"" + CenarioIT.CODIGO_CLARA + "\"}"))
                        .andReturn();
        Cookie cookie = entrada.getResponse().getCookie("sessao");
        sessao = new Cookie(cookie.getName(), cookie.getValue());

        cenario.aulaAprovada(clara, matteo, LocalDate.of(2026, 3, 10), "Escalas");
        relogio.avancar(Duration.ofMinutes(5));
        cenario.aulaAprovada(clara, matteo, LocalDate.of(2026, 9, 1), "Estudo");
        relogio.avancar(Duration.ofMinutes(5));
        cenario.aulaAprovada(clara, matteo, LocalDate.of(2026, 5, 20), "Leitura");
        mvc.perform(
                post("/api/v1/alunos/{id}/relatorios-semestrais", matteo.id())
                        .cookie(sessao)
                        .with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"periodoChave\":\"2026-1\"}"));
        cenario.processarFila();
        String semestral =
                jdbc.queryForObject("select id::text from relatorio_semestral", String.class);
        mvc.perform(
                post("/api/v1/relatorios-semestrais/{id}/aprovacao", semestral)
                        .cookie(sessao)
                        .with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"versaoConfirmada\":1}"));
    }

    private JsonNode historicoWeb() throws Exception {
        return JSON.readTree(
                mvc.perform(get("/api/v1/alunos/{id}/historico", matteo.id()).cookie(sessao))
                        .andReturn()
                        .getResponse()
                        .getContentAsString());
    }

    private byte[] pdfWeb(JsonNode item) throws Exception {
        String recurso =
                item.path("tipo").asString().equals("AULA")
                        ? "relatorios-aula"
                        : "relatorios-semestrais";
        return mvc.perform(
                        get(
                                        "/api/v1/" + recurso + "/{id}/pdf-oficial",
                                        item.path("relatorioId").asString())
                                .cookie(sessao))
                .andReturn()
                .getResponse()
                .getContentAsByteArray();
    }

    private byte[] pdfBot(int indice) throws Exception {
        API_BOTS.resetRequests();
        enviarCallback(USUARIO_CLARA, "v1:hitem:" + indice);
        return API_BOTS.findAll(postRequestedFor(urlPathMatching(".*/sendDocument")))
                .getFirst()
                .getPart("document")
                .getBody()
                .asBytes();
    }

    @Test
    void mesmaListaEMesmosPdfsNosDoisCanais() throws Exception {
        JsonNode web = historicoWeb();
        List<String> linhasWeb = new ArrayList<>();
        for (int i = 0; i < web.path("itens").size(); i++) {
            JsonNode item = web.path("itens").get(i);
            linhasWeb.add(
                    (i + 1)
                            + ". "
                            + item.path("rotulo").asString()
                            + " · "
                            + formatacao.aprovadoEm(
                                    Instant.parse(item.path("aprovadoEm").asString())));
        }

        enviarTexto(USUARIO_CLARA, "histórico");
        enviarTexto(USUARIO_CLARA, matteo.nome());
        List<String> linhasBot =
                List.of(ultimaMensagem().get("text").asString().split("\n"))
                        .subList(1, linhasWeb.size() + 1);

        assertThat(linhasWeb).hasSize(4);
        assertThat(linhasBot).isEqualTo(linhasWeb);
        assertThat(linhasWeb.getFirst()).startsWith("1. Aula de 01/09/2026");
        assertThat(linhasWeb.getLast()).startsWith("4. Semestral (01/01/2026 a 30/06/2026)");
        assertThat(rotulosBotoes(ultimaMensagem()))
                .startsWith(
                        web.path("itens").get(0).path("rotulo").asString(),
                        web.path("itens").get(1).path("rotulo").asString());

        for (int i = 0; i < linhasWeb.size(); i++) {
            byte[] naWeb = pdfWeb(web.path("itens").get(i));
            assertThat(naWeb).isNotEmpty();
            assertThat(pdfBot(i)).as("PDF do item %d", i + 1).isEqualTo(naWeb);
        }
    }
}
