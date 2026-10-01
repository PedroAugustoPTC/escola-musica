package br.com.felipeblaft.relatorios.saida.persistencia;

import br.com.felipeblaft.relatorios.dominio.comum.TipoRelatorio;
import br.com.felipeblaft.relatorios.dominio.relatorio.VersaoRelatorio;
import br.com.felipeblaft.relatorios.porta.VersaoRelatorioRepositorio;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

/** Versões imutáveis: só inserção (Princípio III). */
@Repository
class VersaoRelatorioRepositorioAdaptador implements VersaoRelatorioRepositorio {

    private final VersaoRelatorioJpa jpa;

    VersaoRelatorioRepositorioAdaptador(VersaoRelatorioJpa jpa) {
        this.jpa = jpa;
    }

    @Override
    public void salvar(VersaoRelatorio versao) {
        if (jpa.existsById(versao.id())) {
            throw new IllegalStateException("versão é imutável");
        }
        jpa.saveAndFlush(RelatorioAulaMapeador.paraEntidade(versao));
    }

    @Override
    public Optional<VersaoRelatorio> buscar(TipoRelatorio tipo, UUID relatorioId, int numero) {
        return jpa.findByRelatorioTipoAndRelatorioIdAndNumero(tipo.name(), relatorioId, numero)
                .map(RelatorioAulaMapeador::paraDominio);
    }
}
