package br.com.felipeblaft.reports.doubles;

import br.com.felipeblaft.reports.domain.access.ActivationCode;
import br.com.felipeblaft.reports.port.ActivationCodeGeneratorPort;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/**
 * Devolve os códigos de ativação programados, em ordem; depois deles, códigos sequenciais
 * previsíveis e distintos (12 caracteres do alfabeto sem ambíguos).
 */
public class FixedActivationCodeGenerator implements ActivationCodeGeneratorPort {

    private static final String BASE = "KQMXPTRW";
    private static final String DIGITS = "23456789";
    private static final int TAIL = 4;

    private final Deque<String> scheduled = new ArrayDeque<>();
    private int sequence;

    public FixedActivationCodeGenerator(String... codes) {
        scheduled.addAll(List.of(codes));
    }

    public void schedule(String... codes) {
        scheduled.addAll(List.of(codes));
    }

    @Override
    public ActivationCode generate() {
        if (!scheduled.isEmpty()) {
            return activation(scheduled.poll());
        }
        sequence++;
        StringBuilder tail = new StringBuilder();
        int rest = sequence;
        for (int i = 0; i < TAIL; i++) {
            tail.append(DIGITS.charAt(rest % DIGITS.length()));
            rest /= DIGITS.length();
        }
        return activation(BASE + tail.reverse());
    }

    private static ActivationCode activation(String value) {
        return ActivationCode.retry(value)
                .orElseThrow(
                        () -> new IllegalArgumentException("código de ativação fora do formato"));
    }
}
