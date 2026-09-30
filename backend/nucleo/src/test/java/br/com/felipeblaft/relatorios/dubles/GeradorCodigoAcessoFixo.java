package br.com.felipeblaft.relatorios.dubles;

import br.com.felipeblaft.relatorios.dominio.acesso.CodigoAcesso;
import br.com.felipeblaft.relatorios.porta.GeradorCodigoAcessoPorta;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/** Devolve os códigos programados, em ordem; depois deles, códigos sequenciais previsíveis. */
public class GeradorCodigoAcessoFixo implements GeradorCodigoAcessoPorta {

    private final Deque<String> programados = new ArrayDeque<>();
    private int sequencia;

    public GeradorCodigoAcessoFixo(String... codigos) {
        programados.addAll(List.of(codigos));
    }

    public void programar(String... codigos) {
        programados.addAll(List.of(codigos));
    }

    @Override
    public CodigoAcesso gerar() {
        if (!programados.isEmpty()) {
            return CodigoAcesso.de(programados.poll());
        }
        sequencia++;
        String sufixo = Integer.toString(sequencia, 8).replace('0', 'A').replace('1', 'B');
        String base = "KQMXPTRW";
        return CodigoAcesso.de(base.substring(0, 8 - sufixo.length()) + sufixo.replace('8', 'C'));
    }
}
