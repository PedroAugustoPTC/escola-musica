package br.com.felipeblaft.reports.domain.registry;

import java.time.LocalDate;

/**
 * Entrada validada de aluno (R-11): valida todos os campos e acumula as violações em um único
 * {@code VALIDATION_FAILED} com {@code details.fields}. Esqueleto da Fundação; a regra é do fluxo
 * W-C1.
 *
 * @param cpf obrigatório no cadastro; {@code null} na edição que mantém o CPF atual
 * @param instrument opcional
 * @param guardian obrigatório se o aluno é menor de 18 anos na data de referência
 */
public record StudentData(
        PersonName name, BirthDate birthDate, Cpf cpf, Instrument instrument, Guardian guardian) {

    /**
     * Valida os textos brutos recebidos de qualquer canal. {@code today} é a data de hoje no fuso
     * da escola.
     */
    public static StudentData of(
            String name,
            String birthDate,
            String cpf,
            String instrument,
            String guardianName,
            String guardianPhone,
            String guardianEmail,
            LocalDate today) {
        throw new UnsupportedOperationException("StudentData: fluxo W-C1");
    }

    @Override
    public String toString() {
        return "StudentData";
    }
}
