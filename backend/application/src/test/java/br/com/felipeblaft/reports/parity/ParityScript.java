package br.com.felipeblaft.reports.parity;

import java.util.ArrayList;
import java.util.List;

/**
 * Roteiro único executado nos dois canais (R-16, SC-006): cada passo registra um resultado neutro e
 * o teste compara os dois registros, que devem ser idênticos.
 */
public final class ParityScript {

    public interface Step {
        Object run(ChannelDriver channel) throws Exception;
    }

    private final List<String> names = new ArrayList<>();
    private final List<Step> steps = new ArrayList<>();

    public ParityScript step(String name, Step step) {
        names.add(name);
        steps.add(step);
        return this;
    }

    /** Executa o roteiro no canal e devolve "passo = resultado" de cada passo, em ordem. */
    public List<String> run(ChannelDriver channel) throws Exception {
        List<String> registration = new ArrayList<>();
        for (int i = 0; i < steps.size(); i++) {
            registration.add(names.get(i) + " = " + steps.get(i).run(channel));
        }
        return registration;
    }
}
