package br.com.felipeblaft.reports.entrada.interno.seguranca;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/** V5: comparação sobre digests, sem curto-circuito e fail-closed (verificação estrutural). */
class ComparadorSegredoTeste {

    private final AtomicInteger avaliacoes = new AtomicInteger();
    private final ComparadorSegredo comparador = new ComparadorSegredo(avaliacoes::incrementAndGet);

    @Test
    void iguaisAceitamEDiferentesRecusam() {
        assertThat(comparador.igual("segredo", "segredo")).isTrue();
        assertThat(comparador.igual("segredo", "segredx")).isFalse();
        assertThat(comparador.igual("", "segredo")).isFalse();
    }

    @Test
    void tamanhosDiferentesEPrefixosNaoAceitam() {
        assertThat(comparador.igual("segredo", "segredo-longo")).isFalse();
        assertThat(comparador.igual("segredo-longo", "segredo")).isFalse();
        assertThat(comparador.igual("s", "segredo")).isFalse();
    }

    @Test
    void nuloEVazioNuncaAutenticam() {
        assertThat(comparador.igual(null, null)).isFalse();
        assertThat(comparador.igual("", "")).isFalse();
        assertThat(comparador.igual(null, "")).isFalse();
        assertThat(comparador.igual("qualquer", null)).isFalse();
        assertThat(comparador.igual("qualquer", "")).isFalse();
        assertThat(comparador.igual(null, "segredo")).isFalse();
    }

    @Test
    void iscaNaoEReproduzidaPorEntradaConhecida() {
        for (String tentativa : List.of("", "isca", "isca:", "0".repeat(64), "null", "nenhum")) {
            assertThat(comparador.igual(tentativa, null)).isFalse();
        }
    }

    @Test
    void qualquerDosDoisTokensAutentica() {
        assertThat(comparador.contemAlgum("atual", List.of("atual", "proximo"))).isTrue();
        assertThat(comparador.contemAlgum("proximo", List.of("atual", "proximo"))).isTrue();
        assertThat(comparador.contemAlgum("outro", List.of("atual", "proximo"))).isFalse();
    }

    @Test
    void sempreAvaliaTodosOsConfiguradosMesmoDepoisDeUmAcerto() {
        assertThat(comparador.contemAlgum("atual", List.of("atual", "proximo"))).isTrue();
        assertThat(avaliacoes.get()).isEqualTo(2);
        avaliacoes.set(0);
        assertThat(comparador.contemAlgum("errado", List.of("atual", "proximo"))).isFalse();
        assertThat(avaliacoes.get()).isEqualTo(2);
        avaliacoes.set(0);
        // token ausente também conta como avaliado (contra a isca)
        assertThat(comparador.contemAlgum("atual", Arrays.asList("atual", null))).isTrue();
        assertThat(avaliacoes.get()).isEqualTo(2);
    }

    @Test
    void naoHaTokenConfiguradoNaoAutenticaNemQuandoORecebidoEVazio() {
        assertThat(comparador.contemAlgum("", Arrays.asList(null, ""))).isFalse();
        assertThat(comparador.contemAlgum("x", List.of())).isFalse();
    }

    @Test
    void construtorPublicoFunciona() {
        assertThat(new ComparadorSegredo().igual("a", "a")).isTrue();
    }
}
