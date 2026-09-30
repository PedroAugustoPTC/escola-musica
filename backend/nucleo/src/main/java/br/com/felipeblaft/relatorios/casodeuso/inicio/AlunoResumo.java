package br.com.felipeblaft.relatorios.casodeuso.inicio;

import br.com.felipeblaft.relatorios.dominio.cadastro.Aluno;
import java.time.LocalDate;
import java.util.UUID;

/** Aluno como aparece nas listas dos dois canais (sem CPF nem data de nascimento). */
public record AlunoResumo(
        UUID id, String nome, String instrumento, LocalDate ultimaAulaEm, boolean possuiRelatorioEmAberto) {

    static AlunoResumo de(Aluno aluno, LocalDate ultimaAula, boolean emAberto) {
        return new AlunoResumo(aluno.id(), aluno.nome(), aluno.instrumento(), ultimaAula, emAberto);
    }
}
