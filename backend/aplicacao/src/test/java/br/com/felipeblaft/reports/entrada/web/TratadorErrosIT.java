package br.com.felipeblaft.reports.entrada.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import br.com.felipeblaft.reports.suporte.CenarioIT;
import br.com.felipeblaft.reports.suporte.ContratoApiBase;
import br.com.felipeblaft.reports.usecase.home.GetTeacherHomeUseCase;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import ch.qos.logback.core.read.ListAppender;
import jakarta.servlet.http.Cookie;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;

/**
 * FR-054: uma exceção inesperada em um endpoint do portal vira 500 com {@code ERRO_INTERNO} e o
 * texto do catálogo, sem nome de classe, stack trace, SQL nem caminho de arquivo na resposta, e o
 * log registra o erro sem os dados que a exceção carregava.
 */
class TratadorErrosIT extends ContratoApiBase {

    private static final String DETALHE_INTERNO =
            "SELECT * FROM aluno WHERE nome = 'Matteo Mafra' falhou em /app/src/Repositorio.java:42";

    @MockitoSpyBean GetTeacherHomeUseCase obterInicio;

    private final ListAppender<ILoggingEvent> eventos = new ListAppender<>();
    private Logger raiz;
    private Level nivelRaiz;

    @BeforeEach
    void capturarLogs() {
        raiz = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        nivelRaiz = raiz.getLevel();
        raiz.setLevel(Level.INFO);
        eventos.start();
        raiz.addAppender(eventos);
    }

    @AfterEach
    void restaurarLogs() {
        raiz.detachAppender(eventos);
        eventos.stop();
        raiz.setLevel(nivelRaiz);
    }

    private String textoDosLogs() {
        StringBuilder texto = new StringBuilder();
        for (ILoggingEvent evento : List.copyOf(eventos.list)) {
            texto.append(evento.getFormattedMessage()).append('\n');
            if (evento.getThrowableProxy() != null) {
                texto.append(ThrowableProxyUtil.asString(evento.getThrowableProxy())).append('\n');
            }
        }
        return texto.toString();
    }

    @Test
    void excecaoInesperadaDevolve500ComCodigoEMensagemDoCatalogoSemDetalheInterno()
            throws Exception {
        cenario.aluno(cenario.clara(), "Matteo Mafra", "Piano");
        Cookie sessao = entrar(CenarioIT.CODIGO_CLARA);
        doThrow(new IllegalStateException(DETALHE_INTERNO)).when(obterInicio).run(any());

        MvcResult resultado = mvc.perform(get("/api/v1/inicio").cookie(sessao)).andReturn();

        assertThat(resultado.getResponse().getStatus()).isEqualTo(500);
        String corpo = resultado.getResponse().getContentAsString();
        JsonNode json = JSON_LEITOR.readTree(corpo);
        assertThat(json.path("codigo").asString()).isEqualTo("ERRO_INTERNO");
        assertThat(json.path("mensagem").asString())
                .isEqualTo("Ocorreu um erro inesperado. Tente novamente mais tarde.");
        assertThat(corpo)
                .doesNotContain(
                        "IllegalStateException",
                        "java.lang",
                        "br.com.felipeblaft",
                        "\tat ",
                        "SELECT",
                        "Matteo",
                        ".java",
                        "/app/");
        assertThat(resultado.getResponse().getContentType()).contains("json");
    }

    @Test
    void logRegistraOErroSemOsDadosQueAExcecaoCarregava() throws Exception {
        cenario.aluno(cenario.clara(), "Matteo Mafra", "Piano");
        Cookie sessao = entrar(CenarioIT.CODIGO_CLARA);
        doThrow(new IllegalStateException(DETALHE_INTERNO)).when(obterInicio).run(any());

        mvc.perform(get("/api/v1/inicio").cookie(sessao)).andReturn();

        String logs = textoDosLogs();
        assertThat(logs).contains("erro-inesperado").contains("IllegalStateException");
        assertThat(logs).doesNotContain("Matteo", "SELECT", "Repositorio.java", "/app/");
    }

    private static final tools.jackson.databind.json.JsonMapper JSON_LEITOR =
            tools.jackson.databind.json.JsonMapper.builder().build();
}
