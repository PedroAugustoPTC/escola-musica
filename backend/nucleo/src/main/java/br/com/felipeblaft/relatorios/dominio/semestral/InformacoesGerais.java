package br.com.felipeblaft.relatorios.dominio.semestral;

import java.time.LocalDate;
import java.time.Period;
import java.util.Objects;

/**
 * Seção 1 do semestral, preenchida pelo sistema (FR-044): nunca vem da IA nem pode ser alterada por
 * ela. A idade é calculada no fim do período ou hoje, o que vier antes.
 */
public record InformacoesGerais(
        String nome,
        int idade,
        String instrumento,
        String professor,
        String periodoChave,
        LocalDate periodoInicio,
        LocalDate periodoFim,
        int quantidadeAulasConsideradas) {

    public InformacoesGerais {
        Objects.requireNonNull(nome);
        Objects.requireNonNull(professor);
        Objects.requireNonNull(periodoChave);
        Objects.requireNonNull(periodoInicio);
        Objects.requireNonNull(periodoFim);
    }

    public static InformacoesGerais calcular(
            String nome,
            LocalDate dataNascimento,
            String instrumento,
            String professor,
            PeriodoSemestral periodo,
            int quantidadeAulas,
            LocalDate hoje) {
        LocalDate referencia = periodo.fim().isBefore(hoje) ? periodo.fim() : hoje;
        int idade = Math.max(0, Period.between(dataNascimento, referencia).getYears());
        return new InformacoesGerais(
                nome,
                idade,
                instrumento,
                professor,
                periodo.chave(),
                periodo.inicio(),
                periodo.fim(),
                quantidadeAulas);
    }
}
