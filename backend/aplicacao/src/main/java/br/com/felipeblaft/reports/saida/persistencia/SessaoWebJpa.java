package br.com.felipeblaft.reports.saida.persistencia;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

interface SessaoWebJpa extends JpaRepository<SessaoWebEntidade, UUID> {

    Optional<SessaoWebEntidade> findByTokenHash(String tokenHash);

    @Modifying
    @Query("delete from SessaoWebEntidade s where s.expiraEm <= :agora")
    int removerVencidas(Instant agora);
}
