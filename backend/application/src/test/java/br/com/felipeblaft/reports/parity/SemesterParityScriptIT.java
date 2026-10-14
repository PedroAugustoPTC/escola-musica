package br.com.felipeblaft.reports.parity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import br.com.felipeblaft.reports.config.MessageCatalog;
import br.com.felipeblaft.reports.domain.registry.Student;
import br.com.felipeblaft.reports.domain.registry.Teacher;
import br.com.felipeblaft.reports.support.ScenarioIT;
import br.com.felipeblaft.reports.support.TelegramConversationBase;
import jakarta.servlet.http.Cookie;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Paridade do semestral (R-16, SC-006): período sem aulas, geração, alteração, aprovação e nova
 * geração recusada, pela Web e pelo bot; o semestral resultante é igual, salvo o canal.
 */
class SemesterParityScriptIT extends TelegramConversationBase {

    @Autowired MessageCatalog catalog;

    private Teacher clara;
    private Student matteo;
    private Student helena;
    private Cookie session;

    interface SemesterChannel {
        String generateWithoutLessons() throws Exception;

        String generate() throws Exception;

        String change(String text) throws Exception;

        String approve() throws Exception;

        String generateAgain() throws Exception;
    }

    @BeforeEach
    void load() throws Exception {
        clara = scenario.clara();
        matteo = scenario.student(clara, "Matteo Ferreira", "Piano");
        helena = scenario.student(clara, "Helena Prado", "Violino");
        for (Student student : List.of(matteo, helena)) {
            scenario.approvedLesson(clara, student, LocalDate.of(2026, 3, 10), "Escala de Dó");
            scenario.approvedLesson(clara, student, LocalDate.of(2026, 5, 20), "Estudo nº 5");
        }
        link(USER_CLARA, ScenarioIT.CODE_CLARA);
        scenario.timePerJob = java.time.Duration.ofSeconds(2);
        MvcResult input =
                mvc.perform(
                                post("/api/v1/sessao")
                                        .with(csrf().asHeader())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("{\"codigo\":\"" + ScenarioIT.CODE_CLARA + "\"}"))
                        .andReturn();
        Cookie cookie = input.getResponse().getCookie("sessao");
        session = new Cookie(cookie.getName(), cookie.getValue());
    }

    @AfterEach
    void restoreClock() {
        scenario.timePerJob = java.time.Duration.ZERO;
    }

    /** Uma linha por tarefa de IA do relatório: quantas chamadas e se os dados de custo vieram. */
    private List<String> costs(Student student) {
        return jdbc.queryForList(
                "select task || ' x' || count(*) || ' sucesso=' || bool_and(success)"
                        + " || ' duracao=' || bool_and(duration_ms is not null)"
                        + " || ' custo=' || bool_and(estimated_cost_usd is not null)"
                        + " from ai_call where report_type = 'SEMESTRAL' and report_id = ?"
                        + " group by task order by task",
                String.class,
                semester(student));
    }

    private UUID semester(Student student) {
        return jdbc.queryForObject(
                "select id from semester_report where student_id = ? and status <> 'CANCELADO'",
                UUID.class,
                student.id());
    }

    private int current(Student student) {
        return jdbc.queryForObject(
                "select current_version from semester_report where id = ?",
                Integer.class,
                semester(student));
    }

    private String result(Student student, String type) {
        return jdbc.queryForObject(
                "select result_code from processing_job where student_id = ? and type = ?",
                String.class,
                student.id(),
                type);
    }

    private String webPost(String path, Object body) throws Exception {
        MvcResult result =
                mvc.perform(
                                post("/api/v1" + path)
                                        .cookie(session)
                                        .with(csrf().asHeader())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(JSON.writeValueAsString(body)))
                        .andReturn();
        return result.getResponse().getStatus() / 100 == 2
                ? "ACEITO"
                : JSON.readTree(result.getResponse().getContentAsString())
                        .path("codigo")
                        .asString();
    }

    private SemesterChannel web(Student student) {
        return new SemesterChannel() {
            @Override
            public String generateWithoutLessons() throws Exception {
                return webPost(
                        "/alunos/" + student.id() + "/relatorios-semestrais",
                        Map.of("periodoChave", "2026-2"));
            }

            @Override
            public String generate() throws Exception {
                webPost(
                        "/alunos/" + student.id() + "/relatorios-semestrais",
                        Map.of("periodoChave", "2026-1"));
                scenario.processQueue();
                return result(student, "CONSOLIDACAO_SEMESTRAL");
            }

            @Override
            public String change(String text) throws Exception {
                webPost(
                        "/relatorios-semestrais/" + semester(student) + "/alteracoes",
                        Map.of("texto", text, "versaoBase", current(student)));
                scenario.processQueue();
                return result(student, "ALTERACAO");
            }

            @Override
            public String approve() throws Exception {
                webPost(
                        "/relatorios-semestrais/" + semester(student) + "/aprovacao",
                        Map.of("versaoConfirmada", current(student)));
                return jdbc.queryForObject(
                        "select status from semester_report where student_id = ?",
                        String.class,
                        student.id());
            }

            @Override
            public String generateAgain() throws Exception {
                return webPost(
                        "/alunos/" + student.id() + "/relatorios-semestrais",
                        Map.of("periodoChave", "2026-1"));
            }
        };
    }

    /** Código do catálogo cujo texto (sem placeholders) é o da última mensagem do bot. */
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

    private SemesterChannel bot(Student student) {
        return new SemesterChannel() {
            private void chooseStudent() throws Exception {
                sendText(USER_CLARA, "semestral");
                sendText(USER_CLARA, student.name());
            }

            @Override
            public String generateWithoutLessons() throws Exception {
                chooseStudent();
                sendCallback(USER_CLARA, "v1:per:0");
                return lastMessageCode();
            }

            @Override
            public String generate() throws Exception {
                chooseStudent();
                sendCallback(USER_CLARA, "v1:per:1");
                sendCallback(USER_CLARA, "v1:gerar:");
                scenario.processQueue();
                return result(student, "CONSOLIDACAO_SEMESTRAL");
            }

            @Override
            public String change(String text) throws Exception {
                sendText(USER_CLARA, text);
                scenario.processQueue();
                return result(student, "ALTERACAO");
            }

            @Override
            public String approve() throws Exception {
                sendText(USER_CLARA, "aprovar");
                sendText(USER_CLARA, "sim");
                return jdbc.queryForObject(
                        "select status from semester_report where student_id = ?",
                        String.class,
                        student.id());
            }

            @Override
            public String generateAgain() throws Exception {
                chooseStudent();
                sendCallback(USER_CLARA, "v1:per:1");
                return lastMessageCode();
            }
        };
    }

    private static List<String> script(SemesterChannel channel) throws Exception {
        List<String> registration = new ArrayList<>();
        registration.add("período sem aulas = " + channel.generateWithoutLessons());
        registration.add("gerar = " + channel.generate());
        registration.add("alterar = " + channel.change("Parecer: excelente semestre."));
        registration.add("aprovar = " + channel.approve());
        registration.add("gerar de novo = " + channel.generateAgain());
        return registration;
    }

    private List<String> summary(Student student, String channel) {
        UUID id =
                jdbc.queryForObject(
                        "select id from semester_report where student_id = ?",
                        UUID.class,
                        student.id());
        List<String> rows = new ArrayList<>();
        rows.add(
                jdbc.queryForObject(
                        "select status || ' vigente=' || current_version || ' aprovada=' || approved_version"
                                + " || ' aulas=' || considered_lesson_count || ' periodo=' || period_key"
                                + " || ' origem=' || origin_channel || ' aprovacao=' || approval_channel"
                                + " || ' parecer=' || (content ->> 'finalOpinion')"
                                + " from semester_report where id = ?",
                        String.class,
                        id));
        rows.addAll(
                jdbc.queryForList(
                        "select type || ' ' || medium || ' ' || result || ' base=' || base_version || ' canal=' || channel"
                                + " from report_interaction where report_id = ? order by base_version",
                        String.class,
                        id));
        rows.addAll(
                jdbc.queryForList(
                        "select 'versão ' || v.number || ' ' || v.origin || ' ' || d.type from report_version v"
                                + " join pdf_document d on d.version_id = v.id where v.report_id = ?"
                                + " order by v.number, d.type",
                        String.class,
                        id));
        return rows.stream().map(l -> l.replace(channel, "CANAL")).toList();
    }

    @Test
    void sameScriptSameSemesterInBothChannels() throws Exception {
        List<String> naWeb = script(web(matteo));
        List<String> noBot = script(bot(helena));

        assertThat(noBot).containsExactlyElementsOf(naWeb);
        assertThat(naWeb)
                .containsExactly(
                        "período sem aulas = SEM_AULAS_APROVADAS_NO_PERIODO",
                        "gerar = PRONTO_PARA_CONFERENCIA",
                        "alterar = NOVA_VERSAO",
                        "aprovar = APROVADO",
                        "gerar de novo = SEMESTRAL_JA_APROVADO");
        List<String> summaryWeb = summary(matteo, "WEB");
        assertThat(summary(helena, "TELEGRAM")).containsExactlyElementsOf(summaryWeb);
        assertThat(summaryWeb.getFirst())
                .isEqualTo(
                        "APROVADO vigente=2 aprovada=2 aulas=2 periodo=2026-1 origem=CANAL aprovacao=CANAL"
                                + " parecer=Parecer: excelente semestre.");
        assertThat(summaryWeb)
                .contains("versão 2 ALTERACAO OFICIAL", "versão 1 CONSOLIDACAO CONFERENCIA");

        // FR-056: a consolidação e a revisão ligadas ao relatório, nos dois canais
        List<String> costsWeb = costs(matteo);
        assertThat(costs(helena)).containsExactlyElementsOf(costsWeb);
        assertThat(costsWeb)
                .containsExactly(
                        "CONSOLIDACAO x1 sucesso=true duracao=true custo=true",
                        "REVISAO x1 sucesso=true duracao=true custo=true");
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
                                    java.math.BigDecimal.class,
                                    semester(student)))
                    .isNotNull();
            assertThat(
                            jdbc.queryForObject(
                                    "select processing_time_ms from semester_report where id = ?",
                                    Long.class,
                                    semester(student)))
                    .as("tempo de processamento")
                    .isGreaterThan(0L);
        }
    }
}
