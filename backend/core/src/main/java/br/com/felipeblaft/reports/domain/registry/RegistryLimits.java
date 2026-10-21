package br.com.felipeblaft.reports.domain.registry;

import java.time.Duration;

/**
 * Constantes de validação e de listagem dos cadastros (FR-018, data-model.md §5). Os objetos de
 * valor do pacote usam estes limites; nenhuma regra de tamanho fica em adaptador.
 */
public final class RegistryLimits {

    public static final int NAME_MIN = 2;
    public static final int NAME_MAX = 120;
    public static final int INSTRUMENT_MAX = 60;
    public static final int EMAIL_MAX = 254;

    /** Telefone brasileiro: DDD de 11 a 99 e 10 (fixo) ou 11 (celular, começa com 9) dígitos. */
    public static final int DDD_MIN = 11;

    public static final int DDD_MAX = 99;
    public static final int PHONE_DIGITS_LANDLINE = 10;
    public static final int PHONE_DIGITS_MOBILE = 11;

    /** A data de nascimento não pode ser futura nem anterior a 120 anos. */
    public static final int BIRTH_MAX_AGE_YEARS = 120;

    /** 18 anos completos na data de referência (fuso da escola) é adulto. */
    public static final int ADULT_AGE = 18;

    public static final int PASSWORD_MIN = 12;
    public static final int PASSWORD_MAX = 128;

    /** Código de ativação do administrador: 12 caracteres do alfabeto sem ambíguos, 7 dias. */
    public static final int ACTIVATION_CODE_SIZE = 12;

    public static final Duration ACTIVATION_CODE_VALIDITY = Duration.ofDays(7);

    public static final int SEARCH_MAX = 100;
    public static final int TELEGRAM_PAGE_SIZE = 6;

    private RegistryLimits() {}
}
