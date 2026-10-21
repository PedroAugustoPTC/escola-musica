package br.com.felipeblaft.reports.support;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Aluno canário (SC-004): menor de idade fictício e constante, com nome, data de nascimento e os
 * três dados do responsável. Cada valor é distinto dos demais e improvável em texto comum, de modo
 * que uma varredura ({@link ResponseSweeper}) o ache sem falso positivo. O CPF fica em {@link
 * CanaryCpf}. Nada aqui pertence a pessoa real.
 */
public final class CanaryStudent {

    public static final String NAME = "Pietra Zanzibar Quimera";
    public static final LocalDate BIRTH_DATE = LocalDate.of(2016, 3, 14);
    public static final String BIRTH_DATE_ISO = "2016-03-14";
    public static final String BIRTH_DATE_BR = "14/03/2016";
    public static final String GUARDIAN_NAME = "Hortênsio Balbúrdia Cantarelli";
    public static final String GUARDIAN_PHONE_DIGITS = "11943218765";
    public static final String GUARDIAN_PHONE_MASKED = "(11) 94321-8765";
    public static final String GUARDIAN_EMAIL = "balburdia.cantarelli@example.test";
    public static final String INSTRUMENT = "Violão";

    private CanaryStudent() {}

    /** Rótulo e valor de cada dado pessoal do aluno e do responsável (sem o CPF). */
    public static Map<String, String> sweepValues() {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("nome do aluno canário", NAME);
        values.put("nascimento do aluno canário (ISO)", BIRTH_DATE_ISO);
        values.put("nascimento do aluno canário (BR)", BIRTH_DATE_BR);
        values.put("nome do responsável canário", GUARDIAN_NAME);
        values.put("telefone do responsável canário", GUARDIAN_PHONE_DIGITS);
        values.put("e-mail do responsável canário", GUARDIAN_EMAIL);
        return values;
    }

    /** Os dados pessoais do aluno canário mais o CPF canário. */
    public static Map<String, String> sweepValuesWithCpf() {
        Map<String, String> values = new LinkedHashMap<>(sweepValues());
        values.putAll(CanaryCpf.sweepValues());
        return values;
    }
}
