package br.com.felipeblaft.relatorios.casodeuso.canal;

import br.com.felipeblaft.relatorios.dominio.canal.AcaoCanal;
import br.com.felipeblaft.relatorios.porta.AcaoCanalRepositorio;
import java.util.Objects;
import java.util.Optional;

/**
 * Estado de uma ação, para que o orquestrador, sem guardar estado próprio (Princípio II), saiba se
 * uma ação repetida já foi tratada (FR-013).
 */
public class ConsultarAcaoCanalCasoDeUso {

    private final AcaoCanalRepositorio acoes;

    public ConsultarAcaoCanalCasoDeUso(AcaoCanalRepositorio acoes) {
        this.acoes = Objects.requireNonNull(acoes);
    }

    public Optional<AcaoCanal> executar(String acaoId) {
        return acoes.buscarPorId(acaoId);
    }
}
