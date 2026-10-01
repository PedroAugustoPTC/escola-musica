package br.com.felipeblaft.relatorios.saida.persistencia;

import br.com.felipeblaft.relatorios.dominio.comum.TipoRelatorio;
import br.com.felipeblaft.relatorios.dominio.relatorio.InteracaoRelatorio;
import br.com.felipeblaft.relatorios.porta.InteracaoRelatorioRepositorio;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
class InteracaoRelatorioRepositorioAdaptador implements InteracaoRelatorioRepositorio {

    private final InteracaoRelatorioJpa jpa;

    InteracaoRelatorioRepositorioAdaptador(InteracaoRelatorioJpa jpa) {
        this.jpa = jpa;
    }

    @Override
    public void salvar(InteracaoRelatorio interacao) {
        jpa.saveAndFlush(RelatorioAulaMapeador.paraEntidade(interacao));
    }

    @Override
    public Optional<InteracaoRelatorio> buscarPorId(UUID id) {
        return jpa.findById(id).map(RelatorioAulaMapeador::paraDominio);
    }

    @Override
    public Optional<InteracaoRelatorio> ultima(TipoRelatorio tipo, UUID relatorioId) {
        return jpa.findFirstByRelatorioTipoAndRelatorioIdOrderByCriadoEmDescIdDesc(
                        tipo.name(), relatorioId)
                .map(RelatorioAulaMapeador::paraDominio);
    }
}
