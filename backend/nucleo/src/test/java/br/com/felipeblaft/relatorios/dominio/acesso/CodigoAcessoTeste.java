package br.com.felipeblaft.relatorios.dominio.acesso;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class CodigoAcessoTeste {

    @Test
    void normalizaEspacosEMaiusculas() {
        assertThat(CodigoAcesso.tentar("  k7qm2xpa ")).map(CodigoAcesso::valor).contains("K7QM2XPA");
    }

    @Test
    void aceitaFormaAgrupadaComHifen() {
        assertThat(CodigoAcesso.tentar("k7qm-2xpa")).map(CodigoAcesso::valor).contains("K7QM2XPA");
        assertThat(CodigoAcesso.tentar("K7QM 2XPA")).map(CodigoAcesso::valor).contains("K7QM2XPA");
    }

    @Test
    void pareceCodigo() {
        assertThat(CodigoAcesso.pareceCodigo("K7QM-2XPA")).isTrue();
        assertThat(CodigoAcesso.pareceCodigo("abc123")).isTrue();
        assertThat(CodigoAcesso.pareceCodigo("olá, tudo bem?")).isFalse();
        assertThat(CodigoAcesso.pareceCodigo("/start")).isFalse();
        assertThat(CodigoAcesso.pareceCodigo(null)).isFalse();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "K7QM2XP", "K7QM2XPAB", "K7QM2XP0", "K7QM2XPI", "K7QM2XP1", "K7QM_XPA"})
    void recusaForaDoFormato(String entrada) {
        assertThat(CodigoAcesso.tentar(entrada)).isEmpty();
    }

    @Test
    void deExigeFormatoValido() {
        assertThat(CodigoAcesso.de("ABCDEFGH").valor()).isEqualTo("ABCDEFGH");
        assertThatThrownBy(() -> CodigoAcesso.de("ABC")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void alfabetoSemCaracteresAmbiguos() {
        assertThat(CodigoAcesso.ALFABETO).doesNotContain("0", "1", "I", "O").hasSize(32);
    }

    @Test
    void naoRevelaOValorEComparaPeloValor() {
        CodigoAcesso codigo = CodigoAcesso.de("K7QM2XPA");
        assertThat(codigo.toString()).doesNotContain("K7QM");
        assertThat(codigo).isEqualTo(CodigoAcesso.de("k7qm2xpa")).hasSameHashCodeAs(CodigoAcesso.de("K7QM2XPA"));
        assertThat(codigo).isNotEqualTo(CodigoAcesso.de("K7QM2XPB")).isNotEqualTo("K7QM2XPA");
    }
}
