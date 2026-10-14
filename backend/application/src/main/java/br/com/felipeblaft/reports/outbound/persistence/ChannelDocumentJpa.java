package br.com.felipeblaft.reports.outbound.persistence;

import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

interface ChannelDocumentJpa extends JpaRepository<ChannelDocumentEntity, String> {

    @Modifying
    @Query("delete from ChannelDocumentEntity d where d.expiresAt <= :now")
    int removeExpired(Instant now);
}
