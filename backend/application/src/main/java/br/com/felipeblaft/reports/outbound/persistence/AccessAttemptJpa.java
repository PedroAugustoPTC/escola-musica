package br.com.felipeblaft.reports.outbound.persistence;

import br.com.felipeblaft.reports.domain.common.Channel;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface AccessAttemptJpa extends JpaRepository<AccessAttemptEntity, UUID> {

    Optional<AccessAttemptEntity> findByChannelAndSourceHash(Channel channel, String originHash);
}
