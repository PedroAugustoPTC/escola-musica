package br.com.felipeblaft.reports.saida.persistencia;

import br.com.felipeblaft.reports.domain.common.ReportType;
import br.com.felipeblaft.reports.domain.report.ReportVersion;
import br.com.felipeblaft.reports.port.ReportVersionRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

/** Versões imutáveis: só inserção (Princípio III). */
@Repository
class VersaoRelatorioRepositorioAdaptador implements ReportVersionRepository {

    private final VersaoRelatorioJpa jpa;

    VersaoRelatorioRepositorioAdaptador(VersaoRelatorioJpa jpa) {
        this.jpa = jpa;
    }

    @Override
    public void save(ReportVersion versao) {
        if (jpa.existsById(versao.id())) {
            throw new IllegalStateException("versão é imutável");
        }
        jpa.saveAndFlush(RelatorioAulaMapeador.paraEntidade(versao));
    }

    @Override
    public Optional<ReportVersion> search(ReportType tipo, UUID relatorioId, int numero) {
        return jpa.findByRelatorioTipoAndRelatorioIdAndNumero(tipo.name(), relatorioId, numero)
                .map(RelatorioAulaMapeador::paraDominio);
    }
}
