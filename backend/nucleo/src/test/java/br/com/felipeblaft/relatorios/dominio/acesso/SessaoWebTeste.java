package br.com.felipeblaft.relatorios.dominio.acesso;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SessaoWebTeste {

    private static final Instant T0 = Instant.parse("2026-09-21T13:00:00Z");
    private static final Duration DOZE_HORAS = Duration.ofHours(12);

    @Test
    void expiraDozeHorasDepoisDoUltimoUso() {
        SessaoWeb sessao = SessaoWeb.nova(UUID.randomUUID(), "hash", T0, DOZE_HORAS);
        assertThat(sessao.criadaEm()).isEqualTo(T0);
        assertThat(sessao.expiraEm()).isEqualTo(T0.plus(DOZE_HORAS));
        assertThat(sessao.validaEm(T0.plus(DOZE_HORAS).minusMillis(1))).isTrue();
        assertThat(sessao.validaEm(T0.plus(DOZE_HORAS))).isFalse();
        assertThat(sessao.tokenHash()).isEqualTo("hash");
        assertThat(sessao.professorId()).isNotNull();
        assertThat(sessao.id()).isNotNull();
    }

    @Test
    void usoRenovaNoMaximoUmaVezPorMinuto() {
        SessaoWeb sessao = SessaoWeb.nova(UUID.randomUUID(), "hash", T0, DOZE_HORAS);
        assertThat(sessao.registrarUso(T0.plusSeconds(59), DOZE_HORAS)).isFalse();
        assertThat(sessao.expiraEm()).isEqualTo(T0.plus(DOZE_HORAS));

        Instant depois = T0.plus(Duration.ofHours(11));
        assertThat(sessao.registrarUso(depois, DOZE_HORAS)).isTrue();
        assertThat(sessao.ultimoUsoEm()).isEqualTo(depois);
        assertThat(sessao.expiraEm()).isEqualTo(depois.plus(DOZE_HORAS));
        assertThat(sessao.validaEm(T0.plus(Duration.ofHours(13)))).isTrue();
    }
}
