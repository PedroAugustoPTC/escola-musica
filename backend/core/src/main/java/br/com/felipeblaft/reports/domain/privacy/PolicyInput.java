package br.com.felipeblaft.reports.domain.privacy;

import java.util.List;
import java.util.Map;

/**
 * Leitura tolerante de parâmetros brutos (FR-001, FR-002): ausente ou em branco vale o padrão sem
 * aviso; zero, negativo, fora do limite ou não numérico vale o padrão e gera um aviso que cita só o
 * nome da variável, nunca o valor.
 */
public final class PolicyInput {

    private PolicyInput() {}

    public static PolicyValue read(
            Map<String, String> source,
            String name,
            int defaultValue,
            int maximum,
            List<String> warnings) {
        String raw = source.get(name);
        if (raw == null || raw.isBlank()) {
            return PolicyValue.ofDefault(defaultValue);
        }
        try {
            int parsed = Integer.parseInt(raw.trim());
            if (parsed >= 1 && parsed <= maximum) {
                return new PolicyValue(parsed, PolicyOrigin.CONFIGURED);
            }
        } catch (NumberFormatException invalid) {
            // cai no aviso abaixo
        }
        warnings.add("Variável " + name + " inválida; usando o valor padrão " + defaultValue + ".");
        return PolicyValue.ofDefault(defaultValue);
    }
}
