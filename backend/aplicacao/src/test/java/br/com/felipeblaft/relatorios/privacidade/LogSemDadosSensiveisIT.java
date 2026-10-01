package br.com.felipeblaft.relatorios.privacidade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import br.com.felipeblaft.relatorios.casodeuso.carga.ArquivoCarga;
import br.com.felipeblaft.relatorios.casodeuso.carga.CarregarCadastroInicialCasoDeUso;
import br.com.felipeblaft.relatorios.dominio.cadastro.Aluno;
import br.com.felipeblaft.relatorios.dominio.cadastro.Professor;
import br.com.felipeblaft.relatorios.suporte.CenarioIT;
import br.com.felipeblaft.relatorios.suporte.NormalizacaoAudioTeste;
import br.com.felipeblaft.relatorios.suporte.TelegramConversaBase;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import ch.qos.logback.core.read.ListAppender;
import jakarta.servlet.http.Cookie;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;

/**
 * FR-055 e quickstart V12: com os logs da aplicação em DEBUG, nenhum evento (mensagem, MDC ou
 * exceção) traz transcrição, conteúdo do relatório, respostas, nome de aluno, CPF, código de
 * acesso, token de sessão nem o token do bot — nos fluxos de sucesso e de erro dos dois canais.
 */
class LogSemDadosSensiveisIT extends TelegramConversaBase {

    private static final String RESPOSTA = "Resposta sigilosa sobre a rotina de estudo";
    private static final String ALTERACAO = "Pedido sigiloso de alteração do texto";
    private static final String CPF = "52998224725";

    @Autowired private CarregarCadastroInicialCasoDeUso carga;

    private final ListAppender<ILoggingEvent> eventos = new ListAppender<>();
    private Logger raiz;
    private Level nivelRaiz;
    private Logger aplicacao;
    private Level nivelAplicacao;

    @BeforeEach
    void capturarLogs() {
        raiz = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        aplicacao = (Logger) LoggerFactory.getLogger("br.com.felipeblaft");
        nivelRaiz = raiz.getLevel();
        nivelAplicacao = aplicacao.getLevel();
        raiz.setLevel(Level.INFO);
        aplicacao.setLevel(Level.DEBUG);
        eventos.start();
        raiz.addAppender(eventos);
    }

    @AfterEach
    void restaurarLogs() {
        raiz.detachAppender(eventos);
        eventos.stop();
        raiz.setLevel(nivelRaiz);
        aplicacao.setLevel(nivelAplicacao);
    }

    private String textoDosLogs() {
        StringBuilder texto = new StringBuilder();
        for (ILoggingEvent evento : List.copyOf(eventos.list)) {
            texto.append(evento.getFormattedMessage()).append('\n');
            evento.getMDCPropertyMap()
                    .forEach(
                            (chave, valor) ->
                                    texto.append(chave).append('=').append(valor).append('\n'));
            if (evento.getKeyValuePairs() != null) {
                evento.getKeyValuePairs()
                        .forEach(
                                par ->
                                        texto.append(par.key)
                                                .append('=')
                                                .append(par.value)
                                                .append('\n'));
            }
            if (evento.getThrowableProxy() != null) {
                texto.append(ThrowableProxyUtil.asString(evento.getThrowableProxy())).append('\n');
            }
        }
        return texto.toString();
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
        return cookie == null ? null : new Cookie(cookie.getName(), cookie.getValue());
    }

    private UUID relatorio() {
        return jdbc.queryForObject(
                "select id from relatorio_aula order by criado_em desc limit 1", UUID.class);
    }

    private void postJson(Cookie sessao, String caminho, String corpo) throws Exception {
        mvc.perform(
                post(caminho)
                        .cookie(sessao)
                        .with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo));
    }

    @Test
    void fluxosDosDoisCanaisNaoRegistramDadosSensiveis() throws Exception {
        Professor clara = cenario.clara();
        Aluno matteo = cenario.aluno(clara, "Matteo Mafra", "Piano");
        carga.executar(
                new ArquivoCarga(
                        List.of(
                                new ArquivoCarga.ProfessorCarga(
                                        "clara@exemplo.test", "Clara Souza", true)),
                        List.of(
                                new ArquivoCarga.AlunoCarga(
                                        "529.982.247-25",
                                        "Olavo Torrente",
                                        "1998-02-01",
                                        null,
                                        "Violão",
                                        List.of("clara@exemplo.test")),
                                new ArquivoCarga.AlunoCarga(
                                        "111.111.111-11",
                                        "Aluno Com Cpf Invalido",
                                        "2010-01-01",
                                        null,
                                        "Piano",
                                        List.of("clara@exemplo.test")))),
                false);

        // Web: código errado, código certo, registro, resposta, alteração e aprovação
        entrar("ZZZZ2222");
        Cookie sessao = entrar(CenarioIT.CODIGO_CLARA);
        mvc.perform(
                multipart("/api/v1/alunos/{id}/relatorios-aula", matteo.id())
                        .file(
                                new MockMultipartFile(
                                        "audio",
                                        "aula.webm",
                                        "audio/webm",
                                        NormalizacaoAudioTeste.AUDIO_COM_FALA))
                        .cookie(sessao)
                        .with(csrf().asHeader()));
        cenario.processarFila();
        UUID id = relatorio();
        postJson(
                sessao,
                "/api/v1/relatorios-aula/" + id + "/respostas",
                "{\"texto\":\"" + RESPOSTA + "\"}");
        cenario.processarFila();
        postJson(
                sessao,
                "/api/v1/relatorios-aula/" + id + "/alteracoes",
                "{\"texto\":\"" + ALTERACAO + "\",\"versaoBase\":1}");
        cenario.processarFila();
        postJson(
                sessao, "/api/v1/relatorios-aula/" + id + "/aprovacao", "{\"versaoConfirmada\":2}");

        // Telegram: código errado, vínculo, busca de aluno, áudio inválido e áudio com fala
        enviarTexto(USUARIO_BRUNO, "ZZZZ2222");
        vincular(USUARIO_CLARA, CenarioIT.CODIGO_CLARA);
        enviarTexto(USUARIO_CLARA, "Aluno Que Nao Existe");
        enviarTexto(USUARIO_CLARA, "registrar aula");
        enviarTexto(USUARIO_CLARA, "Olavo");
        prepararArquivoTelegram("voz-invalida", NormalizacaoAudioTeste.INVALIDO.getBytes());
        enviarVoz(USUARIO_CLARA, "voz-invalida", 10);
        enviarCallback(USUARIO_CLARA, "v1:confal:");
        cenario.processarFila();
        prepararArquivoTelegram("voz-boa", NormalizacaoAudioTeste.AUDIO_COM_FALA);
        enviarVoz(USUARIO_CLARA, "voz-boa", 100);
        enviarCallback(USUARIO_CLARA, "v1:confal:");
        cenario.processarFila();
        enviarTexto(USUARIO_CLARA, RESPOSTA);
        cenario.processarFila();

        String transcricao =
                Files.readString(Path.of("src/main/resources/ia/simulado/transcricao.txt")).strip();
        String logs = textoDosLogs();
        assertThat(eventos.list).as("a captura de logs funcionou").isNotEmpty();
        Map<String, String> proibidos =
                Map.ofEntries(
                        Map.entry("nome de aluno (Matteo)", "Matteo"),
                        Map.entry("nome de aluno (Olavo)", "Olavo"),
                        Map.entry("nome de aluno buscado", "Aluno Que Nao Existe"),
                        Map.entry("CPF", CPF),
                        Map.entry("CPF formatado", "529.982.247-25"),
                        Map.entry("CPF inválido da carga", "111.111.111-11"),
                        Map.entry("código de acesso", CenarioIT.CODIGO_CLARA),
                        Map.entry("código errado", "ZZZZ2222"),
                        Map.entry("token do bot", TOKEN),
                        Map.entry("token de sessão", sessao.getValue()),
                        Map.entry("resposta", RESPOSTA),
                        Map.entry("alteração", ALTERACAO),
                        Map.entry("transcrição", transcricao.substring(0, 40)),
                        Map.entry("conteúdo do relatório", "Czerny"),
                        Map.entry("bytes do áudio", "AUDIO-DE-AULA-FICTICIO"));
        proibidos.forEach(
                (descricao, valor) -> assertThat(logs).as(descricao).doesNotContain(valor));
    }
}
