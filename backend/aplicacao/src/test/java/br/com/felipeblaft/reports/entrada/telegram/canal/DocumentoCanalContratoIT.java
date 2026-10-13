package br.com.felipeblaft.reports.entrada.telegram.canal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import br.com.felipeblaft.reports.domain.common.ReportType;
import br.com.felipeblaft.reports.domain.registry.Student;
import br.com.felipeblaft.reports.domain.registry.Teacher;
import br.com.felipeblaft.reports.domain.report.LessonReport;
import br.com.felipeblaft.reports.suporte.CenarioIT;
import br.com.felipeblaft.reports.suporte.ContratoCanalOpenApi;
import br.com.felipeblaft.reports.suporte.NormalizacaoAudioTeste;
import br.com.felipeblaft.reports.suporte.TelegramConversaBase;
import br.com.felipeblaft.reports.usecase.channel.EmitChannelDocumentUseCase;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MvcResult;

/**
 * V14, C12, SC-007: o PDF sai por identificador opaco, idêntico ao do portal; qualquer
 * identificador inexistente, vencido, malformado ou em forma de URL/caminho resulta em {@code 404}
 * sem corpo e sem diferença observável; o backend nunca faz chamada de rede a partir do
 * identificador.
 */
class DocumentoCanalContratoIT extends TelegramConversaBase {

    private static final String BASE = "/interno/v1/canal/telegram/documentos/";

    @Autowired EmitChannelDocumentUseCase emitir;

    private Teacher clara;
    private Student matteo;

    private MvcResult buscar(String id) throws Exception {
        return mvc.perform(get(BASE + id).header("X-N8N-Service-Token", TOKEN_SERVICO)).andReturn();
    }

    private Cookie entrar() throws Exception {
        MvcResult entrada =
                mvc.perform(
                                post("/api/v1/sessao")
                                        .with(csrf().asHeader())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("{\"codigo\":\"" + CenarioIT.CODIGO_CLARA + "\"}"))
                        .andReturn();
        Cookie cookie = entrada.getResponse().getCookie("sessao");
        return new Cookie(cookie.getName(), cookie.getValue());
    }

    private byte[] doPortal(String caminho) throws Exception {
        MvcResult resultado = mvc.perform(get(caminho).cookie(entrar())).andReturn();
        assertThat(resultado.getResponse().getStatus()).isEqualTo(200);
        return resultado.getResponse().getContentAsByteArray();
    }

    private LessonReport oficial() {
        clara = cenario.clara();
        matteo = cenario.aluno(clara, "Matteo Ferreira", "Piano");
        return cenario.aulaAprovada(clara, matteo, LocalDate.of(2026, 3, 10), "Escalas e arpejos");
    }

    private UUID conferencia() throws Exception {
        clara = cenario.clara();
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

    @Test
    void pdfOficialSaiIdenticoAoDoPortalComCabecalhosDeNaoCache() throws Exception {
        LessonReport relatorio = oficial();
        String id = emitir.run(clara.id(), ReportType.AULA, relatorio.id(), null, "oficial.pdf");

        MvcResult resultado = buscar(id);

        MockHttpServletResponse resposta = resultado.getResponse();
        assertThat(resposta.getStatus()).isEqualTo(200);
        assertThat(resposta.getContentType()).isEqualTo("application/pdf");
        assertThat(resposta.getHeader("Cache-Control")).contains("no-store");
        assertThat(resposta.getHeader("Content-Disposition"))
                .startsWith("inline")
                .contains("filename=");
        assertThat(resposta.getContentAsByteArray())
                .isEqualTo(doPortal("/api/v1/relatorios-aula/" + relatorio.id() + "/pdf-oficial"));
        assertThat(resposta.getContentAsByteArray()).startsWith("%PDF".getBytes());
        ContratoCanalOpenApi.exigirResposta(resultado);
    }

    @Test
    void pdfDeConferenciaSaiIdenticoAoDoPortal() throws Exception {
        UUID relatorio = conferencia();
        String id = emitir.run(clara.id(), ReportType.AULA, relatorio, 1, "conferencia.pdf");

        byte[] canal = buscar(id).getResponse().getContentAsByteArray();

        assertThat(canal)
                .isEqualTo(doPortal("/api/v1/relatorios-aula/" + relatorio + "/versoes/1/pdf"));
    }

    @Test
    void repeticaoDentroDoPrazoContinuaValida() throws Exception {
        LessonReport relatorio = oficial();
        String id = emitir.run(clara.id(), ReportType.AULA, relatorio.id(), null, "oficial.pdf");

        assertThat(buscar(id).getResponse().getStatus()).isEqualTo(200);
        relogio.avancar(Duration.ofHours(24));
        assertThat(buscar(id).getResponse().getStatus()).isEqualTo(200);
        assertThat(buscar(id).getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void identificadoresInvalidosViram404SemCorpoESemDiferencaObservavel() throws Exception {
        LessonReport relatorio = oficial();
        String vencido =
                emitir.run(clara.id(), ReportType.AULA, relatorio.id(), null, "oficial.pdf");
        relogio.avancar(Duration.ofHours(26));
        String semAssociacao =
                emitir.run(clara.id(), ReportType.AULA, relatorio.id(), null, "oficial.pdf");
        jdbc.update("delete from professor_aluno");

        List<String> ids =
                List.of(
                        "A".repeat(43), // bem formado, inexistente
                        vencido, // vencido
                        semAssociacao, // dono sem associação ao aluno: checagem repetida na leitura
                        "curto",
                        "A".repeat(44),
                        "A".repeat(42) + "+",
                        "http:localhost:" + API_BOTS.port() + ":x",
                        "file:etc:passwd");
        List<String> assinaturas = new ArrayList<>();
        for (String id : ids) {
            MvcResult resultado = buscar(id);
            MockHttpServletResponse resposta = resultado.getResponse();
            assertThat(resposta.getStatus()).as(id).isEqualTo(404);
            assertThat(resposta.getContentAsByteArray()).as(id).isEmpty();
            ContratoCanalOpenApi.exigirResposta(resultado);
            List<String> cabecalhos = new ArrayList<>();
            for (String nome : resposta.getHeaderNames()) {
                cabecalhos.add(nome + "=" + resposta.getHeaders(nome));
            }
            cabecalhos.sort(String::compareTo);
            assinaturas.add(resposta.getStatus() + "|" + cabecalhos);
        }
        assertThat(assinaturas).as("respostas indistinguíveis").containsOnly(assinaturas.get(0));
        // barras codificadas (URL, caminho) já são barradas pelo firewall antes do controlador: 4xx
        // e
        // nunca uma resposta 2xx nem 5xx
        for (String id :
                List.of(
                        "http%3A%2F%2Flocalhost%3A" + API_BOTS.port() + "%2Fx",
                        "..%2F..%2Fetc%2Fpasswd",
                        "a%20b",
                        "file%3A%2F%2F%2Fetc%2Fpasswd")) {
            int status;
            try {
                status = buscar(id).getResponse().getStatus();
            } catch (RuntimeException
                    | java.io.IOException
                    | jakarta.servlet.ServletException rejeitada) {
                status = 400;
            }
            assertThat(status).as(id).isBetween(400, 404);
        }
        // nenhuma chamada de rede nasceu do identificador
        assertThat(API_BOTS.getAllServeEvents()).isEmpty();
    }

    @Test
    void semTokenDeServicoE401() throws Exception {
        LessonReport relatorio = oficial();
        String id = emitir.run(clara.id(), ReportType.AULA, relatorio.id(), null, "oficial.pdf");
        MvcResult resultado = mvc.perform(get(BASE + id)).andReturn();
        assertThat(resultado.getResponse().getStatus()).isEqualTo(401);
        assertThat(resultado.getResponse().getContentAsByteArray()).isEmpty();
    }

    @Test
    void soOHashFicaNoBancoENuncaOIdentificador() throws Exception {
        LessonReport relatorio = oficial();
        String id = emitir.run(clara.id(), ReportType.AULA, relatorio.id(), null, "oficial.pdf");
        List<String> hashes =
                jdbc.queryForList("select token_hash from documento_canal", String.class);
        assertThat(hashes).hasSize(1);
        assertThat(hashes.get(0)).hasSize(64).isNotEqualTo(id);
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from documento_canal where token_hash = ?",
                                Integer.class,
                                id))
                .isZero();
    }
}
