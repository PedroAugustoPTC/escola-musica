package br.com.felipeblaft.reports.support;

import br.com.felipeblaft.reports.support.ResponseSweeper.Finding;
import br.com.felipeblaft.reports.support.ResponseSweeper.Place;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Acumula as varreduras de vazamento de um teste (SC-004) e falha uma única vez, ao final, com todos
 * os achados. Cada achado cita o passo, o rótulo do valor e o lugar, nunca o valor. Serve aos
 * testes transversais de privacidade que percorrem vários passos e querem ver todos os vazamentos
 * de uma vez.
 */
public final class LeakAudit {

    private final ResponseSweeper sweeper;
    private final List<String> failures = new ArrayList<>();
    private int sweeps;

    private LeakAudit(ResponseSweeper sweeper) {
        this.sweeper = sweeper;
    }

    /** Auditoria dos valores (rótulo para valor); nenhum lugar é permitido. */
    public static LeakAudit searching(Map<String, String> values) {
        return new LeakAudit(ResponseSweeper.searching(values));
    }

    /** Nova auditoria que também permite os valores nos lugares informados. */
    public LeakAudit allowing(Place... places) {
        LeakAudit audit = new LeakAudit(sweeper.allowing(places));
        audit.failures.addAll(failures);
        return audit;
    }

    /** Varre URL, cabeçalhos, corpo e mensagem de erro de uma resposta. */
    public void exchange(String step, MvcResult result) {
        record(step, sweeper.sweep(result));
    }

    /** Varre um texto de um lugar. */
    public void text(String step, Place place, String text) {
        record(step, sweeper.sweepText(place, text));
    }

    /** Varre uma resposta com um varredor específico (por exemplo, permitindo o corpo). */
    public void exchange(String step, MvcResult result, ResponseSweeper specific) {
        record(step, specific.sweep(result));
    }

    private void record(String step, List<Finding> findings) {
        sweeps++;
        for (Finding finding : findings) {
            failures.add(step + ": " + finding);
        }
    }

    /** Quantas varreduras foram feitas (prova de que o teste examinou algo). */
    public int sweeps() {
        return sweeps;
    }

    /** Falha com todos os achados acumulados (rótulo, lugar e passo; nunca o valor). */
    public void assertClean() {
        if (!failures.isEmpty()) {
            throw new AssertionError(
                    "Dado canário em lugar não permitido (" + failures.size() + "):\n  "
                            + String.join("\n  ", failures));
        }
    }
}
