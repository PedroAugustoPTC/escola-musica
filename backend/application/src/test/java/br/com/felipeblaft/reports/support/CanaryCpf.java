package br.com.felipeblaft.reports.support;

import br.com.felipeblaft.reports.domain.registry.Cpf;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * CPF canário (SC-004): fictício, válido e constante, para os testes de privacidade procurarem
 * vazamento em log, resposta, cabeçalho, URL, mensagem de erro, autoria e ação de saída. Não
 * pertence a nenhuma pessoa; nunca use um CPF real em teste.
 */
public final class CanaryCpf {

    /** Os 11 dígitos. */
    public static final String DIGITS = "11144477735";

    /** A forma com máscara, como o usuário digita. */
    public static final String MASKED = "111.444.777-35";

    private CanaryCpf() {}

    public static Cpf cpf() {
        return Cpf.of(DIGITS);
    }

    /** Rótulo e valor a varrer; o varredor acha o CPF em qualquer máscara ou separador. */
    public static Map<String, String> sweepValues() {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("CPF canário", DIGITS);
        return values;
    }
}
