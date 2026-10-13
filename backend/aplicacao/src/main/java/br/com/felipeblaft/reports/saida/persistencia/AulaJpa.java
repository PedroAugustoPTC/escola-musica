package br.com.felipeblaft.reports.saida.persistencia;

import br.com.felipeblaft.reports.saida.persistencia.RelatorioEntidades.AulaEntidade;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface AulaJpa extends JpaRepository<AulaEntidade, UUID> {}
