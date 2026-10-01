package br.com.felipeblaft.relatorios.saida.persistencia;

import br.com.felipeblaft.relatorios.saida.persistencia.RelatorioEntidades.ChamadaIaEntidade;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface ChamadaIaJpa extends JpaRepository<ChamadaIaEntidade, UUID> {}
