package br.com.felipeblaft.reports.outbound.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.felipeblaft.reports.domain.channel.ChannelAction;
import br.com.felipeblaft.reports.domain.job.ProcessingJob;
import br.com.felipeblaft.reports.domain.report.LessonReport;
import br.com.felipeblaft.reports.domain.report.LessonReportContent;
import br.com.felipeblaft.reports.domain.report.ReportStatus;
import br.com.felipeblaft.reports.domain.semester.SemesterReport;
import br.com.felipeblaft.reports.domain.telegram.ConversationFlow;
import br.com.felipeblaft.reports.domain.telegram.ConversationFlowStep;
import br.com.felipeblaft.reports.domain.telegram.TelegramLink;
import br.com.felipeblaft.reports.port.ChannelActionRepository;
import br.com.felipeblaft.reports.port.ChannelDocumentRepository;
import br.com.felipeblaft.reports.port.JobQueuePort;
import br.com.felipeblaft.reports.port.LessonReportRepository;
import br.com.felipeblaft.reports.port.SemesterReportRepository;
import br.com.felipeblaft.reports.port.TelegramLinkRepository;
import br.com.felipeblaft.reports.support.ConfigTest;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.json.JsonMapper;

/**
 * Feature 003, FR-015 e R-03 (verificação 2): dados gravados na V6, com as chaves e os nomes
 * antigos, continuam legíveis pelos ADAPTADORES REAIS depois da V8 (a V7 renomeia nomes e chaves; a
 * V8, os valores). O contexto da aplicação sobe num esquema próprio parado na V6; o teste grava um
 * relatório de aula com versão, um semestral, uma conversa do Telegram com áudio pendente e
 * confirmação, um trabalho de consolidação, uma ação da outbox com teclado e um documento de canal;
 * migra para a V8 e lê tudo pelas portas, exigindo os mesmos objetos de domínio e textos.
 */
@SpringBootTest(
        properties = {
            "spring.flyway.target=8",
<<<<<<< HEAD
            // o modelo atual tem colunas de migrações posteriores à V8; aqui só se leem as tabelas
            // da V8
            "spring.jpa.hibernate.ddl-auto=none",
=======
            // as tabelas da feature 005 (V9 em diante) ainda não existem neste esquema
>>>>>>> feat/005-s2-we
            "spring.jpa.hibernate.ddl-auto=none",
            "spring.flyway.schemas=" + MigrationV8ReadableDataIT.SCHEMA,
            "spring.flyway.default-schema=" + MigrationV8ReadableDataIT.SCHEMA,
        })
@ActiveProfiles("test")
@Import(ConfigTest.class)
class MigrationV8ReadableDataIT {

    static final String SCHEMA = "migration_v7_data_test";
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String BASE_URL =
            System.getenv()
                    .getOrDefault("DATABASE_URL", "jdbc:postgresql://localhost:5432/reports_test");
    private static final String USER = System.getenv().getOrDefault("DATABASE_USER", "reports");
    private static final String PASSWORD =
            System.getenv().getOrDefault("DATABASE_PASSWORD", "test");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add(
                "spring.datasource.url",
                () -> BASE_URL + (BASE_URL.contains("?") ? "&" : "?") + "currentSchema=" + SCHEMA);
    }

    private static void dropSchema() {
        try (Connection connection = DriverManager.getConnection(BASE_URL, USER, PASSWORD);
                Statement statement = connection.createStatement()) {
            statement.execute("drop schema if exists " + SCHEMA + " cascade");
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @AfterAll
    static void cleanSchema() {
        dropSchema();
    }

    @Autowired private LessonReportRepository lessonReports;
    @Autowired private SemesterReportRepository semesterReports;
    @Autowired private TelegramLinkRepository telegramLinks;
    @Autowired private JobQueuePort jobs;
    @Autowired private ChannelActionRepository actions;
    @Autowired private ChannelDocumentRepository documents;

    private static final String LESSON_CONTENT =
            "{\"conteudosTrabalhados\": [\"Escala de Dó maior\", \"Czerny nº 3\"], \"evolucao\": \"Evoluiu bem; ainda"
                    + " tropeça no compasso 8.\", \"dificuldades\": [\"Mão esquerda\"], \"atividadesPropostas\":"
                    + " [\"Estudar devagar\"], \"observacoes\": \"Chegou atrasado — pediu desculpas.\"}";
    private static final String SEMESTER_CONTENT =
            "{\"frequenciaEEstudo\": {\"frequenciaAulas\": \"Presente em todas\", \"regularidadeEstudoEmCasa\":"
                    + " \"Regular\", \"engajamento\": \"Alto\", \"comentarioGeral\": \"Ótimo semestre\"},"
                    + " \"tecnica\": {\"conteudosTrabalhados\": [\"Escalas\"], \"evolucaoTecnica\": \"Boa\","
                    + " \"pontosADesenvolver\": [\"Pedal\"], \"estudosRealizados\": [\"Hanon\"]},"
                    + " \"musicalidade\": {\"repertorioEstudado\": [\"Für Elise\"], \"som\": \"Claro\", \"ritmo\":"
                    + " \"Firme\", \"fraseado\": \"Cantável\", \"articulacao\": \"Limpa\", \"estilo\": \"Romântico\","
                    + " \"comentarioMusical\": \"Sensível\"}, \"leituraEMemorizacao\": {\"avaliacao\": \"Boa\","
                    + " \"comentario\": \"Lê com segurança\"}, \"pontosDeAtencao\": {\"itens\": [\"Postura\"]},"
                    + " \"estrategiasPedagogicas\": {\"itens\": [\"Metrônomo\"]}, \"acompanhamentoFamiliar\":"
                    + " {\"nivelDePresenca\": \"Alto\", \"observacaoDoProfessor\": \"Família presente\"},"
                    + " \"planejamentoProximoSemestre\": {\"metasTecnicas\": [\"Arpejos\"], \"metasMusicais\":"
                    + " [\"Sonata\"], \"habitosDeEstudo\": [\"Diário\"], \"repertorioProposto\": [\"Clementi\"],"
                    + " \"estudosTecnicos\": [\"Czerny\"], \"leituraETeoria\": [\"Armaduras\"], \"projetoDoSemestre\":"
                    + " \"Recital\"}, \"parecerFinal\": \"Aprovada com louvor\"}";
    private static final String GENERAL_INFORMATION =
            "{\"nome\": \"Matteo Mafra\", \"idade\": 14, \"instrumento\": \"Piano\", \"professor\": \"Clara Souza\","
                    + " \"periodoChave\": \"2026-1\", \"periodoInicio\": \"2026-02-01\", \"periodoFim\":"
                    + " \"2026-06-30\", \"quantidadeAulasConsideradas\": 12}";
    private static final String JOB_PARAMETERS =
            "{\"periodoChave\": \"2026-1\", \"confirmarMesmoComEmAberto\": \"true\"}";
    private static final String FLOW_DATA =
            "{\"confirmacao\": \"aprovar:1\", \"semAluno\": \"z\", \"semPeriodo\": \"2026-1\"}";
    private static final String EXPECTED_ACTION_PAYLOAD =
            "{\"actionId\":\"u1-0\",\"type\":\"MESSAGE\",\"chatId\":7,\"text\":\"Olá, Clara\",\"keyboard\":{\"rows\":"
                    + "[[{\"label\":\"Aprovar\",\"data\":\"v1:approve:1\"}]]}}";
    private static final String ACTION_PAYLOAD =
            "{\"acaoId\":\"u1-0\",\"tipo\":\"MENSAGEM\",\"chatId\":7,\"texto\":\"Olá, Clara\",\"teclado\":{\"linhas\":"
                    + "[[{\"rotulo\":\"Aprovar\",\"dados\":\"v1:aprov:1\"}]]}}";

    private static final UUID TEACHER = UUID.randomUUID();
    private static final UUID STUDENT = UUID.randomUUID();
    private static final UUID LESSON = UUID.randomUUID();
    private static final UUID LESSON_REPORT = UUID.randomUUID();
    private static final UUID SEMESTER_REPORT = UUID.randomUUID();
    private static final UUID JOB = UUID.randomUUID();
    private static final String TOKEN_HASH = "a".repeat(64);

    private static void update(String sql, Object... arguments) throws Exception {
        try (Connection connection = DriverManager.getConnection(BASE_URL, USER, PASSWORD)) {
            connection.setSchema(SCHEMA);
            try (var statement = connection.prepareStatement(sql)) {
                for (int i = 0; i < arguments.length; i++) {
                    statement.setObject(i + 1, arguments[i]);
                }
                statement.executeUpdate();
            }
        }
    }

    @BeforeAll
    static void seedV6AndMigrateToV8() throws Exception {
        dropSchema();
        Flyway.configure()
                .dataSource(BASE_URL, USER, PASSWORD)
                .schemas(SCHEMA)
                .defaultSchema(SCHEMA)
                .locations("classpath:db/migration")
                .target("6")
                .load()
                .migrate();

        // ---- grava na V6 (nomes e chaves antigos) ----
        update(
                "insert into professor (id, nome, email, ativo, criado_em, atualizado_em)"
                        + " values (?, 'Clara', 'clara@exemplo.test', true, now(), now())",
                TEACHER);
        update(
                "insert into aluno (id, nome, data_nascimento, cpf_cifrado, cpf_hash, ativo, criado_em,"
                        + " atualizado_em) values (?, 'Matteo', '2012-05-10', '\\x00', 'hash-matteo', true, now(), now())",
                STUDENT);
        update(
                "insert into aula (id, professor_id, aluno_id, data_aula, criado_em) values (?, ?, ?, '2026-09-01', now())",
                LESSON,
                TEACHER,
                STUDENT);
        update(
                "insert into relatorio_aula (id, aula_id, professor_id, aluno_id, transcricao, conteudo,"
                        + " perguntas_pendentes, rodadas_perguntas, status, versao_vigente, canal_origem, criado_em,"
                        + " atualizado_em, tempo_processamento_ms, bloqueio_otimista) values (?, ?, ?, ?,"
                        + " 'Transcrição da aula', ?::jsonb, '[\"Qual peça?\"]'::jsonb, 0, 'AGUARDANDO_CONFERENCIA', 1,"
                        + " 'WEB', now(), now(), 0, 0)",
                LESSON_REPORT,
                LESSON,
                TEACHER,
                STUDENT,
                LESSON_CONTENT);
        update(
                "insert into versao_relatorio (id, relatorio_tipo, relatorio_id, numero, conteudo, origem, canal,"
                        + " criada_em) values (?, 'AULA', ?, 1, ?::jsonb, 'ESTRUTURACAO_INICIAL', 'WEB', now())",
                UUID.randomUUID(),
                LESSON_REPORT,
                LESSON_CONTENT);
        update(
                "insert into relatorio_semestral (id, professor_id, aluno_id, periodo_chave, periodo_inicio,"
                        + " periodo_fim, quantidade_aulas_consideradas, quantidade_relatorios_em_aberto,"
                        + " informacoes_gerais, conteudo, status, versao_vigente, canal_origem, criado_em,"
                        + " atualizado_em, tempo_processamento_ms, bloqueio_otimista) values (?, ?, ?, '2026-1',"
                        + " '2026-02-01', '2026-06-30', 12, 0, ?::jsonb, ?::jsonb, 'AGUARDANDO_CONFERENCIA', 1, 'WEB',"
                        + " now(), now(), 0, 0)",
                SEMESTER_REPORT,
                TEACHER,
                STUDENT,
                GENERAL_INFORMATION,
                SEMESTER_CONTENT);
        update(
                "insert into trabalho_processamento (id, tipo, professor_id, aluno_id, canal_solicitacao, meio,"
                        + " parametros, status, etapa, tentativas, aviso_demora_enviado, criado_em) values (?,"
                        + " 'CONSOLIDACAO_SEMESTRAL', ?, ?, 'WEB', 'TEXTO', ?::jsonb, 'PENDENTE', 'ENVIANDO', 0,"
                        + " false, now())",
                JOB,
                TEACHER,
                STUDENT,
                JOB_PARAMETERS);
        update(
                "insert into vinculo_telegram (id, professor_id, telegram_usuario_id, chat_id, vinculado_em, fluxo,"
                        + " etapa_fluxo, dados_fluxo, audio_pendente_file_id, audio_pendente_file_unique_id,"
                        + " audio_pendente_mime, audio_pendente_tamanho, audio_pendente_recebido_em, atualizado_em,"
                        + " bloqueio_otimista) values (?, ?, 777, 888, now(), 'SEMESTRAL', 'ESCOLHENDO_SEMESTRE',"
                        + " ?::jsonb, 'file-1', 'unique-1', 'audio/ogg', 2048, now(), now(), 0)",
                UUID.randomUUID(),
                TEACHER,
                FLOW_DATA);
        update(
                "insert into acao_canal (id, lote_id, canal, origem, update_id, chat_id, tipo, payload, estado,"
                        + " prazo_em, criado_em) values ('u1-0', 'u1', 'TELEGRAM', 'RESPOSTA_UPDATE', 11, 7,"
                        + " 'MENSAGEM', ?::jsonb, 'PENDENTE', now() + interval '1 day', now())",
                ACTION_PAYLOAD);
        update(
                "insert into documento_canal (token_hash, professor_id, relatorio_tipo, relatorio_id, versao,"
                        + " nome_arquivo, criado_em, expira_em) values (?, ?, 'AULA', ?, 1,"
                        + " 'relatorio-aula-2026-09-01-v1.pdf', now(), now() + interval '1 day')",
                TOKEN_HASH,
                TEACHER,
                LESSON_REPORT);

        // ---- migra para a V8 ----
        Flyway.configure()
                .dataSource(BASE_URL, USER, PASSWORD)
                .schemas(SCHEMA)
                .defaultSchema(SCHEMA)
                .locations("classpath:db/migration")
                .target("8")
                .load()
                .migrate();
    }

    @Test
    void v6DataStaysReadableThroughTheRealAdaptersAfterV8() throws Exception {
        // ---- lê pelos adaptadores reais ----
        LessonReport report = lessonReports.searchById(LESSON_REPORT).orElseThrow();
        assertThat(report.status()).isEqualTo(ReportStatus.AWAITING_REVIEW);
        assertThat(report.transcription()).isEqualTo("Transcrição da aula");
        assertThat(report.pendingQuestions()).containsExactly("Qual peça?");
        assertThat(report.content())
                .isEqualTo(
                        new LessonReportContent(
                                List.of("Escala de Dó maior", "Czerny nº 3"),
                                "Evoluiu bem; ainda tropeça no compasso 8.",
                                List.of("Mão esquerda"),
                                List.of("Estudar devagar"),
                                "Chegou atrasado — pediu desculpas."));

        SemesterReport semester = semesterReports.searchById(SEMESTER_REPORT).orElseThrow();
        assertThat(semester.generalInformation().name()).isEqualTo("Matteo Mafra");
        assertThat(semester.generalInformation().age()).isEqualTo(14);
        assertThat(semester.generalInformation().periodKey()).isEqualTo("2026-1");
        assertThat(semester.generalInformation().consideredLessonCount()).isEqualTo(12);
        assertThat(JSON.writeValueAsString(semester.content()))
                .contains("Presente em todas", "Aprovada com louvor", "Recital");

        TelegramLink link = telegramLinks.searchByTelegramUserId(777).orElseThrow();
        assertThat(link.conversation().flow()).isEqualTo(ConversationFlow.SEMESTER);
        assertThat(link.conversation().step()).isEqualTo(ConversationFlowStep.CHOOSING_SEMESTER);
        assertThat(link.conversation().pendingConfirmation()).contains("approve:1");
        assertThat(link.conversation().data("semesterStudent")).contains("z");
        assertThat(link.conversation().data("semesterPeriod")).contains("2026-1");
        assertThat(link.conversation().pendingAudio()).isPresent();
        assertThat(link.conversation().pendingAudio().orElseThrow().fileId()).isEqualTo("file-1");

        ProcessingJob read = jobs.searchById(JOB).orElseThrow();
        assertThat(read.parameter(ProcessingJob.PERIOD_KEY)).contains("2026-1");
        assertThat(read.parameter(ProcessingJob.CONFIRM_OPEN)).contains("true");
        // a consulta usa a chave nova dentro do SQL: se a V7 não a tivesse renomeado, o trabalho
        // não seria achado
        assertThat(jobs.searchConsolidationInProgress(TEACHER, STUDENT, "2026-1")).isPresent();

        ChannelAction action = actions.searchById("u1-0").orElseThrow();
        // a V8 trocou as chaves do JSON do fio, o valor de `type` e a ação do botão (aprov →
        // approve)
        assertThat(JSON.readTree(action.payload()))
                .isEqualTo(JSON.readTree(EXPECTED_ACTION_PAYLOAD));

        assertThat(documents.searchByHash(TOKEN_HASH).orElseThrow().fileName())
                .isEqualTo("relatorio-aula-2026-09-01-v1.pdf");
    }
}
