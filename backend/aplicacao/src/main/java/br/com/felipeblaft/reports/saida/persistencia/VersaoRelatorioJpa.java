package br.com.felipeblaft.reports.saida.persistencia;

import br.com.felipeblaft.reports.saida.persistencia.RelatorioEntidades.VersaoRelatorioEntidade;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface VersaoRelatorioJpa extends JpaRepository<VersaoRelatorioEntidade, UUID> {

    Optional<VersaoRelatorioEntidade> findByRelatorioTipoAndRelatorioIdAndNumero(
            String relatorioTipo, UUID relatorioId, int numero);
}
