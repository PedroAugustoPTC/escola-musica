package br.com.felipeblaft.relatorios.saida.persistencia;

import br.com.felipeblaft.relatorios.saida.persistencia.RelatorioEntidades.AulaEntidade;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface AulaJpa extends JpaRepository<AulaEntidade, UUID> {}
