package br.com.felipeblaft.relatorios.saida.persistencia;

import br.com.felipeblaft.relatorios.saida.persistencia.RelatorioEntidades.ChamadaIaEntidade;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

interface ChamadaIaJpa extends JpaRepository<ChamadaIaEntidade, UUID> {

    /** Vincula ao relatório as chamadas do trabalho ainda sem relatório. */
    @Modifying
    @Query(
            "update ChamadaIaEntidade c set c.relatorioTipo = :tipo, c.relatorioId = :relatorioId"
                    + " where c.trabalhoId = :trabalhoId and c.relatorioId is null")
    int vincularRelatorio(UUID trabalhoId, String tipo, UUID relatorioId);
}
