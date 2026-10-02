package br.com.felipeblaft.relatorios.dubles;

import br.com.felipeblaft.relatorios.porta.LoteEntrega;
import br.com.felipeblaft.relatorios.porta.OrquestradorCanalPorta;
import br.com.felipeblaft.relatorios.porta.ResultadoEntrega;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/** Orquestrador programável: aceita por padrão, recusa conforme a fila de respostas e registra os lotes. */
public class OrquestradorCanalFalso implements OrquestradorCanalPorta {

    public final List<LoteEntrega> recebidos = new ArrayList<>();
    private final Deque<ResultadoEntrega> programadas = new ArrayDeque<>();

    /** Executado durante a entrega, antes da resposta: simula a confirmação chegando no meio do HTTP. */
    public Runnable duranteEntrega = () -> {};

    public void recusarProximas(String codigo, int vezes) {
        for (int i = 0; i < vezes; i++) {
            programadas.add(ResultadoEntrega.recusado(codigo));
        }
    }

    @Override
    public ResultadoEntrega entregar(LoteEntrega lote) {
        recebidos.add(lote);
        duranteEntrega.run();
        ResultadoEntrega programado = programadas.poll();
        return programado == null ? ResultadoEntrega.aceite() : programado;
    }
}
