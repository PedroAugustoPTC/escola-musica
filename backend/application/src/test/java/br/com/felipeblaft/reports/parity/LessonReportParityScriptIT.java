package br.com.felipeblaft.reports.parity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import br.com.felipeblaft.reports.config.MessageCatalog;
import br.com.felipeblaft.reports.domain.registry.Student;
import br.com.felipeblaft.reports.domain.registry.Teacher;
import br.com.felipeblaft.reports.port.LlmPort;
import br.com.felipeblaft.reports.support.AudioNormalizationTest;
import br.com.felipeblaft.reports.support.ProgrammableLlmTest;
import br.com.felipeblaft.reports.support.ScenarioIT;
import br.com.felipeblaft.reports.support.TelegramConversationBase;
import jakarta.servlet.http.Cookie;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/**
 * Paridade do relatório de aula (R-16, SC-006): o mesmo roteiro — enviar áudio, responder, alterar
 * por texto, alterar por áudio, aprovar e tentar aprovar de novo — na Web e no Telegram, com IA
 * simulada. Estados, versões, interações (meio e resultado), PDFs e aprovação devem ser iguais; só
 * o canal registrado muda.
 */
class LessonReportParityScriptIT extends TelegramConversationBase {

    @Autowired MessageCatalog catalog;

    @MockitoSpyBean LlmPort llm;

    private Student matteo;
    private Student helena;

    /** Operações do roteiro num canal; cada uma devolve um código neutro. */
    interface ReportChannel {
        String register() throws Exception;

        String answer(String text) throws Exception;

        String changeByText(String text) throws Exception;

        String changeByAudio() throws Exception;

        String approve() throws Exception;

        String approveAgain() throws Exception;
    }

    @BeforeEach
    void load() {
        Teacher clara = scenario.clara();
        matteo = scenario.student(clara, "Matteo Ferreira", "Piano");
        helena = scenario.student(clara, "Helena Prado", "Violino");
        scenario.timePerJob = Duration.ofSeconds(2);
    }

    @AfterEach
    void restoreClock() {
        scenario.timePerJob = Duration.ZERO;
    }

    /** Uma linha por tarefa de IA do relatório: quantas chamadas e se os dados de custo vieram. */
    private List<String> costs(Student student) {
        return jdbc.queryForList(
                "select task || ' x' || count(*) || ' sucesso=' || bool_and(success)"
                        + " || ' duracao=' || bool_and(duration_ms is not null)"
                        + " || ' custo=' || bool_and(estimated_cost_usd is not null)"
                        + " from ai_call where report_type = 'AULA' and report_id = ?"
                        + " group by task order by task",
                String.class,
                reportOf(student));
    }

    // ---------------------------------------------------------------- consultas neutras

    private UUID reportOf(Student student) {
        return jdbc.queryForObject(
                "select id from lesson_report where student_id = ?", UUID.class, student.id());
    }

    /** Resultado do trabalho do tipo e meio dados (o relógio fixo empata as datas). */
    private String result(Student student, String type, String medium) {
        return jdbc.queryForObject(
                "select result_code from processing_job"
                        + " where student_id = ? and type = ? and medium = ?"
                        + " order by completed_at desc limit 1",
                String.class,
                student.id(),
                type,
                medium);
    }

    private int currentVersion(Student student) {
        return jdbc.queryForObject(
                "select current_version from lesson_report where student_id = ?",
                Integer.class,
                student.id());
    }

    /** Estado final do relatório sem identificadores, com o canal trocado por um marcador. */
    private List<String> summary(Student student, String channel) {
        UUID id = reportOf(student);
        List<String> rows = new ArrayList<>();
        rows.add(
                jdbc.queryForObject(
                        "select status || ' vigente=' || current_version"
                                + " || ' aprovada=' || coalesce(approved_version::text, '-')"
                                + " || ' canalOrigem=' || origin_channel"
                                + " || ' canalAprovacao=' || coalesce(approval_channel, '-')"
                                + " from lesson_report where id = ?",
                        String.class,
                        id));
        rows.addAll(
                jdbc.queryForList(
                        "select type || ' ' || medium || ' ' || result || ' base=' || base_version"
                                + " || ' resultante=' || coalesce(resulting_version::text, '-')"
                                + " || ' canal=' || channel"
                                + " from report_interaction where report_id = ? order by base_version, type",
                        String.class,
                        id));
        rows.addAll(
                jdbc.queryForList(
                        "select 'versão ' || number || ' ' || origin || ' canal=' || channel"
                                + " from report_version where report_id = ? order by number",
                        String.class,
                        id));
        rows.add(
                "pdfs="
                        + jdbc.queryForList(
                                "select d.type from pdf_document d join report_version v on v.id = d.version_id"
                                        + " where v.report_id = ? order by v.number, d.type",
                                String.class,
                                id));
        return rows.stream().map(l -> l.replace(channel, "CANAL")).toList();
    }

    private static ParityScript script(ReportChannel channel) {
        return new ParityScript()
                .step("enviar áudio", c -> channel.register())
                .step("responder", c -> channel.answer("Estudou bem; vai preparar a valsa."))
                .step("alterar por texto", c -> channel.changeByText("Diga que a aula foi ótima."))
                .step("alterar por áudio", c -> channel.changeByAudio())
                .step("aprovar", c -> channel.approve())
                .step("aprovar de novo", c -> channel.approveAgain());
    }

    // ---------------------------------------------------------------- Web

    private ReportChannel web(Cookie session, Student student) {
        return new ReportChannel() {
            private int statusBody(org.springframework.test.web.servlet.MvcResult result) {
                return result.getResponse().getStatus();
            }

            private String code(org.springframework.test.web.servlet.MvcResult result)
                    throws Exception {
                int status = statusBody(result);
                if (status / 100 == 2) {
                    return "ACEITO";
                }
                return JSON.readTree(result.getResponse().getContentAsString())
                        .path("codigo")
                        .asString();
            }

            private MockMultipartFile audio() {
                return new MockMultipartFile(
                        "audio",
                        "aula.webm",
                        "audio/webm",
                        AudioNormalizationTest.AUDIO_WITH_SPEECH);
            }

            @Override
            public String register() throws Exception {
                code(
                        mvc.perform(
                                        multipart(
                                                        "/api/v1/alunos/{id}/relatorios-aula",
                                                        student.id())
                                                .file(audio())
                                                .cookie(session)
                                                .with(csrf().asHeader()))
                                .andReturn());
                scenario.processQueue();
                return result(student, "REGISTRO_AULA", "AUDIO");
            }

            @Override
            public String answer(String text) throws Exception {
                mvc.perform(
                        post("/api/v1/relatorios-aula/{id}/respostas", reportOf(student))
                                .cookie(session)
                                .with(csrf().asHeader())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(JSON.writeValueAsString(java.util.Map.of("texto", text))));
                scenario.processQueue();
                return result(student, "RESPOSTA_PERGUNTAS", "TEXTO");
            }

            @Override
            public String changeByText(String text) throws Exception {
                mvc.perform(
                        post("/api/v1/relatorios-aula/{id}/alteracoes", reportOf(student))
                                .cookie(session)
                                .with(csrf().asHeader())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        JSON.writeValueAsString(
                                                java.util.Map.of(
                                                        "texto",
                                                        text,
                                                        "versaoBase",
                                                        currentVersion(student)))));
                scenario.processQueue();
                return result(student, "ALTERACAO", "TEXTO");
            }

            @Override
            public String changeByAudio() throws Exception {
                mvc.perform(
                        multipart("/api/v1/relatorios-aula/{id}/alteracoes", reportOf(student))
                                .file(audio())
                                .param("versaoBase", String.valueOf(currentVersion(student)))
                                .cookie(session)
                                .with(csrf().asHeader()));
                scenario.processQueue();
                return result(student, "ALTERACAO", "AUDIO");
            }

            private String approveVersion() throws Exception {
                return code(
                        mvc.perform(
                                        post(
                                                        "/api/v1/relatorios-aula/{id}/aprovacao",
                                                        reportOf(student))
                                                .cookie(session)
                                                .with(csrf().asHeader())
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .content(
                                                        "{\"versaoConfirmada\":"
                                                                + currentVersion(student)
                                                                + "}"))
                                .andReturn());
            }

            @Override
            public String approve() throws Exception {
                approveVersion();
                return jdbc.queryForObject(
                        "select status from lesson_report where student_id = ?",
                        String.class,
                        student.id());
            }

            @Override
            public String approveAgain() throws Exception {
                return approveVersion();
            }
        };
    }

    // ---------------------------------------------------------------- Telegram

    /** Código do catálogo cujo texto é o da última mensagem (o Telegram responde com texto). */
    private String lastMessageCode() {
        String text = lastMessage().get("text").asString();
        String better = text;
        int size = 0;
        for (String key : catalog.keys()) {
            String model = catalog.model(key);
            if (!model.contains("{") && model.length() > size && text.contains(model)) {
                better = key;
                size = model.length();
            }
        }
        return better;
    }

    private ReportChannel telegram(Student student) {
        return new ReportChannel() {
            @Override
            public String register() throws Exception {
                sendText(USER_CLARA, "registrar aula");
                sendText(USER_CLARA, student.name());
                prepareTelegramFile(
                        "aula-" + student.id(), AudioNormalizationTest.AUDIO_WITH_SPEECH);
                sendVoice(USER_CLARA, "aula-" + student.id(), 1000);
                sendCallback(USER_CLARA, "v1:confal:");
                scenario.processQueue();
                return result(student, "REGISTRO_AULA", "AUDIO");
            }

            @Override
            public String answer(String text) throws Exception {
                sendText(USER_CLARA, text);
                scenario.processQueue();
                return result(student, "RESPOSTA_PERGUNTAS", "TEXTO");
            }

            @Override
            public String changeByText(String text) throws Exception {
                sendText(USER_CLARA, text);
                scenario.processQueue();
                return result(student, "ALTERACAO", "TEXTO");
            }

            @Override
            public String changeByAudio() throws Exception {
                prepareTelegramFile(
                        "pedido-" + student.id(), AudioNormalizationTest.AUDIO_WITH_SPEECH);
                sendVoice(USER_CLARA, "pedido-" + student.id(), 1000);
                scenario.processQueue();
                return result(student, "ALTERACAO", "AUDIO");
            }

            @Override
            public String approve() throws Exception {
                sendText(USER_CLARA, "aprovar");
                sendText(USER_CLARA, "sim");
                return jdbc.queryForObject(
                        "select status from lesson_report where student_id = ?",
                        String.class,
                        student.id());
            }

            @Override
            public String approveAgain() throws Exception {
                API_BOTS.resetRequests();
                sendCallback(USER_CLARA, "v1:foco:a" + reportOf(student));
                return lastMessageCode();
            }
        };
    }

    @Test
    void sameScriptSameReportInBothChannels() throws Exception {
        Cookie session = signInOnWeb();
        List<String> naWeb = script(web(session, matteo)).run(null);
        link(USER_CLARA, ScenarioIT.CODE_CLARA);
        List<String> noBot = script(telegram(helena)).run(null);

        assertThat(noBot).containsExactlyElementsOf(naWeb);
        assertThat(naWeb)
                .containsExactly(
                        "enviar áudio = PERGUNTAS_PENDENTES",
                        "responder = PRONTO_PARA_CONFERENCIA",
                        "alterar por texto = NOVA_VERSAO",
                        "alterar por áudio = NOVA_VERSAO",
                        "aprovar = APROVADO",
                        "aprovar de novo = RELATORIO_JA_APROVADO");

        List<String> summaryWeb = summary(matteo, "WEB");
        assertThat(summary(helena, "TELEGRAM")).containsExactlyElementsOf(summaryWeb);
        assertThat(summaryWeb)
                .contains("APROVADO vigente=3 aprovada=3 canalOrigem=CANAL canalAprovacao=CANAL")
                .anySatisfy(
                        l -> assertThat(l).startsWith("RESPOSTA_PERGUNTAS TEXTO APLICADA base=0"))
                .anySatisfy(l -> assertThat(l).startsWith("ALTERACAO TEXTO APLICADA base=1"))
                .anySatisfy(l -> assertThat(l).startsWith("ALTERACAO AUDIO APLICADA base=2"))
                .anySatisfy(l -> assertThat(l).startsWith("pdfs=").contains("OFICIAL"));

        // FR-056: uma linha por chamada de IA, todas ligadas ao relatório, nos dois canais
        List<String> costsWeb = costs(matteo);
        assertThat(costs(helena)).containsExactlyElementsOf(costsWeb);
        assertThat(costsWeb)
                .containsExactly(
                        "ESTRUTURACAO x1 sucesso=true duracao=true custo=true",
                        "INCORPORACAO_RESPOSTAS x1 sucesso=true duracao=true custo=true",
                        "REVISAO x2 sucesso=true duracao=true custo=true",
                        "TRANSCRICAO x2 sucesso=true duracao=true custo=true");
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from ai_call where report_id is null",
                                Integer.class))
                .as("chamadas sem relatório")
                .isZero();
        for (Student student : List.of(matteo, helena)) {
            assertThat(
                            jdbc.queryForObject(
                                    "select sum(estimated_cost_usd) from ai_call where report_id = ?",
                                    BigDecimal.class,
                                    reportOf(student)))
                    .as("custo total do relatório")
                    .isNotNull();
            assertThat(
                            jdbc.queryForObject(
                                    "select processing_time_ms from lesson_report where id = ?",
                                    Long.class,
                                    reportOf(student)))
                    .as("tempo de processamento")
                    .isGreaterThan(0L);
        }
    }

    private static ParityScript twoRoundScript(ReportChannel channel) {
        return new ParityScript()
                .step("enviar áudio", c -> channel.register())
                .step("1ª resposta", c -> channel.answer("Foi o Czerny."))
                .step("2ª resposta", c -> channel.answer("Escala de Dó maior."));
    }

    /** Rodada e resultado de cada resposta às perguntas, na ordem em que foram dadas. */
    private List<String> rounds(Student student) {
        return jdbc.queryForList(
                "select 'rodada ' || round || ' ' || result from report_interaction"
                        + " where report_id = ? and type = 'RESPOSTA_PERGUNTAS' order by round",
                String.class,
                reportOf(student));
    }

    @Test
    void twoQuestionRoundsAndThirdIgnoredInBothChannels() throws Exception {
        ProgrammableLlmTest programmable =
                new ProgrammableLlmTest()
                        .questionsInIncorporations(
                                List.of("E a escala?"),
                                List.of("Mais alguma coisa?"),
                                List.of("E a escala?"),
                                List.of("Mais alguma coisa?"));
        doAnswer(programmable).when(llm).generateJson(any(), any(), any());
        Cookie session = signInOnWeb();
        link(USER_CLARA, ScenarioIT.CODE_CLARA);

        List<String> naWeb = twoRoundScript(web(session, matteo)).run(null);
        List<String> noBot = twoRoundScript(telegram(helena)).run(null);

        assertThat(noBot).containsExactlyElementsOf(naWeb);
        assertThat(naWeb)
                .containsExactly(
                        "enviar áudio = PERGUNTAS_PENDENTES",
                        "1ª resposta = PERGUNTAS_PENDENTES",
                        "2ª resposta = PRONTO_PARA_CONFERENCIA");
        List<String> summaryWeb = summary(matteo, "WEB");
        assertThat(summary(helena, "TELEGRAM")).containsExactlyElementsOf(summaryWeb);
        assertThat(summaryWeb.getFirst())
                .isEqualTo(
                        "AGUARDANDO_CONFERENCIA vigente=1 aprovada=- canalOrigem=CANAL canalAprovacao=-");
        assertThat(summaryWeb)
                .contains("versão 1 RESPOSTA_PERGUNTAS canal=CANAL")
                .noneMatch(l -> l.startsWith("versão 2"));
        for (Student student : List.of(matteo, helena)) {
            assertThat(rounds(student)).containsExactly("rodada 1 APLICADA", "rodada 2 APLICADA");
            assertThat(
                            jdbc.queryForObject(
                                    "select question_rounds from lesson_report where id = ?",
                                    Integer.class,
                                    reportOf(student)))
                    .isEqualTo(2);
            assertThat(
                            jdbc.queryForObject(
                                    "select jsonb_array_length(pending_questions)"
                                            + " from lesson_report where id = ?",
                                    Integer.class,
                                    reportOf(student)))
                    .as("as perguntas da 3ª rodada são ignoradas")
                    .isZero();
        }
        // a IA recebeu a 2ª rodada com as perguntas da 1ª incorporação, em cada canal
        assertThat(programmable.incorporations()).hasSize(4);
        assertThat(programmable.incorporations().get(1).questions()).containsExactly("E a escala?");
        assertThat(programmable.incorporations().get(3).questions()).containsExactly("E a escala?");
    }

    private Cookie signInOnWeb() throws Exception {
        var result =
                mvc.perform(
                                post("/api/v1/sessao")
                                        .with(csrf().asHeader())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("{\"codigo\":\"" + ScenarioIT.CODE_CLARA + "\"}"))
                        .andReturn();
        Cookie cookie = result.getResponse().getCookie("sessao");
        return new Cookie(cookie.getName(), cookie.getValue());
    }
}
