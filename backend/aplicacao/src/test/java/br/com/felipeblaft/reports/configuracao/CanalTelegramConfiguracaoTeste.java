package br.com.felipeblaft.reports.configuracao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.felipeblaft.reports.configuracao.PropriedadesAplicacao.CanalTelegram;
import br.com.felipeblaft.reports.configuracao.PropriedadesAplicacao.EntregaProativa;
import org.junit.jupiter.api.Test;

/** R-10, R-09, Princípio IV: configuração inconsistente derruba a subida, sem vazar segredo. */
class CanalTelegramConfiguracaoTeste {

    private static CanalTelegram canal(EntregaProativa entrega, String url, int lease) {
        return new CanalTelegram(
                entrega,
                "atual",
                "proximo",
                url,
                "token-do-orquestrador",
                3000,
                10000,
                30,
                900,
                24,
                60,
                lease,
                2000,
                20,
                24,
                7);
    }

    @Test
    void configuracaoPadraoEValida() {
        assertThatCode(
                        () ->
                                CanalTelegramConfiguracao.validar(
                                        canal(EntregaProativa.DIRETA, "", 60), 1))
                .doesNotThrowAnyException();
    }

    @Test
    void orquestradorSemUrlNaoSobe() {
        assertThatThrownBy(
                        () ->
                                CanalTelegramConfiguracao.validar(
                                        canal(EntregaProativa.ORQUESTRADOR, "", 60), 1))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CANAL_TELEGRAM_ORQUESTRADOR_URL");
        assertThatThrownBy(
                        () ->
                                CanalTelegramConfiguracao.validar(
                                        canal(EntregaProativa.ORQUESTRADOR, "  ", 60), 1))
                .isInstanceOf(IllegalStateException.class);
        assertThatCode(
                        () ->
                                CanalTelegramConfiguracao.validar(
                                        canal(
                                                EntregaProativa.ORQUESTRADOR,
                                                "http://n8n:5678/webhook/abc",
                                                60),
                                        1))
                .doesNotThrowAnyException();
    }

    @Test
    void modoDiretoAceitaURLVazia() {
        assertThat(canal(EntregaProativa.DIRETA, null, 60).orquestradorConfigurado()).isFalse();
        assertThatCode(
                        () ->
                                CanalTelegramConfiguracao.validar(
                                        canal(EntregaProativa.DIRETA, null, 60), 1))
                .doesNotThrowAnyException();
    }

    @Test
    void leaseDeveExcederConexaoMaisResposta() {
        // 3000 + 10000 ms = 13 s
        assertThatThrownBy(
                        () ->
                                CanalTelegramConfiguracao.validar(
                                        canal(EntregaProativa.DIRETA, "", 13), 1))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("lease-segundos");
        assertThatThrownBy(
                        () ->
                                CanalTelegramConfiguracao.validar(
                                        canal(EntregaProativa.DIRETA, "", 5), 1))
                .isInstanceOf(IllegalStateException.class);
        assertThatCode(
                        () ->
                                CanalTelegramConfiguracao.validar(
                                        canal(EntregaProativa.DIRETA, "", 14), 1))
                .doesNotThrowAnyException();
    }

    @Test
    void exatamenteUmCaminhoProativoDeveEstarAtivo() {
        assertThatThrownBy(
                        () ->
                                CanalTelegramConfiguracao.validar(
                                        canal(EntregaProativa.DIRETA, "", 60), 0))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("exatamente um");
        assertThatThrownBy(
                        () ->
                                CanalTelegramConfiguracao.validar(
                                        canal(EntregaProativa.DIRETA, "", 60), 2))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void urlPublicaExigeHttpsEHttpSoEmRedeInterna() {
        for (String permitida :
                new String[] {
                    "https://n8n.exemplo.com.br/webhook/x",
                    "http://n8n:5678/webhook/x",
                    "http://localhost:5678/x",
                    "http://127.0.0.1:5678/x",
                    "http://10.0.3.4:5678/x",
                    "http://172.20.0.5/x",
                    "http://192.168.1.10:8080/x",
                    "http://[::1]:5678/x"
                }) {
            assertThat(CanalTelegramConfiguracao.destinoPermitido(permitida))
                    .as(permitida)
                    .isTrue();
        }
        for (String recusada :
                new String[] {
                    "http://n8n.exemplo.com.br/webhook/x",
                    "http://8.8.8.8/x",
                    "http://203.0.113.9:5678/x",
                    "ftp://n8n/x",
                    "n8n:5678/x",
                    "http:///x",
                    "isto não é url"
                }) {
            assertThat(CanalTelegramConfiguracao.destinoPermitido(recusada)).as(recusada).isFalse();
        }
    }

    @Test
    void urlPublicaSemHttpsNaoSobeESemVazarOCaminhoSecreto() {
        String url = "http://n8n.exemplo.com.br/webhook/segredo-no-caminho-123";
        assertThatThrownBy(
                        () ->
                                CanalTelegramConfiguracao.validar(
                                        canal(EntregaProativa.ORQUESTRADOR, url, 60), 1))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("https")
                .satisfies(
                        erro ->
                                assertThat(erro.getMessage())
                                        .doesNotContain("segredo-no-caminho-123", "n8n.exemplo"));
    }

    @Test
    void toStringMascaraTokensEUrl() {
        String texto = canal(EntregaProativa.ORQUESTRADOR, "https://x/segredo", 60).toString();
        assertThat(texto)
                .doesNotContain("atual", "proximo", "token-do-orquestrador", "segredo")
                .contains("ORQUESTRADOR");
    }

    @Test
    void entregaProativaNulaViraDireta() {
        assertThat(canal(null, "", 60).entregaProativa()).isEqualTo(EntregaProativa.DIRETA);
    }
}
