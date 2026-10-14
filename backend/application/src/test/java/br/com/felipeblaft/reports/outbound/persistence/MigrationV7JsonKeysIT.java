package br.com.felipeblaft.reports.outbound.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.felipeblaft.reports.support.MigrationTestSupport;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Feature 003, R-03 (verificação 3), FR-015: a V7 renomeia só as CHAVES dos documentos {@code
 * jsonb} que o banco guarda (conteúdo dos relatórios, informações gerais, parâmetros do trabalho e
 * dados do fluxo do Telegram). Grava na V6 documentos com as chaves antigas e textos em português,
 * migra para a V7 e exige: nenhuma chave do vocabulário antigo em lugar algum, as chaves novas no
 * lugar, e os valores (textos e ordem das listas) idênticos aos gravados.
 */
class MigrationV7JsonKeysIT {

    private static final String SCHEMA = "migration_v7_json_keys_test";
    private static final JsonMapper JSON = JsonMapper.builder().build();

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
            "{\"periodoChave\": \"2026-1\", \"confirmarMesmoComEmAberto\": \"true\", \"texto\": \"Troque a"
                    + " observação\", \"versaoBase\": \"2\", \"textoEntendido\": \"Entendi: troque\","
                    + " \"arquivoCanal\": \"file-1\", \"arquivoCanalUnico\": \"u-1\", \"arquivoMime\":"
                    + " \"audio/ogg\", \"arquivoTamanho\": \"2048\", \"mensagemEtapa\": \"77\"}";
    private static final String FLOW_DATA =
            "{\"confirmacao\": \"aprovar\", \"opcoes\": \"a,b\", \"histAluno\": \"x\", \"histItens\": \"1,2\","
                    + " \"alunoEmAberto\": \"y\", \"semAluno\": \"z\", \"semOpcoes\": \"c,d\", \"semPeriodo\":"
                    + " \"2026-1\"}";

    private final MigrationTestSupport db = new MigrationTestSupport(SCHEMA);
    private final UUID teacher = UUID.randomUUID();
    private final UUID student = UUID.randomUUID();
    private final UUID lessonReport = UUID.randomUUID();
    private final UUID semesterReport = UUID.randomUUID();

    @BeforeEach
    void seedV6() throws Exception {
        db.dropSchema();
        db.flyway("6").migrate();
        db.update(
                "insert into professor (id, nome, email, ativo, criado_em, atualizado_em) values ('"
                        + teacher
                        + "', 'Clara', 'clara@exemplo.test', true, now(), now())");
        db.update(
                "insert into aluno (id, nome, data_nascimento, cpf_cifrado, cpf_hash, ativo, criado_em, atualizado_em)"
                        + " values ('"
                        + student
                        + "', 'Matteo', '2012-05-10', '\\x00', 'hash-matteo', true, now(), now())");
        UUID lesson = UUID.randomUUID();
        db.update(
                "insert into aula (id, professor_id, aluno_id, data_aula, criado_em) values ('"
                        + lesson
                        + "', '"
                        + teacher
                        + "', '"
                        + student
                        + "', '2026-09-01', now())");
        db.update(
                "insert into relatorio_aula (id, aula_id, professor_id, aluno_id, transcricao, conteudo,"
                        + " perguntas_pendentes, rodadas_perguntas, status, versao_vigente, canal_origem, criado_em,"
                        + " atualizado_em, tempo_processamento_ms, bloqueio_otimista) values ('"
                        + lessonReport
                        + "', '"
                        + lesson
                        + "', '"
                        + teacher
                        + "', '"
                        + student
                        + "', 'Transcrição da aula', '"
                        + LESSON_CONTENT
                        + "'::jsonb, '[\"Qual peça?\"]'::jsonb, 0, 'AGUARDANDO_CONFERENCIA', 1, 'WEB', now(), now(),"
                        + " 0, 0)");
        db.update(
                "insert into versao_relatorio (id, relatorio_tipo, relatorio_id, numero, conteudo, origem, canal,"
                        + " criada_em) values ('"
                        + UUID.randomUUID()
                        + "', 'AULA', '"
                        + lessonReport
                        + "', 1, '"
                        + LESSON_CONTENT
                        + "'::jsonb, 'ESTRUTURACAO_INICIAL', 'WEB', now())");
        db.update(
                "insert into relatorio_semestral (id, professor_id, aluno_id, periodo_chave, periodo_inicio,"
                        + " periodo_fim, quantidade_aulas_consideradas, quantidade_relatorios_em_aberto,"
                        + " informacoes_gerais, conteudo, status, versao_vigente, canal_origem, criado_em,"
                        + " atualizado_em, tempo_processamento_ms, bloqueio_otimista) values ('"
                        + semesterReport
                        + "', '"
                        + teacher
                        + "', '"
                        + student
                        + "', '2026-1', '2026-02-01', '2026-06-30', 12, 0, '"
                        + GENERAL_INFORMATION
                        + "'::jsonb, '"
                        + SEMESTER_CONTENT
                        + "'::jsonb, 'AGUARDANDO_CONFERENCIA', 1, 'WEB', now(), now(), 0, 0)");
        db.update(
                "insert into versao_relatorio (id, relatorio_tipo, relatorio_id, numero, conteudo, origem, canal,"
                        + " criada_em) values ('"
                        + UUID.randomUUID()
                        + "', 'SEMESTRAL', '"
                        + semesterReport
                        + "', 1, '"
                        + SEMESTER_CONTENT
                        + "'::jsonb, 'CONSOLIDACAO', 'WEB', now())");
        db.update(
                "insert into trabalho_processamento (id, tipo, professor_id, aluno_id, canal_solicitacao, meio,"
                        + " parametros, status, etapa, tentativas, aviso_demora_enviado, criado_em) values ('"
                        + UUID.randomUUID()
                        + "', 'CONSOLIDACAO_SEMESTRAL', '"
                        + teacher
                        + "', '"
                        + student
                        + "', 'WEB', 'TEXTO', '"
                        + JOB_PARAMETERS
                        + "'::jsonb, 'PENDENTE', 'ENVIANDO', 0, false, now())");
        db.update(
                "insert into vinculo_telegram (id, professor_id, telegram_usuario_id, chat_id, vinculado_em, fluxo,"
                        + " etapa_fluxo, dados_fluxo, atualizado_em, bloqueio_otimista) values ('"
                        + UUID.randomUUID()
                        + "', '"
                        + teacher
                        + "', 777, 888, now(), 'SEMESTRAL', 'ESCOLHENDO_SEMESTRE', '"
                        + FLOW_DATA
                        + "'::jsonb, now(), 0)");
    }

    @AfterEach
    void cleanSchema() throws SQLException {
        db.dropSchema();
    }

    /**
     * Todas as chaves de todos os níveis, e todos os valores de texto/número em ordem de leitura.
     */
    private record Walk(Set<String> keys, List<String> values) {}

    private static Walk walk(JsonNode node) {
        Set<String> keys = new TreeSet<>();
        List<String> values = new ArrayList<>();
        collect(node, keys, values);
        return new Walk(keys, values);
    }

    private static void collect(JsonNode node, Set<String> keys, List<String> values) {
        if (node.isObject()) {
            // a ordem das chaves do jsonb é a da própria coluna; para comparar valores usa-se a
            // ordem das chaves NOVAS
            List<String> names = new ArrayList<>(node.propertyNames());
            names.sort(String::compareTo);
            for (String name : names) {
                keys.add(name);
                collect(node.get(name), keys, values);
            }
        } else if (node.isArray()) {
            node.forEach(item -> collect(item, keys, values));
        } else {
            values.add(node.asString());
        }
    }

    private JsonNode read(String sql) throws Exception {
        return JSON.readTree(db.single(sql));
    }

    /**
     * Valores na ordem em que as chaves ANTIGAS aparecem em ordem alfabética: compara contagem e
     * conteúdo.
     */
    private static List<String> sorted(List<String> values) {
        return values.stream().sorted().toList();
    }

    @Test
    void onlyKeysAreRenamedAndTheTextValuesAreIdentical() throws Exception {
        String[][] columns = {
            {"relatorio_aula", "conteudo"},
            {"versao_relatorio", "conteudo"},
            {"relatorio_semestral", "conteudo"},
            {"relatorio_semestral", "informacoes_gerais"},
            {"trabalho_processamento", "parametros"},
            {"vinculo_telegram", "dados_fluxo"},
        };
        List<List<JsonNode>> beforeByColumn = new ArrayList<>();
        for (String[] column : columns) {
            List<JsonNode> documents = new ArrayList<>();
            for (String json :
                    db.column(
                            "select " + column[1] + "::text from " + column[0] + " order by id")) {
                documents.add(JSON.readTree(json));
            }
            beforeByColumn.add(documents);
        }

        db.flyway("7").migrate();

        JsonNode map = MigrationTestSupport.renameMap();
        Set<String> oldVocabulary = new TreeSet<>();
        for (JsonNode set : map.get("jsonKeys")) {
            set.get("keys").propertyNames().forEach(oldVocabulary::add);
        }
        String[][] renamed = {
            {"lesson_report", "content"},
            {"report_version", "content"},
            {"semester_report", "content"},
            {"semester_report", "general_information"},
            {"processing_job", "parameters"},
            {"telegram_link", "flow_data"},
        };
        int documentsChecked = 0;
        for (int i = 0; i < renamed.length; i++) {
            List<String> after =
                    db.column(
                            "select "
                                    + renamed[i][1]
                                    + "::text from "
                                    + renamed[i][0]
                                    + " order by id");
            assertThat(after)
                    .as("%s.%s", renamed[i][0], renamed[i][1])
                    .hasSameSizeAs(beforeByColumn.get(i));
            for (int d = 0; d < after.size(); d++) {
                Walk old = walk(beforeByColumn.get(i).get(d));
                Walk now = walk(JSON.readTree(after.get(d)));
                assertThat(now.keys())
                        .as("chaves antigas em %s.%s", renamed[i][0], renamed[i][1])
                        .doesNotContainAnyElementsOf(oldVocabulary);
                assertThat(now.keys()).hasSameSizeAs(old.keys());
                assertThat(sorted(now.values()))
                        .as("valores de %s.%s", renamed[i][0], renamed[i][1])
                        .isEqualTo(sorted(old.values()));
                documentsChecked++;
            }
        }
        // 2 conteúdos de aula + 2 de semestral + informações gerais + parâmetros + dados do fluxo
        assertThat(documentsChecked).isEqualTo(7);

        // exemplos de chave nova no lugar certo
        assertThat(read("select content::text from lesson_report").has("workedTopics")).isTrue();
        assertThat(read("select parameters::text from processing_job").has("periodKey")).isTrue();
        assertThat(read("select flow_data::text from telegram_link").has("confirmation")).isTrue();
        assertThat(
                        read("select general_information::text from semester_report")
                                .get("consideredLessonCount")
                                .asInt())
                .isEqualTo(12);
        assertThat(
                        read("select content::text from semester_report")
                                .get("attendanceAndStudy")
                                .get("lessonAttendance")
                                .asString())
                .isEqualTo("Presente em todas");
    }
}
