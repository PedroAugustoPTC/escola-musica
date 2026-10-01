package br.com.felipeblaft.relatorios.dominio.acesso;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.felipeblaft.relatorios.dominio.comum.Canal;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class TentativaAcessoTeste {

    private static final Instant T0 = Instant.parse("2026-09-21T13:00:00Z");
    private static final Duration QUINZE = Duration.ofMinutes(15);

    @Test
    void bloqueiaNaQuintaFalhaConsecutiva() {
        TentativaAcesso tentativa = TentativaAcesso.nova(Canal.WEB, "origem", T0);
        for (int i = 1; i <= 4; i++) {
            tentativa.registrarFalha(T0.plusSeconds(i), 5, QUINZE);
            assertThat(tentativa.bloqueioRestante(T0.plusSeconds(i))).isEmpty();
        }
        tentativa.registrarFalha(T0.plusSeconds(5), 5, QUINZE);
        assertThat(tentativa.tentativasIncorretas()).isEqualTo(5);
        assertThat(tentativa.bloqueadoAte()).isEqualTo(T0.plusSeconds(5).plus(QUINZE));
        assertThat(tentativa.bloqueioRestante(T0.plusSeconds(65))).contains(Duration.ofMinutes(14));
        assertThat(tentativa.bloqueioEncerrado(T0.plusSeconds(65))).isFalse();
        assertThat(tentativa.canal()).isEqualTo(Canal.WEB);
        assertThat(tentativa.origemHash()).isEqualTo("origem");
    }

    @Test
    void fimDoBloqueioZeraAntesDeContarNovaFalha() {
        TentativaAcesso tentativa = TentativaAcesso.nova(Canal.TELEGRAM, "origem", T0);
        for (int i = 0; i < 5; i++) {
            tentativa.registrarFalha(T0, 5, QUINZE);
        }
        Instant depois = T0.plus(QUINZE);
        assertThat(tentativa.bloqueioEncerrado(depois)).isTrue();
        assertThat(tentativa.bloqueioRestante(depois)).isEmpty();

        tentativa.registrarFalha(depois, 5, QUINZE);

        assertThat(tentativa.tentativasIncorretas()).isEqualTo(1);
        assertThat(tentativa.bloqueadoAte()).isNull();
        assertThat(tentativa.atualizadoEm()).isEqualTo(depois);
    }

    @Test
    void sucessoZera() {
        TentativaAcesso tentativa = TentativaAcesso.nova(Canal.WEB, "origem", T0);
        tentativa.registrarFalha(T0, 5, QUINZE);
        tentativa.zerar(T0.plusSeconds(1));
        assertThat(tentativa.tentativasIncorretas()).isZero();
        assertThat(tentativa.bloqueadoAte()).isNull();
        assertThat(tentativa.bloqueioEncerrado(T0)).isFalse();
        assertThat(tentativa.id()).isNotNull();
    }
}
