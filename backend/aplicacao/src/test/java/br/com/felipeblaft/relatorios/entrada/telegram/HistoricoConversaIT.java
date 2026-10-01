package br.com.felipeblaft.relatorios.entrada.telegram;

import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static org.assertj.core.api.Assertions.assertThat;

import br.com.felipeblaft.relatorios.dominio.cadastro.Aluno;
import br.com.felipeblaft.relatorios.dominio.cadastro.Professor;
import br.com.felipeblaft.relatorios.suporte.CenarioIT;
import br.com.felipeblaft.relatorios.suporte.TelegramConversaBase;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/** Histórico pelo bot (sequência 5.5, estado #9). */
class HistoricoConversaIT extends TelegramConversaBase {

    private Professor clara;
    private Aluno matteo;

    @BeforeEach
    void preparar() throws Exception {
        clara = cenario.clara();
        matteo = cenario.aluno(clara, "Matteo Ferreira", "Piano");
        vincular(USUARIO_CLARA, CenarioIT.CODIGO_CLARA);
    }

    @Test
    void historicoVazioOfereceRegistrarAula() throws Exception {
        enviarCallback(USUARIO_CLARA, "v1:hist:");
        enviarTexto(USUARIO_CLARA, "Matteo");

        assertThat(ultimaMensagem().get("text").asString())
                .isEqualTo("Este aluno ainda não tem relatórios aprovados.");
        assertThat(botoes(ultimaMensagem())).containsExactly("v1:reg:");
    }

    @Test
    void listaPaginadaComMaisEPdfOficialDoItem() throws Exception {
        for (int dia = 1; dia <= 12; dia++) {
            cenario.aulaAprovada(clara, matteo, LocalDate.of(2026, 3, dia), "Aula " + dia);
        }
        enviarTexto(USUARIO_CLARA, "histórico");
        enviarTexto(USUARIO_CLARA, "Matteo");

        JsonNode lista = ultimaMensagem();
        assertThat(lista.get("text").asString())
                .startsWith("<b>Histórico de Matteo Ferreira</b>")
                .contains("\n1. Aula de 12/03/2026 · Aprovado em 21/09/2026 às 10:00")
                .contains("\n10. Aula de 03/03/2026")
                .doesNotContain("11. ");
        assertThat(botoes(lista))
                .contains("v1:hitem:0", "v1:hitem:9", "v1:hmais:1")
                .doesNotContain("v1:hitem:10");

        enviarCallback(USUARIO_CLARA, "v1:hmais:1");
        assertThat(ultimaMensagem().get("text").asString())
                .contains("\n11. Aula de 02/03/2026")
                .contains("\n12. Aula de 01/03/2026");
        assertThat(botoes(ultimaMensagem()))
                .contains("v1:hitem:10", "v1:hitem:11")
                .doesNotContain("v1:hmais:2");

        API_BOTS.resetRequests();
        enviarCallback(USUARIO_CLARA, "v1:hitem:11");
        API_BOTS.verify(
                1,
                postRequestedFor(urlPathMatching(".*/sendDocument"))
                        .withRequestBody(containing("relatorio-aula-2026-03-01.pdf")));
    }

    @Test
    void itemForaDaListaExpirou() throws Exception {
        cenario.aulaAprovada(clara, matteo, LocalDate.of(2026, 3, 1), "Aula");
        enviarTexto(USUARIO_CLARA, "histórico");
        enviarTexto(USUARIO_CLARA, "Matteo");

        enviarCallback(USUARIO_CLARA, "v1:hitem:5");

        assertThat(textosEnviados())
                .contains("Esta opção não vale mais. Veja abaixo a etapa atual.");
    }
}
