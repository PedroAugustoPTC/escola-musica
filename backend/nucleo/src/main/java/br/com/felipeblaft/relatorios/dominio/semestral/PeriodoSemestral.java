package br.com.felipeblaft.relatorios.dominio.semestral;

import br.com.felipeblaft.relatorios.dominio.comum.CodigoErro;
import br.com.felipeblaft.relatorios.dominio.comum.ExcecaoDominio;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Semestre letivo (data-model.md §9): chave {@code AAAA-S}; o 1º vai de 01/01 a 30/06 e o 2º de
 * 01/07 a 31/12.
 */
public record PeriodoSemestral(int ano, int semestre) implements Comparable<PeriodoSemestral> {

    private static final Pattern CHAVE = Pattern.compile("^(\\d{4})-([12])$");

    public PeriodoSemestral {
        if (semestre != 1 && semestre != 2) {
            throw new IllegalArgumentException("semestre deve ser 1 ou 2");
        }
    }

    /** Período a partir da chave; chave inválida é {@code DADOS_INVALIDOS}. */
    public static PeriodoSemestral de(String chave) {
        Matcher partes = chave == null ? null : CHAVE.matcher(chave);
        if (partes == null || !partes.matches()) {
            throw new ExcecaoDominio(CodigoErro.DADOS_INVALIDOS);
        }
        return new PeriodoSemestral(
                Integer.parseInt(partes.group(1)), Integer.parseInt(partes.group(2)));
    }

    public static PeriodoSemestral contendo(LocalDate data) {
        return new PeriodoSemestral(data.getYear(), data.getMonthValue() <= 6 ? 1 : 2);
    }

    /**
     * Semestres disponíveis (FR-041): do semestre de {@code desde} até o semestre de {@code hoje},
     * do mais recente ao mais antigo.
     */
    public static List<PeriodoSemestral> disponiveis(LocalDate desde, LocalDate hoje) {
        PeriodoSemestral atual = contendo(hoje);
        PeriodoSemestral primeiro = contendo(desde.isAfter(hoje) ? hoje : desde);
        List<PeriodoSemestral> periodos = new ArrayList<>();
        for (PeriodoSemestral p = atual; p.compareTo(primeiro) >= 0; p = p.anterior()) {
            periodos.add(p);
        }
        return periodos;
    }

    public String chave() {
        return ano + "-" + semestre;
    }

    public LocalDate inicio() {
        return semestre == 1 ? LocalDate.of(ano, 1, 1) : LocalDate.of(ano, 7, 1);
    }

    public LocalDate fim() {
        return semestre == 1 ? LocalDate.of(ano, 6, 30) : LocalDate.of(ano, 12, 31);
    }

    public boolean contem(LocalDate data) {
        return !data.isBefore(inicio()) && !data.isAfter(fim());
    }

    /** O semestre ainda não terminou na data de hoje. */
    public boolean emAndamento(LocalDate hoje) {
        return contem(hoje);
    }

    public PeriodoSemestral anterior() {
        return semestre == 2 ? new PeriodoSemestral(ano, 1) : new PeriodoSemestral(ano - 1, 2);
    }

    @Override
    public int compareTo(PeriodoSemestral outro) {
        return ano != outro.ano
                ? Integer.compare(ano, outro.ano)
                : Integer.compare(semestre, outro.semestre);
    }
}
