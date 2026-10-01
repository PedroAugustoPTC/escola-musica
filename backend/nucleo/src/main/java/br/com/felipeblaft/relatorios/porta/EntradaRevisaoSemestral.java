package br.com.felipeblaft.relatorios.porta;

import br.com.felipeblaft.relatorios.dominio.semestral.ConteudoRelatorioSemestral;
import java.util.Objects;

/** Entrada da revisão do semestral: conteúdo atual (seções 2 a 10) e o pedido do professor. */
public record EntradaRevisaoSemestral(ConteudoRelatorioSemestral conteudoAtual, String pedido)
        implements EntradaTarefaSemestral {

    public EntradaRevisaoSemestral {
        Objects.requireNonNull(conteudoAtual);
        Objects.requireNonNull(pedido);
    }
}
