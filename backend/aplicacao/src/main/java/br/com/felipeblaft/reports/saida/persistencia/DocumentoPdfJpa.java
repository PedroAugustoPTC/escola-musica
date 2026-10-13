package br.com.felipeblaft.reports.saida.persistencia;

import br.com.felipeblaft.reports.saida.persistencia.RelatorioEntidades.DocumentoPdfEntidade;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface DocumentoPdfJpa extends JpaRepository<DocumentoPdfEntidade, UUID> {

    Optional<DocumentoPdfEntidade> findByVersaoIdAndTipo(UUID versaoId, String tipo);
}
