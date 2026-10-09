package br.com.felipeblaft.relatorios.iareal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import br.com.felipeblaft.relatorios.dominio.cadastro.Aluno;
import br.com.felipeblaft.relatorios.dominio.cadastro.Professor;
import br.com.felipeblaft.relatorios.suporte.CenarioIT;
import br.com.felipeblaft.relatorios.suporte.IntegracaoBase;
import jakarta.servlet.http.Cookie;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Suíte opcional com IA real (perfil Maven {@code ia-real}, fora da cobertura; R-16): processa as
 * gravações fictícias de {@code assets/} com FFmpeg e os provedores reais e mede o tempo até a
 * conferência (SC-001), a alteração por texto e por áudio (SC-013) e o semestral com 30 aulas
 * (SC-010), com o custo estimado de cada etapa. Escreve {@code target/ia-real/relatorio.md}.
 *
 * <p>Sem {@code TRANSCRICAO_CHAVE} e {@code LLM_CHAVE} no ambiente, a suíte é ignorada.
 */
@AutoConfigureMockMvc
@TestPropertySource(
        properties = {
            "relatorios.provedores.modo=real",
            "relatorios.teste.normalizacao-simulada=false"
        })
class IaRealSuiteIT extends IntegracaoBase {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final Duration META_CONFERENCIA = Duration.ofMinutes(2);
    private static final String RESPOSTA =
            "Estudou três vezes na semana e vai continuar com a mesma peça na próxima aula.";

    @Autowired private MockMvc mvc;
    @Autowired private CenarioIT cenario;

    private Cookie sessao;

    private record Medida(
            String etapa,
            String arquivo,
            double megabytes,
            long milissegundos,
            BigDecimal custo,
            String resultado) {}

    private final List<Medida> medidas = new ArrayList<>();

    /** O que mudou no relatório quando a IA incorporou a resposta às perguntas (FR-025). */
    private record Incorporacao(
            String arquivo, List<String> secoesAlteradas, boolean respostaSoNasObservacoes) {

        boolean adequada() {
            return !secoesAlteradas.isEmpty() && !respostaSoNasObservacoes;
        }
    }

    private static final List<String> SECOES_SEM_OBSERVACOES =
            List.of("conteudosTrabalhados", "evolucao", "dificuldades", "atividadesPropostas");

    private final List<Incorporacao> incorporacoes = new ArrayList<>();

    @Test
    void gravacoesDoClienteComIaReal() throws Exception {
        assumeTrue(
                temChave("TRANSCRICAO_CHAVE") && temChave("LLM_CHAVE"),
                "sem chaves dos provedores de IA");
        Path assets = Path.of(System.getenv().getOrDefault("ASSETS_DIR", "../../assets"));
        List<Path> gravacoes;
        try (Stream<Path> arquivos = Files.list(assets)) {
            gravacoes =
                    arquivos.filter(a -> a.getFileName().toString().matches("(?i).+\\.(ogg|mp4)$"))
                            .sorted()
                            .toList();
        }
        assumeTrue(!gravacoes.isEmpty(), "sem gravações em " + assets.toAbsolutePath());

        Professor clara = cenario.clara();
        sessao = entrar();
        UUID primeiroRelatorio = null;
        Path primeiraGravacao = null;
        for (Path gravacao : gravacoes) {
            Aluno aluno = cenario.aluno(clara, nomeDoAluno(gravacao), "Piano");
            UUID relatorio = registrar(aluno, gravacao);
            if (primeiroRelatorio == null && relatorio != null) {
                primeiroRelatorio = relatorio;
                primeiraGravacao = gravacao;
            }
        }
        assertThat(primeiroRelatorio).as("ao menos uma gravação chegou à conferência").isNotNull();

        alterarPorTexto(primeiroRelatorio);
        alterarPorAudio(primeiroRelatorio, primeiraGravacao);
        semestralComTrintaAulas(clara);

        escreverRelatorio();
        assertThat(medidas)
                .filteredOn(m -> m.etapa().equals("Registro até a conferência"))
                .allSatisfy(m -> assertThat(m.resultado()).isEqualTo("AGUARDANDO_CONFERENCIA"));
        // FR-025: a resposta entrou nas seções adequadas, não só anexada às observações
        assertThat(incorporacoes).as("ao menos uma gravação teve perguntas").isNotEmpty();
        assertThat(incorporacoes)
                .filteredOn(i -> !i.adequada())
                .as("incorporações que só anexaram a resposta às observações")
                .isEmpty();
    }

    // ------------------------------------------------------------------ etapas

    private UUID registrar(Aluno aluno, Path gravacao) throws Exception {
        long inicio = System.nanoTime();
        byte[] conteudo = Files.readAllBytes(gravacao);
        MvcResult envio =
                mvc.perform(
                                multipart("/api/v1/alunos/{id}/relatorios-aula", aluno.id())
                                        .file(
                                                new MockMultipartFile(
                                                        "audio",
                                                        "aula" + extensao(gravacao),
                                                        tipo(gravacao),
                                                        conteudo))
                                        .cookie(sessao)
                                        .with(csrf().asHeader()))
                        .andReturn();
        if (envio.getResponse().getStatus() != 202) {
            medidas.add(
                    new Medida(
                            "Registro até a conferência",
                            nome(gravacao),
                            mb(conteudo.length),
                            millis(inicio),
                            BigDecimal.ZERO,
                            "RECUSADO " + envio.getResponse().getStatus()));
            return null;
        }
        cenario.processarFila();
        UUID relatorio = relatorioDoAluno(aluno);
        if (relatorio != null && "AGUARDANDO_RESPOSTAS".equals(status(relatorio))) {
            JsonNode antes = conteudo(relatorio);
            postar(
                    "/api/v1/relatorios-aula/" + relatorio + "/respostas",
                    Map.of("texto", RESPOSTA));
            cenario.processarFila();
            incorporacoes.add(incorporacao(nome(gravacao), antes, conteudo(relatorio)));
        }
        String resultado = relatorio == null ? resultadoDoUltimoTrabalho() : status(relatorio);
        medidas.add(
                new Medida(
                        "Registro até a conferência",
                        nome(gravacao),
                        mb(conteudo.length),
                        millis(inicio),
                        custo(relatorio),
                        resultado));
        return "AGUARDANDO_CONFERENCIA".equals(resultado) ? relatorio : null;
    }

    private JsonNode conteudo(UUID relatorio) {
        return JSON.readTree(
                jdbc.queryForObject(
                        "select conteudo::text from relatorio_aula where id = ?",
                        String.class,
                        relatorio));
    }

    private static Incorporacao incorporacao(String arquivo, JsonNode antes, JsonNode depois) {
        List<String> alteradas =
                SECOES_SEM_OBSERVACOES.stream()
                        .filter(secao -> !antes.path(secao).equals(depois.path(secao)))
                        .toList();
        String observacoes = depois.path("observacoes").asString("");
        boolean soAnexou =
                alteradas.isEmpty() && !observacoes.equals(antes.path("observacoes").asString(""));
        return new Incorporacao(arquivo, alteradas, soAnexou);
    }

    private void alterarPorTexto(UUID relatorio) throws Exception {
        BigDecimal antes = custo(relatorio);
        long inicio = System.nanoTime();
        postar(
                "/api/v1/relatorios-aula/" + relatorio + "/alteracoes",
                Map.of(
                        "texto",
                        "Acrescente nas observações que o aluno chegou pontualmente e com o material.",
                        "versaoBase",
                        versao(relatorio)));
        cenario.processarFila();
        medidas.add(
                new Medida(
                        "Alteração por texto",
                        "—",
                        0,
                        millis(inicio),
                        custo(relatorio).subtract(antes),
                        "versão " + versao(relatorio)));
    }

    private void alterarPorAudio(UUID relatorio, Path gravacao) throws Exception {
        Path trecho = Files.createTempFile("trecho-", ".ogg");
        try {
            Process ffmpeg =
                    new ProcessBuilder(
                                    "ffmpeg",
                                    "-y",
                                    "-loglevel",
                                    "error",
                                    "-i",
                                    gravacao.toString(),
                                    "-t",
                                    "15",
                                    "-vn",
                                    "-c:a",
                                    "libopus",
                                    trecho.toString())
                            .redirectErrorStream(true)
                            .start();
            assertThat(ffmpeg.waitFor(60, TimeUnit.SECONDS)).isTrue();
            byte[] conteudo = Files.readAllBytes(trecho);
            BigDecimal antes = custo(relatorio);
            int versaoAntes = versao(relatorio);
            long inicio = System.nanoTime();
            mvc.perform(
                    multipart("/api/v1/relatorios-aula/{id}/alteracoes", relatorio)
                            .file(
                                    new MockMultipartFile(
                                            "audio", "alteracao.ogg", "audio/ogg", conteudo))
                            .param("versaoBase", Integer.toString(versaoAntes))
                            .cookie(sessao)
                            .with(csrf().asHeader()));
            cenario.processarFila();
            medidas.add(
                    new Medida(
                            "Alteração por áudio (15 s)",
                            nome(gravacao),
                            mb(conteudo.length),
                            millis(inicio),
                            custo(relatorio).subtract(antes),
                            versao(relatorio) > versaoAntes
                                    ? "versão " + versao(relatorio)
                                    : resultadoDoUltimoTrabalho()));
        } finally {
            Files.deleteIfExists(trecho);
        }
    }

    private void semestralComTrintaAulas(Professor clara) throws Exception {
        Aluno aluno = cenario.aluno(clara, "Aluno Fictício do Semestral", "Piano");
        List<String> temas =
                List.of(
                        "Escala de Dó maior em duas oitavas",
                        "Estudo nº 5 de Czerny, op. 599",
                        "Leitura à primeira vista em clave de fá",
                        "Minueto em Sol maior de Petzold",
                        "Arpejos de Lá menor",
                        "Dinâmica e fraseado na sonatina de Clementi");
        LocalDate data = LocalDate.of(2026, 1, 12);
        for (int i = 0; i < 30; i++) {
            cenario.aulaAprovada(clara, aluno, data.plusDays(5L * i), temas.get(i % temas.size()));
        }
        long inicio = System.nanoTime();
        postar(
                "/api/v1/alunos/" + aluno.id() + "/relatorios-semestrais",
                Map.of("periodoChave", "2026-1"));
        cenario.processarFila();
        BigDecimal custo =
                jdbc.queryForObject(
                        "select coalesce(sum(custo_estimado_usd), 0) from chamada_ia where relatorio_tipo = 'SEMESTRAL'",
                        BigDecimal.class);
        String status =
                jdbc.queryForList("select status from relatorio_semestral", String.class).stream()
                        .findFirst()
                        .orElse(resultadoDoUltimoTrabalho());
        medidas.add(new Medida("Semestral com 30 aulas", "—", 0, millis(inicio), custo, status));
    }

    // ------------------------------------------------------------------ relatório

    private void escreverRelatorio() throws IOException {
        StringBuilder md = new StringBuilder("# Suíte com IA real\n\n");
        md.append("Provedores: transcrição `")
                .append(System.getenv().getOrDefault("TRANSCRICAO_MODELO", "padrão"))
                .append("`, LLM `")
                .append(System.getenv().getOrDefault("LLM_MODELO", "padrão"))
                .append("`.\n\n");
        md.append("| Etapa | Arquivo | MB | Tempo (s) | Custo (USD) | Resultado |\n");
        md.append("|---|---|---:|---:|---:|---|\n");
        for (Medida m : medidas) {
            md.append(
                    String.format(
                            Locale.ROOT,
                            "| %s | %s | %.1f | %.1f | %.4f | %s |%n",
                            m.etapa(),
                            m.arquivo(),
                            m.megabytes(),
                            m.milissegundos() / 1000.0,
                            m.custo(),
                            m.resultado()));
        }
        List<Medida> registros =
                medidas.stream()
                        .filter(m -> m.etapa().equals("Registro até a conferência"))
                        .toList();
        long dentroDaMeta =
                registros.stream()
                        .filter(m -> m.milissegundos() <= META_CONFERENCIA.toMillis())
                        .count();
        md.append(
                String.format(
                        Locale.ROOT,
                        "%nSC-001: %d de %d gravações chegaram à conferência em até 2 min (meta: 90%%).%n",
                        dentroDaMeta,
                        registros.size()));
        md.append(
                String.format(
                        Locale.ROOT,
                        "Custo total estimado: US$ %.4f.%n",
                        medidas.stream()
                                .map(Medida::custo)
                                .reduce(BigDecimal.ZERO, BigDecimal::add)));
        md.append("\n## Incorporação das respostas às perguntas (FR-025)\n\n");
        md.append("| Arquivo | Seções alteradas além de `observacoes` | Resultado |\n");
        md.append("|---|---|---|\n");
        for (Incorporacao i : incorporacoes) {
            md.append(
                    String.format(
                            Locale.ROOT,
                            "| %s | %s | %s |%n",
                            i.arquivo(),
                            i.secoesAlteradas().isEmpty()
                                    ? "—"
                                    : String.join(", ", i.secoesAlteradas()),
                            i.adequada()
                                    ? "incorporada nas seções"
                                    : "apenas anexada às observações"));
        }
        Path destino = Path.of("target", "ia-real", "relatorio.md");
        Files.createDirectories(destino.getParent());
        Files.writeString(destino, md);
    }

    // ------------------------------------------------------------------ apoio

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

    private void postar(String caminho, Object corpo) throws Exception {
        mvc.perform(
                post(caminho)
                        .cookie(sessao)
                        .with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.writeValueAsString(corpo)));
    }

    private int versao(UUID relatorio) throws Exception {
        JsonNode dto =
                JSON.readTree(
                        mvc.perform(get("/api/v1/relatorios-aula/{id}", relatorio).cookie(sessao))
                                .andReturn()
                                .getResponse()
                                .getContentAsString());
        return dto.path("versaoVigente").asInt();
    }

    private UUID relatorioDoAluno(Aluno aluno) {
        return jdbc
                .queryForList(
                        "select id from relatorio_aula where aluno_id = ?", UUID.class, aluno.id())
                .stream()
                .findFirst()
                .orElse(null);
    }

    private String status(UUID relatorio) {
        return jdbc.queryForObject(
                "select status from relatorio_aula where id = ?", String.class, relatorio);
    }

    private String resultadoDoUltimoTrabalho() {
        return jdbc
                .queryForList(
                        "select coalesce(resultado_codigo, status) from trabalho_processamento order by criado_em desc limit 1",
                        String.class)
                .stream()
                .findFirst()
                .orElse("SEM TRABALHO");
    }

    private BigDecimal custo(UUID relatorio) {
        if (relatorio == null) {
            return BigDecimal.ZERO;
        }
        return jdbc.queryForObject(
                "select coalesce(sum(custo_estimado_usd), 0) from chamada_ia where relatorio_id = ?",
                BigDecimal.class,
                relatorio);
    }

    private static boolean temChave(String variavel) {
        String valor = System.getenv(variavel);
        return valor != null && !valor.isBlank();
    }

    /**
     * "Aula Matteo Mafra_21_09_2026.ogg" → "Matteo Mafra" (nomes fictícios das gravações de teste).
     */
    static String nomeDoAluno(Path gravacao) {
        String nome = gravacao.getFileName().toString().replaceFirst("(?i)^Aula\\s+", "");
        int fim = nome.indexOf('_');
        return (fim > 0 ? nome.substring(0, fim) : nome.replaceFirst("\\.[^.]+$", "")).strip();
    }

    private static String nome(Path gravacao) {
        return gravacao.getFileName().toString();
    }

    private static String extensao(Path gravacao) {
        String nome = nome(gravacao);
        return nome.substring(nome.lastIndexOf('.')).toLowerCase(Locale.ROOT);
    }

    private static String tipo(Path gravacao) {
        return extensao(gravacao).equals(".mp4") ? "video/mp4" : "audio/ogg";
    }

    private static double mb(long bytes) {
        return bytes / (1024.0 * 1024.0);
    }

    private static long millis(long inicioNano) {
        return (System.nanoTime() - inicioNano) / 1_000_000;
    }
}
