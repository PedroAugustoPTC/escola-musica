package br.com.felipeblaft.reports.inbound.telegram;

import br.com.felipeblaft.reports.domain.common.DomainException;
import br.com.felipeblaft.reports.domain.registry.Cpf;
import java.util.regex.Pattern;

/**
 * Reconhece apenas a <em>forma</em> de um CPF para que o texto nunca seja usado como busca, ecoado
 * nem guardado: depois de remover espaços, pontos, traços e barras, restam exatamente 11 dígitos
 * (com ou sem formatação). A validade do CPF é decidida no núcleo; {@link #isValid(String)} só
 * serve para separar um CPF digitado onde se espera um telefone.
 */
final class CpfShape {

    private static final Pattern SEPARATORS = Pattern.compile("[\s.\-/]");
    private static final Pattern ELEVEN_DIGITS = Pattern.compile("\d{11}");

    private CpfShape() {}

    static boolean matches(String text) {
        return text != null && ELEVEN_DIGITS.matcher(SEPARATORS.matcher(text).replaceAll("")).matches();
    }

    /** Forma de CPF e dígitos verificadores corretos (o núcleo é quem sabe validar). */
    static boolean isValid(String text) {
        if (!matches(text)) {
            return false;
        }
        try {
            Cpf.of(text);
            return true;
        } catch (DomainException invalid) {
            return false;
        }
    }
}
