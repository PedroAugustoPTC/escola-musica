package br.com.felipeblaft.relatorios.dominio.cadastro;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class AlunoTeste {

    private static final Instant T0 = Instant.parse("2026-09-01T12:00:00Z");

    private Aluno aluno(LocalDate nascimento) {
        return Aluno.novo(
                "Matteo Mafra", nascimento, new byte[] {1, 2}, "hash", "Resp", "Piano", T0);
    }

    @Test
    void idadeCompletaNaData() {
        Aluno aluno = aluno(LocalDate.of(2014, 5, 10));
        assertThat(aluno.idadeEm(LocalDate.of(2026, 5, 9))).isEqualTo(11);
        assertThat(aluno.idadeEm(LocalDate.of(2026, 5, 10))).isEqualTo(12);
        assertThat(Aluno.idade(LocalDate.of(2030, 1, 1), LocalDate.of(2026, 1, 1))).isZero();
    }

    @Test
    void menorDeDezoitoExigeResponsavel() {
        LocalDate hoje = LocalDate.of(2026, 9, 21);
        assertThat(Aluno.exigeResponsavel(LocalDate.of(2008, 9, 22), hoje)).isTrue();
        assertThat(Aluno.exigeResponsavel(LocalDate.of(2008, 9, 21), hoje)).isFalse();
        assertThat(Aluno.exigeResponsavel(LocalDate.of(1990, 1, 1), hoje)).isFalse();
    }

    @Test
    void novoAlunoAtivoComCpfProtegido() {
        Aluno aluno = aluno(LocalDate.of(2014, 5, 10));
        assertThat(aluno.ativo()).isTrue();
        assertThat(aluno.cpfHash()).isEqualTo("hash");
        byte[] cifrado = aluno.cpfCifrado();
        cifrado[0] = 9;
        assertThat(aluno.cpfCifrado()).containsExactly(1, 2);
        assertThat(aluno.toString()).doesNotContain("Matteo");
        assertThat(aluno.criadoEm()).isEqualTo(T0);
        assertThat(aluno.atualizadoEm()).isEqualTo(T0);
    }

    @Test
    void atualizaCadastroSomenteQuandoMuda() {
        Aluno aluno = aluno(LocalDate.of(2014, 5, 10));
        Instant depois = T0.plusSeconds(60);
        assertThat(
                        aluno.atualizarCadastro(
                                "Matteo Mafra", LocalDate.of(2014, 5, 10), "Resp", "Piano", depois))
                .isFalse();
        assertThat(
                        aluno.atualizarCadastro(
                                "Matteo Mafra",
                                LocalDate.of(2014, 5, 10),
                                "Resp",
                                "Violão",
                                depois))
                .isTrue();
        assertThat(aluno.instrumento()).isEqualTo("Violão");
        assertThat(aluno.atualizadoEm()).isEqualTo(depois);
        assertThat(
                        aluno.atualizarCadastro(
                                "Matteo M.", LocalDate.of(2014, 5, 10), "Resp", "Violão", depois))
                .isTrue();
        assertThat(
                        aluno.atualizarCadastro(
                                "Matteo M.", LocalDate.of(2014, 5, 11), "Resp", "Violão", depois))
                .isTrue();
        assertThat(
                        aluno.atualizarCadastro(
                                "Matteo M.", LocalDate.of(2014, 5, 11), null, "Violão", depois))
                .isTrue();
        assertThat(aluno.nome()).isEqualTo("Matteo M.");
        assertThat(aluno.dataNascimento()).isEqualTo(LocalDate.of(2014, 5, 11));
        assertThat(aluno.nomeResponsavel()).isNull();
    }
}
