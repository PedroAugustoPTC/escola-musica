package br.com.felipeblaft.reports.outbound.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface ConsentTermJpa extends JpaRepository<ConsentTermEntity, UUID> {

    @Query("select t from ConsentTermEntity t order by t.number desc")
    List<ConsentTermEntity> listNewestFirst();
}
