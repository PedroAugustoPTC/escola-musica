package br.com.felipeblaft.relatorios.saida.persistencia;

import br.com.felipeblaft.relatorios.dominio.comum.TipoRelatorio;
import br.com.felipeblaft.relatorios.dominio.trabalho.ChamadaIa;
import br.com.felipeblaft.relatorios.porta.RegistroCustoPorta;
import br.com.felipeblaft.relatorios.saida.persistencia.RelatorioEntidades.ChamadaIaEntidade;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Custo e tempo por chamada de IA na tabela {@code chamada_ia} (FR-056); sem prompt nem resposta.
 */
@Repository
class RegistroCustoAdaptador implements RegistroCustoPorta {

    private final ChamadaIaJpa jpa;

    RegistroCustoAdaptador(ChamadaIaJpa jpa) {
        this.jpa = jpa;
    }

    @Override
    public void registrar(ChamadaIa c) {
        ChamadaIaEntidade e = new ChamadaIaEntidade();
        e.id = c.id();
        e.trabalhoId = c.trabalhoId();
        e.relatorioTipo = c.relatorioTipo() == null ? null : c.relatorioTipo().name();
        e.relatorioId = c.relatorioId();
        e.tarefa = c.tarefa().name();
        e.provedorModelo = c.provedorModelo();
        e.segundosAudio = c.segundosAudio();
        e.tokensEntrada = c.tokensEntrada();
        e.tokensSaida = c.tokensSaida();
        e.duracaoMs = c.duracaoMs();
        e.custoEstimadoUsd = c.custoEstimadoUsd();
        e.sucesso = c.sucesso();
        e.codigoErro = c.codigoErro();
        e.criadoEm = c.criadoEm();
        jpa.save(e);
    }

    @Override
    @Transactional
    public void vincularRelatorio(UUID trabalhoId, TipoRelatorio tipo, UUID relatorioId) {
        jpa.vincularRelatorio(trabalhoId, tipo.name(), relatorioId);
    }
}
