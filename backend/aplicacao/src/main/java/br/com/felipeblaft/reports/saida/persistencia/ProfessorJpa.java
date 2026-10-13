package br.com.felipeblaft.reports.saida.persistencia;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface ProfessorJpa extends JpaRepository<ProfessorEntidade, UUID> {

    Optional<ProfessorEntidade> findByEmail(String email);

    Optional<ProfessorEntidade> findByCodigoAcessoHash(String codigoAcessoHash);
}
