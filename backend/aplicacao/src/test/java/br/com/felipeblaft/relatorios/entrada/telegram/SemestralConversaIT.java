package br.com.felipeblaft.relatorios.entrada.telegram;

import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static org.assertj.core.api.Assertions.assertThat;

import br.com.felipeblaft.relatorios.dominio.cadastro.Aluno;
import br.com.felipeblaft.relatorios.dominio.cadastro.Professor;
import br.com.felipeblaft.relatorios.suporte.CenarioIT;
import br.com.felipeblaft.relatorios.suporte.TelegramConversaBase;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/** Semestral pelo bot (sequência 5.4, estado #8). */
class SemestralConversaIT extends TelegramConversaBase {

    private Professor clara;
    private Aluno matteo;

    @BeforeEach
    void preparar() throws Exception {
        clara = cenario.clara();
        matteo = cenario.aluno(clara, "Matteo Ferreira", "Piano");
        cenario.aulaAprovada(clara, matteo, LocalDate.of(2026, 3, 10), "Escala de Dó");
        cenario.aulaAprovada(clara, matteo, LocalDate.of(2026, 5, 20), "Estudo nº 5");
        vincular(USUARIO_CLARA, CenarioIT.CODIGO_CLARA);
    }

    private void escolherMatteo() throws Exception {
        enviarTexto(USUARIO_CLARA, "semestral");
        enviarTexto(USUARIO_CLARA, "Matteo");
    }

    private Map<String, Object> conversa() {
        return jdbc.queryForMap("select * from vinculo_telegram");
    }

    /** Gera o semestral do 1º semestre e chega à conferência. */
    private UUID gerarNoPrimeiroSemestre() throws Exception {
        escolherMatteo();
        enviarCallback(USUARIO_CLARA, "v1:per:1");
        enviarCallback(USUARIO_CLARA, "v1:gerar:");
        cenario.processarFila();
        API_BOTS.resetRequests();
        return jdbc.queryForObject("select id from relatorio_semestral", UUID.class);
    }

    @Test
    void periodosComEstrelaEEmAndamentoEPrevia() throws Exception {
        escolherMatteo();

        JsonNode periodos = ultimaMensagem();
        assertThat(periodos.get("text").asString())
                .startsWith("Escolha o semestre de Matteo Ferreira:")
                .contains("2º semestre 2026: 01/07/2026 a 31/12/2026")
                .contains("1º semestre 2026: 01/01/2026 a 30/06/2026");
        assertThat(rotulosBotoes(periodos))
                .startsWith("★ 2º semestre 2026 · em andamento", "1º semestre 2026");
        assertThat(botoes(periodos)).startsWith("v1:per:0", "v1:per:1");

        enviarCallback(USUARIO_CLARA, "v1:per:0");
        assertThat(ultimaMensagem().get("text").asString())
                .startsWith("Nenhum relatório de aula aprovado neste período.");
        assertThat(botoes(ultimaMensagem())).containsExactly("v1:voltar:");

        enviarCallback(USUARIO_CLARA, "v1:voltar:");
        assertThat(ultimaMensagem().get("text").asString()).startsWith("Escolha o semestre");

        enviarCallback(USUARIO_CLARA, "v1:per:1");
        assertThat(ultimaMensagem().get("text").asString())
                .isEqualTo("O 1º semestre 2026 tem 2 relatório(s) de aula aprovado(s).");
        assertThat(botoes(ultimaMensagem())).containsExactly("v1:gerar:", "v1:voltar:");
    }

    @Test
    void gerarMostraEtapaEConferenciaComPdf() throws Exception {
        escolherMatteo();
        enviarCallback(USUARIO_CLARA, "v1:per:1");
        API_BOTS.resetRequests();

        enviarCallback(USUARIO_CLARA, "v1:gerar:");
        assertThat(textosEditados()).contains("<i>Consolidando as aulas do semestre…</i>");
        cenario.processarFila();

        String conferencia =
                textosEnviados().stream()
                        .filter(t -> t.contains("Confira o relatório"))
                        .findFirst()
                        .orElseThrow();
        assertThat(conferencia)
                .contains("Aluno: Matteo Ferreira")
                .contains("Período: 1º semestre 2026")
                .contains("Versão 1")
                .contains("2 relatório(s) de aula considerado(s) nesta consolidação.")
                .contains("<b>1. Informações Gerais</b>")
                .contains("<b>10. Parecer Final</b>");
        API_BOTS.verify(1, postRequestedFor(urlPathMatching(".*/sendDocument")));
        assertThat(botoes(ultimaMensagem())).contains("v1:aprov:1", "v1:alt:", "v1:canc:");
        assertThat(conversa().get("relatorio_foco_tipo")).isEqualTo("SEMESTRAL");
    }

    @Test
    void alterarEAprovarDepoisNaoGeraDeNovo() throws Exception {
        gerarNoPrimeiroSemestre();

        enviarTexto(USUARIO_CLARA, "Parecer: excelente semestre.");
        assertThat(ultimaMensagem().get("text").asString())
                .isEqualTo("<i>Aplicando a alteração…</i>");
        cenario.processarFila();
        assertThat(String.join("\n", textosEnviados()))
                .contains("Alteração aplicada. Confira a versão 2.")
                .contains("Entendi: “Parecer: excelente semestre.”")
                .contains("Parecer: excelente semestre.");

        enviarTexto(USUARIO_CLARA, "aprovar");
        enviarTexto(USUARIO_CLARA, "sim");
        assertThat(ultimaMensagem().get("text").asString())
                .startsWith("Relatório aprovado em 21/09/2026");
        assertThat(jdbc.queryForObject("select status from relatorio_semestral", String.class))
                .isEqualTo("APROVADO");

        escolherMatteo();
        enviarCallback(USUARIO_CLARA, "v1:per:1");
        assertThat(ultimaMensagem().get("text").asString())
                .isEqualTo("O relatório semestral deste período já foi aprovado.");
    }

    @Test
    void semestralEmAbertoOfereceContinuarOuDescartar() throws Exception {
        gerarNoPrimeiroSemestre();
        escolherMatteo();
        enviarCallback(USUARIO_CLARA, "v1:per:1");
        assertThat(ultimaMensagem().get("text").asString())
                .startsWith("Você tem um relatório em aberto.");
        assertThat(botoes(ultimaMensagem())).containsExactly("v1:cont:", "v1:desc:");
        API_BOTS.resetRequests();

        enviarCallback(USUARIO_CLARA, "v1:cont:");
        assertThat(String.join("\n", textosEnviados()))
                .contains("Confira o relatório antes de aprovar");

        escolherMatteo();
        enviarCallback(USUARIO_CLARA, "v1:per:1");
        enviarCallback(USUARIO_CLARA, "v1:desc:");
        enviarCallback(USUARIO_CLARA, "v1:descok:");

        assertThat(jdbc.queryForObject("select status from relatorio_semestral", String.class))
                .isEqualTo("CANCELADO");
        assertThat(ultimaMensagem().get("text").asString())
                .isEqualTo("O 1º semestre 2026 tem 2 relatório(s) de aula aprovado(s).");
    }

    @Test
    void relatoriosDeAulaEmAbertoPedemGerarMesmoAssim() throws Exception {
        jdbc.update(
                "update relatorio_aula set status = 'AGUARDANDO_CONFERENCIA', aprovado_em = null,"
                        + " versao_aprovada = null, canal_aprovacao = null"
                        + " where id = (select r.id from relatorio_aula r join aula a on a.id = r.aula_id"
                        + " where a.data_aula = '2026-05-20')");
        escolherMatteo();

        enviarCallback(USUARIO_CLARA, "v1:per:1");

        assertThat(ultimaMensagem().get("text").asString())
                .contains(
                        "Há 1 relatório(s) de aula em aberto neste período; eles não serão considerados.");
        assertThat(botoes(ultimaMensagem())).containsExactly("v1:gerarsim:", "v1:voltar:");
        enviarCallback(USUARIO_CLARA, "v1:gerar:");
        assertThat(ultimaMensagem().get("text").asString())
                .isEqualTo(
                        "Há 1 relatório(s) de aula em aberto neste período; eles não serão considerados.");
        enviarCallback(USUARIO_CLARA, "v1:per:1");
        enviarCallback(USUARIO_CLARA, "v1:gerarsim:");
        cenario.processarFila();
        assertThat(
                        jdbc.queryForObject(
                                "select quantidade_relatorios_em_aberto from relatorio_semestral",
                                Integer.class))
                .isEqualTo(1);
    }

    @Test
    void cancelarPeloBotao() throws Exception {
        gerarNoPrimeiroSemestre();

        enviarCallback(USUARIO_CLARA, "v1:canc:");
        enviarCallback(USUARIO_CLARA, "v1:cancok:");

        assertThat(textosEnviados()).contains("Relatório cancelado.");
        assertThat(jdbc.queryForObject("select status from relatorio_semestral", String.class))
                .isEqualTo("CANCELADO");
        assertThat(conversa().get("relatorio_foco_id")).isNull();
    }
}
