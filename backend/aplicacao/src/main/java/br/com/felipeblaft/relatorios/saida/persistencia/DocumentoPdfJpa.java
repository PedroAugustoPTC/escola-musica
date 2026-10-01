package br.com.felipeblaft.relatorios.saida.persistencia;

import br.com.felipeblaft.relatorios.saida.persistencia.RelatorioEntidades.DocumentoPdfEntidade;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface DocumentoPdfJpa extends JpaRepository<DocumentoPdfEntidade, UUID> {

    Optional<DocumentoPdfEntidade> findByVersaoIdAndTipo(UUID versaoId, String tipo);
}
