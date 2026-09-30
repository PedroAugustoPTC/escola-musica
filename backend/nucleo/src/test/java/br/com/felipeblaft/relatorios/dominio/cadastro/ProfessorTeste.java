package br.com.felipeblaft.relatorios.dominio.cadastro;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class ProfessorTeste {

    private static final Instant T0 = Instant.parse("2026-09-01T12:00:00Z");
    private static final Duration SETE_DIAS = Duration.ofDays(7);

    private Professor comCodigo() {
        Professor professor = Professor.novo("Clara Souza", " Clara@Exemplo.TEST ", true, T0);
        professor.substituirCodigo("hash-1", T0, SETE_DIAS);
        return professor;
    }

    @Test
    void novoNormalizaEmailESemCodigo() {
        Professor professor = Professor.novo("Clara Souza", " Clara@Exemplo.TEST ", true, T0);
        assertThat(professor.email()).isEqualTo("clara@exemplo.test");
        assertThat(professor.codigoAcessoHash()).isNull();
        assertThat(professor.codigoValidoEm(T0)).isFalse();
        assertThat(professor.codigoExpiradoEm(T0)).isFalse();
        assertThat(professor.renovarValidade(T0, SETE_DIAS)).isFalse();
        assertThat(professor.primeiroNome()).isEqualTo("Clara");
        assertThat(professor.criadoEm()).isEqualTo(T0);
        assertThat(Professor.normalizarEmail(null)).isNull();
    }

    @Test
    void codigoValidoAteSeteDiasDepoisDaEmissao() {
        Professor professor = comCodigo();
        assertThat(professor.codigoAcessoExpiraEm()).isEqualTo(T0.plus(SETE_DIAS));
        assertThat(professor.codigoValidoEm(T0.plus(SETE_DIAS).minusSeconds(1))).isTrue();
        assertThat(professor.codigoValidoEm(T0.plus(SETE_DIAS))).isFalse();
        assertThat(professor.codigoExpiradoEm(T0.plus(SETE_DIAS))).isTrue();
        assertThat(professor.codigoExpiradoEm(T0)).isFalse();
    }

    @Test
    void professorInativoNaoTemCodigoValido() {
        Professor professor = comCodigo();
        assertThat(professor.atualizarCadastro("Clara Souza", false, T0.plusSeconds(1))).isTrue();
        assertThat(professor.ativo()).isFalse();
        assertThat(professor.codigoValidoEm(T0.plusSeconds(2))).isFalse();
    }

    @Test
    void renovaNoMaximoUmaVezPorHora() {
        Professor professor = comCodigo();
        assertThat(professor.renovarValidade(T0.plus(Duration.ofMinutes(59)), SETE_DIAS)).isFalse();
        assertThat(professor.codigoAcessoExpiraEm()).isEqualTo(T0.plus(SETE_DIAS));

        Instant umaHoraDepois = T0.plus(Duration.ofHours(1));
        assertThat(professor.renovarValidade(umaHoraDepois, SETE_DIAS)).isTrue();
        assertThat(professor.codigoAcessoExpiraEm()).isEqualTo(umaHoraDepois.plus(SETE_DIAS));
        assertThat(professor.codigoAcessoRenovadoEm()).isEqualTo(umaHoraDepois);
        assertThat(professor.atualizadoEm()).isEqualTo(umaHoraDepois);
    }

    @Test
    void renovaQuandoNuncaFoiRenovado() {
        Professor professor =
                new Professor(
                        Professor.novo("A", "a@b.c", true, T0).id(),
                        "A",
                        "a@b.c",
                        true,
                        "hash",
                        T0,
                        null,
                        T0,
                        T0);
        assertThat(professor.renovarValidade(T0, SETE_DIAS)).isTrue();
    }

    @Test
    void reemissaoSubstituiHashEReiniciaValidade() {
        Professor professor = comCodigo();
        Instant depois = T0.plus(Duration.ofDays(10));
        professor.substituirCodigo("hash-2", depois, SETE_DIAS);
        assertThat(professor.codigoAcessoHash()).isEqualTo("hash-2");
        assertThat(professor.codigoValidoEm(depois.plus(Duration.ofDays(6)))).isTrue();
    }

    @Test
    void atualizarCadastroSemMudancaNaoAltera() {
        Professor professor = comCodigo();
        assertThat(professor.atualizarCadastro("Clara Souza", true, T0.plusSeconds(5))).isFalse();
        assertThat(professor.atualizarCadastro("Clara S.", true, T0.plusSeconds(5))).isTrue();
        assertThat(professor.nome()).isEqualTo("Clara S.");
    }
}
