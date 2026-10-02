package br.com.felipeblaft.relatorios.saida.persistencia;

import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

interface DocumentoCanalJpa extends JpaRepository<DocumentoCanalEntidade, String> {

    @Modifying
    @Query("delete from DocumentoCanalEntidade d where d.expiraEm <= :agora")
    int removerVencidos(Instant agora);
}
