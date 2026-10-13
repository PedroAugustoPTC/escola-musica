package br.com.felipeblaft.reports.paridade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import br.com.felipeblaft.reports.domain.registry.Student;
import br.com.felipeblaft.reports.domain.registry.Teacher;
import br.com.felipeblaft.reports.suporte.CenarioIT;
import br.com.felipeblaft.reports.suporte.NormalizacaoAudioTeste;
import br.com.felipeblaft.reports.suporte.TelegramConversaBase;
import jakarta.servlet.http.Cookie;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;

/**
 * Retomada entre canais (SC-009, quickstart V6 e V7): começar num canal e concluir no outro,
 * continuar ou descartar, nos dois sentidos, e a versão desatualizada recusada nos dois canais.
 */
class RoteiroParidadeRetomadaIT extends TelegramConversaBase {

    private Cookie sessao;
    private Student matteo;
    private Student helena;

    @BeforeEach
    void carregar() throws Exception {
        Teacher clara = cenario.clara();
        matteo = cenario.aluno(clara, "Matteo Ferreira", "Piano");
        helena = cenario.aluno(clara, "Helena Prado", "Violino");
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
    }

    // ---------------------------------------------------------------- ações na Web

    private void webRegistrar(Student aluno) throws Exception {
        mvc.perform(
                multipart("/api/v1/alunos/{id}/relatorios-aula", aluno.id())
                        .file(
                                new MockMultipartFile(
                                        "audio",
                                        "aula.webm",
                                        "audio/webm",
                                        NormalizacaoAudioTeste.AUDIO_COM_FALA))
                        .cookie(sessao)
                        .with(csrf().asHeader()));
        cenario.processarFila();
    }

    private JsonNode webEmAberto(Student aluno) throws Exception {
        MvcResult resultado =
                mvc.perform(
                                get("/api/v1/alunos/{id}/relatorio-em-aberto", aluno.id())
                                        .cookie(sessao))
                        .andReturn();
        return resultado.getResponse().getStatus() == 204
                ? JSON.nullNode()
                : JSON.readTree(resultado.getResponse().getContentAsString());
    }

    private String webPost(String caminho, Object corpo) throws Exception {
        MvcResult resultado =
                mvc.perform(
                                post("/api/v1" + caminho)
                                        .cookie(sessao)
                                        .with(csrf().asHeader())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                corpo == null
                                                        ? "{}"
                                                        : JSON.writeValueAsString(corpo)))
                        .andReturn();
        if (resultado.getResponse().getStatus() / 100 == 2) {
            return "OK";
        }
        return JSON.readTree(resultado.getResponse().getContentAsString())
                .path("codigo")
                .asString();
    }

    // ---------------------------------------------------------------- ações no bot

    private void botRegistrar(Student aluno) throws Exception {
        enviarTexto(USUARIO_CLARA, "registrar aula");
        enviarTexto(USUARIO_CLARA, aluno.name());
        prepararArquivoTelegram("aula-" + aluno.id(), NormalizacaoAudioTeste.AUDIO_COM_FALA);
        enviarVoz(USUARIO_CLARA, "aula-" + aluno.id(), 1000);
        enviarCallback(USUARIO_CLARA, "v1:confal:");
        cenario.processarFila();
    }

    private String status(Student aluno) {
        List<String> status =
                jdbc.queryForList(
                        "select status from relatorio_aula where aluno_id = ? order by criado_em",
                        String.class,
                        aluno.id());
        return String.join(",", status);
    }

    private UUID relatorioAberto(Student aluno) {
        return jdbc.queryForObject(
                "select id from relatorio_aula where aluno_id = ? and status not in ('CANCELADO', 'APROVADO')",
                UUID.class,
                aluno.id());
    }

    @Test
    void comecarNaWebEConcluirNoBot() throws Exception {
        webRegistrar(matteo);
        webPost(
                "/relatorios-aula/" + relatorioAberto(matteo) + "/respostas",
                Map.of("texto", "Estudou bem."));
        cenario.processarFila();

        enviarTexto(USUARIO_CLARA, "registrar aula");
        enviarTexto(USUARIO_CLARA, matteo.name());
        assertThat(ultimaMensagem().get("text").asString()).contains("pelo portal");
        enviarCallback(USUARIO_CLARA, "v1:cont:");
        enviarCallback(USUARIO_CLARA, "v1:aprov:1");
        enviarCallback(USUARIO_CLARA, "v1:aprovok:1");

        assertThat(status(matteo)).isEqualTo("APROVADO");
        assertThat(
                        jdbc.queryForObject(
                                "select canal_origem || '>' || canal_aprovacao from relatorio_aula",
                                String.class))
                .isEqualTo("WEB>TELEGRAM");
    }

    @Test
    void comecarNoBotEConcluirNaWeb() throws Exception {
        botRegistrar(matteo);
        enviarTexto(USUARIO_CLARA, "Estudou bem.");
        cenario.processarFila();

        JsonNode aberto = webEmAberto(matteo);
        assertThat(aberto.path("canalOrigem").asString()).isEqualTo("TELEGRAM");
        assertThat(aberto.path("status").asString()).isEqualTo("AGUARDANDO_CONFERENCIA");
        assertThat(
                        webPost(
                                "/relatorios-aula/"
                                        + aberto.path("relatorioId").asString()
                                        + "/aprovacao",
                                Map.of("versaoConfirmada", 1)))
                .isEqualTo("OK");

        assertThat(
                        jdbc.queryForObject(
                                "select canal_origem || '>' || canal_aprovacao from relatorio_aula",
                                String.class))
                .isEqualTo("TELEGRAM>WEB");
    }

    @Test
    void descartarNosDoisSentidos() throws Exception {
        // começou na Web, descartado no bot
        webRegistrar(matteo);
        enviarTexto(USUARIO_CLARA, "registrar aula");
        enviarTexto(USUARIO_CLARA, matteo.name());
        enviarCallback(USUARIO_CLARA, "v1:desc:");
        enviarCallback(USUARIO_CLARA, "v1:descok:");
        // começou no bot, descartado na Web
        botRegistrar(helena);
        String descarteWeb =
                webPost("/relatorios-aula/" + relatorioAberto(helena) + "/cancelamento", null);

        assertThat(status(matteo)).isEqualTo("CANCELADO");
        assertThat(descarteWeb).isEqualTo("OK");
        assertThat(status(helena)).isEqualTo("CANCELADO");
        assertThat(webEmAberto(matteo).isNull()).isTrue();
        assertThat(webEmAberto(helena).isNull()).isTrue();
    }

    @Test
    void versaoDesatualizadaERecusadaNosDoisCanais() throws Exception {
        webRegistrar(matteo);
        UUID id = relatorioAberto(matteo);
        webPost("/relatorios-aula/" + id + "/respostas", Map.of("texto", "Estudou bem."));
        cenario.processarFila();
        // o bot apresenta a versão 1
        enviarTexto(USUARIO_CLARA, "registrar aula");
        enviarTexto(USUARIO_CLARA, matteo.name());
        enviarCallback(USUARIO_CLARA, "v1:cont:");
        // a Web altera para a versão 2
        webPost(
                "/relatorios-aula/" + id + "/alteracoes",
                Map.of("texto", "Diga que foi ótima.", "versaoBase", 1));
        cenario.processarFila();

        // o bot tenta aprovar a versão 1 que apresentou
        API_BOTS.resetRequests();
        enviarTexto(USUARIO_CLARA, "aprovar");
        String noBot = textosEnviados().getFirst();
        // a Web tenta aprovar a versão 1 que tinha na tela
        String naWeb =
                webPost("/relatorios-aula/" + id + "/aprovacao", Map.of("versaoConfirmada", 1));

        assertThat(noBot).startsWith("Esta versão está desatualizada").contains("versão 2");
        assertThat(naWeb).isEqualTo("VERSAO_DESATUALIZADA");
        assertThat(status(matteo)).isEqualTo("AGUARDANDO_CONFERENCIA");
        assertThat(botoes(ultimaMensagem())).contains("v1:aprov:2");
    }
}
