package br.com.felipeblaft.reports.saida.persistencia;

import br.com.felipeblaft.reports.domain.common.ReportType;
import br.com.felipeblaft.reports.domain.report.ReportInteraction;
import br.com.felipeblaft.reports.port.ReportInteractionRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
class InteracaoRelatorioRepositorioAdaptador implements ReportInteractionRepository {

    private final InteracaoRelatorioJpa jpa;

    InteracaoRelatorioRepositorioAdaptador(InteracaoRelatorioJpa jpa) {
        this.jpa = jpa;
    }

    @Override
    public void save(ReportInteraction interacao) {
        jpa.saveAndFlush(RelatorioAulaMapeador.paraEntidade(interacao));
    }

    @Override
    public Optional<ReportInteraction> searchById(UUID id) {
        return jpa.findById(id).map(RelatorioAulaMapeador::paraDominio);
    }

    @Override
    public Optional<ReportInteraction> last(ReportType tipo, UUID relatorioId) {
        return jpa.findFirstByRelatorioTipoAndRelatorioIdOrderByCriadoEmDescIdDesc(
                        tipo.name(), relatorioId)
                .map(RelatorioAulaMapeador::paraDominio);
    }
}
