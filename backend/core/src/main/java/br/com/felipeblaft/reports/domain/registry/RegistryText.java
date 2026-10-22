package br.com.felipeblaft.reports.domain.registry;

import br.com.felipeblaft.reports.domain.common.DomainException;
import br.com.felipeblaft.reports.domain.common.ErrorCode;
import java.text.Normalizer;

/**
 * Regra comum de texto dos cadastros (FR-018): corte das pontas, NFC, vazio após o corte é ausente
 * e caracteres de controle são recusados com {@code INVALID_TEXT}. O tamanho conta pontos de
 * código, não unidades UTF-16.
 */
final class RegistryText {

    private RegistryText() {}

    /** Texto limpo, ou {@code null} se ausente (nulo ou vazio depois do corte). */
    static String clean(String raw) {
        if (raw == null) {
            return null;
        }
        String text = Normalizer.normalize(raw, Normalizer.Form.NFC).strip();
        if (text.isEmpty()) {
            return null;
        }
        if (text.codePoints().anyMatch(point -> Character.getType(point) == Character.CONTROL)) {
            throw new DomainException(ErrorCode.INVALID_TEXT);
        }
        return text;
    }

    static int length(String text) {
        return text.codePointCount(0, text.length());
    }
}
