package br.com.felipeblaft.relatorios.seguranca;

import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import br.com.felipeblaft.relatorios.dominio.cadastro.Aluno;
import br.com.felipeblaft.relatorios.dominio.cadastro.Professor;
import br.com.felipeblaft.relatorios.dominio.relatorio.RelatorioAula;
import br.com.felipeblaft.relatorios.suporte.CenarioIT;
import br.com.felipeblaft.relatorios.suporte.NormalizacaoAudioTeste;
import br.com.felipeblaft.relatorios.suporte.TelegramConversaBase;
import jakarta.servlet.http.Cookie;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;

/**
 * SC-008: com a sessão e o vínculo de Clara, todo endpoint e toda ação do bot sobre o aluno e os
 * relatórios de Bruno são recusados (403 ou 404 na Web, "Nenhum aluno encontrado…" ou ação expirada
 * no bot), sem revelar nenhum dado de Bruno nem de Helena.
 */
class AcessoCruzadoIT extends TelegramConversaBase {

    private static final String CONTEUDO_DE_BRUNO = "Sopro com apoio diafragmático";

    private Cookie sessaoClara;
    private Aluno helena;
    private UUID aulaAprovada;
    private UUID aulaEmAberto;
    private UUID trabalho;
    private UUID semestral;

    @BeforeEach
    void preparar() throws Exception {
        Professor clara = cenario.clara();
        Professor bruno = cenario.bruno();
        cenario.aluno(clara, "Matteo Mafra", "Piano");
        helena = cenario.aluno(bruno, "Helena Prado", "Flauta");
        vincular(USUARIO_CLARA, CenarioIT.CODIGO_CLARA);
        sessaoClara = entrar(CenarioIT.CODIGO_CLARA);
        Cookie sessaoBruno = entrar(CenarioIT.CODIGO_BRUNO);

        RelatorioAula aprovada =
                cenario.aulaAprovada(bruno, helena, LocalDate.of(2026, 3, 10), CONTEUDO_DE_BRUNO);
        aulaAprovada = aprovada.id();
        JsonNode geracao =
                JSON.readTree(
                        mvc.perform(
                                        post(
                                                        "/api/v1/alunos/{id}/relatorios-semestrais",
                                                        helena.id())
                                                .cookie(sessaoBruno)
                                                .with(csrf().asHeader())
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .content("{\"periodoChave\":\"2026-1\"}"))
                                .andReturn()
                                .getResponse()
                                .getContentAsString());
        trabalho = UUID.fromString(geracao.path("trabalhoId").asString());
        mvc.perform(
                multipart("/api/v1/alunos/{id}/relatorios-aula", helena.id())
                        .file(audio())
                        .cookie(sessaoBruno)
                        .with(csrf().asHeader()));
        cenario.processarFila();
        semestral = jdbc.queryForObject("select id from relatorio_semestral", UUID.class);
        aulaEmAberto =
                jdbc.queryForObject(
                        "select id from relatorio_aula where status <> 'APROVADO'", UUID.class);
    }

    private Cookie entrar(String codigo) throws Exception {
        MvcResult entrada =
                mvc.perform(
                                post("/api/v1/sessao")
                                        .with(csrf().asHeader())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("{\"codigo\":\"" + codigo + "\"}"))
                        .andReturn();
        Cookie cookie = entrada.getResponse().getCookie("sessao");
        return new Cookie(cookie.getName(), cookie.getValue());
    }

    private static MockMultipartFile audio() {
        return new MockMultipartFile(
                "audio", "aula.webm", "audio/webm", NormalizacaoAudioTeste.AUDIO_COM_FALA);
    }

    private record Chamada(
            String descricao, AbstractMockHttpServletRequestBuilder<?> requisicao, int esperado) {}

    private static MockHttpServletRequestBuilder json(
            String caminho, String corpo, Object... variaveis) {
        return post(caminho, variaveis).contentType(MediaType.APPLICATION_JSON).content(corpo);
    }

    @Test
    void todoEndpointSobreDadosDeBrunoERecusadoSemRevelarNada() throws Exception {
        UUID aluno = helena.id();
        List<Chamada> chamadas =
                List.of(
                        new Chamada("aluno", get("/api/v1/alunos/{id}", aluno), 403),
                        new Chamada(
                                "em aberto",
                                get("/api/v1/alunos/{id}/relatorio-em-aberto", aluno),
                                403),
                        new Chamada(
                                "registrar aula",
                                multipart("/api/v1/alunos/{id}/relatorios-aula", aluno)
                                        .file(audio()),
                                403),
                        new Chamada("semestres", get("/api/v1/alunos/{id}/semestres", aluno), 403),
                        new Chamada(
                                "prévia",
                                get("/api/v1/alunos/{id}/semestres/2026-1/previa", aluno),
                                403),
                        new Chamada(
                                "gerar semestral",
                                json(
                                        "/api/v1/alunos/{id}/relatorios-semestrais",
                                        "{\"periodoChave\":\"2026-1\"}",
                                        aluno),
                                403),
                        new Chamada("histórico", get("/api/v1/alunos/{id}/historico", aluno), 403),
                        new Chamada("trabalho", get("/api/v1/trabalhos/{id}", trabalho), 404),
                        new Chamada(
                                "descartar trabalho",
                                post("/api/v1/trabalhos/{id}/descarte", trabalho),
                                404),
                        new Chamada(
                                "relatório de aula",
                                get("/api/v1/relatorios-aula/{id}", aulaEmAberto),
                                404),
                        new Chamada(
                                "respostas",
                                json(
                                        "/api/v1/relatorios-aula/{id}/respostas",
                                        "{\"texto\":\"Sim.\"}",
                                        aulaEmAberto),
                                404),
                        new Chamada(
                                "alteração de aula",
                                json(
                                        "/api/v1/relatorios-aula/{id}/alteracoes",
                                        "{\"texto\":\"Mude.\",\"versaoBase\":1}",
                                        aulaAprovada),
                                404),
                        new Chamada(
                                "nova tentativa",
                                post("/api/v1/relatorios-aula/{id}/nova-tentativa", aulaEmAberto),
                                404),
                        new Chamada(
                                "aprovar aula",
                                json(
                                        "/api/v1/relatorios-aula/{id}/aprovacao",
                                        "{\"versaoConfirmada\":1}",
                                        aulaEmAberto),
                                404),
                        new Chamada(
                                "cancelar aula",
                                post("/api/v1/relatorios-aula/{id}/cancelamento", aulaEmAberto),
                                404),
                        new Chamada(
                                "PDF de conferência de aula",
                                get("/api/v1/relatorios-aula/{id}/versoes/1/pdf", aulaAprovada),
                                404),
                        new Chamada(
                                "PDF oficial de aula",
                                get("/api/v1/relatorios-aula/{id}/pdf-oficial", aulaAprovada),
                                404),
                        new Chamada(
                                "semestral",
                                get("/api/v1/relatorios-semestrais/{id}", semestral),
                                404),
                        new Chamada(
                                "alteração semestral",
                                json(
                                        "/api/v1/relatorios-semestrais/{id}/alteracoes",
                                        "{\"texto\":\"Mude.\",\"versaoBase\":1}",
                                        semestral),
                                404),
                        new Chamada(
                                "aprovar semestral",
                                json(
                                        "/api/v1/relatorios-semestrais/{id}/aprovacao",
                                        "{\"versaoConfirmada\":1}",
                                        semestral),
                                404),
                        new Chamada(
                                "cancelar semestral",
                                post("/api/v1/relatorios-semestrais/{id}/cancelamento", semestral),
                                404),
                        new Chamada(
                                "PDF de conferência semestral",
                                get("/api/v1/relatorios-semestrais/{id}/versoes/1/pdf", semestral),
                                404),
                        new Chamada(
                                "PDF oficial semestral",
                                get("/api/v1/relatorios-semestrais/{id}/pdf-oficial", semestral),
                                404));

        List<String> falhas = new ArrayList<>();
        for (Chamada chamada : chamadas) {
            MvcResult resultado =
                    mvc.perform(chamada.requisicao().cookie(sessaoClara).with(csrf().asHeader()))
                            .andReturn();
            int status = resultado.getResponse().getStatus();
            String corpo = resultado.getResponse().getContentAsString();
            if (status != chamada.esperado()) {
                falhas.add(
                        chamada.descricao()
                                + " → "
                                + status
                                + " (esperado "
                                + chamada.esperado()
                                + ")");
            }
            if (corpo.contains("Helena")
                    || corpo.contains("Bruno")
                    || corpo.contains(CONTEUDO_DE_BRUNO)) {
                falhas.add(chamada.descricao() + " revelou dados: " + corpo);
            }
        }
        assertThat(falhas).isEmpty();

        // nada de Bruno mudou
        assertThat(jdbc.queryForObject("select count(*) from relatorio_aula", Integer.class))
                .isEqualTo(2);
        assertThat(
                        jdbc.queryForObject(
                                "select status from relatorio_aula where id = ?",
                                String.class,
                                aulaEmAberto))
                .isNotEqualTo("CANCELADO");
        assertThat(jdbc.queryForObject("select status from relatorio_semestral", String.class))
                .isEqualTo("AGUARDANDO_CONFERENCIA");
    }

    @Test
    void botNaoEncontraOAlunoNemAgeSobreRelatoriosDeBruno() throws Exception {
        enviarTexto(USUARIO_CLARA, "Helena");
        assertThat(ultimaMensagem().get("text").asString())
                .isEqualTo("Nenhum aluno encontrado para \"Helena\".");

        for (String acao :
                List.of(
                        "v1:foco:a" + aulaEmAberto,
                        "v1:foco:a" + aulaAprovada,
                        "v1:foco:s" + semestral,
                        "v1:foco:t" + trabalho,
                        "v1:aprov:1",
                        "v1:pdf:",
                        "v1:canc:",
                        "v1:gerar:2026-1")) {
            enviarCallback(USUARIO_CLARA, acao);
        }
        enviarTexto(USUARIO_CLARA, "histórico");
        enviarTexto(USUARIO_CLARA, "Helena");

        assertThat(String.join("\n", textosEnviados()))
                .doesNotContain("Helena Prado")
                .doesNotContain("Bruno")
                .doesNotContain(CONTEUDO_DE_BRUNO);
        assertThat(API_BOTS.findAll(postRequestedFor(urlPathMatching(".*/sendDocument"))))
                .isEmpty();
        assertThat(
                        jdbc.queryForObject(
                                "select status from relatorio_aula where id = ?",
                                String.class,
                                aulaEmAberto))
                .isNotEqualTo("CANCELADO");
        assertThat(jdbc.queryForObject("select status from relatorio_semestral", String.class))
                .isEqualTo("AGUARDANDO_CONFERENCIA");
    }
}
