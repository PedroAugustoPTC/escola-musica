package br.com.felipeblaft.relatorios.privacidade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import br.com.felipeblaft.relatorios.dominio.cadastro.Aluno;
import br.com.felipeblaft.relatorios.dominio.cadastro.Professor;
import br.com.felipeblaft.relatorios.suporte.CenarioIT;
import br.com.felipeblaft.relatorios.suporte.NormalizacaoAudioTeste;
import br.com.felipeblaft.relatorios.suporte.TelegramConversaBase;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;

/**
 * FR-018 e quickstart V12: depois de sucesso e de falha, nos dois canais, não sobra áudio no
 * diretório temporário nem no volume do servidor local da API de bots, e o banco não guarda bytes
 * de áudio em nenhuma coluna.
 */
class NaoRetencaoAudioIT extends TelegramConversaBase {

    private static final String MARCA = "AUDIO-DE-AULA-FICTICIO";

    private Cookie sessao;
    private Aluno matteo;
    private Aluno olavo;

    @BeforeEach
    void preparar() throws Exception {
        // isola de arquivos que outros testes deixaram de propósito (ex.: áudio nunca baixado)
        for (Path diretorio :
                List.of(propriedades.audio().tempDir(), propriedades.telegram().arquivosDir())) {
            for (Path arquivo : arquivos(diretorio)) {
                Files.deleteIfExists(arquivo);
            }
        }
        Professor clara = cenario.clara();
        matteo = cenario.aluno(clara, "Matteo Mafra", "Piano");
        olavo = cenario.aluno(clara, "Olavo Torrente", "Violão");
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

    private void enviarPelaWeb(Aluno aluno, byte[] conteudo) throws Exception {
        mvc.perform(
                multipart("/api/v1/alunos/{id}/relatorios-aula", aluno.id())
                        .file(new MockMultipartFile("audio", "aula.webm", "audio/webm", conteudo))
                        .cookie(sessao)
                        .with(csrf().asHeader()));
        cenario.processarFila();
    }

    private void enviarPeloBot(Aluno aluno, String fileId, byte[] conteudo) throws Exception {
        enviarTexto(USUARIO_CLARA, "registrar aula");
        enviarTexto(USUARIO_CLARA, aluno.nome());
        prepararArquivoTelegram(fileId, conteudo);
        enviarVoz(USUARIO_CLARA, fileId, conteudo.length);
        enviarCallback(USUARIO_CLARA, "v1:confal:");
        cenario.processarFila();
    }

    private static List<Path> arquivos(Path diretorio) throws Exception {
        if (!Files.isDirectory(diretorio)) {
            return List.of();
        }
        try (Stream<Path> caminhos = Files.walk(diretorio)) {
            return caminhos.filter(Files::isRegularFile).toList();
        }
    }

    private void semAudioRetido() throws Exception {
        assertThat(arquivos(propriedades.audio().tempDir())).as("diretório temporário").isEmpty();
        assertThat(arquivos(propriedades.telegram().arquivosDir()))
                .as("volume do telegram-bot-api")
                .isEmpty();
    }

    @Test
    void sucessoNosDoisCanaisNaoDeixaAudio() throws Exception {
        enviarPelaWeb(matteo, NormalizacaoAudioTeste.AUDIO_COM_FALA);
        enviarPeloBot(olavo, "voz-sucesso", NormalizacaoAudioTeste.AUDIO_COM_FALA);

        assertThat(
                        jdbc.queryForList(
                                "select status from relatorio_aula order by criado_em",
                                String.class))
                .containsExactly("AGUARDANDO_RESPOSTAS", "AGUARDANDO_RESPOSTAS");
        semAudioRetido();
    }

    @Test
    void falhaNosDoisCanaisNaoDeixaAudio() throws Exception {
        enviarPelaWeb(matteo, NormalizacaoAudioTeste.SILENCIO.getBytes(StandardCharsets.UTF_8));
        enviarPelaWeb(olavo, NormalizacaoAudioTeste.INVALIDO.getBytes(StandardCharsets.UTF_8));
        enviarPeloBot(
                matteo,
                "voz-silencio",
                NormalizacaoAudioTeste.SILENCIO.getBytes(StandardCharsets.UTF_8));
        enviarPeloBot(
                olavo,
                "voz-invalida",
                NormalizacaoAudioTeste.INVALIDO.getBytes(StandardCharsets.UTF_8));

        assertThat(jdbc.queryForObject("select count(*) from aula", Integer.class)).isZero();
        semAudioRetido();
    }

    @Test
    void bancoNaoGuardaBytesDeAudio() throws Exception {
        enviarPelaWeb(matteo, NormalizacaoAudioTeste.AUDIO_COM_FALA);
        enviarPeloBot(olavo, "voz-banco", NormalizacaoAudioTeste.AUDIO_COM_FALA);

        List<Map<String, Object>> binarias =
                jdbc.queryForList(
                        "select table_name, column_name from information_schema.columns"
                                + " where table_schema = 'public' and data_type = 'bytea'");
        assertThat(binarias)
                .extracting(c -> c.get("table_name") + "." + c.get("column_name"))
                .containsExactly("aluno.cpf_cifrado");

        List<String> comMarca = new ArrayList<>();
        for (Map<String, Object> coluna :
                jdbc.queryForList(
                        "select table_name, column_name from information_schema.columns"
                                + " where table_schema = 'public'"
                                + " and data_type in ('text', 'character varying', 'jsonb')"
                                + " and table_name <> 'flyway_schema_history'")) {
            String tabela = (String) coluna.get("table_name");
            String nome = (String) coluna.get("column_name");
            Integer quantidade =
                    jdbc.queryForObject(
                            "select count(*) from \""
                                    + tabela
                                    + "\" where \""
                                    + nome
                                    + "\"::text like ?",
                            Integer.class,
                            "%" + MARCA + "%");
            if (quantidade != null && quantidade > 0) {
                comMarca.add(tabela + "." + nome);
            }
        }
        assertThat(comMarca).as("colunas com o conteúdo do áudio").isEmpty();
    }
}
