package br.com.felipeblaft.relatorios.dominio.canal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class PoliticaRetentativaTeste {

    private static final Instant AGORA = Instant.parse("2026-10-02T12:00:00Z");

    private final PoliticaRetentativa politica =
            new PoliticaRetentativa(
                    Duration.ofSeconds(30), Duration.ofMinutes(15), Duration.ofHours(24));

    @Test
    void esperaDobraAteOTeto() {
        long[] esperadosEmSegundos = {30, 60, 120, 240, 480, 900, 900, 900};
        for (int n = 1; n <= esperadosEmSegundos.length; n++) {
            assertThat(Duration.between(AGORA, politica.proximaTentativa(n, AGORA)))
                    .as("tentativa %d", n)
                    .isEqualTo(Duration.ofSeconds(esperadosEmSegundos[n - 1]));
        }
    }

    @Test
    void numeroMuitoGrandeDeTentativasNaoEstouraOTeto() {
        assertThat(Duration.between(AGORA, politica.proximaTentativa(500, AGORA)))
                .isEqualTo(Duration.ofMinutes(15));
    }

    @Test
    void proximaTentativaComPrazoNuncaPassaDoPrazo() {
        Instant prazo = AGORA.plusSeconds(100);
        assertThat(politica.proximaTentativa(1, AGORA, prazo)).isEqualTo(AGORA.plusSeconds(30));
        assertThat(politica.proximaTentativa(3, AGORA, prazo)).isEqualTo(prazo);
        assertThat(politica.proximaTentativa(6, AGORA.plusSeconds(99), prazo)).isEqualTo(prazo);
    }

    @Test
    void jitterAplicaAte10PorCentoParaMaisOuParaMenos() {
        PoliticaRetentativa maisUm =
                new PoliticaRetentativa(
                        Duration.ofSeconds(100),
                        Duration.ofHours(1),
                        Duration.ofHours(24),
                        () -> 1.0);
        PoliticaRetentativa menosUm =
                new PoliticaRetentativa(
                        Duration.ofSeconds(100),
                        Duration.ofHours(1),
                        Duration.ofHours(24),
                        () -> -1.0);
        assertThat(Duration.between(AGORA, maisUm.proximaTentativa(1, AGORA)))
                .isEqualTo(Duration.ofSeconds(110));
        assertThat(Duration.between(AGORA, menosUm.proximaTentativa(1, AGORA)))
                .isEqualTo(Duration.ofSeconds(90));
    }

    @Test
    void jitterForaDoIntervaloEProtegido() {
        PoliticaRetentativa exagerada =
                new PoliticaRetentativa(
                        Duration.ofSeconds(100),
                        Duration.ofHours(1),
                        Duration.ofHours(24),
                        () -> 50.0);
        assertThat(Duration.between(AGORA, exagerada.proximaTentativa(1, AGORA)))
                .isEqualTo(Duration.ofSeconds(110));
    }

    @Test
    void jitterNuloViraZero() {
        PoliticaRetentativa semJitter =
                new PoliticaRetentativa(
                        Duration.ofSeconds(30), Duration.ofMinutes(15), Duration.ofHours(24), null);
        assertThat(Duration.between(AGORA, semJitter.proximaTentativa(1, AGORA)))
                .isEqualTo(Duration.ofSeconds(30));
    }

    @Test
    void prazoContaDesdeACriacao() {
        assertThat(politica.prazoDe(AGORA)).isEqualTo(AGORA.plus(Duration.ofHours(24)));
        assertThat(politica.prazo()).isEqualTo(Duration.ofHours(24));
    }

    @Test
    void valoresInvalidosSaoRecusados() {
        Duration trinta = Duration.ofSeconds(30);
        Duration hora = Duration.ofHours(1);
        assertThatThrownBy(() -> new PoliticaRetentativa(null, hora, hora))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PoliticaRetentativa(Duration.ZERO, hora, hora))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PoliticaRetentativa(Duration.ofSeconds(-1), hora, hora))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PoliticaRetentativa(hora, trinta, hora))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PoliticaRetentativa(trinta, null, hora))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PoliticaRetentativa(trinta, hora, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PoliticaRetentativa(trinta, hora, Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PoliticaRetentativa(trinta, hora, Duration.ofSeconds(-5)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> politica.proximaTentativa(0, AGORA))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
