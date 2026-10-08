package br.com.felipeblaft.relatorios.casodeuso.canal;

import br.com.felipeblaft.relatorios.dominio.canal.AcaoCanal;
import br.com.felipeblaft.relatorios.dominio.canal.EstadoAcaoCanal;
import br.com.felipeblaft.relatorios.porta.AcaoCanalRepositorio;
import java.util.List;
import java.util.Objects;

/**
 * Update repetido (FR-007): devolve só as ações ainda não confirmadas, na ordem original e com os
 * mesmos ids; lista vazia se todas foram confirmadas.
 */
public class ObterAcoesNaoConfirmadasDoUpdateCasoDeUso {

    private final AcaoCanalRepositorio acoes;

    public ObterAcoesNaoConfirmadasDoUpdateCasoDeUso(AcaoCanalRepositorio acoes) {
        this.acoes = Objects.requireNonNull(acoes);
    }

    public List<AcaoCanal> executar(long updateId) {
        return acoes.buscarPorUpdate(updateId).stream()
                .filter(acao -> acao.estado() == EstadoAcaoCanal.ENTREGUE)
                .filter(acao -> acao.payload() != null)
                .toList();
    }
}
