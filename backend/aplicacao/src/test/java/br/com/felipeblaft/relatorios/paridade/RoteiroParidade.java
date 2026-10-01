package br.com.felipeblaft.relatorios.paridade;

import java.util.ArrayList;
import java.util.List;

/**
 * Roteiro único executado nos dois canais (R-16, SC-006): cada passo registra um resultado neutro e
 * o teste compara os dois registros, que devem ser idênticos.
 */
public final class RoteiroParidade {

    public interface Passo {
        Object executar(CanalDriver canal) throws Exception;
    }

    private final List<String> nomes = new ArrayList<>();
    private final List<Passo> passos = new ArrayList<>();

    public RoteiroParidade passo(String nome, Passo passo) {
        nomes.add(nome);
        passos.add(passo);
        return this;
    }

    /** Executa o roteiro no canal e devolve "passo = resultado" de cada passo, em ordem. */
    public List<String> executar(CanalDriver canal) throws Exception {
        List<String> registro = new ArrayList<>();
        for (int i = 0; i < passos.size(); i++) {
            registro.add(nomes.get(i) + " = " + passos.get(i).executar(canal));
        }
        return registro;
    }
}
