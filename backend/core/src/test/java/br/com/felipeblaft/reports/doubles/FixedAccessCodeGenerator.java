package br.com.felipeblaft.reports.doubles;

import br.com.felipeblaft.reports.domain.access.AccessCode;
import br.com.felipeblaft.reports.port.AccessCodeGeneratorPort;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/** Devolve os códigos programados, em ordem; depois deles, códigos sequenciais previsíveis. */
public class FixedAccessCodeGenerator implements AccessCodeGeneratorPort {

    private final Deque<String> scheduled = new ArrayDeque<>();
    private int sequence;

    public FixedAccessCodeGenerator(String... codes) {
        scheduled.addAll(List.of(codes));
    }

    public void schedule(String... codes) {
        scheduled.addAll(List.of(codes));
    }

    @Override
    public AccessCode generate() {
        if (!scheduled.isEmpty()) {
            return AccessCode.of(scheduled.poll());
        }
        sequence++;
        String suffix = Integer.toString(sequence, 8).replace('0', 'A').replace('1', 'B');
        String base = "KQMXPTRW";
        return AccessCode.of(base.substring(0, 8 - suffix.length()) + suffix.replace('8', 'C'));
    }
}
