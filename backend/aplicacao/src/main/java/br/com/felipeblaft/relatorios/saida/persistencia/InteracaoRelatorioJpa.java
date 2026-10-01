package br.com.felipeblaft.relatorios.saida.persistencia;

import br.com.felipeblaft.relatorios.saida.persistencia.RelatorioEntidades.InteracaoRelatorioEntidade;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface InteracaoRelatorioJpa extends JpaRepository<InteracaoRelatorioEntidade, UUID> {

    Optional<InteracaoRelatorioEntidade>
            findFirstByRelatorioTipoAndRelatorioIdOrderByCriadoEmDescIdDesc(
                    String relatorioTipo, UUID relatorioId);
}
